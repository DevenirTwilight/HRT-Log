# Next executable storage slice — owner decision recorded

Owner answer received on2026-10-22: **“批准默认关闭的实现，外部审核在启用前完成”**.
This supersedes the pending-decision wording below for the default-OFF production
writer/reader/key adapter. See REQUIREMENTS§144 and PR104. External review remains
NOT_REVIEWED_EXTERNALLY and is required before activation. Actual new migrations
and complete-backup format changes still require their compatibility design/review;
this decision is not feature activation. The original request below is retained
as history, not an unresolved repeat permission question.

## What has actually advanced

- Baseline: Room13; supported record-backup business versions through13. PNBAK1
  remains records-only and excludes medical original files.
- #102 is merged (e4ebab2e), with three actual API26/29/35 Foundation18 reports
  verified against original ZIP SHA/CRC. Resource ownership/cleanup is available.
- #103 candidate 8dd60c43 adds bounded source ownership and real pipe tests. Local
  172 domain +64 POC +20 Foundation and69 isolation tests pass; final native/CI
  is pending. No claims are borrowed from an older source.
- Existing test-only cipher, journal/recovery, backup, image/PDF and debug library
  demonstrations are retained. They are not production format/key approval.

## Concrete next implementation scope

Recommend candidate A from report44: upstream Tink streaming AEAD via supported
public APIs; per-immutable-version keys; explicit source/format admission; owned
no-replace encrypted staging; full authentication before publication; durable
filesystem ownership evidence; exact SQL CAS and authorized synchronous commit;
startup recovery which never deletes foreign files or disturbs a live writer.
Keep all real user entry points disabled while implementation is validated.

Planned files/interfaces, without claiming they already exist:

| Component | Scope |
| --- | --- |
| AttachmentKeyFoundation / new reviewed key adapter | Missing-key refusal, separate roles, device wrapping, explicit budgets; never reuse database keys |
| New AttachmentObjectStore | Supported crypto API, immutable objects, authenticated reader, no plaintext disk fallback |
| AttachmentOperations / new import coordinator | Durable ownership journal, reservations, idempotent requests, cancellation and recovery; reuse new input transport |
| AttachmentTransaction / session owner | Preserve native commit/revoke ordering; integrate all actual consumers and AppLock only with dedicated tests |
| AttachmentSchema, entities, DAO, migrations | Add only required durable ownership fields after current-schema compatibility review; no schema number reserved here |
| Separate complete medical-backup service | Reviewed independent format and portable key/whole-set authentication; preserve PNBAK1; required before enabling library |

Any schema change must start from actual latest Room13 or its successor, include
all historical upgrade paths and rollback fixtures, and coordinate other active
branches. No test-only magic/context is simply renamed into a production format.
Full backup, metadata evolution, resource quotas and cleanup policies must be
explicitly frozen; older record backups must retain unknown/retired attachment
identity rather than guessing links. The new transport's explicit limit is not
a production quota decision.

## Why this requires a specific decision

The owner's archived task `docs/audits/product-quality-2026-10/sources/med02b3/execution-task.md`,
section0, explicitly says that production protocol freeze, writer/reader, key
wiring, Room migration and complete backup require a specific approval first.
Reports44/45 additionally keep independent review separate from owner approval;
the record remains NOT_REVIEWED_EXTERNALLY. A generic continuation instruction
has not been relabelled as a signed protocol/format review.

Requested decision is the above default-disabled storage implementation scope,
not permission to enable the UI, store unique originals, sign an APK or publish.
Where external review must precede implementation under the archived instruction,
its actual reviewer/sign-off or an explicit owner change to that development
prerequisite must be recorded; no internal tests are substituted for it. Feature
activation still requires complete file/backup/consumer/compatibility acceptance.
