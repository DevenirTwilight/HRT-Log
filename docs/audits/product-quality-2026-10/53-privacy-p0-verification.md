# 53 — PRIVACY-P0 验证记录（PR-B）

2026-10-14。只记录 PR-B（PRIVACY-P0）；PR-A（B2-GATE3）证据见报告52，已合并为 b248294，两批不合并计算。基线 b248294（默认分支 `claude/new-session-1959qb`）。最终 HEAD 的 CI 结果在其结束后写入 HANDOFF/STATUS；在此之前不称通过。

## 交付

| 项 | 文件 |
| --- | --- |
| 公开隐私声明（英文权威） | `PRIVACY.md` |
| 证据表：每条表述 → 源码 / 守卫 / 测试 | `docs/privacy-evidence.md` |
| README 隐私链接、非诊疗目的、版本层次（默认分支 / 私有交付 / 公开 Release / 待审 PR） | `README.md` |
| 应用内离线入口：关于 → 隐私 → 阅读完整隐私说明（全屏、可滚动、无网络/链接） | `ui/PrivacyNotice.kt`、`ui/SettingsScreen.kt` |
| 四语字符串（en、fr、zh-CN、zh-TW，各 24 键） | `res/values*/strings.xml` |
| 发布说明模板（隐私/预期用途段，未发布） | `docs/release-notes/TEMPLATE.md` |
| 法规条件矩阵事实核对注记（结论不变） | `docs/med-eu-privacy-readiness-v2.md` |

## 数据流事实审计（逐条核对源码，摘要）

- 合并后的 fullRelease 清单（`:app:processFullReleaseMainManifest`）无 INTERNET；权限：POST_NOTIFICATIONS、SCHEDULE_EXACT_ALARM、RECEIVE_BOOT_COMPLETED、WAKE_LOCK、FOREGROUND_SERVICE、ACCESS_NETWORK_STATE（后三者来自 WorkManager）、USE_BIOMETRIC/USE_FINGERPRINT、DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION。声明写“无 INTERNET 权限则无法建立网络连接”，不写“技术上绝不可能外传”。
- PNBAK1：Argon2id 32MiB/3/1 → AES-256-GCM，结构化序列化 9，含回收站、不含私人便签/伪装笔记/设置/附件（`DataTransfer.kt`、`RecordBackupCompatibility.kt`）。AppLock 为 Argon2id 19MiB/2/1 哈希 + Keystore，单独描述，不混写为备份参数。
- 导出（CSV/PDF/VisitPack）为明文，经 SAF；提供者可能是云端，外部副本不可撤回。
- 伪装：笔记外壳文本存于普通 SharedPreferences（未加密），声明明确提示；伪装不防 root、系统设置、安装包检查。
- 删除全部数据：删除记录数据库与密钥；导出、备份、外部副本不随之删除。

## 测试（本地，提交前）

- `PrivacyNoticeLayoutTest`（Robolectric API35）16：4 语 × 320/411dp × 1×/2× 字号，明暗交替；每节可滚动到达且可见；标题语义 1+10；所有文本无截断/省略；WCAG AA 4.5:1（onSurface、onSurfaceVariant、primary 对 surface）；入口与关闭 ≥48dp；说明内唯一可点击元素为关闭（无链接/外部跳转）。
- `PrivacyNoticeOlderApiTest`（Robolectric API26、API29）2：zh-TW、320dp、2×、暗色，同一断言。
- `PrivacyNoticeFactsTest` 3：备份 KDF/密码参数与说明一致（参数变化即失败）；源清单无 INTERNET、allowBackup=false 与排除规则；笔记外壳未加密已披露。
- `ShellPrivacyBoundaryTest`（full）3：计算器/笔记外壳不渲染隐私说明及其入口（testTag）、不出现说明标题/入口/备份节文案与真实应用名 `app_name`；外壳源码不引用说明或 AboutScreen。
- `TranslationsTest` 1。
- `:app:testFullDebugUnitTest` 全部 887，0 失败/错误，61 skip 均为既有条件跳过（ExportTest、M2ScenarioVisualAuditTest、ButtonAuditTest）；含 `PeriodStabilityTest`。
- `:app:lintFullDebug` EXIT 0；新增文件无 lint 告警（SettingsScreen 的 3 条 UseKtx 为既有）。

## 不变量

不改 Room schema/guards、PNBAK1 magic/KDF/加密/序列化、PK/M2、versionCode/applicationId、签名、依赖目录与构建脚本；无新权限、无网络、无 WebView、无分析 SDK。

## 未运行 / 局限（如实标注）

- 说明页 UI 测试在 Robolectric（JVM）上运行，不在 CI 模拟器上；真机 TalkBack 焦点遍历 NOT_RUN（只验证语义属性）。
- 无匿名截图入库（Robolectric 截图未作为证据提交）。
- 法律审查：NOT_RUN；本声明是工程事实陈述，不是法律意见。
- 已知缺口（不在声明中宣称）：`THIRD_PARTY_NOTICES.md` 与应用内许可文本未列 BouncyCastle；“删除全部数据”不清除 `stock_alerts` 偏好（仅内部 id 与日期）。

## CI 结果

（待最终 HEAD 的 CI 结束后记录于 HANDOFF/STATUS：run URL、各 job 结论。）
