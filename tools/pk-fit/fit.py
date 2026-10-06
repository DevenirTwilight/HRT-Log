#!/usr/bin/env python3
"""
Fits the literature-based concentration models and writes them into
pk-engine/src/main/resources/pk-params.json under "models".

Model form (an assumed structure fitted to observed values, not a published model):
  response to 1 mg at time t >= 0:  h(t) = sum_j A_j * (exp(-lam_j t) - exp(-ka t))
  repeated doses add up (linear superposition).
Every target below names the literature entry it comes from (topic:ref ids in pk-params.json).
Pure Python, no third-party packages. Run:  python3 tools/pk-fit/fit.py
"""
import json, math, os

LN2 = math.log(2)
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
PARAMS = os.path.join(ROOT, "pk-engine/src/main/resources/pk-params.json")

def h(t, terms, ka):
    if t < 0: return 0.0
    return sum(a * (math.exp(-lam * t) - math.exp(-ka * t)) for a, lam in terms)

def auc_inf(terms, ka):
    return sum(a * (1 / lam - 1 / ka) for a, lam in terms)

def auc_0_t(terms, ka, t):
    return sum(a * ((1 - math.exp(-lam * t)) / lam - (1 - math.exp(-ka * t)) / ka) for a, lam in terms)

def peak(terms, ka, t_max=400.0):
    """Cmax and Tmax of h by a coarse scan plus golden-section refinement."""
    step = 0.01 if t_max < 50 else 0.05
    best_t, best = 0.0, -1.0
    t = 0.0
    while t <= t_max:
        v = h(t, terms, ka)
        if v > best: best, best_t = v, t
        t += step
    lo, hi = max(0.0, best_t - step), best_t + step
    g = (math.sqrt(5) - 1) / 2
    for _ in range(60):
        a, b = hi - g * (hi - lo), lo + g * (hi - lo)
        if h(a, terms, ka) > h(b, terms, ka): hi = b
        else: lo = a
    tm = (lo + hi) / 2
    return h(tm, terms, ka), tm

def bisect(f, lo, hi, it=200):
    flo = f(lo)
    for _ in range(it):
        mid = (lo + hi) / 2
        fm = f(mid)
        if (fm > 0) == (flo > 0): lo, flo = mid, fm
        else: hi = mid
    return (lo + hi) / 2

def fit_one_term(t_half, cmax, auc):
    """One term with a fixed apparent half-life; ka and A chosen so Cmax and AUC(0-inf) match."""
    lam = LN2 / t_half
    def cmax_for(ka):
        a = auc / (1 / lam - 1 / ka)
        return peak([(a, lam)], ka)[0] - cmax
    ka = bisect(cmax_for, lam * 1.0001, 50.0)
    a = auc / (1 / lam - 1 / ka)
    return [(a, lam)], ka

def ss_profile(terms, ka, dose, tau, n=60):
    """Steady-state Cmax, Cmin, average and AUC over one interval after n doses."""
    t0 = (n - 1) * tau
    vals = []
    t = 0.0
    while t <= tau + 1e-9:
        vals.append(sum(dose * h(t0 + t - k * tau, terms, ka) for k in range(n)))
        t += 0.05
    auc = sum((vals[i] + vals[i + 1]) / 2 * 0.05 for i in range(len(vals) - 1))
    return max(vals), min(vals), auc / tau, auc

def model(unit, terms, ka, basis, assumption, checks, extra=None):
    m = {"unit": unit, "ka_per_h": ka, "terms": [{"A_per_mg": a, "lambda_per_h": lam, "t_half_h": LN2 / lam} for a, lam in terms],
         "basis": basis, "assumption": assumption, "checks": checks}
    if extra: m.update(extra)
    return m

models = {}

# --- Oral estradiol valerate: Zhang 2024 (1 mg fasted, LC-MS/MS, baseline-corrected, n=24) -----------------
terms, ka = fit_one_term(14.48, 20.47, 575.99)
c, tm = peak(terms, ka)
z4 = (4 * peak(terms, ka)[0], 4 * auc_0_t(terms, ka, 48))
nat = ss_profile(terms, ka, 2.0, 24.0)
models["EV_ORAL"] = model("pg/mL", terms, ka,
    ["estradiol_oral_sl_im:Zhang2024 (t1/2 14.48 h, Cmax 20.47 pg/mL, AUC0-inf 575.99 pg*h/mL, 1 mg EV fasted)"],
    "One absorption + one elimination term; the apparent half-life is taken as observed (it reflects absorption and estrone recycling, not intrinsic E2 clearance).",
    {"tmax_h": tm, "cmax_1mg": c, "zimmermann1998_4mg_cmax": z4[0], "zimmermann1998_4mg_auc0_48": z4[1],
     "natazia_2mg_ss_cmax": nat[0], "natazia_2mg_ss_auc24": nat[3]},
    {"cv": 0.37, "cv_source": "estradiol_oral_sl_im:Zhang2024 (AUC0-inf CV 37.41%)", "e2_per_mg": 272.38 / 356.47})

# --- Oral micronized estradiol: Activella label (1 mg single dose, n=24) ----------------------------------
terms, ka = fit_one_term(14.0, 26.8, 766.5)
c, tm = peak(terms, ka)
oro = ss_profile(terms, ka, 2.0, 24.0)
models["E2_ORAL"] = model("pg/mL", terms, ka,
    ["estradiol_oral_sl_im:FDA_ACTIVELLA (t1/2 14.0 h, Cmax 26.8 pg/mL, AUC0-t 766.5 pg*h/mL used as AUC0-inf, 1 mg)"],
    "One absorption + one elimination term with the observed apparent half-life (14 h). AUC0-t is used for AUC0-inf (the label gives no AUC0-inf), which slightly underestimates exposure.",
    {"tmax_h": tm, "cmax_1mg": c, "oromone_2mg_ss_cmax": oro[0], "oromone_2mg_ss_cmin": oro[1], "oromone_2mg_ss_cavg": oro[2], "oromone_2mg_ss_auc24": oro[3]},
    {"cv": 0.48, "cv_source": "estradiol_oral_sl_im:FDA_ACTIVELLA (AUC geometric CV 48%)", "e2_per_mg": 1.0})

# --- Cyproterone acetate oral --------------------------------------------------------------------------------
# Reference weight 70 kg; amplitudes scale with 70 / body weight (clearance is reported per kg).
REF_W = 70.0
cl = 3.6 * 60 / 1000 * REF_W            # L/h, Kuhnz 1993 single 2 mg: 3.6 mL/min/kg
auc_per_mg = 0.88 / cl * 1000            # ng*h/mL per mg; F 0.88 (Huber 1988, SmPC)
lams = [LN2 / 0.8, LN2 / 43.9, LN2 / 78.6]   # Kuhnz 1993 initial 0.8 h; SmPC terminal 43.9 h; Kuhnz 1993 after cycle 3: 78.6 h
target = {"cmax_per_mg": 15.2 / 2, "tmax": 1.6, "R": 2.2, "t_half_single": 54.0, "t_half_multi": 78.6}

# Apparent half-lives: after one dose (24-168 h) and after 21 daily doses (24-168 h after the last one).
def apparent_t_half(f, t1=24.0, t2=168.0):
    return LN2 * (t2 - t1) / math.log(f(t1) / f(t2))

def cpa_terms(ka, f1, f3):
    f2 = 1 - f1 - f3
    return [(auc_per_mg * f / (1 / lam - 1 / ka), lam) for f, lam in zip((f1, f2, f3), lams)]

def cpa_err(ka, f1, f3):
    if f1 <= 0 or f3 <= 0 or f1 + f3 >= 1: return 1e9
    t = cpa_terms(ka, f1, f3)
    c, tm = peak(t, ka, 30)
    r = auc_inf(t, ka) / auc_0_t(t, ka, 24)
    single = apparent_t_half(lambda x: h(x, t, ka))
    multi = apparent_t_half(lambda x: sum(h(x + k * 24, t, ka) for k in range(21)))
    # Accumulation and the longer half-life after repeated doses weigh 3x (the user's priorities for CPA).
    weight = {"R": 3.0, "t_half_multi": 3.0}
    return sum(weight.get(k, 1.0) * ((v - target[k]) / target[k]) ** 2 for k, v in
               (("cmax_per_mg", c), ("tmax", tm), ("R", r), ("t_half_single", single), ("t_half_multi", multi)))

best = None
for ka in [0.4 + 0.2 * i for i in range(20)]:
    for f1 in [0.05 * i for i in range(1, 19)]:
        for f3 in [0.03 * i for i in range(1, 30)]:
            e = cpa_err(ka, f1, f3)
            if best is None or e < best[0]: best = (e, ka, f1, f3)
e, ka, f1, f3 = best
step = [0.05, 0.02, 0.01]
for _ in range(400):  # coordinate refinement
    improved = False
    for i in range(3):
        for s in (1, -1):
            p = [ka, f1, f3]; p[i] += s * step[i]
            ne = cpa_err(*p)
            if ne < e: e, (ka, f1, f3), improved = ne, p, True
    if not improved: step = [x / 2 for x in step]
terms = cpa_terms(ka, f1, f3)
c2, tm2 = peak(terms, ka, 30)
# High-dose absorption: slower ka so that Cmax after 100 mg is ~270 ng/mL (Huber 1988: 255; SmPC: about 285);
# the AUC per mg stays the same (clearance is similar at 2 mg and 100 mg). The resulting Tmax is reported.
def terms_at(kx):
    return [(a * (1 / lam - 1 / ka) / (1 / lam - 1 / kx), lam) for a, lam in terms]
ka100 = bisect(lambda kx: 100 * peak(terms_at(kx), kx, 60)[0] - 270.0, 0.005, ka)
c100, tmax100 = peak(terms_at(ka100), ka100, 60); c100 *= 100
single = apparent_t_half(lambda t: h(t, terms, ka))
multi = apparent_t_half(lambda t: sum(h(t + k * 24, terms, ka) for k in range(21)))
ss2 = ss_profile(terms, ka, 2.0, 24.0, 40)
models["CPA_ORAL"] = model("ng/mL", terms, ka,
    ["transdermal_cpa:Kuhnz1993 (2 mg: Cmax 15.2 ng/mL, initial t1/2 0.8 h, single-dose terminal t1/2 54 h, clearance 3.6 mL/min/kg, t1/2 78.6 h after repeated dosing, ~2-fold accumulation)",
     "transdermal_cpa:SMPC_DIANETTE_UK (Tmax 1.6 h after 2 mg; AUC accumulation 2.2-2.4 fold)",
     "transdermal_cpa:SMPC_ANDROCUR_UK (F 88 %, terminal t1/2 43.9 h, Tmax 3 h after 100 mg)",
     "transdermal_cpa:Huber1988 (F 88 %, Cmax 255 +/- 110 ng/mL after 100 mg)"],
    "Three elimination terms (0.8 h, 43.9 h, 78.6 h) sharing one absorption rate; their shares are fitted so that Cmax and Tmax after 2 mg and the ~2.2-fold accumulation of daily dosing match. The 78.6 h term makes the half-life longer after repeated doses. Absorption is slower at high doses (Cmax per mg at 100 mg is about a third of that at 2 mg with similar clearance): ka is fitted to Cmax ~270 ng/mL at 100 mg and interpolated on log(dose) between 2 mg and 100 mg. Amplitudes are for 70 kg and scale with 70 / weight. Age effects (about 95 h in older men) are not modelled.",
    {"cmax_2mg": 2 * c2, "tmax_2mg_h": tm2, "accumulation_R": auc_inf(terms, ka) / auc_0_t(terms, ka, 24),
     "cmax_100mg": c100, "tmax_100mg_h": tmax100, "apparent_t_half_single_h": single, "apparent_t_half_after_21_daily_h": multi,
     "ss_2mg_cmax": ss2[0], "ss_2mg_cmin": ss2[1]},
    {"cv": 0.43, "cv_source": "transdermal_cpa:Huber1988 (Cmax SD 110 / 255 ng/mL)", "ref_weight_kg": REF_W,
     "ka_dose_points": [{"dose_mg": 2, "ka_per_h": ka}, {"dose_mg": 100, "ka_per_h": ka100}]})

with open(PARAMS) as f: data = json.load(f)
data["models"] = models
data["models_note"] = "Fitted by tools/pk-fit/fit.py from the literature entries named in each model's basis; 'checks' are model outputs for comparison with the literature, recomputed by the engine's validation tests."
with open(PARAMS, "w") as f: json.dump(data, f, ensure_ascii=False, indent=1)
for k, m in models.items():
    print(k, "ka=%.4f" % m["ka_per_h"], [("%.4g" % t["A_per_mg"], "%.3g h" % t["t_half_h"]) for t in m["terms"]])
    print("   ", {kk: round(v, 3) for kk, v in m["checks"].items()})
