# 独立设计提案：保留上下文的长期 HRT 记录

状态：2026-10-07 产品研究提案，**未实施、未获功能实现批准**。输入为[源码审计](../hrt-product-research-2026-10-07.md)与[竞品矩阵](../hrt-competitor-matrix-2026-10-07.md)。设计目标不是复制竞品界面或实现，而是利用 HRT Log 现有 Rule、Record、SupplyEntry、LabValue、StageReview、Appointment 与 PDF 能力建立一致的历史语义。

## Problem statement / constraints

用户需要跨几个月或几年的可靠回顾：当时的方案、实际登记、采样上下文和个人观察。现有模块各自有记录，但可变 profile/目录会重解释历史，单药版本不能表达组合治疗阶段。

约束：离线无账户；不扩大联网权限；不判断结果正常与否；不提供剂量/部位建议；不自动推断因果；保留原始单位与时间精度；schema1/2 历史与备份兼容；不根据最新配置补造过去；所有新功能四语；伪装模式、app lock 与导出明确控制。当前正式签名、发布流程不在本提案范围。

## I. Featherline：直接源码研究及其边界

所有 Featherline 链接固定到审阅提交 `e9a3d180fef7c32bf59f06e42832a04762eb1d2d`。GPL-3.0；本节只有行为/结构分析，不附实现代码，不建议移植。

### A. Medicine → group → actual history

| 对象 | 实际作用及解决的问题 | HRT Log 应吸收的抽象需求，不复制的实现 |
|---|---|---|
| Medicine | 保存 selection（catalog/custom/patch-off）、category、preparation、显示单位、stock、archive 等；制剂支持片强度、胶囊、液体浓度/体积、凝胶百分比/克重、贴片储量或释放速率。 | 产品规格、服用指令、实际记录分离；不复制 sealed class 分支、序列化字符串与默认预设。 |
| MedicineIdentityKey | 按药品选择与规格构造可比较身份；包括自定义名称规范化、数字规范化和 imported namespace，帮助去重、复用库存与导入匹配。 | 建立稳定 identity 与用户确认的等同关系；不复制 key grammar、舍入阈值或匹配算法。 |
| MedicationGroup | 一组 medicines 与 dose instructions 共享计划/提醒；daily/weekly 间隔和 time slots、effectiveFrom、archive 与 replacedBy/recreatedFrom 链。 | 多药一起登记、区分“安排”与“产品”；替换链不是完整不可变治疗组合 epoch，HRT Log 不应照搬作为全部历史模型。 |
| MedicationLogEntry | medicine 引用、category/applicationType、instruction、实际时间/时区、原计划时间、group/slot、count、import keys、量差等。 | 实际发生的量/时间独立于计划，保留关联与输入来源；原计划与实际状态不要混成一个字段。 |
| DoseInstruction | 分数片、整单位、mL、克、noop，分别解释用户执行的动作和包装消耗。 | 数量必须有量纲；药物量、产品体积、库存扣除不能直接等同。 |
| equivalentE2Mg snapshot | 历史记录保存换算后的 E2 等效量；PkSimulation 读取此快照，使当时换算量不依赖后来编辑。 | 保存实际成分量及其换算依据；这不意味着所有激素/所有历史 PK 参数都已 snapshot，也不是药效“等效剂量”的推荐。 |

证据：[MedicineModels.kt](https://github.com/mkx173/Featherline/blob/e9a3d180fef7c32bf59f06e42832a04762eb1d2d/app/src/main/java/com/mkx/hrttracker/model/medication/MedicineModels.kt)、[MedicineIdentityKey.kt](https://github.com/mkx173/Featherline/blob/e9a3d180fef7c32bf59f06e42832a04762eb1d2d/app/src/main/java/com/mkx/hrttracker/model/medication/MedicineIdentityKey.kt)、[MedicationGroupModels.kt](https://github.com/mkx173/Featherline/blob/e9a3d180fef7c32bf59f06e42832a04762eb1d2d/app/src/main/java/com/mkx/hrttracker/model/medication/MedicationGroupModels.kt)、[PkSimulation.kt](https://github.com/mkx173/Featherline/blob/e9a3d180fef7c32bf59f06e42832a04762eb1d2d/app/src/main/java/com/mkx/hrttracker/model/pk/PkSimulation.kt)、[data-model.md](https://github.com/mkx173/Featherline/blob/e9a3d180fef7c32bf59f06e42832a04762eb1d2d/docs/data-model.md)。

### B. stock projection 与 365 天 simulation

`MedicineStockRepository` 合并 medicines/groups/logs/home snapshot，构建并缓存 `MedicineStockProjection`；缓存区分首帧 snapshot 与 live 计算，避免首次显示空数据。`MedicineStockRateCalculator` 的日均次数是计划频率，不是实际观察速率。

`ScheduledRunwayCalculator` 展开今天到未来 365 天 active groups 的未履行 occurrences；今天已经过时但未记录的剂量仍计需求；按计划/记录签名排除已履行槽位；预览一个即将保存的记录时排除对应槽位，避免库存预扣后再次计需求。它按确定顺序消费 stock/open-container，给出 NoSchedule、Days（最后可覆盖日）或 BeyondHorizon。BeyondHorizon 不是“永不缺货”；last-covered day 也不能直接等同 HRT Log 的 firstShort timestamp。

源文件：[MedicineStockRepository.kt](https://github.com/mkx173/Featherline/blob/e9a3d180fef7c32bf59f06e42832a04762eb1d2d/app/src/main/java/com/mkx/hrttracker/data/repository/MedicineStockRepository.kt)、[ScheduledRunwayCalculator.kt](https://github.com/mkx173/Featherline/blob/e9a3d180fef7c32bf59f06e42832a04762eb1d2d/app/src/main/java/com/mkx/hrttracker/data/repository/ScheduledRunwayCalculator.kt)、[MedicineStockModels.kt](https://github.com/mkx173/Featherline/blob/e9a3d180fef7c32bf59f06e42832a04762eb1d2d/app/src/main/java/com/mkx/hrttracker/model/medication/MedicineStockModels.kt)。

这里的库存预测与激素浓度预测是不同问题，库存算法不提供药理建议。

HRT Log **已有** CalendarModel upcoming 逐槽模拟（目前 366 天）和 StockScreen 均耗估算。独立扩展不是搬来一个相同 calculator，而是统一 ProjectionResult 定义：basis、asOf、inventoryRevision、regimenVersionIds、horizonEnd、firstUncoveredAt、lastCoveredAt、assumptions、excluded/unknown counts、source data revision。

| 模式 | 独立设计 | 不能误导的边界 |
|---|---|---|
| scheduled runway | 复用自身 ScheduleEngine 展开已经保存的生效版本和覆盖；减去已完成实际扣账；过时未登记作为可选需求，明确口径；逐包装考虑用户登记的过期/报废与开放状态。 | 未确认槽位不自动补服、不自动扣账；模拟不改变真实库存。 |
| observed consumption runway | 从可冲销 SupplyEntry 净消耗区分服药、损耗、盘点调整；用户选 30/90 天观察窗，显示记录覆盖/变更数量；库存除以观察日均净用量，只在数据足够时显示估算。 | 不把缺记录日当零消耗；盘点调整不作服药；零或负净量显示不可估算，不显示无穷。 |
| current regimen runway | 固定今天选定的组合方案作为反事实“若以后保持此方案”，不包含未来已保存变更。 | 与 scheduled 的真实未来计划分开命名。 |
| future regimen simulation | 用户建立 scenario 分支，指定将来有效时间；只读模拟库存与计划需求，不进入实际日志、提醒或 epoch，确认保存才生效。 | 不优化剂量、不推荐采购/服药；批次互换需用户指定兼容关系。 |

### C. Widget、quick log 与 cache

Featherline 有 dose widgets（不同大小）与 Anchor。HomeSnapshot 聚合后生成 WidgetSnapshot；加密 DataStore/Keystore cache 使后台/重启读取与首页一致，主题/语言/单位变化触发刷新，cache 不可用时清空。`QuickLogActionCallback` 携 group/slot/medicine 信息，重新查当前 group，拒绝过期删除/归档目标，只建立缺少的 scheduled logs，再走 repository mutation、库存和 snapshot 更新；不是纯 UI 加一个勾。

证据：[widget 源码目录](https://github.com/mkx173/Featherline/tree/e9a3d180fef7c32bf59f06e42832a04762eb1d2d/app/src/main/java/com/mkx/hrttracker/widget) 中 HrtWidget、QuickLogActionCallback、WidgetSnapshotStore/Builder/Repository、HomeWidgetManager、AnchorWidget。已审阅回调，没有据此证明所有并发点击都完全无竞态；本次未执行竞品测试。加密 cache 也不保护已经交给 launcher 的 RemoteViews。

HRT Log 的四种独立 widget：

1. **Today**：下一项计划、今天已登记/未确认数量、可选预约；点击进入日时间线。默认不展示浓度/症状/批号，不复制 Featherline 进度环和分组卡片。
2. **Privacy**：用户选择中性标题，仅显示自选的“下一项时间”或“有待处理记录”；可关数字和时间本身。点击必须进入真实身份认证，不从 widget 泄露药物名。
3. **Quick-log**：用户明确选择可快速登记的槽位；默认实际时间为点击时刻、量为保存的计划快照；若规则已更改/槽位已完成则刷新，不扣第二次。偏离计划的时间/量需要打开确认页。app lock 开启时先认证，不能通过后台按钮绕过。
4. **disguise-safe**：伪装开启即撤销旧 pending actions、推送中性 RemoteViews 并擦掉健康 cache；伪装 widget 只打开对应 Calculator/Notes shell。**不在伪装桌面隐秘写服药记录**，以免实际记录误操作。退出伪装仍需用户重新选择展示内容。

架构采用现有数据库一致读事务→独立 WidgetProjection（最小字段/opaque actionId、版本、expiry）→加密短期 cache→renderer；actionId 服务器无关，只在本机按版本验证、数据库唯一 slot claim + 同一库存事务保证幂等。到期缓存显示“打开应用更新”，不能显示过期的已服状态。刷新触发：服药/更正/计划/库存、跨日、时区、语言、lock/disguise；Direct Boot 只提供中性占位，不把健康 snapshot 放 device-protected storage。launcher 可能缓存旧图，说明系统边界并真机测切换；FLAG_SECURE 不保护桌面 widget。

### D. Journal / TrackedDate / Anchor

TrackedDate 是用户命名日期、图标/颜色、pinnedOrder，支持过去计天/未来倒数；Journal 的 notes 与这些日期相邻；Anchor 把选定日期放桌面。它解决“日期对我有意义”的需求，不等于完整医疗时间线。

证据：[TrackedDate.kt](https://github.com/mkx173/Featherline/blob/e9a3d180fef7c32bf59f06e42832a04762eb1d2d/app/src/main/java/com/mkx/hrttracker/model/journal/TrackedDate.kt)、[JournalEntities.kt](https://github.com/mkx173/Featherline/blob/e9a3d180fef7c32bf59f06e42832a04762eb1d2d/app/src/main/java/com/mkx/hrttracker/data/local/JournalEntities.kt)、[AnchorWidget.kt](https://github.com/mkx173/Featherline/blob/e9a3d180fef7c32bf59f06e42832a04762eb1d2d/app/src/main/java/com/mkx/hrttracker/widget/AnchorWidget.kt)。HRT Log 用下文 TimelineEvent/Milestone，沿用自己的日历和阶段回顾，不照搬 Journal 页面、文案、日期庆祝视觉或 Anchor 布局。

### E. Backup security：逐项比较

Featherline container 当前 version3（兼容2），snapshot version6，与 Room schema9 是**三个版本轴**。Argon2id 默认64MiB/t3/p1；参数头检查 t1–10、memory1KiB–256MiB、p1–4、key32，并在调用 KDF 前拒绝不支持/越界输入。AES-GCM 对完整 header 作 AAD；gzip 压缩在认证后的解密路径解压，声明原长度及实际计数均受限。JSON上限128MiB、容器192MiB；RestoreService bounded read，app/package/schema/type/UUID/引用等 `toValidatedSnapshot` 检查后事务恢复。

证据：[BackupCrypto.kt](https://github.com/mkx173/Featherline/blob/e9a3d180fef7c32bf59f06e42832a04762eb1d2d/app/src/main/java/com/mkx/hrttracker/data/backup/BackupCrypto.kt)、[BackupRestoreService.kt](https://github.com/mkx173/Featherline/blob/e9a3d180fef7c32bf59f06e42832a04762eb1d2d/app/src/main/java/com/mkx/hrttracker/data/backup/BackupRestoreService.kt)、[BackupSnapshot.kt](https://github.com/mkx173/Featherline/blob/e9a3d180fef7c32bf59f06e42832a04762eb1d2d/app/src/main/java/com/mkx/hrttracker/data/backup/BackupSnapshot.kt)、[backup-format.md](https://github.com/mkx173/Featherline/blob/e9a3d180fef7c32bf59f06e42832a04762eb1d2d/docs/backup-format.md)。这些是审阅到的防护，不是完整安全审计结论；有限制仍可能多缓冲峰值/OOM。

| 检查 | HRT Log 当前状态 | 要补的独立实现 |
|---|---|---|
| magic、认证加密、salt/nonce | 已有 PNBAK1、AES-GCM、magic AAD、随机salt/nonce，wrong password/tamper不能正常解密。 | 保持兼容；不要以安全名义破坏旧备份。 |
| KDF 上界 | **固定**32MiB/t3/p1，不读取攻击者给的 cost，因此当前没有“缺少 attacker-cost bounds”的问题。 | 将来可变KDF header用白名单profile或严格bounds，认证完整header；低端机基准后定值。 |
| 容器输入上界 | NotesViewModel URI `readBytes()` 无显式文件大小上限。 | 从流计数/截断拒绝，不依赖提供者报的长度；limit+1检测。 |
| plaintext/JSON上界 | 解密全量、转 String、JSONObject，全量多副本；缺少独立行数/嵌套/字符串长度预算。 | 分阶段预算、有限解析器、行/字符串/深度/总字节上界；大图片将来分块附件且禁止任意文件路径。 |
| gzip/decompression | 当前**不压缩**，没有现存gzip bomb路径。 | 只有未来引入压缩才需认证后解压、绝对输出限额、声明长度/实际长度一致、拒绝尾随额外payload；不要照抄竞品常数。 |
| 容器/应用/语义版本 | 当前payload format/schema检查和拒绝新schema，magic区分容器；未见独立app identity与完整语义模型版本。 | v2明确container/payload/database三轴、package/product ID；v1正常兼容路径。 |
| 表/列/字段类型 | RawData 允许表名单，但列来自row.keys；restore临时卸guards后bulk insert；FK/unique不是全部业务验证。 | typed DTO与列白名单、枚举、finite/positive amounts、日期/zone、区间、关联、ledger/reversal/id完整检查；拒绝unknown columns或走明确migration。 |
| 业务guard | 重建trigger不追溯验证已经插入的非法值。未执行恶意备份实证，不能宣称已有可利用漏洞。 | staging验证全部不变量；成功后单一事务替换；约束失败回滚，保持原数据库/key。 |
| 恢复预览 | 现流程偏直接恢复。 | 认证后显示数量/范围/版本/覆盖范围，确认原有用户导入语义；同时显示不包含lock/private notes等哪些项目，避免“整机备份”误解。 |
| 内存清理 | key有finally；部分password仅在成功路径fill，String无法可靠擦除。 | password/key/byte buffer在finally清理，减少String副本；不承诺 JVM 内存完全可擦除。 |
| 错误/日志 | 已有通用错误，不应输出健康内容。 | wrong password/认证失败不泄露内容；语义错误显示字段类型而非数据，诊断计数需无健康信息。 |

建议先以**32MiB输入/64MiB解析预算的候选值**做低内存机峰值和多年合成数据试验，再决定上限；这不是已验证产品限额。有限读取/typed restore 不要求换加密协议。必须用合成测试覆盖 oversized、truncated、wrong password、tampered、unsupported version、深层JSON、极多rows、NaN/Infinity、负剂量、重复/悬空ID、重叠version、错误ledger、恢复中断，以及schema1/2正常恢复。原DB任何失败保持不变。

## II. HRT Log 独立历史模型

### 1. Medication Identity → Regimen Version → Treatment Epoch → Actual Event

这是新设计，不是 Featherline 类型的翻译。保留单药 Rule 作为 schedule engine adapter，新表建立不可变关系。身份描述记录对象；每次规格修订有自己的 revision；方案指令与真实事件有各自快照。

| 表/对象 | 建议字段（概念schema，不是待搬入的实现） | 不变量 |
|---|---|---|
| MedicationIdentity | id、userLabel、ingredientIds、userConfirmedAliasIds、archivedAt | 用户稳定身份；同名不同规格不能自动合并。别名确认有审计。 |
| MedicationRevision | id、identityId、recordedAt、supersedesId、formulation、strengthComponents[]、productQuantityUnit、brandOptional、sourceLabel | 制剂规格不可变；成分化学形式与活性母体分开，强度带分母。label更名不必创建epoch，真正formulation变化才触发。 |
| RegimenVersion | id、identityId/revisionId、effectiveFrom/Until UTC、zone、route、doseInstructions[]、scheduleSpecVersion、ruleVersionIds、status（planned/active/paused/stopped）、changeReasonOptional、recordedAt、supersedesId | 指令不可变；一事务关闭旧版/创建新版。药物、剂量、途径、间隔、时间表、制剂变化都是候选切点；提醒提前量/颜色改动不切治疗epoch。 |
| TreatmentEpoch | id、start/end（半开区间）、timePrecision、memberVersionIds[]、changeEventId、recordedAt、revisionId、supersedesId、labelOptional | 活跃组合的时间区间；含明确暂停/无药阶段，不覆盖过去版本。多个同事务改药合成一个切点，避免零长度epoch。 |
| ActualMedicationEvent | id、occurredAt/zone/timePrecision、recordedAt、plannedSlotRefOptional、eventKind、regimenVersionIdOptional、epochResolution、doseSnapshot、medicationRevisionId、routeSnapshot、origin、externalId、revision/supersedes/deletedAt | 真实记录独立；未计划服药不强行关联现有计划；历史编辑保留更正链。 |
| EventDoseComponent | eventId、ingredient/formId、quantityDecimal、unit、enteredQuantity/Unit、conversionBasisRevision、conversionMethod、uncertaintyOptional | 多成分；decimal原值/标度存储，不靠无量纲Double扣库存。只做用户输入的量换算，不给“等效效果”建议。 |
| PkInputSnapshot | eventId、supportedModelId、modelVersion/hash、parameterSetRevision、productRevision、resolvedRouteParams、conversionRevision、weightBasisOptional、assumptions | 能重现当时输入。模型bundle升级与用户profile修改均不得静默改旧图。 |

UX 理由：编辑药物时先区分“修改以后使用的方案”与“更正过去录入错误”，默认只影响以后；显示生效时间和变化摘要。名称/包装备注不是治疗改变；更换不同制剂浓度可形成产品revision及epoch，但批号/来源单独是 supply event，不自动算治疗阶段。用户可以把多个连续改变标为同一个 review chapter，但不合并底层不同方案事实。

合成例：Epoch A `[2026-01-01,2026-03-10)` 显示“EV 2 mg oral daily”（用户记录示例，不是推荐）；3月10日改记录途径后 B 开始。A 下可筛 doses/labs/symptoms/wellbeing/reviews。**不写“B导致症状改善”**，仅并列时间关联。

时间边界：一次给药跨越epoch不会被截断其PK作用；曲线必须保留上个阶段的残余输入。采样归属按采样时区/有效instant，enteredAt独立。只有date的回顾/症状若当天跨多个epoch，保存候选集或用户确认，不虚构午夜发生。补录/回溯更正创建新的历史revision，旧PDF snapshot仍可查看原始生成依据。一次同药同槽不同版本不能简单按时间删记录，沿用本项目去重规则并检查实际事件是否冲突。

实现验收：同一产品改名称不改变既有event；换route/strength不重解释旧PK；组合epoch区间无重叠/零长；pause/resume、DST、跨时区和回溯更正一致；未知旧数据仍显示未知。

### 2. Unified HRT Timeline

不要用“每日卡片越堆越多”的首页。顶端显示正在看的epoch与方案摘要，下方按日期/时间列事实，筛选药物、事件类别、epoch。每次改变能展开 before/after；planned淡色、actual实色、未确认明确标签，区分用户记录与自动投影。默认折叠过去未确认槽位为日摘要，避免几百条淹没化验/回顾。

`TimelineEvent` 是索引/投影，不重复存整个Dose/Lab：id、kind、occurredAt/date/precision、recordedAt、zone、sourceType/sourceId/sourceRevision、visibility、epochResolution、titleSnapshotOptional。类型包含 plannedDose、actualDose、confirmedMissed、unconfirmedSlot、regimenChange、labPanel、symptomObservation、dailyCheckin、stageReview、appointment、milestone。planned为规则展开的虚拟条目；不能当已发生事件导出。

`Milestone` 保存 kind（StartedHRT/RouteChange/Surgery/Appointment/Custom）、用户文字、date/precision、linkedEventId、privacy/export visibility。系统 regimen/lab/review 已有实体，timeline 引用即可；用户标“Started HRT”必须用户确认，不能取数据库最早记录自动推定。事件更正或删除不留下幽灵索引，按 revision重建。没有庆祝排名/时长比较，也不复制 Anchor widget 视觉。

### 3. Lab Context

增加 LabPanel（sampledAt/zone/precision、laboratory、assayOptional、resultIds），同次采样关联而非按近似时间自动合并。ContextRevision 保存：capturedAt、sampleRevision、epochId/candidateIds、regimenVersionIds、lastActualByIngredient[]（eventId+revision、takenAt、elapsed、amount+unit、route）、nearby confirmedMissed/unconfirmed/late counts + window/rule、optional estimateSnapshot。

“最近一次”从**实际**事件选且按成分分别显示；E2化验不误用T服药，K/CREA可显示用户选择的相关药物上下文而不暗示单一因果。附近窗口由用户/产品明示，不使用偷偷设定的医学阈值；晚服定义来自当时计划阈值。若记录缺时、进口历史无确定计划或有冲突，则显示未知。

采样点 PK optional：保存模型ID/版本、输入event revisions、lab训练集合IDs+fitVersion、输出单位/分位与assumptions。**本次化验点不用于自己的预测，再把结果当独立验证**：可提供只用采样前可得数据的估算；若回顾性fit包括本次/后来的化验，醒目标明。没有支持模型就不显示。补录旧服药后可选择刷新 context，新revision保留原依据；不静默修改已生成的visit report。

只呈现时间/数量/途径/模型上下文，不判断正常、达标或该如何改药。旧数据迁移时可以生成带“现在回推”的context，绝不标成“当时自动记录”。

### 4. Source Provenance Snapshot

`SymptomObservation`：id、date或occurredAt、zone/timePrecision、recordedAt、groupId、neutralLabelSnapshot、note、catalogVersion、matchedMedicationRevisionIds、relevantRegimenVersionIds、epochResolution、sourceRevisionIds、sourceIds、origin、revision/supersedesId。

`SourceRevision`：id、sourceId、publisher/title、URL、documentDate/revisionDate、section、viewedAt、thirdParty/sites、quote/actionText/urgencySnapshot、language、contentHash。对应关联表 `ObservationSourceMatch` 记录哪种药匹配到哪条来源。只引用当时展示的匹配，不把group中所有来源附给每个人。文献来源更正不覆写旧revision，可另外显示“此来源后来已修订”，原文核实流程继续沿用本项目要求。

来源快照不是“证明发生药物不良反应”；该记录仅说明用户当时选了该症状。保存目录版本和相关药物不意味着认定药物导致症状。翻译改动保留当时语言/label；用户换语言可看新译名但保留原版入口。

schema2旧记录没有这些字段：保留 date/group/note，标 `LEGACY_CONTEXT_UNKNOWN`；附当前目录需标“当前参考来源，不能确认记录当时显示版本”。不得为旧记录回填看似精确的当时药物/sourceIds。

### 5. Appointment / Visit Pack

扩展现有 Appointment，不重新造预约模块：Practitioner（id/displayName/specialty/contactOptional，无在线目录）、Appointment.practitionerIdOptional（保留原字符串历史）、VisitQuestion（text/order/status/notedAnswerOptional）、ChecklistItem（用户自建/模板选入、checkedAt）、VisitPackSnapshot。

预约页输入问题与选择回顾区间，默认从上次**用户确认完成的访视**到本次，未找到则询问/显示可调整区间。按钮 **Generate Visit Pack** → 内容选择预览（用药/改变、labs+context、症状、stage reviews、daily趋势、来源/批号、问题清单、可选PK）→ 一致事务读取 → PDF本机导出。Surgery/照片/性相关条目、药物来源默认需显式选择；医师仅获得用户选择内容。

报告顺序：用户提供的标题/基本信息（可不实名）、当前记录方案及变更、since-last-visit事实摘要、化验原值/原范围与采样上下文、症状/阶段回顾、用户问题、简洁方法/缺失记录/模型限制。不得正常/异常颜色、建议剂量或疗效评级。区间方案来自immutable版本，不用当前名称/route重绘整个历史。

Snapshot：id、generatedAt、range/zone、includedSections/IDs+revisions、templateVersion、language、inputDigest、summaryMetrics、reportHash；PDF是否留存由用户选择，snapshot元数据仍需保护。重新生成是新版本；关闭某条症状后既有导出不自动消失。每节来源可点击，没来源的自填文本如实标自填。隐私提示聚焦实际PDF分享操作，不制造额外确认层。

### 6. Longitudinal Change Summary

用 deterministic query+原创四语模板，不需要 LLM。summary记录范围、计算版本、输入修订、coverage。合成示例：“方案变更2次；登记迟服3次；确认漏服1次；另有4次未确认；新增2组化验；症状X登记于3个不同日期；阶段回顾1次；包装来源/批号更换1次。”

计数规则：多药同一change事务算一次组合改变，可展开分药；编辑更正不算新服药；未来预约/计划不算已发生；lab panel与result分别计数；症状按distinct dates，不能说3天连续/持续；批号更新只有历史event才可计，不能比当前字符串凭空编历史。late按旧阈值；暂停/取消槽位不进分母；未确认与confirmed missed分列。未登记不推断正常/未发生，无够用数据时不输出adherence百分比。

### 7. Injection support

独立 `InjectionDetail`：actualEventId、route（IM/SC/unknown）、siteId+labelSnapshot、sideOptional、vialContainerId、productRevisionId、enteredConcentration quantity+unit、drawnVolume quantity+unit、administeredAmount components、wasteVolumeOptional、note。包装沿用Container/SupplyEntry扩展液体单位，不再存第二套库存。

记录页用户填实际量或体积，已明确浓度时展示纯算术换算并保留输入依据；未知浓度时不猜。浓度数值、mg/mL与其他单位不兼容时要求确认。库存按真实用户确认的drawn/waste扣除，而非自动把mg当mL；抽出量与实际给入量可不同。支持vial编号/批号/开封，不根据品牌补出推荐浓度/器材。

部位历史按最近登记时间展示列表与原创简图，用户可自建部位、只记录左右或不记录。颜色表示日期，不标“安全”“应该打这里”；不自动建议轮换间隔，不判断硬结感染。它辅助回顾而非注射教学。Yuuki site picker、Mona placements、MyHRT/MyTRT rotation属于产品思想 prior art，不复用图形资源和解剖提示。

### 8. Broader hormone support

值得做**记录模型 hormone-agnostic**，不值得下一版承诺所有激素都有PK或同一套医学条目。核心 Ingredient（E2/T/P4/antiandrogen/custom）与chemical form/作用分类分开；复方产品允许多个成分；regimen不依据性别固定route/周期；lab analyte独立于用药；用户称谓/阶段标签不强制女性化/男性化。

menopause/周期疗法要独立CyclePlan（anchor、phase durations/ON-OFF、phase rules、effective revision），不能用“每N天”假装每日服X天停Y天。TRT/transmasc记录应支持不同T酯、制剂与vial，nonbinary允许任何组合。抗雄药不要算成“减少了多少T”的简单预测；浓度≠药效。

PK capability registry按ingredient×chemical form×route×product×model version明确支持；各自literature provenance/测试，未知正常记录但不模拟。provenance目录按产品/途径/地区核实，不把现有女性化来源直接推广。wellbeing custom先通用，stage review可选不同主题但不暗示标准治疗进程。目的扩展默认关闭/渐进揭示，避免给现用户增加一堆与自己无关字段。

## III. 迁移、交付与独立开发验收

按[路线图](../hrt-roadmap-2026-10-07.md)分阶段迁移，不一次性要求重写所有表：

1. P0 固定未来记录的完整med/profile与source snapshots，修正历史PK/export读取和未确认语义，有限输入/typed restore。旧config能确定的保留，缺项标unknown；用户可明确选择按当前模型重算，但不能静默更改已存快照。
2. P1 新identity/revision/version/epoch表，Rule adapter兼容旧schedule；从旧Rule边界建立“可证实的阶段候选”，对缺失profile不补猜。旧Actual保持original IDs/revisions/source keys/ledger，不重新扣库存；epoch链接可为空/ambiguous。
3. P1 context/Timeline/VisitPack依赖上面的稳定模型；再做四种runway与注射量纲。每项先单独设计文档、合成迁移测试、用户审核UX，再实现。

需要有价值的检查：旧备份schema1/2恢复、原历史/库存净量保持、重复导入幂等、回溯编辑与冲销、方案边界/DST、历史曲线与来源不被新配置重解释、PDF分页/四语/选择范围、widget并发点击与lock/disguise、恶意大文件失败保持原DB。文档调研本身不需要跑整套Android构建；本轮不声称已通过上述未来功能测试。

独立性验收：PR附problem/constraints/model/UX rationale/prior art/测试证据；不附竞品实现片段；公式与参数回到原始公开文献，资源和翻译有出处/授权。研究者已看源码，因此本次不是法律意义的严格隔离 clean room；后续如需严格隔离，应由未读受限实现的人根据抽象规格编码，并保留审核记录。
