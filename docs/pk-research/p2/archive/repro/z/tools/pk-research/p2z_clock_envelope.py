#!/usr/bin/env python3
"""P2-Z: clock uncertainty and marginal moment sensitivity for PRE-EXPOSED P2-X M2 models.

Research only. Synthesized clock grids ARE NOT known dosing times or priors.
No Doll calibration, individual data, clinical coverage, or production changes.
"""
from __future__ import annotations
import argparse, csv, json, math
from pathlib import Path
import numpy as np
from scipy.stats import qmc
import p2v_continuous_fit as pk

ROOT=Path(__file__).resolve().parents[2]
SOURCE=ROOT/'docs/pk-research/p2/p2x-conditional-ensemble-results.json'
YAISH={'pre_pmol_L':204.5,'post_pmol_L':1994.,'pre_SD_pmol_L':63.5,
       'post_SD_pmol_L':1457.,'dose_mg':0.5,'nominal_post_time_h':1.5}


def clock_design(log2_n=11):
    """Deterministic coverage of illustrative daytime gaps, NOT actual exposure distribution.

    Four doses/day at [0,g1,g1+g2,g1+g2+g3].
    Daytime intervals bounded 2.5--6 h, giving overnight gaps 6--16.5 h.
    This is deliberately broad; nothing in the paper verifies these intervals.
    """
    if not 1<=log2_n<=15:raise ValueError('log2_n outside safe bound')
    x=qmc.Sobol(d=3,scramble=False).random_base2(log2_n)
    gaps=2.5+3.5*x
    slots=np.column_stack([np.zeros(len(x)),gaps[:,0],
                           gaps[:,0]+gaps[:,1],np.sum(gaps,axis=1)])
    fixed=np.array([[0,6,12,18],[0,5,10,15],[0,4,9,14],[0,4,8,12],[0,3,6,10]],float)
    return np.vstack((fixed,slots)), ['fixed_q6h','fixed_night9h','fixed_night10h','fixed_night12h','fixed_night14h']+['sobol_'+str(i) for i in range(len(x))]


def periodic_response(param,clock,post_h=1.5,days=120):
    """Long-run clock response at predose t=0 and after first dose at post_h.

    Background and effective gain not fit here. Upstream time responses normalized
    to a hypothetical single-dose h(1h), which cancels in mean ratios.
    """
    clock=np.asarray(clock,float)
    if clock.ndim!=2 or clock.shape[1]!=4 or np.any(clock[:,0]!=0) or np.any(np.diff(clock,axis=1)<=0) or np.any(clock[:,-1]>=24):
        raise ValueError('invalid clock')
    if not 0<post_h<np.min(clock[:,1]):raise ValueError('post draw after first dose / before second')
    if not isinstance(days,int) or not 20<=days<=365:raise ValueError('days out of validated range')
    total_pre=np.zeros(len(clock))
    total_post=np.full(len(clock),float(pk.shape(np.array([post_h]),param)[0]))
    # Day-chunk to avoid huge allocations and floating point non-determinism
    for first in range(1,days+1,20):
        d=np.arange(first,min(days+1,first+20),dtype=float)
        age=24*d[None,None,:]-clock[:,:,None]
        total_pre+=np.sum(pk.shape(age,param),axis=(1,2))
        total_post+=np.sum(pk.shape(age+post_h,param),axis=(1,2))
    return total_pre,total_post


def feasibility(pre_curve,post_curve,pre=204.5,post=1994.):
    pre_curve=np.asarray(pre_curve);post_curve=np.asarray(post_curve)
    if post<=pre or pre<0:raise ValueError('invalid published mean comparison')
    diff=post_curve-pre_curve
    ratio=np.divide(pre_curve,diff,out=np.full(pre_curve.shape,np.inf),where=diff>0)
    target=pre/(post-pre)
    eligible=(diff>0)&(ratio<=target+1e-12)
    return eligible,ratio,target


def day_convergence(param,clock):
    a1,b1=periodic_response(param,clock,days=120)
    a2,b2=periodic_response(param,clock,days=180)
    den=max(1e-12, float(np.max(np.abs(np.r_[a2,b2]))))
    return max(float(np.max(np.abs(a1-a2))),float(np.max(np.abs(b1-b2))))/den


def analyze(log2_n=11,extra_pseudoloss=0.10, times=(1.25,1.5,1.75)):
    pack=json.loads(SOURCE.read_text())
    rows=pack['rows'];best=min(r['score'] for r in rows)
    keep=[(i,r) for i,r in enumerate(rows) if r['score']<=best+extra_pseudoloss+1e-10]
    clocks,clock_ids=clock_design(log2_n)
    n_clock=len(clocks);ns=np.array([8,11,14])
    modes={
      'exact_means': (YAISH['pre_pmol_L'],YAISH['post_pmol_L']),
      'post_minus_1_se_n11': (YAISH['pre_pmol_L'],YAISH['post_pmol_L']-YAISH['post_SD_pmol_L']/math.sqrt(11)),
      'post_plus_1_se_n11': (YAISH['pre_pmol_L'],YAISH['post_pmol_L']+YAISH['post_SD_pmol_L']/math.sqrt(11)),
      'pre_plus_1_se_n11': (YAISH['pre_pmol_L']+YAISH['pre_SD_pmol_L']/math.sqrt(11),YAISH['post_pmol_L']),
    }
    model_results=[];record=[]
    scenario_counts={str(t):{k:[] for k in modes} for t in times}
    for mid,r in keep:
        p=r['parameter'];result={'fit_id':mid,'score':r['score'],'n_fast':r['n_fast'],
                'assumed_price_baseline_pg_ml':r['price_fixed_baseline_pg_ml'],
                'q24_index':r['trough_index_per_1mg_q6_q12_q24']['24']}
        for t in times:
            a,b=periodic_response(p,clocks,post_h=t)
            for mode,(pre,post) in modes.items():
                good,ratio,target=feasibility(a,b,pre,post)
                s= scenario_counts[str(t)][mode];s.append(good)
                if mode=='exact_means':
                    result['at_'+str(t)+'_nominal_fraction_design_grid']=float(np.mean(good[5:]))
                    result['at_'+str(t)+'_any_feasible']=bool(np.any(good))
                    result['at_'+str(t)+'_all_feasible']=bool(np.all(good))
                    result['at_'+str(t)+'_night_gap_min_feasible_h']=float(np.min(24-clocks[good,-1])) if np.any(good) else None
                    result['at_'+str(t)+'_night_gap_max_feasible_h']=float(np.max(24-clocks[good,-1])) if np.any(good) else None
                    result['at_'+str(t)+'_fixed_clock_status']={clock_ids[i]:bool(good[i]) for i in range(5)}
                    # Identify minimum overnight gap that satisfies mean constraint, NOT a profile CI.
                    if abs(t-1.5)<1e-12:
                        for ci in range(5):
                            record.append({'fit_id':mid,'clock':clock_ids[ci], 'night_gap_h':float(24-clocks[ci,-1]),
                                           'shape_ratio':float(ratio[ci]),'observed_mean_ratio':target,
                                           'eligible_nominal':bool(good[ci]),'q24_index':result['q24_index']})
        model_results.append(result)
    summary={}
    for t in times:
        for mode in modes:
            f=np.stack(scenario_counts[str(t)][mode])
            keys=f'{t}h_{mode}'
            # EACH column = illustrative clock. Counts over models, not probability.
            summary[keys]={
              'model_count':len(keep),'clock_count':n_clock,
              'models_feasible_any_clock':int(np.sum(np.any(f,axis=1))),
              'models_feasible_all_clocks':int(np.sum(np.all(f,axis=1))),
              'fixed_clocks_feasible_counts':{clock_ids[j]:int(np.sum(f[:,j])) for j in range(5)},
              'grid_feasible_cells':int(np.sum(f[:,5:])),
              'total_grid_cells':int(f[:,5:].size),
              'fraction_of_arbitrary_grid_cells':float(np.mean(f[:,5:])),
            }
            if mode=='exact_means' and abs(t-1.5)<1e-12:
                selected=[keep[i][1]['trough_index_per_1mg_q6_q12_q24']['24'] for i in range(len(keep)) if np.any(f[i])]
                summary[keys]['eligible_any_clock_q24_index_range']=[float(min(selected)),float(max(selected))] if selected else None
    # Lower/upper possible SD of paired difference for the SAME cohort, unknown corr
    sdlo=abs(YAISH['post_SD_pmol_L']-YAISH['pre_SD_pmol_L'])
    sdhi=YAISH['post_SD_pmol_L']+YAISH['pre_SD_pmol_L']
    return {
      'phase':'P2-Z', 'status':'EXPOSED_EXPLORATORY_SCHEDULE_ENVELOPE_NOT_PREDICTIVE',
      'input_p2x_candidate_rows':len(rows),'retained_pseudo_loss_delta':extra_pseudoloss,
      'retained_candidate_models':len(keep),'design_grid_sobol_count':n_clock-5,
      'design_clocks_not_observed_not_prior_or_posterior':True,
      'design_interdose_day_gap_h':[2.5,6], 'design_overnight_gap_h':[6,16.5],
      'sampling_time_range_h':list(times),
      'YAISH':YAISH,
      'YAISH_paired_difference_SD_possible_range_assuming_common_n_and_same_people':[sdlo,sdhi],
      'YAISH_paired_mean_difference_SE_conditional_on_n':{str(n):[sdlo/math.sqrt(n),sdhi/math.sqrt(n)] for n in ns},
      'YAISH_exact_pre_post_equal_population_and_assay_assumed_not_verified':True,
      'Yaish_event_clock_unavailable':True,
      'YAISH_SD_ambiguity_not_converted_to_clinical_likelihood':True,
      'old_P2X_training_exposed':True,'data_are_summary_and_digitized_not_IPD':True,
      'Doll_used':False,'production_untouched':True,
      'clinical_validation_established':False,
      'models':model_results,'fixed_clock_records':record,'summaries':summary}


def main():
    ap=argparse.ArgumentParser()
    ap.add_argument('--out',required=True)
    ap.add_argument('--csv',required=True)
    ap.add_argument('--log2n',type=int,default=11)
    args=ap.parse_args()
    if Path(args.out).exists() or Path(args.csv).exists():raise FileExistsError('No overwrite')
    res=analyze(log2_n=args.log2n)
    op=Path(args.out);cp=Path(args.csv);op.parent.mkdir(parents=True,exist_ok=True);cp.parent.mkdir(parents=True,exist_ok=True)
    op.write_text(json.dumps(res,indent=2,ensure_ascii=False,allow_nan=False)+'\n')
    with cp.open('w',newline='') as f:
        w=csv.DictWriter(f,fieldnames=['fit_id','clock','night_gap_h','shape_ratio','observed_mean_ratio','eligible_nominal','q24_index'])
        w.writeheader();w.writerows(res['fixed_clock_records'])
    print(json.dumps({'tested_models':res['retained_candidate_models'], 'clocks':res['design_grid_sobol_count'],
      '90_nominal':res['summaries']['1.5h_exact_means']},ensure_ascii=False))
if __name__=='__main__':main()
