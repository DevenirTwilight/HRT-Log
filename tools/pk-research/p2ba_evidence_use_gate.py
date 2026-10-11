#!/usr/bin/env python3
"""P2-BA: collapse pseudo-repeated sensitivity folds by original study,
and enforce release/use-case gating independent of optimistic shape scores.

This is NOT a clinical decision-support model and NEVER changes production.
Uses public P2-AZ exposed source findings, pinned to Git BLOB SHA-1.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import math
import statistics
from collections import Counter, defaultdict
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
P2=ROOT/"docs/pk-research/p2"
SOURCE=P2/"p2az-retrospective-transfer/p2az-results.json"
SOURCE_BLOB_SHA1="38fb3ccc4521f8f0d683d2c755c0370539831ed9"
GROUPS=("Price1997","Rosano1997","Komesaroff1998")
MODELS=("Legacy","M1","M2","FrozenP2X15")
PERMISSIONS={
    "legacy_pgml_historical":{"allow":True,"tier":"EXISTING_LEGACY_ESTIMATE",
      "requires_opt_in":False,"display_units":"pg/mL (legacy algorithm, not externally validated for individuals)",
      "condition":"retain existing production logic unchanged; prominently label historical empirical extrapolation"},
    "m2_normalized_research":{"allow":True,"tier":"RESEARCH_ONLY",
      "requires_opt_in":True,"display_units":"dimensionless H(t)/H(1h)",
      "condition":"show experimental model and 15 frozen candidate shapes, no measured individual scale"},
    "legacy_vs_m2_normalized":{"allow":True,"tier":"RESEARCH_ONLY",
      "requires_opt_in":True,"display_units":"dimensionless H(t)/H(1h) for BOTH",
      "condition":"same per-model 1h normalization, separate from legacy absolute concentration"},
    "m2_conditional_peak_time":{"allow":True,"tier":"RESEARCH_ONLY",
      "requires_opt_in":True,"display_units":"hours (model-only conditional)",
      "condition":"not actual person's Tmax and not dosing or blood-draw advice"},
    "m2_absolute_pgml":{"allow":False,"tier":"UNVERIFIED_ABSOLUTE",
      "requires_opt_in":False,"display_units":None,
      "condition":"individual baseline, gain, analytical calibration and external assessment absent"},
    "m2_90pct_prediction_interval":{"allow":False,"tier":"UNVERIFIED_COVERAGE",
      "requires_opt_in":False,"display_units":None,
      "condition":"15 parameter candidates are not a calibrated clinical prediction distribution"},
    "m2_personal_trough_prediction":{"allow":False,"tier":"UNVERIFIED_TAIL",
      "requires_opt_in":False,"display_units":None,
      "condition":"timing, prior dosing, metabolic rate, baseline and late validation insufficient"},
    "m2_default_engine_replacement":{"allow":False,"tier":"P2_C_HOLD",
      "requires_opt_in":False,"display_units":None,
      "condition":"no approved independent personal absolute-pg/ml deployment gate"},
    "m2_dose_recommendation":{"allow":False,"tier":"CLINICAL_UNAUTHORIZED",
      "requires_opt_in":False,"display_units":None,
      "condition":"research curve comparisons are not treatment guidance"},
    "convert_m2_to_pgml_using_doll_one_hour":{"allow":False,"tier":"UNVERIFIED_EXTRAPOLATION",
      "requires_opt_in":False,"display_units":None,
      "condition":"Doll's population total peak is neither individual above-baseline gain nor route-general calibration"}
}


def blob_sha(data:bytes)->str:
    return hashlib.sha1(("blob "+str(len(data))+chr(0)).encode("ascii")+data).hexdigest()


def load_source(path=SOURCE):
    content=Path(path).read_bytes()
    if blob_sha(content)!=SOURCE_BLOB_SHA1:
        raise ValueError("P2-AZ checked-in source Git BLOB SHA-1 changed")
    data=json.loads(content)
    if not data["P2_C_HOLD"] or not data["source_exposed_previous_design_leakage"]:
        raise ValueError("P2-AZ leakage/clinical limit missing")
    if data["source_scenarios"]!=18 or len(data["folds"])!=54:
        raise ValueError("expected exactly 18 paired sensitivity settings * 3 source folds")
    return data


def source_key(row):
    sc=row["scenario"]
    return (sc["price_figure"],sc["price_assumed_baseline_pgml"],sc["rosano_assumed_baseline_pmol_l"])


def choose_permission(request, opt_in=False, label_unverified=False):
    if request not in PERMISSIONS:
        raise ValueError("unsupported capability; fail closed")
    cap=PERMISSIONS[request]
    allowed=bool(cap["allow"] and (not cap["requires_opt_in"] or opt_in)
                 and (cap["tier"]!="EXISTING_LEGACY_ESTIMATE" or label_unverified))
    return {"capability":request,"allowed":allowed,"tier":cap["tier"],
            "units":cap["display_units"] if allowed else None,
            "requires_opt_in":cap["requires_opt_in"],
            "condition":cap["condition"]}


def rounded(x):
    if not math.isfinite(x):
        raise ValueError("nonfinite result")
    return round(x,6)


def summarize(data):
    folds=data["folds"]
    scenarios=defaultdict(dict)
    source_results={g:[] for g in GROUPS}
    for row in folds:
        group=row["heldout_study"]
        if group not in GROUPS or row["heldout_study"] in row["training_studies"] or set(row["training_studies"])!=set(GROUPS)-{group}:
            raise ValueError("study folds leaked across group boundaries")
        key=source_key(row)
        if group in scenarios[key]:
            raise ValueError("duplicate study scenario")
        if row["scores"]["Legacy"]["candidate_index"] is not None:
            raise ValueError("Legacy shape was tuned")
        record={}
        for model in MODELS:
            score=row["scores"][model]["heldout_mse"]
            if not isinstance(score,(int,float)) or score<0 or not math.isfinite(score):
                raise ValueError("invalid score")
            record[model]=score
        if record["Legacy"]==0 or record["M1"]==0:
            raise ValueError("relative performance denominator zero")
        scenarios[key][group]=record
        source_results[group].append(record)
    if len(scenarios)!=18 or any(set(v)!=set(GROUPS) for v in scenarios.values()):
        raise ValueError("source sensitivity variants do not preserve three cohort units")
    per_group={}
    for group,records in source_results.items():
        if len(records)!=18:
            raise ValueError("18 perturbations per original study expected")
        values={}
        for model in ("M1","M2","FrozenP2X15"):
            ratios=sorted(r[model]/r["Legacy"] for r in records)
            values[model+"_over_Legacy"]={
                "wins_sensitivity_scenarios":sum(v<1 for v in ratios),
                "min":rounded(min(ratios)),"median":rounded(statistics.median(ratios)),
                "max":rounded(max(ratios))
            }
        contrasts=sorted(r["M2"]/r["M1"] for r in records)
        values["M2_over_M1"]={
            "wins_sensitivity_scenarios":sum(v<1 for v in contrasts),
            "min":rounded(min(contrasts)),"median":rounded(statistics.median(contrasts)),
            "max":rounded(max(contrasts))}
        per_group[group]=values
    scenario_records=[]
    for (figure,price,ros),per in sorted(scenarios.items()):
        scenario_records.append({
            "price_figure_same_cohort":figure,
            "price_baseline_assumed_pgml":price,
            "rosano_baseline_assumed_pmol_l":ros,
            "m2_better_than_legacy_in_all_three_studies":all(v["M2"]<v["Legacy"] for v in per.values()),
            "m2_better_than_m1_in_all_three_studies":all(v["M2"]<v["M1"] for v in per.values()),
            "m2_worse_or_equal_m1_study_ids":[g for g in GROUPS if per[g]["M2"]>=per[g]["M1"]]
        })
    lambda_choice={key:Counter() for key in ("0","0.002","0.01","0.05")}
    for row in folds:
        for key in lambda_choice:
            family=row["complexity_penalty_sensitivity"][key]["chosen_family"]
            if family not in ("M1","M2","Legacy"):
                raise ValueError("unknown penalty-selected model")
            lambda_choice[key][family]+=1
    return {
        "phase":"P2-BA","source_phase":"P2-AZ","source_blob_sha1":SOURCE_BLOB_SHA1,
        "independent_study_count":3,
        "source_units":list(GROUPS),
        "parameter_source_price_figure_versions_same_subjects":True,
        "sensitivity_scenarios_per_study":18,
        "study_deletion_evaluations_total_but_not_independent":54,
        "all_three_study_wins_m2_legacy_scenarios":sum(r["m2_better_than_legacy_in_all_three_studies"] for r in scenario_records),
        "all_three_study_wins_m2_m1_scenarios":sum(r["m2_better_than_m1_in_all_three_studies"] for r in scenario_records),
        "per_study":per_group,
        "scenario_conditional_checks":scenario_records,
        "human_patient_absolute_predictions_validated":False,
        "observed_group_shapes_not_future_blinded":True,
        "development_exposure_of_all_three_studies":True,
        "frozen_p2x15_external_performance_unproven":True,
        "complexity_penalties_not_aic_bic_or_bayes":True,
        "artificial_penalty_family_choice_counts":{key:{m:lambda_choice[key][m] for m in ("Legacy","M1","M2")} for key in lambda_choice},
        "model_and_product_use_capabilities":{name:choose_permission(name,opt_in=True,label_unverified=True) for name in PERMISSIONS},
        "allowed_research_exposure_without_dosing_or_absolute_claims":True,
        "P2_C_HOLD":True,
        "no_new_independent_human_PK_cohorts":0,
        "full_documentation_not_automatic_app_integration":True,
    }


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("--out",type=Path,required=True)
    args=p.parse_args()
    if args.out.exists():
        p.error("refusing overwrite")
    data=summarize(load_source())
    args.out.parent.mkdir(parents=True,exist_ok=True)
    args.out.write_text(json.dumps(data,indent=2,sort_keys=True,ensure_ascii=False,allow_nan=False)+chr(10),encoding="utf-8")
    print("P2-BA: 3 true groups; 18 sensitivity scenarios; experimental normalized curves allowed; personal M2 pg/mL blocked")


if __name__=="__main__":
    main()
