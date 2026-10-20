# P4 closeout — substantial write-performance repair A1/2

Base: `51effe6ce05459606560b4e378eb8240b58ff7f6`; merged default `320171cdc882ec0b62830b60e2329a4174ffa31c`. P4 remains Draft / OFF / PRODUCTION_BLOCKED. This is the first substantial write-performance repair of the finite closeout cycle; earlier baseline diagnostics are not performance passes.

The completed native baselines retain full staged auditing as the dominant write cost. Native 140 measured ~7s full audit plus ~1.2s ticket capture/~0.8s validation. Native 89's partial SQL detail identifies the full ledger/operation payload projection as its leading observed driver call (~1.42s); incomplete transport does not establish a full exclusive wall-time decomposition. Native 51 completes backup/restore (8661/34240ms), but writes 6955/7421/7244ms still FAIL. Raw official evidence is in adjacent `native-profile-*` directories. Host differences between runs are not causal improvement evidence.

## Minimal structural change

Capture primitive operation headers alongside the already validated canonical operation chain, once per complete read-only transaction. Reuse those immutable values in the ledger projection and clinical/adjustment passes, while continuing to query their original ledger/identity/clinical rows with unchanged order/coverage. All existing chain, hash, canonical text, allocation, revision, source, unknown, reference, stored-column and historical projection checks still run. Returned JSON rows are fresh copies, never mutable aliases to evidence.

Metadata has a conservative 4MiB accounting bound in addition to the existing canonical 32MiB text fence; this is not a process-RSS guarantee. Overflow discards the entire metadata cache and uses the original full SQL path. Zero budget explicitly exercises that old path as a differential oracle. Missing rows never become zero or known absence from an incomplete cache. All evidence is cleared before the audit exits. Existing total_changes/schema_version checks reject any mutation during the read snapshot, including writes through an underlying handle. No cache survives a transaction or establishes trust for a later write.

No generation journal, new table, Room version, backup version, incremental validation or production activation is introduced. This deliberately starts with the safer transaction-scoped reuse allowed by the task instead of assuming cross-write incremental validation equals a full audit. If this cannot meet the fixed native gate, at most one further evidence-based substantial repair is allowed; unproved cross-write trust is not an acceptable shortcut.

## Failure oracles and evidence

Four additional shared tests cover exact/fresh captured rows and lifetime, full/zero/overflow projection equivalence and unchanged business dumps, mutation injected at the new narrow stream with mandatory detection/rollback, and forged header/canonical text at the actual capture query with mandatory detection. The original after-chain forgery test still exercises the exact old query using budget zero, retaining all rejection/injection/dump assertions. Its stale-query initial failure is preserved in `initial-MeasuredConsumptionStoreTest.xml`; no negative assertion was deleted.

Full data regression: 461 PASS, zero failure/error/skip; shared Android Kotlin compilation PASS. App safety regression: 43 PASS, zero failure/error/skip. Original 14600-record / 2000-container / 32603-row portable fixture PASS, 185 queries, full projection and read-only assertions intact. Final native outcomes remain pending and are recorded separately when actually completed. JVM speed is not native acceptance.

The native collector now retains the already validated pre-cleanup full metadata (same 64KiB limit, current run nonce, fixed emulator context and trace digest), instead of falling back to truncated logcat after test package cleanup. A retained FAIL remains FAIL; tampered trace/wrong nonce/oversized metadata are rejected. Two tests were RED before implementation; all 66 ANSM tooling tests now PASS. This is report preservation, not a new diagnostic framework or relaxed admission gate.

UI, P5/P6, PK, encryption, actual user data and release delivery are untouched. Physical device / manual TalkBack: NOT_RUN.
