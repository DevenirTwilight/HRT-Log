#!/usr/bin/env python3
"""Synthetic precision sensitivity with free baseline, fast/slow gains, and slow rate."""
from __future__ import annotations
import argparse,json
from itertools import combinations
from pathlib import Path
import numpy as np
from p2ae_tail_gain_profile import ROOT,DATA,PROTOCOLS,select,anchor_total,sigma
from p2ae_cross_model_profiles import competitor_distance

SCENARIOS=[(3.,.125),(1.,.05),(1.,.02),(.5,.01)]

def run(data):
    r=select(data);t=np.array(PROTOCOLS['through48_with_pre'],float)
    y=[anchor_total(t,z) for z in r]
    out={}
    for floor,cv in SCENARIOS:
        count=0;dist=[]
        for i,j in combinations(range(len(r)),2):
            d1,_=competitor_distance(y[i],t,sigma(y[i],floor,cv),r[j])
            d2,_=competitor_distance(y[j],t,sigma(y[j],floor,cv),r[i])
            ds=min(d1,d2);dist.append(ds);count+=int(ds>=2.)
        key=f'sd_floor_{floor}_fractional_cv_{cv}'
        out[key]=dict(threshold2_count=count,median_distance=float(np.median(dist)),max_distance=float(np.max(dist)),p90_distance=float(np.quantile(dist,.9)))
    return dict(phase='P2-AE-precision-stress',all_noise_cases_hypothetical=True,not_assay_claim=True,
        has_new_human_data=False,not_medical_or_sample_plan=True,rate_grid=[.025,.8],results=out)

def main():
    p=argparse.ArgumentParser();p.add_argument('--out',type=Path,default=ROOT/'data'/'p2ae-precision-sensitivity.json');a=p.parse_args()
    if a.out.exists():raise FileExistsError('refusing overwrite')
    d=run(json.loads(DATA.read_text()))
    a.out.write_text(json.dumps(d,indent=2,sort_keys=True)+'\n')
    for k,v in d['results'].items():print(k,v)
if __name__=='__main__':main()
