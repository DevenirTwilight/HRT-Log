#!/usr/bin/env python3
"""P2-AX: metadata-only SL E2 source qualification and synthetic scoring contracts.

Never reads individual health data. Clinical/external validation requires separate
pre-registered human approval, licensed data access and locked model predictions.
"""
import argparse
import json
import math
from pathlib import Path

REQUIRED = (
    "cohort_id", "source_url", "route", "formulation",
    "numeric_serial_e2_available", "actual_dose_sample_clock_available",
    "assay_e2_traceable", "baseline_and_prior_doses_documented",
    "late_8h_e2_available", "reuse_permission_confirmed",
    "unseen_after_model_lock", "independent_cohort",
)
CHECKS = (
    ("route", lambda r: r["route"] == "SL_E2", "ROUTE_NOT_SL_E2"),
    ("formulation", lambda r: r["formulation"] == "ordinary_micronized", "FORMULATION_MISMATCH"),
    ("numeric_serial_e2_available", lambda r: r["numeric_serial_e2_available"], "NO_ACCESSIBLE_SERIAL_E2"),
    ("actual_dose_sample_clock_available", lambda r: r["actual_dose_sample_clock_available"], "CLOCK_NOT_VERIFIED"),
    ("assay_e2_traceable", lambda r: r["assay_e2_traceable"], "E2_ASSAY_NOT_TRACEABLE"),
    ("baseline_and_prior_doses_documented", lambda r: r["baseline_and_prior_doses_documented"], "BACKGROUND_HISTORY_UNRESOLVED"),
    ("reuse_permission_confirmed", lambda r: r["reuse_permission_confirmed"], "REUSE_NOT_CONFIRMED"),
    ("unseen_after_model_lock", lambda r: r["unseen_after_model_lock"], "NOT_PRELOCK_BLIND"),
    ("independent_cohort", lambda r: r["independent_cohort"], "COHORT_INDEPENDENCE_NOT_ESTABLISHED"),
)
BOOL_FIELDS = (
    "numeric_serial_e2_available", "actual_dose_sample_clock_available",
    "assay_e2_traceable", "baseline_and_prior_doses_documented",
    "late_8h_e2_available", "reuse_permission_confirmed",
    "unseen_after_model_lock", "independent_cohort",
)

def audit_record(rec):
    missing = [name for name in REQUIRED if name not in rec]
    if missing:
        raise ValueError("missing registry keys: " + ",".join(missing))
    if any(type(rec[k]) is not bool for k in BOOL_FIELDS):
        raise ValueError("registry flags must be explicit bool")
    if not all(isinstance(rec[k], str) and rec[k] for k in
               ("cohort_id", "source_url", "route", "formulation")):
        raise ValueError("invalid source identification")
    blocks = [code for _, predicate, code in CHECKS if not predicate(rec)]
    core = not blocks
    tail = core and rec["late_8h_e2_available"]
    if core and not tail:
        blocks.append("NO_OBSERVED_LATE_8H_E2")
    # This is a *reviewable metadata candidate*, never automatic acceptance.
    return {"cohort_id": rec["cohort_id"], "metadata_candidate": core,
            "late_tail_candidate": tail,
            "blocked_by": blocks,
            "decision": "HUMAN_REVIEW_REQUIRED" if core else "HOLD"}

def audit_registry(records):
    ids = [r["cohort_id"] for r in records]
    if len(set(ids)) != len(ids):
        raise ValueError("duplicate cohort_id: deduplication needed")
    audited = [audit_record(r) for r in records]
    return {
        "phase": "P2-AX",
        "date": "2026-10-11",
        "reviewed_sources": len(audited),
        "metadata_candidates": sum(r["metadata_candidate"] for r in audited),
        "late_tail_candidates": sum(r["late_tail_candidate"] for r in audited),
        "locked_external_validation_performed": False,
        "production_replacement_gate": "P2-C_HOLD",
        "sources": audited
    }

def profiled_shape_sse(shape, values, weights=None):
    """Descriptive least-squares fit y=b+G*h with b unconstrained, G>0.

    IMPORTANT: fitting b,G to test observations leaks information for absolute
    predictive accuracy; NEVER pass this diagnostic as an external score.
    """
    n = len(shape)
    if n < 3 or n != len(values):
        raise ValueError("at least three paired samples required")
    w = [1.0] * n if weights is None else list(weights)
    if len(w) != n:
        raise ValueError("weight length mismatch")
    for v in list(shape) + list(values) + w:
        if not isinstance(v, (int, float)) or not math.isfinite(v):
            raise ValueError("non-finite input")
    if any(a <= 0 for a in w):
        raise ValueError("weights must be positive")
    total = sum(w)
    xm = sum(a * x for a, x in zip(w, shape)) / total
    ym = sum(a * y for a, y in zip(w, values)) / total
    den = sum(a * (x-xm)**2 for a, x in zip(w, shape))
    if den <= 1e-20:
        raise ValueError("flat sampled model cannot identify gain")
    gain = sum(a*(x-xm)*(y-ym) for a,x,y in zip(w,shape,values)) / den
    if gain <= 0:
        raise ValueError("gain not positive; model not eligible")
    baseline = ym - gain*xm
    sse = sum(a*(y-baseline-gain*x)**2 for a,x,y in zip(w,shape,values))
    return {"baseline_diagnostic_only": baseline, "gain_diagnostic_only": gain,
            "weighted_sse_diagnostic_only": sse, "residual_df": n-2}

def locked_prediction_rmse(observed, prediction, analytical_sd):
    """Fixed prediction standardized RMSE; disallows refitting after observation.

    Not a clinical accuracy/coverage claim; sd must be a justified, pre-locked
    analytical uncertainty on the same unit/platform, not a guessed CV.
    """
    if not (len(observed) == len(prediction) == len(analytical_sd)) or not observed:
        raise ValueError("paired non-empty observations required")
    if any(not math.isfinite(x) for seq in (observed,prediction,analytical_sd)
           for x in seq) or any(s <= 0 for s in analytical_sd):
        raise ValueError("invalid observations or SD")
    return math.sqrt(sum(((o-p)/s)**2 for o,p,s in zip(
        observed,prediction,analytical_sd))/len(observed))

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--registry", required=True)
    ap.add_argument("--output", required=True)
    args = ap.parse_args()
    source = Path(args.registry)
    dest = Path(args.output)
    if dest.exists():
        raise SystemExit("refusing to overwrite result: " + str(dest))
    records = json.loads(source.read_text(encoding="utf-8"))["sources"]
    result = audit_registry(records)
    dest.write_text(json.dumps(result, ensure_ascii=False, indent=2,
                               sort_keys=True) + "\n", encoding="utf-8")
    print("P2-AX metadata audit:", result["reviewed_sources"], "sources;",
          result["metadata_candidates"], "candidate(s); P2-C HOLD")

if __name__ == "__main__":
    main()
