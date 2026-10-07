# Prior art 与独立设计记录

2026-10-07 建立。目的：如实记录研究、区分通用需求与具体表达，帮助审阅独立设计；不是法律意见，也不是宣布项目没有任何历史许可问题。历史来源链仍见 [docs/licensing.md](docs/licensing.md)。[竞品证据目录](docs/hrt-competitor-matrix-2026-10-07.md)固定本次审阅提交。

## 四类边界

1. **通用思想 / prior art**：服药时间表、版本、库存、抽血、记录身体感受、治疗阶段、时间线、日期纪念、复诊报告、本地隐私、认证加密。不能因竞品已有就称其为某项目独有，也不能因此主张本项目首创。
2. **可研究的 UX/建模需求**：原计划与实际登记分开；包装数量与药物量分开；历史换算快照；补录/未知与确认漏服分开；共享首页/widget只读projection；范围可选择的医生报告。研究其解决的问题，结合自身数据和用户任务写原创流程。
3. **不直接复制**：具体界面层次/卡片布局/图标/配色组合、截图、文案/翻译、产品/医学预设表、算法实现/参数表/字符串grammar、source/测试fixture。通用数学和加密原语可以独立使用，依据公开标准/原始文献，不由竞品实现反向翻译。
4. **受许可证约束的复用**：许可证许可与是否符合独立产品目标是两件事。GPL/AGPL并不禁止研究思想，也不绝对禁止依法复用；如果选择复制/修改/链接相关实现，需分析完整授权链及分发义务。当前策略是独立设计，不把受限实现纳入本项目。

## Feature 记录

| Feature | Prior art | HRT Log reason | Independent design | Relevant commits/docs |
|---|---|---|---|---|
| 既有提醒、包装、wellbeing | Trans Memo，含开发中对界面/截图的参考 | 迁移原用户记录、减少忘记与重复输入 | 自写导入器；原始历史如实致谢，不声称从未参考UI；新daily/review依据核实来源重新设计 | REQUIREMENTS §16、licensing.md；wellbeing-research/design；bf7d6d4 |
| 单药计划版本与事件快照 | 通用版本化系统；Featherline group/log 对比 | 编辑方案不改写已发生记录 | 现有Rule/Record上新增immutable medication revisions；不用竞品identity key/serialization | [现状](docs/hrt-product-research-2026-10-07.md)、[设计 II.1](docs/design/longitudinal-hrt-record.md) |
| Treatment Epochs | 临床纵向记录/时间区间；Chrysalide treatment changes、Featherline替换链 | 多药组合与化验/观察同阶段回顾 | 半开区间组合版本、未知/修订链、非因果；非复制group替换机制 | [独立设计](docs/design/longitudinal-hrt-record.md) |
| runway | HRT Log现有forecast；Featherline schedule simulation；MyHRT/MyTRT supplies | 明确不同库存预测依据 | scheduled/observed/current/scenario四模式，独立量纲和projection定义 | CalendarModel/StockScreen；设计 I.B |
| Widget | Android平台通用；Featherline dose/Anchor；Mona、MyHRT | 减少记录阻力，保护桌面显示 | Today/Privacy/Quick-log/disguise-safe，自身事务幂等与opaque action；不复制视觉/回调实现 | 设计 I.C |
| Timeline/Milestone | Featherline TrackedDate；TransTracks；Chrysalide objectives | 将方案、实际、化验与个人观察放同轴 | source-reference projection、time precision、确认/未确认，里程碑用户确定 | 设计 II.2 |
| Lab Context | Featherline panel E2/T间隔；HRT Log当前E2 elapsed | 回顾采样处于何种记录上下文 | 分成分last actual、epoch、context revisions、避免自己校准验证自己 | 设计 II.3 |
| 来源不可变快照 | 文献版本/溯源通用思想；本项目症状来源核查 | 防止目录升级重新解释历史 | SourceRevision+ObservationSourceMatch、legacy unknown、不附当时未匹配来源 | 设计 II.4；wellbeing-research.md |
| Visit Pack/事实摘要 | 本项目复诊PDF；HRTMe GP报告；Chrysalide预约 | 准备复诊并由用户控制分享 | 现有预约扩展、确定性计数/问题清单/选择内容/输入修订snapshot | 设计 II.5–6 |
| 注射与vial/site | Mona placements；Yuuki site picker；MyHRT/MyTRT | 记录真实体积/部位与库存量纲 | 依托Container/SupplyEntry，浓度与量换算留依据；历史图不作建议 | 设计 II.7 |
| hormone-agnostic记录 | Mona广泛route；MyHRT、MyTRT、HRTMe | 不把数据结构绑定一种人群 | ingredient/form/route/product分离、复方和CyclePlan；记录支持与PK能力分开 | 设计 II.8 |
| backup bounds | AEAD/KDF/有限解析通用安全原则；Featherline/MyHRT核查 | 安全恢复不破坏原有历史 | 自己的PNBAK兼容、有限读取/typed semantic validation、候选预算实测 | 设计 I.E |
| 浓度与校准 | 原始文献/公开数学；曾经参考Transmtf来源链 | 提供明确局限的可选估算 | 当前已文献独立重写，不能回灌竞品算法/未授权参数；旧history许可记录保留 | licensing.md、pk-model.md、pk-params.json、LiteratureValidationTest |
| P0 历史完整性实施 | 通用事件快照、来源溯源、未确认状态与事务验证；上方各主题研究 | 修复当前可变配置重解释历史与自动未登记语义 | 扩展自身config_snapshot；nullable症状context；兼容旧存储的UNCONFIRMED映射；固定PNBAK1协议下自写预算与校验。不引入竞品实现/参数/UI/文字 | [先行设计](docs/design/history-integrity-p0.md) 53eb35e；实现4667086、显示/单位边界b7aae2b；后续验证见HANDOFF 2k |

除明确列出的P0实施行外，各行仍为“已有行为审计或设计提案”，后续实现才填对应 implementation commit，不用研究文档提交冒充功能实现。首次研究提交 `131ad14`，矩阵提交 `c926b85`；具体提案参见其 Git history。

## 哪些项目尤其需要隔离式流程

| 项目 | 许可状态（本次固定提交） | 本项目的实际操作边界 |
|---|---|---|
| Featherline | GPL-3.0 | 读行为/模型、写抽象需求；不复制Kotlin、算法表达、UI/资源/测试；若以后选择依法复用需先解决整体许可证/源码分发义务。 |
| Mona | AGPL-3.0 | 同上；若复用其受保护实现到修改版本，网络交互提供源码义务等需具体分析，不能笼统说“AGPL感染所有研究”或“不开服务器就完全没义务”。 |
| TransTracks | GPL-3.0-or-later | 归档不取消版权；照片/UI/备份实现仍不能直接搬。 |
| HRT Recorder | 未发现 LICENSE | GitHub公开查看不授予一般复制/修改/分发权；研究抽象行为，不建议复制任何源码。 |
| MyHRT security | 自定义 Source Verification License | 明确仅为核查，禁止用于其他产品；只记录设计事实，密码算法与加密使用公开标准独立写。不标MIT/开源。 |
| Trans Memo / HRTMe / MyTRT | 完整应用未找到可审阅的公开授权源码 | 用官方行为说明研究任务，不复制截图/文字/图形；不反编译/提取资源。本轮未这样做。 |
| Chrysalide / Yuuki HRT-Tracker / Transmtf | 顶层MIT | MIT允许符合条件复用，但本项目仍以研究为主；真要复用须逐文件来源/版权/依赖核查，保留通知。Transmtf PK无license上游风险尤需回原文献。 |

本次同一研究者已读多个实现，因此**不能把后续自己编码宣称为严格法律clean room**。可执行的“防复制”流程是：研究行为/需求→公开抽象规格→独立作者/实现→diff审阅；如需要严格隔离，则由未看受限源码的实现者，仅依据去掉表达/算法细节的需求文档编码，研究者与实现者之间保存交付审查。不是一条免责声明就能免除版权义务。

## Design-before-code：建议成为后续贡献规则

重大新功能先提交 `docs/design/<feature>.md`，至少包含：

1. Problem statement：具体用户任务、当前不足、成功标准。
2. Constraints：隐私、医学中立、离线、时间精度、旧数据/备份兼容、授权。
3. Data model：量纲/不可变字段/关联/不变量、迁移与unknown路径。
4. UX rationale：为什么适合HRT Log，原创流程/文案草案，可访问性和四语；不要贴竞品截图当设计要求。
5. Alternatives/prior art：项目、固定提交/官方链接、研究范围、借鉴问题、明确不复用的具体实现。
6. Validation：合成数据、关键反例、安全边界、回滚与真机验证。
7. Review：先审设计，再写代码；若实现改变设计，先同步文档。

实现 PR 提供来源声明：使用了哪些外部材料，哪些实现是独立的；任何真实复用逐文件列出许可/作者/通知，不写无法证实的“全部原创”。审阅扫描新增源码/资源中的上游路径、copyright、literal文案/fixture/模型参数，重点核对未授权搬入与跨语言逐行翻译；相似度工具只是线索，不是法务结论。图标/字体/医学量表/翻译另有许可，MIT软件许可不覆盖它们。

项目级 LICENSE 是当前缺口：由维护者在历史/作者/依赖审计后选择，记载版权归属和第三方通知；不要让新贡献者以为没有LICENSE也可自动复用。旧移植代码仍在history，不通过新增LICENSE追溯授权他人的作品。本轮不删除旧标签/history或代作者联系许可方。
