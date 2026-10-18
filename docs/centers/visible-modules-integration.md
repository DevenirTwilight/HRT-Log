# Centers: visible future modules (2026-10-18)

Baseline 11664eda, actual default claude/new-session-1959qb. #73 and #74 merged; #74 source 11e2ae2 passed all six required checks, merge 07836294. UI-05 #75 and LAB-01 #78 are already present. #72 is superseded by this incremental implementation, never merged wholesale. #57/#58 remain independent drafts; no copied unapproved implementation.

| Files | Actual change |
|---|---|
| UnavailableModuleCard.kt | Reusable Material3 clickable card, status and dismissible scrollable explanation; no business callbacks or storage |
| DrugReferenceCenterScreen.kt | Retain one center, existing source search/medication associations; plans and stock navigation; six separate future modules |
| MedicalRecordsScreen.kt | Preserve UI-05 and #73 real navigation; six future medical modules after real records/actions |
| MedicationsScreen.kt / AppShell.kt | One optional stock callback to existing Stock destination, dismiss center first; plans returns to existing medication screen |
| values*/center_modules.xml | Dedicated four-language resources; no concurrent global strings edits |
| MedicalHomeTest, DrugCenterMatrixTest, DrugCenterNavigationTest, UiHierarchyTest | Replace obsolete visibility assumptions, actual feedback and no importer; retain all old drawer entries and nonempty DB invariance |

Existing 12 drawer destinations remain unchanged and reachable. Center remains accessible from Medication plans, with one component and one controller; no second reference-center screen or database. Existing medical visit doctor/institution text, editing, questions, PDF packs, labs and timeline remain real. Current saved references are user-entered/unverified, not approved official catalogue data. Official French distribution remains blocked by licensing and FT/ST review. RCP and Notice are independently OFF pending #58. P4 stock and MED02 originals remain OFF.

TEST_ONLY: synthetic fixtures and local rendering. Physical device/TalkBack NOT_RUN. Local tests and screenshots pending at this checkpoint. CI pending; no claim of completion/release.

## Verification

PASS: 170 selected tests, zero failures/errors/skips, including 48 cases per center × three contrasts, portrait and 2x landscape, real NotesApp/Room drug and medical invariance, old medical editing/UI-05, all original drawer routes, AppShell back/disguise behavior, translations and PeriodStability. Receipt: visible-modules-matrix-evidence.json. Eight additional render cases capture both centers’ future sections and explanation dialogs; final56 (48 rerun +8 new) PASS, zero failures/errors/skips. Total178 unique selected tests, not226. Source hashes and all48 valid PNGs in visible-modules-final-evidence.json. Physical device, actual TalkBack and device keyboard NOT_RUN.

First one-test failure and its scoped assertion correction are retained. Eight original drug overview captures selected a dismissed dialog, producing black images: excluded from acceptance and replaced by visible-window selection with nonblank pixel checks; UI callback tests were unaffected. Inspected Simplified Chinese medical overview/future, French dark and English light drug overview, Chinese official-catalogue dialog. All renders use empty or synthetic data.

Compatibility: the integration merged reviewed LAB-02 design #80 as58da398e, preserving both requirement sections and handoff records. No production behavior from an unmerged PR is copied. All238 baseline core/data, PK, security, manifest and Gradle files are unchanged by the UI delta. AppShell’s only production delta is the existing Stock navigation callback. No new data source, importer or URI/storage API exists in UnavailableModuleCard.

PRODUCTION_IMPLEMENTED means only visible UI/status feedback and existing backend navigation. Official data/clinical documents, regulatory facts, exact-stock P4 and MED02 storage remain PRODUCTION_BLOCKED at their independent gates. NOT_IMPLEMENTED: those future modules’ actual backend. RELEASED: no, this task does not sign or deliver APKs. Final HEAD and required CI/merge receipt will be recorded in PR82 after checks, without creating a metadata-only CI loop.

### Future replacement interfaces

ANSM approved catalogue replaces drug-center-catalogue; #58 independently replaces drug-center-rcp and drug-center-notice after review. ANSM05/06 replace safety/shortage/recall placeholders with sourced snapshots. MED02 security-approved APIs replace files/originals/link cards; practitioner profiles and organization/export need their own approved implementation. Existing visit pack PDF stays usable independently. Each unavailable card has only title/reason/tag; replace explicitly with an approved real callback when ready.
