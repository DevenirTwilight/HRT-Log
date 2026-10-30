# ANSM RCP §5.2 model implementation / independent comparison checkpoint — 2026-10-30

**Owner scope:** fill missing E2/testosterone formulation curves and re-evaluate previously simulated formulations against French official data. Every step persisted. **This checkpoint does not claim completion of all formulations.**

## 1. What is actually implemented

### Live Engine: E2 ESTREVA gel

- RCP CIS 63216075: https://base-donnees-publique.medicaments.gouv.fr/medicament/63216075/extrait (RCP revised 2026-07-06).
- `pk-params.json`: added `E2_GEL_ESTREVA` with source metadata; gel product ID 2 now uses it in `Engine.choose`. IDs 1/3 Estrogel/Oestrogel and 4 Divigel unchanged; ID 5 (unknown) keeps `NO_PRODUCT_DATA`.
- Fit to (not independently validated against) labelled **group means**: single 1.5mg ~40 pg/mL peak; once-daily 1.5mg by day22 peak ~70 pg/mL, value at 24h ~40, ~steady state after four days.
- Empirical parameterization per 1mg: `ka=0.277747697/h`, `lambda=0.0400025385/h`, `A=43.0387377 pg/mL/mg`. Under an assumed zero-baseline response the predicted peak/peak/trough are ~39.9/~70.1/~40.0. Apparent model lambda is **not** observed plasma estradiol clearance. Data do not resolve actual baseline, Tmax, central/peripheral distribution or individual variability. `ILLUSTRATIVE` flag is mandatory.
- Tests: existing `EngineTest.gelsPerProduct` corrected, new `FrenchRcpModelTest`; executing tests requires CI, not merely committing test files.

### Research-only: Testosterone esters TE/TU IM

- `OfficialTestosteroneDepotShapes` and dedicated test: two **dimensionless normalized release shapes** referencing ANDROTARDYL 250mg IM TE (250mg ester = 180mg parent testosterone, Tmax 1.5–3d, depot half-life ~4.5d) and NEBIDO 1000mg TU (1000mg ester = 631.5mg parent T, first-dose Tmax ~7d, depot-release half-life 90 ± 40d).
- Uses a first-order release-shape kernel with rates algebraically determined from the reported time to peak and depot-release half-life; normalize a reference dose peak to 1. Repeated doses are an explicit linear **scenario**. **Not a serum ng/mL prediction**; source absolute Cmax values contain unknown endogenous T baseline, so these are not in production Engine or Android UI. Sources:
  - https://base-donnees-publique.medicaments.gouv.fr/medicament/68178899/extrait
  - https://base-donnees-publique.medicaments.gouv.fr/medicament/69033757/extrait

### Research-only: Two distinct T gels

- Added `T_GEL_ANDROGEL_1PCT` and `T_GEL_ANDROGEL_1P62` as **incremental T** `ng/mL` models (NOT total serum T); **not dispatched into the Android application**.
- 1% ANDROGEL 50mg: RCP says mean serum increment +2.5ng/mL after daily application, and reaches near-steady by day2.
- ANDROGEL 16.2mg/g, 2.5g gel (40.5mg active T): RCP mean increment +2.2ng/mL; this formulation differs from the 1% gel. Time-shape assumptions are **not measured**; an empirical absorption `ka=0.5/h` and apparent release-decay `lambda=0.06/h` are chosen for both with amplitude independently fixed to each documented mean once-daily steady increment; CV=1.0 is an unvalidated scenario.
- Product references:
  - https://base-donnees-publique.medicaments.gouv.fr/medicament/64205548/extrait
  - https://base-donnees-publique.medicaments.gouv.fr/medicament/68097237/extrait
- Tests `OfficialTestosteroneGelModelsTest` ensure per-product source identity, units, normalisation to mean **increment**, and declared limitations. Neither T product justifies predicting endogenous T suppression or exact personal blood tests.

## 2. Cross-product comparisons of EXISTING curves

Machine-readable official checkpoints are stored in `docs/pk-research/france-official-rcp-checkpoints.json`. Reproduction script `python3 tools/pk-research/fr_rcp_check.py --json` reports each comparison, preserving the original source and analytic unit. Tests `test_fr_rcp_check.py` check unit conversions, numerical stability and known discrepancies. The values below were independently computed from **existing** fitted coefficients, not from a new fit.

| Existing model / regimen | Official ANSM RCP group mean | Current model (without individual baseline) | Interpretation |
|---|---|---|---|
| OROMONE 2mg E2 daily, steady Cmax | 89±16 pg/mL | 82.6 pg/mL | Within 1 SD, but original model already included Oromone info; NOT independent proof |
| OROMONE 2mg E2 daily, steady Cmin | 35±13.4 pg/mL | 39.5 pg/mL | Within 1 SD, not individual validation |
| OROMONE 2mg E2 daily, steady AUC0–24 | 1486±374 pg·h/mL | 1533 | Within 1 SD |
| DUOVA 1mg oral EV daily, steady Cmax | ~166 pmol/L | ~116 pmol/L | Lower; **different co-formulation with MPA**, no SD in RCP |
| DUOVA 1mg oral EV daily, steady Cmin | ~101 pmol/L | ~54 pmol/L | Lower; same confounding |
| DUOVA 2mg oral EV daily, steady Cmax | 308 pmol/L | ~232 pmol/L | Lower; different co-formulation |
| DUOVA 2mg oral EV daily, steady Cmin | 171 pmol/L | ~108 pmol/L | Lower; different co-formulation |
| UTROGESTAN oral P4 **single** 200mg, 2h | 11.75 ng/mL (pre-dose 0.13) | 11.61 ng/mL | Near reference; assay not specified in RCP |
| UTROGESTAN oral P4 200mg, 6h | 2.00 ng/mL (pre-dose 0.13) | 8.88 ng/mL | ~4.75x the observed increment after baseline subtraction |
| UTROGESTAN oral P4 200mg, 8h | 1.64 ng/mL (pre-dose 0.13) | 7.66 ng/mL | ~5.07x the observed increment after baseline subtraction |

Sources: https://base-donnees-publique.medicaments.gouv.fr/medicament/60071379/extrait ; https://base-donnees-publique.medicaments.gouv.fr/medicament/62617300/extrait ; https://base-donnees-publique.medicaments.gouv.fr/medicament/66620660/extrait ; https://base-donnees-publique.medicaments.gouv.fr/medicament/61673424/extrait .

**Critically:** the existing single absorption/dissipation oral-P4 kernel may be structurally incapable of capturing the reported 1h→2h acceleration and 4h→6h steep decline with later tail. The FDA Prometrium-based model uses a different product and possibly different assay. This is strong motivation to develop a separately sourced P4 formulation model, **not** sufficient to indiscriminately replace the current production model or declare its clinical error. Nonidentical studies, baseline, assay, food and conditioning are material.

## 3. Pending work (remain OPEN)

1. Confirm CI (JVM, science, Android/API26/29/35), inspect failures; no claim of passing before actual checks.
2. Independent ANSM reference checks of P4 time course and EV dose-response, baseline/assay methods; fit new models in isolated research branch and compare held-out studies before a production update.
3. Add proper T molecule, ester, product catalog and route in the medication editor and persisted frozen snapshots; never silently re-interpret past T medications with no profile. Display explicit model limitations in all four languages and test history and backup compatibility.
4. T injection *absolute concentration* needs baseline-subtracted source data or model calibrated against personal labs, with causal no-lookahead guard. Do not directly sum total-T RCP peaks to make personalized serum concentration.
5. Search and fit E2 EC, EB, EN, EU, and T cypionate/propionate/mixed injections, SC, oral, patch, implant routes from more regulators (FDA, EMA, TGA etc.), with product- and route-specific source verification; unsupported is explicit until evidence exists.
6. Show per-analyte T **distinct** from E2, separate y-axis/unit/model details; never merge antiandrogen blood concentration with T suppression; verify baseline and repeated dosing.
7. Audit archive/frozen report replay and build on physical device. No signed APK, public release, private delivery or CI success reported by this checkpoint.

## 4. Contract & compliance

- All model code independent, not copied from third-party tracker. Official facts attributed with stable URLs; no official source text copied wholesale into the app.
- Historical lab snapshots, backup, Room database, on-device encryption, permissions and existing dose records untouched so far; no schema migration.
- `pk-params.json` top-level `status` now truthfully distinguishes production-loaded E2 (including Estreva) and research-only T models. The file `version` is deliberately unchanged until explicit version and historical replay design.
- English, simplified/traditional Chinese, French translation work applies to future product UI: no UI additions or translated text delivered yet.
