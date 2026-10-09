"""P2-P: conditional sampling-time separability for exposed P2-O PK hypotheses.

All profile curves are dimensionless H(t)/H(1h); this is NOT a planned
clinical draw order, actual concentration, precision model, or optimization
using Fisher information. A measurable one-hour increment and actual
predose baseline are ASSUMED; any unmodeled baseline/scale nuisance makes
these scores optimistic. All sources were exposed before candidate choice.
"""
import argparse
import itertools
import json
import math
from pathlib import Path

import p2o_slow_tail_scan as prior
import p2n_transit_convolution as model

ROOT=Path(__file__).resolve().parents[2]
CFG=ROOT/"docs/pk-research/p2/p2p-conditional-sampling-design.json"
SCAN=ROOT/"docs/pk-research/p2/p2o-slow-tail-parameter-scan.json"
PRICE=ROOT/"docs/pk-research/p2/p2i-price-figure-points.json"
ROSANO=ROOT/"docs/pk-research/p2/p2e-source-metrics.json"

def profiles(design,scan,price,rosano):
    """Apply the P2-O acceptance definition literally, not a new fit."""
    times=design["candidate_times_h"]
    if times!=sorted(set(times)) or times[0]<=0:
        raise ValueError("Unique ascending positive sampling times required")
    pts={r["t"]:r["central"] for r in price["plot_points"]}
    assert all(pts[t]==scan["acceptance"][f"price_manual_{t}h_pg_ml"]
               for t in (1,2,4))
    rs=rosano["Rosano1997"]["points"]
    ratio=next(x["mean_pmol_l"] for x in rs if x["minutes"]==40)/next(
        x["mean_pmol_l"] for x in rs if x["minutes"]==20)
    assert ratio==scan["acceptance"]["metric_rosano_raw40over20"]
    kept={str(b):[] for b in design["baseline_cases_price_pg_ml"]}
    evaluated=0
    for case in prior.scan_inputs(scan):
        evaluated+=1
        metric=prior.raw_case_metrics(case)
        accepted=[b for b in design["baseline_cases_price_pg_ml"]
                  if prior.accept(metric,b,scan["acceptance"])]
        if not accepted:
            continue
        one=model.unnormalized(1.,case)
        if not math.isfinite(one) or one<=0:
            raise ValueError("Bad reference increment")
        h=[model.unnormalized(t,case)/one for t in times]
        if any(not math.isfinite(x) or x<0 for x in h):
            raise ValueError("Bad nonnegative model output")
        q=prior.predose_q6(case,scan["repeated_event_scenario"]["preceding_events"])
        if not math.isfinite(q) or q<=0:
            raise ValueError("Bad repeated-dose trough")
        row={"time_values":h,"q6_relative_to_1mg_1h":q}
        for b in accepted:
            kept[str(b)].append(row)
    if evaluated!=scan["expected_evaluated_combinations"]:
        raise ValueError("P2-O grid identity mismatch")
    return kept,evaluated

def pair_summary(rows,time_indices,sep_threshold,trough_factor=2.):
    """Threshold concerns separation of *model predictions*, not measured error.

    A pair is indistinguishable only if at every requested time the
    difference is <= sep_threshold*H(1h). The result is an optimistic
    binary discriminability heuristic, not a statistical test.
    """
    if not time_indices or any(not isinstance(i,int) or i<0 for i in time_indices):
        raise ValueError("Nonempty, valid sample indices required")
    if not math.isfinite(sep_threshold) or sep_threshold<0:
        raise ValueError("Invalid separability threshold")
    if not math.isfinite(trough_factor) or trough_factor<=1:
        raise ValueError("Invalid divergence factor")
    ambiguous=0
    still_large=0
    biggest_ratio=1.
    for ia in range(len(rows)):
        a=rows[ia]
        for ib in range(ia+1,len(rows)):
            b=rows[ib]
            if max(abs(a["time_values"][i]-b["time_values"][i]) for i in time_indices)>sep_threshold:
                continue
            ambiguous+=1
            qa=a["q6_relative_to_1mg_1h"];qb=b["q6_relative_to_1mg_1h"]
            ratio=max(qa,qb)/min(qa,qb)
            biggest_ratio=max(biggest_ratio,ratio)
            if ratio>=trough_factor:
                still_large+=1
    return {"unresolved_pairs":ambiguous,
            "unresolved_with_trough_divergence_at_least_2x":still_large,
            "max_trough_divergence_among_unresolved":biggest_ratio}

def evaluate(design,scan,price,rosano):
    kept,count=profiles(design,scan,price,rosano)
    times=design["candidate_times_h"]
    thresholds=design["toy_pairwise_separation_threshold_over_one_hour_increment"]
    all_scenarios=[]
    for b in design["baseline_cases_price_pg_ml"]:
        rows=kept[str(b)]
        pairs=len(rows)*(len(rows)-1)//2
        by_threshold=[]
        for threshold in thresholds:
            singles=[{"time_h":t,**pair_summary(rows,[i],threshold)}
                     for i,t in enumerate(times)]
            combos=[{"time_h":[times[i],times[j]],
                      **pair_summary(rows,[i,j],threshold)}
                     for i,j in itertools.combinations(range(len(times)),2)]
            best_any=min(combos,key=lambda z:(
                z["unresolved_pairs"],z["unresolved_with_trough_divergence_at_least_2x"],z["time_h"]))
            late_only=[z for z in combos if min(z["time_h"])>=design["late_only_min_hour"]]
            best_late=min(late_only,key=lambda z:(
                z["unresolved_pairs"],z["unresolved_with_trough_divergence_at_least_2x"],z["time_h"]))
            best_late_goal=min(late_only,key=lambda z:(
                z["unresolved_with_trough_divergence_at_least_2x"],z["unresolved_pairs"],z["time_h"]))
            wanted=[next(z for z in combos if z["time_h"]==ts)
                    for ts in design["selected_report_sets_h"]]
            by_threshold.append({"difference_threshold_normalized":threshold,
                "single_time":singles,"two_time_preselected":wanted,
                "best_any_pair_by_ambiguity_count":best_any,
                "best_after_4h_by_ambiguity_count":best_late,
                "best_after_4h_by_trough_goal":best_late_goal})
        all_scenarios.append({"Price_baseline_pg_ml_HYPOTHETICAL":b,
            "model_candidates_from_P2O":len(rows),"total_unordered_candidate_pairs":pairs,
            "threshold_sensitivity":by_threshold})
    return {"phase":"P2-P",
      "source_grid_evaluated":count,
      "late_dose_q6_is_model_generated_not_real_human_concentration":True,
      "one_hour_amplitude_and_predose_baseline_assumed_known":True,
      "separation_threshold_is_NOT_measurement_accuracy_or_CI":True,
      "ICH_M15_guidance_does_not_certify_this_design":True,
      "output_is_NOT_a_Fisher_information_D_optimal_design":True,
      "clinical_blood_draw_schedule_prescribed":False,
      "Doll144_used_as_amplitude_anchor":False,
      "clinical_accuracy_established":False,
      "new_LOCKED_EXTERNAL_human_data_sets":0,
      "production_change_authorized":False,
      "scenarios":all_scenarios}

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("--out",required=True)
    args=p.parse_args()
    dst=Path(args.out)
    if dst.exists():
        raise ValueError("Never overwrite a previous research result")
    inputs=[json.loads(p.read_text()) for p in (CFG,SCAN,PRICE,ROSANO)]
    result=evaluate(*inputs)
    dst.parent.mkdir(parents=True,exist_ok=True)
    dst.write_text(json.dumps(result,indent=2,sort_keys=True,ensure_ascii=False)+"\n")
    print("P2-P conditional informativeness, not clinical trial design:",dst)

if __name__=="__main__":
    main()
