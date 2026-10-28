"""SL-CAL-01 owner-local check (OPTIONAL, run by the owner on their own machine).

Reads two CSV files that live OUTSIDE this repository, prints a short aggregate summary to the terminal, and writes nothing.
It refuses to read a path inside the repository, refuses to read a file whose location is not a regular local file, never opens a network
connection, and never prints an individual lab value, date or dose: only counts, eligibility reasons, calibration coefficients with their
uncertainty, and leave-one-lab-out error summaries. Do not paste the output anywhere that would identify you; do not commit it.

  doses.csv : time,mg,route         route is one of SL, ORAL, GEL; time is ISO-8601 WITH a UTC offset, e.g. 2026-10-20T08:00:00+08:00
  labs.csv  : time,value,unit       unit is pg/mL or pmol/L; time with offset, to the minute
  python owner_local_check.py --doses /path/outside/repo/doses.csv --labs /path/outside/repo/labs.csv --history-complete

--history-complete is YOUR statement that the dose file contains every dose you took since you started; without it nothing is calibrated.
This script is a research aid for the SL-CAL-01 questions. It is not medical advice and does not suggest any dose change.
"""
import argparse
import csv
import math
import sys
from datetime import datetime
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))
import eligibility_rules as rules  # noqa: E402
import sl_cal_core as c  # noqa: E402

REPO = c.ROOT
ROUTE_KEY = {'ORAL': 'E2_ORAL', 'GEL': 'E2_GEL_ESTROGEL'}
PRIOR_SD = 0.9


def outside_repo(path):
    p = Path(path).resolve()
    if p == REPO or REPO in p.parents:
        raise SystemExit('refusing to read a file inside the repository: keep personal data outside it')
    if not p.is_file():
        raise SystemExit('not a regular file: ' + str(path))
    return p


def parse_time(text):
    t = datetime.fromisoformat(text.strip())
    if t.tzinfo is None:
        raise SystemExit('every time needs a UTC offset (e.g. +08:00); refusing to guess a time zone')
    return t


def load(doses_path, labs_path):
    with open(outside_repo(doses_path), newline='') as f:
        doses = [(parse_time(r['time']), float(r['mg']), r['route'].strip().upper()) for r in csv.DictReader(f)]
    with open(outside_repo(labs_path), newline='') as f:
        labs = [(parse_time(r['time']), float(r['value']), r['unit'].strip()) for r in csv.DictReader(f)]
    if not doses or not labs:
        raise SystemExit('need at least one dose and one lab')
    if any(route not in ('SL', 'ORAL', 'GEL') for _t, _m, route in doses):
        raise SystemExit('route must be SL, ORAL or GEL')
    if any(unit not in ('pg/mL', 'pmol/L') for _t, _v, unit in labs):
        raise SystemExit('unit must be pg/mL or pmol/L')
    return doses, labs


def summarize(doses, labs, history_complete):
    t0 = min(d[0] for d in doses)
    hours = lambda t: (t - t0).total_seconds() / 3600.0
    sl = [(hours(t), mg) for t, mg, r in doses if r == 'SL']
    nsl = [(hours(t), mg, ROUTE_KEY[r]) for t, mg, r in doses if r != 'SL']
    if not sl:
        return ['no sublingual dose in the file: this check is about the sublingual part; nothing to do']
    reg = c.Regimen('owner', sl, nsl, days=int(max(hours(t) for t, _v, _u in labs) // 24) + 1)
    basis = c.Basis(reg, c.m2_shape())
    out = [f'doses: {len(doses)} ({len(sl)} sublingual, {len(nsl)} other)  labs: {len(labs)}']
    keep_t, keep_y, sigma = [], [], []
    reasons = {}
    for t, v, unit in labs:
        h = hours(t)
        since = reg.hours_since_last_sl([h])[0]
        d = rules.assess_lab(time_known_to_minute=True, last_sl_dose_known=math.isfinite(since), hours_since_last_sl=since if math.isfinite(since) else None,
                             history_gate_passed=history_complete and h >= 0)
        for r in d.reasons:
            reasons[r] = reasons.get(r, 0) + 1
        if d.decision == 'REJECT':
            continue
        pg = v / 3.671 if unit == 'pmol/L' else v
        keep_t.append(h); keep_y.append(pg)
        sigma.append(c.SIGMA_LAB)
    out.append('lab decisions: ' + (', '.join(f'{k}={n}' for k, n in sorted(reasons.items())) or 'all usable') + f'; usable: {len(keep_t)}')
    if not keep_t:
        return out + [rules.REASON_TEXT['NO_USABLE_LAB']['en']]
    t_arr, y_arr = np.array(keep_t), np.array(keep_y)
    _idx, sg = __import__('run_sl_cal_01').apply_policy('delta', basis, t_arr, y_arr, c.SIGMA_LAB)
    methods = ['uncalibrated', 'A_sl_only', 'C_common'] + (['B2_two_pooled'] if reg.mixed else [])
    for m in methods:
        fit = c.fit_method(m, basis, t_arr, y_arr, sg, PRIOR_SD)
        loo = []
        if len(t_arr) >= 2:
            for i in range(len(t_arr)):
                rest = np.array([j for j in range(len(t_arr)) if j != i])
                f2 = c.fit_method(m, basis, t_arr[rest], y_arr[rest], sg[rest], PRIOR_SD)
                loo.append(math.log(y_arr[i]) - math.log(f2.total(basis, t_arr[i:i + 1])[0]))
        ig = 0.0 if m == 'uncalibrated' else 1 - fit.sl_sd ** 2 / PRIOR_SD ** 2
        out.append(f'{m:15s} k_sl={fit.ks:.2f} (ln sd {fit.sl_sd:.2f})' + (f' k_nsl={fit.kn:.2f}' if reg.mixed and m != 'uncalibrated' else '')
                   + f' info_gain={ig:.2f}' + (f' LOO_rms_ln={math.sqrt(np.mean(np.square(loo))):.2f}' if loo else ' LOO=n/a (one lab)'))
    verdict = rules.assess_set(len(keep_t), 1 - c.fit_method('A_sl_only', basis, t_arr, y_arr, sg, PRIOR_SD).sl_sd ** 2 / PRIOR_SD ** 2)
    out.append('set decision: ' + verdict.decision + ('' if not verdict.reasons else ' - ' + rules.REASON_TEXT[verdict.reasons[0]]['en']))
    out.append('Residuals are model-versus-reading differences, not a statement that any lab is wrong. No dose advice is implied.')
    return out


if __name__ == '__main__':
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('--doses', required=True); ap.add_argument('--labs', required=True)
    ap.add_argument('--history-complete', action='store_true')
    a = ap.parse_args()
    print('\n'.join(summarize(*load(a.doses, a.labs), a.history_complete)))
