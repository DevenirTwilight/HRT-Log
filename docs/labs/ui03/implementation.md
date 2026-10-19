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

Implementation in progress. Local compile/UI tests running. CI/native navigation/screenshots not yet verified. Physical device and TalkBack `NOT_RUN`. No APK, signing or Release authorized by this task.
