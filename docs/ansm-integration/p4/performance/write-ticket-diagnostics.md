# Post-budget real write-ticket diagnostics

TEST_ONLY. No production source, schema, backup format, allocator, forecast, cryptography or PK changes. P4 stays DRAFT / PRODUCTION_BLOCKED / OFF.

The original write budget includes ticket capture plus record/stock transaction. Existing sampling only covered the full auditor. The generated synthetic native probe now also times the actual `MeasuredUseIntegration.ticket` and `validate` calls, using the existing dataset scope. It neither creates a reference scope nor writes a record. Each call is inside the normal transaction and asserts unchanged `total_changes()` and `schema_version`. These fences detect SQL writes, including writes later rolled back; they are diagnostic assertions, not a production security boundary. The synthetic probe has no concurrent writer.

All original acceptance metrics and failure lists are captured before these diagnostics. Unsampled `write_ticket_capture_ms` / `write_ticket_validate_ms` and sampled equivalents are separate fields. The existing 1ms/16MiB post-budget ART trace now includes full audit followed by ticket capture and validation; `sampled_cached_full_audit_ms` still times only the full auditor. No method arguments or real user records are collected. Timing includes read-only fences/transaction overhead; it is not an isolated microbenchmark or an acceptance substitute.

The fixture SHA remains `9313167178989def0d4d89d785f84cdde764fba21033543cdf85e472c5511242`. Workload, RAM, heap, 5s write threshold and failure propagation are unchanged. Do not reuse diagnostic tickets for writes, cache trust between transactions, or omit full historical checks to meet a budget.

## Reproduction and actual results

`python3 scripts/prepare_ansm_native_performance.py` generates only Android test source; clean with `--clean`. Run the existing low-memory workflow for native evidence. New native runtime results are NOT_RUN at this commit.

`python3 docs/ansm-integration/p4/performance/ledger-stream-query-reproducer.py --write-ticket` reproduces the JVM diagnostic. Its `--emit PATH` output SHA equals the actually compiled/passed local probe (`cbfde3632cae017296cf370d91e7ef4ff2c4c34013688191effecd1f88c6c82f`). The portable fragment is tested byte-for-byte against the native helper. Original large fixture and 185-query projection equality PASS; ticket capture/validation ~120.48/124.28ms on JVM only. Generated Android Kotlin compile PASS. All 46 tool tests PASS, after the new missing-component contract first failed. Receipts: write-ticket-local-evidence.json and write-ticket-jvm-diagnostic.json. No physical/TalkBack testing: NOT_RUN.

For source951, actual API26/29 UTF8 method PASS is separately verified from official artifact digest + ZIP CRC + exact HTML test row, archived in native-profile-95108693/utf8-platform-oracle.json. Its PR-event six required checks PASS; the duplicate push run39262612979 has one CANCELLED Android job and is retained distinctly, not relabeled PASS. Performance source951 still FAIL (three writes ~11.08/10.26/10.93s). Prior source3b53 backup-stage process exit remains cause UNDETERMINED despite a subsequent complete run.
