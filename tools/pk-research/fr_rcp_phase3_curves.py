#!/usr/bin/env python3
"""Evidence-gated, normalized formulation PK shape research (NOT patient PK).

All outputs except the AZMIRO 200 mg single dose are dimensionless relative
response shapes. Rate scenarios not sourced are mathematical sensitivities, NOT
estimated human half-lives. No production PK, dosing advice, or internet access.
"""
from __future__ import annotations
import argparse
import json
import math
from pathlib import Path

LN2 = math.log(2.0)


def peak_time(rise_h: float, decay_h: float) -> float:
    if not (0 < decay_h < rise_h):
        raise ValueError('0 < decay < rise required')
    return math.log(rise_h / decay_h) / (rise_h - decay_h)


def rise_for_peak(target_h: float, tail_half_h: float) -> float:
    decay = LN2 / tail_half_h
    if target_h <= 0 or target_h >= 1 / decay:
        raise ValueError('Target Tmax not identifiable under chosen decay')
    lo, hi = decay * (1 + 1e-10), max(1.0, 10.0 / target_h)
    while peak_time(hi, decay) > target_h:
        hi *= 2
    for _ in range(110):
        mid = (lo + hi) / 2
        if peak_time(mid, decay) > target_h:
            lo = mid
        else:
            hi = mid
    return (lo + hi) / 2


def shape(t_h: float, rise_h: float, decay_h: float) -> float:
    if t_h <= 0:
        return 0.0
    pk = peak_time(rise_h, decay_h)
    norm = math.exp(-decay_h * pk) - math.exp(-rise_h * pk)
    return (math.exp(-decay_h * t_h) - math.exp(-rise_h * t_h)) / norm


def profile(key: str, t_peak_h: float, half_life_h: float, evidence: str,
            source: str, absolute_cmax: float | None = None) -> dict:
    k = rise_for_peak(t_peak_h, half_life_h)
    decay = LN2 / half_life_h
    horizon = max(4 * half_life_h, 5 * t_peak_h)
    points = [dict(hour=round(horizon * i / 240, 5),
                   relative=round(shape(horizon * i / 240, k, decay), 8))
              for i in range(241)]
    out = dict(id=key, mode='baseline_corrected_absolute' if absolute_cmax is not None
               else 'dimensionless_shape_only', evidence=evidence, source=source,
               assumed_tpeak_h=t_peak_h, tail_half_life_h=half_life_h,
               effective_rise_per_h=k, effective_tail_per_h=decay,
               points=points)
    if absolute_cmax is not None:
        out['absolute_200mg_TC_ng_dL'] = [round(p['relative'] * absolute_cmax, 5)
                                        for p in points]
    return out


def build() -> dict:
    specs = [
        # E2 injection ester comparisons: Tmin and half-life scenario aren't PK estimates.
        ('E2_EB_IM', 48, [48, 96, 168], 'human_Tmax_only_tail_sensitivity', 'Oriowo1980'),
        ('E2_EC_IM', 96, [96, 168, 240], 'human_Tmax_only_tail_sensitivity', 'Oriowo1980'),
        ('E2_EV_IM', 48, [72, 120, 192], 'human_Tmax_only_tail_sensitivity', 'Oriowo1980'),
        ('E2_EU_IM', 120, [240, 480, 960], 'HYPOTHETICAL_not_human_identified', 'Leyendecker1975'),
        ('T_TP_IM', 14, [19], 'secondary_human_Tmax_half_life', 'Fujioka1986_and_review'),
        ('T_TC_IM', 71.7, [8 * 24], 'FDA_baseline_corrected_group_anchors', 'FDA_AZMIRO_200mg'),
        ('T_TE_IM', 48, [4.5 * 24], 'ANSM_group_Tmax_and_release_half_life', 'ANSM_ANDROTARDYL'),
        ('T_TU_IM', 7 * 24, [50 * 24, 90 * 24, 130 * 24], 'ANSM_release_half_life_scenarios_only', 'ANSM_NEBIDO'),
        ('T_TE_SC', 11.9, [24, 72, 168], 'FDA_week12_median_Tmax_tail_sensitivity', 'FDA_XYOSTED'),
        ('T_TU_ORAL_TLANDO', 5, [6, 12, 24], 'FDA_steady_state_Tmax_tail_sensitivity', 'FDA_TLANDO'),
        ('T_TU_ORAL_JATENZO', 5, [6, 12, 24], 'FDA_steady_state_Tmax_tail_sensitivity', 'FDA_JATENZO'),
        ('T_TESTAVAN_GEL', 3, [8, 16, 36], 'ANSM_Tmax_2to4_h_tail_sensitivity', 'ANSM_TESTAVAN'),
        ('T_NATESTO_NASAL', 2 / 3, [.5, .75, 1.25], 'FDA_40min_Tmax_illustration', 'FDA_NATESTO'),
        ('T_STRIANT_BUCCAL', 11, [12, 20, 36], 'FDA_initial_Tmax_but_not_removal', 'FDA_STRIANT'),
    ]
    curves = []
    for key, peak_h, halves, grade, source in specs:
        for h in halves:
            try:
                curves.append(profile(key, peak_h, h, grade, source,
                                      758.0 if key == 'T_TC_IM' else None))
            except ValueError:
                raise AssertionError(f'Invalid scenario {key} peak={peak_h} half={h}')
    # Unlike other simulated profiles, EEn actual fitted rates were published.
    en_fast, en_slow = 1.505 / 24, .12445 / 24
    en_peak = peak_time(en_fast, en_slow)
    en = profile('E2_EN_IM', en_peak, LN2 / en_slow,
                 'published_human_absorption_and_elimination_rates_shape_only',
                 'Wiemeyer1986')
    curves.append(en)
    unsupported = [
        dict(id='E2_EV_SUBLINGUAL', reason='Only E2 not EV sublingual human sampling verified'),
        dict(id='E2_EC_SC', reason='Retrospective E2 concentration samples, no controlled full time curve'),
        dict(id='E2_EV_SC', reason='Retrospective E2 concentration samples, no controlled full time curve'),
        dict(id='E2_SPRAY_LENZETTO', reason='Steady-state Cmax/Cavg/Cmin but no verified Tmax shape'),
        dict(id='T_ANDROGEL_1pct', reason='Steady Cavg and effect of stopping; no fitted sample time course'),
        dict(id='T_ANDROGEL_1p62pct', reason='Different vehicle; no fitted sampled time course'),
        dict(id='T_PATCH', reason='Legacy regulatory steady peak/trough exist, but no fitted continuous-input and removal model'),
        dict(id='T_IMPLANT', reason='Human long-term aggregate and label exist, but no dose/individual-time resolved fitted curve'),
    ]
    return dict(schema='hrtlog.pk_phase3_research_shape.v1', status='RESEARCH_ONLY',
                key_warning='Dimensionless normalized sensitivities are NOT absolute serum concentration curves',
                absolute_TC_warning='TC 200mg Cmax=758 ng/dL is a group mean baseline-corrected FDA summary; median Tmax and 8d apparent half-life are not same-person paired parameters; no clinical validation',
                scenarios=curves, unsupported=unsupported,
                extra_observed_only=[
                    dict(id='E2_IMPLANT_CLARA_25mg', weeks=[0, 1, 3, 20, 24],
                         total_E2_pg_ml=[31.6, 105.2, 80, 80, 59.9],
                         warning='week3-20 ~80 is descriptive plateau, not 2 exact timepoint measurements; interpolation only'),
                ])


def self_test() -> None:
    assert abs(peak_time(1.505 / 24, .12445 / 24) / 24 - 1.8055442746509542) < 1e-10
    assert abs(rise_for_peak(71.7, 192) - .035483523290851776) < 1e-10
    assert abs(shape(71.7, rise_for_peak(71.7, 192), LN2 / 192) - 1) < 1e-10
    assert shape(-1, .3, .04) == 0
    o = build()
    assert len(o['scenarios']) == 37, len(o['scenarios'])
    assert len(o['unsupported']) == 8
    assert len([x for x in o['scenarios'] if x['mode'] == 'baseline_corrected_absolute']) == 1
    assert all(0 <= v['relative'] <= 1.0000001 for s in o['scenarios'] for v in s['points'])


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--out', type=Path)
    args = parser.parse_args()
    self_test()
    payload = json.dumps(build(), ensure_ascii=False, indent=2) + '\n'
    if args.out:
        args.out.write_text(payload, encoding='utf8')
    else:
        print(payload, end='')
