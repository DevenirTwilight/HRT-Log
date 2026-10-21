# Measured backup plaintext staging after a matched kernel OOM

P4 candidate only; OFF / PRODUCTION_BLOCKED. Continues the existing owner-authorized P4 backup/performance work (REQUIREMENTS §114/117); this is not a new migration, encryption format, clinical feature or release authorization.

## Observed failure

Source bba9562d, native run39267083400/job118109495854/artifact12193548500: process died at `full_encrypted_backup`, original JSON remains IN_PROGRESS. The verified post-test kernel tail explicitly kills PID3915 (`s.app.data.test`); the synthetic log identifies that exact PID as `net.plainnotes.app.data.test`. This run has a confirmed system OOM kill. 151160KiB anon RSS is the kernel observation at that kill, not measured peak RSS. The last saved Java heap observation is not total process memory. The precise failing allocation/subphase (DOM/text/UTF8/KDF/cipher) remains unestablished. The older 3b53 exit is NOT retroactively classified by this new evidence.

All post-restore writes, ticket diagnostics and sampling in this run are NOT_RUN. No write-budget conclusion can be derived from it. Original 512MiB RAM/192MiB ART and fixture/thresholds remain unchanged.

## Candidate allocation change

The old internal export constructs the whole business DOM, serializes the entire document and encodes it to a contiguous UTF8 array before the unchanged cipher. The candidate encodes one row at a time inside the same capture transaction, with one final plaintext array. It removes the need to retain a whole-business DOM and full-document String. This is a structural reduction in avoidable allocations, not proof that a particular old allocation caused the kill or that the new native run will pass.

`RawData.forEachBackupRow` extracts the existing trusted-column/type encoder without changing its rules. Ordinary legacy exports retain the DOM path. Only `exportMeasuredBackup` selects `MeasuredBackupJson`; staging, restore, validation, full audit, stock and history algorithms are retained. The byte stream includes every DOMAIN_TABLES table, in the existing order, all required columns, integer-quanta formatting, nulls, floats, Base64 blobs and strings. No SQL or table name comes from a backup. A frozen independent copy of the pre-change encoder in the shared migration tests guards against both paths accidentally sharing an incorrect new encoding.

UTF8 is always the platform encoder on complete row JSON fragments. Fragment boundaries are ASCII JSON punctuation, not arbitrary slices within strings or surrogate sequences. No hand-written Unicode codec, whole-catalog lookup, inferred identity or unit conversion is introduced.

`BackupPlainBytes` reserves at most the existing 32MiB plaintext limit in 64KiB fragments, checks remaining capacity before copying and flattens once. Thus fragment-plus-final-byte-array storage is bounded by twice that existing limit, excluding a row/JSON text, cursor, VM and cipher allocations. This is NOT a process RSS bound. Temporary UTF8 fragment arrays and owned byte fragments are zeroed in finally/use on success and exceptions; returned plaintext is still cleared by the original export finally. There is no plaintext file, GC forcing, heap/RAM increase or weakened acceptance assertion.

Encoding now completes while the captured transaction is open; KDF/encryption remain outside it. Existing seed/audit/capture stay in that transaction. A limit/encoding failure now rolls back any seed work in this transaction instead of discovering TooLarge after capture has committed. No record/ledger/official-source rewrite is added. The final bytes are only returned after the entire document succeeds. A canceled/failed export cannot expose a partial archive.

PNBAK1 magic, format1, current candidate business Schema12, Room13, salt/nonce/KDF/AES-GCM/AAD/limits, restore parser, older-backup upgrades and newer-version rejection are unchanged. No new delivery APK or public Release.

## Evidence gates

New tests were first compiled without the implementation and failed with missing symbols. Then focused tests passed. Final independent frozen-oracle/full data/App regression, large fixture/native compilation and actual API26 runtime results must be recorded separately. Native memory/performance admission remains NOT_RUN for this candidate until the new workflow completes; physical devices/TalkBack remain NOT_RUN. Source951's completed 10–11s write failure and sourcebba's confirmed OOM are retained, never converted into a pass.
