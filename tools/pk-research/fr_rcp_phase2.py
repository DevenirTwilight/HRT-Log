#!/usr/bin/env python3
"""French RCP phase II: T depot structural bounds, gel product heterogeneity, P4 tail.
Research-only, deterministic, stdlib, NOT fit to clinical individual values.

Use python3 tools/pk-research/fr_rcp_phase2.py [--out PATH].
Inputs: checked-in phase2-ledger.json. No health/user data or production changes.
"""
from __future__ import annotations
import argparse
import json
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
LEDGER = ROOT / "docs/pk-research/fr-rcp-phase1/phase2-ledger.json"
LN2 = math.log(2)


def bateman(t_h: float, fast: float, slow: float) -> float:
    """Effective two-rate response. DO NOT interpret fast as parent-T clearance."""
    if t_h <= 0:
        return 0.
    if not (0 < slow < fast):
        raise ValueError("require 0 < slow < fast")
    return math.exp(-slow * t_h) - math.exp(-fast * t_h)


def bateman_tpeak(fast: float, slow: float) -> float:
    if not (0 < slow < fast):
        raise ValueError("require 0 < slow < fast")
    return math.log(fast / slow) / (fast - slow)


def min_constant_baseline_to_reach_thresholds(
    peak_total: float, slow: float, fast: float, thresholds: list[tuple[float, float]]
) -> dict:
    tp = bateman_tpeak(fast, slow)
    yp = bateman(tp, fast, slow)
    out = {}
    for days, target in thresholds:
        fraction = bateman(days * 24, fast, slow) / yp
        # B+(peak_total-B)*fraction >= target
        bound = (target - peak_total * fraction) / (1 - fraction)
        out[str(days)] = dict(required_baseline_ng_ml=max(0., bound),
                              relative_to_peak_increment=fraction,
                              threshold_total_ng_ml=target)
    return dict(tmax_hours=tp, tmax_days=tp / 24,
                threshold_constraints=out,
                min_nonnegative_baseline_ng_ml=max(
                    0., *(x["required_baseline_ng_ml"] for x in out.values())))


def find_fast_for_tpeak(slow: float, tpeak_h: float) -> float:
    lo, hi = slow * (1 + 1e-9), 1.
    if not (bateman_tpeak(hi, slow) < tpeak_h < bateman_tpeak(lo, slow)):
        raise ValueError("target peak outside feasible range")
    for _ in range(100):
        mid = (lo + hi) / 2
        if bateman_tpeak(mid, slow) > tpeak_h:
            lo = mid
        else:
            hi = mid
    return (lo + hi) / 2


def tu_steady_ratio(t_half_days: float, first_tpeak_days: float = 7.,
                    maintenance_interval_days: float = 70.) -> dict:
    slow = LN2 / (t_half_days * 24)
    fast = find_fast_for_tpeak(slow, first_tpeak_days * 24)
    interval_h = maintenance_interval_days * 24
    # 3 h grid on the *last* steady-dose interval; 100 previous doses.
    dt = 3
    curve = [sum(bateman(t + j * interval_h, fast, slow) for j in range(100))
             for t in range(0, int(interval_h) + 1, dt)]
    # This is a scaled incremental curve; nonnegative constant baseline only
    # reduces max/min ratio. Do not compare to paired-patient data.
    mx, mn = max(curve), min(curve)
    peak1 = bateman(first_tpeak_days * 24, fast, slow)
    def second_peak_for_assumed_baseline(baseline_ng_ml: float) -> float:
        amp = (11 - baseline_ng_ml) / peak1
        day42 = 42 * 24
        return max(baseline_ng_ml + amp * (
            bateman(t, fast, slow) + bateman(t - day42, fast, slow))
                   for t in range(day42, day42 + 20 * 24 + 1, 6))
    # The RCP describes loading on day 0 and day 42, followed by 70-day
    # intervals. Fifth administration is day 252. Do not confuse the
    # infinite-regimen mathematical limit above with actual 3–5 injections.
    injection_days = (0, 42, 112, 182, 252)
    fifth_shape = [sum(bateman((t - d) * 24, fast, slow)
                       for d in injection_days)
                   for t in (252 + j * .125 for j in range(561))]
    scale_B0 = 11 / peak1
    return dict(release_half_life_days=t_half_days,
                fast_h_inv=fast, slow_h_inv=slow,
                fifth_peak_total_B0=max(fifth_shape) * scale_B0,
                fifth_trough_total_B0=fifth_shape[-1] * scale_B0,
                first_peak_h=bateman_tpeak(fast, slow),
                no_baseline_steady_peak_trough_ratio=mx / mn,
                second_peak_total_B0=second_peak_for_assumed_baseline(0.),
                second_peak_total_B3=second_peak_for_assumed_baseline(3.),
                baseline_is_hypothetical=True)


def p4_tail(ledger: dict) -> dict:
    p = next(x for x in ledger["cohorts"] if x["cohort_id"] == "FR_P4_ORAL_RCP_SHARED_200MG")
    times = p["time_h"]
    observed = p["mean_ng_ml"]
    vals = []
    for b in (0., .13, .26):
        # Range is purely a mathematical baseline sensitivity exercise,
        # NOT a physiological interval or clinical confidence interval.
        y2, y4, y6, y8 = (observed[times.index(t)] - b for t in (2, 4, 6, 8))
        def half_life(x, y, delta_h=2):
            return LN2 * delta_h / math.log(x / y)
        vals.append(dict(assumed_constant_baseline=b, half_life_2to4h=half_life(y2, y4),
                         half_life_4to6h=half_life(y4, y6), half_life_6to8h=half_life(y6, y8),
                         difference_4to6h=y4 - y6))
    return dict(reused_brand_labels=p["source_reuse"], unique_underlying_rcp_cohorts=1,
                source_timepoints_hours=times, source_total_means_ng_ml=observed,
                baseline_scenarios_not_ci=vals,
                warning="Time-window slopes are apparent slopes of cohort MEANS, NOT individual terminal half-lives. Labels may copy same original study. No independent validation.")


def t_gel(ledger: dict) -> dict:
    t = next(x for x in ledger["cohorts"] if x["cohort_id"] == "FR_TESTAVAN_STUDY_000127_DAY90")
    rows = t["groups"]
    ratio_cavg = rows[2]["cavg_mean"] / rows[1]["cavg_mean"]
    site = t["site_comparison"]
    return dict(titrated_groups_n=[x["n"] for x in rows],
                cavg_day90_46mg_ng_dl=rows[1]["cavg_mean"],
                cavg_day90_69mg_ng_dl=rows[2]["cavg_mean"],
                between_titrated_groups_cavg_69_over_46=ratio_cavg,
                cannot_fit_monotonic_individual_dose_response=(ratio_cavg < 1),
                arm_shoulder_vs_abdomen_cmax_ratio=(
                    site["shoulder_arm"]["cmax_mean_ng_dl"] /
                    site["abdomen"]["cmax_mean_ng_dl"]),
                arm_shoulder_vs_abdomen_cavg_ratio=(
                    site["shoulder_arm"]["cavg_mean_ng_dl"] /
                    site["abdomen"]["cavg_mean_ng_dl"]),
                product_comparisons=[dict(product="ANDROGEL 1% 50mg",
                                          mean_increment_ng_ml=2.5, dose_mg=50,
                                          baseline_return_h=[72, 96]),
                                     dict(product="ANDROGEL 16.2mg/g 2.5g",
                                          mean_increment_ng_ml=2.2, dose_mg=40.5,
                                          baseline_return_h=[72, 96]),
                                     dict(product="TESTAVAN 20mg/g", dose_mg=None,
                                          mean_increment_ng_ml=None,
                                          baseline_return_approx_within_daily_interval_h=12)],
                warning="Total vs incremental, nonpaired titrated cohorts, applied site, vehicle, and different return-to-baseline operational definitions make cross-product fits invalid.")


def run(ledger: dict) -> dict:
    te = next(x for x in ledger["cohorts"] if x["cohort_id"] == "FR_TE_250_IM")
    t_half = te["observations"]["release_half_life_days_approx"]
    slow = LN2 / (t_half * 24)
    fasts = (.025, .030, .035, .040, .050, .060, .070)
    te_curves = [dict(fast_h_inv=f, **min_constant_baseline_to_reach_thresholds(
        20., slow, f, [(20., 2.), (26., 1.)])) for f in fasts]
    tu_curves = [tu_steady_ratio(h) for h in (50., 60., 70., 90., 130.)]
    return dict(schema="hrtlog.pk_france_phase2_shape_bounds.v1",
                state="RESEARCH_ONLY; NOT fitted T curves; no individual validation",
                TE=dict(source_cohort=te["cohort_id"], fixed_terminal_t_half_d=t_half,
                        family="effective two-rate, nonnegative total baseline; not physiologic compartment identification",
                        effective_rise_grid=te_curves),
                TU=dict(source_cohort="FR_TU_1000_IM",
                        sensitivity_t_half_d=[50, 60, 70, 90, 130],
                        q70d_first_peak7d_bateman_candidates=tu_curves,
                        official_observations=dict(first_peak_approx=11,
                                                   second_peak_approx=15,
                                                   steady_peak_approx=11,
                                                   steady_trough_approx=5),
                        warning="Official peak and trough are marginal cohort means; not paired ratios. Same first/steady peak ≈11 incompatible as exact constraints with any strictly positive time-invariant accumulating injection response; approximate study summaries, titration/baseline differences, or nonstationarity can resolve."),
                P4=p4_tail(ledger), T_gels=t_gel(ledger),
                admission="No physiological parameters are identified; not sufficient for HRT Log production or dose advice.")


def self_test():
    assert bateman(0, .04, .006) == 0
    assert bateman(10, .04, .006) > 0
    assert 36 <= bateman_tpeak(.06, LN2/(4.5*24)) <= 72
    for hl in (50., 90., 130.):
        k = LN2 / (hl * 24)
        f = find_fast_for_tpeak(k, 7*24)
        assert abs(bateman_tpeak(f, k) - 168) < 1e-8
    assert tu_steady_ratio(90.)["no_baseline_steady_peak_trough_ratio"] < 2.2


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--ledger", type=Path, default=LEDGER)
    parser.add_argument("--out", type=Path)
    opts = parser.parse_args()
    self_test()
    result = run(json.loads(opts.ledger.read_text(encoding="utf8")))
    payload = json.dumps(result, ensure_ascii=False, indent=2, sort_keys=True) + "\n"
    if opts.out:
        opts.out.write_text(payload, encoding="utf8")
    else:
        print(payload, end="")
