# PR #126 Android unit-test failure evidence

- Source SHA: `222b4bd6100bcdd29c42ab46a26215d51c898c00`
- PR-event run: `40293330403`, Android job `121473313604`
- Artifact: `build-results` (`12637552586`)
- `:core:data:testDebugUnitTest`: 654 tests, 8 failures.
- Failure mechanism: the test-only `PublishedBusinessFixture.dump(..., schema=N)` iterated `DOMAIN_TABLES` but did not omit the new `chrysalide_import_item` table for schema versions <16. This caused pre-v16 fixtures to emit a table they could not represent, or query that table in Room 9 fixtures. Consequences included old Room migration/backup tests failing, schema-11 rejection, and one whole-document byte mismatch. The failures share the same stale test-fixture version gate; they do not indicate a production migration failure.
- Corrective change adds the missing schema-16 omission to the fixture. It is a test-fixture compatibility fix; current-source rerun is pending.
