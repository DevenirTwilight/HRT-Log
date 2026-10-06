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


# --- Infusion helpers (patch: constant release over the wear time) -------------------------------------------
def infusion(t, terms, ka, rate, wear):
    """Concentration at t for a constant input `rate` (mg/h) from 0 to `wear` hours."""
    if t <= 0: return 0.0
    te = min(t, wear)
    def g(x): return (1 - math.exp(-x * te)) * math.exp(-x * (t - te)) / x
    return rate * sum(a * (g(lam) - g(ka)) for a, lam in terms)

# --- Estradiol patch: Vivelle-Dot label, steady state, twice weekly (3.5-day wear), abdomen ------------------
LAM_SKIN = LN2 / 6.8          # apparent half-life after removal 5.9-7.7 h (Vivelle-Dot label); midpoint
K_SYS = LN2 / 1.0             # estradiol plasma half-life about 1 h (Dermestril RCP)
vivelle = [(0.0375, 34.0), (0.05, 57.0), (0.075, 72.0), (0.1, 89.0)]   # mg/day, Cavg pg/mL
# Steady state of a constant input gives C = rate * AUC_per_mg, so AUC_per_mg = Cavg / (mg/h); least squares over the four rates.
num = sum((r / 24) * c for r, c in vivelle); den = sum((r / 24) ** 2 for r, c in vivelle)
patch_auc = num / den
a_patch = patch_auc / (1 / LAM_SKIN - 1 / K_SYS)
pterms = [(a_patch, LAM_SKIN)]
def patch_ss(rate_mg_day, wear, n=12):
    f = lambda t: sum(infusion(t - k * wear, pterms, K_SYS, rate_mg_day / 24, wear) for k in range(n))
    t0 = (n - 1) * wear
    vals = [f(t0 + i * 0.25) for i in range(int(wear / 0.25) + 1)]
    return max(vals), min(vals), sum(vals) / len(vals)
pchecks = {}
for r, c in vivelle: pchecks["vivelle_%g_cavg" % r] = patch_ss(r, 84.0)[2]
for r in (0.025, 0.05, 0.1): pchecks["climara_%g_cavg_single_week" % r] = (lambda f: sum(f(i * 0.5) for i in range(337)) / 337)(lambda t: infusion(t, pterms, K_SYS, r / 24, 168.0))
models["E2_PATCH"] = model("pg/mL", pterms, K_SYS,
    ["transdermal_cpa:L_VIVELLEDOT (steady-state Cavg 34 / 57 / 72 / 89 pg/mL at 0.0375 / 0.05 / 0.075 / 0.1 mg/day; half-life after removal 5.9-7.7 h)",
     "transdermal_cpa:RCP_DERMESTRIL (estradiol plasma half-life about 1 h)"],
    "Constant release at the labelled rate during wear into a skin reservoir (apparent half-life 6.8 h, the midpoint of the label's 5.9-7.7 h after removal), then plasma (half-life about 1 h). The response is linear in the release rate. Application site (buttock about +17 % Cavg on the Climara label) and the within-wear rise and fall are not modelled.",
    pchecks, {"cv": 0.48, "cv_source": "transdermal_cpa:L_CLIMARA (relative SD of Cavg 48 %, abdomen)", "e2_per_mg": 1.0, "input": "zero-order release during wear"})

# --- Estradiol gel, one model per product (user decision 2026-10-06) -----------------------------------------
def gel_fit(t_half, ss_points, tau=24.0):
    """ss_points: [(dose mg/day, Cmax, Cavg)]; AUC per mg from Cavg (least squares), ka from the Cmax/Cavg ratio."""
    lam = LN2 / t_half
    auc = sum(d * cav * tau for d, cm, cav in ss_points) / sum(d * d for d, cm, cav in ss_points)
    ratio = sum(cm / cav for d, cm, cav in ss_points) / len(ss_points)
    def ss_ratio(ka):
        a = auc / (1 / lam - 1 / ka)
        cmax, _, cavg, _ = ss_profile([(a, lam)], ka, 1.0, tau, 40)
        return cmax / cavg - ratio
    ka = bisect(ss_ratio, lam * 1.001, 20.0)
    return [(auc / (1 / lam - 1 / ka), lam)], ka
# EstroGel's Cmax/Cavg ratio (1.64) cannot occur with a 36 h half-life and once-daily dosing in this structure, so the
# absorption rate is fixed by Tmax 4.5 h after gel application (Jarvinen 1999: 4-5 h) and only Cavg (AUC per mg) is fitted.
lam_eg = LN2 / 36.0
gka_eg = bisect(lambda k: math.log(k / lam_eg) / (k - lam_eg) - 4.5, lam_eg * 1.0001, 20.0)
gterms_eg = [((0.75 * 28.3 * 24 / 0.75 ** 2) / (1 / lam_eg - 1 / gka_eg), lam_eg)]
eg_ss = ss_profile(gterms_eg, gka_eg, 0.75, 24.0, 40)
models["E2_GEL_ESTROGEL"] = model("pg/mL", gterms_eg, gka_eg,
    ["transdermal_cpa:L_ESTROGEL (0.75 mg/day, day 14: Cmax 46.4 pg/mL, Cavg 28.3 pg/mL; apparent terminal half-life about 36 h)",
     "transdermal_cpa:Jarvinen1999 (Tmax 4-5 h after gel application)"],
    "Applies to EstroGel and Oestrogel (same 0.06 % formulation). One absorption and one elimination term; the apparent half-life (36 h) is the label value; AUC per mg from the steady-state Cavg; absorption rate from Tmax 4.5 h (Jarvinen 1999). The label's steady-state Cmax (46.4 pg/mL) is not reproduced: a 36 h half-life with once-daily dosing gives a flatter profile. Application area and body site are taken as on the label (no quantitative literature for other areas or sites).",
    {"ss_0.75mg_cmax": eg_ss[0], "ss_0.75mg_cavg": eg_ss[2], "tmax_single_h": peak(gterms_eg, gka_eg)[1]},
    {"cv": 0.9, "cv_source": "transdermal_cpa:L_DIVIGEL (CV of Cavg 81-149 %; no CV is given for EstroGel)", "e2_per_mg": 1.0, "products": ["oestrogel", "estrogel"]})
divi = [(0.25, 14.7, 9.8), (0.5, 28.4, 21.0), (1.0, 51.5, 30.5)]
gterms_dv, gka_dv = gel_fit(10.0, divi)
models["E2_GEL_DIVIGEL"] = model("pg/mL", gterms_dv, gka_dv,
    ["transdermal_cpa:L_DIVIGEL (steady state at 0.25 / 0.5 / 1.0 mg/day: Cmax 14.7 / 28.4 / 51.5, Cavg 9.8 / 21 / 30.5 pg/mL; apparent terminal half-life about 10 h)"],
    "One absorption and one elimination term; apparent half-life 10 h (label); AUC per mg fitted to the three steady-state Cavg values, absorption rate to the mean Cmax/Cavg ratio. Application area and site as on the label.",
    {("ss_%gmg_cavg" % d): ss_profile(gterms_dv, gka_dv, d, 24.0, 40)[2] for d, _, _ in divi} | {"sirvio2026_1.5mg_cavg": ss_profile(gterms_dv, gka_dv, 1.5, 24.0, 40)[2]},
    {"cv": 0.9, "cv_source": "transdermal_cpa:L_DIVIGEL (CV of Cavg 81-149 %)", "e2_per_mg": 1.0, "products": ["divigel"]})
lam_mid = LN2 / math.sqrt(36.0 * 10.0)
auc_mid = (gterms_eg[0][0] * (1 / gterms_eg[0][1] - 1 / gka_eg) + gterms_dv[0][0] * (1 / gterms_dv[0][1] - 1 / gka_dv)) / 2
ka_mid = math.sqrt(gka_eg * gka_dv)
gterms_mid = [(auc_mid / (1 / lam_mid - 1 / ka_mid), lam_mid)]
models["E2_GEL_OTHER"] = model("pg/mL", gterms_mid, ka_mid,
    ["E2_GEL_ESTROGEL and E2_GEL_DIVIGEL (no data for this product)"],
    "No pharmacokinetic data for this product (Estreva, home-made gel, others). Half-life is the geometric mean of the two products with data (36 h and 10 h, about 19 h); AUC per mg and absorption rate are their means. Shown with a wider band and the label 'no data for this product'.",
    {"estreva_single_1.5mg_cmax": 1.5 * peak(gterms_mid, ka_mid)[0]},
    {"cv": 1.2, "cv_source": "assumption: wider than the Divigel label CV (about 0.9) because no product data exist", "e2_per_mg": 1.0, "products": ["estreva", "diy"], "no_product_data": True})

# --- Estradiol valerate intramuscular: Schug 2012 (10 mg, n=24) and Oriowo 1980 (Tmax about 2 days) -----------
IM_TMAX = 48.0; IM_CMAX = 505.7 / 10; IM_AUC = 82660.0 / 10
def im_for(lam):
    ka = bisect(lambda k: math.log(k / lam) / (k - lam) - IM_TMAX, lam * 1.0001, 5.0)
    return [(IM_AUC / (1 / lam - 1 / ka), lam)], ka
lam_im = bisect(lambda lam: peak(*im_for(lam), 400)[0] - IM_CMAX, 0.001, LN2 / 5)
iterms, ika = im_for(lam_im)
elev = next((t for t in range(int(IM_TMAX), 1000) if h(t, iterms, ika) < 0.5 * peak(iterms, ika, 400)[0]), None)
models["EV_IM"] = model("pg/mL", iterms, ika,
    ["estradiol_oral_sl_im:Schug2012 (single 10 mg IM: Cmax 505.7 pg/mL, AUC0-t 82,660 pg*h/mL, geometric means)",
     "estradiol_oral_sl_im:Oriowo1980 (Tmax about 2 days; elevated for 7-8 days after 5 mg)"],
    "One absorption (depot) term and one elimination term fitted jointly to Cmax, AUC (Schug 2012) and Tmax 48 h (Oriowo 1980). AUC0-t is used for AUC0-inf.",
    {"tmax_h": peak(iterms, ika, 400)[1], "cmax_10mg": 10 * peak(iterms, ika, 400)[0], "hours_until_below_half_peak": elev},
    {"cv": 0.5, "cv_source": "assumption: Schug 2012 gives geometric means without dispersion in the abstract", "e2_per_mg": 272.38 / 356.47})


# --- Sublingual estradiol: Doll 2022 only (1 mg in 10 trans women, LC-MS/MS, sampled 0-8 h) ----------------------
# Targets: Tmax 1 h, Cmax 144 pg/mL, AUC0-8 = 1.8 x the same study's oral arm. Doll's oral arm reached 35 pg/mL at 8 h
# (its last sample), higher than the E2_ORAL model (26.6 pg/mL at 8 h), so the oral reference is E2_ORAL scaled to
# 35 pg/mL at 8 h. A fast term peaking at 144 pg/mL at 1 h has an AUC0-8 of at least ~390 (reached when its absorption
# and decline rates are equal, ~1/h); 1.8 x the oral reference is ~390, so the data leave essentially no room for a
# swallowed share. The swallowed share is set to what remains (about 0) and the conflict is documented.
oral = models["E2_ORAL"]; o_terms = [(t["A_per_mg"], t["lambda_per_h"]) for t in oral["terms"]]; o_ka = oral["ka_per_h"]
doll_scale = 35.0 / h(8.0, o_terms, o_ka)
oral_ref_auc8 = doll_scale * auc_0_t(o_terms, o_ka, 8.0)
k1, k2 = 0.9995, 1.0005                       # equal-rate limit C = B t e^(-t), written as a difference of exponentials
f_terms = [(144.0 / (math.exp(-k1) - math.exp(-k2)), k1)]; f_ka = k2
fast_auc8 = auc_0_t(f_terms, f_ka, 8.0)
s_frac = max(0.0, (1.8 * oral_ref_auc8 - fast_auc8) / auc_0_t(o_terms, o_ka, 8.0))
sl = lambda t: h(t, f_terms, f_ka) + s_frac * h(t, o_terms, o_ka)
t_sl, c_sl = max(((i * 0.01, sl(i * 0.01)) for i in range(1, 801)), key=lambda x: x[1])
auc8 = fast_auc8 + s_frac * auc_0_t(o_terms, o_ka, 8.0)
models["E2_SL"] = model("pg/mL", f_terms, f_ka,
    ["estradiol_oral_sl_im:Doll2022 (1 mg sublingual vs oral in 10 trans women: SL Tmax 1 h, Cmax 144 pg/mL, AUC0-8 1.8 x oral; oral 35 pg/mL at 8 h; LC-MS/MS; sampled to 8 h only)"],
    "Fast mucosal term (rise and decline both about 1/h) plus a swallowed share that behaves like oral estradiol (E2_ORAL). Fitted to Tmax 1 h, Cmax 144 pg/mL and AUC0-8 = 1.8 x the study's own oral arm (E2_ORAL scaled to its 35 pg/mL at 8 h). These three values leave essentially no room for a swallowed share, so after a few hours the curve falls close to zero. Everything after 8 h is extrapolation and is very likely an underestimate: Cortez 2024 reports trough estradiol of about 95 pg/mL on 6.2 mg/day (immunoassay). Doll 2022 does not report how long the tablet was held; only the default tier is calibrated, other tiers scale the mucosal share with hold time (extrapolation, no literature).",
    {"tmax_h": t_sl, "cmax_1mg": c_sl, "auc0_8": auc8, "doll_oral_reference_auc0_8": oral_ref_auc8, "ratio_auc0_8": auc8 / oral_ref_auc8, "swallowed_share": s_frac, "c_at_24h_1mg": sl(24.0)},
    {"cv": 0.6, "cv_source": "assumption: Doll 2022 abstract gives no dispersion; wider than oral (Activella AUC CV 48 %)", "e2_per_mg": 1.0,
     "swallowed_model": "E2_ORAL", "swallowed_share": s_frac, "default_tier": 2, "tier_minutes": [2, 5, 10, 15], "calibrated_hours": 8.0})

# --- Spironolactone and canrenone: Gardiner 1989 (100 mg daily, day 15, healthy men, n=12) ----------------------
def fit_ss(t_half, cmax_ss, auc_tau, dose, tau=24.0):
    lam = LN2 / t_half; auc1 = auc_tau / dose   # at steady state AUC over one interval = single-dose AUC0-inf
    def f(ka):
        a = auc1 / (1 / lam - 1 / ka)
        return ss_profile([(a, lam)], ka, dose, tau, 30)[0] - cmax_ss
    ka = bisect(f, lam * 1.0001, 30.0)
    return [(auc1 / (1 / lam - 1 / ka), lam)], ka
for key, name, th, cm, auc, d1, tmax_ss in (("SPI_PARENT", "spironolactone", 1.4, 80.0, 231.0, 72.0, 2.6),):
    t_, k_ = fit_ss(th, cm, auc, 100.0)
    ssp = ss_profile(t_, k_, 100.0, 24.0, 30)
    models[key] = model("ng/mL", t_, k_,
        ["spironolactone_progesterone:Gardiner1989 (100 mg daily, day 15: %s Cmax %g ng/mL, AUC0-24 %g ng*h/mL, post-steady-state t1/2 %g h)" % (name, cm, auc, th)],
        "One absorption and one elimination term for %s; half-life as measured after steady state, absorption rate and amplitude fitted to steady-state Cmax and AUC0-24. Data are from healthy men; food raises exposure (parent AUC about +95 %%, Overdiek 1986) and is not modelled." % name,
        {"day1_cmax_100mg": 100 * peak(t_, k_, 48)[0], "ss_cmax_100mg": ssp[0], "ss_tmax_h": max(((x * 0.05, sum(100 * h(29 * 24 + x * 0.05 - k * 24, t_, k_) for k in range(30))) for x in range(480)), key=lambda v: v[1])[0]},
        {"cv": 0.25 if key == "SPI_PARENT" else 0.15, "cv_source": "spironolactone_progesterone:Gardiner1989 (SD of steady-state Cmax: 20/80, 39/181)",
         "parent": "SPI", "compound": name})

# Canrenone: a single term cannot give Cmax,ss 181 with AUC0-24 2173 and a 16.5 h half-life, so two elimination terms
# (an unknown faster one and the measured 16.5 h) share one absorption rate; fitted to steady-state Cmax, AUC0-24 and
# Tmax and to day-1 Cmax (Gardiner 1989; Tmax from the 2014 Aldactone label, same data).
lam_c2 = LN2 / 16.5
def can_terms(ka, lam1, f1):
    auc1 = 2173.0 / 100.0
    return [(auc1 * f1 / (1 / lam1 - 1 / ka), lam1), (auc1 * (1 - f1) / (1 / lam_c2 - 1 / ka), lam_c2)]
def can_eval(ka, lam1, f1):
    t = can_terms(ka, lam1, f1)
    n = 30; t0 = (n - 1) * 24
    ssv = [(x * 0.1, sum(100 * h(t0 + x * 0.1 - k * 24, t, ka) for k in range(n))) for x in range(241)]
    tm, cm = max(ssv, key=lambda v: v[1])
    return cm, tm, 100 * peak(t, ka, 48)[0]
def can_err(ka, lam1, f1):
    if not (0 < f1 < 1) or lam1 <= lam_c2 * 1.5 or ka <= 0.05 or abs(ka - lam1) < 1e-3 or abs(ka - lam_c2) < 1e-3: return 1e9
    cm, tm, d1 = can_eval(ka, lam1, f1)
    return ((cm - 181) / 181) ** 2 + ((tm - 4.3) / 4.3) ** 2 + ((d1 - 155) / 155) ** 2
best = min(((can_err(k, l, f), k, l, f) for k in (0.2, 0.4, 0.6, 1.0, 1.5) for l in (0.11, 0.21, 0.35, 0.55, 0.85) for f in (0.2, 0.4, 0.6, 0.8)))
e, ka_c, l1, f1 = best; step = [0.1, 0.05, 0.1]
for _ in range(200):
    imp = False
    for i in range(3):
        for sg in (1, -1):
            p_ = [ka_c, l1, f1]; p_[i] += sg * step[i]
            ne = can_err(*p_)
            if ne < e: e, (ka_c, l1, f1), imp = ne, p_, True
    if not imp: step = [x / 2 for x in step]
    if max(step) < 1e-4: break
ct = can_terms(ka_c, l1, f1); cm, tm, d1 = can_eval(ka_c, l1, f1)
models["SPI_CANRENONE"] = model("ng/mL", ct, ka_c,
    ["spironolactone_progesterone:Gardiner1989 (100 mg daily: canrenone Cmax day 1 155 +/- 43, day 15 181 +/- 39 ng/mL, AUC0-24 2173 ng*h/mL, post-steady-state t1/2 16.5 h)",
     "spironolactone_progesterone:FDA_Aldactone_2014 (canrenone Tmax 4.3 h at steady state, same study)"],
    "Canrenone formed from spironolactone, described directly as a response to the spironolactone dose: one absorption/formation rate and two elimination terms (a faster one fitted, and the measured 16.5 h). Data are from healthy men; food raises exposure (canrenone AUC about +41 %, Overdiek 1986) and is not modelled.",
    {"ss_cmax_100mg": cm, "ss_tmax_h": tm, "day1_cmax_100mg": d1, "fast_t_half_h": LN2 / l1, "accumulation_auc": 2173.0 / (100 * auc_0_t(ct, ka_c, 24))},
    {"cv": 0.15, "cv_source": "spironolactone_progesterone:Gardiner1989 (SD of steady-state Cmax 39 / 181)", "parent": "SPI", "compound": "canrenone"})

# --- Oral micronized progesterone: illustrative only (user decision 2026-10-06) ---------------------------------
P4 = [(100.0, 17.3, 43.3), (200.0, 38.1, 101.2), (300.0, 60.6, 175.7)]   # Prometrium label: after 5 daily doses, Cmax, AUC0-10
lam_p = LN2 / ((8.77 + 9.98) / 2)    # Bijuva label (a different oral progesterone product): 8.77 / 9.98 h
pk_ = bisect(lambda k: math.log(k / lam_p) / (k - lam_p) - 1.8, lam_p * 1.0001, 30.0)   # Tmax 1.5-2.3 h (label)
def p4_auc10(a, d):
    n = 5; t0 = (n - 1) * 24
    f = lambda x: sum(d * h(t0 + x - k * 24, [(a, lam_p)], pk_) for k in range(n))
    vals = [f(i * 0.05) for i in range(201)]
    return sum((vals[i] + vals[i + 1]) / 2 * 0.05 for i in range(200))
# AUC0-10 is linear in the amplitude: least squares over the three doses.
unit = [p4_auc10(1.0, d) for d, _, _ in P4]
pa_ = sum(u * a10 for u, (d, cm, a10) in zip(unit, P4)) / sum(u * u for u in unit)
models["P4_ORAL"] = model("ng/mL", [(pa_, lam_p)], pk_,
    ["spironolactone_progesterone:FDA_Prometrium_2026 (after 5 daily doses of 100 / 200 / 300 mg: Cmax 17.3 / 38.1 / 60.6 ng/mL, AUC0-10 43.3 / 101.2 / 175.7 ng*h/mL; CV about 100-130 %)",
     "spironolactone_progesterone:FDA_Bijuva_2026 (half-life 8.77 / 9.98 h; a different oral progesterone product)"],
    "Illustrative curve only. One absorption and one elimination term: amplitude fitted to the Prometrium label AUC0-10 after 5 daily doses (100 / 200 / 300 mg), absorption rate to Tmax 1.8 h (label 1.5-2.3 h). The label's mean Cmax is about 2.3 x higher than the peak of this curve, as expected when Cmax varies this much between people (each person's own peak is averaged); the half-life is borrowed from another product's label because Prometrium and Utrogestan labels give none. Between-person variability is very large, and lab results depend strongly on the assay (direct immunoassay read about 8 x higher than LC-MS after oral dosing, Levine 2000).",
    {("ss_auc0_10_%gmg" % d): p4_auc10(pa_, d) for d, _, _ in P4} | {("ss_cmax_%gmg" % d): ss_profile([(pa_, lam_p)], pk_, d, 24.0, 5)[0] for d, _, _ in P4} | {"tmax_h": peak([(pa_, lam_p)], pk_, 48)[1]},
    {"cv": 1.2, "cv_source": "spironolactone_progesterone:FDA_Prometrium_2026 (Cmax CV about 100-130 %)", "illustrative": True})

with open(PARAMS) as f: data = json.load(f)
data["models"] = models
data["models_note"] = "Fitted by tools/pk-fit/fit.py from the literature entries named in each model's basis; 'checks' are model outputs for comparison with the literature, recomputed by the engine's validation tests."
with open(PARAMS, "w") as f: json.dump(data, f, ensure_ascii=False, indent=1)
for k, m in models.items():
    print(k, "ka=%.4f" % m["ka_per_h"], [("%.4g" % t["A_per_mg"], "%.3g h" % t["t_half_h"]) for t in m["terms"]])
    print("   ", {kk: (round(v, 3) if isinstance(v, (int, float)) else v) for kk, v in m["checks"].items()})
