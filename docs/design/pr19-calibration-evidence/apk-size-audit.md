# APK size audit

All packages are compared using their actual byte size; all comparisons require matching source revision, release mode and build tools.

## Summary

| APK | Size (MB decimal) | vs first APK | SHA256 prefix |
|---|---:|---:|---|
| `pr19-before-calibration-unsigned.apk` | 12.736 | +0.000 MB (+0.00%) | `c145fb953f5c` |
| `app-full-release-unsigned.apk` | 12.742 | +0.005 MB (+0.04%) | `91ac845fecaa` |

## pr19-before-calibration-unsigned.apk

- Total: 12,736,319 B (12.736 MB)
- SHA256: `c145fb953f5c6ae0ab1963fff0bce758f65f8699cc3135f53e7ad9fe7ea39b3a`
- ZIP file entries: 406

| Package component | Compressed (MB) | Uncompressed (MB) | Entries |
|---|---:|---:|---:|
| `lib/x86_64` | 2.533 | 5.757 | 2 |
| `lib/x86` | 2.325 | 4.933 | 2 |
| `lib/arm64-v8a` | 2.290 | 5.198 | 2 |
| `dex` | 2.275 | 4.532 | 1 |
| `lib/armeabi-v7a` | 1.882 | 3.565 | 2 |
| `resources.arsc` | 1.136 | 1.136 | 1 |
| `res/` | 0.129 | 0.208 | 292 |
| `other` | 0.074 | 0.448 | 8 |
| `META-INF/` | 0.023 | 0.064 | 86 |
| `kotlin/` | 0.012 | 0.051 | 8 |
| `profiles` | 0.011 | 0.011 | 2 |
| ZIP metadata, alignment and other overhead | 0.048 | — | — |

**Largest entries by ZIP compressed size**

| Member | Compressed MB | Raw MB |
|---|---:|---:|
| `lib/x86_64/libsqlcipher.so` | 2.528 | 5.746 |
| `lib/x86/libsqlcipher.so` | 2.320 | 4.923 |
| `lib/arm64-v8a/libsqlcipher.so` | 2.285 | 5.188 |
| `classes.dex` | 2.275 | 4.532 |
| `lib/armeabi-v7a/libsqlcipher.so` | 1.878 | 3.557 |
| `resources.arsc` | 1.136 | 1.136 |
| `pk-params.json` | 0.040 | 0.259 |
| `assets/dexopt/baseline.prof` | 0.009 | 0.009 |
| `wellbeing-translations.json` | 0.008 | 0.035 |
| `symptom-sources.json` | 0.007 | 0.039 |
| `org/bouncycastle/x509/CertPathReviewerMessages.properties` | 0.007 | 0.047 |
| `org/bouncycastle/x509/CertPathReviewerMessages_de.properties` | 0.007 | 0.050 |
| `kotlin/kotlin.kotlin_builtins` | 0.005 | 0.029 |
| `lib/x86/libandroidx.graphics.path.so` | 0.005 | 0.009 |
| `lib/x86_64/libandroidx.graphics.path.so` | 0.005 | 0.011 |

**Native libraries (all entries)**

| Member | Compressed bytes | Raw bytes | SQLCipher |
|---|---:|---:|---|
| `lib/arm64-v8a/libandroidx.graphics.path.so` | 4386 | 10096 | False |
| `lib/arm64-v8a/libsqlcipher.so` | 2285379 | 5187544 | True |
| `lib/armeabi-v7a/libandroidx.graphics.path.so` | 4064 | 7252 | False |
| `lib/armeabi-v7a/libsqlcipher.so` | 1877679 | 3557424 | True |
| `lib/x86/libandroidx.graphics.path.so` | 4782 | 9284 | False |
| `lib/x86/libsqlcipher.so` | 2319848 | 4923492 | True |
| `lib/x86_64/libandroidx.graphics.path.so` | 4614 | 10760 | False |
| `lib/x86_64/libsqlcipher.so` | 2528107 | 5746024 | True |

Caution: ZIP component sizes do not measure Android runtime RAM usage, installed storage expansion, cold-start time or clinical/functional correctness.

## app-full-release-unsigned.apk

- Total: 12,741,571 B (12.742 MB)
- SHA256: `91ac845fecaa3e1e0f451b22f9e47064f0de6e59204d5192f735b9bfd321b6fb`
- ZIP file entries: 406

| Package component | Compressed (MB) | Uncompressed (MB) | Entries |
|---|---:|---:|---:|
| `lib/x86_64` | 2.533 | 5.757 | 2 |
| `lib/x86` | 2.325 | 4.933 | 2 |
| `lib/arm64-v8a` | 2.290 | 5.198 | 2 |
| `dex` | 2.278 | 4.536 | 1 |
| `lib/armeabi-v7a` | 1.882 | 3.565 | 2 |
| `resources.arsc` | 1.138 | 1.138 | 1 |
| `res/` | 0.129 | 0.208 | 292 |
| `other` | 0.074 | 0.448 | 8 |
| `META-INF/` | 0.023 | 0.064 | 86 |
| `kotlin/` | 0.012 | 0.051 | 8 |
| `profiles` | 0.011 | 0.011 | 2 |
| ZIP metadata, alignment and other overhead | 0.048 | — | — |

**Largest entries by ZIP compressed size**

| Member | Compressed MB | Raw MB |
|---|---:|---:|
| `lib/x86_64/libsqlcipher.so` | 2.528 | 5.746 |
| `lib/x86/libsqlcipher.so` | 2.320 | 4.923 |
| `lib/arm64-v8a/libsqlcipher.so` | 2.285 | 5.188 |
| `classes.dex` | 2.278 | 4.536 |
| `lib/armeabi-v7a/libsqlcipher.so` | 1.878 | 3.557 |
| `resources.arsc` | 1.138 | 1.138 |
| `pk-params.json` | 0.040 | 0.259 |
| `assets/dexopt/baseline.prof` | 0.010 | 0.010 |
| `wellbeing-translations.json` | 0.008 | 0.035 |
| `symptom-sources.json` | 0.007 | 0.039 |
| `org/bouncycastle/x509/CertPathReviewerMessages.properties` | 0.007 | 0.047 |
| `org/bouncycastle/x509/CertPathReviewerMessages_de.properties` | 0.007 | 0.050 |
| `kotlin/kotlin.kotlin_builtins` | 0.005 | 0.029 |
| `lib/x86/libandroidx.graphics.path.so` | 0.005 | 0.009 |
| `lib/x86_64/libandroidx.graphics.path.so` | 0.005 | 0.011 |

**Native libraries (all entries)**

| Member | Compressed bytes | Raw bytes | SQLCipher |
|---|---:|---:|---|
| `lib/arm64-v8a/libandroidx.graphics.path.so` | 4386 | 10096 | False |
| `lib/arm64-v8a/libsqlcipher.so` | 2285379 | 5187544 | True |
| `lib/armeabi-v7a/libandroidx.graphics.path.so` | 4064 | 7252 | False |
| `lib/armeabi-v7a/libsqlcipher.so` | 1877679 | 3557424 | True |
| `lib/x86/libandroidx.graphics.path.so` | 4782 | 9284 | False |
| `lib/x86/libsqlcipher.so` | 2319848 | 4923492 | True |
| `lib/x86_64/libandroidx.graphics.path.so` | 4614 | 10760 | False |
| `lib/x86_64/libsqlcipher.so` | 2528107 | 5746024 | True |

Caution: ZIP component sizes do not measure Android runtime RAM usage, installed storage expansion, cold-start time or clinical/functional correctness.
