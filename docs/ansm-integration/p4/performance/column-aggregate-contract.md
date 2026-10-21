# One invalid-cell aggregate, retaining every original column predicate — 2026-10-19

Baseline1feacef4 native39226229051 actually FAILS writes6163.48682/6110.58547/7005.45267ms>5000; all other original budget categories pass. Official artifact08ae367b…b52d851/SHA/CRC/parser replay retained in native-profile-1feacef4/. Stored-column post-budget component767.1867ms is a diagnostic, not an isolated controlled gain forecast.

Before implementation, the shared JVM/native26-29 contract matches45 SQL oracle batches against the unchanged old function: empty/null, each column's invalid types, SQLite affinity, finite/infinite/NaN storage, exact text bound, UTF8/NUL and early/late invalid rows. The fixture is an isolated factory database with three storage types per synthetic table. It is NOT a Room migration or PNBAK1 compatibility claim; those original full tests remain separate.

Let p(r,c)=1 iff the original CASE considers row r/column c invalid, otherwise0 (including SQL NULL as false). Old acceptance requires MAX_r p(r,c)=0 for every c. New acceptance requires MAX_r OR_c p(r,c)=0. These are equivalent, including empty tables via the same COALESCE0. CASE over SQL three-valued OR maps TRUE to1 and FALSE/NULL to0, preserving the old per-column CASE semantics. Build a balanced OR tree so trusted-column growth does not introduce linear expression depth.

Keep every original typeof/nullable/finite/text-length predicate byte-for-byte. Keep the early COUNT row fence, trusted PRAGMA columns, SUM UTF8 BLOB byte expression and32MiB total text bound. Only reduce aggregate states/columns from N invalid flags to one. Return type remains Unit; malformed data still rejects before any destination write. No physical quantity, trusted history, conversion, schema/backup format, PK, crypto, permission or release changes.

Require the45-batch old-SQL oracle after implementation, full data/real Room upgrades/restores/rollback/typed/corruption contracts, native compilation, final App regression and original large workload/native absolute admission. Do not claim more than the evidence; production remains BLOCKED until all original gates pass.

Local result: PASS450 data tests (zero failure/error/skip), generated native Kotlin compile PASS; original14600 facts/2000 containers/32603 rows JVM workload PASS,185 queries and complete counted/uncounted projection equality PASS; final App Period/Privacy/stock/UNKNOWN43 tests PASS. Receipts: column-aggregate-data-evidence.json, column-aggregate-final-local-evidence.json and column-aggregate-query-diagnostic.json. This is not native performance admission. New source CI/native gates pending.

Previous1fe source six engineering categories all PASS, but original native write budgets FAIL as retained. The10716-case platform UUID oracle actually ran and passed API26/29; artifact/report hashes in native-profile-1feacef4/platform-uuid-oracle-evidence.json. No substitution of JVM or compilation evidence for native execution.
