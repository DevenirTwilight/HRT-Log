# Allocation-free UTF-8 length, same platform boundaries

Actual b171 native failure and trace retained in native-profile-b17178ec. Native CharsetUtils.toUtf8Bytes accounts for407370 sampled exclusive CPU microseconds; approximate diagnostic, not a predicted controlled speed gain. No pagination or relaxed audits.

First run the75555-case shared JVM/native platform corpus with a thin platform-delegating helper and unchanged production callers: PASS1 test (receipt utf8-length-baseline-evidence.json). Every UTF16 code unit,10000 seeded strings (seed0x55465438, length0..64), paired/unpaired/high-high-low combinations, NUL and exact65535/65536/65537-byte ASCII/2/3/4-byte cases. Two separate pairing assertions ensure per-code-unit sums cannot replace paired encoding. This characterization is not a fabricated RED and has not yet executed natively.

Replace only byte-array allocation used to obtain length at four boundary checks (canonical output, use request, package payload, operation chain). Count ASCII1/BMP2or3/pair4/unpaired1 exactly as platform UTF8 replacement. A Long accumulator cannot overflow for Int-sized strings. SHA256 still consumes the actual platform UTF8 bytes; canonical NUL/surrogate rejection, JSON parsing, byte ceilings, chain aggregate retention limits, all ownership/clinical/ledger checks remain unchanged. No wire format, algorithm-version, PK, quantity, database or backup-crypto changes. No mutable shared encoder/cache.

Require full data/backup/Room/ledger regression, native compile, final App checks and unchanged original workload and native budgets. Full engineering/native admission for the new source is pending; P4 remains BLOCKED/OFF.

Final local result:451 data tests,43 App Period/Privacy/StockConsistency/UNKNOWN tests,10 Python tests all PASS/zero failures/errors/skips in their actual suites; shared and generated native Kotlin compile PASS. Original14600 facts/2000 packages/32603 rows JVM probe PASS,185 queries and complete counted/uncounted projection equality retained. Receipts utf8-length-{data,final-local}-evidence.json and utf8-length-query-diagnostic.json. Native corpus and native performance remain NOT_RUN for this candidate.

Approved-main merge sourcee36 (before this candidate) actually FAILS native39254914424 writes5796.96347/5840.99653/6842.41092ms>5000; artifact30338ea7…159b96 SHA/CRC/parser replay preserved in native-profile-e36c52db. Inventory production code is unchanged fromb171 in that mainline merge; different hosted timings are not a controlled effect. Original gates and test workload stay unchanged.
