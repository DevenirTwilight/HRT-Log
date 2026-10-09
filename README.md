# HRT Log / Plain Notes

Native offline Android log and reminder app. Package: `net.plainnotes.app`.
Current baseline: Build **25** / **0.2.0**, database schema **9**.
Current work and verification: [`docs/HANDOFF.md`](docs/HANDOFF.md);
feature status and unapproved candidates: [`docs/BACKLOG.md`](docs/BACKLOG.md).
[`docs/PLAN.md`](docs/PLAN.md) retains the historical architecture decisions.
No INTERNET permission, accounts, analytics or advertising SDKs.

Sublingual PK: P0/P1 engineering fixes completed; [P2-A/B research](docs/pk-research/p2/README.md) is isolated from the app. Evidence/protocol were committed before fitting; constrained prototypes and conditional comparisons do **not** establish clinical accuracy. Production parameters remain unchanged; P2-C is unimplemented and needs separate authorization.

## Build

JDK **21**, Gradle wrapper **9.3.1**, Android SDK platform **37.0**, build-tools **37.0.0**.
All dependency versions are in `gradle/libs.versions.toml`.

```sh
./gradlew -PjvmOnly :core:domain:test
./gradlew :core:data:testDebugUnitTest :core:reminder:testDebugUnitTest
./gradlew :app:assembleFullDebug
./gradlew :app:assembleFullRelease
python3 scripts/check_release_manifest.py
./gradlew :core:data:connectedDebugAndroidTest
```

`local.properties` sets `sdk.dir` locally and is ignored. The JVM-only setting excludes
Android modules and does not require an SDK. Only the full variant is maintained;
the play variant has been retired. Local release builds are unsigned; delivery uses
the existing official signing identity outside source control. Gradle verifies the distribution checksum.

Builds delivered to the product owner, including test builds, use the existing private
official signing key so they can replace the installed official app without uninstalling.
CI debug artifacts are for automated checks, not delivery. Use
`bash scripts/sign_local_apk.sh BUILD_TOOLS_DIR UNSIGNED_APK KEYSTORE PASSWORD_FILE OUTPUT_APK`
to sign a release APK locally and verify the existing official certificate. Keep all
credentials and delivered APKs outside this repository; never upload private keys to CI.
AGP 9.1.1 uses external Kotlin 2.2.20 with the documented legacy DSL opt-outs;
these fixed versions are tested together, not automatically upgraded.

## Current progress (0.2.0)

Medication rules and reminders, calendar, history and inventory, well-being and labs,
concentration estimation/calibration, Trans Memo/HRT tracker import, encrypted backup,
CSV/PDF export, app lock and privacy settings are implemented. Full builds also include
calculator/notes disguise. English, Simplified/Traditional Chinese and French are supported.
See [`docs/HANDOFF.md`](docs/HANDOFF.md) for verification and remaining work,
[`docs/REQUIREMENTS.md`](docs/REQUIREMENTS.md) for current requirements, and
[`docs/licensing.md`](docs/licensing.md) for licensing status and the history of the
removed model port (now rewritten independently from published literature).
Published-literature parameter and engine validation tests are implemented.
This is not clinical validation. Physical-device acceptance remains separate.
The [sublingual E2 P0 audit](docs/pk-research/sublingual-v2.md) adds independently
sourced study summaries and reproducible test comparisons. It identifies unresolved
calibration defects; production parameters remain unchanged and external scientific
validation is not established. See the [execution record](docs/pk-research/sublingual-p0-verification.md).
Health information must never be committed; use only synthetic test data.

Build 24 adds trash/restore; Build 25 implements explicit historical-period creation,
editing, deletion, overlap handling and legacy projection compatibility. These have
passing functional CI and a prior official-signature delivery record. Current UI
repairs keep Build 25/schema 9 and are not a new APK delivery. See
[`timeline design`](docs/design/timeline-editing-v2.md) and the current handoff.

### Historical implementation notes

The following Build 8–17 notes describe earlier increments, not the current baseline. New medication
events preserve their formulation inputs, and new symptom observations preserve
their matched catalogue sources. Missing legacy context remains explicitly unknown.
An overdue dose without an intake record is shown as unconfirmed until the user
confirms it was missed. Backup restoration has size/structure limits and transactional
validation. These changes are described in
[`history-integrity-p0.md`](docs/design/history-integrity-p0.md); they are not yet a
new public release. Treatment stages, a unified timeline and user milestones are now implemented;
regimen definitions are frozen independently of reminder versions. Legacy schedules are
marked as reconstructed, and date-only observations may span several stages. See
[`epochs-timeline-p1.md`](docs/design/epochs-timeline-p1.md). Saved lab context is implemented in build 10: frozen regimen/stage, per-ingredient
last actual intake, and a clearly labelled preceding 48-hour record window. Optional
uncalibrated estimates preserve inputs, parameters and results. Old labs require explicit
reconstruction; edits to results do not rewrite context. CSV includes all context revisions
and PDF shows the latest. See [`lab-context-p1.md`](docs/design/lab-context-p1.md).
Build 11 implements the first Visit Pack: editable appointments, user-confirmed
completed visits, visit questions, selectable PDF exports, factual summaries and
immutable generation records with a digest. See
[`visit-pack-p1.md`](docs/design/visit-pack-p1.md).
Lab panels and full historical model bundles remain future work.
Build 12 groups the Timeline by treatment periods, with labs, reviews, milestones
and appointments; individual doses remain in History. Reminder-only versions merge
for display while exact historical versions and saved lab contexts stay unchanged.
Old milestones are visible without a 90-day limit and saving has commit feedback.
Future events appear separately. Legacy nonuniform dose-slot correspondence remains
explicitly unknown; no stop or treatment start is inferred from intake history.
See [`verification and limits`](docs/treatment-period-timeline-verification.md).
Build 13 shows grouped HRT Tracker and Trans Memo intake history in the Timeline,
including older records without a saved regimen. Details use frozen record context
and open the corresponding original History entries. Intake spacing never becomes
an inferred prescription or treatment start, and imports without planned times are
not labelled on time. User-entered pause, stop and resume milestones can retain a
historical date and reason; they do not change current schedules or rewrite regimens.
See [`imported timeline verification`](docs/imported-timeline-verification.md).
Build 14 supersedes the separate-history approach for recognizable records: stable
actual dose distributions and repeated intervals reconstruct past treatment periods.
Observed patterns are explicitly distinguished from saved prescriptions; matching
adjacent standards merge into the same period. Existing imports are reprojected
automatically. Uncertain records remain available for review, and long gaps do not
imply stopping treatment. Stored records, reminders, stock and frozen lab contexts
are unchanged. See [`independent design`](docs/design/imported-treatment-periods.md).

Build 17 uses the same presentation for imported and native records, without import
badges or source-specific history buckets. Equivalent cadence expressions and short
matching date boundaries join the same treatment period; actual changes and long
unknown gaps remain distinct. Original source facts and saved definitions are retained,
with prescription and record evidence available in the audit detail. See
[`continuity verification`](docs/source-neutral-treatment-continuity-verification.md).

Build 8 repairs compatible native historical context using its saved rule snapshot.
Old HRT Tracker imports can recover missing context from the original export, or users
can explicitly confirm a past formulation for a date range. Existing dose amounts, times
and inventory remain intact. Concentration charts scale to visible estimates and labs
by default, with an optional full uncertainty range; this changes display geometry only.
See [`concentration-context-chart-hotfix.md`](docs/design/concentration-context-chart-hotfix.md).
Build 9 displays the region preference as a compact dropdown instead of a permanent
list of radio buttons; existing selections and regional source behavior are preserved.

Public binaries: [0.2.0 release](https://github.com/DevenirTwilight/HRT-Log/releases/tag/v0.2.0).
They use a private release key, distinct from the public debug key. Back up your data
before replacing a debug-signed installation; uninstalling deletes local app data.
Never commit the private signing key or upload its backup to a public release.

## License

HRT Log is released under the [MIT License](LICENSE) unless otherwise noted.
Third-party components and historical material may be subject to separate terms; see
[`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md) and [`docs/licensing.md`](docs/licensing.md).
Older releases and Git history contain material that is not covered by this license.

The official app is free, has no ads, analytics or telemetry, and requests no INTERNET
permission. These are project policies enforced by review, CI permission checks and the
release process, not license conditions. Contributions: [`CONTRIBUTING.md`](CONTRIBUTING.md).

## How this project is developed

HRT Log is a human-directed, AI-implemented project. Product requirements, design decisions,
testing, validation and release decisions are directed by the maintainer, while source code
and portions of the documentation are primarily produced with AI development tools. See
[`docs/AI-DEVELOPMENT.md`](docs/AI-DEVELOPMENT.md).

## Disguise mode

Settings → Disguise mode swaps the launcher icon for a working calculator or notes app. Type your code and press `=`
(calculator) or search for it (notes) to open the app; an optional decoy code opens a separate, empty space.
Shake the phone to return to the disguise. Known limits: the real app name still shows in the system app list,
in system settings and in notification headers; after switching, some launchers take a few seconds to refresh and
drop the old home-screen shortcut (add the new icon from the app drawer).
