#!/usr/bin/env python3
"""P2-AY: error-aware anchor-normalized shape observability (model-only).

Consumes the real SHA-256 frozen P2-AW JSON from GitHub, NOT patient data.
All amplitudes/errors are analyst-chosen scenarios, never assay specifications
or clinical accuracy claims. Does not change any parameter or drug dose.
"""
import argparse
import hashlib
import json
import math
from pathlib import Path

AW_SHA256 = "ecb62798eb785d2c4ae6aca48a2dd5a2b0a6b4f65fd4b7fe8ed875271819df90"
EXPECTED_SOURCE = "P2-AW"
K = 2.0  # analyst-selected standardized contrast, NOT statistical test power
AMPLITUDES_PG_ML = (50, 150, 450)
ANALYTIC_SD_PG_ML = (1, 3, 10)
REQUIRED_TIMES = (0.25, 0.5, 1.0, 2.0, 4.0, 6.0, 8.0, 12.0, 24.0)


def read_frozen_aw(path):
    blob = Path(path).read_bytes()
    if hashlib.sha256(blob).hexdigest() != AW_SHA256:
        raise ValueError("P2-AW source SHA-256 changed: refusing analysis")
    item = json.loads(blob)
    if (item["phase"] != EXPECTED_SOURCE
            or item["frozen_m2_count"] != 15
            or item["frozen_total_fit_count"] != 40
            or item["clinical_model_replacement_authorized"]):
        raise ValueError("frozen model provenance/clinical gate changed")
    times = tuple(r["time_h"] for r in item["single_dose_normalized_shape_at_times"])
    if times != REQUIRED_TIMES:
        raise ValueError("source grid changed")
    for item_row in item["single_dose_normalized_shape_at_times"]:
        old = item_row["legacy"]
        lo, mid, hi = (item_row["m2_min"], item_row["m2_median"],
                       item_row["m2_max"])
        if (not all(math.isfinite(x) and x >= 0 for x in (old, lo, mid, hi))
                or not lo <= mid <= hi):
            raise ValueError("bad frozen model envelope")
    return item


def ratio_noise_factor(r):
    """First-order SD of R=(Yt-Y0)/(Y1-Y0): (sigma/A)*f(R).

    Assumes 3 DISTINCT independent samples, equal analytical SD sigma,
    constant additive background, exact t, and true A=E[Y1-Y0]>0.
    At t=1 the same anchor cannot be treated as an independent third sample:
    output therefore suppresses that trivial row's noise interpretation.
    """
    if not math.isfinite(r) or r < 0:
        raise ValueError("ratio must be non-negative, finite")
    return math.sqrt(1.0 + r*r + (r-1.0)**2)


def cross_ratio_covariance(r, s, amplitude, sd, same_nonanchor_sample=False):
    """Delta-method covariance for two ratios sharing measured 0h and 1h anchors.

    Separate non-anchor samples are independent. Same non-anchor sample uses
    the same observation and must have r==s. No biological covariance assumed.
    """
    vals = (r, s, amplitude, sd)
    if not all(math.isfinite(v) for v in vals) or r < 0 or s < 0 or amplitude <= 0 or sd <= 0:
        raise ValueError("invalid ratio or hypothetical measurement scenario")
    if same_nonanchor_sample and r != s:
        raise ValueError("same-sample ratios require same expected value")
    return (sd/amplitude)**2 * ((r-1)*(s-1) + r*s + (1 if same_nonanchor_sample else 0))


def envelope_gap(legacy, low, high):
    """Worst-case absolute gap to ANY frozen M2 candidate at this fixed t.

    Intervals are marginal ranges of 15 candidate values, not a probabilistic
    interval. If the Legacy value lies within the envelope, gap lower-bound=0.
    """
    if not all(math.isfinite(v) for v in (legacy, low, high)) or low > high:
        raise ValueError("bad frozen model range")
    if legacy < low:
        return low-legacy
    if legacy > high:
        return legacy-high
    return 0.0


def scaled(x, multiplier=1_000_000):
    if not math.isfinite(x) or x < 0:
        raise ValueError("expected finite nonnegative numeric value")
    return int(round(x*multiplier))


def analyze(aw):
    output = []
    for entry in aw["single_dose_normalized_shape_at_times"]:
        t = entry["time_h"]
        old, lo, hi = entry["legacy"], entry["m2_min"], entry["m2_max"]
        gap = envelope_gap(old, lo, hi)
        maximum_gap = max(abs(lo-old), abs(hi-old))
        # Covariance with a measured shared 0h and 1h anchor: conservative
        # first-order SD factor across the whole frozen marginal envelope.
        noise_coefficient = max(ratio_noise_factor(v) for v in (old, lo, hi))
        relevant = (t != 1.0 and gap > 0)
        minimum_amplitudes = {
            str(sd): (scaled(K*sd*noise_coefficient/gap, 1_000)
                      if relevant else None)
            for sd in ANALYTIC_SD_PG_ML
        }
        indicators = {}
        for amp in AMPLITUDES_PG_ML:
            for sd in ANALYTIC_SD_PG_ML:
                key = "A{}_sd{}".format(amp, sd)
                indicators[key] = (relevant and gap*amp >= K*sd*noise_coefficient)
        output.append({
            "time_h": t,
            "legacy_ratio_micro": scaled(old),
            "m2_min_ratio_micro": scaled(lo),
            "m2_max_ratio_micro": scaled(hi),
            "minimum_model_gap_micro": scaled(gap),
            "maximum_model_gap_micro": scaled(maximum_gap),
            "delta_method_worst_sd_factor_micro": scaled(noise_coefficient) if t != 1 else None,
            "minimum_A1h_increment_pgml_milli_for_2sd": minimum_amplitudes,
            "passes_artificial_2sd_rule": indicators if t != 1 else {},
            "A150_min_absolute_gap_pgml_milli": scaled(gap*150, 1_000),
        })
    return {
        "phase": "P2-AY",
        "date": "2026-10-11",
        "source_phase": "P2-AW",
        "frozen_aw_json_sha256": AW_SHA256,
        "model_only_not_human_observation": True,
        "individual_absolute_prediction_validated": False,
        "analytical_sd_scenarios_pgml": list(ANALYTIC_SD_PG_ML),
        "one_hour_increment_scenarios_pgml": list(AMPLITUDES_PG_ML),
        "two_sd_rule_is_analyst_stress_not_clinical_power": True,
        "constant_baseline_known_actual_clocks_assumed": True,
        "ratio_denominator_requires_reliable_measured_increase": True,
        "measurement_assumes_independent_equal_sd_samples": True,
        "no_laboratory_LLOQ_or_replicate_variances_inferred": True,
        "no_model_uncertainty_probability_coverage_claim": True,
        "no_new_locked_external_human_cohorts": 0,
        "production_replacement_gate": "P2-C_HOLD",
        "rows": output,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--frozen-aw", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    if args.output.exists():
        parser.error("refusing to overwrite existing output")
    frozen = read_frozen_aw(args.frozen_aw)
    result = analyze(frozen)
    args.output.write_text(json.dumps(result, ensure_ascii=False, sort_keys=True,
                                     indent=2, allow_nan=False) + "\n", encoding="utf-8")
    print("P2-AY frozen envelope/marginal noise audit PASS; P2-C HOLD")


if __name__ == "__main__":
    main()
