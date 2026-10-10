#!/usr/bin/env python3
"""P2-Y conditional cross-study study-level mean/delta audit.

NEVER clinical prediction. Published group aggregates are exposed, cohorts are not
interchangeable, no raw participant data, and no claim of clinical validation.
For Yaish, this assumes same n/participants for pre/post study means, q6h exact
steady-state, perfect adherence, linear superposition, stable nonnegative basal
mean and a common effective linear mean amplitude across those two timepoints.
"""
from __future__ import annotations
import json, math, argparse, sys
from pathlib import Path
import numpy as np
from scipy.special import gammainc
import p2v_continuous_fit as v
HERE=Path(__file__).resolve().parents[2]
IN=HERE/'docs/pk-research/p2/p2x-conditional-ensemble-results.json'
OUTDOI=['10.1089/trgh.2023.0022','10.1016/S0029-7844(96)00513-3']
YO={'pre_mean_pmol_L':204.5,'pre_sd_pmol_L':63.5,'post_90_mean_pmol_L':1994.,'post_90_sd_pmol_L':1457., 'post_90_median_pmol_L':1721.,'post_90_iqr_pmol_L':[1000.,2432.], 'dose_mg':.5,'interval_h':6.,'post_after_h':1.5,'unit_pmol_per_pg':3.671}
PRICE={'fig_18h_pg_ml':25.,'fig_24h_pg_ml':24.,'table_half_life_mean_h':18.,'table_half_life_sd_h':2.5,
       'assay_sensitivity_pg_ml':8.,'e2_interassay_cv':.125,'two_terminal_points_reported':True}

def terminal_half_life(c1:float,c2:float,base:float,delta_h:float=6.):
    if delta_h<=0 or any(not math.isfinite(float(x)) for x in [c1,c2,base]):raise ValueError('invalid')
    if base>=min(c1,c2) or c2>=c1: return None
    return delta_h*math.log(2.) / math.log((c1-base)/(c2-base))

def sums(param, interval_h=6., post_h=1.5, count=300):
    if interval_h<=0 or post_h<0 or count<10:raise ValueError('invalid scenario')
    pri=interval_h*np.arange(1,count+1,dtype=float)
    post=post_h+interval_h*np.arange(count,dtype=float)
    pre=float(np.sum(v.shape(pri,param)))
    after=float(np.sum(v.shape(post,param)))
    return pre,after

def yaish_constraint(param, dose=.5, pre=204.5, post=1994., interval=6., post_time=1.5):
    if dose<=0 or pre<0 or post<0 or post<=pre:raise ValueError('invalid stats')
    a,b=sums(param,interval,post_time)
    dd=b-a
    delta=post-pre
    if dd<=0:
        return {'physically_feasible_with_nonnegative_baseline':False,'reason':'nonpositive modeled rise', 'delta_shape':dd,'S_pre':a,'S_post':b}
    gain_pmol_per_mg_at_1h = delta/(dose*dd)
    base=pre-gain_pmol_per_mg_at_1h*dose*a
    return {'physically_feasible_with_nonnegative_baseline':bool(base>=-1e-8),
            'baseline_required_pmol_L':base, 'gain_required_pmol_L_per_mg':gain_pmol_per_mg_at_1h,
            'gain_required_pg_ml_per_mg':gain_pmol_per_mg_at_1h/3.671,
            'S_pre':a,'S_post':b,'delta_shape':dd,'pre_to_rise_shape_ratio':a/dd,
            'pre_to_rise_observed_ratio':pre/delta,
            'post_paired_assumption_not_individual_prediction':True}

def run(rows, tolerance=.1):
    best=min(x['score'] for x in rows)
    data=[]
    for idx,row in enumerate(rows):
        y=yaish_constraint(row['parameter'])
        data.append(dict(row_id=idx, score=row['score'], within_loss_tolerance=bool(row['score']<=best+tolerance+1e-10),
                         price_assumed_baseline_pg_ml=row['price_fixed_baseline_pg_ml'],
                         rosano_baseline_cap_pmol_L=row['rosano_baseline_cap_pmol_l'],
                         n_fast=row['n_fast'],q24=row['trough_index_per_1mg_q6_q12_q24']['24'],yaish=y))
    near=[x for x in data if x['within_loss_tolerance']]
    def group_summary(xs):
        pos=[r for r in xs if r['yaish']['physically_feasible_with_nonnegative_baseline']]
        return {'count':len(xs), 'nonnegative_baseline_feasible_count':len(pos),
                'infeasible_count':len(xs)-len(pos),
                'mean_baseline_required_pmol_L_range':[min(r['yaish'].get('baseline_required_pmol_L',float('inf')) for r in xs),max(r['yaish'].get('baseline_required_pmol_L',float('-inf')) for r in xs)] if xs else None,
                'q24_range_eligible':[min(r['q24'] for r in pos),max(r['q24'] for r in pos)] if pos else None,
                'price_assumptions_eligible':sorted(set(r['price_assumed_baseline_pg_ml'] for r in pos))}
    return {'phase':'P2-Y','input_source':'P2-X exposed previously fitted parameter grid',
            'historical_data_not_new_blind_external_validation':True,
            'yaish_newly_used_fulltext_mean_SD':YO,'yaish_mean_difference_pmol_L':YO['post_90_mean_pmol_L']-YO['pre_mean_pmol_L'],
            'price_original_pdf_method':PRICE,
            'price_half_life_sensitivity_examples':[
                {'assumed_baseline_pg_ml':b,'half_life_h':terminal_half_life(25,24,b)}
                for b in [0.,10.,15.,18.,20.,22.,23.,23.5,23.8,24.]],
            'conditional_assumptions':['Yaish pre and 90min published mean applied to same people','q6h regular with exact 90min draw','stationary nonnegative mean basal contribution','linear effective group gain unchanged during two draws','no omitted earlier doses','no independent corrections for immunoassay biases','asymptotic doses 300'],
            'ensemble_total':group_summary(data), 'ensemble_near_best_loss_0p1':group_summary(near),
            'numerical_scenarios':data, 'Doll_used':False,'production_parameters_changed':False,
            'independent_external_validation':False,'model_approved_for_clinical_prediction':False}

def main():
    ap=argparse.ArgumentParser();ap.add_argument('--out',required=True);opt=ap.parse_args()
    op=Path(opt.out)
    if op.exists():raise FileExistsError('Never overwrite evidence records')
    d=json.load(open(IN));res=run(d['rows'])
    op.parent.mkdir(parents=True,exist_ok=True);op.write_text(json.dumps(res,ensure_ascii=False,indent=2,allow_nan=False)+'\n')
    print('P2-Y conditional',res['ensemble_near_best_loss_0p1'])
if __name__=='__main__':main()
