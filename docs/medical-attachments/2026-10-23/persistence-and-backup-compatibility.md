# MED-02 local persistence and portable-backup compatibility

Authorization: REQUIREMENTS §145 (2026-10-23). Baseline #104 source 663901f9,
default branch d2223c0a at implementation start. No competing open Room migration
was found. External review remains **NOT_REVIEWED_EXTERNALLY**. User entry OFF.
Native acceptance status belongs in HANDOFF; implemented is not synonymous with passed.

## Local protocol implemented, not another crypto implementation

Reuse AttachmentSession, AttachmentInputStream, ObjectStreams, DeviceKeys and
AttachmentTransaction. Add one Room14 table `attachment_journal`; do not alter
medical/business columns, PNBAK1, SQLCipher or Tink wire bytes. Room1→…→13→14
is the supported additive chain. A reader declaring Room13 refuses Room14;
there is no downgrade or destructive fallback. Older backup business versions
remain supported independently; the maximum remains13, ordinary user-only export12.

Journal fields: dataset/operation composite key; record epoch and typed record UUID;
physical generation; document UUID; canonical complete request SHA256; immutable
object/version/attempt/DEK-handle UUIDs; two neutral device-alias UUIDs; provisioning
ready flags and monotonic consumed-use flags; proof state; parent/file device and
inode; full authenticated ciphertext/plaintext counts and SHA256. The existing
operation row holds idempotency UUID, expected generation/revision, reservations,
staging envelope and phase. Positive wrapping epoch is1 for this local protocol;
each fresh stage/active alias has **one allowed encryption invocation**, never a
reusable counter inferred after a crash. The active envelope persists epoch1.
No raw key, provider URI, absolute patient path or plaintext temporary file.

| Step | Durable fact / allowed effect | Interrupted outcome |
| --- | --- | --- |
| PREPARE | Complete request commitment, fresh attempt/crypto IDs and both alias identities in native SQL; no original visible | No alias adopted by name; retry of incomplete UUID refused |
| Provision | Explicit new Keystore aliases; READY committed only after provisioning | Unknown generation outcome retained; never recreate/adopt |
| Reserve | Each use goes0→1 in FULL-synchronous native SQL **before** wrap | Consumed use never refunded, even if Cipher was not reached |
| STAGE | Stage envelope recorded; O_EXCL/O_NOFOLLOW private object; exact owned FD identity committed; bounded Tink ciphertext only | Partial/unknown ciphertext retained, never published |
| AUTHENTICATE | Cipher file fsync, chmod0400, close writable FDs; same owned read-only FD full AEAD/EOF and both hashes; active envelope wrap+unwrap verified | Authenticated receipt alone is not a save |
| COMMIT | Capability encloses actual SQL COMMIT: typed-record/current scope/generation check, document revision CAS, immutable version, active envelope, link, pointer and operation COMMITTED | Any SQL failure rolls back all publication; old pointer and links survive |
| RECOVER | Exclusive writer lock; all incomplete journals→QUARANTINED and operations→ABORTED, including retired epochs | No auto-commit, encryption resume, alias resurrection or file deletion; repeat is a no-op |

Success is returned only after transaction commit. A response lost after COMMIT is
resolved by same UUID/same request returning the original committed receipt; it
does not open input or wrap again. A different request under that UUID refuses.
The request includes scope, operation, document, typed record, expected revision,
title/category/MIME and expected byte count/hash. Actual stream must match it.
The future importer must obtain that bounded identity safely, not spool plaintext.

The existing B1 physical generation is a **STAGING namespace**, not a validated
whole-library checkpoint. This slice never fabricates its manifest, activates it
or relaxes the generation VALIDATED guard. Per-object journal evidence permits
the document's version pointer; the store's active-generation pointer remains
unchanged. A future complete-set restore/activation needs separate reviewed
generation semantics. Legacy versions have no journal/attempt and stay UNKNOWN;
their stored STAGED/MISSING/etc labels and hashes are not rewritten or promoted.

The old blanket VERIFIED ban is replaced only for matching authenticated journal
and operation evidence, exact immutable identities/counts/hashes and protocol1.
All original value, history, feature0, link and CAS protections remain. This is a
trusted encrypted-metadata protocol, not a claim that SQL triggers perform AEAD.
Only the coordinator proves bytes; raw database tampering with all evidence or
an attacker controlling the app UID/kernel is outside that trust boundary.

## Files, cancellation and recovery limits

Files live under Context.noBackupFilesDir/attachment-objects-v1, separated from
records-only backup and containing only UUID leaves. Pin a private no-follow
directory FD; `/proc/self/fd/<held-fd>/<UUID>` anchors every open/create to that
same inode without hidden Android APIs/JNI. Check UID/type/private mode, parent
dev/inode, file dev/inode, regular type and link count1. O_EXCL forbids replacement;
fsync file/directory and containing directory. Kernel FileLock excludes live
coordinators/recovery across processes; busy fails closed. No move-to-publish:
SQLite pointer visibility avoids a rename/SQL atomicity gap. Existing objects
are never opened writable by the production protocol. Read authenticates the
whole object on the pinned FD before returning a bounded capability lease.

Lock/revocation closes registered crypto resources, prevents late output and
actual commit. A fresh capability/current dataset is required for recovery.
Dataset switch changes identity; old namespaces/links do not match integer IDs.
Cancellation after a durable commit cannot undo it; reconcile by UUID.
Ownership, key or authentication failure returns no plaintext and marks the
retained version CORRUPT when authority remains valid. Revoked authority cannot
write that status. This preserves the immutable original manifest/history.

**Deliberate minimum:** recovery never unlinks any ciphertext, even with inode
evidence. Interrupted, unknown and damaged objects are logically quarantined,
not renamed/deleted. Unknown unregistered leaves are never opened by search or
recovery. This avoids inode reuse and unlink-TOCTOU guesses. Retained bytes and
two Keystore aliases per attempt accumulate; reservations are not reclaimed.
Automatic garbage collection, global quota/retention UX and key consolidation
remain activation work, not a hidden claim of completed cleanup. Logical trash
retains all versions; restore authenticates the retained current object and CASes
the state; quarantined clinical links do not silently reactivate.

## Complete portable medical backup: reviewable proposal, NOT_IMPLEMENTED

PNBAK1 remains records-only. The following is a logical model, **not approved
magic bytes, serialization, KDF/cipher suite, nonce scheme or a new outer header**.
Do not copy device Keystore keys or reuse record-database/PIN keys.

| Logical record | Proposed required fields |
| --- | --- |
| Archive descriptor | Independent format/version, archive UUID, creation timestamp, logical vault IDs, reviewed suite/KDF identifiers and bounded parameters; authenticated by final set commitment |
| Structured dataset | Versioned business schema, typed immutable record UUIDs, portable record facts; source integer IDs only as local mapping hints |
| Documents | UUID, revision, title/category, state/deletion time, active version UUID; all retained versions, including trash and superseded versions |
| Version manifest | Logical vault/object/version/attempt/DEK-handle, protocol/suite, MIME, ordinal, exact plain/cipher lengths and SHA256; opaque relative archive object ID |
| Relationship | Link UUID, typed record UUID/kind, document UUID, revision/state; quarantined/retracted relationships retained |
| Portable envelope | DEK handle/version identity, independently reviewed password-derived portable wrapping hierarchy, role/epoch and bound archive/object identity; never source device alias/key |
| Whole-set manifest | Canonical versioned ordering, exact document/version/link/tombstone/envelope/object membership and counts/lengths/digests; authenticated final commitment; missing/extra/duplicate member rejects |

Export state machine: SNAPSHOT → RESERVE → EXPORT_ENCRYPTED_OBJECTS → AUTHENTICATE_SET
→ FINALIZE_OUTPUT. Snapshot all retained versions, trash, links and tombstones in
one consistent metadata view; pin immutable source objects until export finishes.
An export racing a purge must refuse or retain those objects, not silently omit
them. Fail on unavailable/corrupt retained originals; records-only export remains
an explicit separate action. Bounded temporary output is encrypted; incomplete
output never claims a finished archive. Whole-set authentication binds descriptor,
metadata, portable envelopes and every object, preventing omission/substitution.

Restore state machine: INSPECT_BOUNDED_HEADER → UNLOCK_PORTABLE_KEYS →
STAGE_NEW_NAMESPACE → VERIFY_ALL_OBJECTS_AND_SET → VERIFY_RECORD_UUID_MAP →
PREPARE_NEW_DEVICE_ENVELOPES → ATOMIC_ACTIVATION → RETAIN_OLD_NAMESPACE.
Allocate new local dataset, record epoch, physical generation and fresh device
aliases/use reservations. Preserve logical vault/content crypto IDs needed by AAD;
never mutate source ciphertext context just to reuse old integer IDs. Authenticate
every retained object and exact set, relational closure, limits, active pointers
and tombstones before activation. Build explicit typed UUID→new integer-ID mapping;
collisions or absent targets quarantine/refuse rather than attach to an existing
patient record with the same integer ID. New-device envelopes bind the new local
namespace. Atomic activation publishes the new structured dataset, generation,
links and completion marker together; until then existing data remains current.

Wrong password/envelope failure: reject without exposing data or overwriting old
state. Corruption, missing/extra object, duplicate UUID, count mismatch or unknown
format: refuse the entire set. Space shortage/cancel/lock/crash: abort staging,
retain old dataset, quarantine only the new operation; never guess-delete an
unknown file. Target free-space/reservation and maximum archive/row/object/KDF
budgets must be agreed before format implementation, not read unbounded from
untrusted headers. Old namespaces/keys remain until independently proved safe
retention/purge. Recovery is idempotent and never auto-activates a partial set.

| Input / reader | Current application | Future complete-backup reader | Older application |
| --- | --- | --- | --- |
| Supported PNBAK1 records-only | Existing path unchanged, fresh dataset/epoch; old attachment links quarantined | Same explicit records-only semantics; originals not claimed | Existing version gates apply |
| Proposed full archive | Unsupported; never route through PNBAK1 | After separate format review: verify whole set and fresh local wrapping | Refuse unknown format; no partial record extraction |
| Room13 user DB | Additive14; old attachment evidence UNKNOWN | Later explicit migration, no invented proof | Existing13 app can still open the unupgraded DB |
| Room14 user DB | Journal-aware, feature OFF | Supported through reviewed migrations | Refuse downgrade; never destructive fallback |
| Source device envelopes only | Cannot restore missing device key | Portable envelope required; source alias is not a recovery mechanism | No new guarantee |

Required acceptance before that format is frozen: independent deterministic
cross-device round trip of all retained versions/trash/links; wrong password;
one-bit damage in header/metadata/envelope/every object/final commitment; omitted,
extra, duplicate/reordered members; bounds/KDF abuse; every export/restore crash
seam; ENOSPC/cancellation; failed activation rollback; old PNBAK1 unchanged;
integer-ID reuse across datasets; lost source Keystore with valid portable
envelopes; no source keys/URIs or plaintext temp files in archive/logs. API26/29/35
and independent security review are separate gates. No portable format or exporter
is implemented by the local journal PR.
