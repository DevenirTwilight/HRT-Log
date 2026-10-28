"""SL-CAL-01 synthetic study driver. Deterministic (fixed root seed, per-cell seeds), synthetic users only, no network, no repo data written
except the single --out JSON. Usage: python run_sl_cal_01.py --out docs/pk-research/sl-cal/results/sl-cal-01-synthetic.json [--users N] [--workers K]
"""
import argparse
import hashlib
import json
import math
import sys
from concurrent.futures import ProcessPoolExecutor
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))
import sl_cal_core as c  # noqa: E402

SCHEMA = 'sl-cal-01-synthetic-v1'
ROOT_SEED = 20261028
LAB_COUNTS = (1, 2, 3, 5)
SCHEMES = ('trough', 'peak', 'random')
TRUTH_CASES = (('SL_1mg_q12', 'sl'), ('SL_2mg_q24', 'sl'), ('MIX_SL1mg_q12_oral2mg', 'common'), ('MIX_SL1mg_q12_oral2mg', 'distinct'),
               ('MIX_SL1mg_q24_gel1p5mg', 'common'), ('MIX_SL1mg_q24_gel1p5mg', 'distinct'))
G_RANGE = (0.3, 3.0)                      # individual-amplitude range requested by the task book
DEFAULT = dict(sigma_obs=c.SIGMA_LAB, time_sd=0.0, bias=1.0, b_true=c.BACKGROUND_PGML, truth_shape=c.M2_ID, prior_sd=c.PRIOR_SD_P1A, truth='loguniform')
SWEEPS = {
    'baseline': {}, 'noise_low': dict(sigma_obs=0.10), 'noise_high': dict(sigma_obs=0.45),
    'timing_15min': dict(time_sd=0.25), 'timing_30min': dict(time_sd=0.50), 'assay_bias_1.25': dict(bias=1.25),
    'background_10': dict(b_true=10.0), 'background_40': dict(b_true=40.0), 'shape_mismatch_p2x-22': dict(truth_shape='p2x-22'),
    'prior_sd_0.9': dict(prior_sd=0.9), 'truth_prior_consistent': dict(truth='prior'),
}
EXCLUDE_WINDOWS = {'exclude_2.5h': 2.5, 'exclude_1.5h': 1.5, 'exclude_4h': 4.0}
POLICIES_DEFAULT = ('all', 'exclude_2.5h', 'delta')
POLICIES_BASELINE = ('all', 'exclude_1.5h', 'exclude_2.5h', 'exclude_4h', 'delta')
FIT_METHODS = ('A_sl_only', 'B_two_independent', 'B2_two_pooled', 'C_common', 'D_p1a_framework')
GRID_SPAN_H = 72.0
LOO_USERS = 40


def apply_policy(policy, basis, t_rec, y, sigma_fit):
    """Return (index array of used labs, sigma per used lab). Uses only recorded times and the population model (no individual data)."""
    idx = np.arange(len(t_rec))
    if policy == 'all':
        return idx, np.full(len(idx), sigma_fit)
    if policy in EXCLUDE_WINDOWS:
        keep = idx[basis.regimen.hours_since_last_sl(t_rec) >= EXCLUDE_WINDOWS[policy]]
        return keep, np.full(len(keep), sigma_fit)
    if policy == 'delta':
        h = 0.05
        def pop(t):
            return c.BACKGROUND_PGML + basis.S(t) + (basis.N(t) if basis.regimen.mixed else 0.0)
        dln = (np.log(pop(t_rec + h)) - np.log(pop(t_rec - h))) / (2 * h)
        return idx, np.sqrt(sigma_fit ** 2 + (c.ASSUMED_TIMING_SD_H * dln) ** 2)
    raise ValueError(policy)


def curve_metrics(fit_c, truth_c, basis_c, grid, S_g, N_g, mu_true, b_model):
    """fit_c: Fit; compares fitted total with the true total on the grid (increment above each side's own background too)."""
    est = b_model + fit_c.ks * S_g + (fit_c.kn * (N_g if fit_c.rate == 1.0 else basis_c.N(grid, fit_c.rate)) if basis_c.regimen.mixed else 0.0)
    lr = np.log(est) - np.log(mu_true)
    last = grid >= grid[-1] - 24.0
    inc_true = mu_true - truth_c['b_true']
    inc_est = est - b_model
    ok = inc_true > 5.0
    inc_lr = np.log(np.maximum(inc_est[ok], 1e-9)) - np.log(inc_true[ok])
    return dict(curve_med=float(np.median(np.abs(lr))), within15=float(np.mean(np.abs(lr) <= math.log(1.5))),
                peak_err=float(np.log(est[last].max()) - np.log(mu_true[last].max())),
                trough_err=float(np.log(est[last].min()) - np.log(mu_true[last].min())),
                inc_med=float(np.median(np.abs(inc_lr))) if ok.any() else math.nan)


def run_cell(args):
    sweep_name, cfg_over, case_idx, scheme, n_labs, users, policies, do_loo = args
    cfg = {**DEFAULT, **cfg_over}
    sweep_idx = list(SWEEPS).index(sweep_name)
    reg_name, truth_type = TRUTH_CASES[case_idx]
    rng = np.random.default_rng([ROOT_SEED, sweep_idx, case_idx, SCHEMES.index(scheme), n_labs])
    regimen = c.regimens()[reg_name]
    model_shape = c.m2_shape()
    truth_shape = c.m2_shape(cfg['truth_shape'])
    model_basis, truth_basis = c.Basis(regimen, model_shape), c.Basis(regimen, truth_shape)
    grid = np.arange(regimen.days * 24 - GRID_SPAN_H, regimen.days * 24, 0.1)
    S_g = model_basis.S(grid)
    N_g = model_basis.N(grid) if regimen.mixed else np.zeros_like(grid)
    S_true_g = truth_basis.S(grid)
    N_true_g = truth_basis.N(grid) if regimen.mixed else np.zeros_like(grid)
    rows = {}   # (policy, method) -> list of metric dicts
    for _ in range(users):
        if cfg['truth'] == 'prior':
            g_sl = math.exp(rng.normal(0, c.PRIOR_SD_P1A))
            g_nsl = g_sl if truth_type == 'common' else math.exp(rng.normal(0, c.PRIOR_SD_P1A))
        else:
            g_sl = math.exp(rng.uniform(math.log(G_RANGE[0]), math.log(G_RANGE[1])))
            g_nsl = g_sl if truth_type == 'common' else math.exp(rng.uniform(math.log(G_RANGE[0]), math.log(G_RANGE[1])))
        if truth_type == 'sl':
            g_nsl = 1.0
        t_act = c.sample_lab_times(rng, regimen, scheme, n_labs)
        t_rec = t_act + (rng.normal(0, cfg['time_sd'], n_labs) if cfg['time_sd'] > 0 else 0.0)
        t_rec = np.maximum(t_rec, 1.0)
        mu_act = cfg['b_true'] + g_sl * truth_basis.S(t_act) + (g_nsl * truth_basis.N(t_act) if regimen.mixed else 0.0)
        y = mu_act * cfg['bias'] * np.exp(cfg['sigma_obs'] * rng.standard_normal(n_labs))
        mu_true = cfg['b_true'] + g_sl * S_true_g + g_nsl * N_true_g
        truth_c = {'b_true': cfg['b_true']}
        want_loo = do_loo and n_labs >= 2
        for policy in policies:
            idx, sigma = apply_policy(policy, model_basis, t_rec, y, c.SIGMA_LAB)
            variants = [('uncalibrated', None)] + [(m, None) for m in FIT_METHODS] + [('oracle', None)]
            for method, _ in variants:
                if method == 'oracle':
                    fit = c.Fit(ks=g_sl, kn=g_nsl if regimen.mixed else 1.0, method='oracle')
                elif len(idx) == 0:
                    fit = c.Fit(sl_sd=cfg['prior_sd'], nsl_sd=cfg['prior_sd'], prior_var=cfg['prior_sd'] ** 2)
                else:
                    fit = c.fit_method(method, model_basis, t_rec[idx], y[idx], sigma, cfg['prior_sd'])
                m = curve_metrics(fit, truth_c, model_basis, grid, S_g, N_g, mu_true, c.BACKGROUND_PGML)
                m['refused'] = float(len(idx) == 0 and method not in ('oracle',))
                m['n_used'] = float(len(idx))
                m['sl_err'] = math.log(fit.ks) - math.log(g_sl)
                m['nsl_err'] = (math.log(fit.kn) - math.log(g_nsl)) if regimen.mixed else math.nan
                m['sl_z'] = m['sl_err'] / fit.sl_sd if fit.sl_sd == fit.sl_sd and fit.sl_sd > 0 and method not in ('uncalibrated', 'oracle') else math.nan
                m['ig_sl'] = 1 - fit.sl_sd ** 2 / fit.prior_var if fit.prior_var == fit.prior_var and method not in ('uncalibrated', 'oracle') and len(idx) else 0.0
                m['corr'] = fit.corr
                m['g_sl'] = g_sl
                if want_loo and method not in ('oracle',):
                    errs, errs_np = [], []
                    for i in range(n_labs):
                        rest = np.array([j for j in range(n_labs) if j != i])
                        keep, sg = apply_policy(policy, model_basis, t_rec[rest], y[rest], c.SIGMA_LAB)
                        if method == 'uncalibrated' or len(keep) == 0:
                            f2 = c.Fit()
                        else:
                            f2 = c.fit_method(method, model_basis, t_rec[rest][keep], y[rest][keep], sg, cfg['prior_sd'])
                        pred = f2.total(model_basis, np.array([t_rec[i]]))[0]
                        e = math.log(y[i]) - math.log(pred)
                        errs.append(e)
                        if model_basis.regimen.hours_since_last_sl(t_rec[i:i + 1])[0] >= 2.5:
                            errs_np.append(e)
                    m['loo_rms'] = math.sqrt(float(np.mean(np.square(errs))))
                    m['loo_rms_nonpeak'] = math.sqrt(float(np.mean(np.square(errs_np)))) if errs_np else math.nan
                rows.setdefault((policy, method), []).append(m)
    out = []
    for (policy, method), lst in rows.items():
        out.append(summarize(sweep_name, reg_name, truth_type, scheme, n_labs, policy, method, lst))
    return out


def q(values, p):
    a = np.array([v for v in values if v == v], float)
    return float(np.percentile(a, p)) if a.size else None


def r5(x):
    return None if x is None or (isinstance(x, float) and not math.isfinite(x)) else round(float(x), 5)


def summarize(sweep, reg, truth, scheme, n, policy, method, lst):
    col = lambda k: [m.get(k, math.nan) for m in lst]
    sl = [abs(v) for v in col('sl_err')]
    z = [abs(v) for v in col('sl_z') if v == v]
    out = dict(sweep=sweep, regimen=reg, truth=truth, scheme=scheme, n_labs=n, policy=policy, method=method, users=len(lst),
               refused_rate=r5(np.mean(col('refused'))), mean_labs_used=r5(np.mean(col('n_used'))),
               curve_med_abs_log_median=r5(q(col('curve_med'), 50)), curve_med_abs_log_p90=r5(q(col('curve_med'), 90)),
               within_1p5x_median=r5(q(col('within15'), 50)), peak_err_median=r5(q(col('peak_err'), 50)),
               peak_abs_err_p90=r5(q([abs(v) for v in col('peak_err')], 90)), trough_err_median=r5(q(col('trough_err'), 50)),
               trough_abs_err_p90=r5(q([abs(v) for v in col('trough_err')], 90)), increment_abs_log_median=r5(q(col('inc_med'), 50)),
               sl_coef_signed_err_median=r5(q(col('sl_err'), 50)), sl_coef_abs_err_median=r5(q(sl, 50)), sl_coef_abs_err_p90=r5(q(sl, 90)),
               nsl_coef_abs_err_median=r5(q([abs(v) for v in col('nsl_err') if v == v], 50)),
               sl_coverage90=r5(np.mean([v <= 1.6449 for v in z])) if z else None, ig_sl_median=r5(q(col('ig_sl'), 50)),
               posterior_corr_median=r5(q(col('corr'), 50)))
    if any('loo_rms' in m for m in lst):
        out['loo_rms_median'] = r5(q([m.get('loo_rms', math.nan) for m in lst], 50))
        out['loo_rms_nonpeak_median'] = r5(q([m.get('loo_rms_nonpeak', math.nan) for m in lst], 50))
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--out', required=True)
    ap.add_argument('--users', type=int, default=100, help='users per cell in the baseline sweep')
    ap.add_argument('--sweep-users', type=int, default=30, help='users per cell in sensitivity sweeps')
    ap.add_argument('--workers', type=int, default=3)
    ap.add_argument('--only', default=None, help='comma list of sweeps (testing)')
    args = ap.parse_args()
    jobs = []
    for name, over in SWEEPS.items():
        if args.only and name not in args.only.split(','):
            continue
        users = args.users if name == 'baseline' else args.sweep_users
        policies = POLICIES_BASELINE if name == 'baseline' else POLICIES_DEFAULT
        for case in range(len(TRUTH_CASES)):
            for scheme in SCHEMES:
                for n in LAB_COUNTS:
                    jobs.append((name, over, case, scheme, n, users, policies, name == 'baseline'))
    rows = []
    if args.workers <= 1:
        for j in jobs:
            rows += run_cell(j)
    else:
        with ProcessPoolExecutor(args.workers) as ex:
            for part in ex.map(run_cell, jobs, chunksize=4):
                rows += part
    cfg = dict(root_seed=ROOT_SEED, lab_counts=LAB_COUNTS, schemes=SCHEMES, truth_cases=TRUTH_CASES, g_range=G_RANGE, default=DEFAULT,
               sweeps=SWEEPS, exclude_windows_h=EXCLUDE_WINDOWS, assumed_timing_sd_h=c.ASSUMED_TIMING_SD_H, pool_tau=c.POOL_TAU,
               fit_methods=FIT_METHODS, grid_span_h=GRID_SPAN_H, background_pgml=c.BACKGROUND_PGML, m2_candidate=c.M2_ID,
               sigma_fit=c.SIGMA_LAB, prior_sd_p1a=c.PRIOR_SD_P1A, users_baseline=args.users, users_sweep=args.sweep_users, loo_users='all baseline users with n_labs>=2')
    doc = dict(schema=SCHEMA, synthetic_only=True, clinical_accuracy_established=False, real_health_data_used=False, config=cfg,
               rows=sorted(rows, key=lambda r: (r['sweep'], r['regimen'], r['truth'], r['scheme'], r['n_labs'], r['policy'], r['method'])))
    text = json.dumps(doc, separators=(',', ':'), sort_keys=True, allow_nan=False) + '\n'
    Path(args.out).write_text(text)
    print(len(rows), 'rows', hashlib.sha256(text.encode()).hexdigest())


if __name__ == '__main__':
    main()
