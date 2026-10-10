# Experimental SL PK v0.1 — DEBUG-only sandbox (not production UI)

**Status:** Research-only, Draft PR #1. No individual E2 concentration accuracy or clinical validation claim.

## Protocol and production safety

The P2 frozen research protocol hashes the existing `ConcentrationCalculator.kt`, `ConcentrationScreen.kt`, `ConcChart.kt`, `ChartViewport.kt`, app string resources and other production files (see `production-baseline.json`). The first UI implementation attempted to add a research panel to the existing concentration page; GitHub CI correctly rejected modifications to these frozen files.

We **did not weaken, disable or update the protocol hashes**. All those files, and `ConcentrationCalculatorTest.kt`, have been restored byte-for-byte to the branch baseline. A research UI embedded in the production page is therefore **not authorized on this P2-isolated research branch**. It will require a separate reviewed engineering/protocol decision in the future.

## What was implemented instead

A separate **Debug-variant-only** experimental Android Activity is registered exclusively through:
- `app/src/debug/AndroidManifest.xml`
- `app/src/debug/java/net/plainnotes/app/debug/ExperimentalSlComparisonActivity.kt`

Run it after installing the **debug APK**:

```sh
adb shell am start -n net.plainnotes.app/.debug.ExperimentalSlComparisonActivity
```

There is no production navigation entry and no corresponding release manifest activity. The exported component exists **only in Debug builds** and has no route to the personal HRT database or saved records.

This screen:
- Accepts explicit, manually typed **hypothetical** administration events `hour:mg`, e.g. `0:0.5, 6:0.5, 12:0.5, 18:0.5`; reference time defaults to hour 48.
- Builds `DoseEvent` entirely in memory and uses the independent `ResearchShapeComparisonV01` helper for old-vs-experimental models. These are **synthetic sandbox events**, not real or verified patient records.
- Allows switching Price's hypothetical Figure 1 baseline among 0, 6, 12, 18 and 24 pg/mL. This assumption selects a frozen M2 candidate according to exposed-data pseudo-loss, not the user's measured baseline.
- Shows both models on the same **dimensionless shape scale** for the last 48 hours. Each is separately normalized by its own 1mg-at-1h response. These are NOT pg/mL, validated clinical intervals, or individual concentration forecasts.
- Does not read, save, export or transmit any personal medical history; no network access or database writes.
- Displays a persistent research-only warning in the debug screen, including that M2 does not model individual holding time and swallowed fraction.

## The optional future historical adapter

A new, separate `ResearchHistoricalSlAdapter` is provided under `app/src/main/java/net/plainnotes/app/conc/experimental/`, with independent regression tests. It reads historical medication snapshots using the existing public qualification APIs and filters to historic, valid, recorded E2 sublingual events. **It is not wired into any production view or into the debug manual-input sandbox**. It makes it possible to evaluate future safe integration without changing the current frozen `ConcentrationCalculator`. Parity with the official eligibility rules is a separate future gate.

## Shared research kernel and limitations

The helper `ResearchShapeComparisonV01.compare` can compare an explicitly supplied `DoseEvent` scenario, whether already independently qualified historical events or clearly labelled **synthetic debug inputs**. It does not fetch personal records or assume a routine q6h dosing schedule. The legacy branch calls the production `Engine` in isolation for sublingual E2 events and normalizes the output against a one-mg one-hour reference of the latest input's hold-time tier; it does **not** modify production output. The experimental M2 branch uses the same timestamps/mg and normalizes against each candidate's 1mg/1h response. Its shape cannot be interpreted as a blood concentration without unsupported patient-specific gain and background.

The comparator deliberately truncates additional input history older than 30 days before its 48-hour display window, reports omitted counts, and rejects non-E2 SL events (oral E2 and sublingual EV). Debug screen input is limited to 128 artificial events; underlying research helper enforces a separate 10,000-event safety cap.

## Verification and acceptance

- `pk-engine/.../ResearchShapeComparisonV01Test.kt`: six JVM tests on route filtering, reference normalization, future event omission, baseline scenario selection, history truncation and malformed inputs.
- `app/.../ResearchHistoricalSlAdapterTest.kt`: three tests on snapshot history qualification, future/out-of-window rejection and immutable inputs.
- All protected production files remain unmodified in the current diff; the P2 frozen hash guard must pass.
- The Debug-only Activity must compile in `fullDebug` and **must not exist in the merged `fullRelease` manifest**.
- Full Android + simulator CI on the latest commit is the acceptance check; do not infer from earlier successful commits.

## Outstanding scientific questions

The unresolved Price figure/Table 1 AUC discrepancy, group-level measurement and assay biases, actual dose-clock uncertainty, model tail non-identifiability, and lack of prospective individual holdout data remain open. A debug research graph is useful for investigating mathematical behavior, **not** a clinical estimate or authorization for therapy decisions.
