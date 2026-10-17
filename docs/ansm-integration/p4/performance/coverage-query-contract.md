# Exact source coverage: equivalent set materialization

The original correlated NOT EXISTS query could rescan presentation/identity relations per container. At2000 containers, real post-restore writes took6.44…6.75s and FAILED the predefined5s diagnostic budget. That failure remains archived; thresholds are unchanged.

The candidate materializes DISTINCT non-null live handles having a CONTAINER identity with a PRESENTATION snapshot, then uses a left anti-join to find exact-encoding containers outside that set. It is the same existence predicate over the same transaction. No current name, dose, catalog or inferred identity is introduced. Retired NULL handles cannot cover a current container; all existing owner/dataset/type/hash/causal and ledger checks still run independently. There is no new index, schema or cross-transaction cache.

One shared Room/SQLCipher contract compares both queries on empty, legacy, separate owners/meanings and an exact container missing its source, proves read-only results, and requires the full auditor to reject the orphan. Initial compilation failed because the added test omitted two required constructor values; those fixture values were supplied and the selected contract passed. No assertion was weakened. Full regression is a separate evidence gate.

Identical large fixture after optimization: audit3.81s, forecast4.32s, encrypted export4.75s, encrypted restore12.35s; three real writes4.61/4.32/4.64s. All local diagnostic thresholds passed, with532886528 bytes sampled JVM heap. Both the failed and passing reports, hashes and generated native compilation evidence are archived. API26 low-memory/native disk and final HEAD CI still pending.
