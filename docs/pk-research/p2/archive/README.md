# P2-R → P2-AT 完整研究报告索引（2026-10-10）

> **研究归档，不是临床准确性证明或生产模型发布。** 此目录保存已完成阶段的**完整 Markdown 报告正文**；现有冻结协议、正式 E2_SL 参数和 APK 没有修改。历史报告中的“尚未上传 GitHub”等句子反映**成文时**的状态，而非本次归档后的状态。

## 从哪里开始读

1. **[P2-R 已在仓库的升级证据门槛](../p2r-cross-study-evidence-upgrade-gates.md)**：哪些证据才允许 M0/M1/M2 升级；[来源台账](../p2r-cohort-evidence-ledger.json)、[内部暂定门槛](../p2r-upgrade-policy.json)、[审查代码](../../../../tools/pk-research/p2r_evidence_gate.py)。
2. **[P2-X 条件参数集合](p2-x.md)**：40 个条件参数组合、近优 15 候选；是已暴露组级数据的探索性筛选，不是个人参数或 95% 置信区间。
3. **[P2-AG 研究型跨研究比较](p2-ag.md)** / **[P2-AL 稳健性与模型选择门槛](p2-al.md)**：对训练过的研究不应将过拟合误称独立验证；P2-AG 的 M1 不是 App 的 Legacy PK。
4. **[P2-AQ 取得的真实 OSF 数据](p2-aq.md)**：VNC54 含最多 11 名参与者的 90 分钟 E2 测量，仍不足以验证连续时程；不分发逐人 XLSX。
5. **[P2-AS 正式旧模型审计](p2-as.md)** 和 **[P2-AT Legacy 与 M2 的直接比较](p2-at.md)**：这是回答“旧模型为什么不受同等检验”的最直接两篇文献。P2-AT 的 M2 优势是在其**已经接触过**的 Price/Rosano/Komesaroff 资料上的形状拟合，不是盲化外测。
6. **[当前证据与投产边界](EVIDENCE-AND-RELEASE-STATUS.md)**、[原始研究包及复现说明](SOURCE-PACKAGES-AND-REPRODUCIBILITY.md)。

## 按阶段阅读完整报告

| 阶段 | 本仓库文件 | 主要工作 |
|---|---|---|
| **R** | [已存在的 P2-R 原始文档](../p2r-cross-study-evidence-upgrade-gates.md) | 跨研究人体证据台账、M0/M1/M2 升级的 fail-closed 门槛 |
| S | [完整报告](p2-s.md) | Rosano/Komesaroff 早期增长边界、配对协方差与检测平台 |
| T | [完整报告](p2-t.md) | Doll/NCT 等 IPD 可获取性、同队列去重与采样缺口 |
| U | [完整报告](p2-u.md) | Rosano/Komesaroff/Price 组均值联合形状约束、3,605 离散候选 |
| V | [完整报告](p2-v.md) | 连续参数 M0/M1/M2 候选、跨研究拟合和代码交叉复算 |
| W | [完整报告](p2-w.md) | 重复给药 q6/q12/q24、Cortez/Yaish 检验及慢尾不确定性 |
| X | [完整报告](p2-x.md) | 40 套条件拟合、近优 15 候选与长尾可辨识性 |
| Y | [完整报告](p2-y.md) | Price 半衰期与 Yaish 前后浓度的条件约束 |
| Z | [完整报告](p2-z.md) | 未知给药时钟、统计汇总差异及 2048 个时间情景 |
| AA | *没有找到可核实的独立阶段报告* | 不补写虚构成果 |
| AB | [原始核完整性说明](p2-ab.md) | PR #1 的科研计算核导入和冻结哈希 |
| AC | [原始来源到计算核复现](p2-ac.md) | PR #1 的数学核与来源复算 |
| AD | [完整报告](p2-ad.md) | 背景—长尾混淆与极端尾部灵敏度 |
| AE | [完整报告](p2-ae.md) | 快慢输入比例/速率共同变化时的欠识别 |
| AF | [完整报告](p2-af.md) | 消除/慢吸收交换与重复给药后预测函数的稳健性 |
| AG | [完整报告](p2-ag.md) | 跨研究留出与过拟合；不能声称 M2 比简单研究模型更准 |
| AH | [完整报告](p2-ah.md) | Price 1997 原文 Table 1 AUC₀–₂₄ 与 Figure 1 的口径 |
| AI | [完整报告](p2-ai.md) | Figure 来源分级、剂量比例性及 0.25/0.5/1 mg 汇总 |
| AJ | [完整报告](p2-aj.md) | 原版扫描 Figure 1 重新数字化，1 mg AUC 约 1551 |
| AK | [完整报告](p2-ak.md) | 0.5/0.25 mg 遮挡读图与条件性区间 AUC |
| AL | [完整报告](p2-al.md) | M1/M2 模型排名的读图敏感性和分层验收门槛 |
| AM | [完整报告](p2-am.md) | 人体外测协议、采样时间误差、形状/绝对/尾部三门槛 |
| AN | [完整报告](p2-an.md) | 共享锚点协方差、缺失机制、弱锚点与排名反转 |
| AO | [完整报告](p2-ao.md) | 人体数据来源检索：Doll、Yaish、OSF、MaineHealth 等 |
| AP | [完整报告](p2-ap.md) | OSF 访问受限时的文件级准入检查方法 |
| AQ | [完整报告](p2-aq.md) | 已取得 OSF Excel 的实际字段与个体 90min E2 汇总审计 |
| AR | *没有找到已完成的独立阶段报告* | 仅为此前拟议的单点可辨识性后续研究，不标为完成 |
| AS | [完整报告](p2-as.md) | **正式 HRT Log E2_SL** 公式与 Price/Rosano/Komesaroff/Yaish 的公平条件审计 |
| AT | [完整报告](p2-at.md) | **正式 Legacy vs 15 套 M2** 同尺度、同时间与可见数据的直接比较 |

## GitHub 软件开发对应关系

- [PR #1 — 研究用 M2 Kotlin 核和冻结重现](https://github.com/DevenirTwilight/HRT-Log/pull/1)：ResearchSublingualV01、形状比较器、研究输入适配器。
- [PR #2 — Android M2 实验页面集成](https://github.com/DevenirTwilight/HRT-Log/pull/2)：可选的相对曲线研究页面，与正式 pg/mL 浓度页面分开；以 PR 的实时状态为准，**本归档不声称已发布**。
- [生产旧模型参数](../../../../pk-engine/src/main/resources/pk-params.json)和 [正式 Engine](../../../../pk-engine/src/main/kotlin/net/plainnotes/app/pk/Engine.kt)。原有冻结文件在本次归档中均保持原样。

\n## 逐阶段科研源码及计算结果\n\n除完整报告外，已把先前独立 ZIP 中**184 个经过筛选的 Python、CSV、JSON 文件**收入 [repro/](repro/README.md)。它们只供科研重现与源数据溯源，不进入 Android 构建；具体纳入/排除及每文件 SHA-256 见 [清单](repro/SELECTION-MANIFEST.json)。原始期刊图片和逐人健康数据没有上传。\n\n## 重要的科研限制

- 现有研究已经充分说明：**旧模型并非有临床准确度证书**；M2 在已见的某些曲线上形状拟合较好，但不能用再拟合评价当作独立人体外测。
- Price Figure 1 和 Table 1 的 AUC 差异仍未解决；长尾/谷值的不确定性可能远超过冻结 15 候选的范围。
- Yaish/VNC54 真实 90min 个体样本并非新的完全未见研究，也**不等同于 0–24 h 个体药代时间序列**。
- 目前无足以证明两种模型个体 pg/mL 预测优劣的、真实锁定且合格的独立人体数据。
- 仓库归档的是**研究报告和来源溯源**，不能拿作个人治疗建议；更不能让独立归档自动授权替换生产参数。

最后更新时间：2026-10-10。本目录是新增文件集合，不改变 P2 原始 protocol lock 或 baseline。
