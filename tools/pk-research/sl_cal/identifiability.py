"""SL-CAL-01 local identifiability analysis (synthetic sampling designs, no fitting of data).
For each regimen/scheme/lab-count, averages the Gauss-Newton information of ln k_sl and ln k_nsl over random lab-time draws, and reports
how well two coefficients separate. Usage: python identifiability.py --out docs/pk-research/sl-cal/results/sl-cal-01-identifiability.json
"""
import argparse
import json
import math
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))
import sl_cal_core as c  # noqa: E402

SEED = 20261029
DRAWS = 300
SD_LEVELS = (c.PRIOR_SD_P1A, 0.9)
MIXED = ('MIX_SL1mg_q12_oral2mg', 'MIX_SL1mg_q24_gel1p5mg')
SL_ONLY = ('SL_1mg_q12', 'SL_2mg_q24')
B_PRIOR_SD_PGML = 12.0     # design assumption for the background uncertainty used in the B-confounding probe
RULE = dict(min_information_gain=0.5, max_abs_posterior_corr=0.8)


def two_param(info_data, sd, pooled):
    prior = c.prior_cov_for('B2_two_pooled' if pooled else 'B_two_independent', True, sd)
    post = np.linalg.inv(info_data + np.linalg.inv(prior))
    return post, prior


def analyse(name, scheme, n, rng):
    reg = c.regimens()[name]
    basis = c.Basis(reg, c.m2_shape())
    sigma2 = c.SIGMA_LAB ** 2
    recs = []
    for _ in range(DRAWS):
        t = c.sample_lab_times(rng, reg, scheme, n)
        s = basis.S(t)
        nn = basis.N(t) if reg.mixed else np.zeros_like(s)
        mu = c.BACKGROUND_PGML + s + nn
        recs.append((s / mu, nn / mu, t))
    out = dict(regimen=name, scheme=scheme, n_labs=n, draws=DRAWS)
    if reg.mixed:
        flat_corr, ranks, minratio = [], 0, []
        per_sd = {}
        for js, jn, _t in recs:
            info = np.array([[js @ js, js @ jn], [js @ jn, jn @ jn]]) / sigma2
            ev = np.linalg.eigvalsh(info)
            minratio.append(ev[0] / ev[1] if ev[1] > 0 else 0.0)
            if ev[0] / max(ev[1], 1e-300) < 1e-6:
                ranks += 1
            else:
                cov = np.linalg.inv(info)
                flat_corr.append(cov[0, 1] / math.sqrt(cov[0, 0] * cov[1, 1]))
            for sd in SD_LEVELS:
                for pooled in (False, True):
                    post, prior = two_param(info, sd, pooled)
                    key = f"sd{sd:.3f}_{'pooled' if pooled else 'independent'}"
                    ig_s, ig_n = 1 - post[0, 0] / sd ** 2, 1 - post[1, 1] / sd ** 2   # reference: the single-coefficient prior variance, same for every variant
                    corr = post[0, 1] / math.sqrt(post[0, 0] * post[1, 1])
                    ok = (ig_s >= RULE['min_information_gain'] and ig_n >= RULE['min_information_gain'] and abs(corr) <= RULE['max_abs_posterior_corr']) if not pooled else None
                    per_sd.setdefault(key, []).append((ig_s, ig_n, corr, ok, math.sqrt(post[0, 0]), math.sqrt(post[1, 1])))
        out['rank_deficient_fraction'] = round(ranks / DRAWS, 4)
        out['min_over_max_eigenvalue_median'] = round(float(np.median(minratio)), 6)
        out['flat_prior_posterior_corr_median'] = round(float(np.median(flat_corr)), 4) if flat_corr else None
        out['by_prior'] = {k: dict(ig_sl_median=round(float(np.median([r[0] for r in v])), 4), ig_nsl_median=round(float(np.median([r[1] for r in v])), 4),
                                    abs_posterior_corr_median=round(float(np.median([abs(r[2]) for r in v])), 4),
                                    posterior_sd_sl_median=round(float(np.median([r[4] for r in v])), 4), posterior_sd_nsl_median=round(float(np.median([r[5] for r in v])), 4),
                                    two_coefficients_acceptable_fraction=(round(float(np.mean([r[3] for r in v])), 4) if v[0][3] is not None else None)) for k, v in per_sd.items()}
    else:
        res = {}
        for sd in SD_LEVELS:
            rows = []
            for js, _jn, _t in recs:
                info_s = float(js @ js) / sigma2
                post_known_b = 1 / (info_s + 1 / sd ** 2)
                # (ln k_sl, B) jointly; d ln mu / dB = 1/mu
                return_b = None
                rows.append((1 - post_known_b / sd ** 2, post_known_b))
            res[f'sd{sd:.3f}'] = dict(ig_sl_background_known_median=round(float(np.median([r[0] for r in rows])), 4))
        bconf = {}
        for sd in SD_LEVELS:
            vals = []
            for js, _jn, t in recs:
                mu = c.BACKGROUND_PGML + basis.S(t)
                jb = 1.0 / mu
                jj = np.column_stack([js, jb])
                prior = np.diag([sd ** 2, B_PRIOR_SD_PGML ** 2])
                post = np.linalg.inv(jj.T @ jj / sigma2 + np.linalg.inv(prior))
                ig = 1 - post[0, 0] / sd ** 2
                vals.append((ig, post[0, 1] / math.sqrt(post[0, 0] * post[1, 1])))
            bconf[f'sd{sd:.3f}'] = dict(ig_sl_background_uncertain_median=round(float(np.median([v[0] for v in vals])), 4),
                                         abs_corr_k_background_median=round(float(np.median([abs(v[1]) for v in vals])), 4))
        out['background_known'] = res
        out['background_uncertain'] = bconf
    return out


def run():
    rows = []
    for ci, name in enumerate(MIXED + SL_ONLY):
        for si, scheme in enumerate(('trough', 'peak', 'random')):
            for n in (1, 2, 3, 5):
                rng = np.random.default_rng([SEED, ci, si, n])
                rows.append(analyse(name, scheme, n, rng))
    return dict(schema='sl-cal-01-identifiability-v1', synthetic_only=True, seed=SEED, draws=DRAWS, sigma_lab=c.SIGMA_LAB,
                background_pgml=c.BACKGROUND_PGML, b_prior_sd_pgml=B_PRIOR_SD_PGML, rule=RULE, rows=rows)


if __name__ == '__main__':
    ap = argparse.ArgumentParser(); ap.add_argument('--out', required=True); a = ap.parse_args()
    Path(a.out).write_text(json.dumps(run(), indent=0, sort_keys=True, allow_nan=False) + '\n')
