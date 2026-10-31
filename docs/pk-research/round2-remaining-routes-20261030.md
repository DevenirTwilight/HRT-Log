# ROUND2 — Remaining E2/T route gap and bounded curve study (2026-10-30)

**State: RESEARCH ONLY. No new user-facing medication concentration curves in HRT Log.** This report follows the previous T-01 and T-02 studies in the same Draft PR #173. Research deliverables are automatically checked into GitHub (not only shared in chat).

## What remains without an established production curve

| Analyte, route and example | Production or research coverage | This round's evidence/action | Next gate |
|---|---|---|---|
| E2 oral, sublingual, transdermal gel/patch, injection (existing supported esters) | Existing E2 literature engine per `docs/BACKLOG.md`, independent external clinical validation incomplete | No modifications | Check exact molecule, ester, carrier, route, current engine parameter source and historical freezing separately |
| **T SC testosterone enanthate (XYOSTED)** | No production T engine. **New synthetic summary envelope** | DailyMed Week12, N137 mixed titrated doses: Cavg553, Cmax790, Cmin436 ng/dL; median Tmax11.9h; steady by week6 | Individual or proper group mean numeric time course; tested baseline and dose-specific response before production |
| **T SC testosterone cypionate** | No production T engine. **Human cross-sectional time samples catalogued** | 11 transgender men on weekly SC cypionate: 497 ng/dL pre-dose, 656 at 6h, 621 at day5, 477 at day7; pooled 7 intermediate points 627±206 ng/dL | Preserve original source, uncertainty and sampling mapping; fit only to cohort with proper timepoint SD and holdout; not transfer XYOSTED curve |
| **T transdermal patch (ANDRODERM)** | No production T engine; **withdrawal component only** | Historic US label: T peak median ~8h, patch nominal delivery 24h, apparent decline t½ ~70min after removal. Product discontinued; may still have historical recording value | Need actual 0–24h numeric series for on-phase and endogenous baseline; removal component alone is insufficient |
| **T oral undecanoate (JATENZO)** | No production T engine. Numeric anchors **only**, not a curve | Mixed-dose final visit Cavg403, Cmax1008 ng/dL (NaF-EDTA plasma), 15g vs30g breakfast fat ~25% less exposure | Collect formulation-specific numerical 24h series, meal times, dose histories, plasma/serum conversion protocol |
| **T intranasal gel (NATESTO)** | No production T engine. **Decay range only** | DailyMed ~40min Tmax, half-life 10–100min, 11mg TID day90 average 421±116 ng/dL | Collect time-resolved pulse amplitude, baseline and overlap evidence; wide half-life range alone not predictive |
| **T pellet implant (TESTOPEL)** | No production T engine. **Release/observation constraints only** | DailyMed 75mg/pellet, approximately 1/3, 1/4, 1/6 material absorbed in months 1–3; observational 702 insertions/380 men, levels maintained ~4–6months | Pellet dissolution != serum PK; pellet count/history, time-varying endogenous baseline and individual blood draws required |
| **E2 transdermal spray (LENZETTO)** | No verified distinct spray curve; **new synthetic summary envelopes** | UK SmPC day14 (see next table), steady day7–8, 1–3 sprays. Do not call this a French-market RCP until French registration verified | Distinct PK for spray, independent baseline and application effects; no gel parameter copy |
| **E2 implant (compounded pellets)** | No verified production curve. Human evidence catalogued | Australian observational study: 38 patients / 88 implants; median insertion gap 270d; threshold ≤250pmol/L ~4 months after initial 100mg vs ~13 months after subsequent implant | Initial and repeat implants clearly differ; no single fixed-dose terminal exponential can be inferred from threshold alone |
| T IM cypionate, T buccal, T topical solution and other brands | NO production T model; not yet quantified here | Keep `SOURCE_REQUIRED`; source search remains | Drug/formulation-specific C(t) |
| E2 vaginal systemic formulations and additional injectable ester/vehicle products (EC/EB/EN/EV etc.) | E2 partial / route-ester-specific evidence incomplete | Keep per-product unsupported unless validated | Identify formulation and whether systemic absorption is meaningful; local vaginal products should not silently create universal serum E2 |
| CPA, spironolactone, bicalutamide, GnRH agonists, progesterone etc. | Separate drug PK/PD, not a direct E2/T exogenous PK input | **Do not auto-convert administration into a T or E2 curve** | Separate empirically justified endocrine turnover/PD models |

### Source-linked quantitative registry
See [`round2-source-registry-20261030.json`](t-routes/round2-source-registry-20261030.json) for eight non-interchangeable product/study records and direct source URLs; this is machine-readable and includes units, analyte, design, dose, sample size, statistic type and assumptions.

### E2 spray — important dose-scaling failure

UK LENZETTO product SmPC 5.2 Table2 day14, **unadjusted for baseline, geometric means** (postmenopausal women, N=24/23/24):

| Sprays/day | Cmax pg/mL | Cmin pg/mL | Cavg pg/mL |
|---:|---:|---:|---:|
| 1 | 31.2 | 10.3 | 17.8 |
| 2 | 46.1 | 16.4 | 28.2 |
| 3 | 48.4 | 18.9 | 29.5 |

- 1→2 increase in reported Cavg = **+10.4 pg/mL**; 2→3 = **+1.3 pg/mL**.
- Linear extrapolation of the first two reported Cavg points to 3 sprays gives **38.6 pg/mL**, whereas reported is **29.5 pg/mL** (difference **9.1 pg/mL**). Distinct cohorts, baseline differences and geometric-mean statistic must be kept; cannot assert the existence of a specific saturable absorption mechanism.
- The UK SmPC describes a peak around **02:00–06:00 after morning application** and ~7–8d steady state. Under an **arbitrary 08:00 application assumption**, peaks at 18 and 22 hours after dose are both consistent with the clock description. This does not determine the full curve.

## New executable research construction

[`tools/pk-research/route_round2_steady_envelopes.py`](../../tools/pk-research/route_round2_steady_envelopes.py) defines a **normalized beta-shaped steady-period envelope**, not a pharmacokinetic absorption kernel:

```text
shape(t) = (t/t_peak)^p * ((period-t)/(period-t_peak))^q
q = p * (period/t_peak - 1)
C_synthetic(t) = Cmin + (Cmax-Cmin)*shape(t)
```

The expression is defined for 0<t<period; zeros at endpoints. An analytic beta-function integral allows p to be solved to match the numerical ratio `(Cavg-Cmin)/(Cmax-Cmin)` exactly. Multiple peak phases give **different time-series shapes with identical reported Cmin/Cavg/Cmax**. The construction is explicitly a counterexample to overclaiming identifiability, **not** real observed human serum evolution.

For UK LENZETTO it generates six 24h scenarios (1/2/3 sprays and assumed peak at 18/22h). For XYOSTED it generates a 168h summary envelope using *arithmetic pooled* 553/790/436 ng/dL and sets peak time to reported median individual Tmax 11.9h **only as a synthetic convention**, not as an established peak of the pooled mean curve.

File outputs **already checked in**:
- [`round2_illustrative.csv`](t-routes/round2_illustrative.csv), **751 rows**, source units and `SYNTHETIC_STEADY_ENVELOPE` flags.
- [`round2_research_results.json`](t-routes/round2_research_results.json), parameters and numerical status.
- [E2 spray illustrative SVG](t-routes/round2_e2_spray_envelope.svg) and [T SC TE illustrative SVG](t-routes/round2_t_sc_envelope.svg).
- [Seven independent tests](../../tools/pk-research/test_route_round2_steady_envelopes.py), analogous local execution: **7/7 PASS**; precise GitHub head CI separately PENDING/NOT VERIFIED.

## Negative evidence and safety

- A summary-matched hypothetical periodic envelope **cannot generate initial-dose, accumulated, washout, missed-dose or dose-adjustment curves**, and must **never** be copied to production HRT Log `Engine.Curve`.
- Quantitative decay-only stress: ANDRODERM 70min post-removal half-life yields normalized drug-derived excess remaining after 3h = **~0.1682** and after 24h = **~6.4 × 10⁻⁷**, conditional on exponential excess decay. This does **not** predict measured total T without endogenous baseline.
- NATESTO half-life range 10–100min: normalized excess remaining 80min after peak spans **0.0039 to 0.5743**, ~147-fold, even before uncertainty in input timing and amplitude.
- Implant threshold reports provide no single transferable Tmax, Cmax, absolute dose conversion or concentration curve.
- Other medications suppressing or modifying hormone secretion cannot be assigned direct E2/T absorption inputs without a separate validated PD model.

## Exact sources and cohort identity

- UK [Lenzetto SmPC §5.2](https://www.medicines.org.uk/emc/product/11175/smpc).
- US [Xyosted DailyMed §12.3](https://dailymed.nlm.nih.gov/dailymed/drugInfo.cfm?setid=8a3d204c-be26-49e0-8599-0ac12a272e81).
- US [Jatenzo DailyMed §12.3](https://dailymed.nlm.nih.gov/dailymed/drugInfo.cfm?setid=ed7b5d41-7475-4c10-99b9-b62b3434ae60).
- US [Natesto DailyMed §12.3](https://dailymed.awsprod.nlm.nih.gov/dailymed/fda/fdaDrugXsl.cfm?setid=dea6bed1-eaca-11e3-ac10-0800200c9a66&type=display).
- US [Androderm FDA historic label §12.3](https://www.accessdata.fda.gov/drugsatfda_docs/label/2020/020489s036lbl.pdf) — historic product, market discontinued.
- [SC cypionate transgender cohort](https://pmc.ncbi.nlm.nih.gov/articles/PMC5686655/), N11, published 2017.
- [Testopel DailyMed](https://dailymed.nlm.nih.gov/dailymed/drugInfo.cfm?setid=03b9c0b1-5884-11e4-8ed6-0800200c9a66) and [observational 702 insertions](https://pubmed.ncbi.nlm.nih.gov/22240203/).
- [E2 implants gender-affirming observational cohort](https://pubmed.ncbi.nlm.nih.gov/37845025/).

**Follow-up**: prioritize numeric **time-series** data extraction for SC T cypionate and XYOSTED and first/repeat E2 implants, preserving actual dose histories and assay details, then compare route-specific models across independent cohorts. Never claim all dosage forms are implemented merely because an SVG envelope exists.
