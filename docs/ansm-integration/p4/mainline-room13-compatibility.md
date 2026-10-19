# P4 mainline version collision and compatibility integration — 2026-10-19

Actual default86131172, source5fe9466f plus isolated prefix candidate. Existing owner joint-production/continuation/backend task authorizes necessary non-destructive Room/business-schema evolution; it does not authorize wiping, cryptographic change, catalogue distribution or a new release. This contract was frozen before integration. The candidate is now coded with440 local data tests PASS and native test compilation PASS; it is not native acceptance or production admission.

| Boundary | Approved main | Unreleased P4 prototype | Integrated candidate |
|---|---|---|---|
| Room |12, LAB02 panel table + nullable result link|12, inventory_encoding column|13, preserve approved12 then add explicit inventory marker|
| Business backup schema |11, LAB02 panel fields|11, exact inventory encoding|12, both approved lab fields and explicit inventory format|
| PNBAK1 cryptography |Existing authenticated format|Unchanged|Unchanged|

Do not replace main12.json or edit the approved11→12 LAB migration. Generate13.json from integrated entities. New12→13 validates old reference/measurement absence and adds inventory_encoding TEXT NOT NULL DEFAULT LEGACY_SCALAR; it never rounds, resizes or reinterprets capacity/used/clinical Double bits. Full upgrade chain1…12→13 must run without destructive fallback. An unreleased P4 prototype database12 is a different format and must not be silently treated as an approved main database12.

## File-level integration

| Files | Required action |
|---|---|
| NotesDatabase.kt / Entities.kt | Preserve LabPanelEntity/nullable lab result panel_id and existing identities; candidate version13, inventory field additive |
| Migrations.kt / NotesRepository.kt | Preserve all main migrations, add12→13; retain approved lab APIs and internal measured transactions, no App write admission |
| RecordBackupCompatibility.kt | Business12; schema<11 has no lab_panel/panel_id; schema<12 has no inventory_encoding and normalizes only to explicit LEGACY_SCALAR |
| DataTransfer.kt / BackupValidation.kt | Preserve parent-first lab panel ordering and approved old-column subset handling; candidate exact validation/atomic staging plus all lab guards |
| schema1…12 JSON | Preserve approved history byte-for-byte; generate13 separately |
| migration/backup shared contracts | Extend target, retain every old assertion/old fixture, add actual main12/main-business11 and old-reader fences |
| REQUIREMENTS/HANDOFF | Preserve all decisions from both branches; main108…113 keep their numbers, P4 historical additions get a mapping without altering immutable task sources |

## Restore and old readers

- Old main Room12 reader must reject Room13 before interpreting quanta; do not add downgrade/destructive fallback.
- Old main business11 reader must reject business12 before any destination write; outer PNBAK1 framing/KDF/GCM untouched.
- New reader accepts actual business1…11 with original semantics. Business11 may include LAB02 panels and panel_id, but must not claim the prototype's inventory_encoding field or accept prototype exact operations. Older encodings remain unknown legacy physical identity, never infer CIS/CIP/units from current names.
- Business12 requires an explicit valid LEGACY_SCALAR/INTEGER_QUANTA_V1 marker. Retain candidate source/ledger/UUID/epoch/CAS/ABA/type/hash/global audit and atomic rollback. Restore exact history without a current catalogue. Preserve panel parent/child links and original result hashes.
- Malformed/future versions, stale replacement tickets, structural tampering, reused IDs, failure injection/force-stop and nonempty replacement must leave the active dataset intact. Disabling attachments remains independent.

## Required evidence before P4 admission

Complete old upgrade chain and failed-DDL rollback, frozen approved12 schema hash, actual business11 panel backup/restore, old12-reader/new13 DB refusal, old11-backup-reader/new12 refusal, schema1…11 imports, all candidate measured contracts, lab contracts, backup/replacement/isolation and PeriodStability. Execute native SQLCipher26/29/35; rerun unchanged original API26 large-workload budgets on the final integrated source. Mainline already contains the AppOps grant-wait correction missing from old P4; integrate it ordinarily and execute the original scheduled reminder assertion, never copy an unmerged fix or call the old failure a flake.

State: CANDIDATE_CODED / LOCAL_DATA_VERIFIED / PRODUCTION_BLOCKED. Candidate Room13/business12 changes are isolated on PR57; new native and all required CI are pending. P5/P6 remain gated; physical device/TalkBack NOT_RUN; no signing/APK/Release.

Actual local receipts: main861-room13-schema-evidence.json (main12 preserved, only inventory column added), first/second failure receipts, then main861-room13-local-evidence.json (440/0/0/0). App stock/center/PeriodStability regression running; latest merged-head CI/native will be recorded in PR57 without a metadata-only CI loop.
