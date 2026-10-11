# HRT Log 舌下 E2 药代研究进展总档案（P2-R → P2-AT）

**归档日期：2026-10-10。** 本目录用于让研究进度在 GitHub 中按阶段阅读、审查和延续。它不是药物使用说明、人体预测准确性认证，也不授权替换正式 `pg/mL` 模型。

## 从哪里开始

1. [跨阶段科学结论与产品决策](MODEL_STATUS.md)：现阶段对 Legacy 和 M2 能/不能得出的结论。
2. [逐阶段目录](#逐阶段完整索引)：查看 R 到 AT，每一阶段可以单独打开。
3. [本地独立科研包清单和 SHA-256](ARCHIVE_MANIFEST.md)：完整可重复研究包的来源记录，**不是声明这些 ZIP 已上传 GitHub**。
4. [原始 P2-R 研究及证据门槛](../p2r-cross-study-evidence-upgrade-gates.md)；[P2 AB/AC 原版源到核复算](reports/p2-ac.md)。
5. [正在评审的 PR #2](https://github.com/DevenirTwilight/HRT-Log/pull/2)：M2 实验页面工程集成；这与科学放行是两条独立工作流。

## 逐阶段完整索引

| 阶段 | 可见内容 | 阶段结论 / 资料状态 |
|---|---|---|
| **R** | [跨研究证据台账与升级门槛](../p2r-cross-study-evidence-upgrade-gates.md) | 原仓库已存在；明确定义 G0–G4、数据来源去重及外部验证要求 |
| **S** | [早期增长边界与研究间检测差异](reports/p2-s.md) | 完整报告 |
| **T** | [原始数据可获得性与身份追踪](reports/p2-t.md) | 完整报告 |
| **U** | [跨研究汇总点的共同曲线限制](reports/p2-u.md) | 完整报告 |
| **V** | [连续函数候选与机制模型](reports/p2-v.md) | 完整报告 |
| **W** | [重复给药情景及模型反证](reports/p2-w.md) | 完整报告 |
| **X** | [40 组候选 / 15 套近优 M2](reports/p2-x.md) | 完整报告；实际 Kotlin 核另见 PR #1/#2 |
| **Y** | [跨研究限制与新候选](reports/p2-y.md) | 完整报告 |
| **Z** | [未知给药时钟与鲁棒性](reports/p2-z.md) | 完整报告 |
| **AA** | [编号核验说明](P2-AA-AND-AR-STATUS.md) | **本次未找到独立编号为 AA 的可核验完成报告**；不可虚构 |
| **AB** | [M2 Kotlin 核数学不变量](reports/p2-ab.md) | 完整文档，来源 PR #2 |
| **AC** | [P2-X 到 Kotlin 源码一致性](reports/p2-ac.md) | 完整文档，来源 PR #2 |
| **AD** | [基线与长尾可辨识性](reports/p2-ad.md) | 完整报告 |
| **AE** | [基线 / 慢吸收联合自由度](reports/p2-ae.md) | 完整报告 |
| **AF** | [消除与慢输入率同时未知](reports/p2-af.md) | 完整报告；长期谷值不稳健 |
| **AG** | [跨研究外推和模型过拟合](reports/p2-ag.md) | 完整报告；研究型 M1/M2，不是 App Legacy |
| **AH** | [Price 0–24h AUC 的原文口径](reports/p2-ah.md) | 完整报告 |
| **AI** | [Price 剂量比例与读图下界](reports/p2-ai.md) | 完整报告 |
| **AJ** | [Price 原刊 Figure 1 重读](reports/p2-aj.md) | 完整报告；1mg 组新 AUC 约 1550.6 |
| **AK** | [Price 低剂量曲线区间](reports/p2-ak.md) | 完整报告；0.5/0.25mg 遮挡较多 |
| **AL** | [模型选择验收门槛](reports/p2-al.md) | 完整报告；假设变动下不能宣布通用赢家 |
| **AM** | [独立验证协议与实际采血时刻](reports/p2-am.md) | 完整报告；时间误差不可忽略 |
| **AN** | [测量误差、缺失和统计](reports/p2-an.md) | 安全摘要；原独立报告/程序见包清单 |
| **AO** | [公开人体数据来源发现](reports/p2-ao.md) | 安全摘要；数据共享声明不能当文件已取得 |
| **AP** | [OSF 官方网页/API访问记录](reports/p2-ap.md) | 安全摘要；**历史性访问受阻**，已由 AQ 更新 |
| **AQ** | [VNC54 / TCRUW 实际 Excel 审计](reports/p2-aq.md) | 脱敏聚合摘要；实际读取个体 E2，但没有合格全时程外测 |
| **AR** | [编号核验说明](P2-AA-AND-AR-STATUS.md) | **曾提出研究设想，本次未找到已完成的独立交付记录** |
| **AS** | [正式 Legacy E2_SL 参数与人体曲线审计](reports/p2-as.md) | 安全摘要；验证旧计算核≠临床准确 |
| **AT** | [正式 Legacy 与 M2 的直接形状对比](reports/p2-at.md) | 研究结论摘要；M2 在已见组级数据上拟合更好，尚非独立胜利 |

说明：此表保留 **每个编号的可核查状态**，未把空缺的 AA/AR 或前期企划误写为已完成。P2-D–Q 等更早的研究继续在仓库既有 `docs/pk-research/p2/` 中，不因本目录而废弃。

## 当前关键科研判断

- Legacy 默认舌下 `E2_SL` 是基于少数人体汇总点拟合的经验核。软件数学复现通过，但 10–30min 上升速度、6–24h 快速衰减与 Price/Rosano/Komesaroff 发表曲线存在结构差异；个人临床准确度未获充分证明。
- M2 具备可复算的 Erlang 快输入 + 慢 Bateman 输入、15 候选的数学功能，并在**已用于开发/选择候选**的 Price/Rosano/Komesaroff 组曲线上描述性形状误差较小，不能等同未来人体泛化优势。
- Price 1mg Figure1 人工梯形 AUC0–24 约 1550.6 pg·h/mL，原表 2109 pg·h/mL；差异仍未解释。不能借此直接声明论文错误或强行调参。
- VNC54 曾获取真实 90min E2 样本，来自已暴露 Yaish 队列，缺少逐人的完整 0–24h 连续采样及真实长期给药时间；不是新的盲化独立验证。
- M2 可以作为正式 App 内**用户主动进入的实验性相对曲线**，并排比较 Legacy；但 M2 尚无可独立确认的个人绝对浓度标定与临床长尾验证，不能替换原 `pg/mL` 预测。
- **原模型也没有“默认上线就准确”的特权。** 必须对两者进行对称的未来新队列评估。

## 工程分支状态快照

- 正式开发基线 `claude/new-session-1959qb` 记录 SHA `f69d892d47ca64250db7b35eb95c5e1d0f8a01d8`。
- [PR #1](https://github.com/DevenirTwilight/HRT-Log/pull/1)：早期实验模型 v0.1，Draft。
- [PR #2](https://github.com/DevenirTwilight/HRT-Log/pull/2)：集成 M2 实验页及受审的 `AppShell.kt` 导航接线偏离 `PD-2026-10-10-M2-ENTRY`，Draft。审计时 PR head `cd56688e36c4dccaceab4be814773c562ef862cc`。Release A–F 曾通过并记录，**最新一轮设备 CI 曾出现失败，应以 PR 实时结果重新核对**，不能把早期绿灯当新提交绿灯。
- 本总档案是**文档专用独立分支**；没有改动 `Engine.kt`、`pk-params.json`、数据库、APK、P2 原始锁定协议或 PR #1/#2。

## 内容收录边界

包含已经可核查的完整**中文研究报告（S–AM）**、来自 PR #2 的 AB/AC 完整审计、AN–AT 的脱敏方法与结论摘要、研究包 SHA 清单。**未上传完整独立 ZIP、未上传 Price 原始期刊 PDF/受版权保护扫描图、未上传两份 OSF 的逐人原始 Excel/问卷、没有将合成模拟当成人体测量。**

报告是阶段历史快照：早期“数据未获取”状态可能已由后期“已获取”更新。阅读时按时间顺序使用最新证据，不可挑选早期版本覆盖后期事实。
