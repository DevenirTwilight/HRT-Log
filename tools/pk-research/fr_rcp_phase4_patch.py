#!/usr/bin/env python3
"""Non-production piecewise skin-depot + 24h patch-release scenario.

ANDRODERM historical FDA label: release for 24h and reported ~70min plasma
half-life after removal. Effective skin storage is UNIDENTIFIED, so no single
candidate here is a validated testosterone concentration curve.
"""
import json
import math
from pathlib import Path

LN2 = math.log(2.0)
WEAR_H = 24.0
ELIM_H = 70.0 / 60.0


def step_response(t_h: float, skin_half_h: float) -> float:
    if t_h <= 0:
        return 0.0
    ks = LN2 / skin_half_h
    ke = LN2 / ELIM_H
    if abs(ks - ke) < 1e-10:
        # limit of two equal rates, integrated impulse k*t*exp(-k*t)
        return (1 - (1 + ks * t_h) * math.exp(-ks * t_h)) / ks
    return ks / (ke - ks) * ((1 - math.exp(-ks * t_h)) / ks -
                             (1 - math.exp(-ke * t_h)) / ke)


def patch_release(t_h: float, skin_half_h: float, wear_h: float = WEAR_H) -> float:
    if wear_h <= 0:
        raise ValueError('wear_h must be positive')
    return max(0.0, step_response(t_h, skin_half_h) -
               step_response(t_h - wear_h, skin_half_h))


def run():
    rows = []
    for half in (1.0, 2.0, 3.0, 6.0, 12.0, 24.0):
        xs = [0.05 * i for i in range(961)]
        ys = [patch_release(t, half) for t in xs]
        maxval = max(ys)
        peak_idx = ys.index(maxval)
        rows.append(dict(skin_half_life_h_HYPOTHETICAL=half,
                         pulse_wear_h=24, plasma_half_life_h_label=ELIM_H,
                         peak_h=xs[peak_idx],
                         fraction_at_24h=ys[480]/maxval,
                         fraction_at_26h=ys[520]/maxval,
                         fraction_at_48h=ys[960]/maxval,
                         normalized_points=[dict(h=round(xs[i],2),relative=round(ys[i]/maxval,7))
                                            for i in range(0,961,10)]))
    return dict(schema='hrtlog.pk_france_phase4_patch.v1',status='SHAPE_SCENARIOS_NOT_PRODUCTION',
                product='historical ANDRODERM patch',
                population_context='FDA old 2.5/5mg per 24h product, hypogonadal men; total T reported',
                measured_total_T_cavg_cmax_cmin_ng_dl=[498,748,322],
                measured_mean_tmax_h=22,
                label_plasma_t_half_after_removal_minutes=70,
                mechanism='constant input for each 24h patch, skin reservoir, then circulating clearance',
                rate_uncertainty='Skin reservoir half-life deliberately varied and NOT reported in label; total vs baseline-corrected levels unknown',
                scenarios=rows)


def self_test():
    assert patch_release(-1.,6.) == 0.
    assert patch_release(0.,6.) == 0.
    assert 0 < patch_release(12.,6.) < patch_release(24.,6.)
    assert patch_release(48.,6.) < patch_release(26.,6.)
    try:
        patch_release(10,6,0)
        raise AssertionError('invalid wear allowed')
    except ValueError:
        pass
    d=run()
    assert len(d['scenarios']) == 6
    assert all(20 < x['peak_h'] < 30 for x in d['scenarios'])


if __name__=='__main__':
    self_test()
    out=Path(__file__).with_name('phase4-patch-local.json')
    out.write_text(json.dumps(run(),indent=2)+'\n',encoding='utf8')
    print(json.dumps({'scenarios':len(run()['scenarios']), 'computed':True, 'output':str(out)}))
