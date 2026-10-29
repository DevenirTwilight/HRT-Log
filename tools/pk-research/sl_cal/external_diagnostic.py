"""SL-CAL-01 conditional external diagnostic. NOT a validation.
Uses only the aggregate summaries that the repository's literature file already holds (no tables, figures or individual data are read
or copied). For each external group summary, computes the single amplitude multiplier g that would make the frozen M2 total match it:
  g = (observed - B) / (dose * A * response(t))     (dose proportionality is ASSUMED here and is itself unverified)
It also times the M2 tail so the 'history that must be complete' window can be stated from the model, not guessed.
"""
import argparse
import json
import math
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))
import sl_cal_core as c  # noqa: E402

PMOL_PER_PG = 3.671        # production Units.kt
SOURCE = c.ROOT / 'pk-engine/src/test/resources/sublingual-literature-validation.json'
GROUPS = {'doll_sl_peak': 'A_fitting (the legacy model was fitted to it; the M2 anchor is Price)', 'pines_60min': 'B_external', 'yaish_90min': 'B_external', 'burnier_1h_fold': 'B_external'}
TAIL_FRACTIONS = (0.10, 0.03, 0.01, 0.001)


def run():
    d = json.loads(SOURCE.read_text())
    rec = {r['id']: r for r in d['records']}
    p, a = c.m2_shape()
    peak = max(a * c.ref.response(np.arange(0, 24, 0.01), p))
    rows = []
    for rid, group in GROUPS.items():
        r = rec[rid]
        if r['unit'] == 'pmol/L':
            obs = r['value'] / PMOL_PER_PG
        elif r['unit'] == 'fold':
            obs = r['value'] * c.BACKGROUND_PGML      # 'fold over basal'; basal = the model background, a stated assumption
        else:
            obs = r['value']
        inc_model = r['dose_mg'] * a * float(c.ref.response(r['time_h'], p))
        g = (obs - c.BACKGROUND_PGML) / inc_model
        rows.append(dict(id=rid, study=r['study_id'], group=group, dose_mg=r['dose_mg'], time_h=r['time_h'], assay=r['assay'],
                         observed_pgml_as_used=round(obs, 2), model_increment_pgml=round(inc_model, 2), g_needed=round(g, 3), ln_g=round(math.log(g), 3)))
    ln = np.array([x['ln_g'] for x in rows])
    external_only = np.array([x['ln_g'] for x in rows if x['group'] == 'B_external'])
    grid = np.arange(0.0, 96.0, 0.01)
    resp = a * c.ref.response(grid, p) / peak
    after_peak = grid >= grid[int(np.argmax(resp))]
    tail = {f'{f:g}': round(float(grid[after_peak][np.argmax(resp[after_peak] < f)]), 2) for f in TAIL_FRACTIONS}
    return dict(schema='sl-cal-01-external-diagnostic-v1', not_a_validation=True, clinical_accuracy_established=False,
                note='Aggregate summaries only. Different assays, populations, baselines and doses; dose proportionality assumed. A spread of g across studies is a statement about between-study heterogeneity, not about any person.',
                m2=dict(candidate=c.M2_ID, amplitude_pgml_per_mg=round(a, 4), background_pgml=c.BACKGROUND_PGML),
                groups=rows, ln_g_sd_all=round(float(np.std(ln, ddof=1)), 3), ln_g_sd_external_only=round(float(np.std(external_only, ddof=1)), 3),
                g_min=round(float(math.exp(ln.min())), 3), g_max=round(float(math.exp(ln.max())), 3),
                single_dose_tail_hours_until_fraction_of_peak_increment=tail,
                implied_prior_sd_note='The p1a prior sd (0.455) is 0.39 to 0.66 of the between-study ln-spread above (all four groups / the three external ones); it is a diagnostic ratio, not a fitted prior.')


if __name__ == '__main__':
    ap = argparse.ArgumentParser(); ap.add_argument('--out', required=True); args = ap.parse_args()
    Path(args.out).write_text(json.dumps(run(), indent=0, sort_keys=True, allow_nan=False) + '\n')
