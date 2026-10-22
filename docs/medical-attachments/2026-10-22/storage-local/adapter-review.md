# Default-OFF production adapter review boundary

Source749bacb50e202b7589493c697b2f1ddf1a788b4b, PR104. Author review, not independent
security certification. NOT_REVIEWED_EXTERNALLY.

- Public Tink key/primitive API replaces the experimental subtle dependency in
  production code; the frozen primitive remains test-only as a bidirectional oracle.
- Content AAD is fixed domain+revision/suite+five UUIDs, independently vector tested.
  Local dataset/epoch remain authorization. No provider-derived identity/size.
- Writer returns only a staged manifest after cipher close; IO/auth/cleanup errors
  refuse it. Full trusted commitments and strict AEAD EOF precede random reads.
  No SQL commit or format-admission claim is made. Raw keys and bounded work buffers
  are cleared in finally; no claim about copied Tink/JCA/native key material.
- Stage/active device keys are separate, neutral aliases. Provision is explicit;
  missing alias, malformed envelope and scope/role/epoch mismatch cannot create a
  replacement. AES-GCM device IV must be12 bytes; envelope length exactly60.
- Private pinned immutable FS ownership, durable IV-use reservations/rotation,
  journal/CAS, trusted SQL selection and application auth-owner wiring are NOT
  provided by these primitive adapters. There is no app caller or feature enable.
  A future coordinator must establish those facts before exposing originals.
- Reader protects returned buffers with pre/post capability checks. It cannot erase
  earlier copies, revoke an external consumer's memory or continuously monitor an
  inode modified by a hostile same-UID process after authentication. Arbitrary
  provider read/close can block; no universal deadline is claimed.
- Existing full release isolation assertions remain unchanged. Room13, PNBAK1,
  record-backup schemas, original historical data, PK and permissions are unchanged.

Local548/0fail/error/skip plus Android-test compile PASS are archived separately.
API26/29/35 and R8 are pending until their actual final-source evidence is inspected.
Physical ARM, power-cut/real ENOSPC, external review and real user/TalkBack use are
NOT_RUN. No new encrypted portable archive, database migration or UI importer is
implemented in this slice. Migration and complete-backup designs were submitted
before these adapters; do not infer approval for a new persistent format.
