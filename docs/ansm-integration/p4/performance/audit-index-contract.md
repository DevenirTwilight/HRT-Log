# Transaction-local audit indexing

Status: IMPLEMENTED / LOCAL_REGRESSION_PASS. Full data388/shared113 and Android test-source compilation PASS; native runtime and final HEAD CI remain separate gates.

The baseline real Repository probe showed growth with exact-operation history. The optimization leaves full audit admission, storage/type/size checks, source validation, all ledger prefixes, record facts and causal heads in place.

Within one read-only audit call only:

- Identity rows and source/presentation parses are reused by their full stable identifiers; owner/dataset checks still apply to every use.
- The stock scale is reused by the frozen payload digest.
- Each container/capacity builds one integer-prefix index with the original StockQuantum reducer and range checks. A sequence index holds the maximum ledger ID strictly before each operation. Lookup checks the frozen head against that same maximum and returns the exact prefix at that head.

This is mathematically the original algorithm: max(ids whose operation sequence is earlier), followed by the bounded exact sum at that head. Zero-effect observations remain visible in logical sequence; the lookup does not require them to have ledger rows. Missing/foreign/future heads and source identities remain errors. There is no persisted memo or cross-write cache, and no skipped prefix or historical rewrite.

The identical manual probe passed. Exact-only200 write median fell from168.86ms to104.34ms and full audit from275.65ms to153.62ms; reports are archived in indexed-9281885/. These are one-run diagnostics, not statistically controlled device acceptance. A mixed five/ten-year fixture does not establish all-exact ten-year or physical-device latency acceptance.
