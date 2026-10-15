#!/usr/bin/env python3
"""P2-AN measurement/error/missingness audit: SYNTHETIC methodology only.

No HRT Log production PK function or independent human measurement is used here.
Frozen P2-AM protocol is imported as a read-only reference; no GitHub access.
"""
from __future__ import annotations
import csv
import hashlib
import json
import math
import random
import statistics
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
TIMEPOINTS=(2.,3.,4.,6.,8.)
# MOCK labels: These are deliberately fictitious paired prediction errors,
# NOT fitted P2-X M2 and NOT the HRT Log legacy model.
DEMO_A_ERRORS=(0.04,0.04,0.04,0.01,0.01)
DEMO_B_ERRORS=(0.01,0.01,0.02,0.06,0.07)


def ratio(base, anchor, target):
    d=anchor-base
    if not (math.isfinite(d) and d>0 and all(math.isfinite(x) for x in (base,anchor,target))):
        raise ValueError('invalid ratio/anchor denominator')
    return (target-base)/d


def ratio_covariance(base,anchor,followups, sd_base,sd_anchor,sd_followups):
    """First order delta-method covariance; independent raw assay noises only."""
    if len(followups)!=len(sd_followups) or not followups:
        raise ValueError('followup size mismatch')
    if not all(math.isfinite(s) and s>=0 for s in [sd_base,sd_anchor,*sd_followups]):
        raise ValueError('invalid SD')
    d=anchor-base
    if d<=0 or not math.isfinite(d):raise ValueError('nonpositive anchor increment')
    rr=[ratio(base,anchor,y) for y in followups]
    n=len(rr)
    return [[(int(i==j)*sd_followups[i]**2 + rr[i]*rr[j]*sd_anchor**2
              + (rr[i]-1)*(rr[j]-1)*sd_base**2)/d**2
             for j in range(n)] for i in range(n)]


def mc_ratio_covariance(base,anchor,followups,sdb,sda,sdy,seed=12497,reps=75000):
    rng=random.Random(seed)
    sample=[]
    for _ in range(reps):
        bb=base+rng.gauss(0,sdb)
        aa=anchor+rng.gauss(0,sda)
        ys=[y+rng.gauss(0,s) for y,s in zip(followups,sdy)]
        if aa-bb<=0: continue
        sample.append([(y-bb)/(aa-bb) for y in ys])
    means=[statistics.fmean(x[j] for x in sample) for j in range(len(followups))]
    cov=[[statistics.fmean((x[i]-means[i])*(x[j]-means[j]) for x in sample)
          for j in range(len(followups))] for i in range(len(followups))]
    return cov,len(sample)


def construct_cohort(n=120,seed=20261010):
    rng=random.Random(seed)
    cohort=[]
    for i in range(n):
        # Synthetic truth; individual-level correlated shape noise shared by all samples.
        eta=rng.gauss(0,.030)
        observed=[]
        a=[];b=[]
        for t,e_a,e_b in zip(TIMEPOINTS,DEMO_A_ERRORS,DEMO_B_ERRORS):
            truth=math.exp(-.255*(t-1))
            measured=truth+eta + rng.gauss(0,.007)
            observed.append(measured)
            a.append(truth+e_a)
            b.append(truth+e_b)
        cohort.append({'id':f'SYNTH-{i:03d}','study':('SYNTH_A' if i<60 else 'SYNTH_B'),
                       'observed':observed,'mock_A':a,'mock_B':b})
    return cohort


def retain_mask(p,kind,seed):
    if kind=='complete':return [True]*len(TIMEPOINTS)
    rng=random.Random(seed)
    if kind=='drop_late_evaluations':
        return [True,True,True,rng.random()>.85,rng.random()>.85]
    if kind=='mcar':return [rng.random()>.35 for _ in TIMEPOINTS]
    if kind=='signal_dependent_mnar':
        return [rng.random()>(.87 if y<.35 else .08) for y in p['observed']]
    raise ValueError('unknown missingness process')


def evaluate(cohort,mechanism,seed=7021):
    ppl=[]; all_point_diffs=[]; by_t=[]
    for k,p in enumerate(cohort):
        mask=retain_mask(p,mechanism, seed+1009*k)
        indices=[i for i,on in enumerate(mask) if on]
        if len(indices)<2:continue
        ea=[abs(p['mock_A'][i]-p['observed'][i]) for i in indices]
        eb=[abs(p['mock_B'][i]-p['observed'][i]) for i in indices]
        differences=[v-u for u,v in zip(ea,eb)]
        ppl.append({'id':p['id'],'study':p['study'], 'n':len(indices),
                    'delta':statistics.fmean(differences),
                    'point_deltas':differences,'index':indices})
        all_point_diffs+=differences
        by_t+=indices
    return {'mechanism':mechanism,'subjects':len(ppl),'excluded_subjects':len(cohort)-len(ppl),
            'scored_points':len(all_point_diffs),
            'subjects_equal_weight_delta_mockB_minus_mockA':statistics.fmean(z['delta'] for z in ppl) if ppl else None,
            'points_weighted_delta':statistics.fmean(all_point_diffs) if all_point_diffs else None,
            'retained_by_time':{str(TIMEPOINTS[i]):by_t.count(i) for i in range(len(TIMEPOINTS))},
            'people':ppl}



def partial_identification_interval(cohort,mechanism,seed=7021):
    """Sharp deterministic bounds for FULL five-time paired MAE difference.

    Missing target ratios are assumed only to be nonnegative; no imputation or
    stochastic missingness assumption is made. Fixed known model predictions.
    For each absent y, |B-y|-|A-y| is in [-|B-A|,+|B-A|].
    """
    low=[];high=[];observed=[];missing=[]
    for k,p in enumerate(cohort):
        mask=retain_mask(p,mechanism,seed+1009*k)
        lo=hi=0.
        for i,keep in enumerate(mask):
            a=p['mock_A'][i]; b=p['mock_B'][i]
            if keep:
                d=abs(b-p['observed'][i])-abs(a-p['observed'][i])
                lo+=d;hi+=d;observed.append(d)
            else:
                distance=abs(b-a)
                lo-=distance;hi+=distance;missing.append(distance)
        low.append(lo/len(TIMEPOINTS));high.append(hi/len(TIMEPOINTS))
    return {'full_schedule_delta_lower':statistics.fmean(low),
            'full_schedule_delta_upper':statistics.fmean(high),
            'missing_evaluations':len(missing),'observed_evaluations':len(observed),
            'identifies_sign':('A_better' if statistics.fmean(low)>0 else
                              'B_better' if statistics.fmean(high)<0 else 'UNDETERMINED'),
            'assumptions':'Pointwise absolute-error triangle inequality, fixed positive model predictions, no assumptions about missing true observations; bounds are arithmetic not probabilistic.'}

def percentile(seq,q):
    z=sorted(seq);j=(len(z)-1)*q
    lo=int(j);hi=min(lo+1,len(z)-1)
    return z[lo]+(z[hi]-z[lo])*(j-lo)


def bootstrap_subject_ci(people,n=5000,seed=527):
    if len(people)<2:raise ValueError('insufficient subjects')
    rng=random.Random(seed);m=len(people)
    vals=[z['delta'] for z in people]
    boot=[statistics.fmean(vals[rng.randrange(m)] for _ in range(m)) for _ in range(n)]
    return [percentile(boot,.025),percentile(boot,.975)]


def bootstrap_points_naive_ci(people,n=5000,seed=527):
    # WRONG if treated as independent; intentionally computed as negative control.
    rng=random.Random(seed)
    vals=[e for p in people for e in p['point_deltas']];m=len(vals)
    boot=[statistics.fmean(vals[rng.randrange(m)] for _ in range(m)) for _ in range(n)]
    return [percentile(boot,.025),percentile(boot,.975)]


def stratified_subject_ci(people,n=5000,seed=527):
    # Preserve study composition, randomize participants within study only.
    rng=random.Random(seed)
    studies=sorted(set(z['study'] for z in people))
    groups=[[z['delta'] for z in people if z['study']==name] for name in studies]
    total=sum(len(g) for g in groups)
    if min(map(len,groups))<2:raise ValueError('stratum too small')
    boot=[]
    for _ in range(n):
        score=sum(sum(g[rng.randrange(len(g))] for _ in g) for g in groups)/total
        boot.append(score)
    return [percentile(boot,.025),percentile(boot,.975)]


def affine_assay_invariance():
    b,a,ys=25.,225.,[145.,85.,45.]
    org=[ratio(b,a,y) for y in ys]
    affine=[ratio(.82*b+14,.82*a+14,.82*y+14) for y in ys]
    def curved(y):return .82*y+.001*y*y
    nonlinear=[ratio(curved(b),curved(a),curved(y)) for y in ys]
    # Illustrative unmodeled time-varying additive sample shift, not a documented assay bias.
    timedrift=[ratio(b,a,y+8) for y in ys]
    return {'baseline_pg_ml':b,'anchor_pg_ml':a,'followups_pg_ml':ys,
            'original_ratios':org, 'affine_assay_ratios':affine,
            'nonlinear_assay_ratios':nonlinear,'time_variable_plus8pg_ratios':timedrift,
            'max_affine_abs_delta':max(abs(x-y) for x,y in zip(org,affine)),
            'max_nonlinear_abs_delta':max(abs(x-y) for x,y in zip(org,nonlinear)),
            'max_time_variable_abs_delta':max(abs(x-y) for x,y in zip(org,timedrift)),
            'NOTE':'All transforms are intentionally hypothetical. Constant offset or linear scaling cancels only if shared across baseline, anchor and evaluation samples.'}


def covariance_experiment():
    b,a,ys=25.,225.,[145.,85.,45.]
    sdb,sda,sdy=5.,5.,[5.,5.,5.]
    delta=ratio_covariance(b,a,ys,sdb,sda,sdy)
    mc,n=mc_ratio_covariance(b,a,ys,sdb,sda,sdy)
    def cor(c,i,j):return c[i][j]/math.sqrt(c[i][i]*c[j][j])
    maxrel=max(abs(mc[i][j]-delta[i][j])/max(1e-12,abs(delta[i][j])) for i in range(3) for j in range(3))
    return {'scenario':{'baseline':b,'anchor':a,'followup':ys,'sd_base':sdb,'sd_anchor':sda,'sd_followup':sdy},
            'expected_ratio':[ratio(b,a,y) for y in ys],
            'covariance_delta_approx':delta,'covariance_monte_carlo':mc,
            'monte_carlo_valid_samples':n,'max_elementwise_relative_covariance_delta_mc':maxrel,
            'correlation_r2_r4_delta':cor(delta,0,1),
            'correlation_r2_r4_monte_carlo':cor(mc,0,1),
            'delta_assumptions':'Independent additive approximately Gaussian raw concentration errors; same baseline and anchor reused for all ratios. Not an observed assay covariance matrix.'}



def weak_anchor_ratio_sensitivity(seed=302211, trials=100000):
    """Gaussian error thought experiment; illustrate denominator instability.
    3 x joint SD gate is intentionally NOT claimed to guarantee accurate ratios.
    Distribution tails of ratios can have undefined moments: report quantiles,
    not an estimated ratio SD as an uncertainty parameter.
    """
    outcome={}
    for d in (23.,35.,200.):
        rng=random.Random(seed)
        base=25.;anchor=base+d;follow=base+.45*d
        ratios=[];gate_ratios=[];crossings=0
        for _ in range(trials):
            bb=base+rng.gauss(0,5.)
            aa=anchor+rng.gauss(0,5.)
            yy=follow+rng.gauss(0,5.)
            if aa<=bb:
                crossings+=1
                continue
            value=(yy-bb)/(aa-bb)
            ratios.append(value)
            if aa-bb>3*math.sqrt(50.):gate_ratios.append(value)
        k=str(int(d))
        outcome[k]={'denominator_true_pg_ml':d,'signal_to_joint_sd':d/math.sqrt(50.),
                    'passes_p2am_3sigma_rule':d>3*math.sqrt(50.),
                    'crossed_or_zero_denominator_fraction':crossings/trials,
                    'accepted_ratio_2_5_percentile':percentile(ratios,.025),
                    'accepted_ratio_median':percentile(ratios,.5),
                    'accepted_ratio_97_5_percentile':percentile(ratios,.975),
                    'accepted_ratio_gt2_fraction':sum(x>2 for x in ratios)/len(ratios),
                    'accepted_ratio_abs_gt2_fraction':sum(abs(x)>2 for x in ratios)/len(ratios),
                    'n_accepted':len(ratios),
                    'observed_p2am_quality_gate_retention_fraction':len(gate_ratios)/trials,
                    'observed_gate_ratio_2_5_percentile':percentile(gate_ratios,.025),
                    'observed_gate_ratio_97_5_percentile':percentile(gate_ratios,.975)}
    return {'note':'Fully hypothetical independent Gaussian errors of 5 pg/mL on baseline, anchor and evaluation. `passes_p2am_3sigma_rule` describes the UNOBSERVED true signal only. Actual P2-AM gate applies to each noisy OBSERVED denominator; conditional gate percentiles and retention are shown separately. Not real instrument calibration or clinical CI.',
            'scenarios':outcome}

def reference_gate():
    from p2am_protocol_reference import load_and_score
    x=load_and_score(ROOT/'inputs')
    assert x['origin']=='SYNTHETIC' and x['is_clinical_validation'] is False
    return {'origin':x['origin'],'subjects':x['n_subjects'],'scored_samples':x['n_scored_samples'],
            'paired_delta_m2_demo_minus_legacy_demo':x['paired_mae_delta_m2_minus_legacy'],
            'absolute_pgml_gate':x['absolute_pgml_gate'],
            'clinical_validation':x['is_clinical_validation'],
            'IMPORTANT':'These demo prediction functions are NOT HRT Log production engines.'}


def main():
    cohort=construct_cohort()
    missing={}
    for k in ('complete','mcar','drop_late_evaluations','signal_dependent_mnar'):
        out=evaluate(cohort,k)
        ppl=out.pop('people')
        out['paired_subject_cluster_bootstrap_95_percent_ILLUSTRATIVE']=bootstrap_subject_ci(ppl)
        out['stratified_subject_cluster_bootstrap_95_percent_ILLUSTRATIVE']=stratified_subject_ci(ppl)
        out['INVALID_independent_point_bootstrap_95_percent_ILLUSTRATIVE']=bootstrap_points_naive_ci(ppl)
        out['partial_identification_no_imputation']=partial_identification_interval(cohort,k)
        missing[k]=out
    result={'phase':'P2-AN','origin':'SYNTHETIC_ONLY','is_clinical_validation':False,
            'model_names':'MOCK_A / MOCK_B are made-up demonstration functions, NOT real Legacy/M2 engines',
            'covariance':covariance_experiment(),'assay_affine_and_nonlinearity':affine_assay_invariance(),
            'weak_anchor_denominator_sensitivity':weak_anchor_ratio_sensitivity(),
            'missingness':missing,'p2am_reference_replayed':reference_gate(),
            'gates':{'synthetic_math':'RUNNABLE','missingness_mechanism':'DEMONSTRATED_NOT_IDENTIFIED',
                     'external_human_shape':'NO_EXTERNAL_DATA', 'absolute_pgml':'BLOCKED',
                     'long_tail':'BLOCKED','production_replacement':'NOT_AUTHORIZED'},
            'not_for':['medical decisions','individual pg/mL prediction','P2-X model ranking','clinical uncertainty quantification']}
    p=ROOT/'results'/'p2an-analysis.json'
    p.parent.mkdir(exist_ok=True,parents=True)
    p.write_text(json.dumps(result,ensure_ascii=False,sort_keys=True,indent=2)+'\n',encoding='utf-8')
    print('P2-AN wrote',p)
    for k,v in missing.items():print(k,v['subjects'],v['subjects_equal_weight_delta_mockB_minus_mockA'])
    return result

if __name__=='__main__':main()
