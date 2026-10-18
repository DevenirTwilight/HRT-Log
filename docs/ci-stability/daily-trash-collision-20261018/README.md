# Daily snapshot restore: millisecond reference collision

2026-10-18. Baseline default branch `claude/new-session-1959qb` at `d13048b18ad15eb4e7aba70f63c3c5d94a705660`. Independent fix, not part of the unmerged P4 inventory candidate.

Actual CI run39069136997 / job117503045426 failed `TrashDataTest.aDailyValueEnteredAgainIsSwappedIntoTheBinOnRestore`: SQLite UNIQUE constraint `trash_item.kind, trash_item.ref`, originating at `Trash.insert` during `setScore` called by `restoreTrash`. The same source also had a green run; that does not fix the defect. Artifact12114701507 digest and ZIP CRC were verified; see `red-evidence.json`.

Daily SCORE/NOTE/SYMPTOM refs include a millisecond timestamp. Delete a value, enter another, and restore the original within the same millisecond: the replacement value attempts to enter the bin with the original's kind/ref while the original is still present. The entire restore fails. Four new fixed-time tests were actually run on unchanged production code and all failed, including a failure-injection case that could not reach its intended reinsert because the identity collision happened first. No sleep, timeout relaxation, retries or modified old assertions were used.

Only a newly inserted daily snapshot whose kind/ref already exists receives a fresh opaque UUID ref. Existing snapshot bytes, original ref, date and deletion time stay unchanged. Other kinds retain their stable refs and duplicate rejection. The unique index and CONFLICT_ABORT remain. Existing Repository transactions serialize the check and insert and roll back the complete swap if reinsertion fails. An unexpected UUID collision still aborts; nothing is overwritten.

No schema, migration, backup format/cryptography or clinical/stock/PK semantics change. Daily refs are already opaque TEXT; supported backup validation and restore use their snapshot payload, not a timestamp parsed from the ref. The tests verify encrypted backup/restore of the new ref, both displaced values, unchanged timestamps, non-daily duplicate rejection and an injected failure with byte-identical domain rows after rollback.

Local results: initial in-memory247 tests PASS, then the same4 new methods were moved to the existing shared-test mechanism and run with file-backed SQLite: full247 PASS, no failures/errors/skips. Native SQLCipher wrapper compiled; native26/29/35 execution and finalSHA six CI are NOT_RUN/PENDING at this checkpoint. Python156 PASS. Physical hardware NOT_RUN.

## Verified source CI checkpoint

Source `88a60f1d8df01671d8ea64b90117343974839012`: all six required checks passed in both push/PR runs. Official artifacts were downloaded and their GitHub digests and ZIP CRC verified. Actual SQLCipher `DailyTrashCollisionAndroidTest` ran all4 cases with zero failures/skips on each API26,29,35. Full JVM reports: data247/0fail/0skip, reminder20/0fail/0skip, app1379/0fail/205 existing conditional skips. The original `TrashDataTest` assertions remain unchanged. See api26/api29/api35/build evidence and source-88a60f1-checks JSON.

This follow-up changes only evidence/HANDOFF. Its final HEAD ordinary CI must run separately; source88a60f1 green is not presented as that new HEAD's check result. No merge or release has occurred. [REQUIREMENTS §103](../../REQUIREMENTS.md) covers ANSM PRs; the owner task §2.1 explicitly forbids silently applying that auto-merge authority to a separate general CI/data fix. Owner review/ordinary protected merge remains a separate step.

## Integration ownership

The only existing shared integration file changed is `.github/workflows/android.yml`: append `DailyTrashCollisionAndroidTest` to the API26/29 explicit list; API35 already runs every data test. All14 previous classes, all jobs/flags/prefetch steps and six required checks remain. Existing Gradle source sets already include the shared contract; no Gradle changes or dependencies are needed.

PR57 independently adds `MeasuredContainerAndroidTest` and `MeasuredConsumptionAndroidTest` to that same line. This is a known future textual conflict, not permission to copy its unmerged code. After this fix is reviewed/merged, P4 integration must take the union of the original14 classes, the daily class and both measured classes. Preserve both HANDOFF entries, then rerun finalSHA checks. No other active ANSM58/59/64 PR changes that workflow. No forced merge or branch-protection changes.

This bug fix remains a separate reviewable PR; ANSM auto-merge authority is not assumed to authorize an unrelated daily-data fix. No release, signing, version change or APK delivery.
