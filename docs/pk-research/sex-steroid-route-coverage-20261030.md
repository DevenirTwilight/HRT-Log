# Sex-steroid route coverage research — 2026-10-30 (initial evidence tranche)

**Status:** RESEARCH_ONLY; not a production model, not clinically validated, no changes to engine, calibration, chart, schema, historical records or APK. This is an evidence-and-design record for extending HRT Log beyond its current E2 curve implementation. Do not confuse availability of a formula with a justified absolute serum prediction.

## Repository baseline / problem

- Repository `docs/BACKLOG.md` identifies `Engine.Curve` as having **no T** and additional delivery routes/implants unsupported. E2 literature engine already exists; this study does not replace its frozen coefficients.
- Existing P2 sublingual evidence expressly says structural identifiability and independent human validation remain inadequate. Reuse its safeguards for every new route.
- Keep **E2 estradiol**, **T testosterone**, estrone, DHT, synthetic progestins and antiandrogen **pharmacodynamics** separate; a pill/antiandrogen does not automatically produce a measurable circulating E2 or T concentration curve.
- Brand-specific formulations cannot share an absorption coefficient merely because their active molecule and route match.

## Primary official French RCP anchors (accessed 2026-10-30)

| Product / route | Verifiable constraint | Limit |
|---|---|---|
| ESTREVA 0.1% gel E2 | RCP 2026-07-06 §5.2: 1.5 mg E2 / 400 cm² single dose average peak ~40 pg/mL; with repeated dosing steady state ~4 days, average 24h post-last application ~40 pg/mL and average day-22 peak ~70 pg/mL; repeat 3 g gel yields ~2× AUC vs 1.5 g gel | Baseline and complete concentration-time observations absent; neither 40 nor 70 is a universal Cmax; cannot uniquely set ka, elimination and systemic gain |
| PROVAMES 1 mg oral E2 | RCP 2023-12-05 §5.2: micronized oral estradiol; concentration declines by 6 h, may rise again due to enterohepatic recycling | No absolute peak/AUC sufficient for a newly validated full curve. **Sublingual use of the tablet is not the authorized oral RCP route** |
| ANDROGEL 50 mg sachet T gel | RCP 2026-09-04 §5.2: percutaneous absorption approx 9–14%, rises within first hour, steady state from day 2; 5 g gel produces mean increment ~2.5 ng/mL | Brand-specific; endogenous T baseline must not be mistaken for dose-derived increment |
| ANDROGEL 16.2 mg/g T gel | RCP 2026-02-04 §5.2: absorption 1–8.5%; 2.5 g mean increment ~2.2 ng/mL, steady state from day 2; after stopping, decline starts ~24h then return to baseline ~72–96h | Distinct from 50 mg sachet product; **do not copy 9–14% absorption** |
| NEBIDO 1000 mg / 4 mL IM T undecanoate | RCP 2026-01-29 §2: 1000 mg ester = ~631.5 mg T equivalent; §5.2 first injection mean Cmax ~38 nmol/L at ~7d, 2nd injection at 6w mean Cmax ~50 nmol/L, steady state after injections 3–5, steady-state mean Cmax ~37 and Cmin ~16 nmol/L for q10w regimen | Includes endogenous baseline and formulation-specific depot kinetics; never scale ester mg as if T mg; not an independently fitted concentration-time data series |

Official links:
- https://base-donnees-publique.medicaments.gouv.fr/medicament/63216075/extrait
- https://m.base-donnees-publique.medicaments.gouv.fr/rcp-65281285-12
- https://m.base-donnees-publique.medicaments.gouv.fr/rcp-64205548-10
- https://base-donnees-publique.medicaments.gouv.fr/medicament/64205548/extrait
- https://base-donnees-publique.medicaments.gouv.fr/medicament/69033757/extrait

Secondary contextual reviews (not independent parameter validation):
- https://pmc.ncbi.nlm.nih.gov/articles/PMC5182226/ (T gel/patch/injection routes)
- https://academic.oup.com/jcem/article/107/3/614/6410585 (SC vs IM T)
- https://academic.oup.com/edrv/article/43/5/824/6447570 (oral TU, implants, gels)
- https://pmc.ncbi.nlm.nih.gov/articles/PMC12180117/ (E2 route-specific sample-time caveats)

## Missing curve research matrix (status is candidate research, never 'implemented')

| Analyte | Route/form | Model family to test | Current research decision |
|---|---|---|---|
| T | topical gel (specific brand) | skin depot -> central, repeat-dose superposition; baseline separate | **Priority 1: RCP numeric anchors available; fit only conditionally** |
| T | IM TU / NEBIDO | slow depot release + conversion -> T, weeks-long repeat superposition | **Priority 1: official Cmax/Cmin/Tmax anchors; must model baseline and long follow-up** |
| T | IM/SC enanthate and cypionate | ester depot absorption + conversion with route-specific parameters | Priority 2: published data exist; no transferable NEBIDO coefficient |
| T | transdermal patch | controlled input while worn + post-removal washout | Priority 2: formulation-specific; distinguish patch wear events |
| T | oral TU | meal-dependent lymphatic uptake, lag and multiple daily pulses | Priority 2: use product-specific meal/food PK data, not ordinary oral Bateman E2 |
| T | nasal gel / pellets | rapid mucosal pulse / slowly degrading implants | Priority 3: source-specific and stop/remove handling required |
| E2 | gel (ESTREVA and other brands) | slow skin-depot input, repeated doses | Existing E2 engine needs cross-check; constrain ESTREVA specifically |
| E2 | transdermal patch | time-bounded input and removal | Existing E2 engine needs RCP cross-check; patch μg/day is **delivery rate**, not content |
| E2 | oral micronized tablet | first-pass formation, optional enterohepatic secondary absorption | Existing E2 engine needs independent checks |
| E2 | sublingual/buccal | dual mucosal + swallowed input, uncertain baseline and tail | Existing P2 research inconclusive; no unvalidated new amplitude/tail |
| E2 | injectable esters EV/EC/EB/EN | ester + vehicle- and route-specific depot kernels | Existing engine needs source-specific validation; do not interchange esters |
| E2 | vaginal / implant / spray | formulation-specific local vs systemic absorption | Priority 3; some preparations mainly local, no automatic systemic curve |
| Other agents | CPA, spironolactone, bicalutamide, GnRH agonists | separate PK analytes and/or indirect E2/T PD effect | **Do not synthesize an E2 or T response from administration alone**; no evidence-backed instantaneous mapping |

## Model-design contract

1. One entry per `(analyte, molecule/ester, formulation/product, route, strength, unit, dose history)`; include an explicit `evidence_level`, `publication`, `model_version`, `input_units`, `output_units`, `baseline_policy`, `population`, `time_grid`, `uncertainty_status`.
2. Math candidate for an absorption-limited input (not a fitted medical prediction): `C(t) = B(t) + S * sum_i dose_i * ∫_0^(t-ti) input_route(u) exp[-ke(t-ti-u)] du`; constrain `input_route>=0`, mass balance and causality; permit multiple depot compartments. `S` is NOT identifiable from a peak alone.
3. Keep population baseline separate. Endogenous T, E2 from T aromatization, suppression and sex-hormone-binding changes require their own independently justified models; **do not infer from this PK convolution**.
4. For depot injectables, ester-to-parent conversion and unit conversion are explicit and snapshotted per historical event.
5. Fit source identity, sampling times, assay, formulation, participants, baseline method and whether observations are single-/multiple-dose. Never combine averages from unrelated populations as a single synthetic subject.
6. Compare independent cohorts by predeclared hold-out; report errors in native concentration units plus time-dependent shape and peak/trough/AUC errors. A normalized shape-only curve must be labeled and must never masquerade as absolute ng/mL or pg/mL.
7. Negative cases: no concentration observations or not identifiable => `record_only` or `illustrative_shape_only`, not silently generate fabricated absolute serum levels.
8. No changes to frozen legacy/M2 results or historical lab/calibration without separate compatibility/version approval.

## Immediate reproducible next implementation tranche

1. Inspect actual `pk-engine` entrypoints and domain medication route enums; construct test fixtures for all supported and missing combinations, proving current coverage before touching implementation.
2. Extract official §5.2 constraints into structured source-identity JSON including page/URL, dose, formulation, timepoint, units, population, baseline and uncertainty.
3. Prototype **T gel (ANDROGEL 50 and 16.2 as distinct sources)** and **T IM TU (NEBIDO)** independently in research-only Python with algebraic mass conservation and synthetic dose histories.
4. Test numerical stability, no pre-dose effect, unit round-trips, once vs repeat dose, abrupt removal/stop, washout, and comparison to the exact published anchors.
5. Only when each route has independent human evidence sufficient for amplitude and timing and compatibility gates pass, seek separate production integration into T selector; otherwise ship no false absolute predictions.

**Result of this tranche:** primary-source PK constraints and a route-by-route evidence backlog are documented. There is **no justified claim that every dosage form has a usable validated absolute serum curve**.

## Source correction 2026-10-30

The initially recorded URL ending `/68097237/extrait` is **ANDROGEL 16.2 mg/g pump**, not ANDROGEL 50 mg sachet. It has been corrected above to `/64205548/extrait` for the 50 mg sachet. Never transfer formulation-specific absorption or exposure constraints between the two products. Verified against ANSM current RCP 2026-09-04.
