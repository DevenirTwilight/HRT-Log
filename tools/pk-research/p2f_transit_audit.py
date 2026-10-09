"""Analytical early-rise feasibility for exposed human aggregate statistics.

Research-only. No production model read or changed. No human dose fitting.
"""
import argparse
import json
import math
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
INPUT=ROOT/"docs/pk-research/p2/p2f-cohort-and-transit-source.json"

def gamma_ratio_double(order,rate,t):
    if order<1 or rate<=0 or t<=0:raise ValueError("invalid gamma")
    return 2**(order-1)*math.exp(-rate*t)

def corrected_ratio(upper,lower,baseline):
    if not (0<=baseline<lower<upper):raise ValueError("invalid concentration")
    return (upper-baseline)/(lower-baseline)

def gamma_shape_from_three_later_points(values,baseline):
    c10,c20,c40,c60=(values[x] for x in (10,20,40,60))
    if not 0<=baseline<c10:raise ValueError("baseline invalid")
    a=corrected_ratio(c40,c20,baseline)
    b=corrected_ratio(c60,c40,baseline)
    alpha=math.log(a/b)/math.log(4/3)
    k=3*(alpha*math.log(2)-math.log(a))
    return {
     "baseline_assumed_pmol_l":baseline,
     "gamma_order_continuous":alpha+1,
     "effective_rate_per_hour":k,
     "predicted_C20_over_C10_increment_ratio":2**alpha*math.exp(-k/6),
     "observed_C20_over_C10_increment_ratio":corrected_ratio(c20,c10,baseline),
     "not_an_estimated_physiological_baseline":True
    }

def evaluate(src):
    ids={s["id"]:s for s in src["studies"]}
    y=ids["Yager2022_TransPrEP"];th=ids["Abdelmawla2023"]
    rr={int(k):v for k,v in ids["Rosano1997_PK25"]["means_by_minute"].items()}
    assert y["oral_sublingual_combined_n"]==13
    assert th["same_cohort_as"]==y["id"]
    b_threshold=(rr[10]*rr[40]-rr[20]**2)/(rr[10]+rr[40]-2*rr[20])
    result={
      "research_only":True,"external_validation_insufficient":True,
      "clinical_accuracy_established":False,"production_change":False,
      "Yager_oral_plus_sublingual_group_n":13,
      "thesis_same_cohort":True,
      "pooled_28_4_hour_half_life_is_not_sublingual_clearance":True,
      "rosano_raw_ratio_40_over_20":rr[40]/rr[20],
      "rosano_nonnegative_baseline_single_gamma_10_20_40_threshold_pmol_l":b_threshold,
      "rosano_min_integer_order_at_zero_baseline_from_20_40":4,
      "rosano_three_late_points_gamma_sensitivity":
         [gamma_shape_from_three_later_points(rr,b) for b in (0,50,100,150,200,225)],
      "rosano_n25_real_baseline_known":False,
      "mechanism_identified":False}
    assert result["rosano_raw_ratio_40_over_20"]>4
    return result

def main():
    p=argparse.ArgumentParser()
    p.add_argument("--out",required=True)
    a=p.parse_args()
    data=json.loads(INPUT.read_text())
    result=evaluate(data)
    path=Path(a.out)
    if path.exists():raise ValueError("refuse overwrite")
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(result,indent=2,sort_keys=True,ensure_ascii=False)+"\n")
    print("Research only; no model refit:",path)
if __name__=="__main__":main()
