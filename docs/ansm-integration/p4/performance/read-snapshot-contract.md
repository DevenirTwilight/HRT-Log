# Transaction-local reference reads

Status: local407 PASS / Android source compilation PASS; **native execution pending, P4_BLOCKED**.

The previous native a3e95e4 candidate improved full audit to18.13s and restore to81.10s but still failed four budgets. A synthetic counted audit then identified62219 SQL calls:12007 identity lookups,8006 event lookups,6009 capture lookups,6000 snapshot lookups and other repeated keyed reads. `run_ansm_inventory_query_diagnostic.py` reproduces the same fixture and appends a read-only counted audit after the real writes. Counts and timings from this manual tool are diagnostics, not native acceptance.

`ReferenceAuditReads` retains only scalar reference rows for a single synchronous auditor call. The original early count/type/text limits run first. Six reference tables may be prefetched; large mostly unique operation envelopes are excluded. A conservative8MiB allocation estimate controls admission, not the process heap setting. Every incomplete table or unsupported lookup falls back to the real database. A complete table alone may prove an absent primary key. Earliest-event indexes are used only for complete tables. There is no SQL parser or approximate name/unit matching.

Each lookup returns a fresh MatrixCursor with copied headers and copied row slots. Values are null/Long/immutable String; callers never receive cached arrays or mutable JSON. The normal validators still check every row header, namespace, source version, digest, owner, request, reference graph, operation chain, original ledger entry and bounded prefix. Generic/aggregate validators still query the actual database. No historical rows or caches are repaired.

The scope rejects writer methods and nonread queries. It cannot be used after finish or outside a transaction. Before returning any audited projection, the scope checks the captured connection's total_changes and schema_version; unexpected underlying writes abort the caller's transaction. The class is constructed locally, never returned by the auditor and never retained across writes, restores, epochs, datasets or lock changes. It is not a general SQL sandbox.

Frozen consumption requests separately memoize only their immutable typed request and digest; uses are defensively copied into an unmodifiable list. All canonical text, hashes and field validation remain unchanged.

Tests compare actual and cached rows, mutate returned objects/headers, exercise zero and partial admission budgets, reject writer/PRAGMA mutation calls, reject reuse after finish, and inject an underlying write to verify abort and rollback. Existing malicious candidates, Room/backup, VOIDED/REVERSE, source/owner/UUID and historical tests remain unchanged. Real API26/29/35 SQLCipher execution, low-memory heap/time budgets and final-head six-check CI remain required. No budget, fixture, crypto, schema, inventory/forecast algorithm or official distribution gate changes.

## Native result and acceptance boundary

At db34e87, run39066391148 completed with four budget failures. The JVM SQL reduction (62219 to20198) did not establish a native improvement. Do not extend this cache based on host-only timing. The next isolated generated Android test adds post-budget component timings and raw/cached full-audit pairs on the same database, comparing complete projections in both orders. Existing measured phases, limits and failure assertion remain intact. Original heap/disk sampling stops after original metrics are frozen; diagnostic memory is not included in those phase-scoped peaks. No device health data or production writer is used.
