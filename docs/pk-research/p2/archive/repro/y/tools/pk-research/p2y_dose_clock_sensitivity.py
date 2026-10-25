#!/usr/bin/env python3
"""P2-Y: stress-test PRE-EXPOSED fit rows under hypothesized 4x/day clock schedules.

This is a deterministic structural audit, not a discovery of actual Yaish dose
hours, subject-level paired data, assay correction, or validated population PK.
"""
from __future__ import annotations
import argparse,json,math,csv
from pathlib import Path
import numpy as np
import p2v_continuous_fit as v
import p2y_hierarchical_moment_audit as base
ROOT=Path(__file__).resolve().parents[2]
IN=ROOT/'docs/pk-research/p2/p2x-conditional-ensemble-results.json'
SCHEDULES={
 'equal_q6h':(0.,6.,12.,18.),
 'night_9h':(0.,5.,10.,15.),
 'night_10h':(0.,4.,9.,14.),
 'night_12h':(0.,4.,8.,12.),
 'night_14h':(0.,3.,6.,10.),
}

def repeated_schedule_shape(param,slots,post_h=1.5,days=180):
    if not (len(slots)==4 and slots[0]==0 and all(0<=x<24 for x in slots) and all(b>a for a,b in zip(slots,slots[1:]))):
        raise ValueError('expected sorted four daily dose hours starting at zero')
    if not(0<=post_h<slots[1]):raise ValueError('post draw must be after first and before second dose')
    if days<10:raise ValueError('insufficient history')
    # Negative administration times for past calendar days. Previous last dose
    # at -(24-slots[-1]) h, so overnight gap is 24-last slot.
    old=np.array([t-24*d for d in range(1,days+1) for t in slots],float)
    s_before=float(np.sum(v.shape(-old,param)))
    s_after=float(v.shape(np.array([post_h]),param)[0]+np.sum(v.shape(post_h-old,param)))
    return s_before,s_after

def match_published_means(param,slots,pre=204.5,post=1994.,dose=.5,post_h=1.5):
    if not (post>pre>=0 and dose>0):raise ValueError('bad study-level comparison')
    a,b=repeated_schedule_shape(param,slots,post_h)
    diff=b-a
    if diff<=0:
        return {'feasible':False,'reason':'shape cannot produce positive increase','background_required':None}
    effective_gain_pmol_per_mg=(post-pre)/(dose*diff)
    basal=pre-effective_gain_pmol_per_mg*dose*a
    return {'feasible':bool(basal>=-1e-7),'background_required':basal,
            'effective_gain_pmol_per_mg':effective_gain_pmol_per_mg,
            'effective_gain_pgml_per_mg':effective_gain_pmol_per_mg/3.671,
            'shape_pre':a,'shape_post':b,'shape_delta':diff,
            'necessary_ratio_shape':a/diff,'necessary_ratio_observed':pre/(post-pre)}

def analyze(rows,delta=.1):
    best=min(r['score'] for r in rows)
    survivors=[(idx,r) for idx,r in enumerate(rows) if r['score']<=best+delta+1e-10]
    results={};long=[]
    for name,slots in SCHEDULES.items():
        fit=[]
        for idx,r in survivors:
            m=match_published_means(r['parameter'],slots)
            item={'fit_id':idx,'loss':r['score'],'Price_hypothetical_baseline_pgml':r['price_fixed_baseline_pg_ml'],
                  'n_fast':r['n_fast'],'q24_index':r['trough_index_per_1mg_q6_q12_q24']['24'], **m}
            long.append({'schedule':name, **item});fit.append(item)
        yes=[item for item in fit if item['feasible']]
        results[name]={'dose_times_hours':list(slots),'overnight_gap_hours':24-slots[-1],
          'tested_model_count':len(fit),'feasible_model_count':len(yes),
          'feasible_q24_model_index_range':[min(x['q24_index'] for x in yes),max(x['q24_index'] for x in yes)] if yes else None,
          'required_stable_basal_pmol_L_range':[min(x['background_required'] for x in yes),max(x['background_required'] for x in yes)] if yes else None,
          'eligible_price_hypothetical_baselines_pgml':sorted(set(x['Price_hypothetical_baseline_pgml'] for x in yes)),
          'worst_case_mathematical_background_pmol_L':min(x['background_required'] for x in fit),
          'best_case_mathematical_background_pmol_L':max(x['background_required'] for x in fit)}
    return {'phase':'P2-Y','type':'hypothetical clock-condition sensitivity of pre-exposed ensemble',
      'best_score':best, 'near_best_max_extra_pseudoloss':delta, 'near_best_model_count':len(survivors),
      'yaish_observed_mean_pre_pmol_L':204.5, 'yaish_observed_mean_90min_pmol_L':1994.,
      'mean_increment_pmol_L':1789.5,'unit_conversion_pmolL_per_pgml':3.671,
      'source_dose_mg':.5,'four_daily_doses':True,
      'times_hypothesized_NOT_reported_by_study':True,
      'same_subjects_for_both_aggregates_not_reverified':True,
      'assumes_perfect_dose_adherence_stationarity_additive_nonnegative_background_same_gain_and_linear_superposition':True,
      'uses_actual_90min_aggregate_mean_and_SD_from_full_paper_not_2023_abstract_median':True,
      'actual_study_QID_schedule_not_verified':True,
      'not_blinded_external_validation':True,'not_patient_dose_recommendations':True,
      'model_production_untouched':True,'Doll_used':False,'schedules':results,'records':long}

def main():
    ap=argparse.ArgumentParser();ap.add_argument('--json',required=True);ap.add_argument('--csv',required=True);args=ap.parse_args()
    if Path(args.json).exists() or Path(args.csv).exists():raise FileExistsError('Refuse overwrite')
    rows=json.load(open(IN))['rows'];out=analyze(rows)
    Path(args.json).parent.mkdir(parents=True,exist_ok=True)
    Path(args.json).write_text(json.dumps(out,indent=2,ensure_ascii=False,allow_nan=False)+'\n')
    with open(args.csv,'w',newline='') as f:
       cols=['schedule','fit_id','loss','Price_hypothetical_baseline_pgml','n_fast','q24_index','feasible','background_required','effective_gain_pgml_per_mg','shape_pre','shape_post','shape_delta','necessary_ratio_shape']
       w=csv.DictWriter(f,fieldnames=cols,extrasaction='ignore');w.writeheader();w.writerows(out['records'])
    print(json.dumps({k: {'fit':v['feasible_model_count'],'tested':v['tested_model_count'],'night_h':v['overnight_gap_hours']} for k,v in out['schedules'].items()},ensure_ascii=False))
if __name__=='__main__':main()
