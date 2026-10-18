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
