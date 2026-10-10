#!/usr/bin/env python3
"""P2-S early-growth mathematical audit from previously exposed published summaries.

Research only. Assay variability, time errors, participant dependence, and
unverified covariance prevent patient-level or clinical accuracy conclusions.
"""
import argparse
import json
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ROSANO = ROOT / 'docs/pk-research/p2/p2e-source-metrics.json'
KOMESAROFF = ROOT / 'docs/pk-research/p2/p2d-komesaroff-table1.json'


def sem_range(sd_early, sd_late, multiplier, n):
    """Exact Cauchy-Schwarz range over within-person correlation -1..1.

    Assumes the same n independent participants at both time points and the
    stated timepoint SDs estimate variance (not separate SEMs).
    """
    if n <= 1 or min(sd_early, sd_late, multiplier) < 0:
        raise ValueError('invalid SD, multiplier, or n')
    return (abs(sd_late - multiplier * sd_early) / math.sqrt(n),
            (sd_late + multiplier * sd_early) / math.sqrt(n))


def growth_audit(y_early, y_late, sd_early, sd_late, n, order, t_early, t_late):
    """Conservative zero-baseline boundary for n positive sequential stages.

    With a constant nonnegative baseline, actual violations are at least as
    severe as the zero-baseline result. 'order' is mathematical input order,
    not evidence for an anatomical number of compartments.
    """
    assert t_early > 0 and t_late > t_early
    if min(y_early, y_late) <= 0 or order < 1:
        raise ValueError('positive means and positive integer order required')
    factor = (t_late / t_early) ** order
    contrast = y_late - factor * y_early
    lo, hi = sem_range(sd_early, sd_late, factor, n)
    return {
        'input_order': order,
        'growth_bound_factor': factor,
        'observed_ratio': y_late / y_early,
        'conservative_zero_baseline_contrast_pmol_l': contrast,
        'sem_range_over_unknown_correlation_pmol_l': [lo, hi],
        'min_signal_over_max_sem_if_positive': contrast / hi if contrast > 0 and hi > 0 else None,
        'min_symmetric_per_mean_perturbation_to_satisfy_bound_pmol_l':
            max(0., contrast / (1. + factor)),
        'necessary_actual_time_ratio_at_observed_zero_baseline_ratio':
            (y_late / y_early) ** (1. / order),
        'assumptions': ['zero-delay positive linear cascade', 'same participants and published SD',
                        'baseline nonnegative and constant', 'exact nominal sampling times',
                        'measurement method affine scale fixed across sampled times',
                        'not a p-value or a validated measurement-error model'],
    }


def load_inputs():
    src = json.loads(ROSANO.read_text(encoding='utf-8'))
    ko = json.loads(KOMESAROFF.read_text(encoding='utf-8'))
    points = {x['minutes']: x for x in src['Rosano1997']['points']}
    kpoints = {x['minutes']: x for x in ko['observations']}
    if sorted(points) != [10, 20, 40, 60] or not {0, 15, 30}.issubset(kpoints):
        raise ValueError('unrecognized source rows')
    if src['Rosano1997']['n_PK'] != 25 or ko['n'] != 10:
        raise ValueError('different source cohort size; review required')
    if src['Rosano1997']['dispersion'] != 'SD (paper statistical rule)' or ko['reported_dispersion'] != 'SEM':
        raise ValueError('dispersion type mismatch')
    return src, ko, points, kpoints


def analysis():
    _, _, p, k = load_inputs()
    rosano = {
        f'n{order}': growth_audit(p[20]['mean_pmol_l'], p[40]['mean_pmol_l'],
                                 p[20]['sd_pmol_l'], p[40]['sd_pmol_l'], 25,
                                 order, 20., 40.)
        for order in (1, 2, 3)
    }
    # Komesaroff reported SEMs, not SDs. This is a worst-case bound for
    # the linear combination of three group means, with all correlations unknown.
    kom = {}
    for order in (1, 2):
        factor = 2 ** order
        contrast = (k[30]['mean_pmol_l'] - factor * k[15]['mean_pmol_l']
                    + (factor - 1.) * k[0]['mean_pmol_l'])
        se_upper = (k[30]['sem_pmol_l'] + factor*k[15]['sem_pmol_l']
                    + (factor - 1.)*k[0]['sem_pmol_l'])
        kom[f'n{order}'] = {
            'baseline_adjusted_contrast_pmol_l': contrast,
            'upper_bound_sem_pmol_l_from_triangle_inequality': se_upper,
            'signal_over_max_sem_if_positive': contrast/se_upper if contrast > 0 else None,
            'warning': 'No paired covariance; zero baseline is measured mean, not assumed exact for inference'
        }
    return {
        'phase': 'P2-S',
        'date': '2026-10-10',
        'status': 'EXPLORATORY_AGGREGATE_BOUNDS_ONLY',
        'sources': ['docs/pk-research/p2/p2e-source-metrics.json',
                    'docs/pk-research/p2/p2d-komesaroff-table1.json'],
        'rosano_20_40': rosano,
        'komesaroff_0_15_30': kom,
        'no_new_external_locked_human_PK_series': True,
        'clinical_accuracy_established': False,
        'source_data_or_assay_correction_inferred': False,
        'production_change_authorized': False,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', required=True)
    args = parser.parse_args()
    dst = Path(args.out)
    if dst.exists():
        parser.error('Refusing to overwrite a pre-existing audit result')
    dst.parent.mkdir(parents=True, exist_ok=True)
    dst.write_text(json.dumps(analysis(), ensure_ascii=False, indent=2, sort_keys=True) + '\n', encoding='utf-8')
    print(dst)

if __name__ == '__main__':
    main()
