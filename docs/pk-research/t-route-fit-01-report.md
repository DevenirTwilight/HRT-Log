# T_RCP_ROUTE_FIT_01 — ANDROGEL / ANDROTARDYL / NEBIDO

**Date:** 2026-10-30. **Branch:** `research/sex-steroid-route-coverage-20261030`. **State:** CONDITIONAL RESEARCH ONLY; **NO LIVE MODEL OR APK CHANGES**.

## Executable and reproduction

- `tools/pk-research/t_route_calibration_01.py` — Python standard library, explicit positive kernels, nonnegative constant-baseline-and-gain least squares, structured residuals, and tagged curve export.
- Run: `python3 tools/pk-research/t_route_calibration_01.py /tmp/hrt-t-route-fit-01`. It produces `results.json`, `illustrative_curves.csv`; labels are `ILLUSTRATIVE`, never personal concentration predictions.
- The independently assembled local numerical prototype passed **9 internal math/route assertions**. The exact committed script has **10 internal checks** but its execution in GitHub CI/device is **NOT_RUN** as of this report. Do not report repository CI success without running it.
- No personal medication histories, drug doses, health data, user-specific laboratory values, or clinical fitting are included.

## Verified primary source observations (ANSM)

| Product | Route / study constraint | Interpretive restriction |
|---|---|---|
| ANDROGEL 50 mg gel, 5 g sachet | 9–14% absorbed; rises within ~1h, steady from day 2; mean serum T increment ~2.5 ng/mL; return to baseline 72–96h after stopping | The summary does not specify full time points, individual baseline, 24h AUC or concentration assay lower bound |
| ANDROTARDYL testosterone enanthate, 250 mg IM | 250 mg ester is ~180 mg testosterone; depot release half-life ~4.5d; Cmax ~20 ng/mL at 1.5–3d in young men; levels >=2 ng/mL through 20d and >=1 ng/mL through 26d | 20d and 26d are **lower bounds**, not exact observations; 4.5d is depot release, not systemic IV T elimination |
| NEBIDO testosterone undecanoate, 1000 mg IM | 1000 mg ester is ~631.5 mg T; first Cmax ~38 nmol/L at 7d; second injection at 6w with Cmax ~50 nmol/L; q10w maintenance with steady-state Cmax ~37 / Cmin ~16 nmol/L; terminal depot release t1/2 90 ± 40d | These are different treatment stages and mean statistics, **not** four points on an individual first-dose curve; IV plasma half-life ~1h concerns parent T, not ester depot |

Primary sources (ANSM current RCP §5.2):
- ANDROGEL 50 mg (correct product, not the 16.2 mg/g pump): https://base-donnees-publique.medicaments.gouv.fr/medicament/64205548/extrait
- ANDROTARDYL: https://base-donnees-publique.medicaments.gouv.fr/medicament/68178899/extrait
- NEBIDO: https://base-donnees-publique.medicaments.gouv.fr/medicament/69033757/extrait

## Numerical findings (standard-library two-input / depot prototypes)

### ANDROTARDYL

A naive one-compartment first-order depot input with `ka=ln(2)/4.5 per day` and **assumed** parent T elimination t1/2 = 1 h peaks at **~0.28 days**, inconsistent with the 1.5–3 day reported window. Do **not** deploy a generic Bateman curve and call it an ANDROTARDYL fit.

A heuristic two-input candidate (`60% Erlang-2 input with k=.5/d + 40% simple 4.5d-release input`, parent plasma elimination t1/2 1 h assumed) peaks **~1.85 days**; peak is scaled to the aggregate 20 ng/mL, **not an independent validation**. If the serum baseline is *assumed* to be:
- B=0 ng/mL: predicted day20 ~0.383, day26 ~0.146;
- B=1 ng/mL: predicted day20 ~1.364, day26 ~1.139;
- B=2 ng/mL: predicted day20 ~2.345, day26 ~2.131.

For this specific two-input shape, lower bounds at day20 and day26 together imply **assumed baseline >=1.6485 ng/mL**. This is a property of the arbitrary chosen shape, **NOT an estimate of the baseline of participants**; alternative slower tails and endogenous feedback can change it.

### ANDROGEL 50 mg

Three distinct skin input rates (1.0, 1.5, 2.5 per day) were each rescaled to a **hypothetical** steady-state 24h average increment of 2.5 ng/mL. The ANSM document reports a *mean increase*; interpreting this as exactly the AUC-normalized 24h average is a **convention**, not a sourced 24h PK curve.

Using daily applications on days 0–10, at day 13 (72h after last day10 application) the three candidate increments are approximately **0.2096 / 0.0590 / 0.0044 ng/mL above baseline**. This ~47-fold spread despite the identical normalization shows missing shape identifiability. The RCP statement “return to baseline 72–96h” provides no numeric assay threshold to select one family member. The 9–14% absorption range cannot identify distribution volume and clearance.

### NEBIDO

Prototype stress test: positive mixture of fast Erlang-2 release and slow first-order component with fixed 90d terminal release half-life, convolved with **assumed** parent T elimination t1/2 1h. Simulated injection days **0, 42, 112, 182, 252, 322, 392**. A single fixed baseline and fixed gain across all stages are explicitly **restrictive** assumptions.

Grid: fast input rates **0.10–0.22/d** (7 candidates) × strictly positive slow-component weights **0.05–0.90** (18 candidates) = **126 candidate shapes**. For each, nonnegative baseline and gain are least-squares fitted to official four aggregate markers [38, 50, 37, 16] nmol/L, with separate heuristic 7d Tmax penalty.

The lowest-score candidate has: fast rate **0.10/d**, slow weight **0.05**, first peak **10d** rather than target 7d; baseline ~**14.638 nmol/L**, gain ~**12,374 arbitrary per full dose**; predicted [**40.90, 43.17, 41.45, 15.49**] nmol/L; signed residuals [**+2.90, −6.83, +4.45, −0.51**] nmol/L; marker-only SSE ~**75.09 (nmol/L)^2**. This is **not a satisfactory identified fit**. In particular the +/− values should not be reported as clinical accuracy errors or confidence intervals.

The previous unconstrained search allowed a **zero** slow component, which mathematically discarded the RCP 90d terminal release. That zero-weight candidate is excluded from the final 126-shape research grid. A 90d slow component alone does not establish its weight, its form, or exact human kinetics.

## Scientific gates

1. Get independent human concentration-time data per formulation, including baseline, actual injection timing, assay, population and uncertainty, ideally digitized from an explicitly sourced published figure where individual records are unavailable.
2. Do not equate an aggregate Cmax with a specific person's full serum profile, or treat separate stages as identical endpoints; endogenous feedback may change baseline.
3. Do not scale different ester units as parent T milligrams; do not copy ANDROGEL 50 constraints to ANDROGEL 16.2.
4. Validate shape and amplitude separately. A profile with fitted total Cmax but incorrect Tmax/tail is **NOT READY**. Retain numerical residuals and failed constraints without changing targets.
5. Keep prototype curves distinct from the app's production E2 engine; do not modify archived P2/P1, calibration, medical records, Room, UI, backup or signing.

**Outcome:** Three product-specific research prototypes and their limitations are documented and reproducible. None is established as a clinically valid absolute T model for HRT Log yet.
