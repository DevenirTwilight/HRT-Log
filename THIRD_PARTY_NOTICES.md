# Third-party components

Runtime components include Kotlin (Apache-2.0), AndroidX/Compose/Material 3/Material Icons/
Room/WorkManager (Apache-2.0), Dagger/Hilt (Apache-2.0), kotlinx.coroutines (Apache-2.0), and
SQLCipher Android and its native cryptographic dependencies. Their original
copyright and license notices apply; upstream notices are retained in dependency
artifacts. SQLCipher licensing: https://www.zetetic.net/sqlcipher/license/ .

Development-only tools include Gradle (Apache-2.0), JUnit (EPL-2.0), Robolectric
(MIT) and org.json (public domain, tests only). No analytics or advertising SDKs are included.

## Transmtf-HRT-Tracker (MIT)

`pk-engine` contains a Kotlin port of `pk.ts` and `personalModel.ts` (default EKF lab
calibration) from TransmtfTeam/Transmtf-HRT-Tracker, commit 8c9abdde:
https://github.com/TransmtfTeam/Transmtf-HRT-Tracker . Copyright (c) 2025 Transmtf Team,
MIT License — full text in `pk-engine/UPSTREAM_LICENSE` and shown in the app's About page.
`tools/pk-reference/upstream/` holds the unmodified upstream files used only to generate
test fixtures.

### Upstream of the PK model (license pending)

Transmtf HRT Tracker states that its pharmacokinetic algorithms, models and parameters are derived
directly from `PKcore.swift` / `PKparameter.swift` in LaoZhong-Mihari/HRT-Recorder-PKcomponent-Test:
https://github.com/LaoZhong-Mihari/HRT-Recorder-PKcomponent-Test . That repository has no license file,
so permission to reuse this part is not yet established. See `docs/licensing.md` for the status.

Charts are drawn with Compose Canvas; Vico is not shipped.
