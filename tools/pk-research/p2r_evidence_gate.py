"""P2-R conservative evidence-readiness check; no automatic scientific or APK approval.

Test fixtures declaring fresh external cohorts do NOT prove the studies exist.
All source claims require independent primary-data audit and expert signoff.
10% benefit and 5% guardrail: PROVISIONAL internal thresholds, NOT ICH criteria.
"""
import argparse
import json
import math
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
LEDGER=ROOT/"docs/pk-research/p2/p2r-cohort-evidence-ledger.json"
POLICY=ROOT/"docs/pk-research/p2/p2r-upgrade-policy.json"

def declared_true(value):return value is True
def numeric(v):return type(v) in (int,float) and math.isfinite(v)
def audit_source(src,policy,known):
    problems=[]
    cid=src.get("cohort_id")
    if not cid or cid in known:problems.append("missing or previously exposed cohort identity")
    if src.get("exposure_status")!="LOCKED_EXTERNAL_UNSEEN":problems.append("not an unseen holdout")
    if not declared_true(src.get("independently_enrolled")):problems.append("independent enrolment unverified")
    if not src.get("data_hash") or not src.get("access_log_id") or not src.get("protocol_lock_hash"):
        problems.append("no verifiable data/access/protocol identity")
    for key in policy["required_data_fields"]:
        if not declared_true(src.get(key)):problems.append("source lacks validated "+key)
    return problems

def evaluate(ledger,policy,packet):
    destination=packet.get("target")
    source=packet.get("from_model")
    if (source,destination) not in (("M0_ONE_INPUT","M1_TRANSIT"),("M1_TRANSIT","M2_DUAL_INPUT")):
        raise ValueError("Only adjacent M0-M1 or M1-M2 review proposals allowed")
    errors=[]
    for name in ("training_data_subject_level_audited","candidate_math_verified",
                 "simpler_baseline_model_frozen","identifiability_profile_and_symmetries_audited",
                 "sample_size_precision_reviewed"):
        if not declared_true(packet.get(name)):errors.append("missing qualification "+name)
    for name in policy["required_locked_protocol_fields"]:
        if not declared_true(packet.get("protocol",{}).get(name)):
            errors.append("protocol not frozen/audited: "+name)
    if destination=="M1_TRANSIT":
        for name in ("early_prepeak_postpeak_data_audited","M0_noise_and_baseline_alternatives_checked"):
            if not declared_true(packet.get(name)):errors.append("M1 additional qualification: "+name)
    else:
        for name in ("true_late_single_dose_data","actual_repeat_dose_trough_series",
                     "absorption_vs_elimination_alternatives_tested"):
            if not declared_true(packet.get(name)):errors.append("M2 additional qualification: "+name)
    known={row["cohort_id"] for row in ledger["cohorts"]}
    qualified={}
    rejected={}
    ext=packet.get("external_cohorts",[])
    if not isinstance(ext,list):raise ValueError("external_cohorts must be array")
    seen=set()
    for i,record in enumerate(ext):
        if not isinstance(record,dict):record={}
        cid=record.get("cohort_id") or "MISSING_"+str(i)
        issues=audit_source(record,policy,known)
        if cid in seen:issues.append("duplicate candidate holdout cohort")
        seen.add(cid)
        if issues:rejected[cid]=issues
        else:qualified[cid]=record
    if len(qualified)<policy["min_independently_enrolled_heldout_cohorts_for_review"]:
        errors.append("fewer than two independently auditable locked holdout cohorts")
    outputs=packet.get("heldout_study_scores",[])
    if not isinstance(outputs,list):raise ValueError("heldout_study_scores must be array")
    results={}
    for row in outputs:
        if not isinstance(row,dict):raise ValueError("malformed result")
        cid=row.get("cohort_id")
        if not cid or cid in results:errors.append("duplicate/missing study-specific result")
        else:results[cid]=row
    for cid in qualified:
        row=results.get(cid,{})
        required=("primary_relative_improvement","primary_improvement_uncertainty_lower",
                  "other_target_relative_degradation")
        if any(not numeric(row.get(k)) for k in required):
            errors.append(cid+": missing finite study-specific comparison")
            continue
        if row["primary_relative_improvement"]<policy["provisional_improvement_threshold"]:
            errors.append(cid+": primary improvement below tentative threshold")
        if row["primary_improvement_uncertainty_lower"]<=0:
            errors.append(cid+": uncertainty includes zero benefit")
        if row["other_target_relative_degradation"]>policy["provisional_max_heldout_guardrail_degradation"]:
            errors.append(cid+": material degradation of other targets")
        for field in ("subject_clustered_uncertainty_audited","baseline_assay_and_BLQ_sensitivity_passed"):
            if not declared_true(row.get(field)):errors.append(cid+": missing "+field)
        if destination=="M2_DUAL_INPUT":
            if not declared_true(row.get("late_AUC_and_real_repeat_trough_each_improved")):
                errors.append(cid+": slow-tail functional outcomes not both improved")
    for cid in results:
        if cid not in qualified:errors.append(cid+": metric has no qualified heldout source")
    if not declared_true(packet.get("independent_scientific_review_signed")):
        errors.append("independent scientific review absent")
    return {
      "phase":"P2-R","target":destination,
      "review_readiness":"EVIDENCE_PACKET_READY_FOR_HUMAN_REVIEW_NOT_APPROVAL" if not errors else "BLOCKED_EXPLORATORY_ONLY",
      "qualified_heldout_cohorts":len(qualified),
      "rejected_sources":rejected,"blocking_reasons":sorted(set(errors)),
      "independent_human_90pct_coverage_confirmed":False,
      "scientific_accuracy_automatically_proven":False,
      "production_release_authorized":False
    }

def current_state(ledger):
    return {"phase":"P2-R","status":"G0_EXPLORATORY_ONLY",
      "source_reports":len(ledger["cohorts"]),
      "distinct_named_cohort_ids":len({x["cohort_id"] for x in ledger["cohorts"]}),
      "unseen_verified_heldout_PK_cohorts":0,
      "M0_M1_M2_can_be_researched":True,
      "P5_P95_coverage_established":False,"production_release_authorized":False}

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("--out",required=True)
    p.add_argument("--packet")
    a=p.parse_args()
    ledger=json.loads(LEDGER.read_text());policy=json.loads(POLICY.read_text())
    report=current_state(ledger)
    if a.packet:report["packet_review"]=evaluate(ledger,policy,json.loads(Path(a.packet).read_text()))
    dst=Path(a.out)
    if dst.exists():raise ValueError("Refusing overwrite of research output")
    dst.parent.mkdir(parents=True,exist_ok=True)
    dst.write_text(json.dumps(report,indent=2,ensure_ascii=False,sort_keys=True)+"\n")
    print("P2-R review gate, not approval:",dst)
if __name__=="__main__":main()
