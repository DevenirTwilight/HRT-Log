#!/usr/bin/env python3
"""Research-only envelopes matching *published aggregate summaries*.
They are NOT prospective PK, not even population-mean real time series.
No undocumented individual baseline, concentrations or absorption parameters.
"""
from __future__ import annotations
import csv
import json
import math
import sys
from pathlib import Path

SPRAY={1:(31.2,10.3,17.8),2:(46.1,16.4,28.2),3:(48.4,18.9,29.5)}
SC_T=(790.,436.,553.)  # ng/dL, arithmetic pooled week12 mean, not individual trajectory

def beta_mean(p:float, peak_fraction:float)->float:
    """Analytic average of max-1 beta-shaped pulse on [0,1]."""
    if p<=0 or not 0<peak_fraction<1: raise ValueError('invalid shape')
    q=p*(1-peak_fraction)/peak_fraction
    return math.exp(-p*math.log(peak_fraction)-q*math.log1p(-peak_fraction)
        +math.lgamma(p+1)+math.lgamma(q+1)-math.lgamma(p+q+2))

def shape_power(ratio:float,peak_fraction:float)->float:
    if not 0<ratio<1 or not 0<peak_fraction<1:raise ValueError('invalid metrics')
    lo,hi=1e-9,1024.
    for _ in range(100):
        mid=(lo+hi)/2
        if beta_mean(mid,peak_fraction)>ratio:lo=mid
        else:hi=mid
    return (lo+hi)/2

def pulse(t:float,period:float,peak:float,p:float)->float:
    if t<=0 or t>=period:return 0.
    q=p*(period-peak)/peak
    return math.exp(p*math.log(t/peak)+q*math.log((period-t)/(period-peak)))

def envelope(cmax:float,cmin:float,cavg:float,period:float,peak:float,steps:int=96):
    if not (0<=cmin<cavg<cmax and 0<peak<period):raise ValueError('invalid summary')
    ratio=(cavg-cmin)/(cmax-cmin)
    p=shape_power(ratio,peak/period)
    samples=[(round(period*i/steps,7),round(cmin+(cmax-cmin)*pulse(period*i/steps,period,peak,p),6)) for i in range(steps+1)]
    return {'source_cmax':cmax,'source_cmin':cmin,'source_cavg':cavg,
        'period_hours':period,'assumed_peak_hours_after_dose':peak,
        'baseline_unknown':True,'fitted_beta_power':p,'q':p*(period-peak)/peak,
        'analytic_average':cmin+(cmax-cmin)*beta_mean(p,peak/period),
        'points':samples,'status':'ILLUSTRATIVE_STEADY_SUMMARY_ONLY'}

def research():
    spray={}
    for n,(mx,mn,av) in SPRAY.items():
        spray[str(n)]={str(peak):envelope(mx,mn,av,24.,peak) for peak in (18.,22.)}
    sc=envelope(*SC_T,168.,11.9,steps=168)
    return {'research_only':True,'not_personal_or_population_prospective_prediction':True,
        'source_id_spray':'UK_LENZETTO_RCP_DAY14',
        'source_id_sc':'US_XYOSTED_LABEL_WEEK12',
        'spray_1_to_2_cavg_increase_pg_ml':SPRAY[2][2]-SPRAY[1][2],
        'spray_2_to_3_cavg_increase_pg_ml':SPRAY[3][2]-SPRAY[2][2],
        'spray_3_cavg_linear_extrapolation_from_1_to_2':2*SPRAY[2][2]-SPRAY[1][2],
        'spray':spray,'sc_te':sc,
        'patch_post_removal_fraction_after_hours':{str(h):math.exp(-math.log(2)*h/(70./60.)) for h in (1,3,6,12,24)},
        'nasal_remaining_80min_after_peak_for_t12_10_100min': [2**(-80/10),2**(-80/100)],
        'warnings':[
          'Summary means across people are not an individual or averaged time-course.',
          'LENZETTO geometric mean metrics are not jointly consistent moments of one mathematical trajectory.',
          'SC enanthate XYOSTED Week12 group mixes titrated dose strengths; Tmax is MEDIAN of persons.',
          'Steady summary envelopes cannot be re-used for actual day1, missed-dose or cumulative dose simulation.',
          'Patch removal decay is only normalized above an unspecified endogenous baseline; not instantaneous serum T.',
          'Lenzetto 18/22h peak phases assume 08:00 application and a 02:00–06:00 next-day peak.'
        ]}

def check():
    res=research()
    assert len(res['spray'])==3
    for n,(mx,mn,av) in SPRAY.items():
        for ph in ('18.0','22.0'):
            c=res['spray'][str(n)][ph]
            assert abs(c['analytic_average']-av)<1e-8
            assert abs(c['points'][0][1]-mn)<1e-6
            assert abs(c['points'][-1][1]-mn)<1e-6
            assert abs(c['points'][int(float(ph)*4)][1]-mx)<1e-6
    sc=res['sc_te']; assert abs(sc['analytic_average']-553)<1e-8
    assert sc['points'][0][1]==436
    assert abs(res['spray_2_to_3_cavg_increase_pg_ml']-1.3)<1e-7
    assert res['spray_3_cavg_linear_extrapolation_from_1_to_2']>SPRAY[3][2]
    assert res['patch_post_removal_fraction_after_hours']['24']<1e-5
    try:envelope(10,20,15,24,10)
    except ValueError:pass
    else:raise AssertionError('invalid input accepted')
    return res

def export(out:Path):
    res=check();out.mkdir(parents=True,exist_ok=True)
    (out/'round2_results.json').write_text(json.dumps(res,indent=2,ensure_ascii=False)+'\n')
    with (out/'round2_illustrative.csv').open('w',newline='',encoding='utf-8') as f:
        writer=csv.writer(f);writer.writerow(['product','dose_or_scenario','peak_phase_hour','hour_after_application','concentration','unit','status'])
        for n,profiles in res['spray'].items():
            for ph,z in profiles.items():
                for t,c in z['points']:writer.writerow(['LENZETTO',f'{n} spray',ph,t,c,'pg/mL','SYNTHETIC_STEADY_ENVELOPE'])
        for t,c in res['sc_te']['points']:writer.writerow(['XYOSTED','week12 pooled mixed dose','11.9',t,c,'ng/dL','SYNTHETIC_STEADY_ENVELOPE'])
    return res

if __name__=='__main__':
    dest=Path(sys.argv[1]) if len(sys.argv)>1 else Path('round2_out')
    d=export(dest)
    print('PASS: independent analytic average, extrema, unit and negative-input guards')
    print('LENZETTO Cavg increments:',d['spray_1_to_2_cavg_increase_pg_ml'],d['spray_2_to_3_cavg_increase_pg_ml'])
    print('CSV/JSON:',dest)
