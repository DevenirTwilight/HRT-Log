"""Research-only ANSM testosterone depot structural-consistency check.

No production model, no individual prediction, no dose recommendation. Product RCP
reports TOTAL serum testosterone, not the incremental amount from the injection.
The mathematical experiment deliberately assumes a zero baseline to test whether
a single depot-release exponential kernel can reproduce label peaks and steady
state simultaneously. It cannot; do not promote these fitted shapes to HRT Log.
"""
from dataclasses import dataclass
from math import exp, log
import json

# Units: hours, ng/mL, mg of *ester* (not mg of liberated T).
# ANSM §5.2 values are rounded label summaries, NOT raw individual time series.
RCP = {
    "ANDROTARDYL_TE_IM": {
        "cis": "68178899", "updated": "2026-03-03", "ester_mg": 250.0,
        "parent_mg": 180.0, "first_total_peak_ng_ml": 20.0,
        "first_peak_at_h": 48.0,  # midpoint of the labelled 1.5–3 days
        "depot_release_half_life_h": 4.5 * 24,
        "reported_total_lower_bounds": [(20 * 24, 2.0), (26 * 24, 1.0)],
        "url": "https://base-donnees-publique.medicaments.gouv.fr/medicament/68178899/extrait",
    },
    "NEBIDO_TU_IM": {
        "cis": "69033757", "updated": "2026-01-29", "ester_mg": 1000.0,
        "parent_mg": 631.5, "first_total_peak_ng_ml": 11.0,
        "first_peak_at_h": 7 * 24.0,
        "depot_release_half_life_h": 90 * 24.0,  # ANSM: 90 ± 40 days
        "steady_total_peak_ng_ml": 11.0,
        "steady_total_trough_ng_ml": 5.0,
        "source_repeated_interval_h": 10 * 7 * 24.0, # source's maintenance study interval
        "url": "https://base-donnees-publique.medicaments.gouv.fr/medicament/69033757/extrait",
    },
}


@dataclass(frozen=True)
class ExperimentalKernel:
    """Hypothetical impulse response per 1 mg ester, zero background."""
    a: float
    k_abs_h: float
    k_release_h: float

    def concentration(self, dose_mg: float, time_h: float) -> float:
        if time_h < 0 or dose_mg <= 0:
            return 0.0
        return dose_mg * self.a * (exp(-self.k_release_h * time_h) - exp(-self.k_abs_h * time_h))


def tmax(k_abs_h: float, k_release_h: float) -> float:
    return log(k_abs_h / k_release_h) / (k_abs_h - k_release_h)


def fit_zero_baseline_shape(source: dict) -> ExperimentalKernel:
    """Fit 2-day / 7-day peak and depot half-life, NOT a valid clinical model.

    The remaining amplitude is set by a total serum peak with UNKNOWN baseline;
    the resulting zero-baseline interpretation is an explicit counterfactual.
    """
    k_release = log(2.0) / source["depot_release_half_life_h"]
    target = source["first_peak_at_h"]
    lo, hi = k_release * (1.0 + 1e-8), 1.0
    if tmax(lo, k_release) < target or tmax(hi, k_release) > target:
        raise ValueError("First-order zero-baseline kernel cannot represent requested Tmax")
    for _ in range(100):
        mid = (lo + hi) / 2
        if tmax(mid, k_release) > target:
            lo = mid
        else:
            hi = mid
    ka = (lo + hi) / 2
    response_per_mg = exp(-k_release * target) - exp(-ka * target)
    a = source["first_total_peak_ng_ml"] / (source["ester_mg"] * response_per_mg)
    return ExperimentalKernel(a, ka, k_release)


def repeated_peak_and_trough(kernel: ExperimentalKernel, dose_mg: float,
                             count: int, interval_h: float, step_h: float = 1.0):
    """Concentration during the count-th period, assuming identical ester doses."""
    points = int(interval_h / step_h)
    values = [
        sum(kernel.concentration(dose_mg, i * step_h + k * interval_h)
            for k in range(count))
        for i in range(points + 1)
    ]
    return max(values), values[-1]


def evaluate() -> dict:
    te, tu = RCP["ANDROTARDYL_TE_IM"], RCP["NEBIDO_TU_IM"]
    et, ut = fit_zero_baseline_shape(te), fit_zero_baseline_shape(tu)
    peak5, trough5 = repeated_peak_and_trough(
        ut, tu["ester_mg"], 5, tu["source_repeated_interval_h"])
    return {
        "status": "RESEARCH_ONLY_NOT_VALIDATED_NO_PRODUCTION_PROMOTION",
        "assumptions": [
            "linear time-invariant single-depot first-order input",
            "zero endogenous baseline despite total-serum RCP observations",
            "identical dosing and no time-varying testosterone production/suppression",
            "depot-release half-life is not circulating testosterone elimination half-life",
        ],
        "source": {k: {x: v for x, v in s.items() if x in ("cis", "url", "updated")}
                   for k, s in RCP.items()},
        "ester_parent_mass_fraction": {
            "TE": te["parent_mg"] / te["ester_mg"],
            "TU": tu["parent_mg"] / tu["ester_mg"],
        },
        "androtardyl": {
            "assumed_ka_per_h": et.k_abs_h,
            "label_total_cmax_ng_ml": te["first_total_peak_ng_ml"],
            "model_first_cmax_ng_ml": et.concentration(te["ester_mg"], te["first_peak_at_h"]),
            "label_total_lower_bounds": [
                {"day": int(hour / 24), "label_gte_ng_ml": lo,
                 "model_zero_baseline_ng_ml": et.concentration(te["ester_mg"], hour)}
                for hour, lo in te["reported_total_lower_bounds"]
            ],
        },
        "nebido": {
            "assumed_ka_per_h": ut.k_abs_h,
            "label_first_total_cmax_ng_ml": tu["first_total_peak_ng_ml"],
            "model_first_cmax_ng_ml": ut.concentration(tu["ester_mg"], tu["first_peak_at_h"]),
            "label_steady_total_peak_ng_ml": tu["steady_total_peak_ng_ml"],
            "label_steady_total_trough_ng_ml": tu["steady_total_trough_ng_ml"],
            "model_fifth_peak_ng_ml": peak5,
            "model_fifth_trough_ng_ml": trough5,
            "model_matches_both_first_and_steady": abs(peak5 - tu["steady_total_peak_ng_ml"]) <= 2.0,
        },
        "conclusion": "A peak- and release-half-life-matched single depot exponential fails cross-dose official constraints. Unknown endogenous baseline, suppression, study populations and structural PK uncertainty prevent automatic absolute T model deployment.",
    }


if __name__ == "__main__":
    print(json.dumps(evaluate(), ensure_ascii=False, indent=2))
