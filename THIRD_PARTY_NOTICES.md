# Third-party components

HRT Log's own source code is under the MIT License (see `LICENSE`). The MIT License covers
only material the project has the right to license. The items below keep their own terms.

Runtime components include Kotlin (Apache-2.0), AndroidX/Compose/Material 3/Material Icons/
Room/WorkManager (Apache-2.0), Dagger/Hilt (Apache-2.0), kotlinx.coroutines (Apache-2.0),
Bouncy Castle (MIT, see below) and SQLCipher Android and its native cryptographic dependencies. Their original
copyright and license notices apply; upstream notices are retained in dependency
artifacts. SQLCipher licensing: https://www.zetetic.net/sqlcipher/license/ .
Dagger/Hilt bring in `jakarta.inject:jakarta.inject-api:2.0.1` and `javax.inject:javax.inject:1`
(Apache-2.0); the Jakarta Dependency Injection `NOTICE.md` is shipped in the APK unchanged.

Development-only tools include Gradle (Apache-2.0), JUnit (EPL-2.0), Robolectric
(MIT) and org.json (public domain, tests only). No analytics or advertising SDKs are included.

## Bouncy Castle

`org.bouncycastle:bcprov-jdk18on:1.86` is a runtime dependency of the app and `core/data`. The app uses its Argon2id
implementation for the app lock code hash and the `.pnbak` backup key derivation (parameters unchanged). Licence as
shipped in the artifact (`META-INF/LICENSE.md`; the POM names it the Bouncy Castle Licence,
https://www.bouncycastle.org/licence.html, an MIT licence). The same notice is shown in the app under About →
Open-source licences.

> MIT License (https://opensource.org/licenses/MIT)
>
> Copyright (c) 2000-2026 The Legion of the Bouncy Castle Inc. (https://www.bouncycastle.org).
> Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
> associated documentation files (the "Software"), to deal in the Software without restriction,
> including without limitation the rights to use, copy, modify, merge, publish, distribute,
> sub license, and/or sell copies of the Software, and to permit persons to whom the Software is
> furnished to do so, subject to the following conditions: The above copyright notice and this
> permission notice shall be included in all copies or substantial portions of the Software.
>
> THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT
> NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
> NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
> DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT
> OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

The tests in `tools/attachment-poc` use the same artifact (test scope only).

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

## MED-02B0 synthetic crypto review vectors

`tools/attachment-design-validation/draft_vectors.json` selects AES-GCM cases 91,92,93,130,131,132
from C2SP/Wycheproof commit `7a325932231bd7a724c791c8b0d65ea4d6d2f435`, Apache-2.0.
Original test comments/flags/results retained; no upstream executable code copied. License:
`tools/attachment-design-validation/WYCHEPROOF-LICENSE`. Source URL and full source SHA256
are embedded in the fixture. Independently generated draft transcripts are HRT Log synthetic
design evidence, not a production format or security certification. RFC5869 Appendix A.1
and RFC9106 section 5.3 standard test inputs/outputs are identified in test names and report33.

## MED-02B2 test-only Streaming AEAD evaluation

Tink Java / Android 1.23.0 (`com.google.crypto.tink:tink` / `tink-android`) is used only by
JVM tests and synthetic Android instrumentation, never by app runtime/release.
Copyright Google LLC; Apache License 2.0. Upstream source and license:
https://github.com/tink-crypto/tink-java/tree/v1.23.0
https://github.com/tink-crypto/tink-java/blob/v1.23.0/LICENSE
No upstream source is copied into production. Library use is not project security certification.
