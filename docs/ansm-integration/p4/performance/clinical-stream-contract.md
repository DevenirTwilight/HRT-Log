# Complete operation clinical stream — 2026-10-18

Resume existing PR57, source6c871bfd; actual default claude/new-session-1959qb at86131172. Main six required CI PASS, including post-merge #85. P4 ordinary engineering CI PASS does not override native run39130872641: writes8170.41013/8231.03673/7590.97189ms exceed unchanged5000ms. No admission or second P4 PR.

| Files | Candidate change / verification |
|---|---|
| MeasuredConsumptionStore.kt | Replace per-operation live-clinical point reads with a typed, complete LEFT JOIN stream; retain every original check |
| historyTest/MeasuredConsumptionContract.kt | Shared real Room and native SQLCipher query-count negative control and corruption regression |
| performance evidence | Same fixture:14600 facts/2000 packages+operations; full projection equality; JVM diagnostic separately from API26 acceptance |
| HANDOFF.md / PR57 | Actual results and final SHA; no source-identity CI loop |

The operation stream must not filter away retired identities, missing rows or zero-effect operations. Existing identity/owner/dataset/revision, raw clinical Double bits, time, zone, status, config hash and schedule hash checks remain. Joined identity live handle and actual record id must equal the independently validated business identity. Preserve exact integer/real storage type checks; NULL is not zero. Never retain cursor-backed values beyond the row callback, across transactions, datasets, epochs or writes. No new global trust cache, schema/index change, PK or treatment inference.

First write a shared negative query-count test on the unchanged production implementation: valid nonempty consume must audit and preserve all rows/balances, while requiring zero per-record point queries. It should expose the existing one-query-per-operation behavior. Record the actual RED result before implementation. Existing malformed-history and reverse/revision contracts continue to run; add focused cases if the join exposes an uncovered branch.

## Mainline compatibility gate

Main now owns Room12 and business schema11 for LAB02, while PR57's old candidate uses the same numbers for different content. Do not overwrite main's12.json, migration11→12, approved lab entities or backup behavior. Future integration must preserve released/current versions and design the next additive candidate fence (expected Room13/business12, actual names after review), extend all old-reader/restore chains and regenerate deterministic schema from the integrated source. Existing owner§102/108/110 permits necessary non-destructive evolution, but this note is not implementation or proof. Performance work remains isolated on the current candidate; mainline compatibility is an independent blocker before PR57 admission. No migrated user data or production entry enabled.

Status: AUDIT_COMPLETE / TEST_IN_PROGRESS / PRODUCTION_BLOCKED. Native performance must pass original budgets plus all six checks before P5; physical/TalkBack NOT_RUN; no signing/version/APK/Release action.

RED: unchanged6c871bfd performs1 clinical point lookup in the nonempty fixture, expected0; actual failure receipt clinical-stream-red-evidence.json. Candidate LEFT JOIN stream then passes the complete422 data JVM tests with zero failures/errors/skips, and Android test Kotlin compilation. It verifies both joined identity handle and actual record id against the separately audited business identity. Receipt clinical-stream-local-evidence.json. Large original-workload projection/query diagnostic is running; new source native acceptance and six CI PENDING. This is not mainline compatibility or production admission.
