"""P2-G source and input gate. No physiological fitting, no user/clinical data.

Only checks evidence/metadata readiness and trial overlap. NEVER treats an
administrative gate pass as verified clinical predictive accuracy.
"""
import argparse
import hashlib
import json
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
DEFAULT=ROOT/"docs/pk-research/p2/p2g-data-acquisition-registry.json"

def audit_registry(registry):
    studies=registry["studies"]
    if len({s["id"] for s in studies})!=len(studies):
        raise ValueError("Duplicate source ID")
    for study in studies:
        if not study.get("canonical_cohort_id"):
            raise ValueError("Missing canonical cohort for "+study["id"])
    by_cohort={}
    for item in studies:
        by_cohort.setdefault(item["canonical_cohort_id"],[]).append(item["id"])
    if registry["status"]["distinct_canonical_cohorts"] != len(by_cohort):
        raise ValueError("Independent-cohort count disagrees with manifest")
    return {"source_records":len(studies),"distinct_cohorts":len(by_cohort),
       "duplicate_publications_by_cohort":{k:v for k,v in by_cohort.items() if len(v)>1},
       "source_role_is_not_blind_validation":True,
       "all_sources_metadata_or_incomplete":True,
       "new_independent_locked_external_human_datasets":0}

def intake_gate(row):
    """Conservative review gate for hypothetical future permitted data.

    Never grants clinical verification; explicit existing publication/role
    leakage blocks use as LOCKED_EXTERNAL even if everything else is complete.
    """
    issues=[]
    expected={"analyte":"E2","route":"SUBLINGUAL","assay_verified":True,
        "dose_event_history_verified":True,"dose_time_origin_verified":True,
        "sampling_times_verified":True,"time_concentration_series_verified":True,
        "baseline_status":"VERIFIED_OR_EXPLICITLY_MODELED",
        "data_reuse_permission":"PERMITTED","independent_cohort_confirmed":True,
        "protocol_frozen_before_access":True,"previously_exposed":False}
    for key,required in expected.items():
        if row.get(key)!=required:issues.append(key)
    if not row.get("canonical_cohort_id"):issues.append("canonical_cohort_id")
    if not row.get("source_locator"):issues.append("source_locator")
    if row.get("dose_or_time_inferred_from_summary",False):issues.append("dose_or_time_inferred_from_summary")
    return {"eligible_for_separate_external_review":not issues,
        "blockers":issues,
        "external_validated":False,
        "clinical_accuracy_established":False,
        "does_not_authorize_population_model_update":True}

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("--input",default=str(DEFAULT))
    p.add_argument("--out",required=True)
    a=p.parse_args()
    path=Path(a.input)
    raw=path.read_bytes()
    registry=json.loads(raw)
    result=audit_registry(registry)
    result["registry_sha256"]=hashlib.sha256(raw).hexdigest()
    result["status"]="DATA_NOT_READY_FOR_INDEPENDENT_HUMAN_VALIDATION"
    result["clinical_accuracy_established"]=False
    result["formal_model_replacement_authorized"]=False
    output=Path(a.out)
    if output.exists():raise ValueError("Output exists; do not overwrite")
    output.parent.mkdir(parents=True,exist_ok=True)
    output.write_text(json.dumps(result,sort_keys=True,indent=2,ensure_ascii=False)+"\n")
    print("P2-G provenance and access audit only:",output)

if __name__=="__main__":main()
