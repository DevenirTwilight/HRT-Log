# PR #126 API 29 compatibility failure evidence

- Source SHA: `222b4bd6100bcdd29c42ab46a26215d51c898c00`
- PR-event run/job: `40293330403` / `121473313646`
- Artifact: `attachment-poc-api29` (`12637672803`)
- The real app SAF unified import test passed. Both existing MED-02 attachment-operation tests passed.
- Later Room/backup compatibility tests failed because `PublishedBusinessFixture.dump(schema=...)` iterated the schema-20 Chrysalide table for older-schema fixtures. The failure is preserved in `test-results.log` and `TEST.xml`. The fixture now omits that table for schema <16; rerun pending.
