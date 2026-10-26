# MED-02 V1 FINAL ACCEPTANCE & HANDOFF

2026-10-26. **Final default-branch verification IN_PROGRESS.** The exact implementation candidate passed acceptance and #131 is merged. Final ENGINEERING_COMPLETE / READY_FOR_EXTERNAL_REVIEW decision follows the remaining default-branch CI. **NOT_REVIEWED_EXTERNALLY — RELEASE_NOT_AUTHORIZED.**

## Source and immutable evidence

- PR: https://github.com/DevenirTwilight/HRT-Log/pull/131
- Accepted source: `9fafe3c27d563c32f5638029ff084db3e1b59d7f`.
- Protected squash merge / default `claude/new-session-1959qb`: `03f724f41e91a1a866d98de31de673594d1105af`.
- Accepted baseline: `9192b91a99107d41fad402a3d16622f63e1ce1ef` (#130). Merged tree equals tested source tree;132 [source hashes](source-inventory.json) independently checked.
- Permanent acceptance originals: [final receipt](final-acceptance-evidence/9fafe3c2/receipt.json), raw XML/logs/timings/resource samples alongside it. [Original failures](final-acceptance-evidence/4e73-original/receipt.json) remain FAIL, with original kernel/job/logcat evidence.
- This evidence-only branch is `evidence/med02-v1-final-9fafe3c2`; it changes documentation only. Build/review the accepted source or merge SHA above, not an evidence commit as a new product candidate. No duplicate implementation PR or next feature stage.

## Actual failure findings and corrections

| Original gate | Finding, correction, retained boundary |
|---|---|
| API26/29/35 `applicationTransferFacadeAuthenticatesBeforeCommitAndKeepsOldScopeInvalid`: `OWNER_OR_SET_NOT_PROVEN` | Whole-UID export was tested in the unrelated-root bulk cohort, where synthetic aliases are intentionally retained. Production inventory scans all object/generation aliases and correctly refuses an incomplete declared owner set. The identical original method already passed in the clean low-memory13 cohort. Move it unchanged into existing isolated `MedicalAttachmentOperationAndroidTest`; require initial alias cleanliness. Do not delete/adopt unknown aliases, manufacture inventory or change any production COMPLETE/UNKNOWN rule. All threeAPI now pass the actual formal1/2 export/auth/restore/old-scope assertions plus existing unknown-file/alias negatives. |
| `library-api26-512m`: SWITCHED seam not reached within60s | Original13+4+2 and first3 force-stop boundaries passed. Logs show active source creation/export/SQLCipher work, not proven LMK. The watchdog included fixture preparation before real restore. Identical production-writer encrypted source now prepared in a separate60s-bounded process, source keys removed before restore; original60s live seam and90s independent verify deadlines remain. Separate source/live timings retained; no claim that the old combined60s boundary passed or that host variability was excluded. All4generation+4collection+4attachment transaction seams now pass; new-device C independently passes. |
| `featherline-api26-512m`: instrumentation process crashed | Confirmed foreground LMK PID3830 **after real COMMIT**, not the nonfatal logcat-clear refusal, password error or a proven Java heap OOME. Original run stays FAIL. This batch made **no production memory/KDF patch**. The required final-source run fully completed real SAF/password-error/retry/COMMIT/reopen/idempotency with original limits. ART reclaimed68MiB during reopen in the passing trace; original died before corresponding collection. This is evidence about workspace release/collection timing, not proof of a sole cause or universal stability. Pressure/GC timing and physical low-RAM reliability remain explicit external-review/enablement risks, not a claim of repaired memory performance. |

Changed executable test/harness files only: `LibraryRestoreAndroidTest.kt`, `MedicalAttachmentOperationAndroidTest.kt`, `LibraryRestoreRestartAndroidTest.kt`, `scripts/run_library_restore_restart.py`. Production archive, inventory, transaction, crypto, UI and medical-data code were not weakened or changed in this correction. No Room/business-schema, PNBAK1/PNMEDBK1/PNMEDBK2, KDF, quota, permission or key-lifecycle change; no new crypto dependency adopted.

## Final-source acceptance

| Actual9fafe3c2 gate | Result / original evidence |
|---|---|
| Required JVM, Android build/unit/lint/release isolation, API35 device, API26, API29, Science | All six **PASS**. PR Android40389538288; independent push40389531882; Science40389538281/40389532959. Research-deep correctly SKIPPED for unchanged science, not reported PASS. |
| Whole-UID negative/positive cohort | API26/29/35 each8tests, **0failure/0error/0skip**. Original facade assertion plus missing/unknown owner refusal retained. Raw `api*-whole-uid.xml`. |
| Real attachment UI and runtime transfer adapter | Three cases on eachAPI, all **PASS**, no skipped case: SAF/explicit target/COMMIT/list/preview/revoke, cancel/recreate, held transfer channel/auth/read/revoke. Raw `api*-ui.xml`. |
| Library API26/512MiB/192MiB | **PASS** run40389538449:13original+facade tests,4collection codec tests,2business-integration tests; all12 real force-stop/new-PID verifications; wiped independent guest C only receives encrypted archive and passes2tests. |
| Generation source/live phase timings | PREPARED7.669/4.946s; OBJECTS_COMMITTED5.793/8.874s; IN_SWITCH5.890/17.143s; SWITCHED5.604/11.107s. Different nonoverlapping phases, not a retroactive old-budget success. Raw JSON retained. |
| Featherline API26/512MiB/192MiB | **PASS** full actual application sequence, run40389538449/job121806604485. No direct-URI substitute, higher heap, smaller fixture, skipped password or forcedGC. |
| Viewer API26/512MiB/192MiB | **PASS** run40389538098; existing bounded image and unsupported8.x PDF policies retained. |
| Default merge03f724f4 | Science40394519787 **PASS**; Android40394519910 **IN_PROGRESS** at this checkpoint. No source-tree divergence. |
| Physical device / manual TalkBack / hardware power loss | **NOT_RUN**, each independently; emulator force-stop is not hardware power loss. |

### Resource observations, not memory peaks or a stability-fix claim

| Featherline observation | Original failed4e73 | Passing9fafe3c2 |
|---|---:|---:|
| Java used maximum observed, bytes | 80515264 | 81768424 |
| Native allocated maximum observed, bytes | 20085568 | 19563264 |
| PSS maximum observed, KiB | 227433 | 234357 |
| Outcome | LMK afterCOMMIT, reopen not completed | Password/retry/COMMIT/reopen/repeat completed |

Observer sleeps20ms between queries; query overhead makes effective resolution coarser. None is an absolute peak, and native allocation is not native RSS. Failed-process anon196996KiB is a termination observation. The passing process had a higher observed PSS, so no claimed reduction can be inferred. Global pressure/GC timing remains a risk for expert/physical-device review. No claim that Android cannot reclaim this app. Only real commit facts/immutable source identity may acknowledge/reconcile a save; no blind retry, false failure or duplicate-write workaround was introduced.

The finite OFF-candidate qualification uses actual final-source gates plus preserved ownership/authentication/durability/stale-scope contracts. It is not an authorization to expose real files or a certification of low-RAM stability. A concrete new invariant violation or failed final default gate reopens BLOCKED_ENGINEERING; do not continue speculative feature stages.

## What V1 actually implements, still OFF

- Production record Page/composition and scoped gateway: explicit existing appointment/single LAB identity, one SAF selection, bounded actual bytes/type checks, transient URI, explicit confirmation, real encrypted-object/journal COMMIT, list and authenticated read. No guessing by dates/name/integerID, plaintext disk staging or external viewer descriptor grant.
- Production transfer Page/controller/facade: held SAF channel, full PNMEDBK1 reader/PNMEDBK2 collection authentication, read-only preview before confirmation, optional current-collection safety export, real SQLCipher/new-device-key restore and single root-authority COMMIT. Actual commit receipt survives refresh/revoke; UNKNOWN is read-only reconciliation. Same selected source URI cannot be used as protection output; fresh selection required afterward.
- AppLock, actual MainActivity hidden/background/recreate, Disguise and generation/session change revoke presentation/readers; final pixel publication fenced by native capability and presentation epoch. Old scope cannot retag same integerID in a new dataset. No universal GPU/IME/String erasure claim.
- ACTIVE/RETAINED/history/trash/typed relationships remain the existing authenticated full-set engine's obligation. Missing objects, unknown aliases/ownership, pending or oversized resources refuse; no truncation, silent history omission, quota increase, destructive migration or automatic cleanup.

Existing#103–#130 storage/keys/restore/codec/viewer/UI work was reused. Earlier cc08 asynchronous failures and one bounded retry remain in [their receipt](closeout-evidence-cc08/receipt.json), ROOT_CAUSE_NOT_PROVEN; no old assertion was removed or failure rewritten.

## Frozen limits and remaining enablement conditions

- `feature_enabled=0`, `PUBLIC_ENTRY_ENABLED=false`; no public route/file picker/real originals, permanent deletion OFF. This is compiled OFF infrastructure plus isolated synthetic validation, not a publicly available medical vault.
- JPEG/PNG bounded preview. API28+ PDF first page only; API26/27 refuses PDF preview but preserves encrypted storage/read/backup. WebP and other binaries remain opaque. No multi-page/OCR/search/cloud/AI stage.
- Unlink/trash/restore user actions **DEFERRED / OWNER_DECISION_REQUIRED**; existing primitives are not approval for relation-only UX. Before confirmation users cancel; confirmed originals are retained. No promise to refund quota or destroy bytes/aliases.
- No independent expert has reviewed the final candidate: **NOT_REVIEWED_EXTERNALLY**. Native parser/provider concurrency, keys/wraps/journals, all-member transfer, stale handles/pixels, pressure/GC behavior, failure/unknown receipts and release isolation remain explicit expert questions. Physical device/accessibility/power-loss acceptance not invented.
- Owner must commission independent review, resolve blocking findings, complete agreed physical/accessibility acceptance, accept V1 limits and entry placement, and authorize a separately reviewed activation/metadata compatibility patch. DB guards currently enforce zero; activation is not merely flipping a UI constant. Signing/delivery/Release need separate authorization.

## Review and reproduction

Start with [README](README.md), threat boundaries, [source inventory](source-inventory.json), original and current receipts above. Checkout accepted source9fafe3c2 or merge03f724f4; reproduce normal pinned CI plus existing `run_library_low_memory_acceptance.sh`, `run_featherline_low_memory_acceptance.sh` and viewer workflow in the stated synthetic emulators. Source/test hashes bind exact bytes; CI artifacts/hashes and selected unmodified XML/logs are retained in this repository, not AI-review substitutes.

No StageD/E/F follows automatically. Final decision remains conditional only on the outstanding default-branch check at this checkpoint; real enablement, independent security approval and release remain separate.
