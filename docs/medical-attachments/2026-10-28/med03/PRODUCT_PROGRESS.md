# MED-03 PR #145 product candidate

Status: IMPLEMENTED candidate; native acceptance and protected merge PENDING. This is not a delivered APK.

Production path: MainActivity → Medical records → Files → Add file → real SAF → explicit confirmation → existing encrypted transaction. File details → Add link → exact visit/real LabPanel/explicit single result → confirmation → durable relation receipt. Medical forms retain a selection in memory and require a separate attachment confirmation after the real record commits.

No extra ciphertext is created by linking. Trash keeps originals/quota; restoring authenticates content and never revives quarantined relationships. Search uses stored titles and types only. Clinical category editing and permanent destruction remain unavailable.

Evidence: `evidence/data-full-first-green` (664 tests before the final narrowly scoped manifest check); `evidence/local-product-green` (46 app tests and 3 focused migration/manifest tests); `screenshots/manifest.json` (real Compose, scripted metadata only). Earlier failures remain under `evidence/468-*`, `first-targeted`, `extended-fixture-rejected`, and the original Gradle logs. The guarded host allowlist was extended explicitly; test/debug providers remain forbidden in release.

CI must execute `scripts/run_med02_product_native.py` on API26/29/35. It preserves both full MainActivity/SAF business cases, verifies exact test names/no skip, and archives the 3 SQLCipher operation/migration cases before later suites replace reports. No physical device, manual TalkBack, or hardware power-cut claim is made.

Outstanding release gate: final exact-head CI/native results, any defects they expose, protected merge/mainline CI, and unsigned release validation. Issue144 remains open until those facts exist. No signing or private APK delivery authorization applies to this MED-03 candidate.

## 未签名 fullRelease 核对（2026-10-28，本地，源码 4465f518 + 仅文档提交）

`./gradlew :app:assembleFullRelease`（干净工作树，EXIT 0）：`app-full-release-unsigned.apk` 14,387,809 bytes。`aapt2 dump badging`：包名 `net.plainnotes.app`，versionCode 37 / versionName 0.2.0（等于已交付下限 37，**不可直接作为新交付版本**，签名交付必须用源码外参数提升到 ≥38）。四 ABI（arm64-v8a、armeabi-v7a、x86、x86_64）共 8 个 native 库；`zipalign -c -P 16` 通过，所有 64 位 ELF 的 PT_LOAD 对齐 ≥16KiB。合并清单无 `INTERNET`（`check_release_manifest.py` PASS）；APK 内唯一 provider 是 `androidx.startup.InitializationProvider`，没有测试/调试 provider（`check_attachment_release_isolation.py` PASS，1 个 DEX）。未签名，未交付。
