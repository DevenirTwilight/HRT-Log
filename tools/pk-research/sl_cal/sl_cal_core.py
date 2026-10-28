"""SL-CAL-01 research core: calibrating the E2 TOTAL (sublingual M2 shape + non-sublingual compat curves + one background).

Research only. Pure numpy/scipy. Reads two frozen read-only inputs (the frozen M2 candidate table through
pr19_calibration_reference, and pk-params.json for the compat oral/gel curves). It never imports or changes production code,
never touches LIVE_LOCK files, and uses synthetic users only; no real health data exists in or is read by this module.

Units: hours, pg/mL, mg. Observation model: ln(y) ~ Normal(ln(mu), sigma^2) with mu = B + sum_j exp(theta_j) * X_j(t).
"""
import json
import math
import sys
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
sys.path.insert(0, str(HERE.parent))
import pr19_calibration_reference as ref  # noqa: E402  frozen M2 candidates + Price amplitude (research tool, not production)

PARAMS = json.loads((ROOT / 'pk-engine/src/main/resources/pk-params.json').read_text())['models']
M2_ID = 'p2x-39'                    # the product's frozen M2 representative (REQUIREMENTS §61)
BACKGROUND_PGML = 24.0              # M2 convention: assumed background, counted once
SIGMA_LAB = math.sqrt(math.log(1 + 0.299 ** 2))      # production LabFit.SIGMA_LAB (Zhang 2024 intra-individual CV of Cmax)
PRIOR_SD_P1A = math.sqrt(math.log(1 + 0.48 ** 2))    # production LabFit.PRIOR_SD_AMPLITUDE_DEFAULT
PRIOR_SD_RATE = math.sqrt(math.log(1 + 0.29 ** 2))   # production LabFit.PRIOR_SD_RATE
ASSUMED_TIMING_SD_H = 0.25                           # delta-method policy: assumed recorded-time spread (15 min), a design assumption


def m2_shape(candidate_id=M2_ID):
    m2, _ = ref.candidates()
    p = next(x for x in m2 if x['id'] == candidate_id)
    return p, ref.amplitude(p, BACKGROUND_PGML)


def sl_increment(t, sl_doses, shape):
    """Sublingual contribution above background: sum_i mg_i * A * response(t - t_i); zero before each dose."""
    p, a = shape
    t = np.asarray(t, float)
    out = np.zeros_like(t)
    for t0, mg in sl_doses:
        tau = t - t0
        out += np.where(tau >= 0, mg * a * ref.response(np.maximum(tau, 0.0), p), 0.0)
    return out


def compat_increment(t, nsl_doses, rate_scale=1.0):
    """Non-sublingual compat curve (production E2_ORAL / E2_GEL_ESTROGEL form): A*(exp(-lam*rate*tau) - exp(-ka*tau)) per mg.
    The rate scale multiplies only the elimination rate, as in production LabFit for non-SL models."""
    t = np.asarray(t, float)
    out = np.zeros_like(t)
    for t0, mg, key in nsl_doses:
        m = PARAMS[key]
        tau = t - t0
        pos = np.maximum(tau, 0.0)
        ka = m['ka_per_h']
        s = sum(term['A_per_mg'] * (np.exp(-term['lambda_per_h'] * rate_scale * pos) - np.exp(-ka * pos)) for term in m['terms'])
        out += np.where(tau >= 0, mg * s, 0.0)
    return np.maximum(out, 0.0)


class Regimen:
    def __init__(self, name, sl_doses, nsl_doses, days=14):
        self.name, self.sl_doses, self.nsl_doses, self.days = name, sl_doses, nsl_doses, days
        self.sl_times = np.array([d[0] for d in sl_doses])

    @property
    def mixed(self):
        return bool(self.nsl_doses)

    def hours_since_last_sl(self, t):
        t = np.atleast_1d(np.asarray(t, float))
        out = np.full(t.shape, np.inf)
        for i, x in enumerate(t):
            prior = self.sl_times[self.sl_times <= x]
            if prior.size:
                out[i] = x - prior.max()
        return out


def regimens(days=14):
    def daily(hour, mg, key=None):
        return [(24.0 * d + hour, mg) + ((key,) if key else ()) for d in range(days)]
    return {
        'SL_1mg_q12': Regimen('SL_1mg_q12', daily(8, 1.0) + daily(20, 1.0), [], days),
        'SL_2mg_q24': Regimen('SL_2mg_q24', daily(8, 2.0), [], days),
        'MIX_SL1mg_q12_oral2mg': Regimen('MIX_SL1mg_q12_oral2mg', daily(8, 1.0) + daily(20, 1.0), daily(8, 2.0, 'E2_ORAL'), days),
        'MIX_SL1mg_q24_gel1p5mg': Regimen('MIX_SL1mg_q24_gel1p5mg', daily(8, 1.0), daily(20, 1.5, 'E2_GEL_ESTROGEL'), days),
    }


def sl_period_hours(regimen):
    times = np.sort(regimen.sl_times)
    return float(np.median(np.diff(times))) if times.size > 1 else 24.0


def sample_lab_times(rng, regimen, scheme, n):
    """Actual sampling times on distinct days of the last 5 days. Schemes: trough (just before an SL dose), peak (0.5-2 h after one), random."""
    first = regimen.days - 5
    days = rng.choice(np.arange(first, regimen.days), size=n, replace=False) if n <= 5 else rng.integers(first, regimen.days, n)
    out = []
    for d in days:
        today = np.sort(regimen.sl_times[(regimen.sl_times >= 24.0 * d) & (regimen.sl_times < 24.0 * (d + 1))])
        if scheme == 'random':
            out.append(24.0 * d + rng.uniform(0, 24))
        elif scheme == 'peak':
            out.append(rng.choice(today) + rng.uniform(0.5, 2.0))
        elif scheme == 'trough':
            nxt = today[0] + 24.0 if today.size == 1 else today[0] + 24.0   # earliest SL dose of the next day for q12/q24 alike
            out.append(nxt - rng.uniform(0.0, 0.5))
        else:
            raise ValueError(scheme)
    return np.sort(np.array(out))


class Basis:
    """Unit curves for a regimen. S = sublingual increment; N(rate) = non-SL increment."""
    def __init__(self, regimen, shape):
        self.regimen, self.shape = regimen, shape

    def S(self, t):
        return sl_increment(t, self.regimen.sl_doses, self.shape)

    def N(self, t, rate=1.0):
        return compat_increment(t, self.regimen.nsl_doses, rate)


# ---------------------------------------------------------------------------------------------------------------- fitting

def map_fit(mu_jac, y_log, sigma, prior_cov, max_iter=60):
    """MAP of theta (ln amplitudes) under N(0, prior_cov). mu_jac(theta) -> (mean pg/mL at lab times, d ln mu / d theta).
    Levenberg-damped Gauss-Newton with backtracking; Laplace covariance at the mode. No concentration cap, no clipping of fits."""
    prior_cov = np.atleast_2d(np.asarray(prior_cov, float))
    m = prior_cov.shape[0]
    prec = np.linalg.inv(prior_cov)
    w = 1.0 / np.asarray(sigma, float)
    theta = np.zeros(m)

    def obj(th):
        mu = mu_jac(th)[0]
        if np.any(mu <= 0) or not np.all(np.isfinite(mu)):
            return math.inf
        r = (y_log - np.log(mu)) * w
        return 0.5 * float(r @ r + th @ prec @ th)

    cur, lam = obj(theta), 1e-3
    for _ in range(max_iter):
        mu, dl = mu_jac(theta)
        r = (y_log - np.log(mu)) * w
        j = dl * w[:, None]
        grad = j.T @ r - prec @ theta
        h = j.T @ j + prec
        done = True
        for _try in range(25):
            step = np.clip(np.linalg.solve(h + lam * np.eye(m), grad), -1.5, 1.5)
            cand = np.clip(theta + step, -5.0, 5.0)
            val = obj(cand)
            if val < cur:
                theta, cur, lam, done = cand, val, max(lam / 3, 1e-9), np.max(np.abs(step)) < 1e-8
                break
            lam *= 4
        if done:
            break
    j = mu_jac(theta)[1] * w[:, None]
    return theta, np.linalg.inv(j.T @ j + prec)


METHODS = ('uncalibrated', 'A_sl_only', 'B_two_independent', 'B2_two_pooled', 'C_common', 'D_p1a_framework')
POOL_TAU = 0.25   # B2: ln k_sl and ln k_nsl share a common level; their difference has prior sd tau (a design choice, not an estimate)


def prior_cov_for(method, mixed, sd):
    if method in ('A_sl_only', 'C_common') or not mixed:
        return np.array([[sd * sd]])
    if method == 'B_two_independent':
        return np.diag([sd * sd, sd * sd])
    if method == 'B2_two_pooled':
        return np.array([[sd * sd + POOL_TAU ** 2, sd * sd], [sd * sd, sd * sd + POOL_TAU ** 2]])
    if method == 'D_p1a_framework':       # shared amplitude + non-SL rate, production constants for the rate prior
        return np.diag([sd * sd, PRIOR_SD_RATE ** 2])
    raise ValueError(method)


class Fit:
    """Coefficients of a calibrated total: c(t) = B + ks*S(t) + kn*N(t; rate)."""
    def __init__(self, ks=1.0, kn=1.0, rate=1.0, sl_sd=math.nan, nsl_sd=math.nan, corr=math.nan, prior_var=math.nan, method='uncalibrated'):
        self.ks, self.kn, self.rate, self.sl_sd, self.nsl_sd, self.corr, self.prior_var, self.method = ks, kn, rate, sl_sd, nsl_sd, corr, prior_var, method

    def total(self, basis, t, background=BACKGROUND_PGML):
        out = background + self.ks * basis.S(t)
        return out + self.kn * basis.N(t, self.rate) if basis.regimen.mixed else out


def validate_labs(t_lab, y_lab, sigma):
    """Fail closed on anything the calibration must never see: non-finite or non-positive concentrations, bad times or sigmas."""
    t, y, s = (np.asarray(v, float) for v in (t_lab, y_lab, sigma))
    if not (t.shape == y.shape == s.shape):
        raise ValueError('lab arrays differ in length')
    if not (np.all(np.isfinite(t)) and np.all(np.isfinite(y)) and np.all(np.isfinite(s))):
        raise ValueError('non-finite lab value')
    if np.any(y <= 0) or np.any(s <= 0):
        raise ValueError('non-positive concentration or sigma')


def fit_method(method, basis, t_lab, y_lab, sigma, prior_sd, background=BACKGROUND_PGML):
    validate_labs(t_lab, y_lab, sigma)
    mixed = basis.regimen.mixed
    if method == 'uncalibrated' or len(t_lab) == 0:
        return Fit(sl_sd=prior_sd, nsl_sd=prior_sd, prior_var=prior_sd ** 2)
    s = basis.S(t_lab)
    n = basis.N(t_lab) if mixed else np.zeros_like(s)
    y_log = np.log(y_lab)
    cov0 = prior_cov_for(method, mixed, prior_sd)
    if not mixed or method == 'A_sl_only':
        def mj(th):
            ks = math.exp(th[0]); mu = background + ks * s + n
            return mu, (ks * s / mu)[:, None]
    elif method in ('B_two_independent', 'B2_two_pooled'):
        def mj(th):
            ks, kn = math.exp(th[0]), math.exp(th[1]); mu = background + ks * s + kn * n
            return mu, np.column_stack([ks * s / mu, kn * n / mu])
    elif method == 'C_common':
        def mj(th):
            k = math.exp(th[0]); mu = background + k * (s + n)
            return mu, (k * (s + n) / mu)[:, None]
    elif method == 'D_p1a_framework':
        def mj(th):
            k, rate = math.exp(th[0]), math.exp(th[1])
            nn = basis.N(t_lab, rate); mu = background + k * (s + nn)
            e = 1e-5
            up, dn = basis.N(t_lab, rate * math.exp(e)), basis.N(t_lab, rate * math.exp(-e))
            dv = k * (up - dn) / (2 * e) / mu
            return mu, np.column_stack([k * (s + nn) / mu, dv])
    else:
        raise ValueError(method)
    theta, cov = map_fit(mj, y_log, sigma, cov0)
    corr = float(cov[0, 1] / math.sqrt(cov[0, 0] * cov[1, 1])) if cov.shape[0] == 2 else math.nan
    if not mixed or method == 'A_sl_only':
        return Fit(ks=math.exp(theta[0]), sl_sd=math.sqrt(cov[0, 0]), prior_var=cov0[0, 0], method=method)
    if method in ('B_two_independent', 'B2_two_pooled'):
        return Fit(ks=math.exp(theta[0]), kn=math.exp(theta[1]), sl_sd=math.sqrt(cov[0, 0]), nsl_sd=math.sqrt(cov[1, 1]), corr=corr, prior_var=cov0[0, 0], method=method)
    if method == 'C_common':
        k = math.exp(theta[0]); return Fit(ks=k, kn=k, sl_sd=math.sqrt(cov[0, 0]), prior_var=cov0[0, 0], method=method)
    k = math.exp(theta[0]); return Fit(ks=k, kn=k, rate=math.exp(theta[1]), sl_sd=math.sqrt(cov[0, 0]), corr=corr, prior_var=cov0[0, 0], method=method)
