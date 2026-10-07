# HRT Log / Plain Notes

Native offline Android log and reminder app. Package: `net.plainnotes.app`.
Implementation baseline: [`docs/PLAN.md`](docs/PLAN.md), reviewed at `bd2a6bd`.
No INTERNET permission, accounts, analytics or advertising SDKs.

## Build

JDK **21**, Gradle wrapper **9.3.1**, Android SDK platform **37.0**, build-tools **37.0.0**.
All dependency versions are in `gradle/libs.versions.toml`.

```sh
./gradlew -PjvmOnly :core:domain:test
./gradlew :core:data:testDebugUnitTest :core:reminder:testDebugUnitTest
./gradlew :app:assembleFullDebug :app:assemblePlayDebug
./gradlew :app:assembleFullRelease :app:assemblePlayRelease
python3 scripts/check_release_manifest.py
./gradlew :core:data:connectedDebugAndroidTest
```

`local.properties` sets `sdk.dir` locally and is ignored. The JVM-only setting excludes
Android modules and does not require an SDK. Full and play have the same package;
installing one over the other replaces it. Release APKs are unsigned; use your own
signing setup outside source control. Gradle verifies the distribution checksum.

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
[`docs/licensing.md`](docs/licensing.md) for the unresolved upstream model license.
Literature validation and physical-device verification remain incomplete.
Health information must never be committed; use only synthetic test data.

The development branch now targets build 7 / database schema 4. New medication
events preserve their formulation inputs, and new symptom observations preserve
their matched catalogue sources. Missing legacy context remains explicitly unknown.
An overdue dose without an intake record is shown as unconfirmed until the user
confirms it was missed. Backup restoration has size/structure limits and transactional
validation. These changes are described in
[`history-integrity-p0.md`](docs/design/history-integrity-p0.md); they are not yet a
new public release. Treatment stages, a unified timeline and user milestones are now implemented;
regimen definitions are frozen independently of reminder versions. Legacy schedules are
marked as reconstructed, and date-only observations may span several stages. See
[`epochs-timeline-p1.md`](docs/design/epochs-timeline-p1.md). Historical model bundles,
saved lab context and Visit Packs remain future work.

Public binaries: [0.2.0 release](https://github.com/DevenirTwilight/HRT-Log/releases/tag/v0.2.0).
They use a private release key, distinct from the public debug key. Back up your data
before replacing a debug-signed installation; uninstalling deletes local app data.
Never commit the private signing key or upload its backup to a public release.

## Disguise mode (full build only)

Settings → Disguise mode swaps the launcher icon for a working calculator or notes app. Type your code and press `=`
(calculator) or search for it (notes) to open the app; an optional decoy code opens a separate, empty space.
Shake the phone to return to the disguise. Known limits: the real app name still shows in the system app list,
in system settings and in notification headers; after switching, some launchers take a few seconds to refresh and
drop the old home-screen shortcut (add the new icon from the app drawer). The Play build ships without this feature.
