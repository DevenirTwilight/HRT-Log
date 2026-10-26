# PR #126 isolated Featherline API 26 low-memory pass

- Source SHA: `f3db41915d7f6f1b8f0d8bdcc7d74a1fa301c665`
- PR-event workflow/job: `40295320484` / `featherline-api26-512m`
- Artifact: `featherline-api26-512m` (`12638431014`)
- Result: `AutomaticImportAndroidTest.featherlineImportRunsAsIsolatedLowMemoryFlow` PASS (`OK (1 test)`).
- Device: API26; `meminfo.txt` confirms the unchanged 512 MiB guest; `memory.json` confirms the unchanged 192 MiB heap cap.
- Full real SAF selection, wrong-password rejection, retry with correct password, preview, commit, app restart, re-open and replay check completed. Existing KDF, ciphertext/sample size and input are unchanged.
- Sampled memory is explicitly not peak memory: 20 ms interval, 933 samples; maximum observed Java bytes 86,341,944, native 21,387,216, PSS 227,658 KiB. Do not interpret these sampled values as peak bounds.
- `logcat-clear.txt` retains a diagnostic `failed to clear the 'main' log` message; it did not block the test and the test/result assertions passed.
