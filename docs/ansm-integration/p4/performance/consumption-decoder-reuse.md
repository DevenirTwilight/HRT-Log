# Call-local consumption decoding reuse

2026-10-18. Candidate only; P4_BLOCKED, formal App entry OFF.

Native baseline65cf08e / run39069126496 completed with all six ordinary CI checks green but four unchanged performance limits exceeded. On its API26 SQLCipher emulator (RAM520962048B, max heap201326592B), operation auditing took7563ms and per-container projection6295ms. Full audit17198ms and real post-restore writes18599/18722/19032ms failed15s/5s budgets. Official artifact digest/CRC and all metrics are preserved in `native-65cf08e-*`.

Both hot audit paths parsed a consumption envelope/request and then called the receipt decoder, which parsed and validated the same envelope/request again. The receipt decoder also canonicalized the same request again solely to compute its digest.

The candidate returns the validated envelope, immutable request DTO and receipt together inside one private call-local value. The two callers use that value instead of parsing the row again. Digest comparison uses the exact immutable canonical text already validated by `MeasuredUsePayload.read`.

All original checks remain: envelope fields/format/canonical bytes, chain structure, request fields/version/algorithms and digest, row kind/dataset/record/revision, capture count/keys/identities/state/head/quanta, and exact requested/allocated/unallocated quantity checks. Full dataset, reference graph, ledger prefix, revision and inverse audits are unchanged. The private envelope is used read-only within that call. No parsed JSON is cached across rows, audits, transactions, writes or datasets. Nothing is persisted or exposed as a public API; schemas, backups, source bytes, hashes, allocation rules, PK and UI activation are unchanged.

Before changing production code, two shared tests actually passed against the old implementation:39 valid fixtures, alternating complete payloads with the same operation identity, and33 malformed fixtures explicitly required to fail. The frozen pre-refactor decoder body is retained in `ConsumptionReceiptOracle`; its supporting helpers are unchanged by this candidate. Explicit half-allocation, UNKNOWN and ALREADY_ACCOUNTED expectations supplement equality checks. These tests also compile into the existing native contract suites. Final regression and native results must be recorded separately; host improvement cannot establish native admission.

An additional post-budget test-only12,000-token comparison measures Android decimal-regex compilation versus pattern reuse. It does not modify the production regex or predict whole-audit speed from a microbenchmark. Original timed phases, fixture, sampling scope, budgets and failure assertion remain intact.
