# Synthetic native diagnostic transport

TEST_ONLY. This protocol is generated only into the Android instrumentation source set. The application, inventory writers and user devices do not invoke it. The host runner first requires an API26 QEMU emulator serial and a fresh run nonce. No official product distribution, database export, backup upload or health fixture is involved.

## Phases

1. **Transport preflight, before SQLCipher or the large fixture.** The test writes the eight bytes `00 01 7f 80 ff 0a 0d 00` and nonce metadata into its own internal `files/ansm-performance/` directory. The host acts only after the matching preflight log marker. It uses `run-as` for either of the two explicitly permitted test packages, requires one matching location, verifies every probe byte and writes the nonce to the App-owned acknowledgement using `tee`. The App independently reads that acknowledgement. No acknowledgement within15 seconds stops at `TRANSPORT_PREFLIGHT_FAILED`, with performance `NOT_RUN`.
2. **Original acceptance phases.** The workload, database encryption, runtime configuration, heap/disk sampler, complete audits and existing budgets remain unchanged. Once preflight is acknowledged, the host only tails its own log; it makes no adb queries until the post-budget marker.
3. **Post-budget diagnostics.** Original metrics and budget failures are frozen first. Read-only component probes and sampled full-audit projection equality follow. The test records the completed trace length and nonce, then logs the ready marker. The host validates metadata against that exact run, reads the bounded method trace and saves its SHA256 and parser outcome before acknowledgement. The App allows at most30 seconds for this final acknowledgement. Its original budget assertion still executes and can fail.
4. **Cleanup and artifact collection.** AGP may uninstall the test APK and delete its files. The host already has any acknowledged trace. The collector recovers only the nonce-matching compact report from its captured log and checks the cached trace length/hash. Missing, truncated or mismatched evidence remains unavailable; it cannot become budget success.

## Supported reads and writes

Internal paths are restricted to the named synthetic transport files below `/data/user/0/net.plainnotes.app.data.test/files/ansm-performance/` or the explicitly supported alternate test package `net.plainnotes.app.data`. `run-as` executes as that debug package. There is no shell interpolation or arbitrary package/path lookup. Writes are restricted to the two acknowledgement files.

API26 actually rejected the previous `head -c` assumption: native run39109323920 returned `head: not integer: c`. Its artifact preserves this response and the preflight failure. Earlier runs lacked per-path stderr and retain that evidence limitation.

The replacement uses `dd bs=<byte limit> count=1` through `adb shell -T`: no terminal translation, with remote stderr separated from binary stdout. In particular, dd's transfer statistics must not enter the payload. `exec-out` is not used for these new binary reads.

| Payload | Hard read bound | Acceptance |
|---|---|---|
| Metadata JSON | 65537 bytes | Reject over65536 bytes or malformed/current-nonce mismatch |
| Binary transport probe | 9 bytes | Exactly the eight specified bytes |
| Method trace | Reported length +1 byte, at most32MiB +1 | Preflight size18..32MiB; exact read length and SHA256 |
| Acknowledgement | Canonical36-character nonce | Host echo and independent App file read must match |

Only fixed synthetic stderr is retained, bounded to256 bytes per failed location. No raw database, backup, APK, argument values or medical records are collected. ART samples contain code identities, thread names and sampled timing events; sampled segments are not invocation counts. Unsupported traces preserve raw bytes/hash with `UNAVAILABLE`; overflow or incomplete stacks remain explicitly partial.

## Evidence boundary

Actual run39109780636 at0b3c703 passed the eight-byte transport preflight and final sample acknowledgement. The official artifact12129718330 SHA256/ZIP CRC were verified. The2310261-byte trace and deterministic gzip, raw hash, parser summary and transport proof are archived in `native-profile-0b3c703/`. Re-running the parser on the archived bytes reproduces its JSON exactly.

The profile is **PARTIAL_DIAGNOSTIC**:158040 records,152 clipped boundary frames,zero unknown methods/unmatched exits and no overflow. Sampled segments are not invocation counts; recursive inclusive times must not be added as flat cost. SQLCipher cursor reads and Android26 CharSequence range append were the largest exclusive sampled CPU costs. The common whole-unescaped-string append candidate therefore retains the entire original scalar scan and uses the String overload only after validation; escaped ranges keep the old path. Local data415, Period5 and Privacy3 pass, including frozen canonical byte-oracle and new shared cross-API boundary cases. Native candidate results remain pending.

**Original performance remains FAIL:** audit15569ms, forecast16150ms, three actual writes15939/15672/15662ms. Passing transport does not establish the5-second write budget, physical hardware acceptance or release approval. P4_BLOCKED, formal App write entry OFF; P5/P6 not implemented.
