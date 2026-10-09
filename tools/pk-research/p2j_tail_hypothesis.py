"""P2-J: counterfactual tail extrapolation, NOT a claim about Price author methods."""
import argparse
import json
import math
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
SOURCE=ROOT/"docs/pk-research/p2/p2i-price-figure-points.json"

def tail_area(c24,h):
    if not math.isfinite(c24) or not math.isfinite(h) or c24<0 or h<=0:
        raise ValueError("Nonnegative finite E2 concentration; finite positive half life")
    return c24*h/math.log(2)

def end_slope_half_life(c18,c24,b):
    if not all(math.isfinite(v) for v in (c18,c24,b)) or not(0<=b<c24<c18):
        raise ValueError("Two descending positive baseline-corrected concentrations required")
    return 6*math.log(2)/math.log((c18-b)/(c24-b))

def trapezoid(raw,b):
    t=[r["t"] for r in raw]
    if t!=[0,1,2,3,4,6,8,12,18,24]:
        raise ValueError("Unexpected Price grid")
    c=[b]+[r["central"] for r in raw[1:]]
    return sum((c[i]+c[i+1])*(t[i+1]-t[i])/2 for i in range(len(t)-1))

def audit(doc):
    c=doc["plot_points"]
    c18=next(r["central"] for r in c if r["t"]==18)
    c24=next(r["central"] for r in c if r["t"]==24)
    auc_raw0=trapezoid(c,0)
    auc_table=doc["table1_mean_subject_level_baseline_subtracted_auc0_24"]
    h=18.0
    b=20.0 # intentionally hypothetical, NOT observed Price baseline
    baseline_corrected=trapezoid(c,b)-24*b
    derived_h=end_slope_half_life(c18,c24,b)
    return {
       "evidence_role":"COUNTERFACTUAL_EXPOSED_SOURCE_ONLY",
       "author_table_reported_auc0_24":auc_table,
       "hypothetical_raw_auc0_24_if_predose_zero":auc_raw0,
       "hypothetical_raw_tail_auc_24_infinity_using_author_mean_half_life":tail_area(c24,h),
       "hypothetical_raw_auc0_infinity":auc_raw0+tail_area(c24,h),
       "hypothetical_predose_background_pg_ml":b,
       "baseline_corrected_auc0_24_at_hypothetical_background":baseline_corrected,
       "group_mean_last_two_baseline_corrected_points_halflife_h":derived_h,
       "baseline_corrected_auc0_infinity_with_hypothetical_background_and_derived_h":
         baseline_corrected+tail_area(c24-b,derived_h),
       "raw_last_two_points_half_life_h":end_slope_half_life(c18,c24,0),
       "author_used_auc_infinity_proven":False,
       "author_table_auc_definition_remains_AUC0_24":True,
       "group_curve_halflife_is_not_mean_individual_halflife":True,
       "not_external_validation":True,
       "clinical_accuracy_established":False,
       "production_change_authorized":False
    }

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("--out",required=True)
    args=p.parse_args()
    result=audit(json.loads(SOURCE.read_text()))
    path=Path(args.out)
    if path.exists():raise ValueError("Refuse overwrite")
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(result,indent=2,ensure_ascii=False,sort_keys=True)+"\n")
    print("Counterfactual tail only, not original author calculation:",path)
if __name__=="__main__":main()
