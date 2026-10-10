"""P2-AA/P2-AR retrospective analytical checks (2026-10-10).

No private health records or real participant rows are read. No clinical prediction.
"""
import json
import math
from dataclasses import asdict, dataclass


@dataclass(frozen=True)
class TwoPointFit:
    gain: float
    background: float
    feasible: bool


def rmse(measured: tuple[float, ...], predicted: tuple[float, ...]) -> float:
    if not measured or len(measured) != len(predicted):
        raise ValueError("Nonempty equal-length observation vectors required")
    if not all(math.isfinite(v) for v in measured + predicted):
        raise ValueError("Nonfinite input")
    return math.sqrt(sum((a - b) ** 2 for a, b in zip(measured, predicted)) / len(measured))


def profile_two_point(
    pre: float, post: float, response_pre: float, response_post: float
) -> TwoPointFit:
    """Fit two data to b+gain*S for a synthetic visit, not a real PK person."""
    if not all(math.isfinite(x) for x in (pre, post, response_pre, response_post)):
        raise ValueError("Nonfinite input")
    if response_post <= response_pre:
        raise ValueError("Conditional proof requires positive response increment")
    gain = (post - pre) / (response_post - response_pre)
    background = pre - gain * response_pre
    return TwoPointFit(gain, background, gain > 0 and background >= 0)


def reanalyse() -> dict:
    # Four-decimal dimensionless entries from P2-AT, not source individual data.
    price_obs = (0.4911, 0.2564, 0.1898, 0.1244, 0.0974, 0.0760, 0.0528, 0.0541)
    price_old = (0.7359, 0.4063, 0.1995, 0.0409, 0.0077, 0.0006, 0.0003, 0.0002)
    rosano_obs = (0.1102, 0.2203, 0.9322)
    rosano_old = (0.3835, 0.6492, 0.9304)
    a = profile_two_point(120.0, 350.0, 0.25, 1.40)
    b = profile_two_point(120.0, 350.0, 0.50, 1.80)
    rejected = profile_two_point(120.0, 350.0, 0.75, 2.00)
    assert a.feasible and b.feasible and not rejected.feasible
    for fit, spre, spost in ((a, 0.25, 1.40), (b, 0.50, 1.80)):
        assert math.isclose(fit.background + fit.gain * spre, 120.0, abs_tol=1e-10)
        assert math.isclose(fit.background + fit.gain * spost, 350.0, abs_tol=1e-10)
    price_error = rmse(price_obs, price_old)
    rosano_error = rmse(rosano_obs, rosano_old)
    assert abs(price_error - 0.11665) < 0.00002
    assert abs(rosano_error - 0.29361) < 0.00004
    return {
        "status": "analytical_reanalysis_not_external_validation",
        "price_1997_legacy_rmse_rounded": price_error,
        "rosano_1997_legacy_rmse_rounded": rosano_error,
        "synthetic_pre_pmol_per_l": 120.0,
        "synthetic_post_pmol_per_l": 350.0,
        "shape_a": asdict(a),
        "shape_b": asdict(b),
        "shape_rejected_by_nonnegative_background": asdict(rejected),
        "independent_external_subjects": 0,
        "individual_osf_records_used": False,
    }


if __name__ == "__main__":
    print(json.dumps(reanalyse(), ensure_ascii=False, indent=2, sort_keys=True))
