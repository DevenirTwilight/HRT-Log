# Experimental SL M2 v0.1 — CI acceptance record (2026-10-10)

## Exact evaluated source

- Research branch: `research/experimental-sl-v01`
- Fully tested commit: `dfae619500a53742b7bdca3c07c53041210518c0`
- GitHub Actions push run: https://github.com/DevenirTwilight/HRT-Log/actions/runs/38050094015
- At this commit **all three push jobs succeeded**: `jvm`, `android`, `device-tests`.
- This is an engineering validation of **research-only normalized mathematical shapes**, not clinical validation or independent out-of-sample human PK evidence.

## Empirical CI evidence

1. `jvm`: Gradle `:core:domain:test`, `:pk-engine:test`, `:importer:test`, and Python `tools/pk-research` independent research tests passed, including the **frozen P2 protocol guard**.
2. `android`: Android app/core unit tests, `lintFullDebug`, `assembleFullDebug`, `assembleFullRelease`, and debug instrumentation APK assemblies succeeded. This does **not** make the debug-only Activity a release feature.
3. `device-tests`: Android 35 x86_64 emulator. The CI log reports **67 app instrumentation tests executed**, **2 skipped**, **0 failed**, with `BUILD SUCCESSFUL`. The separate core/data set also completed.
4. To rule out a green run that accidentally omitted the new test, the actual downloaded `device-test-results` artifact HTML was inspected. `net.plainnotes.app.debug.ExperimentalSlComparisonAndroidTest.html` reports **1 executed / 0 failed / 0 skipped / 100% success**. Test `standaloneResearchActivityRendersTwoRelativeCurvesFromSyntheticEvents` passed in **1.841 s**. It launches the Debug-only Activity and confirms old/M2 output labels from synthetic, manually entered dose examples.

## Frozen source & release boundary

The branch vs original P2 base difference consists solely of **new files**. Protected existing production `ConcentrationCalculator.kt`, `Engine.kt`, `ConcChart.kt`, `ConcentrationScreen.kt`, `ChartViewport.kt`, fitted production parameters, app resource translations, databases and original test cases remain unmodified.

The experimental Activity and its manifest entry are in `app/src/debug/`; no release Activity/entry is provided. The independent `ResearchHistoricalSlAdapter` is compiled but **unused in official UI or Debug sandbox**; the sandbox reads **only manual synthetic dose examples**, not actual health records.

## PR merged-ref CI issue and updated base

An **earlier** pull-request merged-ref check failed P2 integrity on `AppShell.kt` from the separately advanced target branch, **not changed by this research PR**. On 2026-10-10, the target branch `claude/new-session-1959qb` committed `b6262c3d32c8c4d601b13b9d46f538d171c569a8`, which:
- leaves the original `production-baseline.json` and locked study protocol text intact;
- adds a precise append-only `protocol-deviations.json` for this non-PK `AppShell.kt` UI deviation;
- restricts exception recognition to explicitly recorded, original/new SHA-256 pairs of ordinary non-PK UI files; **`Conc` / `Lab` / `Chart` files, PK engine, research inputs, resources and data remain non-waivable**;
- updates the protocol checker and its tests.

That target-branch *new* JVM job passed at the time of inspection. **Merged-ref CI must be rerun against the updated target branch** and pass before the Draft PR can be declared integration-ready. Do not suppress the guard, change the original P2 baseline, or treat the old merged-ref failure as evidence of an M2 mathematical failure.

## Release / clinical gates remain closed

- Draft PR only; no merge or release APK is authorized by this report.
- M2 effective transit/slow input rates are fitted group-level mathematical parameters, not verified anatomical compartments or personal elimination rates.
- Research curves, AUC and baseline scenario strata are relative exploratory functions, not validated individual serum E2 predictions in pg/mL.
- Additional scientific identification and independently timed individual/held-out data are required before any production model replacement.
