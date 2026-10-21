# P4 validation architecture review (TEST_ONLY)

Current948f8b9f / default320171cd, PR57 Draft/OFF. New owner scope is REQUIREMENTS120. Production implementation is not changed by this review. Complete current writer/invariant inventory: write-path-matrix.md.

## Evaluated mechanisms

A: real full-audit witness bound to exact SupportSQLiteDatabase handle and total_changes/data_version/main+TEMP schema versions/runtime epoch. This is stronger than a Boolean, but does not certify commit identity. A nested savepoint can repair a semantically invalid record, pass the real complete audit and capture this tuple, then roll the repair back while retaining exactly the tuple. The witness now matches invalid state. First actual RED is initial-parity-red.xml. Do not publish this witness as trusted state; process and connection identity alone cannot repair its transaction lifecycle.

B: transactional TEMP seal plus bounded all-domain/runtime DML journal (1024 records, explicit overflow) and schema-FK dependency closure with existing container projection audit. The seal is intended to repair A's rollback problem; its token rolls back with the business transaction. The journal observes DAO/compiled/raw writes and trigger side effects on the observed primary connection. It does not itself validate the transition. FK graph + balance projection is a hypothesis to test, not an assumption that all application constraints are FK constraints. A candidate must also account for same-record/revision uniqueness, zero-ledger operations, complete CONFIRM/allocation sets, causal independent counts/reconciliation, semantic history/trash/lab relations and aggregate size fences. The actual semantic-parity-red.xml demonstrates the gap: a second zero-effect CONSUME has a valid hash-chain link, passes FK checks and the unchanged local container projection, but the independent full audit rejects duplicate (record UUID, revision). This refutes that proposed dependency rule, not all possible semantic incremental validators.

No TEMP prototype is installed by app startup, Repository, migrations, restore or production code. The test journal is not protected against arbitrary deliberate tampering of its own metadata and is not presented as a production authority. A future implementation requires a reviewed writer gateway or another sound protection/invalidation boundary, detection of unsupported schema/connection changes, and explicit semantic dependencies. Stamp equality and journal equality are not independently sufficient proof. Unknown/overflow/cold/reopen must fall back to a real full audit or refuse.

## Failure and rollback design before any possible wiring

Only a genuine successful full audit may issue a baseline, and publication must be bound to successful commit (or a transactionally coupled, unforgeable authority), not merely audit return. Staged RAM restore and encrypted target are distinct owners. Migration, restore, runtime epoch replacement and process/connection replacement discard prior authority. A fresh process has no inherited trust; full-audit7.53s at the old large baseline must be reported as cold readiness cost, never hidden in startup/first write. Cold/fallback interactive5s acceptance is not established.

If persistent state ever becomes necessary, its definition/version/atomic publication, tamper bounds, complete rollback, all migration versions, backup exclusion or serialization rules and old-reader refusal require a separate design review. No Room14 or next business version is reserved. TEMP probes here allocate no production schema and do not change PNBAK1.

For a genuinely controlled transition, every touched row plus reverse/negative semantic dependencies must be closed, with all untouched predicates proven unchanged. Out-of-gateway mutations, trigger-generated edits, deleted rows and row/ID reuse must be observed independently of UI. An operation UUID and its payload conflict must still reject; a no-ledger event must still be audited; foreign keys and balances alone cannot authorize it. Do not clone all existing predicates into a second hand-maintained validator.

## Correctness evidence policy

Initial RED fixtures are retained verbatim. The first B sample (event UUID) was correctly rejected early by the existing writer, so it was a fixture preparation failure, not a locality counterexample. The next container-identity UUID sample passed the full oracle: universal cross-table UUID uniqueness is not an existing requirement. Preserve this behavior; do not tighten production semantics or relabel that PASS as a failure. The next test uses duplicate zero-effect consumption with a valid hash chain and no trigger bypass to exercise an actual global semantic rule.

Successful final counterexample tests mean a proposal was refuted, not that an incremental validator was proven safe or fast. Keep accepted/rejected/error/full business dump/projections/rollback comparisons distinct. No production performance claim is made from these tests. Original full validator and its safety regression assertions remain unchanged.

## Scope and remaining proof obligations

Exact fast verifier, exhaustive transition differential/property tests, commit/connection pooling correctness and safe authority publication: NOT_IMPLEMENTED. All fresh/modified/delete/restore/reconcile/package/legacy/maintenance paths retain original full auditing or existing guards. Existing test inventory is a boundary map, not proof that a new validator covers every future writer.

Low-memory native performance and300ms/1s/scaling of a new fast verifier: NOT_RUN (none is wired). Previous validated workload remains unchanged; latest source948f8b9f run39430822597 completes backup/restore, but writes7514.52/7162.35/6704.49ms FAIL. Six ordinary CI pass does not waive that gate. Any autonomous documentation/test CI replay measures unchanged production, not a new architecture speedup.

No UI/P5/P6/PK/crypto/official-content/permissions/version/signing/Release changes. No Ready or merge until a real proven candidate passes all production gates.

## Checkpoint execution (2026-10-20)

First focused run: 7 shared architecture tests PASS, Android test Kotlin compilation PASS (first-focused-green.xml). These are counterexample/observation tests, not a production fast verifier. An additional metadata-clear/drop regression was added; its first compile failed because the new assertion referenced nonexistent MeasuredRejection rather than the existing ReferenceError enum. compile-error.log preserves that implementation mistake; the enum reference was corrected without changing the assertion. Full data regression now PASS: 470 tests, zero failure/error/skip, including 8 architecture tests; Android test-source compilation PASS. See full-regression-result.json and full-measured-contract-green.xml.

The useful B substrate covers rollback-coupled token, DAO/compiled/raw DML, indirect ledger/container changes, bounded-journal overflow, staged restore and database close/reopen. Clearing a journal is not proof of no business changes; metadata is not a protected authority. Actual process death, connection-pool concurrency, independent writer connections and exhaustive transition equivalence remain NOT_RUN/NOT_IMPLEMENTED. No safety or performance claim is inferred for them.

The existing complete validator performs both structural and semantic checks over all participating historical rows. Ordinary writes have no reviewed trusted baseline/semantic-preservation certificate, so they must invoke that validator. Skipping it after merely noticing some changed rows would remove existing checks. Moving that entire audit to cold startup/first write does not establish the same interactive budget either.

Production decision: PRODUCTION_BLOCKED. Preserve the original complete audit and OFF entry. No incremental production change has been made; the available proof does not authorize it. Further implementation would need a complete predicate/dependency registry (including negative/global predicates) and independently protected, commit-coupled writer authority, reviewed against all matrix paths. A durable authority adds migration/backup/old-reader risks and requires separate design approval. This is a concrete missing proof boundary, not a claim that incrementality is impossible.


## 2026-10-20 finite A/B/C decision

See [decision-2026-10-20.md](decision-2026-10-20.md) for the newer exact-dc54 native failures, cold-path accounting, mechanism comparison and constraint-only test prototype. This does not admit a fast production validator or supersede the retained counterexamples.
