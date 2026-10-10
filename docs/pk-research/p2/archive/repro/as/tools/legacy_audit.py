#!/usr/bin/env python3
"""Read-only reproduction of production E2_SL at default hold tier; NOT an individual PK forecast.
Source parameters manually transcribed with fixed Git object identities from official repo.
The published group data were exposed during earlier model development; not independent validation.
"""
from __future__ import annotations
import json, math, sys, hashlib
from pathlib import Path
import numpy as np
from scipy.integrate import quad
from scipy.special import gammainc

ROOT = Path(__file__).resolve().parents[1]
PARAM = json.loads((ROOT/'inputs/legacy_source_snapshot.json').read_text())
PRICE = json.loads((ROOT/'inputs/price1997_p2aj_digitization.json').read_text())
OBS = json.loads((ROOT/'inputs/p2u_study_aggregate_observations.json').read_text())['studies']
M2 = json.loads((ROOT/'inputs/p2x-conditional-ensemble-results.json').read_text())
YAISH = json.loads((ROOT/'inputs/p2aq_osf_aggregate_audit.json').read_text())['VNC54']['data']


def component(t, m):
    if t < 0: return 0.
    return sum(z['A_per_mg'] * (math.exp(-z['lambda_per_h'] * t) - math.exp(-m['ka_per_h'] * t)) for z in m['terms'])


def default_sl(t, dose=1., tier=2):
    """Engine.sublingual at default tier, without background or LabFit, mg linear superposition."""
    if t < 0: return 0.
    sl = PARAM['E2_SL']; oral = PARAM['E2_ORAL']
    share = sl['swallowed_share']; unconsumed = 1. - share
    minutes = sl['tier_minutes']
    mucosal = min(1., unconsumed * minutes[tier] / minutes[sl['default_tier']])
    return dose * (component(t, sl) * mucosal / unconsumed + component(t, oral) * (1. - mucosal))


def rel(t, tier=2):
    return default_sl(t, tier=tier) / default_sl(1, tier=tier)


def auc(lo, hi, tier=2):
    return quad(lambda x: default_sl(x, tier=tier),lo,hi,epsabs=1.e-8)[0]


def rmse(a,b):
    return float(np.sqrt(np.mean((np.asarray(a,dtype=float)-np.asarray(b,dtype=float))**2)))


def stats_price(b):
    raw = PRICE['digitized_points']; first = raw[0]['value_pg_ml']
    points = []
    for row in raw:
        t = row['hour']; y = row['value_pg_ml']
        rel_obs = (y-b)/(first-b)
        x = rel(t)
        points.append({'h':t,'observed_raw_pg_ml':y,'observed_relative':rel_obs,
            'legacy_relative':x,'residual_pred_minus_observed':x-rel_obs,
            'visual_window_pg_ml':row['visual_window_pg_ml'],
            'reported_observation_kind':'analyst_figure_digitization'})
    held = [row for row in points if row['h']>1]
    return {'b_pg_ml':b,'points':points,'rmse_t2_to24':rmse([x['observed_relative'] for x in held],[x['legacy_relative'] for x in held]),
     'mae_t2_to24':float(np.mean([abs(x['residual_pred_minus_observed']) for x in held])),
     'mean_ratio_obs_to_model_8h':points[5]['observed_relative']/points[5]['legacy_relative'],
     'early_2h_ratio_pred_to_observed':points[1]['legacy_relative']/points[1]['observed_relative']}


def rosano(b):
    st=OBS['Rosano1997_PK25']; raw=list(zip(st['hours'],st['y'])); denom=raw[-1][1]-b
    data=[{'h':t,'observed_relative':(y-b)/denom,'legacy_relative':rel(t), 'observed_pmol_l':y} for t,y in raw]
    return {'b_pmol_l':b,'observed_units':'pmol/L','anchor_h':1.,'data':data,'rmse_first3':rmse([x['observed_relative'] for x in data[:3]],[x['legacy_relative'] for x in data[:3]])}


def komesaroff():
    st=OBS['Komesaroff1998_n10']; b=st['y'][0]; at15=st['y'][1]-b; at30=st['y'][2]-b
    actual=at15/at30; prediction=default_sl(.25)/default_sl(.5)
    return {'study':'Komesaroff 1998','dose_mg':2,'published_time_0_15_30min_pmol_l':st['y'],'reported_dispersion_sem':st['nominal_se_or_read_width'],
        'observed_15min_increment_to_30min_increment':actual,
        'legacy_15min_to_30min_response':prediction,
        'predicted_over_observed_ratio':prediction/actual,
        'warning':'Published 30-minute value was transcribed from study Table 1; both times share group baseline; not individual matched records.'}


def m2_relative(t, r):
    """Independent transcription of frozen M2 input kernel, checks against P2-X frozen output."""
    p=r['parameter']; n=p['n_fast']; k=p['k_fast_per_h']; ke=p['k_elim_per_h'];ks=p['k_slow_per_h']; w=p['effective_slow_weight']
    def raw(x):
        fast=math.exp(n*math.log(k/(k-ke))-ke*x)*gammainc(n,(k-ke)*x)
        gap=abs(ks-ke)
        slow=ks*x*math.exp(-ke*x) if gap<1e-9*max(ks,ke) else ks*math.exp(-min(ks,ke)*x)*(-math.expm1(-gap*x))/gap
        return (1-w)*fast+w*slow
    return raw(t)/raw(1.)

def m2_price_metrics(b):
    observed=stats_price(b)['points'][1:]
    selected=[r for r in M2['rows'] if r['score']<=M2['summary']['global_best_loss']+.10+1e-10]
    rows=[]
    for r in selected:
        # Single frozen global selection; DO NOT post-hoc select a winner using this Price evaluation.
        v=[m2_relative(x['h'],r) for x in observed]
        loss=rmse(v,[x['observed_relative'] for x in observed])
        rows.append({'candidate_score':r['score'], 'm2_price_RMSE':loss, 'price_background_assumed_for_fit':r['price_fixed_baseline_pg_ml'], 'rosano_cap':r['rosano_baseline_cap_pmol_l'], 'n_fast':r['n_fast']})
    return {'b_pg_ml':b, 'all_frozen15_rmse_min':min(r['m2_price_RMSE'] for r in rows),'rmse_median':float(np.median([r['m2_price_RMSE'] for r in rows])),'rmse_max':max(r['m2_price_RMSE'] for r in rows), 'candidate_count':len(rows), 'caveat':'These M2 candidates were already selected against Price points; in-sample descriptive fit, NOT generalization/clinical evidence.'}

def repeated(t_since_last, interval_hours, n=300, tier=2):
    return sum(default_sl(t_since_last+interval_hours*i,tier=tier) for i in range(n))


def yaish_90min_sensitivity():
    """Counterfactual Q intervals, NOT known actual history or validation."""
    out=[]
    for q in [6,8,10,12,24]:
        before=.5*sum(default_sl(q*i) for i in range(1,250))
        after=.5*sum(default_sl(1.5+q*i) for i in range(250))
        out.append({'hypothetical_spacing_h':q,'legacy_pre_pg_ml':before,
            'legacy_post_90min_pg_ml':after,'legacy_delta_pg_ml':after-before,
            'legacy_delta_pmol_l':(after-before)*3.671})
    return {'study':'Yaish 2023, VNC54 OSF workbook aggregate audit P2-AQ',
      'source':'/inputs/p2aq_osf_aggregate_audit.json (anonymized aggregate, not original records)',
      'verified_subject_count':YAISH['unique_people_with_postdose'],
      'observed_paired_median_delta_pmol_l':{v['visit']:v['paired_delta_pmol_l']['median'] for v in YAISH['visit_stats']},
      'counterfactual_0p5mg_four_doses_day':out,
      'warnings':['No actual individual dose timestamps', 'Same subject sampled at 3 and 6 months',
      'Pregenerated M2 evidence already exposed', 'Assay Siemens Immulite 2000; other cohorts use different methods',
      'The observed paired delta is not equivalent to the dose-response increment without dose history']}

def price_visual_extreme(b_min=0.,b_max=24.):
    """Conservative pointwise envelope assuming arbitrary analyst visual reading windows and common b.
       Correlation of human samples unknown; NOT confidence intervals."""
    rows=PRICE['digitized_points']; y1=rows[0]['value_pg_ml'];w1=rows[0]['visual_window_pg_ml']
    vals=[]
    for row in rows[1:]:
        y=row['value_pg_ml'];w=row['visual_window_pg_ml'];
        # both satisfy positive numerator at t2,t8 for b<=24
        low=(y-w-b_max)/(y1+w1-b_max)
        high=(y+w-b_min)/(y1-w1-b_min)
        old=rel(row['hour'])
        vals.append({'h':row['hour'],'min_observed_relative':low,'max_observed_relative':high,
        'legacy_relative':old,'out_of_manually_defined_envelope':old<low or old>high})
    return vals

def main():
    snap=PARAM['E2_SL']
    checks=snap['checks']
    pred={str(t):default_sl(t) for t in [1/6,1/4,1/3,1/2,2/3,1,1.5,2,3,4,6,8,12,18,24]}
    all_price=[stats_price(b) for b in [0.,6.,12.,18.,24.]]
    ros=[rosano(b) for b in [0.,100.,225.]] # 225 close to 234 lower bound; extreme sensitivity, not measured background
    dose_tiers={str(t):{'hold_minutes':snap['tier_minutes'][t], '1h_response':default_sl(1,tier=t), 'fold_vs_default':default_sl(1,tier=t)/default_sl(1)} for t in range(4)}
    m2comparison=[m2_price_metrics(b) for b in [0.,6.,12.,18.,24.]]
    repeat={str(h):{'pre_next_dose_pg_ml_q1mg':repeated(h,h),'steady_8h_after_last_pg_ml_1mg':repeated(8,h)} for h in [6,12,24]}
    first=PRICE['digitized_points'][0]['value_pg_ml']
    aucprice={ 'figure_from_p2aj_without_background': PRICE['figure1_trapezoid_raw_0h_assumed_zero_pg_h_ml'],
    'journal_table_1mg_sl_mean_auc0_24': PRICE['author_table_auc_pg_h_ml'],
    'legacy_single_1mg_auc0_24':auc(0,24), 'legacy_single_1mg_auc0_8':auc(0,8),
    'price_fig_digitized_1h_pg_ml':first,
    'context':'Cross-cohort AUC comparison not a valid individual absolute-prediction test; published Table vs Figure disagrees for Price; area definitions and baseline require caution.'}
    out={'schema_version':1,'phase':'P2-AS','subject':'production legacy E2_SL model audit','not_clinical_validation':True,
      'no_unseen_external_human_data':True,
      'source_repo':PARAM['repository'],'source_ref':PARAM['ref'],'parameters':PARAM,
      'legacy_single_dose_pg_ml_exogenous_only':pred,'price_shape_anchor1h':all_price,'rosano_shape_anchor1h':ros,
      'komesaroff_early_rise':komesaroff(), 'price_visual_stress_envelope':price_visual_extreme(), 'yaish_90min_exploratory_sensitivity':yaish_90min_sensitivity(), 'm2_price_in_sample_comparison':m2comparison, 'dose_holding_tier_counterfactuals':dose_tiers,
      'repeated_dose_synthetic_not_observed':repeat, 'auc_cross_study':aucprice,
      'interpretation_flags':['Doll 1mg 1h/8h AUC are construction targets, not validation','Price Figure1 digitized mean, already seen during M2 candidate selection','Rosano/Komesaroff source values are already exposed, not independent heldout','Assay/population/formulation and group covariance differences','Single chosen baseline is an assumption, not measured','Normalized comparisons only shape; no pg/mL calibration of individual','No personal dose recommendation']}
    s=json.dumps(out,ensure_ascii=False,sort_keys=True,indent=2)+'\n'
    (ROOT/'results/p2as-legacy-audit.json').write_text(s)
    print('Price RMSE',[(z['b_pg_ml'],round(z['rmse_t2_to24'],5)) for z in all_price])
    print('Rosano',[(z['b_pmol_l'],round(z['rmse_first3'],5)) for z in ros])
    print('M2 price RMSE min-med-max',[(z['b_pg_ml'],round(z['all_frozen15_rmse_min'],3),round(z['rmse_median'],3),round(z['rmse_max'],3)) for z in m2comparison])
    print('Komesaroff old/actual ratio',round(out['komesaroff_early_rise']['predicted_over_observed_ratio'],3))
    print('Yaish',yaish_90min_sensitivity()['observed_paired_median_delta_pmol_l'],[round(x['legacy_delta_pmol_l'],1) for x in yaish_90min_sensitivity()['counterfactual_0p5mg_four_doses_day']])
    print('Price manually bounded 2h,8h', [x for x in price_visual_extreme() if x['h'] in [2,8]])
    print('AUC',aucprice)
    print('1mg SL at 1,8,24h',*[round(default_sl(t),6) for t in [1,8,24]])
    print('sha256 results',hashlib.sha256(s.encode()).hexdigest())
    return out

if __name__=='__main__': main()
