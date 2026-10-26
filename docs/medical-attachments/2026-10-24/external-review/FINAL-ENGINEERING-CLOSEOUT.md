# MED-02 FINAL ENGINEERING CLOSEOUT

2026-10-26. **NOT_REVIEWED_EXTERNALLY / RELEASE_NOT_AUTHORIZED**.

## Immutable candidate and acceptance rule

Accepted default baseline: `9192b91a99107d41fad402a3d16622f63e1ce1ef` (#130). Existing #131 is the only closeout PR; no successor stage is planned. Executable/test freeze: `a61f10638446a72602e514c357c7615635146cb9`. [Source inventory](source-inventory.json) binds the actual reviewed bytes. Documentation commits do not certify themselves; resolve the final PR HEAD and merge SHA using:

```sh
gh pr view 131 --repo DevenirTwilight/HRT-Log --json headRefOid,mergeCommit,state,statusCheckRollup
gh run list --repo DevenirTwilight/HRT-Log --commit <exact-sha>
python3 - <<'PY'
import hashlib,json,pathlib
p=pathlib.Path('docs/medical-attachments/2026-10-24/external-review/source-inventory.json')
for f,h in json.loads(p.read_text())['files_sha256'].items():
    assert hashlib.sha256(pathlib.Path(f).read_bytes()).hexdigest()==h,f
PY
```

At this documentation freeze, final-source CI/native acceptance is **PENDING**. ENGINEERING_COMPLETE and READY_FOR_EXTERNAL_REVIEW may be asserted only after the exact final HEAD passes required CI, affected API26/29/35 native contracts, applicable low-memory jobs, with no blocking reviews/conflicts. Prior cc08 greens are not substituted. A failed final gate means BLOCKED_ENGINEERING until its precise cause is resolved. No SECURITY_APPROVED claim is made. The final PR acceptance receipt and merge identify the actual outcome; do not infer PASS from this plan.

## V1 scope and implementation

| Approved capability | Actual code and limits |
|---|---|
| Explicit existing appointment/single LAB target | `MedicalAttachmentRecordPage` → existing ViewModel factory → scoped `RuntimeAttachmentGateway`; target and runtime generation/dataset/epoch checked. No date/name/local-ID guessing. |
| SAF, confirmation, one import | Production record-page composition now owns the same tested content and back handling as debug host. Transient single selection; no bytes or import operation before confirmation. Original bounded flow, actual content classification and UUID semantics unchanged. |
| Commit and list | Existing `MedicalAttachmentOperations` and store; only real COMMIT receipt is success. Refresh failure retains receipt; UNKNOWN only read-only reconcile. |
| Safe read/preview | Existing authenticated reader/ProxyFD, bounded JPEG/PNG, PDF first page API28+. API26/27 PDF refuses preview but permits encrypted storage/backup. Other files opaque; WebP unchanged. No external viewer or plaintext disk. |
| Real lifecycle | MainActivity pause → ViewModel → existing RuntimeLibrarySession hidden notification; AppLock/Disguise/library invalidation revoke existing protected resources. No second authority. Recreate/unlock never restores a URI, selected file or read grant. |
| Late render fencing | Controller clears presentation before closing/recycling; opening reader is owned before decode. Final state publication runs through existing native read-capability publication check plus presentation epoch. Stale render cannot publish after revoke/switch/new open. No claim of erasing all GPU/native copies. |
| Full portable transfer candidate | `MedicalLibraryTransferPage/Controller` → `RuntimeMedicalLibraryGateway` → scoped Repository `MedicalLibraryTransfer` → existing PNMEDBK1 reader / PNMEDBK2 reader/writer and sole-root-COMMIT coordinators. Full authentication is read-only before explicit restore confirmation. Channels held from one URI open; no plaintext JSON disk. |
| Restore result / lost response | Neutral operation UUID only is SavedState/task-forwarded. Actual commit receipt survives revoke; UNKNOWN permits explicit journal lookup, never blind restore replay. Verified pending journal may be explicitly logically aborted through existing coordinators (all resources retained; no destruction/refund). |
| Pre-restore protection | Review offers export of current collection first, then requires fresh restore selection. Same URI as selected restore source is rejected before output open. User may explicitly continue without protection copy. |

No public menu, route, deep link or notification references either production Page. Each Page checks PUBLIC_ENTRY_ENABLED before runtime access. Existing release-isolation guard is retained and strengthened for new UI/test provider. Production Pages are compiled candidates; tests use existing isolated debug synthetic host and controlled gateways/native coordinator layers. This is **not** a claim that release users can reach them or that one automated test traverses the entire future public backup UI.

## Changes that close real gaps

1. Record-page production composition replaces debug-only orchestration without adding a route.
2. Application-layer full-medical-transfer facade, owned SAF channel, scoped adapter, review/password/actual-result UI now call mature authenticated archive and restore engines. PNBAK1 ordinary records remains separate and unchanged.
3. Final pixel publication uses existing native scope/capability fence; opening readers close during hide rather than waiting for decoder completion.
4. Optional pre-restore protection export; explicit pending restore cancellation without deleting resources; cancelled-before-dispatch passwords also zeroed.

No Room/business version, key protocol, archive format, KDF, quota, importer, PK, stock or BDPM changes. #130 visit-pack source is only accepted-mainline integration. No additional library/generation authority was introduced.

## Test evidence and reproduction

- Local targeted suite before last safety-copy addition: 80 cases, zero failures, four existing skipped dialog cases (Robolectric 411dp limitations). Last targeted controller rerun: 6 cases, zero failures/errors/skips, including safety-copy source protection. Kotlin app/core AndroidTest sources compile. These are JVM/compile results, not native execution.
- UI matrix: EN/zh-CN/zh-TW/FR, 320dp/2x and 411dp/1x; new transfer review/receipt cases also night mode. Automated semantics are not manual TalkBack.
- Existing Python release isolation 5 and design validation 61 passed locally; final CI must independently certify final bytes.
- New core native `LibraryRestoreAndroidTest.applicationTransferFacadeAuthenticatesBeforeCommitAndKeepsOldScopeInvalid`: real old-format PNMEDBK1 and PNMEDBK2 auth, unchanged target before confirmation, fresh-device keys/SQLCipher/root COMMIT, original read/typed relation and old-scope refusal. No alternate TEST_ONLY archive.
- New app native `MedicalAttachmentUiAndroidTest.actualRuntimeTransferAdapterWritesAndAuthenticatesOneHeldChannelAndRevokesIt`: real runtime adapter export/authenticate/owned channel/revoke/refused restore. Controlled synthetic provider only. Core native test above separately proves activation; fake UI tests prove review/receipt semantics.
- Existing #129 real DocumentsUI synthetic import → explicit target → COMMIT → authenticated preview remains in API26/29/35 cohort. New code reuses its composition. Actual MainActivity lock/background/recreate and actual Disguise.exit witnesses are retained; these event tests are not a native pixel-forensics claim.
- Library low-memory exact cohort is original 12 plus one new facade method =13; original restart/fresh-C contracts and sample sizes unchanged. Workflow may run this mandatory cohort because scoped Repository is affected. No extra discretionary A→B→C benchmark requested.

Local reproduction (JDK21, Android SDK, cached Robolectric jars):

```sh
./gradlew -ProbolectricDir=<cached-jars> :app:testFullDebugUnitTest \
 --tests net.plainnotes.app.attachments.ui.MedicalLibraryTransferControllerTest \
 --tests net.plainnotes.app.attachments.ui.MedicalAttachmentsControllerTest \
 --tests net.plainnotes.app.attachments.ui.MedicalAttachmentsUiTest \
 --tests net.plainnotes.app.attachments.ui.MedicalAttachmentUiGuardTest \
 --tests net.plainnotes.app.RuntimeLibrarySessionTest \
 :app:compileFullDebugAndroidTestKotlin :core:data:compileDebugAndroidTestKotlin
python3 -m unittest discover -s scripts -p 'test_attachment_release_isolation.py'
python3 -m unittest discover -s scripts -p 'test_attachment_design_validation.py'
```

### Failures retained, not erased

[cc08 receipt and selected raw reports](closeout-evidence-cc08/receipt.json): original push Android run40363107565/job121713554506 failed five asynchronous unit tests (Mona/TransMemo expected-state waits and sublingual saved-state lookup), 2457 tests/5 failed/561 skipped. Same-source PR run40363115035 passed all five Android jobs. One bounded failed-job retry121730008290 passed. **ROOT_CAUSE_NOT_PROVEN**; no source assertions changed, not labelled proven infrastructure or MED defect. Original ZIP artifact12669629204/hash retained in receipt; selected original HTML and native XML are committed. New final source requires fresh CI.

Earlier preview memory, input/operation, multi-library/LMK failures and repairs remain indexed in existing review documents; do not rewrite them as first-attempt success. Local early compile extra-parenthesis error was corrected; an initial Robolectric ConnectException was dependency-fetch failure, resolved by cached jars, not a product PASS. Subsequent targeted tests and instrumentation compilation passed.

## Compatibility and closed historical work

#103/#104/#106/#110/#111/#112/#113/#114/#115/#118/#119/#120/#121/#122 storage, input, keys, snapshot, restore, authority, runtime scope, collection codec/admission and A→B→C are existing work: do not redevelop. #128 viewer corpus/memory and #129 synthetic UI are retained evidence with their own SHAs. #130 is separate accepted visit-pack selection.

PNBAK1 ordinary record crypto/semantics, PNMEDBK1 single-library reader and PNMEDBK2 full collection authentication remain unchanged. ACTIVE/RETAINED/history/trash/typed UUID closure remains the existing engine's obligation; missing originals, UNKNOWN resources or excess aggregate budgets refuse the whole transfer. Failed/aborted candidates retain ciphertext/aliases/reservations; they can cause safe REFUSED rather than a promise that every failed attempt remains exportable. No automatic quota refund or cleanup is introduced.

## Finite product decisions and residual risk

- **DEFERRED / OWNER_DECISION_REQUIRED:** unlink/trash/restore UI. Existing soft-storage primitives do not prove relation-only user semantics. V1 has pre-confirm cancel; confirmed mistaken attachment is retained. Owner must accept that V1 limitation or separately authorize minimal non-destructive management. Unlink must preserve bytes/history and other links; trash preserves quota; restore must not resurrect old links. Permanent key/file destruction remains OFF and separately authorized.
- Android8 PDF refusal and first-page-only API28+ are the frozen safe V1 limits. Multi-page/WebP/OCR/search/cloud/export-to-other-viewer are enhancements, not unfinished mandatory engineering.
- Native PDF/image parser vulnerabilities, malicious provider indefinite blocking/OS behavior, GPU/IME/immutable String remnants, screenshot/recents behavior under existing user capture preference require external evaluation; finite synthetic corpus does not establish universal exploit resistance.
- **PHYSICAL_DEVICE_NOT_RUN; TALKBACK_MANUAL_NOT_RUN; HARDWARE_POWER_LOSS_NOT_RUN.** Emulated force-stop does not prove physical power failure. Sampled Java/native/PSS values in existing preview evidence are not peaks.

## External reviewer and owner handoff

Start with [README](README.md), threat boundaries, [source hashes](source-inventory.json), accepted operation/preview evidence and new tests above. Review commit-bound source, malicious input/native parser/ProxyFD concurrency, keys and durable journals, single-root activation, all-member backup closure, stale scope/pixels, failure receipts and release isolation. No actual independent expert has participated: **NOT_REVIEWED_EXTERNALLY**.

After final engineering gates pass, no further speculative feature stage is required. To open V1 later, owner must: commission independent review of this actual candidate/protocol/threat model; resolve blocking findings; complete agreed physical-device/accessibility acceptance; explicitly accept V1 limits and entry placement; approve a separately reviewed enablement/metadata compatibility patch (current DB feature guards enforce zero, so this is not merely flipping a UI constant); then give explicit feature-use authorization. Signing/delivery/release remain separate. No real medical originals enter this candidate's synthetic tests.

**Final decision is evidence-dependent:** if final-source gates pass with no known blocker, core V1 software work ends as ENGINEERING_COMPLETE + READY_FOR_EXTERNAL_REVIEW, while RELEASE_NOT_AUTHORIZED remains. If not, list only the exact failed gate and minimum corrective change; no automatic next Stage D/E/F.
