# Next persistence slice: concrete review proposal (not implemented)

Room13 is the observed baseline. No next version number is reserved. PNBAK1 and
record-backup business versions through13 remain unchanged. This proposal is not
a migration or a feature-enable authorization. Default-OFF adapters are PR104.

## Why existing tables are insufficient

`attachment_version` lacks the attempt UUID used in content AAD and a suite ID.
`attachment_operation` has object/version/generation and a staged envelope, but
no durable inode/parent ownership or device-wrap usage reservation. Inferring
ownership from a matching filename or adding it to an unrelated free-text field
would be unsafe. An unfinished import cannot insert a final immutable version
with invented hashes and later edit them. IO must not hold a long SQL transaction.

## Candidate additive changes for a later reviewed migration

1. `attachment_version`: nullable `content_suite INTEGER` and `attempt_uuid TEXT`.
   Existing rows remain NULL/unknown. New protocol1 selection requires suite1 and
   valid attempt UUID. Do not invent old attempt IDs or accept old rows as verified.
2. `attachment_key_epoch`: composite scope+key-epoch UUID primary key; neutral
   unique alias; stage/active role; physical generation; provision state;
   positive wrapping epoch; monotonic reserved-use count; created operation.
   FK to local store/generation. Retired rows/usage are never decremented or
   reset after failed wraps. Existing aliases are not adopted by name.
3. `attachment_owned_object`: composite scope+operation+attempt primary key;
   FK to existing operation and physical generation; immutable logical vault,
   object/version/DEKhandle identities; document expected revision; role/key-epoch
   references; relative UUID-only leaf; pinned parent dev/inode and created file
   dev/inode; lifecycle; optional final manifest commitments until authenticated;
   reservation and cleanup reason. Unique scope+object and scope+version.
   An unfinished row does NOT require a nonexistent final version row. Publish
   must prove the reverse relationship exactly when inserting immutable version.
   No original URI, plaintext path, plaintext key or patient-derived filename.

These names/types are candidate design, not a parallel facts database. The source
of active document/version remains existing SQL metadata. Owned-object journal is
only authority for local resource ownership and recovery. New CHECK/FK/partial
unique predicates must be enumerated in AttachmentSchema and guard registry;
write and backup validation must not rely solely on Kotlin DTO validation.

## State and transaction seams

- PREPARE: authorized short transaction validates CURRENT dataset+epoch, operation
  idempotency payload (not just kind/reservations), expected document revision,
  generation, quotas; freezes fresh attempt/object/version/DEKhandle IDs. Commit.
- PROVISION: explicitly journal a new random alias before creation. Existing alias
  refuses replacement. Crash after alias creation but before READY leaves it
  uncertain/quarantined; never regenerate/reuse it to make a missing key appear
  recovered. Budget and ownership review must define safe orphan-key retirement.
- RESERVE_WRAP: atomically reserve+commit one use before any actual wrapping call.
  Candidate upper bound65536 calls/alias is inherited review input, not a proven
  final security policy. Failures/crashes consume reservation. No rollback/abort
  can return IV usage to the budget. Stage/active roles use separate keys and AAD.
- CREATE/WRITE: pinned app-private neutral root/generation dirFD; O_NOFOLLOW,
  no-replace creation. Bind actual dev/inode to operation; uncertain seams are
  quarantined. Bound source reads, encrypt, close, fsync file+directory, complete
  AEAD/manifest verification; no plaintext temporary fallback. UI remains OFF.
- AUTHENTICATED: persist exact manifest and ownership facts while old active
  pointer stays unchanged. Rewrapping for active role separately reserves usage.
- COMMIT: existing AttachmentTransaction capability gate encloses native SQL
  transaction AND commit. Recheck scope/epoch, generation/revision, complete
  ownership/manifest/key binding, operation UUID and payload. Insert final version
  once with true commitments, append active envelope, CAS pointer and completion
  atomically. Failure preserves old active. SQL success only after real commit.
- CANCEL/RECOVER: incomplete operations never become success by inference.
  Startup/restart matches scope and live writer exclusion. Delete only proved-owned
  unreferenced ciphertext; foreign/unknown inode or ambiguous alias quarantines.
  Release reservations according to resource fact, never decrement key usage.
  Closed reader capability cannot be revived by restoring metadata.

Per-device operation lock and cross-process file lock must both be in the final
coordinator. A process-local boolean or TEMP journal is not durable ownership.
Fsync is tested for calls/order on emulator, not claimed proof of hardware power
loss durability. Same-inode immutable consumption and parser isolation remain
separate activation conditions.

## Migration, backup and downgrade contracts

- Non-destructive migration adds nullable metadata and empty candidate tables;
  does not recalculate old links, hashes, units, consumption or PK. Legacy staged
  rows have unknown ownership, cannot be upgraded to AUTHENTICATED automatically.
- Upgrade ALL existing Room paths to the chosen actual version, schema-identity
  tests, strict guard installation/reopen and FK audit. Failure preserves old DB;
  no fallbackToDestructiveMigration. Older app must refuse newer database version.
- Record-only PNBAK1 keeps its frozen table/column allowlist and outer cryptography;
  these attachment runtime tables are not silently added. Verify export against
  frozen old adapter, import all supported business versions and fresh dataset/
  epoch retirement hooks. Same reused integer clinical ID must not reconnect old
  originals. Add new tables to retirement/recovery handling without deleting the
  old crypto namespace or copying old device envelopes into target availability.
- Complete portable medical backup remains an independent future reviewed format.
  It must retain exact attempt/suite/content identities and all retained versions,
  but create fresh local generation/dataset/epoch and device envelopes. Whole-set
  authentication, bounded encrypted staging, no plaintext spool, all-or-nothing
  activation and old/new-version refusal are required. No KDF/header is chosen by
  this document; existing PNBAK1 is not relabelled a complete original-file backup.

## Required acceptance before actual wiring

Use synthetic files only. Add migration fixtures from all real supported versions;
foreign/duplicate identities; same-ID fresh dataset; old unknown ownership; alias
loss/replacement/role mix; durable use overcount after crash; UUID payload conflict;
ABA/revision; failed CAS/SQL commit and full rollback; scope switch/revocation;
foreign path/inode replacement/hardlink/symlink; live-writer exclusion; ENOSPC,
file/dir fsync errors; process kill at EVERY seam; recovery repeated idempotently;
old record backups and unknown future versions; restore without current catalogue;
all retained versions/trash and hashes equal to independent oracle. API26/29/35,
R8/default-OFF and no-permission-change checks continue. Actual hardware power
loss/ARM external review are separately NOT_RUN until evidence exists.

This is the exact next approval/review boundary. Do not implement a Room version,
relax VERIFIED/feature guards or create a complete-backup format merely because
PR104's stream and key adapters pass tests or merge.
