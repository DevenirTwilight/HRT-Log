# Portable medical backup: concrete review candidate, NOT_APPROVED

Source baseline01f45fb8; owner task147 (recorded on independent storage PR-A). This PR prepares production snapshot/membership/typed identity components and a TEST_ONLY reference candidate. No app export/import route, final magic bytes, persistent format or activation. PNBAK1 remains records-only and unchanged. Permanent deletion remains OFF.

## Proposed reviewed boundary

A distinct new outer magic/version must cause old applications to refuse, never delegate an unknown archive to PNBAK1. Bounded fixed-size inspectable header: version/suite/KDF identifiers, random salt, bounded Argon2id parameters, declared maximum body size. Exact wire encoding and numerical KDF/resource budgets require owner approval before freeze. Mature Argon2id and Tink Streaming AEAD are candidates, not new primitives. Review password normalization, KDF denial-of-service bounds, key domains, segment format and error oracle before protocol acceptance. No reuse of database/PIN/device keys.

Authenticated body binds header, archive ID, complete structured business JSON/version, typed stable record identities, document titles/categories/revisions/trash, all retained immutable versions/content contexts/lengths/digests, all links and states, portable DEK envelopes, and exact sorted whole-set membership. Missing/extra/duplicate members refuse. Include superseded and trash versions; damaged/unavailable required originals fail the whole operation. Local integers are mapping hints only, never record identity. Source Keystore aliases and device envelopes are not portable recovery keys.

Proposal: derive domain-separated archive wrapping/manifest keys from a password hierarchy; portable envelopes wrap original content DEKs under archive-bound identity. Copy immutable Tink ciphertext/context without changing its clinical content identity. Authenticate every object and full set before accepting an archive, then generate fresh local dataset/epoch/generation, fresh device aliases with durable one-use reservations and new local envelopes. Preserve logical content identities; stable typed UUID map resolves links to explicitly mapped new rows, not reused integers.

Activation must publish structured rows, typed mappings, metadata, pointers, generation and completion atomically after all authenticated objects exist in a new namespace. Keep the old dataset/keys/files on any failure, cancellation or process death. No automatic partial activation, plaintext temporary file or guess-delete. Existing generation STAGING is not a full-library VALIDATED checkpoint; a separately reviewed activation/migration contract is still required. This PR implements no schema change, restore SQL or portable production cipher.

## Executable preparation scope

A consistent current-dataset snapshot captures existing business JSON using its bounded encoder and independent full audit, plus metadata/typed identities and every retained committed version. Per-version authentication reuses production held-FD/Tink/device-key mechanisms. Refuse unsupported/unknown retained namespaces or incomplete required evidence rather than silently omitting it. Limits are explicit and configurable; in-memory preparation is not a large-archive streaming exporter or low-memory acceptance claim.

The test-only candidate demonstrates password rejection, full-set corruption/membership rejection and isolated fresh-device identity/key round trip with all versions/trash/links. It is an oracle for set invariants, not approved bytes, production restore, real power-loss or external security review. Candidate state stays untouched until all checks succeed; failed activation leaves its sentinel. Production native snapshot tests and candidate tests are reported separately.

## Approval requested before production format work

Decide exact header/suite/KDF/normalization, whole-set canonical bytes, portable envelope domains, archive/object/row/KDF budgets, staging capacity policy, multi-retired-dataset policy, generation activation/rollback and downgrade refusal. Review full-list preservation (including quarantined/orphan states) and business-schema compatibility. Current application cannot restore a full medical archive; PNBAK1 support must remain independently tested.
