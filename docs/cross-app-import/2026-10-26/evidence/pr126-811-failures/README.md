# PR #126 exact-SHA failure evidence

Source: `8114b9b352791749555da516c48834737e1c1a51`. PR-event Android run `40297811978`; low-memory run `40297811925`.

## API35 real UI test

Artifact `device-test-results` (`12639002541`) is retained in `api35-device-tests/`. The real MainActivity/SAF flow recognized Chrysalide v5 and rendered the explicit medication mapping. The test then touched the mapping row without first scrolling it into the viewport; the selected state remained false at the assertion and the test stopped before the import COMMIT. The target semantics were at `(24,448)-(296,504)` px on the test device. The same artifact reports Featherline's separate API35 authenticated import test PASS. Whether this is only a clipped/off-viewport test target or a production selection callback issue is `ROOT_CAUSE_NOT_PROVEN`; the next candidate scrolls the row into view before physical touch and preserves the selected-state/COMMIT assertions.

## API26 / 512 MiB Featherline low-memory acceptance

Artifact `featherline-api26-512m` (`12638784812`) is retained in `api26-featherline-lowmem/`. Existing limits remained 512 MiB guest and 192 MiB ART heap; the real MainActivity → SAF → wrong-password rejection → correct-password authentication → preview → COMMIT path ran. The `memory.json` checkpoint reached `phase=committed`, but native output was `Process crashed` and did not report `OK (1 test)`, so the whole acceptance is FAIL.

The kernel log identifies the cause as foreground low-memory termination: at guest uptime `459.853657`, lowmemorykiller killed app PID 3869 (`.plainnotes.app`, `adj 0`) to free 230,224 KiB; it also records preceding low-memory kills and a page allocation failure. Android ActivityManager confirms PID 3869 (`net.plainnotes.app`, top foreground) died immediately after the committed checkpoint while the test continued into reopen/verification. No Java `OutOfMemoryError` or `FATAL EXCEPTION` is present. This proves an LMK at the full constrained-device path, not a KDF exception; attribution to retained Argon2 workspace versus overall guest pressure is not independently proven. Memory values are 20ms samples, explicitly not peaks: Java 80,724,352 B, native 20,331,656 B, PSS 220,286 KiB, 925 samples. The `failed to clear the 'main' log` message is housekeeping only and not the cause.

A candidate production change drops references to the already-zeroed Argon2 workspace only after GCM authentication succeeds (wrong-password retries keep the reusable pool); the next exact-source API26 run must prove it avoids this LMK and completes reopen/replay. API26 library low-memory acceptance passed on this SHA. No ordinary feature is enabled by this evidence.
