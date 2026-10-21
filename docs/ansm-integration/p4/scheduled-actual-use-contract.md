# Scheduled actual-use integration contract

Status: IMPLEMENTED_INTERNAL / LOCAL_INTEGRATION_VERIFIED. Eight scheduled synthetic contracts and full data373/shared98 pass, Android test-source compilation passes, App PeriodStability5 and PrivacyNoticeFacts3 pass. Native98 runtime and physical-device tests NOT_RUN. App entry remains OFF. Intermediate fixture/helper/auditor-precondition failures are archived independently; original guards remain unchanged.

The future internal entry must reuse the actual schedule slot and Repository transaction, not manufacture a second unscheduled record or reconstruct prior settings from current product data. A scoped ticket binds medication UUID, local dataset epoch, rule version, slot key, effective instant/zone/dose/late threshold and retained existing record revision. Current schedule and overrides must be resolved in the captured database before a new write. Slot changes, cancellation, dataset replacement or duplicate completion invalidate the write.

For an existing MISSED/UNCONFIRMED record, reuse its ID and preserve its original config snapshot; increment revision and explicitly record the user's actual instant, actual zone and actual dose independently of physical use. For a new slot record, use the retained rule snapshot. Preserve planned values even if the actual amount differs. No default dose or physical amount comes from a catalog.

The stable operation UUID must replay the original operation only when both actual input and original slot identity match. A different UUID cannot produce a second completion of the same slot. A matching clinical dose or instant alone never identifies a package. DEDUCT_CURRENT, ALREADY_ACCOUNTED and UNKNOWN remain separate explicit stock effects, including backfilled facts.

Required executable checks: fresh slot completion, previously missed slot preserving its snapshot after metadata change, stale rule/override rejection, identical retry, same UUID different slot/content rejection, different UUID duplicate slot rejection, transaction failure at record/context/stock checkpoints, explicit already-accounted backfill, timezone/DST boundary and canceled slot. Existing legacy PeriodStability and schedule assertions remain unchanged.

The mixed-stock admission guard belongs at formal record-write entry points. The low-level legacy compatibility allocator retains its original tested behavior for already-defined legacy operations; it must not be reached implicitly by a new mixed-stock user action. No scheduled measured App entrance is enabled until these checks and UI confirmation are implemented.


The candidate consumption request format3 adds a canonical SHA-256 of frozen rule ID, slot key, scheduled instant/zone, planned dose IEEE bits and late threshold. Formats1/2 remain readable and unchanged for prior unscheduled inputs. Full dataset audit compares the current same-revision scheduled fact to this hash; schedule replay must match it. Clinical dose remains independent of planned dose and physical quantity. This does not add a Room column or change PNBAK1 encryption/business schema.
