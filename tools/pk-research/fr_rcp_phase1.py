#!/usr/bin/env python3
"""Reproducible *research-only* ANSM RCP 5.2 comparison; stdlib only.

No production PK parameters, personal dosing, diagnosis, clinical validation,
or default model selection. Input is the checked-in RCP source ledger plus the
EXISTING repo pk-params.json. Prints JSON; optional --out writes it to a file.
"""
from __future__ import annotations
import argparse
import json
import math
from pathlib import Path

DT = 0.05
P4_TIMES = (1, 2, 4, 6, 8)


def kernel(model: dict, dose: float, t: float) -> float:
    if t < 0:
        return 0.
    ka = model["ka_per_h"]
    return dose * sum(p["A_per_mg"] * (math.exp(-p["lambda_per_h"] * t) - math.exp(-ka * t))
                      for p in model["terms"])


def day_curve(model: dict, dose: float, days: int = 30) -> list[float]:
    """24-hour curve after last dose in 1/day regimen, no endogenous baseline."""
    return [sum(kernel(model, dose, i * DT + j * 24) for j in range(days))
            for i in range(round(24 / DT) + 1)]


def metrics(series: list[float]) -> dict:
    return dict(cmax=max(series), tmax_h=series.index(max(series)) * DT,
                cmin_24h=series[-1],
                cavg_24h=sum((series[i] + series[i + 1]) * DT * 0.5
                             for i in range(len(series) - 1)) / 24)


def source_by_id(data: dict, key: str) -> dict:
    return next(s for s in data["references"] if s["id"] == key)


def p4_profile(params: dict, data: dict) -> dict:
    obs = source_by_id(data, "ANSM_UTROGESTAN")
    baseline = obs["baseline"]
    times = obs["stats"]["timepoint_hours"][1:]
    target = [x - baseline for x in obs["stats"]["concentration_mean"][1:]]
    old_model = params["models"]["P4_ORAL"]
    old = [kernel(old_model, 200., t) for t in times]
    old_sse = sum((a - b) ** 2 for a, b in zip(old, target))
    # Exploratory constrained bi-exponential candidates; all five post-dose
    # RCP time points are training data, therefore NO external validation.
    # Shared ka; one fast decay and one slow decay, nonnegative coefficients.
    # Grid search, exact nonnegative two-variable least squares at each grid
    # point. Candidate does not change or replace the shipping engine.
    best = None
    for ka in (.6, .9, 1.2, 1.5, 2., 2.5, 3., 4., 6.):
        for fast in (.2, .3, .4, .5, .6, .8, 1., 1.2):
            for slow in (.01, .02, .03, .05, .08, .12, .18):
                if slow >= fast:
                    continue
                xs = [200 * (math.exp(-fast * t) - math.exp(-ka * t)) for t in times]
                ys = [200 * (math.exp(-slow * t) - math.exp(-ka * t)) for t in times]
                xx = sum(x * x for x in xs); xy = sum(x * y for x, y in zip(xs, ys))
                yy = sum(y * y for y in ys)
                xt = sum(x * t for x, t in zip(xs, target))
                yt = sum(y * t for y, t in zip(ys, target))
                det = xx * yy - xy ** 2
                sols = [(0., 0.)]
                if xx > 0: sols.append((max(0., xt / xx), 0.))
                if yy > 0: sols.append((0., max(0., yt / yy)))
                if det > 1e-12:
                    a = (xt * yy - yt * xy) / det
                    b = (yt * xx - xt * xy) / det
                    if a >= 0 and b >= 0: sols.append((a, b))
                for a, b in sols:
                    pred = [a * x + b * y for x, y in zip(xs, ys)]
                    sse = sum((p - t) ** 2 for p, t in zip(pred, target))
                    if best is None or sse < best["sse"]:
                        best = dict(ka_per_h=ka, fast_lambda_per_h=fast,
                                    slow_lambda_per_h=slow, fast_A_per_mg=a,
                                    slow_A_per_mg=b, sse=sse,
                                    predicted_increment=[round(v, 5) for v in pred])
    return dict(times_h=times, observed_total=obs["stats"]["concentration_mean"][1:],
                observed_minus_predose_baseline=target, predose_baseline=baseline,
                existing_increment=[round(v, 5) for v in old],
                existing_sse=round(old_sse, 6),
                exploratory_two_term_in_sample=best,
                status="TRAINING-ONLY; independent model validation unavailable",
                warnings=["French RCP does not specify assay or human baseline variability",
                          "Baseline subtraction assumes constant 0.13 ng/mL during 8h",
                          "Five time points cannot identify multi-compartment physiology",
                          "Old model fit to different Prometrium study; cross-product comparison"])


def e2_profile(params: dict, data: dict) -> dict:
    results = {}
    for key, dose in (("ANSM_OROMONE_1", 1.), ("ANSM_OROMONE_2", 2.)):
        obs = source_by_id(data, key)["stats"]
        p = metrics(day_curve(params["models"]["E2_ORAL"], dose))
        results[key] = dict(observed={k: obs[k] for k in ("cmax", "cmin", "cavg", "auc0_24")},
                            predicted_increment={k: round(v, 5) for k, v in p.items()},
                            contrast_peak_minus_trough_pred=round(p["cmax"] - p["cmin_24h"], 5),
                            contrast_peak_minus_trough_observed=round(obs["cmax"] - obs["cmin"], 5),
                            warning="RCP ambiguous single-dose/steady-state text; baseline and assay unknown. Total concentrations cannot be equated to modeled increment.")
    return results


def gel_profile(params: dict) -> dict:
    out = {}
    for key in ("E2_GEL_OTHER", "E2_GEL_ESTROGEL"):
        model = params["models"][key]
        out[key] = dict(single_increment={k: round(v, 5) for k, v in metrics(day_curve(model, 1.5, 1)).items()},
                        repeat_day22_increment={k: round(v, 5) for k, v in metrics(day_curve(model, 1.5, 22)).items()})
    return dict(observed_total_approx=dict(single_peak=40, day22_peak=70, day22_post24h=40),
                models=out,
                warning="ESTREVA observations are total serum concentrations with unknown baseline, not certified incremental responses; current OTHER match is provisional and not a validated Estreva-specific model.")


def t_structural(data: dict) -> dict:
    te = source_by_id(data, "ANSM_ANDROTARDYL")
    tu = source_by_id(data, "ANSM_NEBIDO")
    # Mathematical diagnostics, NOT fitted testosterone curves.
    dep_te = math.log(2) / (te["stats"]["depot_release_half_life_days_approx"] * 24)
    elim_iv = math.log(2) / tu["stats"]["iv_testosterone_elimination_half_life_hours_approx"]
    bateman_tmax_h = math.log(elim_iv / dep_te) / (elim_iv - dep_te)
    ss_ratio = 2 ** (tu["stats"]["nominal_maintenance_interval_days"] /
                     tu["stats"]["depot_release_half_life_days_mean"])
    return dict(TE=dict(simple_first_order_depot_with_iv_t_half_1h_tmax_h=round(bateman_tmax_h, 5),
                        official_peak_window_h=[36, 72],
                        finding="Single-rate 4.5d depot + 1h IV clearance predicts peak much earlier than label: transit, nonuniform depot release, or study context needed; not fitted."),
                TU=dict(pure_90day_exponential_max_peak_trough_ratio_q70days=round(ss_ratio, 5),
                        official_steady_total_peak_to_trough_ratio=round(11 / 5, 5),
                        finding="With fixed 90d mono-exponential and nonnegative constant baseline, the 70d ratio bound is below label total peak/trough; 90±40d uncertainty, peak timing, nonlinear release and differing subjects preclude rejection of all depot models."),
                status="STRUCTURAL DIAGNOSTICS ONLY: NO testosterone concentration models fitted")


def run(param_path: Path, anchor_path: Path) -> dict:
    params = json.loads(param_path.read_text(encoding="utf-8"))
    anchors = json.loads(anchor_path.read_text(encoding="utf-8"))
    assert anchors["schema"] == "hrtlog.fr_rcp_research_anchor.v1"
    result = dict(research_state="NOT_PRODUCTION / IN_SAMPLE_ONLY",
                  formula="h(t) = dose * SUM[A_i*(exp(-lambda_i*t) - exp(-ka*t))]; 1/day superposition",
                  comparisons=dict(oral_E2=e2_profile(params, anchors),
                                   Estreva=gel_profile(params), progesterone=p4_profile(params, anchors),
                                   testosterone=t_structural(anchors)),
                  limitations=["Existing HRT Log PK parameters are not regulator-endorsed models",
                               "No mixed-route, individual baseline, estrogen conversion or testosterone suppression inference",
                               "RCP §5.2 data used to fit exploratory candidate must not count as independent holdout",
                               "This research does not establish individual prediction accuracy"])
    return result


def self_test() -> None:
    m = dict(ka_per_h=2., terms=[dict(A_per_mg=3., lambda_per_h=.1)])
    assert kernel(m, 1., -1) == 0.
    assert kernel(m, 1., 0) == 0.
    assert abs(kernel(m, 2., 3.) - 2 * kernel(m, 1., 3.)) < 1e-12
    assert all(v >= 0 for v in day_curve(m, 1., 3))
    # A separate mathematical check: a 90-day mono-exponential's 70-day
    # peak/trough envelope is 2**(70/90), not an interpreted clinical ratio.
    assert 1.7 < 2 ** (70/90) < 1.8


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--params", default="pk-engine/src/main/resources/pk-params.json")
    ap.add_argument("--anchors", default="docs/pk-research/fr-rcp-phase1/anchors.json")
    ap.add_argument("--out", default=None)
    args = ap.parse_args()
    self_test()
    result = run(Path(args.params), Path(args.anchors))
    payload = json.dumps(result, ensure_ascii=False, indent=2, sort_keys=True) + "\n"
    if args.out: Path(args.out).write_text(payload, encoding="utf-8")
    else: print(payload, end="")
