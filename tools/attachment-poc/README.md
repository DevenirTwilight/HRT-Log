# MED-02A synthetic attachment POC

This is **not a production attachment format, database, backup, or security certification**.
The application has only an `androidTestImplementation` dependency. No production UI, storage,
schema, PNBAK1, application keys or real clinical bytes are changed.

Run `./gradlew -PjvmOnly :attachment-poc:test`. Android tests under
`app/src/androidTest/java/net/plainnotes/app/attachmentpoc` run public ProxyFD/PdfRenderer,
image decoding, real synthetic Keystore aliases, the same cryptographic contract, and
an external **live-import force-stop** experiment. The full API35 suite remains intact;
separate API26/29 jobs run only this POC package. Physical ARM is not a claimed benchmark.

`SYNPOC01` uses JCA AES-256-GCM: a new random 256-bit DEK and 64-bit nonce prefix for every
attempt, 32-bit block indices (0..16383 maximum), and reserved `0xffffffff` for the final
manifest. Header (68 bytes) binds store/object/version UUID, block size and prefix to every
AAD; each record is index, length, ciphertext and 128-bit tag. Footer (68 bytes) authenticates
total plaintext length, count and SHA-256. Unknown-length input is streamed; limits count
actual bytes. A reader authenticates the footer, exact physical size, every block and ordered
full-content hash before opening; random reads reauthenticate each involved block. No output
is returned on tag failure, including a partially filled multi-block response. Files are
immutable; a retry uses a fresh key/prefix and CREATE_NEW ciphertext, never nonce-reusing
overwrite. No complete plaintext file is created.

The portable envelope uses fixed-budget PBKDF2-HMAC-SHA256 and GCM **only as an experiment**;
it does not select a production KDF or modify PNBAK1 Argon2id. Device wrapping lets the
provider choose encryption IVs so Android Keystore randomized-encryption rules are honored.
The recovery class is explicitly an in-memory model, not proof of SQLCipher/filesystem
atomicity. Proposed Room10/full-medical-backup work remains subject to approval.

Threat boundaries: app lock is modeled by a revocable test viewer, not wired to a production
attachment page. Disk observations cover new app-owned files/cache on synthetic emulators,
not forensic proof of system/provider storage. Java/Keystore/native/kernel RAM erasure is
best effort. Root/malicious OS, active device compromise, old valid ciphertext replay,
PDF parser vulnerabilities, malicious SAF providers and externally exported copies need
their own controls/review. File fsync + rename is exercised; directory fsync/DB journal/
power-loss recovery require the future architecture. Injected ENOSPC is a deterministic
failure seam, not deliberate device-disk exhaustion.

See reports [30](../../docs/audits/product-quality-2026-10/30-med02a-attachment-poc.md)
and [31](../../docs/audits/product-quality-2026-10/31-med02b-minimum-implementation-plan.md).

## MED-02B2 / B2-GATE (NON_PRODUCTION)

`src/contractTest` now evaluates Tink1.23 Streaming AEAD with full pinned-FD authentication
before a revocable random-read lease, input/cancel/failure IO and explicitly in-memory CAS
models. JVM `testImplementation` / app `androidTestImplementation` only. Never app main
writer/reader, clinical store, formal container/key policy or full backup. Synthetic graph
models remain under attachment-design-validation. Reports39–42 distinguish actual crypto,
file IO, memory/graph models, inherited Room/SQLCipher tests and NOT_RUN hardware gates.
Release payload guard: `python3 scripts/check_attachment_release_isolation.py` after clean
fullRelease; known POC magic/namespaces/providers/Tink cannot enter production DEX/manifest.
