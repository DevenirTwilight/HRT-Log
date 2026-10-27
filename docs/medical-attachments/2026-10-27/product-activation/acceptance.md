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
