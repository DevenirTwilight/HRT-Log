#!/usr/bin/env python3
"""P2-AM: conditional M2 sampling/dose-clock perturbation; NO human prediction.

Uses P2-X previously selected 15 near-optimal group-level shapes, H(1h)=1.
Only a frozen synthetic differential/sensitivity analysis, not instrument error.
"""
import argparse
import json
import math
import statistics
from pathlib import Path
import numpy as np
from p2v_continuous_fit import shape
ROOT=Path(__file__).resolve().parents[1]
TIMES=(0.25,0.5,1.0,2.0,3.0,4.0,6.0,8.0,12.0,24.0)
DELTAS=(5/60,15/60,30/60)

def selected_candidates(ds):
    if ds.get('phase')!='P2-X' or ds.get('Doll_used') is not False:raise ValueError('Unexpected source')
    best=float(ds['summary']['global_best_loss'])
    picked=[]
    for i,r in enumerate(ds['rows']):
        if r['score']<=best+0.1+1e-12:
            picked.append({'candidate_id':f'p2x-{i}','params':r['parameter']})
    if len(picked)!=15:raise ValueError('unexpected candidate set')
    return picked

def perturb(param,t,delta):
    if t-delta<0:raise ValueError('negative time not valid')
    if t==1.0:
        # In this comparison one-hour observation is the mandatory calibration anchor.
        pass
    values=np.array([t,t-delta,t+delta,1.,1.-delta,1.+delta])
    h=shape(values,param)
    if not np.all(np.isfinite(h)) or h[3]<=0:raise ValueError('invalid response')
    ref=h[0]/h[3]
    sample_error=max(abs(h[1]/h[3]-ref),abs(h[2]/h[3]-ref))
    # Single common shift of actual dose clock affects BOTH prediction and anchor.
    common_error=max(abs(h[1]/h[4]-ref),abs(h[2]/h[5]-ref))
    return float(sample_error),float(common_error)

def analyze(ds):
    candidates=selected_candidates(ds)
    blocks=[]
    for delta in DELTAS:
        for t in TIMES:
            if t<delta:continue
            sensitivity=[perturb(candidate['params'],t,delta) for candidate in candidates]
            sample=[x[0] for x in sensitivity]
            common=[x[1] for x in sensitivity]
            blocks.append({'sample_hour':t,'clock_uncertainty_min':int(round(delta*60)),
               'deltaH_sample_clock_median_over15':float(statistics.median(sample)),
               'deltaH_sample_clock_max_over15':float(max(sample)),
               'deltaH_shared_dose_clock_median_over15':float(statistics.median(common)),
               'deltaH_shared_dose_clock_max_over15':float(max(common)),
               'sample_time_plus_minus_not_probabilistic':True})
    return {'phase':'P2-AM','kind':'hypothetical_time_documentation_sensitivity',
         'source':'Frozen P2-X 15 conditional candidates; unchanged 1mg/1h-normalized M2 mathematical kernel',
         'n_models':len(candidates),'candidate_ids':[c['candidate_id'] for c in candidates],
         'sampling_times_h':list(TIMES),'perturbation_minutes':[5,15,30],
         'interpretation':'absolute dimensionless differences, not relative percent or pg/mL, no measured timestamp error',
         'sample_jitter_rule':'shift evaluated sample only; calibration anchor fixed at 1h',
         'dose_clock_rule':'shift dose time relative to every sample, including the 1h anchor, then renormalize',
         'cannot_establish_actual_timestamp_quality':True,
         'rows':blocks}

def main():
    p=argparse.ArgumentParser();p.add_argument('--input',type=Path,default=ROOT/'inputs/p2x-conditional-ensemble-results.json');p.add_argument('--output',type=Path,default=ROOT/'results/p2am-clock-sensitivity.json');a=p.parse_args()
    ds=json.loads(a.input.read_text(encoding='utf-8'))
    res=analyze(ds);a.output.parent.mkdir(parents=True,exist_ok=True)
    a.output.write_text(json.dumps(res,indent=2,sort_keys=True,ensure_ascii=False)+'\n',encoding='utf-8')
    print('P2-AM model clock sensitivity:',len(res['rows']),'scenarios on',res['n_models'],'M2 candidates')

if __name__=='__main__':main()
