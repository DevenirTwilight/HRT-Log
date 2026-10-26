> **V1 final source9fafe3c2 / protected merge03f724f4.** See [single final acceptance and handoff](FINAL-ENGINEERING-CLOSEOUT.md) and [immutable raw receipt](final-acceptance-evidence/9fafe3c2/receipt.json). This evidence-only branch changes no product source. Exact-source and default required checks PASS: ENGINEERING_COMPLETE / READY_FOR_EXTERNAL_REVIEW. NOT_REVIEWED_EXTERNALLY / RELEASE_NOT_AUTHORIZED. Historical LMK is preserved, not claimed repaired. No automatic next feature stage.

# MED-02 external security review package

**NOT_REVIEWED_EXTERNALLY.** Current final-closeout executable freeze is `a61f10638446a72602e514c357c7615635146cb9`, based on accepted default `9192b91a99107d41fad402a3d16622f63e1ce1ef` (#130). Start with [MED-02 FINAL ENGINEERING CLOSEOUT](FINAL-ENGINEERING-CLOSEOUT.md): current call chains, finite V1 scope, test reproduction, retained failures and exact acceptance conditions. [Source inventory](source-inventory.json) hashes reviewed bytes. Final PR #131 HEAD CI must pass independently; earlier SHA evidence is historical and explicitly bounded. No external assessment or release approval has occurred.

`feature_enabled=0`, `PUBLIC_ENTRY_ENABLED=false`. Public import/view/restore, real-original testing, permanent object/key destruction and release/signing remain OFF. The only operation host is a nonexported, emulator-guarded debug Activity, with an isolated random SQLCipher/key namespace and synthetic test-provider allowlist; it is absent from release navigation. No real patient data, credentials or keys are included.

## Threat model and trusted base

Untrusted inputs include ContentProviders and their lengths/MIME/names, selected document bytes, corrupt archives, stale UI/notification requests, concurrent revocation and unexpected termination. Trust relies on Android process/UID and Keystore isolation, SQLCipher and existing root generation authority, mature Tink authenticated encryption, and the existing archive authentication implementation. A compromised OS/root attacker, forensic erasure of every memory copy, or malicious native parser exploitation is not proven prevented by these tests.

| Review boundary | Actual implementation and required review |
|---|---|
| Provider → bounded memory | `MedicalAttachmentOperations.import`, `AttachmentInputStream`, `AttachmentFlowController`. One transient content URI, no persistable grants/replay, actual EOF/16MiB limit, ignore metadata, magic classification only. Input cancellation closes owned stream and wipes registered arrays. A blocking hostile provider has no promised deadline. Review zero-length reads, transport failure, cancellation/COMMIT races and resource exhaustion. |
| Device/object keys | Existing `AttachmentDeviceKeys`, `AttachmentObjectStreams`, `AttachmentPersistentStore`. Tink Streaming AEAD with authenticated frozen identity; Android Keystore wrapping and durable one-use reservations. Missing key is refusal, not key recreation. No database-key reuse, permanent key deletion or quota increase. Review alias retention across failed attempts and generations. |
| Journal and explicit medical identity | Existing journal/`AttachmentTransaction` plus `MedicalAttachmentOperations`. APPOINTMENT or individual LAB must already exist in the frozen scope; no date/name guessing or LabPanel identity invention. PREPARED/STAGED/IN_COMMIT/COMMITTED are real store seams. Only actual COMMIT yields a receipt; unknown outcome requires explicit read-only reconciliation. Existing UUID plus changed selection cannot claim a new save. |
| Read and native viewing | `MedicalAttachmentOperations.open`, existing authenticated/scoped reader, `AttachmentViewer`. Recheck dataset/epoch/active handle, typed ACTIVE link, same current VERIFIED version at publication; failed reads clear output. Internal ProxyFD only, no external app grant or plaintext cache. PDF one bounded raster page; image dimensions checked before allocation; opaque binary only bounded prefix. Review PDF/JPEG/PNG/WebP native parser attack surface, callback threading, image decode limits and cleanup. Recycling/wiping owned buffers does not establish erasure of native/graphics copies. |
| Session and stale requests | `RuntimeLibrarySession`, controller and existing Runtime Request Scope. Pause/lock/invalidation closes registered capabilities; no old integer ID rebinding. SavedState holds neutral scope/target/op identities, no URI or unlock grant. Fresh process verifies status, never auto-resubmits. Review app lock, disguise, pending picker delivery, dispatcher cancellation and explicit reopening. |
| Portable backup and restore | Existing `LibraryLifecycle`, full snapshot, `MedicalArchiveWriter/Reader`, `AttachmentRestorePreflight`, `AttachmentBusinessRestore`, `LibraryRestoreCoordinator`. Full authenticated collection, new-device keys, single authority commit, old-generation retention. PNBAK1 stays ordinary records only; no format/crypto change. Unknown objects are UNKNOWN; missing/incompatible retained sets REFUSED. Joint admission and PNMEDBK2 collection export/restore were implemented by #122, including restored-library new writes and retained resources; unsafe or UNKNOWN admission still refuses. Review key translation, all-member closure and budgets before switch. |
| Retention and deletion | Existing lifecycle inventory/soft trash. Originals/history retained, restore does not implicitly re-link quarantined links. No permanent object/key deletion is added; disassociation is not destruction. Missing/unknown ownership never becomes absence or successful full export. |

## Verified evidence and limits

- [Accepted operation evidence](../operation-flow/accepted-2a47be6a/receipt.json): official ZIP SHA-256 matches GitHub metadata and all ZIP CRCs checked. API26/29/35 each have actual DocumentsUI App2 and core Operation3, zero failures/skips, plus four operation fresh-process verification seams. Original ZIPs contain existing lock/disguise/scope, migration and generation tests; selected original reports are retained alongside them.
- API26/x86/512MiB/192MiB independent job: actual core12 and generation4 + operation4 fresh-PID verification. Memory file is stage observations for the existing restore fixture, **not a viewer/overall RSS peak**. Full app viewer low-memory behavior is NOT_RUN.
- [Original navigation failure and correction](../operation-flow/navigation-fix/README.md) remain separate from passing native work. Final required CI is a separate merge gate.
- Earlier failures (read-only test corruption preparation, isolated provider Kotlin dependency, DocumentsUI selection and historical `ecba64b6` process interruption) remain in the historical evidence branch `docs/med02-operation-evidence-20261024` and existing first/second-native directories. The historical process interruption cause remains UNDETERMINED.
- PHYSICAL_DEVICE / TalkBack / physical power interruption: **NOT_RUN**. Broad malicious parser corpus/fuzzing, JPEG success flow and full viewer memory acceptance are not established by PDF/PNG/binary happy paths. No independent expert review has run.

## Finite remaining enablement work

1. Independently review the implemented joint retained-generation admission and PNMEDBK2 collection representation; do not silently drop fallback versions or equate prior internal tests with expert approval.
2. Build real localized import/list/view UX on this scoped facade only after these policies and review permit it; current public cards stay unopened. No Compose access to keys/Room/raw paths.
3. Specify soft unlink/trash/restore UX and any later permanent deletion policy independently. No destruction authorization exists here.
4. Review/fix parser, provider, session/revocation and archive findings; execute targeted low-memory viewer and physical-device checks against the frozen reviewed source.
5. Obtain independent security findings/remediation acceptance, full backup compatibility acceptance and separate owner permission before public enablement.

## Preview safety slice and reproducible checks (2026-10-26)

`ProductionViewerSafetyAndroidTest` directly exercises the production `AttachmentViewer` with a real revocable AttachmentSession and synthetic memory transport. It is not an encrypted-store or SAF end-to-end test; the existing `AttachmentOperationAndroidTest` covers that separately. Four finite tests (the fourth added after the API26 finding below) cover JPEG/PNG/WebP/PDF decode/render/release/revoke, invalid/short headers, PNG width2049, extreme PDF page aspect, close/revoke and zeroed owned prefixes. Samples include Java heap, native allocations and PSS at20ms cadence: **sampled maxima, not absolute peaks**. Small positive fixtures demonstrate lifecycle, not maximum-size or all-corpus capacity. No fuzzing or native parser exploitation resistance is claimed.

Run on each API26/29/35: `./gradlew :app:connectedFullDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=net.plainnotes.app.attachmentpoc.ProductionViewerSafetyAndroidTest`. Existing routine API26/29/35 instrumentation also discovers this class. Dedicated `med02-preview-low-memory.yml` runs the same tests (three at 4b5a6688, four from the API26 fix onward) on API26/x86512MiB/192MiB with heap assertion, kernel/logcat and memory observations. No A→B→C rerun in this targeted job. New source tests/memory values are PENDING until final CI artifacts are inspected; local Android SDK/compiler unavailable. **No measured values invented.**

WebP currently remains `application/octet-stream` in the stored/backup MIME allowlist and public selector has no dedicated WebP option. Direct native decoder coverage does not make WebP an enabled application preview format. Adding persisted image/webp would require compatibility review; no archive MIME or protocol changes here. MIME/filename are not proof of content safety. Malformed payloads can be encrypted as opaque objects, but must not be advertised as verified-safe documents. Existing provider tests cover cancellation, read failure, misleading metadata and different-payload UUID races; hostile indefinite provider blocking and native parser denial-of-service remain residual threats, with no promised IO deadline.

## UI contract and independent review gate

UI may depend on `RuntimeLibrarySession.attachments` / AttachmentFlowController only while scope is current: launch transient SAF selection, explicitly confirm target/operation identity, show Pending, show Saved only from actual COMMIT receipt, reconcile uncertain outcome read-only before retry, list/open authenticated current objects, release each rendered bitmap and close reader/viewer on exit. Pause/lock/disguise/scope invalidation revoke resources; UI must blank its visual surface on revocation rather than keep displaying a stale Bitmap reference. Rendering runs off the main thread; only one bounded page/image retained per viewer. No Compose/raw Room/path/key access or external descriptor grant is approved. Existing real lock/disguise/dataset tests remain evidence with their own source SHA; the new viewer capability tests do not claim those UI scenarios were newly run.

Reviewers must inspect latest multi-library/root authority, full archive membership, provider blocking/cleanup, ProxyFD threading/native teardown, decode allocation and raster publication/revocation races, app-lock/disguise wiring and memory remnants. Memory samples cannot prove complete erasure of native/graphics buffers. Broad malformed corpus, physical low-memory device, manual UI/TalkBack and physical power interruption remain NOT_RUN. Existing historical crashes/LMK and fixes remain in their source-specific evidence; no new test pass rewrites them.

External findings/remediation acceptance and separate owner approval are both required before public import/view/restore or real-original use. `feature_enabled=0`, `PUBLIC_ENTRY_ENABLED=false`, permanent deletion OFF and `NOT_REVIEWED_EXTERNALLY` remain unchanged. Independent review must cover the actual proposed enablement SHA; this package is preparation, not external approval.

### Original API26 viewer observations — source4b5a6688

Run40322026302, job121569085644, artifact12649937943 (`viewer-api26-512m`), ZIP SHA256 `6f719b2576e83116d186cf6cb3d39f9e2c9cc770f2a235a785642c47430d872c` verified against GitHub metadata. Native output **OK (3 tests)** in29.907s; original native/memory files retained in `preview-evidence-4b5a6688/`. API26/x86512MiB guest, max ART heap201326592 bytes. No production viewer code changed from accepted baseline.

| Phase | Sampled Java bytes | Sampled native bytes | Sampled PSS KiB | Samples |
|---|---:|---:|---:|---:|
| formats |2097704|5871736|111022|53|
| closed |1716680|6624032|120834|3|
| malformed |3224008|7433280|120408|31|

These are independently sampled observations, not simultaneous sums, absolute peaks or proof of reclamation after every native allocation. The closed phase has only3 samples. Kernel/system/native memory outside this process and larger corpora are not bounded by this result. Native parser tests passed without a production-code vulnerability finding; do not infer absence of native vulnerabilities. API29/35 and final documentation HEAD CI are separate gates and not credited from this API26 run. Worker HTTP artifact download was denied by its storage-domain proxy; GitHub connector obtained the same original ZIP and its digest matched, without rerunning tests.

### API26 finding at e9220e6c and fix — Android 8.x PdfRenderer

Both required runs of source `e9220e6c993bb799526d66f76347ca9b9164081a` (push run 40323822330 job 121575603335, pull_request run 40323828101 job 121575880572) failed `attachment-poc-compatibility (26)` identically: five valid-PDF cases of the existing `TinkPdfGate2AndroidTest` reported `java.io.IOException: file not in PDF format or corrupted`. The same class passed on accepted base `ccd51332` and on API29/35 for the same SHA (API35 device-tests job 121575880500 runs it after the new class). The isolated low-memory job passed because its unopenable-PDF test ran last.

Root cause is an Android 8.0/8.1 framework defect, verified in AOSP (`android-8.0.0_r1`, `android-8.1.0_r1` `PdfRenderer.java`): the constructor sets `mInput` before `nativeCreate`; when pdfium cannot open the document, `nativeCreate` already balances its init count, but the half-built object's `finalize()` sees `mInput != null`, calls `doClose()` → `nativeClose(0)` and decrements the process-wide pdfium init count again. Every later `PdfRenderer` in the process then fails (and works on a destroyed library). `android-9.0.0_r1` guards `doClose` with `mNativeDocument != 0`. The new production-viewer malformed-PDF cases were the first tests to make `PdfRenderer` construction fail in the shared instrumentation process; in the app the same would follow any unopenable (damaged or encrypted) PDF attachment on API26/27, which is minSdk.

Fix (fail closed, minimal): on API < 28 `AttachmentViewer.pdfPage` throws `PdfPreviewUnsupported` before any HandlerThread, ProxyFD or `PdfRenderer` exists, so untrusted bytes never reach the defective renderer. Stored PDFs stay encrypted, listed, backed up and readable through the authenticated reader; images and opaque prefixes are unaffected. On API ≥ 28 behaviour is unchanged. `validPdfStillRendersAfterUnopenablePdf` asserts both sides (8.x: refusal before the renderer; P+: valid PDFs of two sizes still render after unopenable ones and forced finalization). The debug synthetic host falls back to the authenticated prefix read for PDFs on 8.x. A later product decision may restore 8.x PDF preview only through an isolated process; that is not implemented. Historical failing runs above are retained, not rewritten.


### API29 hang after #128 merge and fix — PdfRenderer finalizer closes its input (2026-10-26)

After #128 merged (`d6221397`), `attachment-poc-compatibility (29)` intermittently hung inside `ProductionViewerSafetyAndroidTest.validPdfStillRendersAfterUnopenablePdf` until the 45-minute job limit:
- #129 pull_request run 40342161197, job 121640277661: stopped at 66/75;
- #130 run 40341160034, job 121635912808: stopped at 68/75.

The same SHA `1e455dd9` passed this job once in its push run (job 121639890480). The main-branch run for `d6221397` (job 121620221215) failed earlier for an unrelated reason: the API29 system image download was corrupt. The logcat for the hung test shows only the memory sampler running, with no exception.

Likely cause, inferred from AOSP and not reproduced locally because there is no emulator:
- On API 28+, `PdfRenderer` still sets `mInput` before `nativeCreate`. After a failed open, its `finalize()` → `doClose()` therefore still closes that input.
- The test forces GC and finalization while `AttachmentViewer.dispose()` closes the same `ParcelFileDescriptor` on the cleanup dispatcher.
- Two unsynchronized closes of one descriptor number can close a number that has already been reused, such as a new proxy bridge descriptor. Proxied I/O then waits forever.

Fix:
- `PdfRenderer` now receives `descriptor.dup()`, so each descriptor has exactly one closer: `PdfRenderer` (close or finalizer) for the duplicate, `dispose()` for the original.
- Revocation is unchanged. The reader is closed first, so any late read through the duplicate gets EIO.
- The test's `measured()` gained a 120 s watchdog that logs all thread stacks (`SyntheticViewerHang`), so a recurrence leaves evidence instead of a silent timeout.

This fix needs new API29 CI results; the historical hung runs are kept.

## Stage C actual lifecycle candidate (2026-10-26)

Accepted baseline9eb6d0f5 (#129); final candidate identity is the Stage C PR HEAD and current source-inventory hashes, not prior API results. Review call chain: MainActivity.onPause → NotesViewModel.sensitivePresentationHidden → RuntimeLibrarySession.hideSensitivePresentation → RuntimeAttachmentGateway.onHidden → MedicalAttachmentsController.closeViewer. Runtime pause/invalidate/close additionally notify then revoke existing protected capabilities. Ended dataset scopes cannot resume. A presentation epoch prevents old native render returns from publishing after hide/revoke/resume/new-open; it is not permission. Internal ViewModel factory checks PUBLIC_ENTRY_ENABLED before touching real runtime/targets, with no main navigation references and unchanged release guard.

SAF, Tink/key wrapping, ProxyFD, SQLCipher and collection/root COMMIT implementation unchanged. Actual receipt remains authoritative despite refresh/revoke; unknown outcomes require read-only reconcile. API26/27 PDF remains refused. Native parser memory/corpus results retain their exact #128 hashes, not credited anew. See attachment-ui/README Stage C proposal for bounded demand-paging and separate unlink/trash/restore/destruction decision table: none implemented/approved here, no resource release or key destruction.

New tests are PENDING: real AppLock/background/recreation and real disguise-exit event witnesses plus scripted late-pixel result race. Expert must independently review MainActivity, runtime notification/authority ordering, controller state/publication, Compose bitmap ownership and release payload isolation. These layers do not establish a full user-enabled path, hardware memory erasure, recent-task screenshot confidentiality under user-enabled capture, native parser exploit resistance or physical-device behavior. Formal real-original entry remains OFF, external status NOT_REVIEWED_EXTERNALLY, physical/TalkBack/power-loss NOT_RUN. Independent findings/remediation acceptance and separate owner enablement approval are required.


## Final-closeout integration addendum (supersedes earlier candidate/PENDING scope descriptions)

Production `MedicalAttachmentRecordPage` composes the tested UI/SAF/back lifecycle, while `MedicalLibraryTransferPage` connects the full authenticated backup/restore facade to existing engines. Both return before runtime access while public gate is false; no release route references them. MainActivity/AppLock/Disguise lifecycle is real, not a second authority. Final decoded-frame publication also passes the existing native reader's scope fence. See final report for exact new tests and final-source CI requirement. Historical evidence tables above retain original SHAs and must not certify subsequent source. Ordinary PNBAK1 backups remain records-only; the new OFF medical-transfer candidate uses existing PNMEDBK1/2.
