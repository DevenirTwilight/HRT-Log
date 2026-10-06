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
