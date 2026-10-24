#!/usr/bin/env python3
"""P2-AK: Conditional interval AUC audit of the other two Price Figure 1 SL arms.
No scientific calibration or dose recommendation, stdlib only, deterministic.
"""
import argparse
import hashlib
import json
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
SOURCE=ROOT/'data/p2ak-scan-visual-intervals.json'


def trapezoid_weights(t):
    assert len(t) >= 2 and all(b>a for a,b in zip(t,t[1:]))
    return [(t[1]-t[0])/2 if i==0 else (t[-1]-t[-2])/2 if i==len(t)-1 else (t[i+1]-t[i-1])/2 for i in range(len(t))]


def linear_weighted(weights,values):
    assert len(weights)==len(values)
    return sum(w*v for w,v in zip(weights,values))


def auc_from_predose_baseline(traced_auc0, baseline, nonzero_endpoint_weight):
    assert baseline >= 0
    return traced_auc0-nonzero_endpoint_weight*baseline


def interval_witness(lo,hi,target,weights):
    """Witness via common convex interpolation; not a fitted human concentration profile."""
    amin=linear_weighted(weights,lo)
    amax=linear_weighted(weights,hi)
    if not amin <= target <= amax: return None
    alpha=(target-amin)/(amax-amin) if amax!=amin else 0.0
    return [a+(b-a)*alpha for a,b in zip(lo,hi)]


def audit(src):
    t=[0]+src['times_postdose_h']
    allweights=trapezoid_weights(t)
    assert sum(allweights)==24.0
    effective_w=allweights[1:]
    assert sum(effective_w)==23.5
    scenarios=[0,4,6,8,12,16,20,24]
    out={'schema_version':1,'phase':'P2-AK','source_json_sha256':hashlib.sha256(SOURCE.read_bytes()).hexdigest(),
         'trapezoid_time_h':t,'postdose_trapezoid_weights_h':effective_w,'baseline_coefficient_h':sum(effective_w),
         'baseline_scenarios_pg_ml':scenarios,'arms':{},
         'limitations':['Visual intervals are subjective and not certified bounds or intervals from independent readers.',
                        'Missing raw subject-level concentrations and predose baselines; Table 1 AUC is not a blinded independent comparator.',
                        'AUC compatibility conditional on manual envelopes does not validate the figure or the paper.',
                        'No verification of dose proportionality, bioavailability, terminal elimination or individual exposure.']}
    for name,arm in src['dose_arms'].items():
        pts=arm['points']
        assert [p['hour'] for p in pts]==src['times_postdose_h']
        lo=[p['low'] for p in pts]; center=[p['center'] for p in pts]; hi=[p['high'] for p in pts]
        assert all(0<=a<=c<=b for a,c,b in zip(lo,center,hi))
        target=arm['table_auc0_24_pg_h_ml']
        a_lo=linear_weighted(effective_w,lo); a_mid=linear_weighted(effective_w,center); a_hi=linear_weighted(effective_w,hi)
        b_critical=(a_hi-target)/sum(effective_w)
        modeled=[]
        for b in scenarios:
            m_lo=auc_from_predose_baseline(a_lo,b,sum(effective_w))
            m_center=auc_from_predose_baseline(a_mid,b,sum(effective_w))
            m_hi=auc_from_predose_baseline(a_hi,b,sum(effective_w))
            witness=interval_witness(lo,hi,target+b*sum(effective_w),effective_w)
            modeled.append({'assumed_baseline_pg_ml':b,'lower_raw_subtracted_auc_pg_h_ml':round(m_lo,4),
              'center_raw_subtracted_auc_pg_h_ml':round(m_center,4),'upper_raw_subtracted_auc_pg_h_ml':round(m_hi,4),
              'table_auc_within_conditional_visual_envelope':witness is not None,
              'positive_upper_margin_above_table_pg_h_ml':round(m_hi-target,4),
              'witness_curve_at_table_if_feasible': None if witness is None else [round(v,4) for v in witness]})
        out['arms'][name]={'dose_mg':arm['dose_mg'],'table_auc0_24_pg_h_ml':target,
            'table_cmax_mean_pg_ml':arm['table_cmax_mean_pg_ml'],
            'visual_conditional_auc_zero_baseline':{'low':round(a_lo,4),'center':round(a_mid,4),'high':round(a_hi,4)},
            'center_vs_table_gap_pg_h_ml':round(target-a_mid,4),
            'table_over_center_auc_ratio':round(target/a_mid,6),
            'critical_nonnegative_baseline_upper_feasibility_pg_ml':round(b_critical,6),
            'scenario_results':modeled,'underlying_points_with_visibility':pts}
    p2aj=src['1mg_SL_P2AJ_comparator']
    out['one_mg_reference']={'table_auc0_24_pg_h_ml':p2aj['table_auc0_24_pg_h_ml'],
      'digitized_auc_zero_baseline_pg_h_ml':p2aj['traced_auc0_baseline_pg_h_ml'],
      'generous_scan_window_auc_upper_zero_baseline_pg_h_ml':p2aj['manual_stress_upper_auc0_baseline_pg_h_ml'],
      'table_above_subjective_1mg_upper_at_b0_pg_h_ml':round(p2aj['table_auc0_24_pg_h_ml']-p2aj['manual_stress_upper_auc0_baseline_pg_h_ml'],4)}
    return out


def main():
    a=argparse.ArgumentParser();a.add_argument('--input',type=Path,default=SOURCE);a.add_argument('--output',type=Path,default=ROOT/'data/p2ak-results.json');args=a.parse_args()
    src=json.loads(args.input.read_text())
    val=audit(src);args.output.parent.mkdir(parents=True,exist_ok=True)
    args.output.write_text(json.dumps(val,ensure_ascii=False,sort_keys=True,indent=2)+'\n')
    for name,arm in val['arms'].items():
        print(name,'zero-baseline AUC',arm['visual_conditional_auc_zero_baseline'],'table',arm['table_auc0_24_pg_h_ml'], 'b_critical',arm['critical_nonnegative_baseline_upper_feasibility_pg_ml'])

if __name__=='__main__':main()
