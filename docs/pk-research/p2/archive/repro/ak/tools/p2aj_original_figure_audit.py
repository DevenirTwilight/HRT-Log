#!/usr/bin/env python3
"""P2-AJ deterministic audit of manual source pixel coordinates, no PK parameter fitting."""
import argparse
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / 'data/p2aj-manual-figure1-reading.json'


def image_to_value(y, panel, calibration):
    if panel == 'upper':
        u = calibration['upper']
        return 500 - (y - u['y_pixel_at_500']) * 200/(u['y_pixel_at_300']-u['y_pixel_at_500'])
    if panel == 'lower':
        u = calibration['lower']
        return (u['y_pixel_at_0'] - y) * 180 / (u['y_pixel_at_0'] - u['y_pixel_at_180'])
    raise ValueError('No interpolation across broken axis')


def trapz(t, y):
    if len(t) != len(y):
        raise ValueError('mismatched times and values')
    if any(b <= a for a, b in zip(t,t[1:])):
        raise ValueError('nonincreasing sample grid')
    return sum((b-a)*(u+v)/2 for a,b,u,v in zip(t,t[1:],y,y[1:]))


def trapz_weights(t):
    return [(t[1]-t[0])/2 if i==0 else
            (t[-1]-t[-2])/2 if i==len(t)-1 else
            (t[i+1]-t[i-1])/2 for i in range(len(t))]


def analyze(source):
    axis=source['axis_calibration']
    points=source['point_reading']
    assert len(points)==9
    times=[0]+[p['hour'] for p in points]
    values=[0]+[image_to_value(p['y_px'],p['panel'],axis) for p in points]
    widths=[0]+[p['visual_window_pg_ml'] for p in points]
    w=trapz_weights(times)
    auc=trapz(times,values)
    increase=sum(v*q for v,q in zip(w,widths))
    target=source['table1_1mg_sl']['auc0_24_mean_pg_h_ml']
    p2u=trapz(times,[0]+source['previous_p2u_approximate_pg_ml'])
    points_res=[]
    for p,value,wi,wei in zip(points,values[1:],widths[1:],w[1:]):
        points_res.append({**p,'value_pg_ml':round(value,5),'trapezoid_weight_h':wei,
                           'pixel_to_pg_ml_sensitivity':round(200/87 if p['panel']=='upper' else 180/634,5),
                           'weighted_visual_window_pg_h_ml':round(wei*wi,4)})
    baseline_scenarios=[]
    for b in (0,6,12,18,24):
        estimate=auc-23.5*b
        baseline_scenarios.append({'assumed_b_pg_ml':b,
            'baseline_subtracted_auc_pg_h_ml':round(estimate,4),
            'deficit_to_table_pg_h_ml':round(target-estimate,4),
            'generous_all_points_up_upper_bound':round(estimate+increase,4),
            'target_still_exceeds_upper_by_pg_h_ml':round(target-estimate-increase,4)})
    return {
      'schema_version':1,'phase':'P2-AJ','source_study':source['study'],
      'input_sha256':hashlib.sha256(SOURCE.read_bytes()).hexdigest(),
      'time_grid_hours':times,'source_pdf_sha256':source['source_pdf_sha256'],
      'source_crop_sha256':source['source_crop_sha256'],
      'digitized_points':points_res,
      'figure1_trapezoid_raw_0h_assumed_zero_pg_h_ml':round(auc,4),
      'figure1_previous_p2u_0h_zero_pg_h_ml':round(p2u,4),
      'new_minus_previous_pg_h_ml':round(auc-p2u,4),
      'author_table_auc_pg_h_ml':target,
      'zero_baseline_deficit_pg_h_ml':round(target-auc,4),
      'zero_baseline_auc_ratio_table_to_digitized':round(target/auc,6),
      'manual_stress_window_weighted_total_pg_h_ml':round(increase,4),
      'zero_baseline_stress_upper_auc_pg_h_ml':round(auc+increase,4),
      'zero_baseline_gap_even_after_stress_pg_h_ml':round(target-auc-increase,4),
      'baseline_scenarios':baseline_scenarios,
      'trapezoid_weights_hours':w,
      'assumptions':[
        'No 0h plotted curve point: 0h baseline-subtracted increment imposed as zero from author Methods, not observed concentration.',
        'Mean predose background b treated as unknown; table-minus-figure mismatch has conditional nonnegative-b maximum at b=0.',
        'Visual windows are subjective stress ranges; not reader reliability, lab error, SD, CI or scientific impossibility bounds.',
        'Only 1 mg SL visibly traced fully. Overlapping 0.5 / 0.25 mg routes not treated as fully independently digitized.',
        'Research-only source consistency audit; previous exposure excludes blinded generalization claims.'
      ]
    }


def main():
    ap=argparse.ArgumentParser();ap.add_argument('--source',type=Path,default=SOURCE);ap.add_argument('--output',type=Path,default=ROOT/'data/p2aj-results.json')
    a=ap.parse_args();src=json.loads(a.source.read_text(encoding='utf-8'))
    v=analyze(src);a.output.parent.mkdir(parents=True,exist_ok=True)
    a.output.write_text(json.dumps(v,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(f"AUC={v['figure1_trapezoid_raw_0h_assumed_zero_pg_h_ml']:.1f}; table={v['author_table_auc_pg_h_ml']}; stress upper={v['zero_baseline_stress_upper_auc_pg_h_ml']:.1f}; output={a.output}")

if __name__=='__main__':main()
