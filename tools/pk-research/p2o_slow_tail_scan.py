"""P2-O: quantitative practical non-identifiability of SL-E2 dual input curves.

The source data are already exposed. This is a reproducible conditional
shape-space sensitivity audit, NOT an estimated human PK posterior,
confidence set, 90% prediction interval, or clinical validation.

Uses P2-N's stable positive Erlang convolution. No Doll peak or clinical
dose-to-concentration gain is used to fit any curve.
"""
import argparse
import itertools
import json
import math
from pathlib import Path
import p2n_transit_convolution as model

ROOT=Path(__file__).resolve().parents[2]
CFG=ROOT/"docs/pk-research/p2/p2o-slow-tail-parameter-scan.json"
PRICE=ROOT/"docs/pk-research/p2/p2i-price-figure-points.json"
ROSANO=ROOT/"docs/pk-research/p2/p2e-source-metrics.json"

def cfg_to_case(n,kf,ke,ks,w):
    return {"n_fast":n,"k_fast_h":kf,"k_elim_h":ke,
            "k_slow_h":ks,"slow_effective_weight":w}

def raw_case_metrics(case,early=True):
    h1=model.unnormalized(1.,case)
    if not math.isfinite(h1) or h1<=0:raise ValueError("Nonpositive h1")
    h=lambda t:model.unnormalized(t,case)/h1
    return {"r40_20":h(2/3)/h(1/3),
            "r2_1":h(2),
            "r4_1":h(4),
            "r30_15":h(.5)/h(.25)}

def auc_continuous(t,case):
    """Mass-balance identity from dX/dt=Input-ke*X (integrated over 0..t).

    AUC_0t=(F_input(t)-h_raw(t))/ke, before unit-normalization.
    """
    if not math.isfinite(t) or t<0:raise ValueError("Invalid time")
    ke=case["k_elim_h"]
    n=case["n_fast"];kf=case["k_fast_h"];ks=case["k_slow_h"]
    w=case["slow_effective_weight"]
    h1=model.unnormalized(1.,case)
    input_fraction=(1-w)*model.poisson_upper_tail_at_least(n,kf*t)+w*(-math.expm1(-ks*t))
    raw_auc=(input_fraction-model.unnormalized(t,case))/ke
    return max(0.,raw_auc)/h1

def auc_infinite(case):
    """All normalized absorbed input mass eventually disappears at ke."""
    return 1/(case["k_elim_h"]*model.unnormalized(1.,case))

def auc_price_discrete(case):
    return model.sampled_auc(case,[0,1,2,3,4,6,8,12,18,24])

def predose_q6(case,previous_count):
    if not isinstance(previous_count,int) or previous_count<1:
        raise ValueError("prior event count must be a positive integer")
    h1=model.unnormalized(1.,case)
    return .5*math.fsum(model.unnormalized(6*j,case)/h1 for j in range(1,previous_count+1))

def scan_inputs(cfg):
    dims=cfg["candidates"]
    for n,kf,ke,ks,w in itertools.product(
      dims["n_fast"],dims["k_fast_per_h"],dims["k_elim_per_h"],
      dims["k_slow_per_h"],dims["effective_slow_weight"]):
        # Including kslow==ke is essential; n=1 equality is handled analytically.
        yield cfg_to_case(n,kf,ke,ks,w)

def accept(metric,baseline,req):
    r2=(req["price_manual_2h_pg_ml"]-baseline)/(req["price_manual_1h_pg_ml"]-baseline)
    r4=(req["price_manual_4h_pg_ml"]-baseline)/(req["price_manual_1h_pg_ml"]-baseline)
    return (abs(metric["r40_20"]/req["metric_rosano_raw40over20"]-1)
               <=req["tolerance_rosano_relative"]+1e-12
       and abs(metric["r2_1"]/r2-1)<=req["tolerance_price_2h_to_1h_relative"]+1e-12
       and abs(metric["r4_1"]/r4-1)<=req["tolerance_price_4h_to_1h_relative"]+1e-12)

def details(case,metric,previous_count):
    return {"parameters":case,"shape":metric,
      "auc_0_24_discrete_over_h1_h":auc_price_discrete(case),
      "auc_0_24_continuous_over_h1_h":auc_continuous(24,case),
      "auc_0_infinity_over_h1_h":auc_infinite(case),
      "predose_q6_0_5mg_over_1mg_h1":predose_q6(case,previous_count)}

def best_matched_pair(rows):
    """Maximize trough ratio subject to within-1% resemblance of three early metrics."""
    best=None
    for i,a in enumerate(rows):
        for b in rows[i+1:]:
            ra=a["shape"];rb=b["shape"]
            d=max(abs(ra[k]-rb[k])/((ra[k]+rb[k])/2)
                 for k in ("r40_20","r2_1","r4_1"))
            if d>0.01:continue
            qa=a["predose_q6_0_5mg_over_1mg_h1"]
            qb=b["predose_q6_0_5mg_over_1mg_h1"]
            ratio=max(qa,qb)/min(qa,qb)
            if best is None or ratio>best["predose_ratio"]:
                best={"A":a,"B":b,"worst_early_metric_relative_separation":d,
                      "predose_ratio":ratio}
    return best

def summarize(cfg,price,rosano):
    pts={r["t"]:r for r in price["plot_points"]}
    assert pts[1]["central"]==cfg["acceptance"]["price_manual_1h_pg_ml"]
    assert pts[2]["central"]==cfg["acceptance"]["price_manual_2h_pg_ml"]
    assert pts[4]["central"]==cfg["acceptance"]["price_manual_4h_pg_ml"]
    rs=rosano["Rosano1997"]["points"]
    assert next(r["mean_pmol_l"] for r in rs if r["minutes"]==40)/next(
        r["mean_pmol_l"] for r in rs if r["minutes"]==20
    )==cfg["acceptance"]["metric_rosano_raw40over20"]
    allcases=list(scan_inputs(cfg))
    if len(allcases)!=cfg["expected_evaluated_combinations"]:
        raise ValueError("Unexpected Cartesian grid size")
    metricized=[(case,raw_case_metrics(case)) for case in allcases]
    scenarios=[]
    for baseline in cfg["acceptance"]["hypothetical_price_baselines_pg_ml"]:
        selected=[details(case,metric,cfg["repeated_event_scenario"]["preceding_events"])
                  for case,metric in metricized if accept(metric,baseline,cfg["acceptance"])]
        if not selected:
            scenarios.append({"hypothetical_price_baseline":baseline,"accepted":0})
            continue
        q=[s["predose_q6_0_5mg_over_1mg_h1"] for s in selected]
        auc=[s["auc_0_24_discrete_over_h1_h"] for s in selected]
        scenarios.append({"hypothetical_price_baseline":baseline,
          "accepted":len(selected),
          "q6_predose_min":min(q),"q6_predose_max":max(q),
          "q6_predose_max_over_min":max(q)/min(q),
          "auc_discrete_min":min(auc),"auc_discrete_max":max(auc),
          "best_pair_1percent_metrics":best_matched_pair(selected)})
    assert cfg["P2O_Doll_trained_or_targeted"] is False
    return {"phase":"P2-O", "total_grid":len(allcases),
        "equal_slow_and_elimination_rate_cases_included":sum(
             case["k_slow_h"]==case["k_elim_h"] for case in allcases),
        "source_status":"PUBLISHED_AGGREGATES_ALREADY_EXPOSED",
        "selection_tolerances_are_analyst_chosen_not_confidence_intervals":True,
        "Rosano_unknown_baseline_is_NOT_resolved":True,
        "Price_Figure1_vs_Table1_AUC_unresolved":True,
        "scenarios":scenarios,"Doll144_used_for_model_fit":False,
        "new_blind_human_PK_dataset_count":0,
        "clinical_accuracy_established":False,
        "90_percent_human_interval_coverage_established":False,
        "production_model_replacement_authorized":False}

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("--out",required=True)
    a=p.parse_args()
    result=summarize(*[json.loads(f.read_text()) for f in (CFG,PRICE,ROSANO)])
    output=Path(a.out)
    if output.exists():raise ValueError("Refuse to overwrite earlier research")
    output.parent.mkdir(parents=True,exist_ok=True)
    output.write_text(json.dumps(result,indent=2,sort_keys=True,ensure_ascii=False)+"\n")
    print("P2-O non-identifiability scan (not human accuracy validation):",output)

if __name__=="__main__":main()
