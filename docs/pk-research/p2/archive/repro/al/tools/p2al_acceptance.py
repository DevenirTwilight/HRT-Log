#!/usr/bin/env python3
"""P2-AL. Freezes upstream model shapes, rescales Price 1h anchor, and computes
exact extrema of an *artificial visual-interval* mean-square-error difference.
Uses standard library only, and NEVER selects/retrains models by held-out Price.
Not clinical validation or individual E2 prediction.
"""
from __future__ import annotations
import csv
import hashlib
import json
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
INPUT = ROOT / 'inputs'
OUTPUT = ROOT / 'results'
CAPS = (100, 225)
BACKGROUNDS = (0, 6, 12, 18, 24)
HOLDOUT = (2, 3, 4, 6, 8, 12, 18, 24)


def read_inputs():
    return {k: json.loads((INPUT / v).read_text(encoding='utf-8')) for k,v in {
        'ag': 'p2ag-cross-source.json', 'ag_summary':'p2ag-compact-summary.json',
        'aj':'p2aj-results.json', 'af':'p2af-grid-stress.json',
        'ak':'p2ak-results.json',
    }.items()}


def prepared(ag:dict, aj:dict, cap:int, b:int):
    readings = {int(p['hour']): p for p in aj['digitized_points']}
    assert set(readings) == {1, *HOLDOUT}, 'A new point set requires protocol review'
    assert b < readings[1]['value_pg_ml']-readings[1]['visual_window_pg_ml']
    shapes = {}
    for family in ('M1','M2'):
        curve = ag['families'][f'{family}_rosano_cap_{cap}']['price_1h_calibrated_heldout_scores_by_baseline'][str(b)]
        assert curve['anchor_time_h'] == 1 and curve['anchor_value_pg_ml'] == 450.0
        assert tuple(int(x) for x in curve['times_h'][1:]) == HOLDOUT
        assert all(int(x) in readings for x in curve['times_h'][1:])
        shapes[family] = {int(t):(p-b)/(curve['anchor_value_pg_ml']-b)
                          for t,p in zip(curve['times_h'][1:],curve['predicted_pg_ml'][1:])}
    return readings, shapes


def predict_ratio(ratio:float, anchor:float, b:int)->float:
    return b+(anchor-b)*ratio


def mse(readings:dict, shapes:dict, family:str, b:int, anchor:float, selected:dict|None=None)->float:
    ys=selected or {t:readings[t]['value_pg_ml'] for t in HOLDOUT}
    return sum((predict_ratio(shapes[family][t],anchor,b)-ys[t])**2 for t in HOLDOUT)/len(HOLDOUT)


def difference(readings:dict,shapes:dict,b:int,anchor:float,selected:dict|None=None)->float:
    return mse(readings,shapes,'M2',b,anchor,selected)-mse(readings,shapes,'M1',b,anchor,selected)


def bounded_delta(readings:dict, shapes:dict, b:int, minimize:bool):
    """Exact min/max of mean(M2 squared error-M1 squared error)
    over independent artificial digitization rectangles for all 8 heldout points
    plus Price 1h anchor. Assumes model shape is fixed, and anchor is > b.

    For fixed anchor a, the difference is affine in each held-out y. The
    y_i-extremum is at a corner chosen by sign(H2_i-H1_i); this sign remains
    fixed over the anchor range. Resulting function is quadratic in a: extrema
    are at its endpoint or stationary point in range.
    """
    q2=q1=q0=0.0
    count=len(HOLDOUT)
    for t in HOLDOUT:
        h1=shapes['M1'][t]
        h2=shapes['M2'][t]
        y=float(readings[t]['value_pg_ml']);w=float(readings[t]['visual_window_pg_ml'])
        assert w>0
        # derivative d(error2^2-error1^2)/dy=-2(pred2-pred1)
        if minimize:
            extremal_y = y + w if h2 > h1 else y-w
        else:
            extremal_y = y - w if h2 > h1 else y+w
        d1=b*(1-h1)-extremal_y
        d2=b*(1-h2)-extremal_y
        q2 += h2*h2-h1*h1
        q1 += 2*(h2*d2-h1*d1)
        q0 += d2*d2-d1*d1
    q2/=count;q1/=count;q0/=count
    mid=float(readings[1]['value_pg_ml']);w1=float(readings[1]['visual_window_pg_ml'])
    lo,hi=mid-w1,mid+w1
    assert lo>b, 'anchor can cross or undercut the chosen baseline'
    xs=[lo,hi]
    if abs(q2)>1e-14:
        vertex=-q1/(2*q2)
        if lo<=vertex<=hi: xs.append(vertex)
    vals=[(q2*x*x+q1*x+q0,x) for x in xs]
    result=min(vals) if minimize else max(vals)
    return {'delta_mse':result[0],'at_anchor_pg_ml':result[1],
            'quadratic_coefficients':[q2,q1,q0],
            'anchor_range_pg_ml':[lo,hi]}


def evaluate(inputs:dict)->dict:
    ag,aj,af,ak=inputs['ag'],inputs['aj'],inputs['af'],inputs['ak']
    assert ag['phase']=='P2-AG' and aj['phase']=='P2-AJ' and af['phase']=='P2-AF-grid-stress' and ak['phase']=='P2-AK'
    assert ag['price_2to24h_held_out'] is True and ag['previously_seen_sources'] is True
    assert math.isclose(aj['figure1_trapezoid_raw_0h_assumed_zero_pg_h_ml'],
                        ak['one_mg_reference']['digitized_auc_zero_baseline_pg_h_ml'],abs_tol=1e-7)
    assert aj['author_table_auc_pg_h_ml']==2109
    assert inputs['ag_summary']['training_n_timepoints']==7
    entries=[]
    for cap in CAPS:
        for b in BACKGROUNDS:
            r,s=prepared(ag,aj,cap,b)
            anchor=r[1]['value_pg_ml']
            m1=math.sqrt(mse(r,s,'M1',b,anchor))
            m2=math.sqrt(mse(r,s,'M2',b,anchor))
            low=bounded_delta(r,s,b,True)
            high=bounded_delta(r,s,b,False)
            assert low['delta_mse'] <= m2*m2-m1*m1+1e-7 <= high['delta_mse']
            pref = 'M1_all_reading_corners' if low['delta_mse']>0 else 'M2_all_reading_corners' if high['delta_mse']<0 else 'undetermined_by_intervals'
            entries.append({'rosano_baseline_cap_pmol_l':cap,'assumed_price_baseline_pg_ml':b,
                'price_1h_anchor_re_digitized_pg_ml':anchor,
                'm1_rmse_conditional_pg_ml':m1,'m2_rmse_conditional_pg_ml':m2,
                'center_delta_mse_m2_minus_m1':m2*m2-m1*m1,
                'interval_delta_mse_min':low['delta_mse'],
                'interval_delta_mse_max':high['delta_mse'],
                'interval_rank_result':pref,
                'interval_min_anchor_at_pg_ml':low['at_anchor_pg_ml'],
                'interval_max_anchor_at_pg_ml':high['at_anchor_pg_ml']})
    counted={k:sum(e['interval_rank_result']==k for e in entries) for k in
            ('M1_all_reading_corners','M2_all_reading_corners','undetermined_by_intervals')}
    af_early=af['results']['auc_0_8_h']
    af_tail=af['results']['q24_predose_index']
    if not (len(af_early['rows'])==len(af_tail['rows'])==15):raise ValueError('Wrong P2-AF cohort length')
    gates=[
        {'id':'E1','kind':'engineering','criterion':'M2 research kernel is reproducible, tests and Debug startup verified','status':'previously_passed_see_CI_log','notes':'Engineering code run; not individual model accuracy'},
        {'id':'E2','kind':'engineering','criterion':'Legacy production model, personal record data and release remain unchanged','status':'previously_passed_in_research_PR','notes':'Integration into new release requires fresh validation'},
        {'id':'R1','kind':'research','criterion':'Training/evaluation data have no historical researcher exposure','status':'failed','notes':'Price was used/seen in earlier studies; P2-AG retrospective holdout is not blinded'},
        {'id':'R2','kind':'research','criterion':'One model dominates competitor across predefined nuisance/read intervals','status':'failed','notes':'Preference flips by Rosano baseline cap and Price baseline'},
        {'id':'R3','kind':'research','criterion':'Price Figure 1 and Table 1 AUC reproducible on same basis','status':'failed','notes':'Author table 2109 vs 1mg manually redigitized raw b0 1550.5664'},
        {'id':'R4','kind':'research','criterion':'Late-time tail robustness demonstrated under joint ke,ks nuisance freedom','status':'failed','notes':'Nominal refined grid median 24h trough index spread 37.58x'},
        {'id':'R5','kind':'research','criterion':'Independent subject-level human concentrations validate absolute individual pg/mL','status':'missing','notes':'Available P2 material only supports aggregate and synthetic conditions'},
        {'id':'R6','kind':'research','criterion':'Prospectively specified baseline, assay, dose-clock, and external evaluation protocol','status':'missing','notes':'Requires independent data; arbitrary bounds are not clinical thresholds'},
        {'id':'P1','kind':'product','criterion':'Experimental UI labels dimensionless shape, manual/verified input, consent, candidate limitations','status':'eligible_for_review','notes':'No personalized pg/mL, automatic dose recommendation or promoted release'},
        {'id':'P2','kind':'product','criterion':'Replace default numerical clinical concentrations with M2','status':'blocked','notes':'R1-R6 must be resolved, and official model must be assessed independently'},
    ]
    return {
        'phase':'P2-AL','kind':'retrospective_conditional_decision_audit','n_scenarios':len(entries),
        'heldout_times_h':list(HOLDOUT),'historical_selection_leakage':True,
        'original_price_anchor_pg_ml':450.0,'new_price_anchor_source':'P2-AJ manually redigitized Price Figure 1 1mg SL',
        'shape_source':'P2-AG selected early-only fit; shape and candidate completely frozen',
        'anchor_rule':'b+(new_re_read_1h-b)*(p_old(t)-b)/(450-b)',
        'uncertainty_rule':'independent subjective P2-AJ windows at 1h and 2..24h, not CI, not probability',
        'winner_rule':'extrema of MSE(M2)-MSE(M1) over rectangle; positive entire interval=M1, negative entire=M2',
        'scenario_scores':entries,'wins':counted,
        'identifiability':{
            'P2_AF_refined_auc08_median_span_ratio':af_early['refined_median_ratio'],
            'P2_AF_refined_q24_median_span_ratio':af_tail['refined_median_ratio'],
            'P2_AF_refined_q24_per_candidate_ratios':[r['fine_ratio'] for r in af_tail['rows']],
            'P2_AF_refined_q24_all15_gt2':all(r['fine_ratio']>2 for r in af_tail['rows']),
            'P2_AF_refined_q24_gt5_count':sum(r['fine_ratio']>5 for r in af_tail['rows'])},
        'price_auc':{'P2_AJ_raw_b0_trapezoid':aj['figure1_trapezoid_raw_0h_assumed_zero_pg_h_ml'],
                    'published_table1_auc':aj['author_table_auc_pg_h_ml'],
                    'minimum_gap_even_under_subjective_upward_windows_b0':aj['zero_baseline_gap_even_after_stress_pg_h_ml'],
                    'low_dose_intervals_not_resolved_to_point_truth':True},
        'decision_gates':gates,
        'scope':'Engineering-only opt-in research visualization may proceed with safeguards; no clinical replacement approval; neither M1 nor M2 is established accurate for patients.',
        'assumptions_and_limitations':[
            'All sources previously encountered in M2 development; this is not prospective blind test.',
            'No individual subject PK profiles and no full assay covariance.',
            'P2-AJ visual reading widths are subjective artificial bounds; worse unknown digitization errors can change result.',
            'This analysis compares specific P2-AG M1/M2 early-source fits, not the official legacy concentration estimator.',
            'Price 1h observation is used to calibrate each model; no unsupervised absolute concentration transport.',
            'The 10 baseline scenarios are sensitivity assumptions, not 10 independent trials or people.',
            'Refined grid spreads conditional on chosen candidate family, fixed fast component, metric and error scenario.',
            'Research shape denominators are model-specific; do not interchange absolute scale or pg/mL.',
        ],
    }


def execute(root=ROOT):
    a=evaluate(read_inputs())
    OUTPUT.mkdir(exist_ok=True)
    (OUTPUT/'p2al-audit.json').write_text(json.dumps(a,ensure_ascii=False,indent=2,sort_keys=True)+'\n',encoding='utf-8')
    fields=list(a['scenario_scores'][0])
    with (OUTPUT/'p2al-paired-comparison.csv').open('w',newline='',encoding='utf-8') as f:
        dw=csv.DictWriter(f,fieldnames=fields);dw.writeheader();dw.writerows(a['scenario_scores'])
    (OUTPUT/'p2al-gates.json').write_text(json.dumps(a['decision_gates'],ensure_ascii=False,indent=2,sort_keys=True)+'\n',encoding='utf-8')
    return a

if __name__=='__main__':
    a=execute();print(json.dumps({'wins':a['wins'],'n':a['n_scenarios'],'q24_spread':a['identifiability']['P2_AF_refined_q24_median_span_ratio']},indent=2))
