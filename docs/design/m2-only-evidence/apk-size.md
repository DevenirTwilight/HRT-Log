# APK size audit

All packages are compared using their actual byte size; all comparisons require matching source revision, release mode and build tools.

## Summary

| APK | Size (MB decimal) | vs first APK | SHA256 prefix |
|---|---:|---:|---|
| `m2-only-before-unsigned.apk` | 12.743 | +0.000 MB (+0.00%) | `25236d8368ce` |
| `app-full-release-unsigned.apk` | 12.755 | +0.012 MB (+0.10%) | `75a804b3c383` |

## m2-only-before-unsigned.apk

- Total: 12,743,143 B (12.743 MB)
- SHA256: `25236d8368ce2aeb9d497bbb475de101102b0f914ff37a6038ec0cf6cb0730c0`
- ZIP file entries: 406

| Package component | Compressed (MB) | Uncompressed (MB) | Entries |
|---|---:|---:|---:|
| `lib/x86_64` | 2.533 | 5.757 | 2 |
| `lib/x86` | 2.325 | 4.933 | 2 |
| `lib/arm64-v8a` | 2.290 | 5.198 | 2 |
| `dex` | 2.279 | 4.541 | 1 |
| `lib/armeabi-v7a` | 1.882 | 3.565 | 2 |
| `resources.arsc` | 1.140 | 1.140 | 1 |
| `res/` | 0.129 | 0.208 | 292 |
| `other` | 0.074 | 0.448 | 8 |
| `META-INF/` | 0.023 | 0.064 | 86 |
| `kotlin/` | 0.012 | 0.051 | 8 |
| `profiles` | 0.009 | 0.009 | 2 |
| ZIP metadata, alignment and other overhead | 0.048 | — | — |

**Largest entries by ZIP compressed size**

| Member | Compressed MB | Raw MB |
|---|---:|---:|
| `lib/x86_64/libsqlcipher.so` | 2.528 | 5.746 |
| `lib/x86/libsqlcipher.so` | 2.320 | 4.923 |
| `lib/arm64-v8a/libsqlcipher.so` | 2.285 | 5.188 |
| `classes.dex` | 2.279 | 4.541 |
| `lib/armeabi-v7a/libsqlcipher.so` | 1.878 | 3.557 |
| `resources.arsc` | 1.140 | 1.140 |
| `pk-params.json` | 0.040 | 0.259 |
| `wellbeing-translations.json` | 0.008 | 0.035 |
| `assets/dexopt/baseline.prof` | 0.008 | 0.008 |
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

- Total: 12,755,451 B (12.755 MB)
- SHA256: `75a804b3c383ed311d594d3f50cf532563e8fc087040d3755bba46e54249ef3b`
- ZIP file entries: 406

| Package component | Compressed (MB) | Uncompressed (MB) | Entries |
|---|---:|---:|---:|
| `lib/x86_64` | 2.533 | 5.757 | 2 |
| `lib/x86` | 2.325 | 4.933 | 2 |
| `lib/arm64-v8a` | 2.290 | 5.198 | 2 |
| `dex` | 2.281 | 4.545 | 1 |
| `lib/armeabi-v7a` | 1.882 | 3.565 | 2 |
| `resources.arsc` | 1.150 | 1.150 | 1 |
| `res/` | 0.129 | 0.208 | 292 |
| `other` | 0.074 | 0.448 | 8 |
| `META-INF/` | 0.023 | 0.064 | 86 |
| `kotlin/` | 0.012 | 0.051 | 8 |
| `profiles` | 0.009 | 0.009 | 2 |
| ZIP metadata, alignment and other overhead | 0.048 | — | — |

**Largest entries by ZIP compressed size**

| Member | Compressed MB | Raw MB |
|---|---:|---:|
| `lib/x86_64/libsqlcipher.so` | 2.528 | 5.746 |
| `lib/x86/libsqlcipher.so` | 2.320 | 4.923 |
| `lib/arm64-v8a/libsqlcipher.so` | 2.285 | 5.188 |
| `classes.dex` | 2.281 | 4.545 |
| `lib/armeabi-v7a/libsqlcipher.so` | 1.878 | 3.557 |
| `resources.arsc` | 1.150 | 1.150 |
| `pk-params.json` | 0.040 | 0.259 |
| `wellbeing-translations.json` | 0.008 | 0.035 |
| `assets/dexopt/baseline.prof` | 0.008 | 0.008 |
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
