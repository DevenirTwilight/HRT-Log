#!/usr/bin/env python3
"""P2-X: supplementary sensitivity, not independent validation or clinical PK."""
from __future__ import annotations
import argparse,json,math
from pathlib import Path
import numpy as np
import p2x_ensemble as x
import p2v_continuous_fit as v

def run():
    d=json.loads(x.DATA.read_text()); rows=[]
    for name,delta,rho in (('alternative_covariance',.15,.65),('wide_model_discrepancy',.30,0.)):
        studies=v.make_sources(d,delta=delta,rho=rho,ros_baseline_cap=225.,price_baseline_cap=24.)
        for price_b in (0.,18.,24.):
            for n in (8,10):
                a=x.fit_one(studies,n,price_b)
                rows.append({'scenario':name,'delta':delta,'rho':rho, 'price_b':price_b,
                             'n':n,'score':a['score'],'q6':a['trough_index_per_1mg_q6_q12_q24']['6'],
                             'q12':a['trough_index_per_1mg_q6_q12_q24']['12'],
                             'q24':a['trough_index_per_1mg_q6_q12_q24']['24'],
                             'amplitude_price':a['fit']['Price1997_figure1']['amplitude_at_1h'],
                             'price_auc':a['price_model_baseline_subtracted_auc_pg_h_ml']})
    t=[0,1,2,3,4,6,8,12,18,24]
    lo=[0,435,190,106,78,50,39,29,20,20]
    hi=[50,465,255,130,95,64,52,41,31,31]
    auc_lo=float(np.trapezoid(lo,t));auc_hi=float(np.trapezoid(hi,t))
    published=float(d['price_author_table_auc_mean'])
    return {'alternative_assumptions':rows,
            'figure_author_interval_envelope':{
            'source':'Repo p2i-price-figure-points.json manually interpreted wide reading bands + hypothetical zero-hour [0,50] pg/mL',
            'time_grid_h':t,'pointwise_lo_pg_ml':lo,'pointwise_hi_pg_ml':hi,
            'auc_total_raw_range_pg_h_ml':[auc_lo,auc_hi],
            'auc_baseline_subtracted_upper_if_b_ge_zero_pg_h_ml':auc_hi,
            'author_table_mean_auc_baseline_subtracted_pg_h_ml':published,
            'gap_at_least_if_all_bands_and_b_nonnegative_pg_h_ml':published-auc_hi,
            'mathematical_coverage':'Only conditional on manual figure reading bands, fixed trapezoid grid, nonnegative baseline. NOT a confidence bound.',
            'does_not_justify_augmenting_raw_or_replacing_publication':True},
            'warning': 'Different rho/delta values alter pseudoloss denominator and are not likelihood-comparable across scenarios.'}

def main():
    ap=argparse.ArgumentParser();ap.add_argument('--out',required=True);args=ap.parse_args()
    dst=Path(args.out)
    if dst.exists():raise FileExistsError('Refuse overwrite')
    out=run();dst.parent.mkdir(parents=True,exist_ok=True);dst.write_text(json.dumps(out,ensure_ascii=False,indent=2)+'\n')
    for r in out['alternative_assumptions']:
        print(r['scenario'],r['price_b'],r['n'],round(r['score'],4),round(r['q24'],6),flush=True)
    print('conditional price AUC bounds',out['figure_author_interval_envelope']['auc_total_raw_range_pg_h_ml'],flush=True)
if __name__=='__main__':main()
