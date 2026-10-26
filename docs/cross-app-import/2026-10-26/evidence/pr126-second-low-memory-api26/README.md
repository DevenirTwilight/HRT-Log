# PR #126 API 26 / 512 MiB low-memory failure evidence

- Source SHA: `222b4bd6100bcdd29c42ab46a26215d51c898c00`
- PR-event run: `40293330438`, job `121473303455`
- Artifact: `featherline-api26-512m` (`12637821963`)
- Result: FAIL; instrumentation reports `Process crashed`.
- The kernel log records `lowmemorykiller: Killing '.plainnotes.app' ... adj 0` at 08:46:26.299, after several cached processes had already been killed. This is an observed foreground LMK, not an inferred Java exception. `logcat-clear.txt` reports a failed diagnostic log clear; that message is separate and not the cause of this run's failure.
- The last saved memory sample is explicitly pre-KDF (`before-wrong-password-kdf`): 192 MiB heap cap, Java observed 18,429,208 bytes, native observed 22,494,944 bytes, PSS 159,819 KiB. It is not a KDF peak measurement. The kernel evidence proves device-level memory pressure and process death; it does not by itself prove the KDF allocation alone caused the pressure.
- In this run, the same instrumentation test had already traversed HRT Tracker, Trans Memo, Mona and Chrysalide flows before starting the Featherline KDF sequence. The PR change isolates Featherline into a selectable test method and makes the low-memory script run only that full real SAF/password/preview/commit/reopen flow, retaining the 512 MiB / 192 MiB limits and original fixture.
- The isolation correction is a hypothesis about accumulated test-process pressure and remains unverified until a new exact-SHA low-memory run. This failure remains FAIL; no pass is inferred.
