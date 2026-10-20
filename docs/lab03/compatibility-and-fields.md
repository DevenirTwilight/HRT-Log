# LAB-03 compatibility and original report fields — 2026-10-19

Status: IMPLEMENTATION_VERIFIED_AT_403ae960; UI_INTEGRATION_PENDING. Base 080094ae5adcd8fe65cb0bb1638ef71e0ebc1ec8; independent of draft ANSM P4 #57 and UI #89. No Room or backup version is reserved. Runtime Room12 and PNBAK1 business Schema11 remain distinct and unchanged.

## Existing persistence contract

LabValueEntity already stores an analyte code, original numeric value/unit, sampled UTC instant and zone, laboratory, optional lower/upper reference bounds and their unit, notes and optional formal panel identity. New predefined codes use this same repository and transaction, revision, trash and backup paths. Display groups never create formal panels automatically. Secondary converted units do not create additional results. Reference ranges describe the entered laboratory range, not a diagnosis or a selection inferred from administrative sex.

Preset expansion requires no schema migration: the repository accepts explicit codes and units and the analyte dictionary insert is IGNORE, preserving existing entries. Unknown imported codes and units retain their raw identity. Conversion fails without a known analyte/unit pair; no unknown value becomes zero. Existing fourteen application conversion factor pairs retain their exact Double results. New display conversions use the bounded decimal catalogue; no PK parameters or concentration eligibility rules change.

## Fields available now

| Report information | Storage and limitation |
| --- | --- |
| Original numeric result and unit | Existing value/unit, unchanged on save and backup |
| Single-sided or two-sided reference range | Existing nullable bounds and reference_unit; no fabricated missing bound |
| Sampling time, zone, laboratory | Existing fields; note-only edit preserves original zone |
| Assay method, instrument, report date, original name, population definition, significant digits, rounding note | Existing notes, entered by user; not parsed into clinical or calibration assumptions |
| P4 assay classification | Existing unknown/IA/MS behavior preserved; unknown does not become MS |
| eGFR | Reported value only; note asks for reported formula/version; no calculation or assumed formula |
| E2 secondary unit, rounded testosterone secondary zero | One original result plus optional note; no duplicate and no replacement of the positive measured result by rounded zero |

Existing repository/schema/backup constraints require positive numeric results. Actual measured zero and qualified results such as <x need a separately reviewed representation; LAB-03 does not weaken those constraints, invent a positive replacement or claim qualified-result support. An upper-only reference range is distinct from a qualified result.

## Custom project proposal — NOT_IMPLEMENTED

An imported unknown code remains visible and editable with its original unit, and never gains inferred conversion or E2 eligibility. A formal user-defined project creation interface is not enabled in this change. Encoding a user-defined name, method or unit dimension into an unrelated field to evade migration is rejected.

A future reviewed design should introduce dataset-scoped stable custom definition identity (not a display name or reserved E2 code), original display name and raw unit, optional explicitly selected quantity kind, and immutable definition capture for historical results. Unknown units remain opaque. Domain conversion must default to unavailable, and custom projects must be excluded from concentration/calibration. It must cover renamed/deleted definitions, panel revisions, trash, restored datasets with reused integer IDs, backup import/export and old-reader safe rejection. Exact Room and business schema versions must be allocated only after inspecting #57 and current mainline. No custom field migration or backup change is approved or implemented here.

Derived non-HDL cholesterol, lipid ratios and FIB-4 are NOT_IMPLEMENTED; they are never saved as original reported values. Broader bilirubin/urea mass-unit conversions remain unavailable pending a verified analyte-specific source. No attachment picker or MED-02 storage is enabled.

## Verification ledger

- Domain catalogue: 135 tests PASS, including nine new source/conversion/boundary tests.
- Focused LabPanelDataTest: 13 tests PASS, including 45-analyte original-field/backup/trash roundtrip and failed transaction with no partial writes. Shared contract is also scheduled through existing API26/29/35 native classes; native results are NOT_RUN at this checkpoint.
- App catalogue/draft tests: 11 PASS plus PeriodStability5 PASS; Android test Kotlin compile PASS. Full CI App1514PASS/237SKIPPED/0FAIL; data263PASS; reminders20PASS. Exact source403ae960, receipt ci-403ae960.json.
- Native Activity recreation, actual landscape/portrait changes with24 populated rows and actual leave/lock draft clearing: PASS on API26/29/35. Expanded45-code raw backup/trash roundtrip and complete failed transaction rollback: PASS on the same three APIs. Physical devices and TalkBack: NOT_RUN; in-flight save/lock/completion race: NOT_RUN. Single-result and panel-addition unsaved forms keep their existing remember-only rotation behavior; batch form retention is the scope verified here.
- Implementation403ae960: all six required CI categories PASS on both push and PR. A later receipt-only commit still needs its own latest-HEAD checks. #89 homepage integration is pending and is not asserted by these data-branch results. Reproduce retained report checks with python3 docs/lab03/verify-ci-evidence.py.

Room/backup structure is unchanged. Older builds can lack the new display vocabulary despite accepting raw codes; downgrade UI support is not promised. Do not uninstall or clear data to work around vocabulary differences. New readers preserve old codes and unknown units.
