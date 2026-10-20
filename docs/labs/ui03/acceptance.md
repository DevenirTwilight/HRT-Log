# LAB-UI-03 integrated acceptance

PR [#89](https://github.com/DevenirTwilight/HRT-Log/pull/89) replaces the actual `Destination.LABS -> LabsScreen`. Approved data PR90 finalffb4c92c passed12 required check instances and was protected-merged as5a7d7c87; this branch normally merges that default. Parent approval/CI proves the parent source, not the final combined source. No signed APK or Release is produced.

## Final integration status

| Evidence | Actual status |
|---|---|
| Final local UI/history/Period/screenshot bundle |PASS85/85,0 failures/errors/skips; nine raw JUnit reports and source hashes retained in integration-final-evidence.json. |
| Native test Kotlin compilation |PASS for final source. |
| Final exact-head six CI/API26/29/35 execution |PENDING until the final integration commit. A compile is not native execution. |
| Physical phone / actual TalkBack |NOT_RUN. |
| Signed APK / Release |NOT_RUN; not part of this task. |

Formal panels, legacy display-only groups, singleton legacy results and metric-filter history all use LabHomeCard. Existing panel/detail/edit/delete/trash/context/trend/save-as-panel callbacks stay authoritative; no old record is automatically regrouped. Drawer, medical center, concentration and timeline share the same destination. Both native regressions are retained: new-home routes/unlocked recreation, and populated batch recreation/real orientation/lock clearing.

Classification now delegates exclusively to reviewed `LabCatalog.categoryFor` across45 stable codes. Unknown aliases such asHGB/BILI remain OTHER. There is no second catalogue. The obsolete zero-caller LabGroupCard declaration is removed; shared LabGroupRow and LAB-02 details remain connected. The merged AppShell keeps both the FAB edit reset and batch-entry tag, plus the approved memory-only draft/token lifecycle.

Final-source presentation explicitly differs from the early source: the chart uses reviewed `unitOptions` for its canonical axis; stable metric/chart test tags are added; the lab-only chart label preserves finite nonzero abs<0.01 with localized scientific Double.toString. Lab row/spoken/detail-bound formatting similarly preserves finite nonzero abs<0.001, including0.0005. Normal formatting remains unchanged above those thresholds, and true zero remains zero. The stored Double does not recover the report's original precision. Shared ConcChart, global Format, numerical chart points/ranges/curves, PK/calibration and persistence structures are unchanged by these UI guards.

Tests touch the actual TG chart and assert3.05g/L→305mg/dL while the raw original card remains3.05g/L; a two-point tiny CBC chart must show4.0E-4 rather than zero. Exact0.0001–0.0005 report bounds and French decimal formatting are checked. The final screenshot fixture covers four locales,320/411dp,1/2× font and light/dark; a second synthetic fixture covers TG/HDL_C/GLU_FASTING/RBC/HB/absolute and percentage counts, including expanded CBC rows. Screenshots are actual Robolectric native-graphics Compose renders, not physical-phone captures or pixel-golden validation.

Expanded catalogue/units, presets, raw-report metadata limitations/custom-code design and draft retention are documented in `docs/lab03/`. Structured methods/custom creation/qualified true-zero storage remain explicitly NOT_IMPLEMENTED there. Medical attachments remain OFF. Room12/business backup11, encrypted formats, inventory and PK parameters stay compatible; no network/signing/version change is made.

## Historical evidence and corrected CI failure

The18-case focused run,171-case old layout/screenshot matrix and32-case identical baseline render belong to source1f49ce0b/receipt6c593a4c, not automatically to final integration. Their original XML, hashes and before/after images remain unchanged. The original two stale singleton-selector failures are retained; replacements assert exact original identity and spoken name/value/unit.

Remote14ed5f91 included12 Vibe Nuage Agent commits, audited and normally fast-forwarded without reverting external work. Android39337651994/job118351067542 ran1778 cases with1 failure/269 skips: the pre-Back lab_add selector clicked an empty-state button at y480–532 below a470px viewport and opened no dialog. [Original HTML](ci-14ed-openDialogClosesBeforeNavigating.html) and [artifact/viewport receipt](ci-dialog-failure-2026-10-20.json) remain. The corrected visible primary and separately scrolled empty-state paths retain all dialog/Back/page/no-Activity-fallback/no-write assertions. Source0097ea58 passed17 local Back tests and all12 required CI instances, recorded in ci-0097ea58-final-checks.json. That green historical source is not the final combined-head gate.

The first integrated source also passed84/84 cases and native Kotlin compilation with96 captures. Its intermediate receipt/reports remain separate because review subsequently added exact0.0005 and tiny-chart formatting coverage; those earlier passes cannot substitute for the final85-case rerun.

## Screenshot comparison

The `screenshots/before/` images are the approved080094ae homepage using the identical synthetic fixture. Initial6c after-images stay under `screenshots/after/` as history. Final integrated images and source/report hashes are retained separately after the final run; do not describe either earlier source as byte-identical to the final implementation.

Reproduce final renders with `LABUI03_SHOTS=integrated-final20261020 ./gradlew :app:testFullDebugUnitTest --tests net.plainnotes.app.LabUi03ScreenshotsTest --tests net.plainnotes.app.LabUi03CatalogScreenshotsTest`, using the repository JDK21/SDK37 setup. The earlier baseline renderer temporarily used exact approved080094ae LabsScreen and restored it byte-for-byte in finally; that old source was never committed, signed or delivered. Final CI builds from the actual new homepage source.

Final retained images are under `screenshots/integrated-final/`; `integration-final-evidence.json` records all96 captures and48 retained original PNGs,85 tests/nine raw XML reports and final source fingerprints. `python3 docs/labs/ui03/verify-integrated-evidence.py` verifies them offline. Independent root-agent inspection covered 简中320dp/2×dark home and Français320dp/2×dark CBC; UI-agent inspection covered English411dp normal all-results, 繁中411dp normal lipids and English320dp/2×dark glucose. No critical value/unit truncation was observed in these frames; offscreen content remains scrollable. This is not complete pixel-golden or physical-device evidence.

| Configuration | Approved baseline | Final integrated source |
|---|---|---|
| English411dp/1×/light |[before](screenshots/before/top_en_411dp_1.0x_light.png) |[final](screenshots/integrated-final/top_en_411dp_1.0x_light.png) |
| 简中320dp/2×/dark |[before](screenshots/before/top_zh-rCN_320dp_2.0x_dark.png) |[final](screenshots/integrated-final/top_zh-rCN_320dp_2.0x_dark.png) |
| 繁中411dp/1×/light |[before](screenshots/before/top_zh-rTW_411dp_1.0x_light.png) |[final](screenshots/integrated-final/top_zh-rTW_411dp_1.0x_light.png) |
| Français320dp/2×/dark history |[before](screenshots/before/history_fr-rFR_320dp_2.0x_dark.png) |[final](screenshots/integrated-final/history_fr-rFR_320dp_2.0x_dark.png) |

New catalogue examples: [繁中 blood lipids](screenshots/integrated-final/catalog_lipids_zh-rTW_411dp_1.0x_light.png), [English large-font glucose](screenshots/integrated-final/catalog_metabolism_en_320dp_2.0x_dark.png), [Français large-font CBC/absolute+percentage](screenshots/integrated-final/catalog_cbc_fr-rFR_320dp_2.0x_dark.png).
