#!/usr/bin/env python3
"""P2-AZ retrospective exposed-study group deletion and complexity stress.

Study aggregation is the unit: Price1997, Rosano1997_PK25, Komesaroff1998.
This RESELECTS a fresh finite-shape grid using ONLY the OTHER TWO scored
studies within each fold; it is NOT prospective blindness because these
public cohorts and model architecture were already known to investigators.
Never describe P2-X's previously all-study-selected 15 as clean holdout.
No person-level PK data, no production or prescription calculations.
"""
from __future__ import annotations

import argparse
import base64
import itertools
import json
import math
from pathlib import Path
from p2aw_sampling_design import (
    frozen_candidates, legacy_normalized, m2_normalized, read_inputs,
)

ROOT = Path(__file__).resolve().parents[2]
P2 = ROOT / "docs/pk-research/p2"
DESIGN = P2 / "p2az-retrospective-transfer/p2az-design.json"
PRICE = P2 / "p2i-price-figure-points.json"
ROSANO = P2 / "p2e-source-metrics.json"
KOM = P2 / "p2d-komesaroff-table1.json"
FROZEN = P2 / "history-2026-10-10/source-packages/P2-AS-research-source.zip"
STUDIES = ("Price1997", "Rosano1997", "Komesaroff1998")
COMPLEXITY = {"Legacy": 0, "M1": 3, "M2": 5}
PRE_EXPOSED = "EXPOSED_STUDY_RETROSPECTIVE_ONLY"


def load_study_data():
    design = json.loads(DESIGN.read_text(encoding="utf-8"))
    price = json.loads(PRICE.read_text(encoding="utf-8"))
    ros = json.loads(ROSANO.read_text(encoding="utf-8"))
    kom = json.loads(KOM.read_text(encoding="utf-8"))
    if design["phase"] != "P2-AZ" or tuple(design["cohorts"]) != STUDIES:
        raise ValueError("P2-AZ design or grouping changed")
    if price["units"]["concentration"] != "pg/mL" or price["purpose"] != "VISUAL_MANUAL_EXPLORATORY_DIGITIZATION_NOT_AUTHOR_DATA":
        raise ValueError("Price non-author graph identity lost")
    if ros["Rosano1997"]["n_PK"] != 25 or not ros["Rosano1997"]["dispersion"].startswith("SD"):
        raise ValueError("Rosano cohort/SD identity lost")
    if kom["n"] != 10 or kom["reported_dispersion"] != "SEM":
        raise ValueError("Komesaroff cohort/SEM identity lost")
    if len(price["plot_points"]) != 10 or [r["t"] for r in price["plot_points"]] != [0,1,2,3,4,6,8,12,18,24]:
        raise ValueError("Price digitized cohort grid changed")
    if [x["minutes"] for x in ros["Rosano1997"]["points"]] != [10,20,40,60]:
        raise ValueError("Rosano grid changed")
    if [x["minutes"] for x in kom["observations"]] != [0,15,30]:
        raise ValueError("Komesaroff grid changed")
    c0 = kom["observations"][0]["mean_pmol_l"]
    if abs(c0-89.4)>1e-12:
        raise ValueError("Komesaroff measured predose changed")
    raw = {
        "Price1997": {
            "P2-I": [(float(p["t"]), float(p["central"])) for p in price["plot_points"] if p["t"]>0],
            "P2-AJ": [(float(t),float(y)) for t,y in design["price_aj_scan_reading_pgml"].items()],
        },
        "Rosano1997": [(p["minutes"]/60.,float(p["mean_pmol_l"])) for p in ros["Rosano1997"]["points"]],
        "Komesaroff1998": [(p["minutes"]/60.,float(p["mean_pmol_l"])) for p in kom["observations"] if p["minutes"]>0],
    }
    if any(t != raw["Price1997"]["P2-I"][i][0] for i,(t,y) in enumerate(raw["Price1997"]["P2-AJ"])):
        raise ValueError("P2-AJ manually re-read plotted time grid changed")
    return design, raw, c0


def shape_data(raw, kind, price_baseline, ros_baseline, kom_baseline, figure):
    values = {
        "Price1997": raw["Price1997"][figure],
        "Rosano1997": raw["Rosano1997"],
        "Komesaroff1998": raw["Komesaroff1998"],
    }
    baselines = {"Price1997":price_baseline,"Rosano1997":ros_baseline,
                 "Komesaroff1998":kom_baseline}
    anchors = {"Price1997":1., "Rosano1997":1.,
               "Komesaroff1998":.5}
    out = {}
    for study in STUDIES:
        points = values[study]
        b=baselines[study]
        a=next((y-b for t,y in points if abs(t-anchors[study])<1e-12),None)
        if a is None or a <= 0:
            raise ValueError("missing or invalid cohort normalization anchor")
        if any(not math.isfinite(y) or y < 0 for t,y in points):
            raise ValueError("raw measured total concentrations must be finite and nonnegative")
        # Small NEGATIVE baseline-subtracted values are legitimate near the tail:
        # Price AJ 18h manual read 23.85 minus assumed b=24 is -0.15.
        # Do NOT clip to zero: doing so biases the shape comparison.
        out[study]=tuple((t,(y-b)/a) for t,y in points if abs(t-anchors[study])>1e-12)
        if not out[study]:
            raise ValueError("only an anchor and no scored time point")
    return out


def make_grids(design):
    p=design["parameter_grid"]
    counts={}
    groups={}
    one=[]
    for n,kf,ke in itertools.product(p["n_fast"],p["k_fast_per_h"],p["k_elim_per_h"]):
        if not kf>ke>0:
            raise ValueError("bad candidate grid")
        one.append({"n_fast":n,"k_fast_per_h":kf,"k_elim_per_h":ke,
                    "k_slow_per_h":.2,"effective_slow_weight":0.})
    dual=[]
    for n,kf,ke,ks,w in itertools.product(
        (n for n in p["n_fast"] if n>1),
        p["k_fast_per_h"],p["k_elim_per_h"],p["slow_k_per_h"],
        p["effective_slow_weight"]
    ):
        dual.append({"n_fast":n,"k_fast_per_h":kf,"k_elim_per_h":ke,
                     "k_slow_per_h":ks,"effective_slow_weight":w})
    groups["M1"]=one
    groups["M2"]=dual
    counts={name:len(x) for name,x in groups.items()}
    if counts!={"M1":32,"M2":216}:
        raise ValueError("unreviewed candidate-grid size")
    return groups


def mse_for_study(points, fn, anchor):
    a=fn(anchor)
    if not math.isfinite(a) or a<=0:
        raise ValueError("invalid model anchor")
    residuals=[(fn(t)/a-r)**2 for t,r in points]
    return math.fsum(residuals)/len(residuals)


def study_scores(points, old_source, grids, frozen15):
    old = lambda t: legacy_normalized(t,old_source)
    scoring=lambda fn: {study:mse_for_study(points[study],fn,
        .5 if study=="Komesaroff1998" else 1.) for study in STUDIES}
    values={"Legacy":[{"model":None,"losses":scoring(old)}]}
    for family,paras in grids.items():
        values[family]=[]
        for i,model in enumerate(paras):
            fn=lambda t,p=model: m2_normalized(t,p)
            values[family].append({"model":i,"losses":scoring(fn)})
    values["FrozenP2X15"]=[]
    for i,model in enumerate(frozen15):
        fn=lambda t,p=model:m2_normalized(t,p)
        values["FrozenP2X15"].append({"model":i,"losses":scoring(fn)})
    return values


def choose(rows, training):
    if not training or not all(k in STUDIES for k in training):
        raise ValueError("invalid training cohort list")
    return min(rows,key=lambda v:(math.fsum(v["losses"][k] for k in training)/len(training),
                                  v["model"] if v["model"] is not None else -1))


def score(x):
    return round(x,7)


def analyze(design, raw, kom_baseline, old_source, freeze15):
    grids=make_grids(design)
    if len(freeze15)!=15:
        raise ValueError("P2-X frozen candidate count changed")
    folds=[]
    scenarios=0
    for fig,bp,br in itertools.product(
        design["figure_versions"],
        design["baseline_assumptions"]["Price1997_pgml"],
        design["baseline_assumptions"]["Rosano1997_pmol_l"]
    ):
        points=shape_data(raw,None,bp,br,kom_baseline,fig)
        all_rows=study_scores(points,old_source,grids,freeze15)
        scenarios+=1
        for held in STUDIES:
            train=tuple(s for s in STUDIES if s!=held)
            winners={name:choose(all_rows[name],train) for name in ("Legacy","M1","M2","FrozenP2X15")}
            selected={
              name:{
                "candidate_index":win["model"],
                "train_mean_mse":score(math.fsum(win["losses"][k] for k in train)/2),
                "heldout_mse":score(win["losses"][held]),
              }
              for name,win in winners.items()
            }
            oracle=min(all_rows["FrozenP2X15"],key=lambda v:v["losses"][held])
            penalized={}
            for penalty in design["train_penalty_lambda"]:
                family=min(("Legacy","M1","M2"),
                    key=lambda name:(math.fsum(winners[name]["losses"][k] for k in train)/2
                                     + penalty*COMPLEXITY[name],COMPLEXITY[name]))
                penalized[str(penalty)]={"chosen_family":family,
                    "heldout_mse":score(winners[family]["losses"][held])}
            folds.append({
              "scenario":{"price_figure":fig,"price_assumed_baseline_pgml":bp,
                          "rosano_assumed_baseline_pmol_l":br},
              "heldout_study":held,
              "training_studies":list(train),
              "scores":selected,
              "frozen15_heldout_oracle_mse_CONTAMINATED_NOT_PREDICTIVE":score(oracle["losses"][held]),
              "complexity_penalty_sensitivity":penalized,
            })
    if scenarios!=18 or len(folds)!=54:
        raise ValueError("expected 18 provenance/baseline scenarios * 3 heldout cohorts")
    comparisons={}
    for name in ("M1","M2","FrozenP2X15"):
        by=[f for f in folds if f["scores"][name]["heldout_mse"]<f["scores"]["Legacy"]["heldout_mse"]]
        comparisons[name+"_beats_Legacy_count"]=len(by)
        comparisons[name+"_evaluated_folds"]=len(folds)
    per_study={}
    for held in STUDIES:
        sub=[f for f in folds if f["heldout_study"]==held]
        per_study[held]={
            name+"_beats_Legacy":sum(x["scores"][name]["heldout_mse"]<x["scores"]["Legacy"]["heldout_mse"] for x in sub)
            for name in ("M1","M2","FrozenP2X15")
        }
        per_study[held]["scenarios"]=len(sub)
    return {
        "phase":"P2-AZ","source_exposed_previous_design_leakage":True,
        "reselected_shape_grid_trained_only_on_other_two_studies_each_fold":True,
        "frozen15_candidate_set_was_exposed_to_all_three_studies_UPSTREAM":True,
        "figure_P2I_and_P2AJ_are_same_six_person_Price_cohort":True,
        "heldout_metrics_use_observed_group_anchor_not_absolute_prediction":True,
        "legacy_is_fixed_production_shape_no_trainable_parameters":True,
        "training_groups_per_fold":2,
        "independent_human_studies_available_for_retrospective_score":3,
        "individual_level_unseen_validation":False,
        "aicc_formal_model_comparison_possible":False,
        "nominal_shape_parameter_counts":COMPLEXITY,
        "grid_candidate_counts":{"M1":len(grids["M1"]),"M2":len(grids["M2"]),"historical_15":len(freeze15)},
        "n_group_statistical_independence_not_assumed_across_timepoints":True,
        "mixed_assays_populations_formulations_no_absolute_transfer":True,
        "two_cohort_training_choice_can_be_unstable":True,
        "score_is_unweighted_normalized_group_shape_MSE_not_likelihood":True,
        "source_scenarios":scenarios,"folds":folds,
        "summary":comparisons,"by_heldout_study":per_study,
        "new_independent_locked_human_pk_sets":0,
        "clinical_accuracy_established":False,"P2_C_HOLD":True
    }


def run():
    design,raw,kom_base=load_study_data()
    p2x, old=read_inputs(FROZEN)
    candidates=frozen_candidates(p2x)
    return analyze(design,raw,kom_base,old,candidates)


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("--output",required=True,type=Path)
    p.add_argument("--emit",action="store_true",help="print result on one JSON line for public CI archival intake")
    args=p.parse_args()
    if args.output.exists():
        p.error("will not overwrite prior archive")
    data=run()
    args.output.parent.mkdir(parents=True,exist_ok=True)
    args.output.write_text(json.dumps(data,ensure_ascii=False,sort_keys=True,indent=2,allow_nan=False)+"\n",encoding="utf-8")
    if args.emit:
        print("P2AZ_RESULT_B64_BEGIN")
        print(base64.b64encode(args.output.read_bytes()).decode("ascii"))
        print("P2AZ_RESULT_B64_END")
    print("P2-AZ: 18 source/baseline scenarios, 54 study-deletion folds; not blinded; P2-C HOLD")


if __name__=="__main__":
    main()
