# PNMEDBK2 implementation candidate (OFF)

Owner approval: REQUIREMENTS147.J, 2026-10-24. This is a reviewable implementation layout, **not permanent format freeze or enablement**. PNMEDBK1/PNBAK1 remain unchanged. Public entry false, feature0, external review NOT_REVIEWED_EXTERNALLY. No Room/schema/quota change.

## Exact framing

All integers big-endian signed with nonnegative checked bounds; UUIDs are 16 raw bytes. Header remains80 bytes, with magic `PNMEDBK2` and format version2; KDF/suite IDs1, reserved0, salt16, archive UUID16, Argon2id65536KiB/3/1, declared plaintext/ciphertext lengths and reserved0 retain their fixed v1 offsets. v1 parse rejects v2 (and vice versa) before KDF. HKDF labels are `notes.medical.backup.v2/outer` and `/portable-dek`; outer AAD is `notes.medical.backup.outer.v2\0` + exact header. Key-wrap AAD is `notes.medical.backup.dek.v2\0` + archiveUUID + libraryUUID + unchanged object context encoding. Same versionUUID in different libraries never shares a wrapping identity. One-use wrap attempts are scoped by(libraryUUID,versionUUID).

Each outer-authenticated frame is tag:u8 + payloadLength:i64 + payload:

| Tag | Ordered payload |
|---|---|
| 1 | Exact collection index below, once |
| 2 | libraryUUID16 + that library's existing canonical metadata-manifest bytes |
| 3 | same libraryUUID16 + exact existing business JSON bytes |
| 4 | libraryUUID16 + objectUUID16 + versionUUID16 + ciphertextLength:i64 + unchanged inner Tink ciphertext; sorted versionUUID |
| 5 | footer64: commitment32 + libraryCount:i64 + objectCount:i64 + businessAndMetadataRows:i64 + plaintextLength:i64 |

Tags2/3/4 repeat for each library in canonical UUID-string order. Empty retained libraries still have manifest/business frames. Footer commitment is SHA256(`notes.medical.backup.set.v2\0` + header + all preceding plaintext frame bytes). Independent reader rejects unknown/missing/duplicate/extra/reordered/trailing members and authenticates all inner AEAD/hash/EOF, including history, before returning. Neither writer receipt nor reader result activates a dataset.

Index header40 bytes: version:i32=2, collectionUUID16(equal header archive UUID), activeLibraryUUID16, libraryCount:i32(1..8). Each library descriptor165 bytes: libraryUUID16, role:u8(1ACTIVE/2RETAINED), dataset/epoch/logicalVault UUID16 each, businessLength:i64 + SHA32, manifestLength:i64 + SHA32, businessRows:i64, metadataRows:i64, memberCount:i32. Each member72 bytes: objectUUID16, versionUUID16, ciphertextLength:i64, ciphertextSHA32. Exactly one ACTIVE matching activeLibrary; unique sorted library/member IDs, unique object IDs within a library. Cross-library identity is always scoped, never inferred from equal integer IDs or filenames.

Aggregate caps: business JSON32MiB, index + library manifests8MiB (not an extra allowance), total rows100000, versions1000, archive ciphertext512MiB/wire1GiB. Index ≤80000B (maximum defined contents73360B). Per-object16MiB plaintext; local old+new resources still256MiB/512alias, regardless of archive validity.

## Ownership and incomplete integration

Writer accepts caller-owned, already-authenticated production snapshots; wipes password/owned buffers and closes output on every exit, never persists plaintext. Caller must still establish inventory closure before and after exporting. Reader owns input/derived keys/record buffers through a revocable child capability; payload lengths are checked before allocation, exact library hashes/rows/identities must agree with the independently decoded manifest. Its internal source bridge only supplies authenticated ciphertext/DEKs to existing staging, never Compose.

This PR does **not** yet acquire retained snapshots, activate a multi-library generation set or permit restored writes. Existing refusal remains until collection capture, joint durable reservation, complete restore and authority checks are integrated. Tests distinguish component authentication from full business preflight, persistent restore, real process restart and public enablement. Existing source aliases are destroyed only by explicit synthetic test cleanup, not a production deletion API.

## Bounded native resource acceptance

The existing API26/512MiB/192MiB acceptance keeps its original twelve tests and eight restart verifications. A separate fresh instrumentation process now runs its own heap probe plus the three new collection cases; all four must run/pass. Five stage observations include Java usage/ceiling, native allocation, PSS, RSS and process-lifetime HWM. These are not instantaneous overall peaks or evidence for the maximum-size/eight-library restore. Physical devices/external review and collection activation remain separate gates.
