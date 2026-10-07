# 下一版本：评分、优先级与明确产品定义

2026-10-07 研究建议，未批准实施。依据[现状](hrt-product-research-2026-10-07.md)、[81项竞品矩阵](hrt-competitor-matrix-2026-10-07.md)、[独立设计](design/longitudinal-hrt-record.md)。这是专家判断排序，不是实测用户研究，不用精确加权总分伪装客观性。

价值/独特性/一致性越高越好；复杂度/医疗安全风险/像复制风险越高越难或越不利。风险评价是按**本报告独立、中立、离线设计**，不是按竞品现有实现。低“像复制”不等于没有prior art；隐私/恢复也属于安全风险。成熟性依赖和历史正确性优先于功能数量。

| 候选 | 用户价值1–5 | 独特性1–5 | 方向一致性1–5 | 复杂度1–5 | 医疗/安全风险1–5 | 像复制风险1–5 | 优先级/理由 |
|---|---:|---:|---:|---:|---:|---:|---|
| 完整实际输入快照/历史PK与导出读取 | 5 | 4 | 5 | 4 | 3 | 1 | P0：现在编辑可重解释过去；先解决记录可信度 |
| 症状source provenance snapshots | 5 | 5 | 5 | 3 | 3 | 1 | P0：目录/匹配不能重写旧记录来源 |
| confirmed missed / unconfirmed区分 | 5 | 4 | 5 | 3 | 3 | 1 | P0：事实报告不得把未记录写成确认漏服 |
| 有限读取、typed恢复、schema1/2安全兼容 | 5 | 2 | 5 | 3 | 4 | 1 | P0：恢复失败不毁数据，现有加密保留 |
| 项目LICENSE/历史来源审计/PRIOR_ART流程 | 5 | 2 | 5 | 2 | 1 | 1 | P0：公开仓库没有明确开源权利，先治理 |
| immutable medication revision+regimen/epoch底座 | 5 | 5 | 5 | 5 | 3 | 2 | P1第一批：先最小记录模型，不一次做全部UX |
| Lab Context+panel+context revisions | 5 | 4 | 5 | 3 | 3 | 2 | P1：依赖版本，含采样前估算/未知说明 |
| Unified Timeline+epoch筛选 | 5 | 4 | 5 | 4 | 2 | 2 | P1：连接已有模块，差异来自语义不是换皮 |
| Appointment questions / Visit Pack | 5 | 4 | 5 | 3 | 3 | 2 | P1：扩展已有预约/PDF，内容选择与一致快照 |
| Since-last-visit deterministic summary | 5 | 4 | 5 | 3 | 3 | 1 | P1：依赖确认/未知与修订，纯事实模板 |
| 统一scheduled/current runway口径 | 4 | 3 | 4 | 2 | 2 | 2 | P1：已有两种算法，不必新造完整模块 |
| injection/vial/concentration/volume/site history | 4 | 3 | 4 | 4 | 3 | 3 | P1后段：先量纲与包装，再独立部位图 |
| hormone-agnostic基础字段/复方支持 | 4 | 4 | 5 | 4 | 3 | 1 | P1模型阶段：先能记录，PK范围不扩张承诺 |
| 自定义analyte/assay/原始单位保留 | 4 | 2 | 4 | 3 | 3 | 2 | P1后段：化验能力补齐，不猜转换系数 |
| observed consumption runway | 3 | 4 | 4 | 3 | 2 | 1 | P2：先保证ledger质量与coverage说明 |
| future regimen inventory scenarios | 3 | 3 | 3 | 4 | 3 | 3 | P2：只读分支，不提供优化方案建议 |
| Today/Privacy widgets | 4 | 2 | 3 | 3 | 3 | 3 | P2：输入便利，不抢纵向记录底座优先级 |
| Quick-log/disguise-safe widget | 4 | 3 | 4 | 4 | 4 | 3 | P2：lock/幂等/launcher缓存验证后做 |
| Timeline milestones | 3 | 2 | 4 | 2 | 1 | 3 | P2：简单用户事件，可随timeline先做轻量引用 |
| cyclical regimen / menopause专用流程 | 4 | 3 | 4 | 4 | 3 | 2 | P2：先用户需求验证；CyclePlan不套every-N |
| T PK与更多途径/植入模型 | 3 | 2 | 3 | 5 | 5 | 4 | P2：只有原文献/参数与验证够用才做 |
| Health Connect / Wear OS | 3 | 2 | 2 | 4 | 4 | 3 | P2：读取最小可选数据，不同步敏感全库 |
| 完整照片/body-progress社交相册 | 2 | 1 | 2 | 4 | 4 | 5 | 不建议当前做：隐私/附件预算高，偏离主问题 |
| AI解释化验/个性剂量/因果发现 | 2 | 2 | 1 | 5 | 5 | 4 | 不建议：不符合医学中立和离线边界 |
| adherence streak/排名/惩罚性漏服提示 | 2 | 1 | 1 | 2 | 3 | 4 | 不建议：把不确定记录道德化，掩盖事实 |
| 强制云账户/自动上传健康数据 | 2 | 1 | 1 | 5 | 5 | 3 | 不建议：削弱最强本地隐私约束 |
| 同时开发iOS/Watch/全平台 | 3 | 1 | 2 | 5 | 3 | 4 | 不建议下一版：维护/验证面成倍扩大 |
| 照搬Featherline Journal/Anchor样式 | 2 | 1 | 2 | 3 | 1 | 5 | 不建议：需求由自有timeline/milestone完成 |

## P0：立即修正可信度，不发大而全版本

目标是“编辑/目录升级/导入/恢复不会悄悄改写记录意义”。先完成快照、未确认语义、恢复bounds和许可治理的独立设计与合成回归。项目LICENSE需维护者选；不要把此调研意见当作授权。验收：旧备份恢复/原ledger净量不变、未来更改不改变历史、PDF不贴未匹配来源、自动未记录不写成确认漏服、失败恢复保持原DB。

## P1：下一版本的核心功能链

**版本底座 → epoch → Lab Context → Timeline → Visit Pack/事实摘要**，逐步交付。先支持已经存在的成分/途径与历史精度，hormone-agnostic是schema方向，不等于立即增加各人群医学内容。注射/包装量纲与runway是第二批；它们服务准确输入而不是让主界面变成库存软件。不要一次性把所有P1都承诺到同一release；每项独立migration/UX验收后决定范围。

## P2：在核心数据可信后扩展

widget、milestones、观察消耗、未来场景、CyclePlan、T PK、穿戴整合都值得按需求再看。模型验证和权限边界是门槛，不因竞品已有就赶工。前三者可做小功能，T/植入/多平台是独立长期项目。

## 核心问题：HRT Log 最适合成为什么？

**一个在本人设备上保存治疗历史、保留当时方案与采样/观察上下文、帮助复诊沟通的纵向 HRT 记录系统。** 英文短定义：**local-first longitudinal HRT record**；副标题可用“Your treatment history, with its context preserved.”（本提案原创草案，尚未替换应用文案）。

1. 用户核心问题：长期资料分散、改药后忘记当时方案、抽血缺采样上下文、观察记录缺来源、复诊时难从几个月日志提取事实；同时不想将敏感数据交给账户平台。
2. 独特能力：将现有离线加密、版本化计划/实际日志/库存、核实来源、阶段回顾/PDF连接成可追溯记录。单项通用，不宣称首创；同一语义和unknown处理会形成实际差异。
3. 核心：immutable历史、epoch/timeline、lab context、source snapshots、本人可控制的visit pack、备份/迁移可靠性。它们共同回答“那段时间是什么、实际记录了什么、随后观察到什么”。
4. 辅助：schedule/reminder、stock、quick log、部位记录和可选PK，目的都是降低输入成本/补上下文；不应挤占长期回顾首页。PK结果永远标明模型、假设和缺数据。
5. 不值得追：浓度模拟精度的军备竞赛、云AI教练、照片社交、肽类/健身记录平台、连续打卡排名、所有平台同时铺开；这些既缺现阶段优势，也增加医学/隐私/维护成本。
6. 三年后不显得像clone：对每项重大设计保留公开problem/model/UX rationale/prior-art与迁移依据；以独立epoch和context模型约束所有新功能，原创自己的查询/报告/交互；持续诚实记录早期Trans Memo参考和旧许可来源，不改写项目历史。辨识度来自可验证的长期产品规则，不来自功能总数、换色或“从未借鉴”的宣传。

未确定：用户对epoch自动切分/回溯更正、报告模块选择与日常记录负担的实际接受度，跨人群用语，最低设备上的备份峰值内存。应以少量合成原型与自愿用户测试确认，不从源码比较直接推断市场需求。
