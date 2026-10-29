"""SL-CAL-02 parity fixture: runs the SL-CAL-01 research reference (method C_common, delta policy, eligibility rules) on fixed synthetic
cases and writes the inputs and outputs the Kotlin engine must reproduce. Synthetic users only; no health data. Deterministic (fixed seed).
Usage (from tools/pk-research/sl_cal): python make_parity_fixture.py --out ../../../app/src/test/resources/sl-cal-02/parity.json
"""
import argparse
import json
import math
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))
import sl_cal_core as c  # noqa: E402
import eligibility_rules as rules  # noqa: E402

SEED = 20261030
STEP = 0.05
SCHEMES = ('trough', 'peak', 'random')
LAB_COUNTS = (1, 2, 3, 5)


def sigma_delta(basis, t_rec):
    def pop(t):
        return c.BACKGROUND_PGML + basis.S(t) + (basis.N(t) if basis.regimen.mixed else 0.0)
    dln = (np.log(pop(t_rec + STEP)) - np.log(pop(t_rec - STEP))) / (2 * STEP)
    return np.sqrt(c.SIGMA_LAB ** 2 + (c.ASSUMED_TIMING_SD_H * dln) ** 2)


def case(rng, reg_name, scheme, n, time_sd):
    regimen = c.regimens()[reg_name]
    basis = c.Basis(regimen, c.m2_shape())
    g = math.exp(rng.uniform(math.log(0.3), math.log(3.0)))
    t_act = c.sample_lab_times(rng, regimen, scheme, n)
    t_rec = np.maximum(t_act + (rng.normal(0, time_sd, n) if time_sd > 0 else 0.0), 1.0)
    mu = c.BACKGROUND_PGML + g * (basis.S(t_act) + (basis.N(t_act) if regimen.mixed else 0.0))
    y = mu * np.exp(c.SIGMA_LAB * rng.standard_normal(n))
    pts = sorted(set(float(x) for x in np.concatenate([t_rec, t_rec + STEP, t_rec - STEP])))
    arr = np.array(pts)
    s_vals = basis.S(arr)
    n_vals = basis.N(arr) if regimen.mixed else np.zeros_like(arr)
    sigma = sigma_delta(basis, t_rec)
    fit = c.fit_method('C_common', basis, t_rec, y, sigma, 0.9)
    ig = 1 - fit.sl_sd ** 2 / fit.prior_var
    loo = None
    if n >= 2:
        errs = []
        for i in range(n):
            rest = np.array([j for j in range(n) if j != i])
            f2 = c.fit_method('C_common', basis, t_rec[rest], y[rest], sigma[rest], 0.9)
            pred = f2.total(basis, np.array([t_rec[i]]))[0]
            errs.append(math.log(y[i]) - math.log(pred))
        a = sorted(abs(e) for e in errs)
        med = a[n // 2] if n % 2 else (a[n // 2 - 1] + a[n // 2]) / 2
        loo = dict(median_abs_ln=med, rms_ln=math.sqrt(sum(e * e for e in errs) / n))
    hours = basis.regimen.hours_since_last_sl(t_rec)
    return dict(name=f'{reg_name}/{scheme}/n{n}/sd{time_sd}', background=c.BACKGROUND_PGML, labs=[dict(id=i + 1, time=float(t_rec[i]), pg_ml=float(y[i]), hours_since_sl=float(hours[i])) for i in range(n)],
                curve_times=pts, sublingual=[float(v) for v in s_vals], other=[float(v) for v in n_vals],
                expected=dict(factor=float(fit.ks), ln_sd=float(fit.sl_sd), information_gain=float(ig), sigma=[float(v) for v in sigma], loo=loo,
                              decision=rules.assess_set(n, ig).decision, reasons=rules.assess_set(n, ig).reasons))


def rule_cases():
    out = []
    for history in (True, False):
        for precise in (True, False):
            for known in (True, False):
                for hours in (None, 0.0, 1.0, 2.4999, 2.5, 3.0, 11.9):
                    d = rules.assess_lab(precise, known, hours, history)
                    out.append(dict(history=history, precise=precise, known=known, hours=hours, decision=d.decision, reasons=d.reasons))
    sets = [dict(usable=u, gain=g, **{k: v for k, v in zip(('decision', 'reasons'), (lambda s: (s.decision, s.reasons))(rules.assess_set(u, g)))}) for u in (0, 1, 3) for g in (0.0, 0.49999, 0.5, 0.69, 0.95)]
    return out, sets


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--out', required=True)
    a = ap.parse_args()
    rng = np.random.default_rng(SEED)
    cases = []
    for reg_name in ('SL_1mg_q12', 'SL_2mg_q24', 'MIX_SL1mg_q12_oral2mg', 'MIX_SL1mg_q24_gel1p5mg'):
        for scheme in SCHEMES:
            for n in LAB_COUNTS:
                for time_sd in (0.0, 0.5):
                    cases.append(case(rng, reg_name, scheme, n, time_sd))
    lab_rules, set_rules = rule_cases()
    doc = dict(schema='sl-cal-02-parity-v1', seed=SEED, step_h=STEP, constants=dict(prior_sd=0.9, timing_sd_h=c.ASSUMED_TIMING_SD_H, sigma_lab=c.SIGMA_LAB,
               peak_window_h=rules.PEAK_WINDOW_H, min_information_gain=rules.MIN_INFORMATION_GAIN), cases=cases, lab_rules=lab_rules, set_rules=set_rules)
    Path(a.out).parent.mkdir(parents=True, exist_ok=True)
    Path(a.out).write_text(json.dumps(doc, indent=1, sort_keys=True) + '\n')
    print(len(cases), 'cases', len(lab_rules), 'lab rules', len(set_rules), 'set rules')


if __name__ == '__main__':
    main()
