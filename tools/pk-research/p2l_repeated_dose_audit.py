"""P2-L: fair fixed-model repeat-dose projection vs exposed aggregate studies.

No personal records, no inferred actual dosing history, no refitting of production PK.
A cohort's median and group mean cannot be scored as paired individual points.
"""
import argparse
import json
import math
from pathlib import Path
import models as m

ROOT=Path(__file__).resolve().parents[2]
STUDY=ROOT/"docs/pk-research/p2/p2l-repeated-dose-source-audit.json"
PARAMS=ROOT/"pk-engine/src/main/resources/pk-params.json"
CATALOG=ROOT/"docs/pk-research/p2/evidence-catalog.json"

def make_kernels(params,catalog):
    meta=catalog["comparison_model"]
    return {
      "HRT_frozen": lambda t:m.current(t,params),
      "Featherline_80kg_frozen":lambda t:m.feather(t,meta,80)
    }

def repeat_dose_scenario(kernel, dose_mg=0.5, interval_h=6, base_pg_ml=30, omitted_last=False, previous_count=120):
    if not math.isfinite(dose_mg) or dose_mg<=0 or not math.isfinite(interval_h) or interval_h<=0:
        raise ValueError("Positive finite dose and interval required")
    if not math.isfinite(base_pg_ml) or base_pg_ml<0 or previous_count<20:
        raise ValueError("Finite nonnegative baseline and sufficient history required")
    prior=[(-interval_h*j,dose_mg) for j in range(1,previous_count+1) if not (omitted_last and j==1)]
    before=m.population(kernel,prior,0,base_pg_ml)
    after=m.population(kernel,[(0,dose_mg)]+prior,1.5,base_pg_ml)
    return {"predose_pg_ml":before,"90min_postdose_pg_ml":after,
      "prior_dose_omitted":omitted_last,
      "scenario_is_hypothetical":True}

def audit(studies,params,catalog):
    rows={r["id"]:r for r in studies["studies"]}
    yaish=rows["Yaish2023"];kari=rows["Kariyawasam2025"];bar=rows["BarOn2026"]
    assert yaish["pilot_status"].startswith("PREVIOUSLY_SEEN")
    assert rows["BarOn2024_interim"]["cohort"]==bar["cohort"]
    assert kari["table3_sublingual_n"]==38 and kari["total_eligible_patients"]==286
    assert kari["early_and_trough_extremes_excluded_in_E2_E1_analysis"]
    assert bar["thrombotic_events_observed"]==0
    assert studies["gates"]["new_independent_blinded_pk_validation_sets"]==0
    cfg=studies["model_simulation"]
    args=dict(dose_mg=cfg["dose_mg"],interval_h=cfg["interval_h"],
              base_pg_ml=cfg["assumed_baseline_pg_ml"],previous_count=cfg["previous_doses"])
    models={}
    for name,kernel in make_kernels(params,catalog).items():
        models[name]={
          "perfect_6h_schedule":repeat_dose_scenario(kernel,**args),
          "missing_previous_6h_dose":repeat_dose_scenario(kernel,omitted_last=True,**args)}
    return {
      "source_role":"EXPOSED_CONDITIONAL_SYMMETRIC_COMPARISON_NOT_BLIND_VALIDATION",
      "yaish_study_is_prior_P0_evidence":True,
      "yaish_measured_predose_group_mean_pg_ml":yaish["trough_6mo_e2_mean_pmol_l"]/studies["conversion_pmol_per_pg_ml"],
      "yaish_measured_90min_median_pg_ml":yaish["postdose_90min_median_pmol_l"]/studies["conversion_pmol_per_pg_ml"],
      "yaish_measured_90min_iqr_pg_ml":[v/studies["conversion_pmol_per_pg_ml"] for v in yaish["postdose_90min_iqr_pmol_l"]],
      "yaish_median_and_mean_are_NOT_same_statistic":True,
      "kari_e2_last_visit_mean_pg_ml":kari["table4_last_visit_mean_e2_pmol_l"]/studies["conversion_pmol_per_pg_ml"],
      "kari_reported_mean_e1_over_e2_ratio":kari["table4_reported_mean_e1_over_e2_ratio"],
      "kari_ratio_of_means_is_NOT_mean_of_ratios":True,
      "kari_ratio_of_two_reported_means":kari["table4_last_visit_mean_e1_pmol_l"]/kari["table4_last_visit_mean_e2_pmol_l"],
      "kari_excluded_extreme_draws_and_unknown_times_assigned_mid":True,
      "baron_2026_measures_coagulation_NOT_PK_curve":True,
      "baron2024_and_2026_not_separate_people_assumed":True,
      "baron2026_thrombotic_events_observed":0,
      "published_biomarker_change_does_NOT_estimate_clinical_vte_risk":True,
      "comparisons_are_not_individual_residuals":True,
      "models":models,
      "new_independent_blinded_pk_sets":0,
      "clinical_accuracy_established":False,
      "model_replacement_authorized":False}

def main():
    ap=argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--out",required=True)
    a=ap.parse_args()
    things=[json.loads(f.read_text()) for f in (STUDY,PARAMS,CATALOG)]
    result=audit(*things)
    out=Path(a.out)
    if out.exists():raise ValueError("Never overwrite a previous report")
    out.parent.mkdir(parents=True,exist_ok=True)
    out.write_text(json.dumps(result,indent=2,sort_keys=True,ensure_ascii=False)+"\n")
    print("P2-L observational context only, no human PK accuracy claim:",out)

if __name__=="__main__":main()
