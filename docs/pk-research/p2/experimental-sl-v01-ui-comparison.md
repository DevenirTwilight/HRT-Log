# Experimental sublingual shape comparison — opt-in UI and data boundaries

**Scope:** HRT Log `research/experimental-sl-v01` (Draft PR #1). This is an exploratory engineering feature; it does not establish a new clinical estradiol exposure predictor.

## Why the research chart cannot simply overlay the ordinary chart

The production concentration page shows modeled estradiol in **pg/mL** with existing lab fitting and Monte Carlo bands. Experimental M2 shapes are *dimensionless* and normalized by construction to `h(1 hour)=1`. Any automatic conversion of those M2 relative shapes into personal pg/mL would require unsupported assumptions about the individual effective dose gain, baseline, assay, swallowed fraction and body weight. A clinical concentration comparison would therefore be misleading.

Instead this PR introduces a **separate, default-collapsed section** below the unchanged official E2 chart. It is shown only when verified sublingual E2 history exists; opening the panel explicitly triggers a local research comparison. No back-end, server, network or persistence is used.

## Exact semantics

1. `ConcentrationCalculator` already reconstructs `DoseEvent` from each recorded dose and its historical medication/profile snapshot, rejecting missing historical route, invalid actual dose, unknown tier and other invalid contexts. Only already accepted `r...`-identified historical **E2 sublingual events with times <= now** are passed in memory to the optional view. Future planned `f...` events, oral E2, EV sublingual, patches, all other compounds and lab samples do not enter the research input.
2. The optional model-comparison helper uses a last-**48 hour** display window and up to **30 days of earlier accepted SL E2 history** to include legacy residuals and slow modeled absorption. It explicitly reports the count of doses excluded for being older. It never silently asserts zero concentration outside that truncated history.
3. The **legacy curve** is calculated with `Engine.simulate` for the filtered recorded SL E2 doses, including the legacy tier-dependent mucosal/swallowed calculation. It is divided by the same engine's response at 1 hour following a **synthetic 1mg SL reference at the latest historical dose's tier**. This factor is used only within this research view; the official engine and clinical chart are not renormalized or updated.
4. The **M2 curve** uses the *same* recorded dose timestamps and mg amounts in `ResearchSublingualV01.relativeHistory`, normalized by that candidate's own 1mg-at-1h fast+slow response. **M2 does not model hold time, swallowed dose fraction, or measured bioavailability**. Thus the comparison is about conditional timing/shape, not individual serum concentration, equal bioavailability, or calibrated level accuracy.
5. The researcher manually selects one of **five hypothetical Price Figure 1 baselines: 0, 6, 12, 18, 24 pg/mL**, a study-level *assumption*, not a user baseline. One identifiable candidate (minimum exposed-data pseudoloss, then ID) from that stratum is rendered as a **coherent full curve**. The app names the candidate ID and number of candidate rows in the group; these row counts are not human frequencies or probabilities.
6. Both curves use the **same dimensionless axis** but separate 1h reference factors. The legacy curve retains the chart's original primary color; the experimental M2 line uses the secondary color. The ordinary `ConcChart` remains unchanged when optional `comparisonY=null`. A shared research viewport includes both curves in its scale to avoid clipping.
7. All four supported translations (EN, FR, simplified and traditional Chinese) label the panel explicitly as nonclinical.

## What this implementation deliberately does not do

- No selection of M2 for production concentration evaluation, prediction percentiles, calibration, symptom guidance, or treatment decisions.
- No automatic forecast or q6/q12 clock imputation. Only actual **past** doses enter the research panel.
- No other-route concentration contribution, endogenous background, lab normalization or personalized effective amplitude.
- No database schema or persisted experimental preference, no new export field, no modified production PK parameter or signing/release configuration.
- No inference that 15 candidate rows are independent cohorts or a posterior distribution.
- The same named research baseline group can contain multiple candidates; the single displayed curve is a representative *according to the exposed-data pseudo-loss criterion*, not a most probable patient response.

## Verifications

- `ResearchShapeComparisonV01Test`: valid route/analyte, original timestamps, reference normalization, future/planned omission, lookback count, hypothetical baseline group selection and rejection of unsupported input.
- `ConcentrationCalculatorTest`: actual historical snapshots flow to the preview, planned doses and oral E2 do not, no mutation to the official E2 array.
- `ChartViewportTest`: the optional second series participates in research autoscaling without changing normal single-series chart behavior.
- Existing research kernel/source-to-parameter tests still apply.
- Pending CI and device/emulator checks must complete on the **latest commit**, not be inferred from earlier green commits.

## Explicit uncertainties requiring separate future work

Price Figure 1 digitization versus Table 1 AUC, missing true study baseline, assay-platform comparability, dose-timing uncertainty, slow tail identifiability, and lack of prospectively locked independently timed individual SL PK curves all remain unresolved. This experimental preview is **not** an estimate of an individual concentration or proof of superior precision versus the current engine.
