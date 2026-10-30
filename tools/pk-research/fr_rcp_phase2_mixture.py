#!/usr/bin/env python3
"""Non-production *identifiability* scan for a two-timescale Nebido depot.

The synthetic comparison uses only the RCP's approximate, cross-sectional
group summaries (first peak ~11; second ~15; 3rd–5th steady peak/trough ~11/5
ng/mL), so these cannot serve as independent external clinical validation.

Candidate: total(t) = B + (11-B)*sum_j[(1-w)*h_fast(t-tj)+w*h_slow(t-tj)]
where h_fast/slow are normalized to peak 1 at 7 days. The weight w is a
peak-amplitude weight, NOT injected depot mass or bioavailability.
Pure Python stdlib; reads no user data, writes no production parameters.
"""
from __future__ import annotations
import argparse
import json
import math
from functools import lru_cache
from pathlib import Path

from fr_rcp_phase2 import find_fast_for_tpeak, bateman

ROOT = Path(__file__).resolve().parents[2]
LEDGER = ROOT / "docs/pk-research/fr-rcp-phase1/phase2-ledger.json"
DOSE_DAYS = (0., 42., 112., 182., 252.)
LN2 = math.log(2)


@lru_cache(maxsize=32)
def depot_coefficients(half_life_d: float) -> tuple[float, float, float]:
    slow = LN2 / (half_life_d * 24.)
    fast = find_fast_for_tpeak(slow, 7 * 24.)
    return fast, slow, bateman(7 * 24., fast, slow)


def normalized_depot(half_life_d: float, day: float) -> float:
    if day <= 0:
        return 0.
    fast, slow, normalizer = depot_coefficients(half_life_d)
    return bateman(day * 24., fast, slow) / normalizer


def shape_by_day(half_life_d: float, day_grid: list[float]) -> list[float]:
    slow = LN2 / (half_life_d * 24.)
    fast = find_fast_for_tpeak(slow, 7 * 24.)
    normalizer = bateman(7 * 24., fast, slow)
    return [bateman(max(0., day) * 24., fast, slow) / normalizer if day > 0 else 0.
            for day in day_grid]


def trial(fast_life_d: float, slow_peak_weight: float, baseline: float) -> dict:
    if not (0 <= slow_peak_weight <= .5 and 0 <= baseline <= 5):
        raise ValueError("exploratory domain only")
    # Use source 90d release time only as a candidate terminal component.
    slow_depot = 90.
    def shape(day: float) -> float:
        return ((1 - slow_peak_weight) * normalized_depot(fast_life_d, day)
                + slow_peak_weight * normalized_depot(slow_depot, day))
    def level(day: float, doses) -> float:
        return baseline + (11 - baseline) * sum(shape(day - d) for d in doses)
    # Fixed study timing: first injection day0, second day42,
    # subsequent doses at day112/182/252.
    second = max(level(42 + .5 * j, DOSE_DAYS[:2]) for j in range(41))
    last = [level(252 + .5 * j, DOSE_DAYS) for j in range(141)]
    peak, trough = max(last), last[-1]
    return dict(fast_half_life_d=fast_life_d, slow_90d_peak_weight=slow_peak_weight,
                constant_baseline_ng_ml=baseline,
                pred_second_peak_ng_ml=second,
                pred_fifth_peak_ng_ml=peak, pred_fifth_trough_ng_ml=trough,
                sse_approx_group_anchors=(second - 15.) ** 2 +
                                         (peak - 11.) ** 2 + (trough - 5.) ** 2)


def run() -> dict:
    # The grids and objective were selected after seeing published ANSM group
    # means. These are IN-SAMPLE exploration, not a blind locked holdout.
    trials = []
    for fast_life in (5, 7, 10, 12, 15, 18, 20, 25, 30, 40):
        for tick in range(21):
            for baseline in (0, 1, 2, 3, 4, 5):
                trials.append(trial(fast_life, tick * .025, baseline))
    trials.sort(key=lambda x: (x["sse_approx_group_anchors"],
                               x["fast_half_life_d"], x["slow_90d_peak_weight"]))
    best_positive_90d = next(t for t in trials if t["slow_90d_peak_weight"] >= .025)
    return dict(schema="hrtlog.pk_france_tu_mixture_identifiability.v1",
                state="IN_SAMPLE_EXPLORATORY_ONLY; NO_T_PRODUCTION_PK",
                mathematical_form="B + (11-B) * sum((1-w)h_fast(t-dose)+w*h_90d(t-dose))",
                caveat="w is normalized component *peak amplitude*, not molecular fraction; first peak at day 7 enforced in both components. The group totals include endogenous T and different patient populations may be sampled across visits.",
                target_approx_group_stats_ng_ml=dict(
                    first_peak=11, second_peak=15, fifth_peak=11, fifth_trough=5),
                n_scenarios=len(trials),
                best_minimum_sse=trials[0],
                best_with_positive_90day_component=best_positive_90d,
                top_five=trials[:5],
                key_negative_findings=[
                    "The lowest arithmetic SSE is obtained with w=0: study summaries do not identify a required 90d contribution in this grid.",
                    "Even the minimum SSE does not match all summary constraints; second and fifth peaks remain discrepant.",
                    "For any positive nonnegative additive release kernel, the theoretical fifth peak exceeds the first with nonzero residual, while both RCP means are reported ~11 (approximate cohort summaries).",
                    "Do not use this objective rank to select a clinical model; unknown baseline and total-vs-incremental semantics remain."],
                no_independent_holdout=True)


def self_test():
    assert normalized_depot(12, 0.) == 0.
    assert abs(normalized_depot(12, 7.) - 1) < 1e-10
    assert trial(25., 0., 3.)["sse_approx_group_anchors"] < 4.
    assert trial(25., .025, 3.)["sse_approx_group_anchors"] > 3.
    assert run()["n_scenarios"] == 10 * 21 * 6


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", type=Path)
    args = parser.parse_args()
    self_test()
    s = json.dumps(run(), indent=2, sort_keys=True, ensure_ascii=False) + "\n"
    if args.out:
        args.out.write_text(s, encoding="utf8")
    else:
        print(s, end="")
