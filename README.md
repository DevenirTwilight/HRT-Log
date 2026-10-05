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

## M1

See [`docs/milestones/M1.md`](docs/milestones/M1.md) for implementation details,
test evidence, limitations and device verification. PK and import modules are
reserved for M4/M5, and deliberately do not implement model defaults or import mappings.
Health information must never be committed. Use only synthetic test data.

## Disguise mode (full build only)

Settings → Disguise mode swaps the launcher icon for a working calculator or notes app. Type your code and press `=`
(calculator) or search for it (notes) to open the app; an optional decoy code opens a separate, empty space.
Shake the phone to return to the disguise. Known limits: the real app name still shows in the system app list,
in system settings and in notification headers; after switching, some launchers take a few seconds to refresh and
drop the old home-screen shortcut (add the new icon from the app drawer). The Play build ships without this feature.
