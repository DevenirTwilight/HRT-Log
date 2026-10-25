# Mona persistence compatibility proposal (not implemented / awaiting decision)

2026-10-25. Applies only after PR123 acceptance and protected merge. Current default1ddcf431 is Room17/business backup13 (user-only export12). No active PR currently claims Room18; recheck before implementation. This proposal does not activate attachments or modify native backup cryptography.

## Why existing storage is insufficient

- dose_record has a unique source_record_key, but its origin constraint only allows APP/IMPORT_TM/IMPORT_HT/AUTO_MISSED. Calling a Mona record IMPORT_HT/TM would falsify its origin.
- LabPanel has a UUID, but no immutable foreign-source ID/digest. Editable notes, PK fields and concentration context are not authoritative import metadata.
- Content/date/dose matching cannot prove identity; storing only the current target fields cannot distinguish a changed source export from a local user correction. Dropping this evidence on backup would break safe repeated import after restore.

## Smallest proposed persisted extension

- Room17→18 non-destructive migration: add one external_import_item table and allow true IMPORT_MONA origin. No new plan, stock, PK, attachment or reminder table. Recheck actual next available Room version at implementation.
- Receipt fields: source_app (initially MONA only), source_kind (INTAKE/BLOOD_TEST), source_id (exact source integer string), source_schema23, source_digest (canonical source-row SHA256), frozen source_json (bounded original clinical strings/identities), target_kind (DOSE/LAB_PANEL), target_identity (dose source key / real panel UUID), imported_utc and explicit receipt retention state. Composite source identity unique; typed target identity unique. No cross-generation integer-ID guessing.
- Same ID/digest is a duplicate; changed digest or missing/locally changed target is a conflict preserving local facts. No silent update and no content-only merging between unrelated source IDs. Mona exports no installation UUID: report that limitation.
- Receipt and accepted clinical rows use the existing scoped Repository transaction; permission/generation checked at entry and commit. Any failure/cancellation rolls back all accepted types. Real commit gets an acknowledgement before optional refresh/scheduling; no persistent task queue.
- Preserve original clinical strings/zone/compound/ester/basis in receipt, but never feed them as new PK parameters. Current dose/lab model gets only safely representable values. Raw schedules/supplies remain unsupported counts, not active treatment/stock.
- Explicit deletion integration: keep source evidence with live/trash history; a real permanent clinical purge clears its raw clinical payload and retains only the minimal source-identity/digest tombstone needed to avoid accidental resurrection. Wipe removes it. Ordinary cancel/soft delete/restore must not silently erase or rebind source identity. No medical attachment file/key deletion involved.

## Backup compatibility decision requested

PNBAK1 framing, Argon2id parameters, AES-GCM and password handling stay byte-protocol compatible. PNMEDBK1/2 envelopes/crypto/member semantics stay unchanged. The ordinary **business serialization** needs a new explicit version14 only when Mona source data is present, so older maximum13 readers refuse before opening/modifying the target rather than silently losing receipt metadata. Without Mona data, retain existing12/13 output selection. New reader restores old1–13 as before, creating no inferred Mona identities. Version14 requires typed receipt/payload/digest checks and complete preservation in ordinary and collection business snapshots/reconstruction. Misdeclared older schema with Mona-origin/receipt data is rejected. This business-version expansion is a separate compatibility decision; it is NOT claimed to be authorized by the earlier default-OFF MED approval.

## Concrete file/test scope

Production: importer/Mona.kt + ImportFormatDetector; core/data new receipt entity/schema/writer, NotesDatabase, Migrations, SchemaGuards, RecordBackupCompatibility, BackupValidation/restore table maps, narrowly scoped NotesRepository import/trash integration; app NotesViewModel, unified DataSection source preview/report and four locale resources. Existing HT/TM parsers/writers and native backup crypto untouched.

Tests: own official-schema23 fixtures; stable IDs/changed source/local edit/deleted rows; exact UTC/IANA/precision/compound/ester boundaries; null actual time; labs E2/T original units/grouping; unsupported schedule/supply counts; cancel/failure rollback/generation denial/commit receipt; full historical migration chain +17→18/rollback; old backup restore/new-version refusal/full receipt round trip; actual SAF Mona→preview→commit→reopen/reimport on API26/29/35; old import/PeriodStability regressions and four-language320dp/2× accessibility. No real health data. No APK/release.

Until this compatibility expansion is approved: source-format audit and independent parser/UI tests may proceed after A; no production migration, receipt writes or falsely labelled Mona support. No Featherline/other formats.
