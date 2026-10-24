#!/usr/bin/env python3
"""P2-AH: independent trapezoid/accounting audit of the previously frozen P2-U Price digitization.

Every figure datum is an existing approximate analyst read; NOT an individual observation.
No individual PK fits, no rate calibration, and no probabilistic error interpretation.
"""
import argparse
import hashlib
import json
import math
from pathlib import Path

BASE = Path(__file__).resolve().parents[1]

def trapezoid(times, values):
    if not len(times) == len(values) or len(times) < 2:
        raise ValueError('invalid array lengths')
    if not all(math.isfinite(float(x)) for x in [*times,*values]):
        raise ValueError('nonfinite input')
    if any(b <= a for a,b in zip(times,times[1:])):
        raise ValueError('times must strictly increase')
    return math.fsum((t1-t0)*(c0+c1)/2 for t0,t1,c0,c1 in zip(times,times[1:],values,values[1:]))

def trapezoid_weights(times):
    if len(times)<2 or any(b<=a for a,b in zip(times,times[1:])):
        raise ValueError('bad grid')
    return [(times[1]-times[0])/2] + [(times[j+1]-times[j-1])/2 for j in range(1,len(times)-1)] + [(times[-1]-times[-2])/2]

def baseline_corrected_auc(times, raw, predose):
    if times[0] != 0 or abs(raw[0]-predose)>1e-8:
        raise ValueError('predose mismatch')
    return trapezoid(times,[x-predose for x in raw])

def half_life_two_points(t1,t2,c1,c2,baseline):
    if not t2>t1 or not all(math.isfinite(x) for x in [t1,t2,c1,c2,baseline]):
        raise ValueError('invalid times/inputs')
    a,b = c1-baseline,c2-baseline
    if a<=0 or b<=0 or a<=b:
        return None
    return (t2-t1)*math.log(2)/math.log(a/b)

def run(data):
    price = data['studies']['Price1997_figure1']
    oldtimes = list(map(float,price['hours']))
    y = list(map(float,price['y']))
    width = list(map(float,price['nominal_se_or_read_width']))
    if oldtimes != [1,2,3,4,6,8,12,18,24]:
        raise ValueError('frozen old Price grid changed')
    if data['price_author_table_auc_mean'] != 2109:
        raise ValueError('frozen Price AUC changed')
    ts=[0.0]+oldtimes
    coefs=trapezoid_weights(ts)
    tolerance = math.fsum(k*w for k,w in zip(coefs,[0.0]+width))
    scenarios=[]
    for baseline in [0.,6.,12.,18.,24.]:
        raw=[baseline]+y
        baseline_corrected=baseline_corrected_auc(ts,raw,baseline)
        naive_raw=trapezoid(ts,raw)
        t24_relative=y[-1]-baseline
        residual=(2109-baseline_corrected)
        scenarios.append({'assumed_predose_pg_ml':baseline,'observed_figure_raw_auc_0_24_pg_h_ml':round(naive_raw,6),
                          'figure_baseline_corrected_auc_0_24_pg_h_ml':round(baseline_corrected,6),
                          'reported_table_baseline_corrected_auc_0_24_pg_h_ml':2109,
                          'reported_minus_digitized_pg_h_ml':round(residual,6),
                          'reported_to_digitized_ratio':round(2109/baseline_corrected,8),
                          'read_width_based_upper_auc_0_24':round(baseline_corrected+tolerance,6),
                          'reported_minus_upper_read_width':round(residual-tolerance,6),
                          'last_sample_excess_pg_ml':t24_relative})
    # One additional independent diagrammatic data point: archived full mean curve t=1..24 with peak ~450.
    a1_24=trapezoid(oldtimes,y)
    # If table AUC is computed by trapezoid on this exact grid and baseline=0, the 0..1 trapezoid would
    # have to be much larger than allowed by the only two samples (0h predose, 1h ~450).
    implied_0_1=2109-a1_24
    # Compare raw late concentrations to baseline-corrected apparent slopes; NOT systemic elimination.
    t8_idx=oldtimes.index(8)
    t24_idx=oldtimes.index(24)
    half_lives=[]
    for b in [0,6,12,18,21,22,23,23.5,24]:
        half_lives.append({'assumed_predose_pg_ml':b,'apparent_8_24_h':half_life_two_points(8,24,y[t8_idx],y[t24_idx],b)})
    # AUC commutes with arithmetic mean if same fixed sampling times and baseline subtraction.
    subject_example=[[10,120,40,10],[20,110,45,20],[5,90,35,10]]
    example_t=[0.,1.,2.,4.]
    per_subject=[baseline_corrected_auc(example_t,row,row[0]) for row in subject_example]
    mean_curve=[math.fsum(row[j] for row in subject_example)/len(subject_example) for j in range(len(example_t))]
    means_auc=baseline_corrected_auc(example_t,mean_curve,mean_curve[0])
    return {'schema_version':1,'phase':'P2-AH','is_clinical_validation':False,
      'input':{'origin':'P2-U frozen manual figure digitization; 1mg sublingual E2; 6 postmenopausal participants',
               'grid_hours':ts,'digitized_figure_1_y_pg_ml_at_1_to_24h':y,
               'manual_read_widths_not_probabilistic_errors_pg_ml':width,
               'source_article':'https://doi.org/10.1016/S0029-7844(96)00513-3',
               'full_text_transcription':'https://www.academia.edu/122086991/Single_Dose_Pharmacokinetics_of_Sublingual_Versus_Oral_Administration_of_Micronized_17%CE%B2_Estradiol',
               'method_confirmed':'predose corrected before PK computation; table AUC0-24; trapezoid; same 0,1,2,3,4,6,8,12,18,24h grid'},
      'trapezoid_weights_hours':coefs,
      'auc_digitized_raw_1_24_pg_h_ml':a1_24,
      'maximum_simultaneous_auc_read_width_perturbation_pg_h_ml':tolerance,
      'required_auc_0_1_to_match_table_if_raw_later_points_frozen_and_zero_baseline':implied_0_1,
      'actual_trapezoid_0_1_if_0h_zero_and_1h_450':225,
      'scenarios':scenarios,'baseline_sensitive_apparent_half_life':half_lives,
      'mean_auc_commutation_demonstration':{'per_subject_auc':per_subject,'arithmetic_mean_of_individual_aucs':math.fsum(per_subject)/len(per_subject),
                                             'auc_of_mean_curve':means_auc},
      'interpretation_guardrails':[
      'The paper explicitly describes 0h baseline subtraction, AUC0-24, trapezoid; AUC0-inf extrapolation is not an explanation.',
      'Figure values and interval widths are historical analyst digitizations, not the original six individuals.',
      'AUC of an arithmetic mean equals arithmetic mean of individual trapezoidal AUCs on a shared grid; simple noncommutation is not an explanation.',
      'AUC discrepancy remains unresolved; do not force-fit 2109 as an additional pseudo-point.',
      'Mean-curve late apparent half-life is baseline-sensitive and not an individual systemic clearance estimate.',
      'No patient exposure or PK efficacy inference supported.']}

def main():
    p=argparse.ArgumentParser()
    p.add_argument('--input',type=Path,default=BASE/'data/p2u-observed-aggregates.json')
    p.add_argument('--output',type=Path,default=BASE/'data/p2ah-price-auc-audit.json')
    p.add_argument('--overwrite',action='store_true')
    args=p.parse_args()
    if args.output.exists() and not args.overwrite:
        p.error('output already exists; use separate path or --overwrite')
    o=run(json.loads(args.input.read_text()))
    args.output.parent.mkdir(parents=True,exist_ok=True)
    args.output.write_text(json.dumps(o,ensure_ascii=False,indent=2)+'\n')
    print(f"written {args.output}; raw 1-24 = {o['auc_digitized_raw_1_24_pg_h_ml']}; table 2109")

if __name__=='__main__':main()
