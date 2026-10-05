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
