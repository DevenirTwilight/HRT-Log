# Mona PR124 integration checkpoint (2026-10-25)

Starting source ffd2abad; PR123 is MERGED at d477f092, its default Android40210593055/Science40210593016 passed. Work continues in existing PR124, with no concurrent Room migration PR found.

## Executable path

Unified MainActivity DataSection SAF → bounded single snapshot → independent MONA23 rule/typed parser → scoped Repository conflict preview → four-language Mona dialog → explicit confirmation → existing atomic Writer/source receipts → real commit receipt/report. A preview is single-use. Cancellation before commit is not success; later read/reminder failure cannot erase an acknowledged commit. No URI reopen, separate source button, automatic current regimen, inventory consumption or PK calibration.

Preview includes original dose/units/UTC instant/source zone/chemical identity, individual new/existing/deleted/conflict/unsupported states, per-analyte E2/T values, schedules/supplies excluded counts, and reasons. New medicines are inactive review items; no implicit match by name. Current support is database23 only; schedules/supplies are not imported. IDs are scoped to MONA, but Mona has no installation UUID: two device exports with overlapping IDs are conservatively duplicates/conflicts, never guessed independent.

## Compatibility repair and retained evidence

Original ffd2 PR run40216890041 really failed, not infrastructure: restore required external_import_item in old12/13, MedicalArchiveRecords counted an unconditional new table set. Both are now version-gated; absent old receipt table must rebuild as empty, while schema14 still requires it and validates source receipts. Full candidate exact row comparison remains. Current-version test assertions use18; historical17/16 rollback assertions remain unchanged. The16→17 injected-fault builder now includes the17→18 continuation so its fault is actually reached. The synthetic schema12 preflight fixture uses its published table set. Raw host/API26 job logs retained alongside this file.

First local integration pass: importer46, core28, app21 (Mona8 + TransMemo8 + Period5), all zero failure/error/skip; Android test Kotlin compilation passed. Subsequent additions (accessibility, missing-target/multiple conflicts, schema14 independent reconstruction/replay, final native fixture) require the final checks currently running. Do not promote intermediate results to final acceptance.

## Acceptance still pending

Final local data regression, lint and four-language320dp/2× semantic/geometry checks; final exact-SHA six CI checks; actual API26/29/35 MainActivity/DocumentsUI Mona cancellation/confirmation/commit/reopen/replay/malformed file, with original HT/TM assertions retained. Native compilation is not native execution. Activity recreation is not a physical-device/process-death claim. PHYSICAL_NOT_RUN; physical TalkBack NOT_RUN. PR stays Draft until acceptance.

MED feature0/publicfalse/external-review-pending remain unchanged. PNBAK1 and PNMEDBK1/2 crypto/framing unchanged; Room18 and conditional business14 are owner-approved148.C. No Featherline decryption, PK/stock algorithm, INTERNET, APK/signing/version/Release changes. PR-C later reuses single snapshot/container detector, scoped owner, source-specific preview and receipt/report boundary; it still needs its own reviewed password/format adapter and authorization scope.
