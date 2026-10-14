# Synthetic attachment design validation

NOT PRODUCTION: no application source set, registered Room migration, actual attachment/backup
serializer, real clinical data or external professional security certification.

- `room10_draft.sql` / `test_room10_draft.py`: synthetic stdlib SQLite over checked-in Schema9
  fixtures. Local metadata dataset scope is distinct from crypto logical vault and physical
  generation. Does not execute Room/SQLCipher/onOpen or prove filesystem durability.
- `backup_state_model.py` / its tests: deterministic crash/restore model with explicitly assumed
  atomic durable DB state and an authentication input flag. It does not authenticate a package.
- `draft_vectors.json`: public pinned Wycheproof cases and new unapproved HKDF/AES-GCM
  transcript fixtures. JSON input is synthetic; fixed keys MUST NOT be used for real data.
- `generate_draft_vectors.py`: optional local Python `cryptography==50.0.0` generator.
  It is not a general file encryptor; accepts only the SHA-verified upstream JSON.
- JVM `ProductionDraftVectorsTest`: independent JCA interoperability/RFC5869/RFC9106/public
  positive+negative vectors; dependencies are test-only, not added to application runtime.

Run CI-compatible stdlib checks:

```
python3 -m unittest discover -s scripts -p test_attachment_design_validation.py
./gradlew -PjvmOnly :attachment-poc:test
```

The 3 POC audit evidence tests intentionally reproduce publication/revocation/rollback
boundaries; their success records findings and DOES NOT mean the accepted POC was fixed.
Android filesystem experiments use synthetic emulator-only files: hardlinks actually denied,
exclusive creation cannot overwrite existing targets, directory fsync succeeds, leaf symlinks
are rejected. No hardware power-cut or ancestor-directory race proof.

Public vector attribution/license: repository THIRD_PARTY_NOTICES, WYCHEPROOF-LICENSE,
and report33. Production threat model, protocol/migration/backup/policies/approval gates:
reports32–37 in docs/audits/product-quality-2026-10/. B1–B5 default-disabled until required
security and complete backup/recovery acceptance, with explicit owner approval.
