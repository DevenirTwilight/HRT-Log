# 01 当前功能全景与实施状态矩阵（阶段 B，2026-10-12）

审计基准：默认分支 `claude/new-session-1959qb` @ `f53726ff6fa52fc5c78806a1576fdb80fcf29987`（正式源码基线）；PR #19 @ `aecbd7da843abd3f4334c0979e8265a72e6c67b5`（未合并的最新产品方向，只改浓度页相关代码，见 §3）。

## 1. 状态定义

| 状态 | 含义 | 最低证据 |
|---|---|---|
| `DEFAULT_IMPLEMENTED` | 默认分支源码中存在，并从导航可达 | 源码路径 + 入口 |
| `PR_IMPLEMENTED` | 只在 PR #19 分支存在，**未合并** | PR 分支路径 + SHA |
| `PARTIAL` | 核心存在，但缺明确子能力 | 源码 + 缺口说明 |
| `PLANNED` | 只有设计/路线图，无产品代码 | 文档路径 |
| `DEAD_CODE` | 代码存在但无调用入口 | grep 结果 |
| `UNVERIFIED` | 声称已修复/已验收，本审计无法复现（无 SDK/设备） | 文档声明 |
| `DEPRECATED` | 已明确废弃 | 规则文档 |

“测试”一列只列存在的测试文件名，**不代表本审计在本地运行过**（Android 测试本地 BLOCKED，见 `08-verification-log.md`）；CI 记录中这些测试在默认分支 f53726f 上为 success。

## 2. 导航结构（实测源码）

`app/src/main/java/net/plainnotes/app/ui/AppShell.kt:34-46` 定义 11 个一级入口：日历、时间线、复诊、历史、库存、药物、身心状态、血药浓度、化验、（分隔线）设置、关于。全部 `ready=true`，因此 `AppShell.kt:199` 的 `ComingSoonScreen` 分支不可达。抽屉已可滚动（F1 修复，`AppShell.kt:240-256`，`DrawerScrollUiTest`）。没有底部导航、没有返回栈：系统返回键在任何页面直接 `finish()`（`MainActivity.kt:64`），只有复诊详情有自己的 `BackHandler`。

## 3. 功能矩阵

| # | 功能域 | 状态 | 用户入口 | 关键源码 | 数据/持久化 | 已有测试 | 主要缺口/风险（详见 02/04/05） |
|---|---|---|---|---|---|---|---|
| 1 | 日历：日/周/月/年视图、当日待服、已记录、漏服/未确认、预约、缺货标记 | DEFAULT_IMPLEMENTED | 默认首页 | `ui/CalendarScreen.kt:49-106`、`ui/CalendarModel.kt` | `RecordEntity`、`Slot`（`core/domain/ScheduleEngine.kt`） | `CalendarModelTest`、截图审计 | 默认月视图把当日待办放在月格下方；月/年格状态只用颜色表达，无逐格语义描述（02 §3.1） |
| 2 | 服药确认、改期、跳过、改剂量 | DEFAULT_IMPLEMENTED | 日历卡片“完成/改期”；通知按钮 | `ui/Dialogs.kt:33-78`、`NotesRepository.complete/override` | `RecordEntity`、`OverrideEntity` | `ScheduleTest`、`BackfillTest`、`PlanEditTest` | 确认需两次点击（卡片→对话框→保存）；通知有“已服”操作（`ReminderCoordinator.kt:133-134`） |
| 3 | 计划外服药、批量补录（≤上限天数） | DEFAULT_IMPLEMENTED | 日历 FAB、历史页 | `Dialogs.kt:85-175`、`NotesRepository.backfill/unscheduled` | `RecordEntity` | `BackfillTest`、`TimesPerDayTest` | 批量补录不扣库存（有意，`Dialogs.kt:134` 注释） |
| 4 | 药物管理：名称、成分/酯、途径、剂量、规则（每 N 天/每 N 小时/每周）、多时刻不同剂量、提醒、库存参数、PK 输入 | DEFAULT_IMPLEMENTED | 药物页、日历 FAB | `ui/MedicationEditor.kt`、`ui/MedicationsScreen.kt`、`NotesRepository.saveMedication` | `MedicationEntity`、`RuleEntity`、`TimeEntity`、`ProfileEntity`、`RegimenVersionEntity` | `PlanEditTest`、`RegimenHistoryTest` | “删除”实为归档（`MedicationsScreen.kt` 菜单图标 Archive）；完整药物修订链未实现（BACKLOG 已列） |
| 5 | 历史快照：记录冻结当时药物/规则配置 | DEFAULT_IMPLEMENTED | 历史页显示 | `data/MedicationSnapshot.kt`、`HistoricalContext` | `config_snapshot` 列 | `HistoricalContextTest`、`HistoryIntegrityTest` | — |
| 6 | 历史页：7/30/90/全部、按药物筛选、按天分组、依从率、编辑/补录/删除/确认漏服/标记额外剂量 | DEFAULT_IMPLEMENTED | 抽屉“历史” | `ui/HistoryScreen.kt:55-219` | `RecordEntity`、`RecordAnnotationEntity` | `HistoryAttributionTest`、`DataRefreshTest` | 已跳过的记录不可点击（`HistoryScreen.kt:177`），历史页无法撤销误跳过；无搜索 |
| 7 | 治疗时期/时间线：自动识别、合并、手动新建/编辑/删除时期、里程碑、导入历史块、未知区间 | DEFAULT_IMPLEMENTED | 抽屉“时间线” | `ui/LongitudinalScreen.kt:29-234`、`timeline/*`、`domain/TreatmentPeriods.kt`、`data/HistoryPeriods.kt` | `HistoryPeriodEntity`、`MilestoneEntity`、`RegimenVersionEntity` | `PeriodStabilityTest`（交付门槛）、`TimelineV2*`、`TimelineEdit*`、`SameCardSplitTest` 等 | 投影在 Compose 主线程构建；时间线页每 60 s 重算一次，其他页在数据实际变化时重算（`ExtraState` 是 data class，无变化的 30 s 轮询不会触发）（05）；非简洁模式卡片文字密度高（02 §3.5） |
| 8 | 回收站：删除后可恢复/彻底删除（里程碑、化验、预约、阶段回顾、症状、评分、笔记、记录、时期、更正） | DEFAULT_IMPLEMENTED | 设置 → 回收站 | `data/Trash.kt`、`ui/RecycleBin.kt` | `TrashItemEntity` | `TrashDataTest`、`RecycleBinUiTest` | 复诊问题删除是硬删除，不进回收站（`NotesRepository.deleteVisitQuestion`） |
| 9 | 库存：包装容量、开封、补充、更正余量、换新、来源/批号、开封后有效期、低库存与有效期通知 | DEFAULT_IMPLEMENTED | 抽屉“库存”；日历预测卡 | `ui/StockScreen.kt`、`data/SupplyLedger.kt`、`reminder/StockAlerts.kt` | `ContainerEntity`、`SupplyEntryEntity` | `SupplyLedgerTest`、`StockAlertsTest` | 三种“还剩多久”口径（02 §3.3）；历史空包装无限累积在卡片下方并显示内部 `#id`；无包装出厂有效期字段 |
| 10 | 身心状态：每日评分/笔记、症状勾选（按地区来源）、阶段回顾、评分项管理、摘要 PDF | DEFAULT_IMPLEMENTED | 抽屉“身心状态”（3 个标签） | `ui/WellbeingHub.kt`、`WellbeingScreen.kt`、`SymptomsScreen.kt`、`StageReviewScreen.kt`、`export/WellbeingSummary.kt` | `CheckinItem/Score`、`DayNote`、`SymptomCheck`、`StageReview`、`ReviewEffect` | `WellbeingUiTest`、`WellbeingRecordsTest`、`SymptomCatalogTest` | 症状页日期按钮显示原始 ISO 字符串（`WellbeingHub.kt:27`） |
| 11 | 化验：14 种分析物、单位换算、参考范围、实验室、备注、趋势图、P4 检测方法区分 | DEFAULT_IMPLEMENTED | 抽屉“化验” | `ui/LabsScreen.kt` | `LabValueEntity`、`AnalyteEntity` | `LabContextTest` | 每次只能录入一个分析物（同次抽血多项需重复填写）；趋势图无时间范围选择；参考范围取最近一条 |
| 12 | 化验上下文：冻结采样时的方案/近期服药/可选未校准估算 | DEFAULT_IMPLEMENTED | 化验卡片内 | `data/LabContext.kt`、`ui/LabContextSection.kt`、`conc/LabEstimate.kt` | `LabContextEntity`（修订追加） | `LabContextTest`、`LabEstimateTest`、`LabContextUiTest` | PR #19 下仍冻结 Legacy 舌下估算（03） |
| 13 | 同次采样 LabPanel | PLANNED（PARTIAL 底座） | — | BACKLOG “LabPanel” 行 | — | — | 无采样分组实体 |
| 14 | 血药浓度（默认分支）：Legacy 群体模型、LabFit 化验校准（回顾/因果）、5–95% 区间、7/14/60 天、单位、精确点击、其他药物曲线、缺失输入引导 | DEFAULT_IMPLEMENTED | 抽屉“血药浓度”（首次显示免责声明） | `ui/ConcentrationScreen.kt`、`conc/ConcentrationCalculator.kt`、`pk-engine/Engine.kt`、`LabFit.kt` | `PkSettingsEntity`、prefs `conc_*` | `ConcentrationCalculatorTest`、`EngineTest`、`LabFitTest`、`LiteratureValidationTest`、P1 系列 | 大号“当前估算 pg/mL”；科学边界见 03 |
| 15 | 血药浓度（PR #19）：仅 M2 舌下游离 E2 文献参考曲线，固定 p2x-39 + Price 1997 幅度；混合途径不给合并总 E2；其他途径保留兼容计算 | **PR_IMPLEMENTED**（未合并） | 同一入口 | PR 分支 `experimental/UnifiedConcentrationScreen.kt`、`pk/experimental/*`、`ConcentrationScreen.kt` diff | 无 Schema 变更 | PR 新增约 20 个测试文件（03） | PDF 图表与化验估算仍走 Legacy（03 F-PK-1/2）；大号 pg/mL 数字仍是视觉中心 |
| 16 | M1 / 三模型切换 / 独立实验页 | DEPRECATED（被 PR #19 §65 退役） | — | PR #19 REQUIREMENTS §65 | — | — | 研究代码保留 |
| 17 | 复诊：预约（类型/时间/医生/地点/提醒）、确认就诊、问题清单（排序/已问/回答）、Visit Pack PDF（可选章节、事实预览、生成记录摘要） | DEFAULT_IMPLEMENTED | 抽屉“复诊”；日历预约卡 | `ui/VisitsScreen.kt`、`visit/VisitPack.kt`、`export/Exports.kt` | `AppointmentEntity`、`VisitQuestionEntity`、`VisitPackEntity` | `VisitPackTest`、`VisitPackDataTest` | 生成记录显示 12 位摘要哈希（技术细节外露）；PDF 不留存在应用内 |
| 18 | 医疗档案（附件、医生/机构、跨模块关联） | PLANNED | — | `docs/design/medical-records-roadmap.md` | — | — | 文件加密等 10 项门槛未设计 |
| 19 | 导入：Trans Memo（SQLite）、HRT Tracker（JSON），预览/冲突选择/覆盖或合并 | DEFAULT_IMPLEMENTED | 设置 → 数据 | `importer/TransMemo.kt`、`HrtTracker.kt`、`ui/DataSection.kt`、`ui/HtImport.kt` | 导入记录带来源 | `TransMemoTest`、`HrtTrackerTest`、`HrtTrackerImportTest`、`HtImportWizardTest`、`ImportedHistory*` | Trans Memo 明文临时副本在失败/中断时不清理（04） |
| 20 | 导入记录手动关联计划槽（ImportedPlanDialog） | DEAD_CODE | 无 | `ui/HistoryScreen.kt:234`、`NotesViewModel.prepareImportedLink` 无调用者 | — | — | HANDOFF P1 报告已注明“不可达” |
| 21 | 导出：CSV（zip）、PDF 报告（可含浓度图）、身心状态摘要 PDF | DEFAULT_IMPLEMENTED | 设置 → 数据；身心状态顶栏 | `export/Exports.kt`、`NotesViewModel.exportCsv/exportPdf` | — | `ExportTest`、`WellbeingSummaryTest` | 未告知导出文件为明文（04） |
| 22 | 加密备份/恢复（密码 ≥8 字符，大小上限，版本检查） | DEFAULT_IMPLEMENTED | 设置 → 数据 | `data/DataTransfer.kt`、`BackupValidation.kt` | 单文件 | `DataTransferTest`、`EncryptionIntegrationTest`、`MigrationBaselineTest` | 04 详审 |
| 23 | 数据库加密 SQLCipher + Keystore | DEFAULT_IMPLEMENTED | — | `data/DatabaseAccess.kt` | — | `KeyBoundaryTest`、`EncryptionIntegrationTest` | 04 |
| 24 | 应用锁（PIN Argon2id + Keystore、失败退避、生物识别 WEAK、离开后锁定时间） | DEFAULT_IMPLEMENTED | 设置 → 隐私 | `security/AppLock.kt`、`ui/PrivacySection.kt`、`ui/LockScreen.kt` | `noBackupFilesDir/lock.bin` | `AppLockTest`、`SessionTest` | 04 |
| 25 | 伪装模式（计算器/便签外壳、暗码、摇晃退出、独立私密便签库） | DEFAULT_IMPLEMENTED（full） | 设置 → 伪装 | `app/src/full/.../disguise/*` | 独立加密库 | `CalcTest`、`PrivateBoundaryTest`、`PrivateStoreTest`、`DisguiseFlowAndroidTest`、`ProcessRestartAndroidTest` | 04 |
| 26 | 通知隐私（中性标题/正文、锁屏 SECRET、详细内容开关） | DEFAULT_IMPLEMENTED | 设置 → 隐私 | `reminder/ReminderCoordinator.kt`、`StockAlerts.kt:56-62` | prefs | `NotificationPrivacyTest` | — |
| 27 | 提醒可靠性：精确闹钟/AlarmClock、开机/时区/更新恢复、Direct Boot、电池优化检查、测试提醒 | DEFAULT_IMPLEMENTED | 设置 → 可靠性 | `reminder/ReminderCoordinator.kt:98-101`、`Receivers.kt` | 设备保护存储缓存 | `ExactAlarmTest`、`LockedBootAlarmTest`、`CacheStoreTest` | OEM 真机未验收（BACKLOG 已列） |
| 28 | 截屏/最近任务保护 | DEFAULT_IMPLEMENTED | — | `MainActivity.kt:45` `FLAG_SECURE` | — | — | 也阻止用户主动截图给医生（产品取舍） |
| 29 | 外观：主题、动态色、三档对比度、语言（4 种 + 跟随系统）、简洁模式（≤1 个在用药物时生效） | DEFAULT_IMPLEMENTED | 设置 | `ui/SettingsScreen.kt`、`AppShell.kt:136` | prefs | `SettingsButtonInteractionTest`、`TranslationsTest` | 翻译覆盖：en/zh/zh-Hant/fr 各 1069 键，0 缺失（本审计脚本核对） |
| 30 | 关于/致谢/许可 | DEFAULT_IMPLEMENTED | 抽屉“关于” | `SettingsScreen.kt` | — | — | — |
| 31 | Widget、Wear OS、Health Connect、周期方案、T PK | PLANNED/候选 | — | BACKLOG | — | — | 均无代码 |
| 32 | play 变体 | DEPRECATED | — | CLAUDE.md | — | — | — |

## 4. PR #19 对公共组件的影响（不只看浓度页）

依据：`git diff --stat f53726f aecbd7d -- . ':!docs'`（64 个非文档文件，+5081/−28）。

| 范围 | 变化 | 判断 |
|---|---|---|
| `AppShell.kt` | 浓度入口改为包一层 `UnifiedConcentrationScreen`；浓度页也触发 `loadExtra()` | 导航结构不变；进入浓度页多一次全库刷新 |
| `ConcentrationScreen.kt` | 增加 `preview`、`referenceNotice`、`m2Only` 参数，Legacy 校准卡在 M2 时隐藏 | 只影响浓度页 |
| 新资源 `strings_unified_concentration.xml` ×4 语言（各 64 行） | 新增 | 需复核长法语在 2.0 字号下的效果（PR 自述已做 8 张截图） |
| `ButtonAuditTest.kt` | +12 行 | 测试扩展 |
| `NotesViewModel.kt`、`export/*`、`LabEstimate.kt`、数据库、Gradle | **未改** | 因此 PDF 图表与化验估算仍用 Legacy（03 F-PK-1/2） |
| `tools/pk-research/check_protocol.py` | 修改（+/−26） | 守卫本身被改，需在阶段 C 复核未放宽 |
| 版本号/Schema/签名配置 | 未改 | 合并后仍是 Build 25，与已交付多个包无法从版本号区分 |

## 5. 产品差异化优势（有源码证据）

1. **本地优先 + 多层隐私**：无 INTERNET 权限、SQLCipher、Argon2id PIN、伪装外壳与独立私密库、通知中性化、`FLAG_SECURE`。
2. **历史不可改写的设计**：记录冻结配置快照、方案版本、化验上下文追加修订、回收站与“隐藏”语义、Visit Pack 生成记录带输入摘要。
3. **时期/时间线引擎**：能从导入的历史中识别治疗时期，区分用户保存与推断，有专门的稳定性门槛测试。
4. **证据边界意识**：浓度页免责声明、缺失输入不猜测、P2 研究与生产隔离、协议哈希守卫。
5. **四语完整**：1069 个键在 4 种语言中全部存在。

## 6. 与旧文档的对照（避免把已修复问题重新列为缺陷）

- 日历两位数日期被裁（`docs/ui-button-consistency.md:109`，截图 `docs/ui-audit/calendar_month_days_zh-rCN_2.0.png`）：后来改为 `CalendarDateBadge` 测量缩字（`CalendarScreen.kt:375-392`），BACKLOG 记为“本轮修复并测试”。本审计无法重新截图，记为 **UNVERIFIED（历史问题，已修复待复核）**，不列为现存缺陷。
- 导航抽屉不可滚动（F1）：已修复并有测试，不再列为缺陷。
- 库存页与日历预测口径不同：BACKLOG 已登记为“两种基础口径已实现”。本审计**新增**的是第三种口径（通知 7 天需求）以及“低库存”标签与通知可能不一致，见 02 §3.3。
