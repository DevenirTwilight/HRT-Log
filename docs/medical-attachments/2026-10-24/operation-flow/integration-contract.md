# MED-02 scoped operation integration (OFF)

Baseline f5f94238, default claude/new-session-1959qb, #103–118 merged and checked live. No other active attachment/schema PR. Branch owns NotesRepository factory, RuntimeLibrarySession resource registration and new attachments package. No AppShell, theme, navigation, PK, inventory, permissions, version or backup-format changes.

| Chain boundary | Before this slice | Integration |
|---|---|---|
| SAF selection | Only ordinary imports and isolated POC | Isolated synthetic host; release route remains absent |
| Bounded source | Production AttachmentInputStream | Real ContentResolver, one bounded memory read, no URI permission retention, metadata ignored |
| Authenticated ciphertext / durable commit | Production store / journal / immutable identities | Existing store, no second encryptor or transaction engine |
| Explicit association | Existing transaction-only identity bridge | Repository accepts only existing APPOINTMENT/LAB, frozen request scope |
| List/reconciliation | Low-level metadata / operationState | Typed application DTOs, no keys/Room/paths in UI; unknown commit must reconcile |
| Authorized view | Authenticated scoped reader; proxy experiments | Application-owned memory viewer, bounded pixel allocation, callback permission checks, revoked leases and bitmap ownership |
| Complete backup | Full snapshot/writer + LibraryLifecycle | Preserve COMPLETE/REFUSED/UNKNOWN; retained nonempty fallback refuses current single-library format |
| Public enable | Not reviewed externally | Still OFF; this work is not enablement permission |

The single operation UUID must survive as neutral saved request metadata, never a URI or persisted mutation queue. A fresh process checks status, never automatically reopens a provider or replays a write. Cancel cannot undo a committed transaction. A dispatcher cancellation may occur after COMMIT: ambiguous outcomes are reconciliation-required, never a fabricated failure/success. Follow-up list/preview/export cannot reverse a real receipt.

Only an actual returned save receipt acknowledges the selected input automatically. If an exception occurs, a subsequent COMMITTED lookup requires explicit reconciliation: another controller using the same UUID could have committed different bytes during this provider read. The underlying store still independently rejects the different request digest. A lost response is not falsely FAILED, and a competing payload is not falsely acknowledged as the new selection. Explicit reconciliation displays the actual operation fact without replaying the URI. The native negative test holds a real provider pipe before preparation, lets the conflicting importer commit, then releases different bytes and requires ReconcileRequired, a single document, unchanged winning bytes, and explicit read-only reconciliation.

While a controller's input/transaction worker is live, explicit reconcile/recover cannot treat NOT_FOUND as a finished failure or clean up that live attempt. They remain reconciliation-required. Only after the worker returns may its error path independently resolve the actual journal; COMMITTED always comes from the database, never this process-local liveness flag.

Format detection is magic-byte classification, not parser validation or a medical claim. Binary is opaque bounded content. Native PDF/image parsers and their cached pixels require explicit external review before any user entry; no descriptor is exported to another app and no plaintext temporary file exists. Pixel buffers are tracked and recycled on revocation; this does not promise erasure of all native/graphics copies. Blocking/hostile providers do not acquire an invented deadline guarantee.

Soft trash retains originals/history and quarantines relationships. Existing restore authenticates before restoring the document and intentionally does not auto-relink quarantined relationships. No permanent deletion/key destruction or implicit reassociation is added. Unknown ownership is not absence; no resource limits raised. Multi-library portable-set representation is a separate compatibility decision, not implemented here.

feature_enabled=0, NOT_REVIEWED_EXTERNALLY, real originals OFF, real import/view/restore UI OFF. Synthetic operation evidence, ordinary CI, physical device/TalkBack/power loss and external review are distinct.

Restored/retained generation writes still honor existing requireUnpartitionedStorage refusal. This slice does not enable attachment writes into a managed restored library or bypass its joint retained-resource admission; that is a separate policy/integration task. Association granularity is the existing APPOINTMENT or individual LAB result identity, not an invented LabPanel identity.

Synthetic APK tests exercise actual DocumentsUI/provider grants, not a mocked picker result for the success path. Failure transport tests also use a generated reliable-pipe provider. Four fresh-process seams cover PREPARED/STAGED/IN_COMMIT/COMMITTED. The kernel/process-death runner and existing low-memory fixture remain frozen in configuration; adding these three core integration cases makes twelve core low-memory cases, not a claim of viewer low-memory/physical-device acceptance.

The synthetic screen explicitly chooses an existing APPOINTMENT or LAB before selection/confirmation. Equal timestamps do not create another relationship; the typed target stays frozen for that operation. Actual page recreation closes/reopens the encrypted connection, restores only checked neutral scope/operation/target metadata, and reconciles without reopening the provider. The standalone test-APK DocumentsProvider is Java/framework-only so its separate process does not depend on the target APK's Kotlin runtime.

System-picker selection uses an actual injected touch on a visible exact-name file row within the real DocumentsUI package; RecyclerView touch handling need not advertise a clickable accessibility ancestor. The test still requires the real SAF result and unchanged commit/read/association assertions. Timeout diagnostics preserve a bounded synthetic window tree and instrumentation output; they are not a substitute for a successful native path.

Before the single real touch, the test waits for bounded system accessibility quiescence and reacquires/refreshed live nodes. API35 evidence recorded disconnected nodes and a second directory-loader update after cancellation; prior coordinates cannot establish a user selection. This synchronization changes only the test, not production semantics or the required final SAF/commit assertions.
