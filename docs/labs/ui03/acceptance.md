# LAB-UI-03 acceptance evidence

Independent PR: https://github.com/DevenirTwilight/HRT-Log/pull/89. Default baseline080094ae; production homepage source1f49ce0b. Room12/business backup11 retained. This document is branch acceptance, not a signed APK or release receipt.

| Evidence | Actual result |
|---|---|
| Initial focused run |17 cases,15PASS/2FAIL. Original singleton text selectors did not match newly merged accessible name/value/unit. Failure retained. |
| Corrected focused run |18/18PASS; exact original IDs/spoken values asserted, grouping/CRUD/context retained. Native Kotlin compilePASS. |
| After acceptance matrix |171/171PASS:128 four-language/screen/font/theme layout cases,32 actual homepage screenshot cases,5 homepage operations,1 resource/wiring contract,5 PeriodStability. Native Kotlin compilePASS. |
| Baseline comparison |32/32PASS, same fixture/configurations;64 before+64 after captures,16 representative originals perphase retained, all capture SHA256/dimensions recorded. |
| Source compatibility |12 production data/PK/manifest files byte-identical to baseline; exact trend construction block unchanged. Source-only evidence is not a replacement for final CI. |
| Final exact-head six CI/native execution |PENDING. Inspect PR rollup, do not infer from compile. |
| Physical phone, actual TalkBack and portrait↔landscape |NOT_RUN locally. Native test uses activity recreation; it does not establish landscape layout evidence. |
| APK/signing/Release |NOT_RUN and not authorized by this task. Existing build33 is unchanged. |

The actual `Destination.LABS -> LabsScreen` is replaced; drawer, medical center, concentration and timeline select that same destination. The new native MainActivity regression exercises drawer→new home→existing batch form(cancel/default estimate off), medical center→same home, concentration→same home and unlocked-session recreation; execution awaits final CI. Existing route/security methods remain intact.

Formal panels, display-only legacy groups, singleton legacy results and selected-metric history all use `LabHomeCard`. The existing LAB-02 panel detail, original per-result detail/edit/delete, explicit save-as-panel confirmation, frozen context and trend calculation remain authoritative. Filtering and expansion invoke no persistence callbacks. No old record is automatically regrouped.

Expanded catalogue/units, presets, raw-report metadata/custom-code design and batch draft retention belong to the independent LAB-03 workflow. The temporary UI category lookup must delegate to reviewed `LabCatalog.categoryFor` after integration. Medical attachments remain OFF.

## Actual screenshot examples

These are actual Robolectric native-graphics Compose renders of the production homepage with synthetic records, not designs or physical-phone captures. Full representative sets are in `screenshots/before/` and `screenshots/after/`.

| Configuration | Before | After |
|---|---|---|
| English411dp/1×/light |[before](screenshots/before/top_en_411dp_1.0x_light.png) |[after](screenshots/after/top_en_411dp_1.0x_light.png) |
| 简中320dp/2×/dark |[before](screenshots/before/top_zh-rCN_320dp_2.0x_dark.png) |[after](screenshots/after/top_zh-rCN_320dp_2.0x_dark.png) |
| 繁中411dp/1×/light |[before](screenshots/before/top_zh-rTW_411dp_1.0x_light.png) |[after](screenshots/after/top_zh-rTW_411dp_1.0x_light.png) |
| Français320dp/2×/dark history |[before](screenshots/before/history_fr-rFR_320dp_2.0x_dark.png) |[after](screenshots/after/history_fr-rFR_320dp_2.0x_dark.png) |

Reproduce new captures with `LABUI03_SHOTS=after ./gradlew :app:testFullDebugUnitTest --tests net.plainnotes.app.LabUi03ScreenshotsTest` using the repository's documented JDK21/SDK37 setup. Baseline comparison used exact approved080094ae `LabsScreen.kt` temporarily, restored in `finally`; it was never committed, signed or delivered. Local build outputs after that baseline run represent the comparison only and must not be reused as a delivery APK. Final CI builds from a clean checkout of the new homepage source.
