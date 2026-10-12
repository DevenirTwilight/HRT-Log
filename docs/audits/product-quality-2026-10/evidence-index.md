# 证据索引

源码基准：默认分支 `f53726ff6fa52fc5c78806a1576fdb80fcf29987`（D）；PR #19 `aecbd7da843abd3f4334c0979e8265a72e6c67b5`（P19）；PR #18 `68ceac2717b487029d894a76f4185cdf1f890d35`（P18）。

“类型”一列：
- 确认：源码或测试直接证明；
- 风险：有依据但未复现；
- 文档：只有仓库文档的自述，未独立核验。

| 结论 | 类型 | 证据位置 | 基准 |
|---|---|---|---|
| 科研守卫锁定 164 个生产/研究文件，含 strings.xml、ViewModel、数据层，CI 对当前树检查 | 确认 | `docs/pk-research/p2/production-baseline.json`；`tools/pk-research/check_protocol.py:11-40`；`tools/pk-research/test_research.py:107` | D |
| 返回键在任何页面直接结束 Activity | 确认 | `MainActivity.kt:63-65` | D |
| 每 30 s 全量刷新，并无条件计算 Legacy 浓度 | 确认 | `NotesViewModel.kt:43, 45-70, 89-103` | D |
| 投影在组合阶段计算；异常被吞成空结果 | 确认 | `AppShell.kt:80-82`；`NotesViewModel.kt:227-230`；`LongitudinalScreen.kt:33-35` | D |
| 库存三口径 | 确认 | `StockScreen.kt:30-51`；`CalendarModel.kt:47-56`；`StockAlerts.kt:29-41` | D |
| 库存卡列出全部历史包装及 `#id` | 确认 | `StockScreen.kt:65-70`；截图 `docs/ui-audit/2026-10-08-stock-after.png` | D |
| 日历格只用颜色表示状态、无语义 | 确认 | `CalendarScreen.kt:163-179, 206-234` | D |
| 历史页已跳过记录不可点 | 确认 | `HistoryScreen.kt:177` | D |
| 症状日期显示原始字符串 | 确认 | `WellbeingHub.kt:27` | D |
| ImportedPlanDialog 无调用方 | 确认 | `grep prepareImportedLink` 只有定义；`AppShell.kt:206` | D |
| Trans Memo 明文临时文件只在成功/取消时删除 | 确认 | `NotesViewModel.kt:172-183, 201` | D |
| 导出文案未提示明文 | 确认 | `values-zh` 的 `export_csv_desc`/`export_pdf_desc`/`visit_pdf_note` | D |
| 无“上次备份”记录 | 确认 | 全仓库 grep `last_backup|lastBackup` 无结果 | D |
| 伪装暗码 10 次/60 s 限速；伪装授权后不再要求 PIN | 确认（推理部分为风险） | `Disguise.kt` `MAX_MISSES`/`PAUSE_MILLIS`/`check`；`Session.kt` `requiresAppPin` | D |
| 旋转可能重新锁屏 | 风险 | `MainActivity.kt:53`；Manifest 无 `configChanges` | D |
| 备份 Argon2id 32 MiB/3 + AES-GCM，原子恢复 | 确认 | `DataTransfer.kt:83-124`；`NotesRepository.kt:551-565`；`BackupValidation.kt:10-14` | D |
| 数据库密钥由 Keystore 包裹；密钥失败不删数据 | 确认 | `DatabaseAccess.kt` | D |
| 不可变触发器 | 确认 | `SchemaGuards.kt` | D |
| 构建中无源码 SHA；versionCode 固定为 25 | 确认 | `app/build.gradle.kts:4-6`；main 中无 `BuildConfig` 引用 | D |
| 四语 1069 键全部存在 | 确认 | 本审计脚本（08 日志） | D |
| PR #19 未改 `NotesViewModel`/`export`/`LabEstimate` | 确认 | `git diff --stat f53726f aecbd7d -- app/src/main/java/net/plainnotes/app/export app/src/main/java/net/plainnotes/app/NotesViewModel.kt` 为空 | P19 |
| PDF 浓度图用 Legacy `conc.value.result` | 确认 | `NotesViewModel.kt:236-239`；`Exports.kt:183-187` | D=P19 |
| 化验冻结估算用 Legacy | 确认 | `conc/LabEstimate.kt:11-20` | D=P19 |
| M2 公式 B + A·ΣD·H，B=24，A 来自 Price 图 1 加权拟合 | 确认 | `pk-engine/.../experimental/StudyAnchors.kt`；`ResearchDisplayProtocol.kt`；`PreparedResearchHistory.kt` | P19 |
| M2 页面顶部仍是 Legacy 免责声明 | 确认 | P19 `ConcentrationScreen.kt` diff（`pk_disclaimer_short` 保留，后接 `referenceNotice()`） | P19 |
| 能力矩阵 `m2_absolute_pgml=false`、`m2_default_engine_replacement=false` | 确认 | P18 `docs/pk-research/p2/p2ba-evidence-use/p2ba-results.json` | P18 |
| 用户授权 M2-only（§65/§66） | 文档 | P19 `docs/REQUIREMENTS.md` §65–66 | P19 |
| PR #19 自带 P2-AZ 输入副本，不依赖 #17 先合并 | 确认 | `git merge-base --is-ancestor` 为否；P19 树中的 `docs/design/*/p2az*.json` | P19 |
| PR #19 私下交付包的 SHA 和大小 | 文档（未核验） | PR #19 描述 | P19 |
| Legacy 验证 = 文献均值 ±1 SD；SL 使用代理 AUC | 文档 | `docs/pk-model.md:131-156` | D |
| 独立外部人体验证 = 0 | 文档 | HANDOFF P2-AV/AX；P18 `independent_study_count=3`、`human_patient_absolute_predictions_validated=false` | D/P18 |
| v0.2.0 含未获许可的移植代码 | 文档 | `docs/licensing.md` | D |
| 签名备份仓库为私有 | 确认 | GitHub API `private: true` | — |
| 公开仓库无正式签名材料（仅 debug.keystore） | 确认 | `git ls-files` 扫描 | D |
| HANDOFF 顶部 P2-AX 状态过期 | 确认 | `HANDOFF.md:19` 对照 git 中 PR #14 的合并 | D |
| 日历两位数日期被裁 | 历史问题，已修复，UNVERIFIED | `docs/ui-button-consistency.md:109`；`CalendarScreen.kt:375-392` | D |
