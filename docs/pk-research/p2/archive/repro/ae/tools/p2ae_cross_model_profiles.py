#!/usr/bin/env python3
"""P2-AE cross-candidate model challenge with nuisance reoptimization.
Frozen Price-anchored, exposed P2-X *synthetic* truths, NOT external validation.
"""
from __future__ import annotations
import argparse,json
from pathlib import Path
from itertools import combinations
import numpy as np
from scipy.optimize import nnls
from p2ae_tail_gain_profile import ROOT, DATA, PROTOCOLS, RATE_LOW, RATE_HIGH,select,anchor_total,sigma,components,profile_one

MODES=('frozen_entire_shape','free_fast_slow_gains_fixed_ks','free_gains_and_slow_rate')

def competitor_distance(truth_y,t,sd,competitor,mode='free_gains_and_slow_rate',n_grid=121):
    p=competitor['parameter']
    if mode=='frozen_entire_shape':
        from p2v_continuous_fit import shape
        cols=shape(t,p)[:,None]
        mat=np.column_stack((np.ones(len(t)),cols))
        coeff,_=nnls(mat/sd[:,None],truth_y/sd)
        return float(np.linalg.norm((mat@coeff-truth_y)/sd)),float(p['k_slow_per_h'])
    if mode=='free_fast_slow_gains_fixed_ks':grid=np.array([float(p['k_slow_per_h'])])
    elif mode=='free_gains_and_slow_rate':grid=np.unique(np.r_[np.geomspace(RATE_LOW,RATE_HIGH,n_grid),p['k_slow_per_h']])
    else:raise ValueError('invalid mode')
    best=(float('inf'),None)
    for ks in grid:
        f,s=components(t,p,float(ks))
        mat=np.column_stack((np.ones(len(t)),f,s))
        coeff,_=nnls(mat/sd[:,None],truth_y/sd)
        v=float(np.linalg.norm((mat@coeff-truth_y)/sd))
        if v<best[0]:best=(v,float(ks))
    return best

def run(data):
    rows=select(data)
    results={}
    for name in ('through24_no_pre','through24_with_pre','through48_with_pre'):
        t=np.array(PROTOCOLS[name],float)
        y=[anchor_total(t,r) for r in rows]
        sds=[sigma(yi) for yi in y]
        per_pair=[]
        for i,j in combinations(range(len(rows)),2):
            rec=dict(candidate_a=rows[i]['id'],candidate_b=rows[j]['id'])
            for mode in MODES:
                dij,kj=competitor_distance(y[i],t,sds[i],rows[j],mode)
                dji,ki=competitor_distance(y[j],t,sds[j],rows[i],mode)
                rec[mode]=dict(conservative_distance=min(dij,dji),direction_A_to_B=dij,direction_B_to_A=dji,
                    best_rate_A_to_B=kj,best_rate_B_to_A=ki)
                if mode=='frozen_entire_shape':
                    pass
            assert rec['free_gains_and_slow_rate']['conservative_distance']<=rec['free_fast_slow_gains_fixed_ks']['conservative_distance']+1e-7
            assert rec['free_fast_slow_gains_fixed_ks']['conservative_distance']<=rec['frozen_entire_shape']['conservative_distance']+1e-7
            per_pair.append(rec)
        summary={}
        for mode in MODES:
            values=np.array([r[mode]['conservative_distance'] for r in per_pair])
            summary[mode]=dict(n_pairs=len(values),pairs_distance_ge_2=int(np.sum(values>=2)),pairs_distance_ge_3=int(np.sum(values>=3)),
                median_distance=float(np.median(values)),p90_distance=float(np.quantile(values,.9)),
                min_distance=float(values.min()),max_distance=float(values.max()),pairs_distance_lt_1=int(np.sum(values<1)))
        results[name]=dict(summary=summary,details=per_pair)
    return dict(phase='P2-AE-cross-model',truth_is_synthetic_from_same_exposed_studies=True,
        posterior_or_accuracy_probability=False,no_out_of_sample_validation=True,one_sided_vs_two_sided='minimum of two directional refit distances',
        fixed_kfast_kelim_n_for_each_competing_candidate=True,refit_b_and_both_input_gains=True,
        rate_grid=[RATE_LOW,RATE_HIGH],noise='max(3 pg/mL,12.5% synthetic group total)',
        threshold_2_is_illustrative_not_95pct_significance=True,results=results)

def main():
    ap=argparse.ArgumentParser();ap.add_argument('--output',type=Path,default=ROOT/'data'/'p2ae-cross-candidate.json');args=ap.parse_args()
    if args.output.exists():raise FileExistsError('refusing overwrite')
    d=run(json.loads(DATA.read_text()))
    args.output.write_text(json.dumps(d,ensure_ascii=False,indent=2,sort_keys=True)+'\n')
    for name,v in d['results'].items():
        print(name)
        for k,x in v['summary'].items():print(' ',k,'ge2=',x['pairs_distance_ge_2'],'ge3=',x['pairs_distance_ge_3'],'median=',round(x['median_distance'],4))
if __name__=='__main__':main()
