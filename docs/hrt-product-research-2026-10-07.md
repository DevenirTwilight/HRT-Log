# HRT Log：源码核查、竞品与独立产品方向

核查日期：2026-10-07。研究对象是当前源码，**不是已安装 APK 的功能保证**。HRT Log 基线为 `759ee88`，核心功能最近一次提交为 `bf7d6d4`；公开 0.2.0/build 5 基于 `462da22`，不包含最新身心状态实现。本次只做研究与设计，不修改应用、不发布版本。

推荐定义：**local-first longitudinal HRT record —— 在本机保存治疗历史及其当时上下文、帮助本人回顾和准备复诊的记录工具。**

报告分为：本文件的现状核查；[完整竞品矩阵](hrt-competitor-matrix-2026-10-07.md)；[Featherline 与独立设计](design/longitudinal-hrt-record.md)；[下一版本评分路线图](hrt-roadmap-2026-10-07.md)；[研究来源与独立开发记录](../PRIOR_ART.md)。后续文件是建议，不能视为已获实现批准。

## 证据口径

- 源码结论来自默认分支固定提交下的模型、调用链及相关 UI；没有编译、安装竞品或验证其商店二进制。源代码存在不等于所有发行版本已提供。
- “未发现”只表示在本次审阅路径没有证据；不等于证明整个项目绝无该功能。官方页面宣称另行标注，不能提升为源码验证。
- 最近提交、issues、roadmap 用于判断开发方向，不把未合并 PR 当成已实现。GitHub `pushed_at` 可能来自非默认分支，不作为默认分支最后更新日期。
- 不用真实健康数据。所有设计示例为合成数据。没有剂量建议、因果推断、目标浓度判断或诊断。

## 1. HRT Log 当前能力：从实际源码确认

本节文件链接固定到基线提交，便于后续实现后仍可复核。

| 范围 | 已实现与边界 | 核心证据 |
|---|---|---|
| 药物 | 用户创建药物；成分、途径、单位、每次量、包装量、开封期限、部位配置；PK 有额外 profile。不是全面品牌药典，也不是所有可记录药物都能模拟。 | [Entities.kt](https://github.com/DevenirTwilight/HRT-Log/blob/759ee88/core/data/src/main/java/net/plainnotes/app/data/Entities.kt)、[MedicationEdit.kt 所在 UI 目录](https://github.com/DevenirTwilight/HRT-Log/tree/759ee88/app/src/main/java/net/plainnotes/app/ui) |
| 方案、版本 | Rule 有起止 UTC、时区、剂量/提醒窗口/config 快照；每天/每 N 天、每 N 小时、每周，多个时间点与单次剂量覆盖；编辑相同计划复用版本。是单药计划版本，尚无组合治疗方案/epoch。没有重复 ON/OFF 周期模型。 | [NotesRepository.kt](https://github.com/DevenirTwilight/HRT-Log/blob/759ee88/core/data/src/main/java/net/plainnotes/app/data/NotesRepository.kt)、[ScheduleEngine.kt](https://github.com/DevenirTwilight/HRT-Log/blob/759ee88/core/domain/src/main/kotlin/net/plainnotes/app/domain/ScheduleEngine.kt) |
| 提醒 | 计划槽位、提前/延迟窗口、重排与跳过；Android 提醒协调、开机/时区变化恢复、预约提醒、中性内容选项。通知与 OS 电池权限仍需要设备设置。 | [ReminderCoordinator.kt](https://github.com/DevenirTwilight/HRT-Log/blob/759ee88/core/reminder/src/main/java/net/plainnotes/app/reminder/ReminderCoordinator.kt) |
| 实际服药 | actual dose/time、计划关联、手动未计划记录、部位、note、来源键、revision/软删除；编辑/删除可冲销库存。迟服由保存的时间窗口判断。 | Entities、NotesRepository |
| missed 的含义 | 超过下一个计划点而未记录会产生推定 MISSED/AUTO_MISSED。它证明“没有登记”，不能证明“没有服用”。目前复诊事实统计需明确区分确认漏服与未确认。 | ScheduleEngine、NotesRepository 的 missed tracking |
| 历史 | 日历/每日槽位与记录历史，跨版本相同槽位去重。尚不是 labs、症状、预约、方案变更共同的统一时间线。 | [CalendarModel.kt](https://github.com/DevenirTwilight/HRT-Log/blob/759ee88/app/src/main/java/net/plainnotes/app/ui/CalendarModel.kt) |
| 库存 | 独立包装、未开封/在用/空、开封日期/期限、初始已用与当前已用、来源备注/批号；SupplyEntry 增减和 reversal，单次服药与实际包装消耗关联。导入历史不会凭空扣掉当前库存。 | Entities、NotesRepository、[StockScreen.kt](https://github.com/DevenirTwilight/HRT-Log/blob/759ee88/app/src/main/java/net/plainnotes/app/ui/StockScreen.kt) |
| runway | **已经有** upcoming 计划逐槽消耗预测；库存卡另用当前方案平均日耗给 daysLeft。两处口径不同。未实现 observed rate、显式未来情景、过期/报废参与的完整模拟。 | CalendarModel `forecast`；StockScreen `dailyUse`；NotesViewModel 的 upcoming |
| 化验 | 14 个内建 analyte：E2、T、P4、P4_IA、P4_MS、PRL、LH、FSH、SHBG、ALT、AST、GGT、CREA、K；原单位、采样 UTC/zone、参考上下界及单位、实验室、备注；列表/图表/单位转换。DB code 可扩展但 UI 不是自由分析物目录。 | [LabsScreen.kt](https://github.com/DevenirTwilight/HRT-Log/blob/759ee88/app/src/main/java/net/plainnotes/app/ui/LabsScreen.kt)、Entities |
| 化验上下文 | 显示距最近一次**当前被识别为 E2 的实际记录**的时间；所有 analyte 共用该 E2 时间列表。不是对应 analyte/药物的固定上下文，未保存 last dose amount、route、version/epoch 或采样时估算。 | [NotesViewModel.kt](https://github.com/DevenirTwilight/HRT-Log/blob/759ee88/app/src/main/java/net/plainnotes/app/NotesViewModel.kt) `refreshConcentration`、LabsScreen |
| PK | E2 多途径/部分制剂；口服 CPA、螺内酯与 canrenone、P4 示意模型。T 可记录/化验但没有当前应用浓度计算；BICA 虽有引擎枚举，未在应用 calculator 纳入。支持范围不是途径×成分任意组合。 | [ConcentrationCalculator.kt](https://github.com/DevenirTwilight/HRT-Log/blob/759ee88/app/src/main/java/net/plainnotes/app/conc/ConcentrationCalculator.kt)、[pk-engine](https://github.com/DevenirTwilight/HRT-Log/tree/759ee88/pk-engine)、[pk-model.md](https://github.com/DevenirTwilight/HRT-Log/blob/759ee88/docs/pk-model.md) |
| PK 时间/限制 | 使用 180 天实际 ON_TIME/LATE 记录与 30 天未来计划；模型需要配置齐全。贴片下一次应用隐含移除上一片，没有独立实际移除事件。未知/不支持明确排除。P4 等不得包装成测得浓度或临床精度。 | ConcentrationCalculator |
| 化验校准 | E2 MAP + Laplace 近似，回顾性/预测语义、Monte Carlo 分位带；不是所有激素都校准，也不是经过外部临床验证的覆盖率置信区间。 | [LabFit.kt](https://github.com/DevenirTwilight/HRT-Log/blob/759ee88/pk-engine/src/main/kotlin/net/plainnotes/app/pk/LabFit.kt)、docs/pk-model.md |
| 历史参数 | Record/Rule 保存部分药物快照，但 calculator 仍读取当前 medication/profile；过去 route、ester、gel/patch/SL 参数可被当前编辑重解释。参数版本和每次 fit 结果未完整持久化；CPA 用当前体重。 | NotesRepository `configSnapshot` / `saveMedication`，ConcentrationCalculator |
| wellbeing | 每日原创 mood/energy/sleep 三项 + 自定义条目、1–5 记录、日备注；迁移保留其他旧条目并按最近使用显隐，不将其当临床量表。 | [WellbeingScreen.kt 所在目录](https://github.com/DevenirTwilight/HRT-Log/tree/759ee88/app/src/main/java/net/plainnotes/app/ui)、[WellbeingUpgrade.kt 所在数据目录](https://github.com/DevenirTwilight/HRT-Log/tree/759ee88/core/data/src/main/java/net/plainnotes/app/data) |
| 症状与医学来源 | 当前用药匹配目录，组去重、来源原话/发布日期/章节/URL、第三方转载标志；来源说立即时突出原文；地区监测/上报另列，无自编判断。SPI 为“官方来源未列出”，不是自行补清单。 | [SymptomCatalog.kt](https://github.com/DevenirTwilight/HRT-Log/blob/759ee88/app/src/main/java/net/plainnotes/app/symptoms/SymptomCatalog.kt)、[wellbeing-research.md](https://github.com/DevenirTwilight/HRT-Log/blob/759ee88/docs/wellbeing-research.md) |
| 症状历史缺口 | SymptomCheck 只存 date/group_id/note。目录本身有 version，但 observation 不保存 version、匹配药物或来源 revision。PDF `symptomLines` 从**当前目录整个 group**取来源，可能包含当时未匹配的药物来源。 | Entities；[WellbeingSummary.kt](https://github.com/DevenirTwilight/HRT-Log/blob/759ee88/app/src/main/java/net/plainnotes/app/export/WellbeingSummary.kt) |
| 阶段回顾 | 效果 NOT_YET/NOTICED/UNSURE、效果备注、耐受/风险、吸烟、可选血压/体重、满意度；各效果可隐藏。不绑定治疗阶段，日期精度，不评分/不判范围。 | StageReviewEntity、WellbeingSummary、wellbeing-design.md |
| PDF/CSV | 服药/化验/每日状态等 CSV，PDF 与可选浓度图；已有按日期区间生成的复诊摘要，加入症状、阶段回顾、包装来源/批号。缺少逐模块/逐条选择、报告快照和预约绑定。 | [Exports.kt](https://github.com/DevenirTwilight/HRT-Log/blob/759ee88/app/src/main/java/net/plainnotes/app/export/Exports.kt)、NotesViewModel `exportSummary` |
| 预约 | **已经有**预约时间/时区、类型、地点、practitioner 字符串、备注和提醒；无 practitioner 独立目录、问题清单、Visit Pack 实体。 | AppointmentEntity、AppShell/Dialogs、ReminderCoordinator |
| 加密备份 | PNBAK1、固定 Argon2id 32 MiB/t3/p1、salt16、nonce12、AES-GCM，magic 作 AAD；format/schema 检查、事务恢复、schema1 迁移到2。安全欠缺见独立设计文件：主要是读取/解析/业务校验边界，不是“没有加密”。 | [DataTransfer.kt](https://github.com/DevenirTwilight/HRT-Log/blob/759ee88/core/data/src/main/java/net/plainnotes/app/data/DataTransfer.kt)、NotesViewModel `restoreBackup` |
| SQLCipher | 整个 Room DB SQLCipher，随机密钥由 Keystore AES-GCM 包裹；不做 destructive fallback，缺钥报恢复错误。解锁 PIN 是应用访问屏障，不是要求用户认证才能解开 DB key。 | [DatabaseAccess.kt](https://github.com/DevenirTwilight/HRT-Log/blob/759ee88/core/data/src/main/java/net/plainnotes/app/data/DatabaseAccess.kt)、[NotesDatabase.kt](https://github.com/DevenirTwilight/HRT-Log/blob/759ee88/core/data/src/main/java/net/plainnotes/app/data/NotesDatabase.kt) |
| app lock | PIN Argon2id、Keystore 保护配置、失败延迟和可选生物识别；生命周期访问控制。不能阻止有完整设备控制权者读取已解锁进程。 | [AppLock.kt](https://github.com/DevenirTwilight/HRT-Log/blob/759ee88/app/src/main/java/net/plainnotes/app/security/AppLock.kt) |
| disguise/privacy | full 版 Calculator/Notes launcher aliases，独立私密笔记库；FLAG_SECURE；提醒内容中性；无 INTERNET/账户/遥测。正常系统身份是 HRT Log，OS 设置/通知 header 仍可能暴露名称。无桌面 widget。 | AndroidManifest、MainActivity、security；REQUIREMENTS §14 |
| Trans Memo 导入 | 用户 SQLite user_version8，严格列检查、预览、歧义确认、未知标记、药物/历史/包装/每日记录/备注/预约迁移。只支持已验证格式，不保证任意版本。未使用 Trans Memo 源码。 | [TransMemo.kt](https://github.com/DevenirTwilight/HRT-Log/blob/759ee88/importer/src/main/kotlin/net/plainnotes/app/importer/TransMemo.kt) |
| HRT Tracker 导入 | 实际目标是 **TransmtfTeam/Transmtf-HRT-Tracker** JSON（meta≤2），不是本报告的 Yuuki-Sakura Swift 项目；导入 events、E2 labs、gel 配置/体重等，药物匹配需确认，历史不自动生成确定方案。 | [HrtTracker.kt](https://github.com/DevenirTwilight/HRT-Log/blob/759ee88/importer/src/main/kotlin/net/plainnotes/app/importer/HrtTracker.kt)、[HtImport.kt](https://github.com/DevenirTwilight/HRT-Log/blob/759ee88/app/src/main/java/net/plainnotes/app/ui/HtImport.kt) |
| 多语言/迁移 | 英语、简中、繁中、法语；地区与语言独立。Room schema2、迁移1→2、旧备份与导入旧 wellbeing 键映射都有测试；不是数据库任意版本转换器。 | resources、NotesDatabase、[core/data tests](https://github.com/DevenirTwilight/HRT-Log/tree/759ee88/core/data/src/test) |

### 当前数据模型最需要补的部分

1. 药物身份、制剂规格、给药途径、方案时间表混在可变 medication/profile，缺少 immutable medication revision 和完整 actual snapshot。
2. 单药 Rule 不是多药组合的 treatment epoch；变更没有统一类型化审计事件。应保留现有规则引擎而不是推倒重写。
3. `Double + unit string` 缺少剂量物理量层：mg、片、mL、mg/mL、µg/day 不能混成一个数量，复方也需成分列表。
4. 日期型 observation 无时间精度/时区/recordedAt；方案变更当天不能强行分给一个 epoch。
5. 化验无 panel/context 固定快照、校准结果不可重现；症状无 provenance snapshot。
6. 实际注射仅 site 字符串，缺少 vial/浓度/抽取体积；贴片缺少 apply/remove 配对；批号/来源编辑无审计链。
7. 自动 missed 与用户确认漏服语义需拆开。adherence 统计必须公布分母、未知量、排除暂停/跳过的规则。
8. export 缺少同一读事务的一致快照（复诊摘要已用 transaction，普通导出不同）、报告内容选择、长期可读/机器可迁移的语义版本。

### README、docs、源码与发行状态的不一致

- README 的待验证表述不能替代 `LiteratureValidationTest` 与文献参数记录：已有数值/文献检查，仍不等于临床验证或所有药物/途径都验证完成。
- HANDOFF 早期固定“Notes”系统身份已被 §2g 和 REQUIREMENTS §14 的 HRT Log 正常身份覆盖。
- licensing 早期“0.3.0 起”的叙述与当前 `versionName=0.2.0/code5` 不一致；独立重写引擎已随 build4/5 发布，旧移植代码仍存在 Git 历史。不能笼统说当前公开 APK 仍使用旧移植代码，也不能说历史许可问题消失。
- REQUIREMENTS §16 的开头与旧细则对 Release 致谢修改状态表述不一致，判断当前 Release 应读实际 release-notes 和最新 handoff。
- 最新 wellbeing 的源码 CI 成功不是所有真机流程已验证，更不是商店/公开 Release 已更新。此前提供的最新正式签名测试包与公开 build5 不同。
- **HRT Log 当前未发现项目级 LICENSE，GitHub license 字段也未识别。**“公开仓库”目前不自动赋予别人复制/修改/分发的开源权利。应完成作者/依赖/历史审计后由维护者选择许可证；本轮不代选、不加 LICENSE。

## 2. 为什么定位应该是长期记录，而非功能数量竞赛

用户真正的困难不是再多一个浓度曲线，而是半年后回答：**当时是什么治疗方案，实际登记了哪些用药，化验采样处于什么上下文，我自己观察到了什么，以及哪些信息尚不确定？** 一次改药不应改写旧历史，一次目录升级不应重新解释旧症状，一张报告不应把“没有登记”写成“确认漏服”。

HRT Log 的组合优势是本地 SQLCipher/无联网、已版本化计划与库存账本、带文献来源的症状记录、每日记录/阶段回顾和复诊摘要。单项都存在 prior art，**组合成可回溯、保留上下文且如实呈现未知的记录系统**，才是独立方向。不能声称它是市场首创，也不能据本次有限范围声称竞品都没有。

核心应为历史语义、Treatment Epochs、统一时间线、Lab Context、来源快照、Visit Pack/事实变更摘要、可靠恢复与导出。库存、提醒、注射记录和轻量 widget 是辅助输入；PK 是标明模型版本与限制的可选观察层。照片/社交、AI 荷尔蒙建议、极端复杂 PK、健身/肽类管理和 gamification 不应成为主线。

三年后的辨识度来自持续的产品规则：每个历史条目都能说明当时上下文、输入来源和更正链；报告能解释未知；每个重大功能有公开设计、原创 UX 论证、迁移/回归测试和 prior-art 记录。诚实承认早期参考 Trans Memo，不通过改名/换皮抹掉历史；新功能以自己的长期记录模型逐步形成独立产品。
