# APK size audit

All packages are compared using their actual byte size; all comparisons require matching source revision, release mode and build tools.

## Summary

| APK | Size (MB decimal) | vs first APK | SHA256 prefix |
|---|---:|---:|---|
| `default-unsigned.apk` | 12.708 | +0.000 MB (+0.00%) | `463f0b045135` |
| `old-pr3-unsigned.apk` | 12.813 | +0.105 MB (+0.83%) | `107c03b40c7a` |
| `unified-unsigned.apk` | 12.769 | +0.061 MB (+0.48%) | `50cbc3bee873` |

## default-unsigned.apk

- Total: 12,707,919 B (12.708 MB)
- SHA256: `463f0b045135226fd49f077938182077b79472cd4345e4abd9cc8c3cdb46efd0`
- ZIP file entries: 406

| Package component | Compressed (MB) | Uncompressed (MB) | Entries |
|---|---:|---:|---:|
| `lib/x86_64` | 2.533 | 5.757 | 2 |
| `lib/x86` | 2.325 | 4.933 | 2 |
| `lib/arm64-v8a` | 2.290 | 5.198 | 2 |
| `dex` | 2.264 | 4.514 | 1 |
| `lib/armeabi-v7a` | 1.882 | 3.565 | 2 |
| `resources.arsc` | 1.118 | 1.118 | 1 |
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
| `classes.dex` | 2.264 | 4.514 |
| `lib/armeabi-v7a/libsqlcipher.so` | 1.878 | 3.557 |
| `resources.arsc` | 1.118 | 1.118 |
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

## old-pr3-unsigned.apk

- Total: 12,812,823 B (12.813 MB)
- SHA256: `107c03b40c7a61eabb09d8aaf7298a6a8035c7f92460b3718a16dbd43f574f74`
- ZIP file entries: 406

| Package component | Compressed (MB) | Uncompressed (MB) | Entries |
|---|---:|---:|---:|
| `lib/x86_64` | 2.533 | 5.757 | 2 |
| `lib/x86` | 2.325 | 4.933 | 2 |
| `dex` | 2.301 | 4.582 | 1 |
| `lib/arm64-v8a` | 2.290 | 5.198 | 2 |
| `lib/armeabi-v7a` | 1.882 | 3.565 | 2 |
| `resources.arsc` | 1.186 | 1.186 | 1 |
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
| `classes.dex` | 2.301 | 4.582 |
| `lib/arm64-v8a/libsqlcipher.so` | 2.285 | 5.188 |
| `lib/armeabi-v7a/libsqlcipher.so` | 1.878 | 3.557 |
| `resources.arsc` | 1.186 | 1.186 |
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

## unified-unsigned.apk

- Total: 12,768,827 B (12.769 MB)
- SHA256: `50cbc3bee87381f0e1d9b5e8422c9f70606cdb1cd45f272feb820a5bc3de6936`
- ZIP file entries: 406

| Package component | Compressed (MB) | Uncompressed (MB) | Entries |
|---|---:|---:|---:|
| `lib/x86_64` | 2.533 | 5.757 | 2 |
| `lib/x86` | 2.325 | 4.933 | 2 |
| `dex` | 2.296 | 4.575 | 1 |
| `lib/arm64-v8a` | 2.290 | 5.198 | 2 |
| `lib/armeabi-v7a` | 1.882 | 3.565 | 2 |
| `resources.arsc` | 1.147 | 1.147 | 1 |
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
| `classes.dex` | 2.296 | 4.575 |
| `lib/arm64-v8a/libsqlcipher.so` | 2.285 | 5.188 |
| `lib/armeabi-v7a/libsqlcipher.so` | 1.878 | 3.557 |
| `resources.arsc` | 1.147 | 1.147 |
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
