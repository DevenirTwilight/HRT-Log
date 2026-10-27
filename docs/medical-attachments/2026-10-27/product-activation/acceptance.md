# MED-02 product activation acceptance — PR #133

2026-10-27. Continuation of the owner-approved §154/154.A product activation,
not a new MED-02 stage. Baseline df0aacf0; current branch is the sole implementation PR.
PUBLIC_ENTRY_ENABLED=true stays enabled. Room21 is unchanged by this correction.

## Confirmed failures and correction

- df0aacf0 API26/29/35 failed AttachmentOperationAndroidTest line101 because
  it still required the public gate to be false. Replace the obsolete gate assertion;
  retain real SAF, COMMIT, cancellation, reopen, reader and isolation assertions.
- df0aacf0 library512MiB failed MedicalAttachmentOperationAndroidTest line48:
  the current-record list deliberately excludes non-ACTIVE links. Verify the list is
  empty AND the actual persistent link remains QUARANTINED, is unreadable and
  does not hide an unrelated active LAB link. No production guard is weakened.
- Existing synthetic RuntimeAttachmentGateway tests now expect its real scoped
  activation to set flag1. Direct low-level OFF fixtures retain their flag0 assertions.
- The previous retained-reader fix 15..21 remains intact; final native reruns must
  confirm it. Original job logs are retained in evidence/df0aacf0/, including the
  real low-memory failure. That failure was a test assertion, not evidence of LMK.

## Actual new acceptance, not a substitute UI

MedicalAttachmentProductAndroidTest runs MainActivity and actual production routes,
controller, runtime, SQLCipher, Tink and authorized viewer. Only medical seed facts
and the protected test DocumentsProvider are synthetic. It checks real DocumentsUI
selection/cancellation, explicit confirmation, real COMMIT, a NEW Activity reopening
and authenticating PNG, one-link removal, and an individual LAB ID different from
its panel ID. It then exports/authenticates/restores actual PNMEDBK2 through the same
runtime adapter used by the page, rejects the old scope, launches a new Activity,
opens the restored individual LAB page and authenticates its preview.

The adapter portion is NOT described as SAF archive CreateDocument/OpenDocument UI
coverage. The real transfer page's production navigation is exercised. Existing
archive controller/held-channel/fresh-device and force-stop contracts remain mandatory.
Physical-phone archive dialog interaction is NOT_RUN until actually performed.

scripts/run_med02_product_native.py executes exactly this case in a fresh synthetic
emulator installation, preserving its XML before later suites. It checks ro.kernel.qemu
before any package reset; no real-device cleanup or production deletion API is added.
The original operation cases still run unchanged apart from the obsolete gate assertion.
API35 bulk excludes only this new independently verified case to avoid duplicate runs.
API26/29 execute the same isolated case, not a ViewModel-only substitute.

The shared migration contract creates an actual database from published 20.json,
checks aborted migration leaves historical guards intact, opens through real Room
20→21, compares every table's rows, checks FK/encryption and rejects an old reader.
It runs in existing JVM and native SQLCipher suites; no new schema version.

## Local evidence at correction checkpoint

- App + core AndroidTest compilation: PASS.
- AttachmentActivationTest and MedicalAttachmentUiGuardTest: PASS.
- Published Room20→21 targeted migration contract: 1 PASS, 0 fail/error/skip.
- Python release-isolation checks: 5 PASS.
- Three-API new real-entry integration and final source CI: PENDING at this source
  checkpoint; never infer PASS from compilation. See PR #133 exact-head Actions and
  the immutable final acceptance receipt linked from its body after completion.

## Unchanged boundaries and internal admission rule

Do not merge until required checks, applicable native/security/low-memory contracts,
conflicts and reviews pass at the exact final SHA. No prior source's green substitutes.
Keep F04 POTENTIAL and NOT_REVIEWED_EXTERNALLY factual. Internal acceptance is not
an independent security certification. Under §154 the owner's explicit activation
policy replaces the old unconditional external-review-before-enable development gate.
Do not add a paid certification gate. Any concrete reproduced safety/core-flow bug
still blocks. No permanent deletion, quota increase, crypto/format/PK/stock changes.

FullRelease must compile unsigned and pass existing release/source-isolation checks.
No signing, APK delivery, version/tag/Release change is authorized. The owner's phone
requires an updated APK to use the new entry. Manual TalkBack, hardware power loss
and physical-phone acceptance remain NOT_RUN, with V1 viewer limits retained.

## Additional exact failures discovered when host job completed

The old host run 40505845804 finished with 21 core failures and one app failure.
Original HTML/ZIP identity is retained. Current-Room assertions still expected20;
manual fault-injection chains ended at20 so Room could not reach21 and the injected
faults never executed. The correction extends each explicit chain to21 and updates
only current-room expectations, retaining historical versions, injected-fault reached
assertions and full rollback/data checks. Room21's valid 0/1 flag no longer rejects1;
negative guard tests reject2, while OFF defaults and wrong-scope activation remain checked.

After correcting those stale assumptions, six historical upgrade cases exposed a
real production migration defect: migration20To21 required guards that published
historical DDL had not yet installed. The migration now calls the EXISTING canonical
AttachmentSchema.install (including journal guards), inside the same transaction,
before specializing activation. This is the same mechanism used on every onOpen;
no new schema, facts, permissions, cache, trigger protocol or source trust is added.
The exact published20 fixture freezes an actual pre-existing reference runtime
identity, comparing all rows and trigger inventory through rollback and reopen.

The PDF/string freeze failed only at the two explicitly authorized privacy elements.
The test still checks all original PDF/source/locale hashes; it verifies each new
privacy element against a fixed approved SHA256 and normalizes ONLY those elements
and the already-approved Tink attribution to their historical values. No broad hash
replacement, ignored element, deleted assertion or production wording rollback.

105 targeted data tests, eight four-locale unlink-confirmation tests and one PDF/string
freeze test PASS locally; callbacks/controller/activation regression and native test
compilation also PASS. Local unsigned Release attempt lost its Gradle daemon during
R8 while another local task was active: NOT_COMPLETED, cause not proven, not called
product LMK or a successful build. Final CI Release is the authoritative acceptance.

## 31fd715e actual native evidence and remaining checks

Library512MiB ran all14 actual base contracts successfully, then the shell's stale
exact-count guard expected13. Update that strict expectation to14 to include the
new existing exact-link test. No cases, limits or assertions are removed.
Featherline512MiB PASS. The former QUARANTINED assertion failure is resolved.

Real MainActivity on API26/29 imported, reopened and authenticated the visit PNG,
then timed out in the unlink step (two actions had originally shared one line).
The new test reports the exact missing action and full semantics roots, keeping
all assertions. A visible UNKNOWN result, if encountered, is explicitly reconciled
using the existing READ-ONLY UI action; never repeat selection/encryption.
API35 stopped waiting for actual picker cancellation. Production callbacks were
incorrectly using Session.consumePicker as result authorization: MainActivity also
consumes this lock-exemption token in onStart, and callback ordering varies by API.
Both record and archive pages now validate their live pending owner, unchanged
session and current runtime, clear the exemption idempotently and preserve all
backend request/capability/generation/confirmation gates. No process-restored URI
or grant exists. Final real-native evidence must confirm the fix; compilation alone
cannot do so. Original failures stay FAIL and are retained under evidence/31fd715e.

## Actual product-layout and release-payload correction

3e2211d3 API26/29/35 all progressed to the SECOND unlink action, the confirmation
button. The first unlink action's touch had not produced that dialog after closing
preview. Source inspection shows VisitDetail returned the full-page viewer without
consuming Scaffold contentPadding. Its close control occupies the app bar's menu
hit area; a test could click the wrong real control while still finding underlying
semantics. Wrap the existing record page in the existing contentPadding. This is
one host-layout correction, not a visual redesign or viewer rewrite. The native
case now asserts close.top >= realMenu.bottom and waits for the viewer to actually
close before touching management. Deep semantics diagnostics remain on failure.
The system-cancel precondition also distinguishes closing DocumentsUI's initial
roots drawer from cancelling the actual picker. Both use observable nodes, no sleeps.

Local production/test compilation PASS. A standalone serial FullRelease build at
3e completed unsigned (8m30s), and correct app identity/no INTERNET manifest checks
PASS. The old B2 guard then refused the legitimate production Tink namespace. Under
§144/154 Tink is already the approved production implementation: allow its namespace
in DEX, still forbid it in the manifest and retain EVERY actual PoC/test/provider,
format marker and demo-resource rejection. Added a regression that approved Tink
never licenses a co-located test marker and cannot become a manifest component.
All6 Python checks and the actual unsigned APK payload guard now PASS. No Tink
version/code/crypto or R8/signing configuration changed. This local APK is not the
final source candidate and is NOT delivered; exact-final-source CI must rebuild it.

3e library512MiB SUCCESS through the full actual workflow. Featherline512MiB FAIL:
kernel explicitly killed foreground PID3849 after wrong-password rejection, before
the next authentication completed (245648KiB anon at kill, NOT peak/RSS). Preserve
kernel/logcat/measurements/native originals. The earlier clear-log warning is not
the cause. This is a real historical/concurrent low-memory risk; no memory limit,
fixture, test assertion or importer/KDF code is changed to force a pass. Final
source's actual low-memory result remains to be recorded honestly, not assumed.

## Direct individual LAB destination

LabsScreen already displayed an individual result detail, but its attachment callback
opened MedicalLabDetail's default detail again. Pass a transient, explicit LabValue ID
as attachment destination intent and clear it on matching host disposal. The ordinary
visit/medical-center lab-detail entry still defaults to detail. No panel ID substitution,
new persistent field, auto-import or database/record change. Compilation/UI boundary
regressions PASS; the same real MainActivity case requires ONE attachment click for
result2/panel1 and will verify the actual relation through authenticated reading.

## Native row hit-region correction (test input only)

At a0, the real API35 product case completed visit import/reopen/authenticated
preview/real close/exact unlink. Its later result-row center touch opened the Labs
floating add-batch action. The original full semantics tree proves row2 bounds
(32,489)-(288,555), center(160,522), overlapped by lab-open-batch bounds
(141,520)-(304,576). Touch the unobscured left20% of that actual semantic row,
still using actual input events and the production details/attachment routes.
Do not replace it with a ViewModel call, direct URI or a relaxed identity assertion.
Both original and restored LAB row entry use the same real hit-region helper.
No unrelated Labs UI is redesigned. Compilation PASS, final native outcome pending.
Four API security and Featherline512MiB passed a0; the 3e LMK remains a preserved
failure and no memory-stability fix is claimed.

## Post-restore snapshot readiness (test only)

155 API29 completed visit and LAB real SAF/COMMIT/authenticated reads, exact unlink,
formal PNMEDBK2 export/authentication/actual root COMMIT, old runtime rejection and
a new MainActivity scope. Its immediate performScrollTo on restored row2 ran before
the asynchronous committed medical snapshot was composed. Observe both non-loading
snapshot containing that exact LAB and its actual semantic node (bounded30s), then
perform the same real touch. Do not remove the subsequent original document UUID,
authenticated preview or retained-unlink assertions. No production changes or sleeps.
Native test compilation PASS; full exact-source result remains pending. Original
failure and unmodified artifact SHA/XML retained under evidence/155043ff.

## Final frozen-source results (7bdddc88)

Production source is 7bdddc8850b83c083ea9d2abe4e90c3bb59da75c. This evidence
branch is documentation only and is NOT an application build or replacement PR.
PR Android run40519149329: real MainActivity product case PASS on API26/29/35,
1 case each, 0 failures/errors/skips, suite20.808/22.689/20.588 seconds.
API29/API35 whole jobs SUCCESS; API26 uploaded successful case and all steps,
job/check settlement still awaited. Same-source push host run40519142188 SUCCESS:
data659 (0 failures/skips); App2495 (0 failures,561 original skips), PeriodStability5
PASS. Unsigned fullRelease/No INTERNET/test-payload isolation/source ledger PASS.
The product case drives actual visit/LAB SAF, cancel, confirmed COMMIT, new Activity,
authenticated PNG, exact unlink, typed result2 not panel1, real PNMEDBK2 transfer
adapter and restored actual Activity. It does not claim backup file-picker widget
interaction, physical phone or manual TalkBack coverage.

MED02 library512MiB run40519149572/job122241475263 SUCCESS: base14, archive4,
collection2, generation/collection/operation four death seams each, independent
fresh AVD2 PASS; source and target Android IDs differ, transferred archive hash
matches, target heap192MiB. Four API security run40519149422 SUCCESS.

Separate non-protected Featherline512MiB job122241475849 FAIL: actual kernel LMK
at initial wrong-password KDF, PID3921, anon217276KiB observed at termination,
not total/peak RSS. Its importer/KDF/ViewModel/runner files are unchanged versus
f0c4ea9a; this does NOT establish the allocation root cause or claim the whole App
has passed all low-memory paths. Preserve this failure and do not rerun unchanged
source, raise limits or omit checks to select a green result. Shared workflow is
FAIL, its MED02 library job is independently SUCCESS. No claim of Feath repair.

Protected six checks and exact PR host finalization still pending at this checkpoint.
No merge before those actual conditions settle. F04 remains POTENTIAL; external
review NOT_REVIEWED_EXTERNALLY. Owner §154 permits scoped internal admission;
no independent certification, signature, APK delivery, tag/Release or permanent
file/key deletion. Exact raw originals, artifact identities and limits are in receipt.json.

## Protected merge receipt

All six protected checks on exact7bdddc88 PASS, both push and PR. No unresolved
review threads or conflicts. Normal Ready + squash auto merge, matched head, no
admin override: PR133 MERGED 2026-10-27T14:49:38Z, ff54396bc96db4264c26bfb0cb930a2244dbd176.
Actual default claude/new-session-1959qb fetched and PUBLIC_ENTRY_ENABLED=true.
Source/merge trees both95f132496434e9ce81f3c010db117cfb68e0d80a, exact equality.
Scoped internal MED02 admission PASS under owner154; separate optional Featherline
LMK failure remains recorded, not a universal low-memory or independent external
certification claim. No bypass, skip, signing, delivery or release.
Mainline automatically started40524889046 Android /40524889000 security /
40524888999 Science, currently PENDING. Continue checking actual results; no new
manual rerun or further development phase.
