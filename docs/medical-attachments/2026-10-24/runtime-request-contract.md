# Runtime request identity, OFF

Base: merged #115, `b73dcc617d9be6c1b35daea4157f093df3ef3313`. No new Room schema, authority, crypto protocol or attachment entry point.

## Implemented paths

| Producer / consumer | Identity and refusal |
|---|---|
| NotesViewModel → NotesApp, medication/reference editor, labs, visits, history, trash, ordinary backup/import/export | One immutable RuntimeLibrarySession and scoped Repository per screen owner. Reads as well as writes compare generation/dataset/epoch. Root transaction gate remains the only switch authority. Permission is checked before opening storage and again before transaction return/commit. |
| SavedStateHandle / process or task restoration | Only neutral scope metadata is saved. Restored clinical state without provable scope refuses before database lookup. Same scope may resume; different scope cannot be rebound. No saved mutation is replayed. |
| Library invalidation / app resume | Room invalidation is only a wake-up. Compare actual root authority and local identity; remove the old editable composition and show explicit reopen/review guidance in four languages. Reopening creates a new owner rather than retagging callbacks. |
| Alarm, notification open, complete and snooze | Carry frozen scope in private Intent extras. PendingIntent identity includes random batch and opaque mapping UUID in its data URI: extras alone are insufficient because FLAG_UPDATE_CURRENT overwrites them. Actual consumers compare scope before clinical lookup/write. |
| Boot/unlock/time-change scheduling | A fresh scheduling batch reads fresh facts and creates scoped requests. Device-protected cache remains opaque scheduling information only, without dataset/epoch or clinical IDs. Locked/test notifications still work without credential storage. |
| Legacy notification without scope | No clinical completion. Show neutral reopen/review explanation. Unlocked snooze also requires scope; device-locked scheduling remains opaque. App PIN/disguise completion requires opening the app; other reminder behavior remains available. |

Two REDs reproduce the actual baseline defects: a new Repository with old saved scope read a different library before its handle map was populated; creating a new notification action replaced an already issued token despite different scope extras. Their original outputs are retained in `runtime-request-evidence`. Additional session tests exercise missing provenance, epoch change, explicit fresh owner and revocation. Synthetic fake DatabaseAccess fixtures now initialize UNKNOWN/OFF identity, matching the production initialization they override; no existing clinical assertions were weakened.

`RuntimeLibraryRequestAndroidTest` uses the real archive, new local keys, persistent SQLCipher restore, independent full verification and root COMMIT, then tests old versus fresh requests. `RuntimeRequestUiAndroidTest` uses the existing real Compose debug fixture and ordinary PNBAK1 epoch change to exercise old-page rejection and explicit reopening; it is not a second claim of full medical restore. API26/29 explicitly include both; API35's full suite includes them normally. Compilation is not native execution.

Cancellation, authorization loss and post-commit refresh failure never cause automatic retry. UI permission checks are revocation checks, not a replacement for the attachment session's existing commit/publication capability protocol. A commit already completed remains a fact even if its old owner cannot publish into a newer page.

## Boundaries and next slice

MED-02 remains `feature_enabled=0`, `NOT_REVIEWED_EXTERNALLY`; no real originals, public import/open/restore, permanent cleanup, public protocol freeze, signing or release. Existing OFF P4 process writer and P5/P6 are not redesigned. No new persistent task queue or clinical mutation replay exists here.

Next independent slice: owner-release and retained fallback generation inventory/export policy. Completion requires a persistent, auditable classification of reservations versus referenced committed objects across every retained generation, replay-safe logical release of provably unmaterialized reservations, and a complete export/refusal contract for retained libraries. Unknown ownership must remain charged/quarantined. Permanent file/key deletion stays separately gated; do not implement a garbage collector under this request-scope PR.

Evidence status is maintained in HANDOFF and the PR for its exact source SHA. Physical devices, real dual-device migration and external review are not implied by emulator CI.
