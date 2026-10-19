# LAB-UI-03 — actual labs homepage integration

Owner request: 2026-10-19, LAB-03 + LAB-UI-03. Baseline default `080094ae` (Room12 / business backup11). Production PK, database, backup, inventory, permissions and signing are outside this change.

## Audit and file matrix

| File | Responsibility |
|---|---|
| `app/.../ui/LabsScreen.kt` | The existing `Destination.LABS` already calls this Composable. Replace all `LabRow` branches, including metric filters and singleton legacy results, with the same new card. Add batch action, category and search controls. Preserve chart computation and callbacks. |
| `app/.../ui/LabHomeCard.kt` | Shared home visual, three-result preview, expand/collapse, formal/display-only badge, original result detail/edit/delete/context, report-range wording. UI-only code taxonomy until the separate domain catalogue is integrated. |
| `app/src/main/res/{values,values-fr,values-zh,values-b+zh+Hant}/lab_home_strings.xml` | Four-language home-specific copy. |
| `app/src/test/.../LabUi03HomeTest.kt` | All card kinds, filtered history, no-write filters, original row identity, one-sided/unknown ranges. |
| Existing `Lab01GroupDisplayTest`, `Lab02PanelUiTest` | Keep grouping/CRUD/context assertions; replace old requirements that singleton and filtered history must use old visual rows. |
| `AppShell.kt`, `ShellHosts.kt` | Audited, no code replacement necessary: drawer, medical center, timeline and concentration all select the same `Destination.LABS`; existing dialogs remain authoritative. |
| Approval registry + REQUIREMENTS/HANDOFF | Explicit owner scope and honest evidence. Frozen research archive unchanged. |

## Why the old visual persisted

Navigation was already correct. `LabsScreen` deliberately rendered private `LabRow` for every metric-filtered result and every ungrouped singleton. The newer `LabGroupCard` covered only multi-result display groups and formal panels. Opening newer batch/detail dialogs did not replace those homepage branches. This change removes `LabRow` from the homepage and uses `LabHomeCard` for all branches.

## Data boundaries

- `labDraws` remains unchanged. Display grouping never creates `LabPanel`, inferred links or revisions.
- Formal panels still open existing LAB-02 detail. Legacy save-as-panel remains explicit and confirmed by existing host.
- New result action calls existing `onEdit(null)` → `labDialogs.labNew` → existing batch form.
- Result row opens existing detail; expanded original menus call existing edit/delete callbacks; frozen context and rebuild callbacks remain available.
- Filtering/search/expansion are presentation state only. Range labels refer only to entered report bounds; absent/unconvertible ranges remain explicit unknown states.
- Trend points, conversion, range shading, calibration eligibility and PK are unchanged. The catalogue extraction on the independent LAB-03 branch may move existing top-level helpers to a new adapter without changing their scientific meaning.

## Status

Implementation complete on independent draft PR [#89](https://github.com/DevenirTwilight/HRT-Log/pull/89), production source commit `1f49ce0b`. Focused18 UI/context/wiring tests PASS; the first17-test iteration retained2 stale-selector FAIL and their replacement assertions check exact original identity/name/value/unit. The after matrix171/171 PASS (128 four-language layout cases,32 screenshot cases,5 homepage cases,1 resource/wiring case,5 PeriodStability); native test Kotlin compile PASS. Actual native MainActivity navigation execution awaits CI and is not represented by the component screenshots.

64 screenshots captured from the production `LabsScreen` with the same synthetic fixture;16 representative originals retained under `screenshots/after/`, all64 hashes recorded. These cover four languages ×320/411dp ×1/2×font ×light/dark; retained top/history cases show each locale at411dp normal light and320dp large-font dark. Visual inspection covered EN411normalLight, Simplified Chinese320largeDark and French320largeDark history, Traditional Chinese411normalLight and English411normalLight history. No critical value/unit clipping was observed in those inspected frames; this is not a claim of physical-device verification or pixel-golden validation. JUnit XML reports and exact source invariance receipts are retained beside this document.

Identical-fixture baseline renders completed32/32 PASS with64 actual captures,16 originals retained. The baseline source was restored byte-for-byte to the new homepage source before this commit; `baseline-render-source-evidence.json` and `matrix-before-evidence.json` retain source hashes,32-case XML and dimensions for every before/after pair. Exact final-head six CI, native route tests and protected merge remain pending. Physical device and TalkBack `NOT_RUN`. No APK, signing or Release authorized by this task.

The independent LAB-03 branch owns expanded codes/units, batch selection/presets and memory-only batch draft retention. This UI PR intentionally uses the existing catalogue API and UI-only category lookup; integration must replace the lookup with the reviewed `LabCatalog.categoryFor` API without changing the home visual or introducing a second catalogue. The unused old `LabGroupCard` declaration remains in data-owned `LabBatchEntry.kt` until the reviewed UI/data integration can remove it; no production entry calls it from this branch.
