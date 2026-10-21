# P4 closeout: PRODUCTION_BLOCKED

Default branch is `claude/new-session-1959qb`, verified HEAD `320171cdc882ec0b62830b60e2329a4174ffa31c`. Production/native candidate `0f33ae664e657861d0f54d3f9e7e757370aa8917`, PR #57, remains Draft / entrance OFF. Subsequent evidence-only commits do not change these tested runtime inputs. No merge, release or APK delivery.

## Actual native acceptance

API26 SQLCipher, original512MiB RAM /192MiB ART heap;14600 records,2000 containers,32603 rows,19660924-byte business fixture. Run39428902719 / job118648446184 / artifact12267293264. ZIP SHA256 `6214b895413080cdf9a93f322223025e2e748a2a22adb95d0f602ade37a30774`, CRC verified; full metrics and reproducible ART parser output in `performance/native-profile-0f33ae66/`.

| Gate | Earlier complete source51 run39423676688 | Final candidate run39428902719 | Final result |
|---|---:|---:|---|
| Full audit ≤15s |7.19s|7.53s|PASS|
| Shared forecast ≤15s |8.86s|7.92s|PASS|
| Encrypted backup ≤120s |8.66s|8.35s|PASS this run|
| Encrypted restore ≤120s |34.24s|44.56s|PASS this run|
| Actual writes incl tickets ≤5s each |6.96/7.42/7.24s|7.87/7.68/7.57s|**FAIL**|
| Observed Java heap peak ≤192MiB |189.38MiB|119.94MiB|PASS this run; not native RSS|

Different hosts/runs are observations, not a causal speedup claim. Original bba backup-phase kernel OOM remains a real historical failure. Source015's PID-matched kernel OOM occurred during initial staging/restore, before backup/writes; its later timings are NOT_RUN. The final run completes every original phase without OOM, but independent repeat stability and RSS peak are NOT_RUN. OOM is not declared permanently solved.

## Implemented changes and preserved boundaries

- `MeasuredBackupJson.kt` / `NotesRepository.kt`: bounded row encoding already prepared earlier; completed single-owner plaintext cleanup across success, exception, cancellation and rejected transfers. Byte/SHA oracle matches frozen old encoding. PNBAK1 cipher/KDF/format and limits unchanged.
- `MeasuredOperationChain.kt` / `MeasuredConsumptionStore.kt`: complete transaction-only primitive operation evidence reused by projection/clinical/ADJUST reads.4MiB conservative metadata accounting; overflow fully falls back to original SQL. All global checks remain; no cross-write trust, incremental replacement, new table or Schema version. Evidence is cleared on exit, no mutable row aliases, read-time changes rejected. Native final SQL confirms the narrow projection path actually ran; this is not a bypassed optimization.
- Test-only fixture metadata sizing no longer creates a duplicate entire JSON String/byte array. Frozen template SHA/data/actual backup/restore/write workload and budgets unchanged; additional exact19660924-byte assertion.134 platform-document oracle cases, helper absent from production jar.
- Collector retains the already validated complete pre-cleanup report with same64KiB/current-nonce/trace-digest fences. Full final SQL details and trace were recovered, unlike earlier truncated-log fallback. FAIL remains FAIL.

No UI, P5/P6, PK model, permissions, versions, signing or official-data changes. P4 already had candidate Room13/business12 versus merged Room12/business11; this closeout introduces no further migration or backup schema. Main synchronization was a normal merge of already accepted320171cd; no force push or unreviewed branch copy. Previously stale LAB migration expectation was adapted only to P4's existing13/12, retaining raw/history checks and original failure evidence.

## Root cause, proof limits and stop decision

Ordinary actual-use operations still end with a whole-dataset audit, including unrelated history. Final native post-budget isolated reads observe full cached audit6.45/6.67s versus uncached13.63/11.90s; ticket capture1.13s / validation1.18s. Instrumented audit16.42s is observer-inflated, not the frozen budget result. Leading driver calls include ledger stream0.91s, FK checks0.65s, identities0.62s and clinical stream0.57s. These separate read-only probes and sampled CPU segments must not be added into a fabricated exclusive decomposition of an actual write. Commit-only versus mutation-only time was not separately established.

Transaction-scoped reuse is correct under tested invariants, but does not remove the O(history) work and cannot meet the5s gate. The final memory-lifetime attempt completes the workload; the write blocker remains. Stop further speculative microoptimization. Do not introduce cross-write cache/generation trust without proving equivalence across all permitted transitions and out-of-band changes. No third memory attempt, weakened check or relaxed budget is proposed.

Smallest next engineering boundary: review a trustworthy full-audit baseline at first use/migration/restore, exhaustive transition coverage for create/scheduled completion/correction/delete/restore/adjust/reconcile/VOIDED, and invalidation for every DAO/raw SQL/dataset epoch/revision change before approving any change-set validator. A persistent invalidation mechanism needs its own schema/backup/version and failure-rollback review; do not reserve the next Room number. If restore OOM recurs, isolate existing validation/preflight/insert/audit boundaries and establish allocation ownership before selecting a bounded restore design. Do not treat the test counter as a streaming production restore implementation.

## Regression and remaining gates

- Local full data461/461 and App43/43 PASS (including PeriodStability5, stock consistency and UNKNOWN matrix); final focused migration/backup16/16 and tools67 PASS; generated Android test compilation PASS. Raw receipts retained. No skipped assertion.
- Source015 actual API26/29/35 MeasuredConsumption129 methods each PASS, including all new cache and forgery/rollback checks; raw HTML/official archive digests in `performance/compat-01534191/`. Overall old CI later cancelled on source update; these reports do not imply all checks passed. Source51 actual migration/cleanup/cancellation/byte oracle15 each PASS on all three APIs, separate receipts.
- Candidate0f33 JVM/science checks PASS; Android/device/API26/API29 checks were still IN_PROGRESS at snapshot. Six required checks are **not all green**. Final evidence-only HEAD must receive its own checks; no ancestor success is substituted.
- Physical devices/TalkBack: NOT_RUN. New cross-write validator/production activation/P5/P6: NOT_IMPLEMENTED. Existing real-source UI/medical modules untouched.

**Merge conclusion: NOT_ELIGIBLE / PRODUCTION_BLOCKED.** Preserve all failed runs. Ordinary branch protection and future exact-HEAD CI remain mandatory; no automatic merge while this native gate fails.
