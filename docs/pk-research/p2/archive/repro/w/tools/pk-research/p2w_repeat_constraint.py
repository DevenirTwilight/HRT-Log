#!/usr/bin/env python3
"""P2-W repeat-dose stress, NOT a valid population calibration or dosing tool.

For each prior frozen P2-V shape, compare idealized q6/q12/q24 accumulation
and what *extra* study-specific scale would be needed to explain separate
cohort summary troughs. It does not fit model parameters to the new cohorts.
"""
from __future__ import annotations
import argparse,csv,json,math
from pathlib import Path
import numpy as np
from p2v_continuous_fit import shape

ROOT=Path(__file__).resolve().parents[2]
PARAM=ROOT/'docs/pk-research/p2/p2v-candidate-parameters.json'
EVIDENCE=ROOT/'docs/pk-research/p2/p2w-repeat-evidence.json'
OLD_SENS=ROOT/'docs/pk-research/p2/p2v-continuous-fit-results.json'


def accumulation(shape_params, interval_h, time_offset_h=0., previous=120):
    """Next predose concentration (dimensionless per mg) with no NEW morning dose.

    time_offset_h moves blood draw relative to ideal previous-dose interval;
    negative offset means an early draw before the expected next dose.
    """
    if interval_h<=0 or previous<1 or (interval_h+time_offset_h)<=0:
        raise ValueError('Invalid spacing/draw timing')
    t=interval_h*np.arange(1,previous+1,dtype=float)+time_offset_h
    return float(np.sum(shape(t,shape_params)))


def ninety_minutes_after(shape_params, interval_h, previous=120):
    """Dose at time 0 plus older doses, 90m after 0; normalized per mg."""
    t=interval_h*np.arange(1,previous+1,dtype=float)+1.5
    return float(shape([1.5],shape_params)[0]+np.sum(shape(t,shape_params)))


def cohort_gain(target,baseline,dose_mg_per_admin,accum):
    if not (0<=baseline<=target) or dose_mg_per_admin<=0 or accum<=0:
        raise ValueError('Unsupported assumption or target')
    return (target-baseline)/(dose_mg_per_admin*accum)


def lognormal_gain_moment_ratio(sigma):
    """E[G]/median(G) for fixed baseline and lognormal positive gain G."""
    if sigma<0:return float('nan')
    return math.exp(sigma*sigma/2)


def analyze():
    pars=json.loads(PARAM.read_text())
    evidence=json.loads(EVIDENCE.read_text())
    old=json.loads(OLD_SENS.read_text())
    out={'phase':'P2-W','mode':'FROZEN_P2V_SHAPES_NO_REFITTING_TO_REPEAT_COHORTS',
      'source_head_P2V':'7a45741f1a83646195db0a0e20c9806da4878af3',
      'Doll_used':False,'observations_are_group_aggregates':True,
      'Yaish_mean_is_not_median':True,'Cortez_arms_are_separate_people':True,
      'not_a_population_PK_or_real_external_validation':True,
      'production_model_modified':False,'models':{},'relevant_baseline_interpretation':
       'Pretreatment serum E2 is NOT directly the steady treatment baseline. All cross-study gain transport is hypothetical.'}
    m2_variants={
      'P2V_M2_extended_n9':pars['expanded_order_search']['M2']['parameters'],
      'P2V_M2_primary_n8':old['scenarios']['primary']['fit']['M2']['parameters'],
      'P2V_M2_Price_baseline0':old['scenarios']['price_baseline_zero']['fit']['M2']['parameters']}
    all_shapes={f'{f}_extended':pars['expanded_order_search'][f]['parameters'] for f in ('M0','M1')}
    all_shapes.update(m2_variants)
    for name,params in all_shapes.items():
        norm={str(i):accumulation(params,i) for i in (6,12,24)}
        one=evidence['cohorts']['Cortez2024_once_SL'];two=evidence['cohorts']['Cortez2024_twice_SL'];ya=evidence['cohorts']['Yaish2023_q6_SL']
        one_gain=cohort_gain(one['six_month_mean_E2_pg_ml'],one['baseline_mean_E2_pg_ml'],one['six_month_mean_daily_mg'],norm['24'])
        two_gain=cohort_gain(two['six_month_mean_E2_pg_ml'],two['baseline_mean_E2_pg_ml'],two['six_month_mean_daily_mg']/2,norm['12'])
        ratio=(two['six_month_mean_daily_mg']/2*norm['12'])/(one['six_month_mean_daily_mg']*norm['24'])
        # Price gain for same nominal 1mg; this is different formulation/assay/population.
        if name=='P2V_M2_primary_n8':
            price_gain=old['scenarios']['primary']['fit']['M2']['study_profiles']['Price1997_figure1']['amplitude_at_1h']
        elif name=='P2V_M2_Price_baseline0':
            price_gain=old['scenarios']['price_baseline_zero']['fit']['M2']['study_profiles']['Price1997_figure1']['amplitude_at_1h']
        else:
            price_gain=pars['expanded_order_search']['M2' if name.startswith('P2V_M2') else name[:2]]['observed_study_anchors']['Price1997_figure1']['amplitude_at_observed_dose_1h']
        post= ninety_minutes_after(params,6)
        y_t=ya['trough_mean_pmol_l']/ya['pmol_per_pg_ml']
        y_p=ya['ninety_min_median_pmol_l']/ya['pmol_per_pg_ml']
        ya_cases=[]
        for b in (0.,10.,20.,30.,40.,50.):
            if b>=min(y_t,y_p):continue
            gt=cohort_gain(y_t,b,.5,norm['6'])
            gp=cohort_gain(y_p,b,.5,post)
            # Distinct estimands mean vs median: this is not an estimated gain ratio.
            ya_cases.append({'hypothetical_steady_background_pg_ml':b,
              'gain_from_mean_trough_if_representative_pg_ml_per_1mg_1h':gt,
              'gain_from_median_90min_if_representative_pg_ml_per_1mg_1h':gp,
              'diagnostic_ratio_gtrough_over_gpost_NOT_an_individual_ratio':gt/gp})
        # Timing sensitivity: each pair of earlier/later draws vs nominal interval.
        timing={str(i):{str(offset):accumulation(params,i,offset) for offset in (-4.,-2.,0.,2.,4.) if i+offset>0}
                for i in (6,12,24)}
        baseline_inference={'0':{},'pre_treatment_group_mean':{},'50pg':{}}
        for key,base_selector in (('0',lambda c:0.),('pre_treatment_group_mean',lambda c:c['baseline_mean_E2_pg_ml']),('50pg',lambda c:50.)):
            for arm,c,R,d in (('once',one,norm['24'],one['six_month_mean_daily_mg']),
                              ('twice',two,norm['12'],two['six_month_mean_daily_mg']/2)):
                needed=cohort_gain(c['six_month_mean_E2_pg_ml'],base_selector(c),d,R)
                baseline_inference[key][arm]={'required_1h_increment_per_mg_if_shared_shape':needed,
                     'relative_to_Price_fig1_1h_gain_HYPOTHETICAL':needed/price_gain}
        out['models'][name]={
          'reference_Price_1h_increment_pg_ml_per_mg_from_that_fit':price_gain,
          'parameters':params,'dimensionless_trough_index_per_mg':norm,
          'dimensionless_post_90min_index_per_mg_q6':post,
          'conditional_q12_twice_vs_q24_once_predose_increment_ratio_at_equal_daily_mg':ratio,
          'Cortez_group_mean_6mo_ratio_twice_over_once_raw':two['six_month_mean_E2_pg_ml']/one['six_month_mean_E2_pg_ml'],
          'Cortez_increment_ratio_if_subtract_pretreatment_means':(two['six_month_mean_E2_pg_ml']-two['baseline_mean_E2_pg_ml'])/(one['six_month_mean_E2_pg_ml']-one['baseline_mean_E2_pg_ml']),
          'Cortez_required_amplitude_ratio_twice_to_once_using_pretreatment_baselines':two_gain/one_gain,
          'Cortez_required_gain_sensitivity':baseline_inference,
          'Yaish_noncomparable_summary_indicators':ya_cases,
          'timing_sensitivity_indices':timing}
    return out


def main():
    ap=argparse.ArgumentParser()
    ap.add_argument('--out',required=True)
    ap.add_argument('--csv',required=True)
    args=ap.parse_args()
    a=Path(args.out);c=Path(args.csv)
    if a.exists() or c.exists():raise FileExistsError('Refuse overwrite existing output')
    obj=analyze();a.parent.mkdir(parents=True,exist_ok=True);c.parent.mkdir(parents=True,exist_ok=True)
    a.write_text(json.dumps(obj,ensure_ascii=False,indent=2,sort_keys=True)+'\n')
    with c.open('w',newline='') as fd:
        w=csv.writer(fd);w.writerow(['model','interval_h','predose_dimensionless_per_mg','offset_minus2','offset_plus2'])
        for name,m in obj['models'].items():
            for s,x in m['dimensionless_trough_index_per_mg'].items():
                w.writerow([name,s,x,m['timing_sensitivity_indices'][s]['-2.0'],m['timing_sensitivity_indices'][s]['2.0']])
    print('P2-W computed',len(obj['models']),'fixed models. No model refitted, no APK changes.')
if __name__=='__main__':main()
