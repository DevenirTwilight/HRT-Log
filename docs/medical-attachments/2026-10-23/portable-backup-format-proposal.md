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

## Concrete production-format review option 1 (proposal, not frozen)

This is the single suggested production option, not the test candidate's bytes. The executable 2MiB reference uses whole-message AES-GCM and a cheap test KDF; it proves logical refusal/closure only. It does **not** approve this streaming format or its resource cost.

Proposed outer header80 bytes, unsigned big-endian integers, exact reserved zeros:

| Offset | Length | Proposed field |
|---|---:|---|
|0|8|ASCII `PNMEDBK1` (distinct from PNBAK1)|
|8|2|version1|
|10|2|suite1: Tink AES256-GCM-HKDF Streaming,64KiB ciphertext segments|
|12|2|KDF1: Argon2id v1.3|
|14|2|flags0; any unknown bit refuses|
|16|16|fresh random password salt|
|32|16|fresh archive UUID, RFC4122 network byte order|
|48|4|memory65536 KiB|
|52|4|iterations3|
|56|2|parallelism1|
|58|2|reserved0|
|60|8|exact framed plaintext-body length|
|68|8|exact Tink ciphertext-body length|
|76|4|reserved0|

Reader allows only this exact reviewed KDF tuple before deriving anything; no attacker-selected expensive parameters, fallback or normalization. Password UTF8, preserve case/whitespace, reject unpaired surrogates, empty input or >4096 encoded bytes. Argon2id produces32B root; HKDF-SHA256 (salt=archive UUID bytes) derives distinct32B keys with ASCII labels `notes.medical.backup.v1/outer` and `/portable-dek`. Root and transient keys owned/cleared. Outer Tink AAD is domain `notes.medical.backup.outer.v1` plus NUL and all80 header bytes. Every export/retry uses fresh archive UUID/salt and Tink randomness. Per-version portable AES256-GCM envelope: random96bit IV +32B DEK cipher +128bit tag, AAD=domain `notes.medical.backup.dek.v1` plus NUL, archive UUID and the existing immutable content-context encoding. At most1000 DEK wraps per archive; never export device aliases/database/PIN keys.

Encrypted body is uncompressed frames `u8 tag | u64 payloadLength | payload`: exactly one tag1 manifest (max8MiB), one tag2 existing business JSON (max32MiB/100000 rows), sorted tag3 object frames, then one tag4 final-set footer. Manifest has explicit ordered fields and rejects unknown/duplicate keys, noncanonical UUID/integers/UTF8 and noncanonical re-encoding. Tables use trusted versioned allowlists, never archive SQL. Version ordering is unsigned UUID bytes; every required version occurs exactly once. Object payload is objectUUID16 +versionUUID16 +u64 cipherLength +unchanged original Tink ciphertext. No filenames or paths. All document/revision/trash/link states and portable envelopes belong in the encrypted manifest. Footer contains32B SHA256 over domain/header/all earlier complete frame bytes and four u64 counts (frames,objects,structured rows,total framed bytes). Footer hash domain is ASCII `notes.medical.backup.set.v1` plus NUL. Frame count includes the footer (N+3); framed byte count includes its fixed73 bytes and equals the header plaintext length. Hash input excludes the footer itself. Hash is authenticated **by the outer AEAD**, not a substitute for it. Require exact footer, strict authenticated EOF and exact declared physical/body lengths; missing/extra/trailing/duplicate member refuses.

Proposed hard ceilings:1GiB wire bytes,512MiB summed original ciphertext,1000 retained versions, per-object existing16MiB plaintext/16MiB+64KiB ciphertext bounds,8MiB manifest,32MiB structured JSON and100000 ordinary+metadata rows. Caller may impose lower budgets, never trust larger archive declarations. Exact Tink ciphertext-length calculation must be checked against the pinned upstream suite at empty/first/segment/exact-boundary sizes before approval/implementation; do not silently guess its overhead. Proposed64MiB/3-pass KDF and full streaming restore on frozen low-memory API26 remain NOT_RUN, not claims inherited from ordinary PNBAK1.

Staging/activation proposal: a new local dataset/epoch/generation, explicitly mapped typed UUIDs (PURGED maps only to tombstone), fresh device aliases and durable one-use reservations. Stream outer-decrypted **inner ciphertext** into bounded private staging objects; only encrypted data may reach disk. Keep metadata in SQLCipher staging or bounded owned RAM. No visibility until outer authenticated EOF/footer/set membership, all inner object AEAD/hash/length checks, business preflight and typed closure pass. Then a reviewed single native SQL activation publishes records/mappings/generation/pointers/completion. Process death before that retains old current data; no auto-activation. New full-generation proof/trigger changes must be reviewed separately; no Room number allocated here.

Current preparation explicitly refuses retained versions from retired datasets. Production option must either support separate retained/orphan namespaces with honest absent-clinical-fact markers and no guessed relinking, or refuse full export until an approved resolution exists; it may never silently omit them. This multi-namespace choice, durable activation proof, exact canonical bytes/length tests and low-memory KDF evidence are outstanding approval obligations. No public or production-format implementation is authorized by this proposal itself.

Preparation authorization lifecycle: full read-only audit/encoding and private record consumers must not hold the write-COMMIT publication monitor. Read in one SQL snapshot, check/fail closed before and after, register owned buffers for prompt revocation, and withhold final results until all authentication/current-scope checks pass. No private staging consumer may publish before the final permission check. This is not permission to interrupt or weaken an actual write COMMIT.
