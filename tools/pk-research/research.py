#!/usr/bin/env python3
"""Frozen-protocol exploratory study; outputs never enter production.

Standard library only. No network, no user data, no fitting to exposed external
studies. Explicit output directory must be empty/nonexistent to protect evidence.
"""
import argparse
import csv
import hashlib
import itertools
import json
import math
import subprocess
import time
from pathlib import Path
import check_protocol as guard
import models as m

def revision():
    return subprocess.check_output(['git','rev-parse','HEAD'],cwd=guard.ROOT,text=True).strip()

def rank(matrix, tolerance=1e-7):
    """Scale-invariant numerical row/column rank by pivoted elimination."""
    a=[list(row) for row in matrix]
    if not a:return 0
    for col in range(len(a[0])):
        scale=max(abs(row[col]) for row in a)
        if scale:
            for row in a:row[col]/=scale
    pivot=0
    for col in range(len(a[0])):
        best=max(range(pivot,len(a)),key=lambda i:abs(a[i][col]),default=None)
        if best is None or abs(a[best][col]) < tolerance:continue
        a[pivot],a[best]=a[best],a[pivot]
        v=a[pivot][col];a[pivot]=[x/v for x in a[pivot]]
        for i in range(len(a)):
            if i != pivot:
                v=a[i][col];a[i]=[x-v*y for x,y in zip(a[i],a[pivot])]
        pivot+=1
        if pivot==len(a):break
    return pivot

def micro_jacobian():
    params=[.2,.5,.03375,160]
    rows=[]
    for t in [.25,.5,1,1.5,3,6,12,24]:
        row=[]
        for i in range(4):
            a=params.copy();b=params.copy();step=1e-5*params[i]
            a[i]+=step;b[i]-=step
            row.append((m.from_micro(*a)(t)-m.from_micro(*b)(t))/(2*step))
        rows.append(row)
    return {'parameters':['f_m','F_m','F_g','V_L'],'synthetic_times_h':[.25,.5,1,1.5,3,6,12,24],'rank':rank(rows),'columns':4,'jacobian':rows,'tolerance':1e-7,'human_data':False}

def profiles(catalog, settings, excluded_studies=()):
    training=[r for r in catalog['records'] if r['used_in_fitting'] and r['study_id'] not in excluded_studies]
    if not training:
        return []  # No synthetic/preset/exposed observation substituted for missing humans.
    if len(training)!=1 or training[0]['id']!='doll_sl_peak':
        raise ValueError('Only the frozen single training record is supported')
    record=training[0]
    target=m.pg_ml(record['value'],record['unit'])
    bounds=settings['amplitude_bounds'];starts=settings['amplitude_starts'];result=[]
    for baseline in settings['baseline_pg_ml']:
        for k in settings['candidate_A']['k_per_h']:
            fit=m.amplitude_fit(m.Gamma(1,k)(record['time_h'])*record['dose_mg'],target-baseline,bounds,starts)
            item={'id':f'A-b{baseline}-k{k}','model':'A','baseline_pg_ml':baseline,'fixed':{'k':k},'fit':fit,'human_train_record_ids':['doll_sl_peak'],'nominal_parameter_count':2,'actually_fitted_parameter_count':1,'single_observation_jacobian_rank':1}
            if 'amplitude' in fit:item['kernel']=m.Gamma(fit['amplitude'],k)
            result.append(item)
        s=settings['candidate_B']
        for kam,kag,ke,w in itertools.product(s['kam_per_h'],s['kag_per_h'],s['ke_per_h'],s['slow_effective_share']):
            fit=m.amplitude_fit(m.Dual(1,kam,kag,ke,w)(record['time_h'])*record['dose_mg'],target-baseline,bounds,starts)
            item={'id':f'B-b{baseline}-{kam}-{kag}-{ke}-{w}','model':'B','baseline_pg_ml':baseline,'fixed':dict(kam=kam,kag=kag,ke=ke,slow_share=w),'fit':fit,'human_train_record_ids':['doll_sl_peak'],'nominal_parameter_count':5,'actually_fitted_parameter_count':1,'single_observation_jacobian_rank':1}
            if 'amplitude' in fit:item['kernel']=m.Dual(fit['amplitude'],kam,kag,ke,w)
            result.append(item)
    return result

def match(record, kernel, baseline):
    """Compare the reported metric only, never a fabricated peak or history."""
    rid=record['id']
    if rid in ['doll_sl_peak','pines_60min']:
        predicted=baseline+record['dose_mg']*kernel(record['time_h'])
        observed=m.pg_ml(record['value'],record['unit'])
        scenario='single_dose_unknown_baseline_assumed_constant'
    elif rid=='yaish_90min':
        # Actual individual times unavailable: synthetic regular q6h ONLY.
        events=[(h-714,.5) for h,_ in m.repeat_events(6)]
        predicted=m.population(kernel,events,1.5,baseline)
        observed=m.pg_ml(record['value'],record['unit'])
        scenario='synthetic_q6h_30days_not_actual_individual_history'
    elif rid=='burnier_1h_fold':
        if baseline <= 0:return None
        predicted=(baseline+.5*kernel(record['time_h']))/baseline
        observed=record['value'];scenario='conditional_fold_baseline_not_per_person_measurement'
    else:return None
    return {'record_id':rid,'study_id':record['study_id'],'role':record['role'],'time_h':record['time_h'],'dose_mg':record['dose_mg'],'observed':observed,'observed_unit':'fold' if rid=='burnier_1h_fold' else 'pg/mL','original_value':record['value'],'original_unit':record['unit'],'original_statistic':record['statistic'],'source_location':record['source_location'],'assay':record['assay'],'baseline_assumption_pg_ml':baseline,'scenario':scenario,'comparison_status':'conditional','predicted':predicted,'signed_error':predicted-observed,'absolute_error':abs(predicted-observed),'relative_error':(predicted-observed)/observed,'log_ratio':math.log(predicted/observed) if predicted>0 and observed>0 else None,'blind_external':False,'clinical_pass':None}

def metrics(kernel):
    # Dense model-only grid; NOT added human observations. Resolve peak locally.
    times=[i*.002 for i in range(12001)]
    tmax=max(times,key=kernel)
    a,b=max(0,tmax-.002),min(24,tmax+.002)
    for _ in range(50):
        x,y=(2*a+b)/3,(a+2*b)/3
        if kernel(x)>kernel(y):b=y
        else:a=x
    tmax=(a+b)/2
    return {'synthetic_only':True,'dose_mg':1,'Cmax_pg_ml':kernel(tmax),'Tmax_h':tmax,'AUC0_8_pg_h_ml':m.simpson(kernel,8),'AUC0_24_pg_h_ml':m.simpson(kernel,24),'human_corresponding_absolute_auc_or_true_peak_obtained':False}

def run(directory, output):
    lock=guard.check(directory)
    catalog=json.loads((directory/'evidence-catalog.json').read_text())
    settings=json.loads((directory/'experiment-settings.json').read_text())
    split=json.loads((directory/'split-manifest.json').read_text())
    params=json.loads((guard.ROOT/'pk-engine/src/main/resources/pk-params.json').read_text())
    if output.exists() and any(output.iterdir()):raise ValueError('Refusing to overwrite non-empty output directory')
    output.mkdir(parents=True,exist_ok=True)
    ps=profiles(catalog,settings)
    kernels={'HRT_current':lambda t:m.current(t,params),'Featherline_80kg':lambda t:m.feather(t,catalog['comparison_model'],80)}
    canon=[]
    for name in ['A','B']:
        for profile in ps:
            if profile['model']!=name or profile['baseline_pg_ml']!=0:continue
            fixed=profile['fixed']
            if (name=='A' and fixed['k']==settings['candidate_A']['canonical_k']) or (name=='B' and list(fixed.values())==settings['candidate_B']['canonical']):
                kernels['Candidate_'+name]=profile['kernel'];canon.append(profile['id']);break
    if len(canon)!=2:raise ValueError('Missing preregistered canonical profile')
    rows=[]
    for record in catalog['records']:
        for name,kernel in kernels.items():
            baselines=settings['burnier_baseline_pg_ml'] if record['id']=='burnier_1h_fold' else settings['baseline_pg_ml']
            for baseline in baselines:
                if name.startswith('Candidate_'):
                    matching=[p for p in ps if p['model']==name[-1] and p['baseline_pg_ml']==(0 if record['id']=='burnier_1h_fold' else baseline) and p['fixed']==next(p['fixed'] for p in ps if p['id'] in canon and p['model']==name[-1])]
                    kernel=matching[0]['kernel']
                comparison=match(record,kernel,baseline)
                if comparison:comparison['model']=name;rows.append(comparison)
    profile_rows=[]
    for p in ps:
        if 'kernel' not in p:continue
        p['scenario_external_comparisons']=[]
        for record in catalog['records']:
            baseline=24 if record['id']=='burnier_1h_fold' else p['baseline_pg_ml']
            row=match(record,p['kernel'],baseline)
            if row:p['scenario_external_comparisons'].append(row)
        p['model_only_1mg_points']=[{'time_h':t,'increment_pg_ml':p['kernel'](t)} for t in settings['times_h']]
        profile_rows.append({k:v for k,v in p.items() if k!='kernel'})
    math_points=[]
    repeat=[]
    for name,kernel in kernels.items():
        for dose in settings['dose_mg']:
            for t in settings['times_h']:math_points.append({'model':name,'dose_mg':dose,'time_h':t,'concentration_pg_ml':dose*kernel(t),'synthetic_only':True,'dose_interpretation':'linear_software_extrapolation_not_validated_dose_proportionality'})
        for interval in settings['repeat_intervals_h']:
            events=m.repeat_events(interval);last=events[-1][0]
            original=m.population(kernel,events,last+46/60)
            repeat.append({'model':name,'interval_h':interval,'days':30,'dose_mg':2,'synthetic_only':True,'last_46min':original,'trough_before_next':m.population(kernel,events,last+interval),'skip_last_46min':m.population(kernel,events[:-1],last+46/60),'correct_last_to_4mg_46min':m.population(kernel,events[:-1]+[(last,4)],last+46/60),'stop_48h':m.population(kernel,events,last+48),'superposition_error':abs(original-math.fsum(2*kernel(last+46/60-h) for h,_ in events))})
    histories=[]
    for name,kernel in kernels.items():
        events=[(h-714,.5) for h,_ in m.repeat_events(6)]
        scenarios={'regular_q6h':events,'single_dose':[(0,.5)],'skip_last_prior':events[:-2]+[events[-1]],'overnight_12h_gap':[e for e in events if e[0]<=-12 or e[0]==0]}
        for scenario,history in scenarios.items():
            histories.append({'model':name,'scenario':scenario,'synthetic_only':True,'90min_pg_ml':m.population(kernel,history,1.5),'pre_dose_trough_pg_ml':m.population(kernel,[e for e in history if e[0]<0],0)})
    weights=[{'weight_assumption_kg':w,'two_mg_46min_pg_ml':2*m.feather(46/60,catalog['comparison_model'],w),'participant_measured_weight':False} for w in settings['featherline_weights_kg']]
    holds=[{'hold_minutes':h,'hrt_two_mg_46min':2*m.current(46/60,params,h),'candidate_hold_transform':'unsupported','human_validated':False} for h in settings['hold_minutes']]
    micro_sets=[(.1,1,.03,160),(.2,.5,.03375,160),(.2,.25,.016875,80)]
    mk=[m.from_micro(*g) for g in micro_sets]
    ident={'human_train_observations':1,'A_nominal_dim':2,'B_nominal_effective_dim':5,'human_jacobian_rank_max':1,'delete_Doll_training':'not_estimable_no_training_observations' if not profiles(catalog,settings,excluded_studies=('Doll2022',)) else 'unexpected_fit_after_deletion','delete_exposed_study':'fits unchanged by construction; comparisons of remaining studies unchanged; no aggregate ranking','micro_sets':[dict(zip(['f_m','F_m','F_g','V_L'],x)) for x in micro_sets],'synthetic_equivalence_max_error':max(abs(mk[0](t)-k(t)) for t in settings['times_h'] for k in mk[1:]),'micro_jacobian':micro_jacobian(),'flip_flop':{'pairs':[[100,.1,1],[10,1,.1]],'max_error':max(abs(100*m.q(t,.1,1)-10*m.q(t,1,.1)) for t in settings['times_h']),'synthetic_only':True},'parameter_uncertainty':'not_estimable','measurement_error':'not_estimable_heterogeneous_assays_unknown_statistics','study_heterogeneity':'not_estimable_seven_sparse_nonmatched_studies','structural_error':'not_estimable_no_matched_trajectory'}
    result={'code_revision':revision(),'protocol_freeze_commit':'9051875','protocol_sha256':lock['protocol_sha256'],'dataset_sha256':lock['dataset_sha256'],'params_sha256':catalog['production_params_sha256'],'code_sha256':{name:guard.digest(Path(__file__).parent/name) for name in ['models.py','research.py','check_protocol.py','test_research.py']},'seed':settings['seed'],'study_ids':[s['id'] for s in catalog['studies']],'train_ids':split['roles']['TRAIN'],'validation_ids':split['roles']['LOCKED_EXTERNAL'],'design_exposed_ids':split['roles']['DESIGN_EXPOSED'],'previously_seen':split['previously_seen'],'synthetic_only':False,'human_observations':'only evidence-catalog.records; generated points separately tagged synthetic','software_validation_passed':None,'software_validation_note':'Run unittest and engineering checks; this scientific CLI alone cannot certify CI. See verification.md.','external_validation_status':'external_validation_insufficient','clinical_accuracy_established':False,'model_replacement_approved':False,'canonical_profile_ids':canon,'profiles_count':len(ps),'profiles':profile_rows,'study_comparisons':rows,'synthetic_model_points':math_points,'model_internal_metrics':{name:metrics(k) for name,k in kernels.items()},'synthetic_repeated_dose_results':repeat,'history_sensitivity':histories,'weight_sensitivity':weights,'hold_sensitivity':holds,'identifiability':ident,'not_comparable_record_ids':[r['id'] for r in catalog['records'] if r['comparison_status']=='qualitative_no_error'],'protocol_deviations':[],'conclusion':'NUMERICALLY_VALID_ONLY; conditional exposed points cannot select a clinically accurate replacement'}
    (output/'analysis.json').write_text(json.dumps(result,ensure_ascii=False,indent=2,allow_nan=False)+'\n')
    (output/'candidate-parameters.json').write_text(json.dumps([{'id':p['id'],'fixed':p['fixed'],'amplitude':p['fit'].get('amplitude'),'baseline_assumption':p['baseline_pg_ml'],'status':p['fit']['status']} for p in ps],indent=2)+'\n')
    with (output/'study-comparisons.csv').open('w',newline='') as f:
        writer=csv.DictWriter(f,fieldnames=list(rows[0]));writer.writeheader();writer.writerows(rows)
    text=['# P2 frozen-protocol conditional report','','External: **external_validation_insufficient**. Clinical accuracy: **false**. No blind external data. No aggregate winner.','','| Study | Model | Baseline | Fixed observation | Prediction | Signed error |','|---|---|---:|---:|---:|---:|']
    for row in rows:
        if row['baseline_assumption_pg_ml'] in (0,24):text.append(f"| {row['study_id']} | {row['model']} | {row['baseline_assumption_pg_ml']} | {row['observed']:.3f} {row['observed_unit']} | {row['predicted']:.3f} | {row['signed_error']:.3f} |")
    text+=['','All scenarios/negative results: analysis.json (339 profiles). Canonical settings fixed before fitting. Human data are only sourced catalog records; model metrics are synthetic.','',f"Protocol {lock['protocol_sha256']}; dataset {lock['dataset_sha256']}; code {revision()}."]
    (output/'report.md').write_text('\n'.join(text)+'\n')
    manifest={f.name:guard.digest(f) for f in sorted(output.iterdir()) if f.is_file()}
    (output/'output-manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
    print(json.dumps({'output':str(output),'profiles':len(ps),'external_validation_status':result['external_validation_status'],'clinical_accuracy_established':False}))

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--inputs',type=Path,default=guard.P2,help='Frozen dataset+protocol directory')
    p.add_argument('--output',type=Path,required=True,help='New empty research result directory')
    p.add_argument('--validate',action='store_true',help='Validate hashes only; does not fit')
    args=p.parse_args()
    if args.validate:guard.check(args.inputs);print('Frozen inputs/production valid');return
    run(args.inputs,args.output)
if __name__=='__main__':main()
