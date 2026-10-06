# Third-party components

Runtime components include Kotlin (Apache-2.0), AndroidX/Compose/Material 3/Material Icons/
Room/WorkManager (Apache-2.0), Dagger/Hilt (Apache-2.0), kotlinx.coroutines (Apache-2.0), and
SQLCipher Android and its native cryptographic dependencies. Their original
copyright and license notices apply; upstream notices are retained in dependency
artifacts. SQLCipher licensing: https://www.zetetic.net/sqlcipher/license/ .

Development-only tools include Gradle (Apache-2.0), JUnit (EPL-2.0), Robolectric
(MIT) and org.json (public domain, tests only). No analytics or advertising SDKs are included.

## Concentration model

The concentration models (`pk-engine`, `tools/pk-fit`) and the lab calibration were written for
HRT Log from published pharmacokinetic studies; parameters and references are in
`pk-engine/src/main/resources/pk-params.json` and `docs/pk-model.md`. No third-party code is used.
The original 0.2.0 (build 2) contained a port of Transmtf HRT Tracker's model (MIT; its own
upstream had no license). That port was removed during development and is absent from
the replacement 0.2.0 emergency hotfix (build 4). See `docs/licensing.md`.

The HRT tracker importer reads the JSON export format of Transmtf HRT Tracker; it contains no
code from that project.

Charts are drawn with Compose Canvas; Vico is not shipped.
