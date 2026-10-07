# Third-party components

HRT Log's own source code is under the MIT License (see `LICENSE`). The MIT License covers
only material the project has the right to license. The items below keep their own terms.

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

## Build tooling in this repository

`gradlew`, `gradlew.bat` and `gradle/wrapper/gradle-wrapper.jar` are part of Gradle
(Copyright the original authors, Apache-2.0); their own notices apply. `app/debug.keystore` is a
public debug key for CI and local builds only; it is not the official signing key.

## Quoted official texts and reference data

These files contain material quoted from, or derived from, third-party sources. They are not
relicensed under MIT; each item remains subject to its source's terms.

- `app/src/main/resources/symptom-sources.json`: short verbatim quotations from official
  medicine leaflets and public health documents (French BDPM / ANSM, HAS, Taiwan MOHW,
  mainland China package inserts via third-party reposts), with source, URL and date for
  each item. Used to show users what the official documents say; HRT Log does not endorse
  or interpret them.
- `app/src/main/resources/wellbeing-translations.json`: unofficial translations of those
  quotations made for HRT Log. The translations do not replace the original texts.
- `pk-engine/src/main/resources/pk-params.json` and `docs/pk-research/`: values and short
  quotations from published labels and studies, each with a citation.

Symptom scales such as GENDER-Q and GCLS are not included; see `docs/licensing.md`.
