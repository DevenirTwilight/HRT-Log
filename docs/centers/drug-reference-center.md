# Drug reference center — slice B

Current baseline: ff2a4e9, after medical slice A #73 passed all six required checks and merged. B then added its single AppShell browse callback. Earlier isolated work used ec61d53; those results remain separately identified below. No competing A/B shell edits.

The center is a projection of existing NotesState.medications and MedicationReferenceController. Query opens its current offline saved-user-source browser (no target, no implicit binding). Selecting an existing medication opens exactly the same controller with its ID; its frozen scope/revision/UUID confirmation, cancel, unlink and error recovery remain unchanged. No medication save, container resize, PK or historical rewrite is introduced. Empty state keeps manual medication creation in Medication plans.

France/ANSM/BDPM is explicitly a reference region, not a Chinese catalogue. No official dataset is packaged: licence-version inconsistency and FT/ST interpretation remain unresolved. Entered source identity/version/date are unverified snapshots, absent fields remain unavailable; equal names/ingredients do not establish equivalence. Packaging reference is not usable inventory.

RCP and Notice have distinct read-only status sections; neither is a fabricated navigation link. #58 c43ae146 remains independent and source/navigation gated. Safety and supply/shortage/recall/regulatory sections say NOT_IMPLEMENTED with actual availability UNKNOWN, never no-recall/normal-supply/safe. No clinical summary, medical advice or live network operation.

No database/backup/PK/network/security/signing/version changes. Attachments remain OFF. P4/P5 exact-stock admission remains independent. Official document API integration, approved catalogue distribution, regulatory snapshots and packaging metadata are future boundaries, not implemented actions.

Local integration verified: 133 tests, zero failures/errors/skips. Real NotesApp/Room/reference controller with nonempty actual records, ledger and profile; saved-source offline query and cancellation/back leave those rows unchanged. Existing medical home/records, shell navigation, reference controller/history and PeriodStability regressions included. See drug-integration-test-evidence.json. Six required CI checks PENDING. Physical device, TalkBack and keyboard-on-device: NOT_RUN.

First matrix run: FAIL (32 cases, 2 English/320dp/2x failures). Actual click callbacks stayed zero although semantic nodes existed: the dynamic header overlaid scrolling content. Corrected the new dialog to give its list a viewport padded by Scaffold insets, rather than only list content padding. Retain actual touch and count assertions; no semantic-action substitute. Full matrix re-run pending.

Parallel PR72 appeared after initial scan and before PR73 publication. It combines both centers and clickable unavailable modules; latest user task calls for separate slices and real functional entries. No PR72 source was copied or overwritten; route preference was asked asynchronously while isolated verification continues. PR73 medical A at 3fd5b2f has local97 PASS; production hook for this B remains absent until A merges and integration route is settled.

Corrected independent display run: PASS32, zero failures/skips; three contrasts per locale/size/font/theme case, actual click counters retained. Eight large-font component renders in drug-screenshots. This proves isolated UI behavior, NOT production navigation or persistence integration.

## File-level connection plan

- `DrugReferenceCenterScreen.kt`: center/dialog, real existing-medication callbacks, read-only unavailable sections; standalone matrix verified.
- `MedicationsScreen.kt`: optional browse callback enables one center button and hosts the center, retaining original add/edit/archive/reference actions. Full Kotlin compilation PASS. AppShell now supplies the browse callback after A merged; the real center entry is enabled.
- `AppShell.kt`, after A merges: supply `onBrowseReference={openReference(null)}` to the existing medication screen. This is the only planned shared-shell hook; existing per-medication `openReference(it)` remains identical.
- `DrugCenterNavigationTest.kt`, after that hook: real NotesApp/Room and existing reference controller; nonempty actual-record/ledger/profile fixtures, offline saved-source search, cancel/back, frozen rows unchanged.
- `DrugCenterMatrixTest.kt`: 32 real Compose renders each 3 contrasts, actual callback counts, unavailable sections have no click action, long synthetic names/units preserve text. Physical TalkBack and keyboard testing NOT_RUN.

Future #58 integration reuses the same MedicationReferenceDialog product-detail path after its independent approval; no copied RCP/Notice text or invented URL. P4/P5 exact-stock conversion and MED02 originals remain separate gates. This center provides no packaging-capacity conversion, inventory evidence or safety inference.

Final screenshot review found that long explanatory text placed the real search action too far down at 2x. Reordered only this new center: concise unverified/unavailable scope before search and medication actions, detailed region/identity/status explanations afterwards. No information removed and no callback/data changes. Re-running display and actual navigation verification on this final layout; prior133 source hashes remain identified separately.

Final layout verification: PASS38, zero failures/errors/skips (32 display cases, actual NotesApp/Room navigation/invariance, 5 PeriodStability tests). Prior133 integration coverage remains at dd6c728 and retains its original screenshots; final renders are separately in drug-screenshots-final, with current source hashes in drug-final-layout-evidence.json. Search is now visible near the top at 320dp/2x; inspected English dark final render. Required CI pending at this source checkpoint; GitHub PR74 holds the eventual head/check/merge receipt, avoiding a CI-only metadata commit loop. No device or release claim.
