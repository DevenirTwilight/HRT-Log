# Default-OFF storage: implementation and compatibility boundary

Owner decision, 2026-10-22: “批准默认关闭的实现，外部审核在启用前完成”.
Baseline d2223c0a, Room13, supported record-backup business schema through13.
External review: NOT_REVIEWED_EXTERNALLY, required before activation.

## Implement now, without publishing an attachment

Use pinned Tink1.23 public StreamingAead/key APIs, AES256-GCM-HKDF-SHA256,
65536-byte ciphertext segments. New immutable version => new random32-byte DEK;
no partial resume or re-encryption under an old attempt. Preserve independent
legacy POC/oracles; never promote test markers. Add a production internal stream
adapter, a fully authenticated same-channel reader and AndroidKeystore adapter.
No importer, application DI, picker, permission, metadata commit or feature flag
activation is added here. Staged receipts are NOT a successful database save.

Content AAD revision1: ASCII `notes.attachment.object` then zero, u16BE protocol1,
u16BE suite1, five network-order16-byte UUIDs: logicalVault, object, version,
attempt, DEKhandle. No localDataset/recordEpoch/physical path in content AAD;
cross-device recovery must preserve these crypto identities. Format admission
and MIME are separate metadata, not inferred from successful AEAD. No custom
ciphertext header: use upstream Tink wire bytes. Unknown revisions fail closed.

Device wrapping follows candidateA stage/active separate Keystore AES256-GCM
aliases, generated ONLY by explicit provisioning. Alias is neutral and derived
from role and random identity, never patient/title/PIN. Envelope uses12-byte IV,
32-byte DEK ciphertext and16-byte tag. AAD binds content context, stage/active,
localDataset, physicalGeneration, operation and positive wrapEpoch. Missing alias,
wrong role/scope, malformed envelope and auth failure refuse; unwrap NEVER creates
or replaces a key. Database passphrase and portable-backup roles are excluded.
Raw owned DEK arrays are cleared. Tink/JCA copies/native memory cannot be promised
instantaneously erased. Future durable journal must reserve wrap usage before
calling this adapter; no durable budget claim is made by a crypto primitive.

Stream writer owns and closes transferred source/sink on all outcomes; explicit
limits, bounded buffers, plaintext/ciphertext SHA and exact counts. Failed or
cancelled output is not published. Reader verifies trusted metadata commitments,
full AEAD to strict EOF and exact length BEFORE exposing any read lease. Same
pinned channel is used thereafter. Bounded reads verify capability before/after
IO and clear rejected output. This does not replace immutable filesystem ownership,
format admission, native parser isolation or SQL commit authorization.

## Migration proposal — DESIGN_ONLY, no version reserved

Do not occupy Room14 while other work may advance baseline. Review latest schema
before a later migration PR. Existing8 metadata tables cannot express durable
filesystem ownership or complete provenance. Proposed additive journal must bind
operation/idempotency, dataset+epoch, generation, document expected revision,
crypto identities, verified manifest, staging/final relative UUID path, parent
and file device/inode evidence, phase, reservation and wrapping role/usage epoch.
No absolute/provider URI, plaintext path or raw DEK is persisted. Ownership must
be established using pinned private dirFD, O_NOFOLLOW/no-replace creation and
file+directory fsync. Recovery deletes only a proved-owned unreferenced object;
unknown ownership quarantines. Rename is not SQL commit. Active pointer and
operation completion CAS in the same authorized native transaction, rollback
leaves old active version. On restart recover journal before allowing mutation.

Old rows remain unknown/retired; no guessing current integer IDs. Fresh record
restore => fresh dataset/recordEpoch, old links quarantined, crypto context not
rewritten. Full historical migration chain, schema identity, FK guards, process
kill after each FS/SQL seam and same-ID-in-new-dataset tests are required. Failed
migration preserves old DB; never destructive fallback. Older apps refuse a newer
schema. Feature remains0; VERIFIED transition guard is not relaxed by this slice.

## Complete backup proposal — DESIGN_ONLY, independent future format

PNBAK1 and existing business schema/limits/KDF/crypto remain byte-compatible and
records-only. It must not claim to export attachment originals or device keys.
A complete portable medical archive needs an independently reviewed versioned
format, authenticated whole-set manifest, bounded encrypted staging, portable
wrapped DEKs, all retained versions/trash/link identities and exact per-object
commitments. Source device envelopes are not target-available keys. Restore into
fresh physical generation/dataset/epoch, preserve logical crypto IDs, authenticate
ALL objects and relational semantics before atomic activation. New device wraps
use explicitly provisioned target aliases, never DB key. Rollback leaves old DB,
objects and envelopes intact; no plaintext temp file. Unknown version/size/key,
missing object and cancellation fail closed. Reject incomplete sets; no guessing.

Before implementation: freeze archive/KDF/budget/version policy, old-app refusal,
record-only restore UX and deletion/retention policy; approve compatibility design.
Then validate Unicode/binary/limit fixtures, wrong passwords, corruption, missing
keys, cross-device recovery, duplicate/foreign identities, ENOSPC, interruption,
rollback and independent oracle. External review and real hardware/power-loss
remain activation gates, not falsely replaced by synthetic emulator success.
