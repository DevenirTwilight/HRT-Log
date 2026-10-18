# Product center thin vertical slices — 2026-10-18

Baseline: ec61d53, actual default claude/new-session-1959qb. PR71 merged. P1–P3 merged; P4 #57 draft/performance blocked; P5 exact-stock UI not implemented. #58 c43ae146 draft/source review; #60 merged visual reference only. No copied document implementation or visual redesign.

| Slice | Files | Behavior / boundary |
|---|---|---|
| A | MedicalRecordsScreen.kt | Overview cards: visits, questions/preparation, visit pack export; existing labs/history and actual past visits remain |
| A | AppShell.kt | Existing Visits/Labs/Timeline destinations, hub-only return origin; independent Follow-up/calendar routes unchanged |
| A | values*/medical_center.xml | Four languages; choose an existing visit to use its real questions/export; no fake export or empty editor |
| A | AppShellBackNavigationTest.kt | Actual shell with synthetic Room database, navigation/return, no records created |
| B, after A | new DrugReferenceCenterScreen.kt, AppShell.kt, distinct resources/tests | Existing reference controller/search/user bindings; France scope; missing/unverified/not implemented distinct |
| Future | PR58 document panel | Only after its independent source/navigation review; no guessed RCP/Notice URL |
| Future | P4/P5 | Exact-stock writes and uncertainty UI gated independently |
| Future | MED02 | Encrypted attachments, original preview, doctor/institution CRUD OFF |

A leaves the existing Medical Records navigation label and independent Follow-up unchanged. Questions and PDF export belong to a visit: hub cards first open the real visit list (including empty-state add appointment), then existing detail actions. No new route writes or duplicate store. Back from detail returns to list, then hub; direct drawer/calendar follow-up keeps original behavior. Existing past visit correction remains unchanged. Original attachments are not equivalent to user-exported PDF packs.

Room, business backup schema, PNBAK1 cryptography, PK, lock and disguise implementation, manifests, signing and version remain unchanged. Existing tests/assertions retained. Required CI and PeriodStabilityTest remain gates. Device/TalkBack/manual visual evidence: NOT_RUN until executed; no claim of a clinical or file-library implementation.

## Parallel-development integration boundary

Remote branch `feature/ui05-visual-polish-20261018` at 041c1ea (no open PR at this check) changes AppShell and MedicalRecordsScreen visuals. Slice A changes only additional callback/cards and origin navigation; it does not copy, overwrite or merge that branch. Existing Claude visual reference #60 is REFERENCE_ONLY. Review/reconcile these two exact files when either branch is integrated; retain all visual and functional tests from both. Slice B is developed in a separate worktree with new files/resources only until A merges, then applies its minimal navigation hook on that merged baseline. No shared working tree or shared strings.xml edits.

Actual current baseline database is Room11 (not the historical Room10 task reference); slice A changes neither Room version nor business backup schema. Required checks read from active ruleset 25120328: jvm, android, device-tests, attachment-poc-compatibility (26), attachment-poc-compatibility (29), science-change-gate. API35 behavior is included in device-tests; actual completion is reported separately from local Robolectric.

First expanded validation: FAIL at test compilation because the new preparation test was inserted before two existing disguise-mode test methods and therefore duplicated. Removed only the duplicate new method; no old assertion changed. Re-running the full selected set and matrix. Large-font hub cards are separate lazy items for scrolling accessibility.

## Local validation — PASS

97 tests, zero failures/errors/skips, including PeriodStabilityTest, existing MedicalHome/MedicalRecords UI, actual AppShell preparation/export cancellation and origin navigation. 32 locale/size/font/theme cases each exercise all 3 contrasts. Eight native-graphics Robolectric component renders (4 locales, 320dp, 2x, high contrast, light/dark) in medical-screenshots; they are not device screenshots. Visually inspected English dark and Simplified Chinese light. Source hashes and class counts in medical-test-evidence.json. Physical device/TalkBack NOT_RUN. CI PENDING; no release or clinical/attachment admission implied.
