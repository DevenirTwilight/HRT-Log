# 04 数据完整性、加密、备份恢复、锁定与伪装（阶段 C，2026-10-12）

基准：默认分支 f53726f。PR #19 不改数据层（`core/data` 无变更），因此本报告的结论同样适用于 PR #19；与 PR #19 相关的一致性问题见 03。

## 1. 已有优势（源码证实，不应为“优化”而更换）

| 机制 | 证据 | 评价 |
|---|---|---|
| 数据库加密 | `DatabaseAccess.kt`：SQLCipher；32 字节随机口令由 Keystore AES-256-GCM 包裹，存 `noBackupFilesDir`，`AtomicFile` 写入；设备未解锁时抛 `DataLockedException`；密钥异常抛 `KeyRecoveryRequired` 且**不删数据**（`data_error` 文案） | 设计合理，失败语义安全 |
| 系统备份关闭 | Manifest `allowBackup="false"` + `data_extraction_rules` | 避免云备份泄露 |
| 截屏/最近任务 | `MainActivity.kt:45` `FLAG_SECURE` | 有效；代价见 07 D-6 |
| 应用锁 | `AppLock.kt`：PIN 4–12 位、Argon2id（19 MiB/2 次）+ Keystore 加密文件、5 次后指数退避，最长 15 分钟、常数时间比较 | 对“随手查看”足够；限制已在 `security_limits` 中如实告知 |
| 加密备份 | `DataTransfer.kt:83-124`：Argon2id（32 MiB/3 次）+ AES-GCM，AAD=魔数；密码 ≥8 位；文件/JSON/行数/深度/字符串上限（`BackupValidation.kt:10-14`） | 规范 |
| 原子恢复 | `NotesRepository.restoreBackup`：先解密、校验版本/必需表，再在 `withTransaction` 内清空、写入、`validateState`；新版本备份拒绝（`NewerBackup`） | 失败不改现有数据 |
| 历史不可改 | `SchemaGuards`：库存账本、时期修订、Visit Pack、化验上下文、方案版本的 UPDATE/DELETE 触发器 | 数据库层强约束，优于只靠应用代码 |
| 迁移 | Schema 1–9 全部导出；`MigrationBaselineTest`、`EncryptionIntegrationTest`（API35 设备）；CI 检查 `git diff --exit-code core/data/schemas` | 有防回归 |
| 无网络 | `scripts/check_release_manifest.py` 在 CI 中检查无 INTERNET | 有防回归 |
| 通知隐私 | `VISIBILITY_SECRET`、中性标题/正文、伪装开启时 `cancelAll()` | 良好 |
| 公开仓库无秘密 | 本审计 `git ls-files` 扫描：只有 `app/debug.keystore`（Android 标准调试密钥，口令 `android`，`build.gradle.kts:17`），用于验收测试；无正式签名材料、无健康数据库/备份/APK；签名备份仓库 `DevenirTwilight/-` 经 GitHub API 确认 `private: true` | 合规 |

## 2. 发现

### D-1 [确认，P2] Trans Memo 导入的明文临时副本可能残留

- `openTransMemo` 先把用户选的另一个应用的 SQLite 文件**原样复制**到 `noBackupFilesDir/import/transmemo.db`（`NotesViewModel.kt:172-177`），然后解析。
- 副本只在两处删除：`runImport` 成功（`:183`）和 `cancelImport`（`:201`）。
- 以下情况会残留：解析失败（`InvalidExport` 被 `dataOp` 捕获）、`runImport` 抛异常、进程在预览阶段被杀、用户没点取消就离开。启动时也没有清理逻辑。
- 文件在应用私有目录，其他应用无法读取，但它是**未加密的健康记录**，与“数据库全加密”的承诺不一致。
- 修复：`try/finally` 删除 + 启动时清理 `import/` 目录。成本 S，无迁移。
- 验收：单测模拟解析失败，断言文件不存在。

### D-2 [确认，P2] 导出文件为明文，但没有提示

CSV（zip）、PDF 报告、Visit Pack PDF、身心摘要都写到用户选择的位置（下载、云盘等）。说明文案（`export_csv_desc`、`export_pdf_desc`、`visit_pdf_note`）只讲内容，**没有说明文件不加密、离开了应用的保护**。建议在导出对话框加一行固定说明。成本 S。

### D-3 [确认，P1] 没有“上次备份”记录，也没有备份提醒

- 全仓库搜索不到 `last_backup`/`lastBackup`。
- 数据只在本机。手机丢失、恢复出厂设置，或个别设备 Keystore 失效（会触发 `KeyRecoveryRequired`）时，**唯一的恢复途径是用户自己导出过的加密备份**。
- 建议：
  - 在设置 → 数据显示“上次备份：日期/从未”（存在 prefs，非健康数据）；
  - 可选每 N 天提醒，通知文字保持中性。
- 无 Schema 变更。成本 S～M。

### D-4 [风险，P2] 伪装模式下，伪装暗码是进入主数据的唯一门槛，且限速较弱

- `Session.requiresAppPin(disguised, pinEnabled)` 在伪装已授权 PRIMARY 时返回 false（`Session.kt`），即输入暗码后**不再要求应用 PIN**。
- 暗码可以只有 4 位。限速是每 10 次失败暂停 60 秒，然后计数归零循环（`Disguise.kt` `MAX_MISSES=10`、`PAUSE_MILLIS=60_000`）。
- 理论上手工穷举 4 位码最坏约 17 小时；而应用锁的退避最长到 15 分钟。
- 这是“不暴露认证界面”的设计取舍，`disguise_limits` 文案已说明有限保护。
- 建议（需决策）：伪装暗码最少 6 位，或采用与应用锁相同的递增退避（静默，不改变计算器外观）。
- 未复现，仅源码推理。

### D-5 [风险，P3] 旋转屏幕可能重新要求 PIN

Activity 未声明 `configChanges`。重建时 `onCreate` 按 `Session.requiresAppPin(...)` 重新设置 `locked`（`MainActivity.kt:53`）。开启应用锁后，旋转或切换系统深色模式可能重新出现锁屏。方向偏安全，但影响使用。需真机确认。

### D-6 [信息] 生物识别使用 `BIOMETRIC_WEAK`，且只是界面门禁，不绑定密钥

数据库密钥不依赖用户认证（`setUserAuthenticationRequired(false)`）。这与文档中“应用锁只控制谁能打开应用”的说明一致，不是缺陷。如果将来做医疗附件库（07 D-7），需要重新评估是否绑定认证。

### D-7 [确认，P3] 少数实体删除不进回收站

复诊问题是硬删除（`NotesRepository.deleteVisitQuestion`），与 §39 回收站的统一语义不一致。

### D-8 [风险，P2] 周期性读路径中包含写操作

- `refresh()` 每 30 s 运行，在读事务中调用 `TimelineV2Migration.plan(...)`，如有需要就 `repo.migrateTimeline(...)` **写库**（`NotesViewModel.kt:57-60`）。
- `exportBackup` 也会先写一次 `RegimenHistory.seed`（`NotesRepository.kt:541`）。
- 迁移本身有幂等测试（`TimelineV2*`）。但如果将来某次规划结果不稳定，就会每 30 s 写一次。
- 建议：迁移改为启动时一次性步骤，并持久化“已完成”标志。

### D-9 [确认，P2] 恢复会整体替换当前数据，没有自动快照

恢复前会显示 `backup_restore_warning`，但如果用户误选了旧备份，当前数据会被完全替换且无法撤销。建议在恢复确认中提供“先导出当前数据的备份”按钮。不建议自动在应用内保留明文快照。

### D-10 [风险，P2] 签名私钥存放在 GitHub 私有仓库

正式签名备份在私有仓库 `DevenirTwilight/-` 中（HANDOFF §7）。私有不等于离线：GitHub 账户或令牌被盗，签名身份就会泄露，攻击者可以发布能“覆盖安装”的恶意更新。建议另做一份离线加密备份（如本地加密盘），并评估仓库中的材料是否经过口令加密。本审计**未读取**该仓库内容。

## 3. 跨模块数据一致性

| 链条 | 状态 | 依据 |
|---|---|---|
| 记录 ↔ 当时药物配置 | 一致：冻结 `config_snapshot`，编辑药物不改历史 | `MedicationSnapshot`、`HistoricalContextTest` |
| 记录 ↔ 治疗时期 | 一致：历史页标签与时间线用同一投影（`AppShell.kt:79-82`、`NotesViewModel.recordLabels`） | `HistoryAttributionTest`、`PeriodStabilityTest` |
| 化验 ↔ 当时方案/估算 | 冻结修订追加，不可更新 | `immutable_lab_context_update` |
| 化验估算 ↔ 浓度页（PR #19） | **不一致**（03 F-PK-2） | — |
| PDF ↔ 浓度页（PR #19） | **不一致**（03 F-PK-1） | — |
| 库存 ↔ 日历 ↔ 通知 | **三种口径**（02 §3.3） | `StockScreen.kt:30`、`CalendarModel.kt:47`、`StockAlerts.kt:29` |
| Visit Pack ↔ 生成时数据 | 记录输入摘要 + 事实 JSON，表不可改 | `immutable_visit_pack_update` |
| 复诊 ↔ 化验 ↔ 文档 | 无关联实体（医疗档案 PLANNED） | `medical-records-roadmap.md` |

## 4. 未来医疗附件库的前置门槛

`docs/design/medical-records-roadmap.md:56-67` 已列出 10 项门槛，覆盖得比较全面。本审计补充三点：

1. 现有备份上限是 32 MiB JSON（`BackupValidation.kt:10-11`），**无法容纳附件**。必须先设计分块或流式备份格式（新 `FORMAT_VERSION`），并保证旧备份仍可恢复。
2. D-1 的临时明文问题会在附件导入中放大，必须先有统一的“临时文件生命周期”工具。
3. `FLAG_SECURE` 会挡住附件预览的截图，这符合预期；但用系统查看器打开 PDF 会离开应用保护范围，需要在应用内渲染。
