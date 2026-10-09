## 最新状态：P1-C1已实施（2026-10-09，REQUIREMENTS §48）

起始 `d45a67a4f5309f1e951b2dfe69e9295fa42234cb`。采用局部同前缀插值：查询仅使用P1-B合格且采样时间≤min(查询时刻,会话读取NOW)的化验；中心、四分位、摘要、诊断一致。图表及新生成动态PK PDF图在拟合断点断线；旧PDF/冻结估算不重算。四语注明按采样时间重建，不是严格当时已知回放。

本地PK48/domain58/importer12/data72/reminder14通过；完整app384项（371通过/13既有跳过），最终渲染聚焦20项（19通过/1跳过），Lint0错误/131警告、Full Debug/Release及instrumentation编译通过；Python11通过。生产源码`47f53eec993a61b066713b9ca4f5e06e95e61eeb`的[CI37933171917](https://github.com/DevenirTwilight/HRT-Log/actions/runs/37933171917)三个任务全success，日志和三份报告已下载：JVM PK48/domain58/importer12；Android app384（371通过/13跳过）、data72/reminder14/聚焦PK30、Python11；Lint0错误131警告、Full构建/manifest通过；API35 data14/14、app61（59通过/2常规跳过）、独立重启各1通过。20/216/24文件均无APK，机器输出与本地一致（计时除外）。这是模拟器，不是实机。详见 [P1-C1验收](pk-research/sublingual-p1c1-verification.md)。

人口参数、P1-A固定SL/算法2、P1-B历史门控、Calculator2/旧冻结1/2、Build25/schema9不变。P0研究完成；人体外部准确性未确立。**下一独立P1-C2：全离群排除标记与实际拟合集合一致性**，尚未实施；P2人口模型仍研究，结果获知时间及其历史语义另行设计。保留旧研究证据，后续不再把已修因果插值列为现存缺陷。

# HRT Log：当前实现与后续 Backlog

历史P1-B检查点：2026-10-09，REQUIREMENTS §47，P1-B起始 `b9a5393a7435accd3afe02407b38709a0e40f179`。逐条历史资格门控已实施，本地及API35 CI37922137125通过并回查；P1-A完整舌下固定速率/仅幅度保留。P0研究完成、临床准确性未确立；P1-C因果边界/摘要及全离群一致性待独立授权，P2人口模型未实施。§41/42/44既有成果不变。以下历史核对基线保留。

历史核对日期：2026-10-08。起始 HEAD `5441e6efac2a9dcb3b38c0a3fb2b85b4d3c2880e`，开发分支 `claude/new-session-1959qb`，Build 25 / 0.2.0 / schema 9。状态基于源码和测试；研究中的优先级不构成实施授权。当时范围为 REQUIREMENTS §41 的 UI 修复、回归和文档；当前范围以最新需求为准。

状态：**完成并测试**、**已编码/设备待验收**、**确认缺陷**、**已批准未实现**、**候选/需决策**。P0=数据或严重操作障碍，P1=下一独立迭代建议，P2=后置扩展；这是工作排序，不是医学重要性。

## 已完成的底座

| 功能 | 当前证据 | 验收边界 |
| --- | --- | --- |
| Room + SQLCipher、Keystore 和加密备份 | `core/data/src/main/java/net/plainnotes/app/data/NotesDatabase.kt`（schema 9）、`DatabaseAccess.kt`、`BackupValidation.kt` / `NotesRepository` 备份恢复、迁移/备份回滚测试 | 不重建或清空用户库；旧备份兼容继续是门槛 |
| 用药、规则、改期/跳过、提醒、历史 | `NotesRepository.kt`、`ScheduleEngine.kt`、`core/reminder` 及对应测试 | Direct Boot、Doze、权限撤销/OEM 可靠性仍需真实设备 |
| 日/周/月/年日历、库存账本与提醒基础 | `CalendarScreen.kt`、`CalendarModel.kt`、`SupplyLedger.kt`、库存/提醒测试 | UI 大字号本轮修复；预测口径见候选表 |
| 健康状态、化验、冻结采样上下文 | `LabsScreen.kt`、`LabContext.kt`、`LabContextTest`、`LabEstimate.kt` | Lab Context 完成不等于 LabPanel 已完成 |
| 文献 PK 引擎、LabFit 与不确定性区间 | `pk-engine/Engine.kt`、`LabFit.kt`、`FittedModels.kt`、`LiteratureValidationTest`、`EngineTest`、`LabFitTest` | 已实现且有文献数值验证，不是临床验证；支持范围/缺失说明保留 |
| HRT Tracker/Trans Memo 导入、CSV/PDF | `importer`、`app/export/Exports.kt`、导入/导出测试 | 不猜未知字段；普通 PDF/Visit Pack 不等于专用纵向报告 |
| 私密笔记、独立加密库、锁定/伪装 | `app/disguise/privatenotes`、锁定/重启/隔离测试 | 启动器/OEM、Keystore、应用锁真实设备验收独立保留 |
| 四语与主题 | 四套 `strings.xml`、`TranslationsTest`、`NotesTheme.kt` | 英/简中/繁中/法；本轮审计覆盖新增宽度/主题条件 |
| Build 24 回收站、Build 25 时期编辑 | `Trash.kt`、`TimelineEdits.kt`、`TimelineV2Migration.kt`、V2/Flow/PeriodStability、API35 迁移测试 | 已有编码、功能 CI 和原签名交付证据；具体用户数据/真机覆盖安装仍待验收 |

## 候选缺口逐项核对

| 项目 / 状态 | 代码或测试证据 | 真实缺口 | 规格/授权 | 兼容风险 | 建议优先级 | 可独立验收的最小单元 |
| --- | --- | --- | --- | --- | --- | --- |
| LabPanel / 部分实现，扩展待决策 | `LabValueEntity`、`LabContextEntity`、`LabsScreen.LabDialog`；`NotesRepository.saveLab` 能存项目代码；`LabContextTest` | 单结果编辑、孕酮检测方法、上下文已完成；无同次采样分组实体、面板编辑/自定义项目管理。不能因 `analyte` 表存在就称 UI 已完成 | `lab-context-p1.md` 明确将面板/自定义项目另批；尚未批准立即开发 | 新实体/分组关系需迁移；保留旧独立结果、单位原值和冻结上下文 | P1 候选 | 先明确同次采样键/时区/编辑规则，再做只读同次采样分组，旧结果保持可单独打开 |
| Visit Pack 2 / 第一批完成，第二批待决策 | `VisitsScreen.kt`、`VisitPack.kt`、`VisitPackEntity`、`VisitPackDataTest`、`PdfReport` 原生测试 | 预约中已有医生文本、问题清单、生成 PDF/事实预览/不可变摘要；无结构化医生档案、清单模板、应用内 PDF 文件留存/预览。现有 PDF 仅存到用户选择的 SAF 地址，元数据不等于 PDF 文件 | `visit-pack-p1.md` 明确排除上述第二批；需确定文件留存/删除和模板内容 | PDF 隐私、加密附件/容量/备份策略；旧生成记录不可覆盖 | P1 候选 | 确定存储规格后，已有 PDF 的本地导入/只读预览；与新生成流程分开验收 |
| 历史专用报告 / 部分实现 | `CsvExport.write`、`PdfReport`、`VisitSection.MILESTONES/REGIMEN`、`ExportTest`/原生 PDF 测试 | 普通 CSV 与 Visit Pack 已有方案版本/里程碑部分；尚无时期/阶段专用 CSV、纵向报告入口与选择范围。不是“完全没有历史导出” | 纵向设计有方向，无已批准的字段/边界/精度规格 | 不应从当前药物配置重解释过去；报告应标清系统/用户、精确时间/日期级事实 | P1，下一独立单元建议 | 只读时期/阶段/里程碑 CSV：先确认列、时区、精确边界和来源，不增加 PDF 或 schema |
| 库存预测 / 两种基础口径已实现 | `SupplyLedger.kt`、`StockScreen.dailyUse/stockSummary`、`CalendarModel.forecast`、`CalendarModelTest` | 真实消耗有账本/冲销；库存页按当前方案平均，日历按展开的已保存待服槽逐次扣减；未统一口径说明，没有观察消耗速率和假设未来情景 | 两种口径统一有设计方向；观察窗口/情景保存未定 | 不改账本事实；预测范围/未覆盖的未来不能冒充完整预估 | P1 口径说明，P2 情景 | 明确标注并测试现有两种算法的口径/有限预测范围，保持计算行为不变 |
| 注射记录 / 部分实现 | `ProfileEntity`、`RecordEntity.site`、`MedicationEditor` 注射剂量说明、`siteFor`/轮换建议、CSV `site` | 注射途径/酯型 PK、实际部位存储和基础轮换存在；缺结构化药瓶浓度、抽取体积/换算依据、部位历史可视化 | 完整量纲/部位产品规格未定 | mg、mL、酯/有效成分误换算风险；历史录入值与换算依据须冻结 | P1 规格，P2 实施 | 首先确定并测试用户输入 mg/mL 与 mL 的纯换算边界；不自动推断未知浓度 |
| 药物版本 / 冻结快照底座完成，完整 Revision 未实现 | `MedicationSnapshot` v2、`RegimenVersionEntity`、`RegimenDefinition`、快照/版本不变性测试 | 当前药物仍可变；记录/规则/方案有冻结快照。无独立完整 MedicationRevision 链、复方成分表、包装自身量纲 | 研究/纵向设计候选；完整实体和更正 UX 待批准 | 高：旧快照不能被当前值补写，引用、库存单位和备份需正式迁移 | P1 设计，后续分批 | 写独立规格，先新增只读修订来源展示，不能将当前实体当历史版本 |
| PK 历史可复现 / 部分已冻结 | `LabEstimate.capture` 冻结 `calculator_version=1/2`、完整 `parameter_document`、体重/输入修订/原值/结果区间；`LabContextTest` | 冻结化验估算结果已可回看；非化验历史曲线仍使用当前引擎/当前体重。缺算法可执行 bundle、完整来源版本键及选择历史模型的系统 | Lab Context 规格刻意不承诺完整 bundle；V1 当前体重是明确决定，不能视作本轮缺陷 | 高：未来算法/参数升级重算的可比性、引擎分发/兼容版本、存储预算 | P1 设计门槛 | 先定义版本和来源标识及回放验收；不改变现有临床模型或重算冻结结果 |
| Widget / 未实现候选 | Manifest 无 AppWidgetProvider，源码无桌面组件；纵向设计只有概念 | Today/Quick Log/隐私与伪装安全组件均未落地 | 需确定锁定时内容、点击解锁和缓存策略 | 高隐私：桌面泄露、解锁授权、重复记录、过期缓存 | P2 | 只读锁定安全 Today 组件原型，默认无健康内容；先验收隐私再做写入 |
| 暂停/停药 / 基础动作存在，独立状态候选 | 药物 `active`、`NotesRepository` 停用关闭规则/方案、历史 PAUSED/STOPPED/RESUMED 里程碑；`TimelineV2Test` 结束规则 | 能停用药物/停止提醒并写用户历史事件；无独立状态+原因实体及显式与时期关系。历史里程碑不自动改方案 | 独立状态的生效时间、原因/恢复、回溯更正规则待决策 | 高：状态不能制造未发生的停药/补记事实或悄悄结束用户时期 | P1 设计 | 确认规则后先只读区分现有“当前停用”和“历史事件”，不自动联动 |
| 扩展 / 尚未实现候选 | `RuleKind` 仅 days/hours/weekly；`Engine.Curve` 无 T；Manifest/依赖无 Health Connect/Wear OS | 无多阶段 CyclePlan、额外途径/植入模型、T PK、Health Connect、Wear OS；已有一般记录/注射/凝胶/贴片不能算这些扩展已完成 | 概念/长期候选，不在本轮授权内；模型需独立文献与验证 | 新规则/迁移、模型错误、额外权限和设备隐私/同步边界 | P2 或更后 | 每次只选一个经过决策的单元；模型研究先行，集成先明确数据最小化/离线边界 |

## 缺陷、已批准工作和设备验收

- **确认缺陷、本轮修复并测试**：库存操作 0/11dp 宽、日期时间文字裁切、复诊操作不等高、频率选项截断、日历数字/标题及时间选择器 AM/PM 裁切；结果与覆盖条件以 [UI 审计](ui-button-consistency.md) 最新章节为准。
- **确认缺陷等待修复**：原UI/时期回归无已知剩余严重问题；最新PK P0另复现校准缺陷，见下。未覆盖设备情形不据此宣布没有缺陷。
- **已批准未实现的大型功能**：本轮核对没有找到可将上表任何完整大型功能直接列入这一栏的最新明确授权。早期 M1/研究评分不是新的开发指令；不扩张范围。
- **设备待验收**：本轮 UI 的真实短屏、字体/输入法/日期时间输入、滚动与 TalkBack；Build 25 实际用户数据覆盖安装和旧删除恢复；OEM 提醒/Direct Boot/权限变化；应用锁/启动器。Robolectric/API35 模拟器不能替代这些验收。
- **旧非均匀 slot identity**：`RegimenDefinition` 保存不同时刻剂量；未知身份标记和覆盖、改期/跳过现有测试继续检查。稳定业务身份和旧数据如何对齐是未定产品/迁移问题；未复现的数据一致性错误不按猜测重写引擎。若新场景能复现错误，再单列 P0 缺陷与失败用例。

## 用户需决定与下一轮建议

建议先决定专用 CSV 的字段、时期/阶段的范围与来源、时区和日期精度，然后只实现该导出单元。它复用现有只读投影，能独立验收，无 schema 或临床计算改动。

后续分别决定：面板如何识别同次采样/更正；自定义分析物单位；PDF 留存是否加密及是否进备份；注射 mg/mL 与包装单位；暂停/恢复是否联动当前方案；历史 PK bundle 的算法/参数/体重版本政策；小组件锁定隐私边界。不要合并成一次“大版本”授权。

## 最新产品方向补充（2026-10-08，REQUIREMENTS §42）

复诊独立导航及所有功能保留，撤销移入日历/删除侧边栏的旧决定。医疗档案**规划中，未实施**；现阶段只批准正式设计文档，不批准附件系统立即开发。见 [长期设计](design/medical-records-roadmap.md)。阶段 A–E 分别需设计和兼容性验收；有实际可用内容后再考虑更名。Visit Pack 2、医生管理、附件安全库和跨模块关联的范围应依据此设计协调，避免重复模型；本轮不改变上述功能状态或优先级。

## 最新舌下 PK P0 与后续（2026-10-09）

P0**完成研究并测试，不是模型科学验证通过**：[证据](pk-research/sublingual-v2.md)、[报告](pk-research/results/sublingual-p0-report.md)、[校准审计](pk-research/sublingual-calibration-audit.md)、[P1规格](pk-research/sublingual-p1-design.md)。7项原研究，两项全文/五项原始摘要，训练/外部/定性分离；独立2mg/46min对照和234合成模型点、长期叠加/停药/校准测试已实现。生产参数、其他模型、历史与Schema不变。

| 状态/优先级 | 证据与缺口 | 独立最小单元/验收 | 规格与风险 |
| --- | --- | --- | --- |
| P1-B，已实施/本地与API35 CI通过 | 逐条资格层、显式基线API、Calculator统一子集、四语原因；500/220反例及三个合格正例 | 见P1-B验收，CI日志/机器产物已回查 | §47授权，无迁移；未知不是零，完整仅指保存的模型输入 |
| P1-A，已完成并测试（CI全绿/非临床验收） | 近等速率校准放大/错误零值及默认0–152715区间 | 完整SL固定速率，仅幅度；纯SL一维MAP/MC，混合非SL仍调速率 | [验收](pk-research/sublingual-p1a-verification.md)；算法2、旧快照1兼容、人口参数未变；非临床准确性证明 |
| 确认缺陷，P0优先后续 | 因果插值跨未来化验；摘要忽略因果时点 | 精确时点/边界和摘要截止，任意过去t未来化验扰动不影响 | 现有LabFit精确网格正确；只修确认应用路径，不改回顾式或旧资料 |
| 确认缺陷，P1 | 全离群时excluded集合与实际采用冲突 | 标识/计数一致及完整排除原因 | 全离群、自动警告/用户排除是产品决定；不认定残差=化验错误 |
| P2，研究未实施 | SL吞咽份额、含服档、>8h尾、2/4mg及重复给药外推；仅Doll拟合不够 | 取得完整可比原文/数据，独立测试A经验核与B双途径，不投UI | 数据/原文缺口明确；AUC代理非实测，参数不可辨识，复杂不自动更准；无立即授权 |
| 候选/需产品决定，P1设计 | 因果模式无结果获知时间，历史无可执行算法bundle | 先确定知识时点和模型/来源版本回放边界 | 可能涉及未来迁移/备份，但P0无Schema变化、不重算历史 |

下一轮单独建议：**P1-C因果插值与摘要边界**，在每个历史求值时刻仅使用当时可用的合格化验；然后独立修全离群记录一致性。人口模型保持原样。它优先于先前专用CSV候选。P2双途径人口模型尚未实施，等待科研证据和单独授权，不将Featherline参数移植投产。

## P1-B当前边界

P1-B已实施逐条历史资格门控，未确认治疗前的化验不自动生成基线；原始点全部保留，bands/摘要/diagnostics共享合格子集。验收见 [P1-B记录](pk-research/sublingual-p1b-verification.md)。P1-C因果插值/摘要和全离群标识一致性仍未修复、待独立授权；P2人口模型仍研究，人体外部准确性未确立。

未来候选：明确的用户治疗前确认及持久化语义；非SL特定的有界历史加载与残余误差预算。均未实施，不把数据库首条记录、导入开始或180天范围当现实治疗开始。现有DAO仍全量读取，新资格残余计算有资源预算，不承诺任意规模历史读取不会耗尽内存。
