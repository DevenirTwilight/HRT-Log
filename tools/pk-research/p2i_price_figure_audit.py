"""P2-I source-internal consistency audit. Not clinical PK calibration."""
import argparse
import json
import hashlib
import math
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
SOURCE=ROOT/'docs/pk-research/p2/p2i-price-figure-points.json'

def trapezoid(rows,field):
    rows=sorted(rows,key=lambda z:z['t'])
    if len(rows)<2 or rows[0]['t']!=0 or rows[-1]['t']!=24:
        raise ValueError('Expected full 0 to 24h grid')
    if any(a['t']>=b['t'] for a,b in zip(rows,rows[1:])):
        raise ValueError('Nonunique or unordered times')
    if any(not math.isfinite(z[field]) or z[field]<0 for z in rows):
        raise ValueError('Invalid concentration')
    return math.fsum((b['t']-a['t'])*(a[field]+b[field])/2
                     for a,b in zip(rows,rows[1:]))

def audit(x):
    if x['purpose']!='VISUAL_MANUAL_EXPLORATORY_DIGITIZATION_NOT_AUTHOR_DATA':
        raise ValueError('Incorrect evidence status')
    pts=x['plot_points']
    assert [z['t'] for z in pts]==[0,1,2,3,4,6,8,12,18,24]
    if any(not (0<=z['lo']<=z['central']<=z['hi']) for z in pts):
        raise ValueError('Malformed sensitivity bounds')
    lo,mid,hi=(trapezoid(pts,k) for k in ('lo','central','hi'))
    commons=x['wikimedia_2019_redraw']['numeric_points_by_visual_reading']
    if [t for t,v in commons]!=[z['t'] for z in pts]:
        raise ValueError('External graphic nominal grid mismatch')
    second=trapezoid([{'t':t,'v':v} for t,v in commons],'v')
    actual=x['table1_mean_subject_level_baseline_subtracted_auc0_24']
    return {
      'status':'UNRESOLVED_PUBLICATION_GRAPH_TABLE_RECONCILIATION',
      'author_reported_baseline_subtracted_auc_mean':actual,
      'author_reported_auc_sd':x['table1_sd'],
      'manual_raw_figure_auc_central':mid,
      'manual_raw_figure_auc_sensitivity_lower':lo,
      'manual_raw_figure_auc_sensitivity_upper':hi,
      'commons_same_source_digitized_approx_auc':second,
      'table_minus_manual_central':actual-mid,
      'table_minus_manual_upper':actual-hi,
      'manual_approximation_NOT_original_figure_data':True,
      'time_zero_not_observed_in_figure':True,
      'individual_auc_mean_equals_auc_of_means_only_when_same_times_weights':True,
      'baseline_would_reduce_auc_if_figure_raw_and_baseline_nonnegative':True,
      'manual_bounds_are_NOT_confidence_intervals':True,
      'commons_not_independent_human_cohort':True,
      'proof_of_paper_error':False,
      'independent_locked_human_evidence_added':0,
      'clinical_accuracy_established':False,
      'production_replacement_authorized':False}
def main():
    p=argparse.ArgumentParser()
    p.add_argument('--out',required=True)
    args=p.parse_args()
    raw=SOURCE.read_bytes()
    result=audit(json.loads(raw))
    result['source_sha256']=hashlib.sha256(raw).hexdigest()
    dst=Path(args.out)
    if dst.exists():raise ValueError('No overwrite')
    dst.parent.mkdir(parents=True,exist_ok=True)
    dst.write_text(json.dumps(result,indent=2,sort_keys=True,ensure_ascii=False)+'\n')
    print('Research source consistency only:',dst)
if __name__=='__main__':main()
