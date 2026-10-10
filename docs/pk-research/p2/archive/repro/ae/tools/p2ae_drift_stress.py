#!/usr/bin/env python3
"""P2-AE sensitivity: continuous hypothetical time-drifting baseline, no patient data.

At each trial slow-input rate, profile independently nonnegative baseline and
fast/slow gains; baseline slope between [-max_drift,+max_drift] pg/mL/hour.
Constrain baseline nonnegative at both endpoints (and hence all intermediate times).
"""
from __future__ import annotations
import argparse,json
from pathlib import Path
import numpy as np
from scipy.optimize import minimize_scalar,nnls
from p2ae_tail_gain_profile import ROOT,select,PROTOCOLS,RATE_LOW,RATE_HIGH,sigma,anchor_total,components,collate

DRIFT_BOUND=0.1 # HYPOTHETICAL pg/mL/h, NOT observed endogenous fluctuation

def fit(t,y,sd,f,s,d):
    b_floor=max(0.,-d*float(t[-1]))
    H=np.column_stack((np.ones(len(t)),f,s))
    pars,_=nnls(H/sd[:,None],(y-d*t-b_floor)/sd)
    fitted=H@pars+b_floor+d*t
    assert (pars>=0).all() and pars[0]+b_floor+d*t[-1]>=-1e-8
    return float(np.linalg.norm((fitted-y)/sd)),float(pars[0]+b_floor),float(pars[1]),float(pars[2])

def profile_rate(t,y,sd,p,rate,limit=DRIFT_BOUND):
    f,s=components(t,p,rate)
    def objective(d):return fit(t,y,sd,f,s,d)[0]**2
    if limit==0:return fit(t,y,sd,f,s,0)[0],0.
    opt=minimize_scalar(objective,method='bounded',bounds=(-limit,limit),options={'xatol':2e-5,'maxiter':32})
    draws=[(-limit,objective(-limit)),(0.,objective(0.)),(limit,objective(limit)),(float(opt.x),float(opt.fun))]
    d,l=min(draws,key=lambda v:v[1]);return float(np.sqrt(max(l,0))),float(d)

def one(r,times,sd_floor=3.,limit=DRIFT_BOUND,n_grid=121):
    t=np.array(times,float);y=anchor_total(t,r);sd=sigma(y,sd_floor)
    truth=float(r['parameter']['k_slow_per_h'])
    grid=np.unique(np.r_[np.geomspace(RATE_LOW,RATE_HIGH,n_grid),truth])
    errs=[];slopes=[]
    for rate in grid:
        score,slope=profile_rate(t,y,sd,r['parameter'],rate,limit)
        errs.append(score);slopes.append(slope)
    errs=np.array(errs)
    assert errs.min()<1e-5
    feasible=grid[errs<=2.+1e-10]
    return dict(id=r['id'],rate_true=truth,n_usable=len(t),rate_low=float(feasible.min()),rate_high=float(feasible.max()),
        rate_ratio=float(feasible.max()/feasible.min()),chosen_drift_at_max=float(slopes[np.argmax(grid)]),
        grid_n=len(grid),n_feasible=len(feasible))

def run(data,limits=(0.,.05,.1)):
    rows=select(data)
    results={}
    for name in ['through24_with_pre','through48_with_pre']:
      for lim in limits:
        scenarios=[one(r,PROTOCOLS[name],limit=lim) for r in rows]
        ratios=np.array([q['rate_ratio'] for q in scenarios])
        results[f'{name}__drift_{lim}']=dict(summary=dict(n=15,median_rate_ratio=float(np.median(ratios)),
            n_ge_4x=int(np.sum(ratios>=4)),n_ge_10x=int(np.sum(ratios>=10)),min_rate_ratio=float(min(ratios)),max_rate_ratio=float(max(ratios))),per_candidate=scenarios)
    return dict(phase='P2-AE-drift-stress',hypothetical=True,not_estimated_endogenous_drift=True,
        only_synthetic_model_truth=True,noise_sigma='max(3 pg/mL, 0.125*synthetic total E2)',
        background_model='b(t)=b0+drift*t; b(t)>=0 on [0,last_sample]; drift in symmetric HYPOTHETICAL range',
        nuisance='b0>=0, fast gain>=0, slow gain>=0, drift signed bounded',rate_grid=[RATE_LOW,RATE_HIGH],
        feasible_threshold='distance<=2 NOT chi square CI or clinical coverage',source='P2-X exposed fits',results=results)

def main():
    p=argparse.ArgumentParser();p.add_argument('--output',type=Path,default=ROOT/'data'/'p2ae-drift-sensitivity.json');a=p.parse_args()
    if a.output.exists():raise FileExistsError('no replacement of old results')
    d=run(json.loads((ROOT/'data'/'p2x-conditional-ensemble-results.json').read_text()))
    a.output.write_text(json.dumps(d,ensure_ascii=False,sort_keys=True,indent=2)+'\n')
    for k,v in d['results'].items():print(k,v['summary'])
if __name__=='__main__':main()
