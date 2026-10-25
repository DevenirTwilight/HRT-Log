# Cross-app migration entry — PR-A, 2026-10-25

Base main1ddcf431, Room17/business13. This first independently accepted slice unifies HRT tracker/TransMTF (the same existing format family) and Trans Memo v8. New sources are not advertised as supported merely because a parser/protocol investigation exists.

| Files | Change and protected boundary |
|---|---|
| importer/CrossAppInput | Content signatures, bounded bytes/UTF8/depth/string/container/token guards, strict tracker fingerprint, then existing parser/version rules. Unknown/ambiguous rejects; empty bare arrays lack a unique fingerprint. No external crypto, PK or storage. |
| privacy/CrossAppImportReader | One transient SAF input; no persistable URI. JSON32MiB, SQLite64MiB, depth48/string65536/100000 containers and aggregate SQL rows. SQL uses existing private no-backup copy cleanup before preview. No public plaintext cache. |
| DataTransfer/AndroidSqlSource | Optional bounded read-only adapter for unified input; legacy entry defaults stay intact. Real tables only, no views/virtual tables, row/string bounds before list growth. Existing writers unchanged. |
| NotesViewModel | One scoped scan, cancelable Reading, shared preview, then original HtReady/ImportReady and options. Old APIs retained for regression. Real writer return captures committed summary before reminder rebuild/UI refresh; replacement alone revokes the old library as before. |
| DataSection / ForeignImportPreview / four strings | One normal cross-app entry; native encrypted restore stays separate. Actual source counts, not promised inserts. Explicit continuation to unchanged source choices; Trans Memo overwrite is not a default. Actual writer receipts remain the final report. |
| New importer/flow/UI tests | Synthetic wrong extensions, unknown/future versions, malformedUTF8/limits, cancel/stale dialog, real data commit/no stock/no reminders, post-COMMIT failure, original regression assertions retained. |

All source clinical mapping is delegated to original HrtTracker/TransMemo implementations. This slice does not redesign their source keys or claim conflict synchronization: original duplicates/explicit options/summary semantics remain. Raw history is not inferred from current plans. No new Room/schema/backup protocol, permission/dependency, PK, attachment feature, permanent deletion or release change.

Remaining source work: Mona DB23 parser/typed facts separately staged (not user import); generic imported-origin/provenance compatibility design is necessary because current SQL guard only allows APP/IMPORT_TM/IMPORT_HT/AUTO_MISSED. Do not label Mona as IMPORT_HT, and do not smuggle identity into PK/review free text. Featherline requires independent authenticated decoder; current HRTBKP1 is safely unsupported here. Chrysalide logs lack original actual-dose snapshots; current medication dosage cannot fabricate them. Supporting detection is not supporting actual import.

Status: first production + native-test source compilation PASS; importer19 and targeted app17 initiallyPASS; subsequent post-COMMIT fault/UI/native additions need own exact-source verification. Physical/TalkBack/manual NOT_RUN. Final required CI pending. No real medical sample is used.
