"""P2-AW: conditional sampling-design geometry for frozen Legacy and 15 M2 shapes.

Reads only previously exposed, non-personal research snapshots from the original
P2-AS ZIP. Geometry is not statistical power, clinical efficacy, or advice to
change medication/collect blood outside an approved study.
"""
from __future__ import annotations

import argparse
import hashlib
import itertools
import json
import math
import statistics
import zipfile
from pathlib import Path

ARCHIVE_SHA256 = 'f88b6acdda3e4de27dc1fb0fb8d0b62b2e51ac0b2d85347f3219eb4656662b40'
FROZEN_M2_SHA256 = '7bb08b9e5b397e766cd63018567542520105755c9e578a8cdf4204a6df8d5367'
FROZEN_LEGACY_SHA256 = '70d95c7e38222520b09c0d7a740ccf5c92d94775e3aea589cc157daff1ebd2e2'
M2_ARCHIVE_PATH = 'P2-AS/inputs/p2x-conditional-ensemble-results.json'
LEGACY_ARCHIVE_PATH = 'P2-AS/inputs/legacy_source_snapshot.json'
GRID_EARLY = (0.25, 0.5, 0.75, 1.5, 2.0, 3.0, 4.0, 6.0, 8.0)
GRID_EXTENDED = GRID_EARLY + (12.0, 16.0, 24.0)
ANCHORS = (0.0, 1.0)


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def read_inputs(archive: Path) -> tuple[dict, dict]:
    if sha256(archive.read_bytes()) != ARCHIVE_SHA256:
        raise ValueError('Archived original P2-AS ZIP SHA-256 mismatch')
    with zipfile.ZipFile(archive) as z:
        if z.testzip() is not None:
            raise ValueError('Archived P2-AS CRC failure')
        m2 = z.read(M2_ARCHIVE_PATH)
        legacy = z.read(LEGACY_ARCHIVE_PATH)
    if sha256(m2) != FROZEN_M2_SHA256 or sha256(legacy) != FROZEN_LEGACY_SHA256:
        raise ValueError('Frozen internal P2-X or Legacy source changed')
    return json.loads(m2), json.loads(legacy)


def regularized_lower_gamma_integer(n: int, z: float) -> float:
    """P(n,z) for integer n, using stable series for small z."""
    if not isinstance(n, int) or n < 1 or not math.isfinite(z) or z < 0:
        raise ValueError('integer n >= 1 and finite z >= 0 required')
    if z == 0:
        return 0.0
    if z >= 25:
        term = total = 1.0
        for k in range(1, n):
            term *= z / k
            total += term
        return max(0., min(1., 1 - math.exp(-z) * total))
    term = total = 1.0
    for j in range(1, 400):
        term *= z / (n + j)
        total += term
        if term < 1e-15 * total:
            break
    return max(0., min(1., math.exp(n * math.log(z) - z - math.lgamma(n + 1)) * total))


def m2_normalized(t: float, parameter: dict) -> float:
    if not math.isfinite(t):
        raise ValueError('Time must be finite')
    if t <= 0:
        return 0.
    n = int(parameter['n_fast'])
    k = float(parameter['k_fast_per_h'])
    ke = float(parameter['k_elim_per_h'])
    ks = float(parameter['k_slow_per_h'])
    w = float(parameter['effective_slow_weight'])
    if not (0 < ke < k and ks > 0 and 0 <= w <= 1):
        raise ValueError('Frozen M2 parameters invalid')

    def kernel(x: float) -> float:
        fast = math.exp(n * math.log(k / (k - ke)) - ke * x) * regularized_lower_gamma_integer(n, (k - ke) * x)
        gap = abs(ks - ke)
        slow = ks * x * math.exp(-ke * x) if gap < 1e-9 * max(ks, ke) else ks * math.exp(-min(ks, ke) * x) * (-math.expm1(-gap * x)) / gap
        return (1 - w) * fast + w * slow

    return kernel(t) / kernel(1.)


def legacy_normalized(t: float, source: dict) -> float:
    if not math.isfinite(t):
        raise ValueError('Time must be finite')
    if t <= 0:
        return 0.
    sl, oral = source['E2_SL'], source['E2_ORAL']
    share = float(sl['swallowed_share'])

    def kernel(x: float) -> float:
        def part(component: dict) -> float:
            return sum(float(term['A_per_mg']) * (math.exp(-float(term['lambda_per_h']) * x) - math.exp(-float(component['ka_per_h']) * x)) for term in component['terms'])
        return part(sl) + share * part(oral)
    return kernel(t) / kernel(1.)


def frozen_candidates(p2x: dict) -> list[dict]:
    best = float(p2x['summary']['global_best_loss'])
    selected = [row['parameter'] for row in p2x['rows'] if float(row['score']) <= best + 0.10 + 1e-10]
    if len(p2x['rows']) != 40 or len(selected) != 15:
        raise ValueError('P2-X frozen selection rule / model count changed')
    return selected


def affine_profile_distance(x: list[float] | tuple, y: list[float] | tuple) -> float:
    """sqrt(1-rho^2) of centered shape vectors under *positive* gain.

    Equivalent to fitting an unknown additive intercept and positive amplitude
    for one curve to the other, divided by centered norm. Symmetric for rho>0.
    This is NOT an error estimate, test power, or probability. If either vector
    is flat, return 0 (not identifiable); two ordered anchor points also yield 0.
    """
    if len(x) != len(y) or len(x) < 2 or not all(math.isfinite(a) for a in list(x) + list(y)):
        raise ValueError('Equal nonflat finite arrays with at least two elements required')
    xm, ym = statistics.fmean(x), statistics.fmean(y)
    u, v = [a - xm for a in x], [a - ym for a in y]
    xx, yy = sum(a*a for a in u), sum(a*a for a in v)
    if xx <= 1e-24 or yy <= 1e-24:
        return 0.
    rho = sum(a*b for a, b in zip(u, v)) / math.sqrt(xx * yy)
    rho = min(1., max(-1., rho))
    if rho <= 0:
        return 1.
    return math.sqrt(max(0., 1 - rho*rho))


def profile_schedule(times: tuple[float,...], legacy: dict, m2: list[dict]) -> dict:
    if len(times) < 2 or len(set(times)) != len(times) or any(t < 0 for t in times):
        raise ValueError('Each sample time must be nonnegative and unique')
    base = [legacy_normalized(t, legacy) for t in times]
    shapes = [[m2_normalized(t, p) for t in times] for p in m2]
    a = [affine_profile_distance(base, shape) for shape in shapes]
    b = [affine_profile_distance(u, v) for u, v in itertools.combinations(shapes, 2)]
    return {
        'hours': [round(x, 6) for x in times],
        'legacy_v_m2_min': min(a),
        'legacy_v_m2_median': statistics.median(a),
        'm2_pairs_min': min(b),
        'm2_pairs_median': statistics.median(b),
    }


def ranked_one_extra(legacy: dict, models: list[dict]) -> list[dict]:
    records = []
    for t in GRID_EXTENDED:
        score = profile_schedule(ANCHORS + (t,), legacy, models)
        perturbed = [profile_schedule(ANCHORS + (max(0., t + delta),), legacy, models)['legacy_v_m2_min']
                     if all(abs(max(0., t + delta) - anchor) > 1e-12 for anchor in ANCHORS) else 0.
                     for delta in (-.25, 0., .25)]
        score['scenario_jitter_pm_15min_worst_min'] = min(perturbed)
        records.append(score)
    return sorted(records, key=lambda v: (-v['legacy_v_m2_min'], v['hours'][-1]))


def rank_combinations(legacy: dict, models: list[dict], extra: int, extended: bool) -> list[dict]:
    if extra not in (2, 3):
        raise ValueError('Extra slots must be 2 or 3')
    grid = GRID_EXTENDED if extended else GRID_EARLY
    rows = [profile_schedule(ANCHORS + slots, legacy, models) for slots in itertools.combinations(grid, extra)]
    return sorted(rows, key=lambda r: (-r['legacy_v_m2_min'], -r['legacy_v_m2_median'], tuple(r['hours'])))


def stable_public_numbers(value):
    """Round model geometry to 9 decimal places for Python/libm portability.

    Differences below 1e-9 in a dimensionless, noise-free geometry are not
    interpretable experimental information; retain full precision internally
    for selection and make the published JSON stable across runtimes.
    """
    if isinstance(value, dict):
        return {key: stable_public_numbers(val) for key, val in value.items()}
    if isinstance(value, list):
        return [stable_public_numbers(val) for val in value]
    if isinstance(value, float):
        return round(value, 9)
    return value


def run_study(p2x: dict, legacy: dict) -> dict:
    models = frozen_candidates(p2x)
    one = ranked_one_extra(legacy, models)
    two_eight = rank_combinations(legacy, models, 2, False)
    three_eight = rank_combinations(legacy, models, 3, False)
    two_full = rank_combinations(legacy, models, 2, True)
    three_full = rank_combinations(legacy, models, 3, True)
    late = []
    for t in (0.25, .5, 1., 2., 4., 6., 8., 12., 24.):
        values = [m2_normalized(t, p) for p in models]
        late.append({'time_h': t, 'legacy': legacy_normalized(t, legacy), 'm2_min': min(values),
                     'm2_median': statistics.median(values), 'm2_max': max(values)})
    scenarios = ((0., 1.), (0., 1., .25), (0., 1., 2.), (0., 1., 8.), (0., 1., 24.),
                 (0., 1., .25, 2.), (0., 1., 2., 8.), (0., 1., 8., 24.),
                 (0., 1., .25, .5, 2.), (0., 1., .25, 2., 8., 24.))
    return stable_public_numbers({
        'phase': 'P2-AW', 'date': '2026-10-11',
        'status': 'MODEL_BASED_SAMPLING_GEOMETRY_NOT_HUMAN_DATA',
        'p2as_original_zip_sha256': ARCHIVE_SHA256,
        'frozen_p2x_json_sha256': FROZEN_M2_SHA256,
        'frozen_legacy_json_sha256': FROZEN_LEGACY_SHA256,
        'frozen_m2_count': len(models), 'frozen_total_fit_count': len(p2x['rows']),
        'affine_shape_metric': 'positive_gain_centered_correlation_residual_sqrt_1_minus_rho_squared',
        'metric_not_power_accuracy_confidence_or_biological_equivalence': True,
        'baseline_0h_and_one_hour_anchor_required_for_grid_search': True,
        't_hours_are_synthetic_design_grid_not_observed_data': True,
        'time_jitter_pm_15min_is_arbitrary_stress_not_actual_sampling_error': True,
        'no_personal_data_used': True,
        'independent_new_human_PK_series': 0,
        'clinical_model_replacement_authorized': False,
        'source_time_grid_hours': list(GRID_EXTENDED),
        'baseline_1h_only_with_two_points': profile_schedule((0., 1.), legacy, models),
        'one_extra_ranked_nominal': one,
        'two_extra_best_0_8h': two_eight[:8],
        'three_extra_best_0_8h': three_eight[:8],
        'two_extra_best_0_24h': two_full[:8],
        'three_extra_best_0_24h': three_full[:8],
        'predeclared_design_scenarios': [profile_schedule(t, legacy, models) for t in scenarios],
        'single_dose_normalized_shape_at_times': late,
    })


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--archive', required=True, type=Path, help='Original byte-identical P2-AS research ZIP')
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    if args.output.exists():
        parser.error('Refusing to overwrite any previous research result')
    p2x, old = read_inputs(args.archive)
    result = run_study(p2x, old)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2, sort_keys=True, allow_nan=False) + '\n', encoding='utf-8')
    print('P2-AW: 15 frozen M2 candidates, geometry and frozen provenance PASS')


if __name__ == '__main__':
    main()
