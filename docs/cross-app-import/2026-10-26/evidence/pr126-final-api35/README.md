# PR #126 final SHA API 35 device-test evidence

- Source SHA: `f3db41915d7f6f1b8f0d8bdcc7d74a1fa301c665`
- PR-event run: `40295320498`, job `121479860424`, artifact `device-test-results` (`12638790129`)
- `featherlineImportRunsAsIsolatedLowMemoryFlow`: PASS on the API35 device job; the isolated full SAF/password/authenticated preview/commit/reopen flow completed.
- `realEntryAutomaticallyDetectsBothSourcesCommitsAndReopensHistory`: FAIL. After locating and clicking the Chrysalide mapping row, its RadioButton semantics still reported `Selected=false`; the following assertion failed before commit. This is not a commit/parser failure because the user mapping did not become selected and no Chry record was written.
- This evidence does not establish whether the cause is a Compose event/recomposition issue or a control wiring bug; the app selection interaction needs correction and a fresh device run.
