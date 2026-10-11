# 两个尚未找到独立完成档案的阶段：P2-AA 与 P2-AR

日期：2026-10-10。此页存在的目的是**防止为了编号连续而编造科研完成记录**。

## P2-AA

本次核对了仓库 `claude/new-session-1959qb` 和 PR #2 最新可访问的树，找到了实验 M2 源码、P2-AB 数学核验证、P2-AC 源到核复现、科研 UI 原型文档，但没有发现以 `P2-AA` 为独立标题、能确认研究范围和验收结果的报告。

因此只称“编号资料待补证”；**不否认当时进行过工程原型工作，也不声称已验证一个未见的 P2-AA 成果。** 相关已知工程材料：
- https://github.com/DevenirTwilight/HRT-Log/pull/1
- https://github.com/DevenirTwilight/HRT-Log/pull/2
- [P2-AB](reports/p2-ab.md) / [P2-AC](reports/p2-ac.md)

## P2-AR

P2-AQ 结束时曾提出继续研究“真实 90min 个体浓度对 M2 参数的条件约束”，但现有可核查资料中没有独立完成的 P2-AR 报告、通过测试清单或固定输出。

随后实际完成并可核查的是 [P2-AS 正式旧模型审计](reports/p2-as.md)、[P2-AT 直接比较](reports/p2-at.md)。

待找到准确报告时，可以另建文档补足来源及 SHA；**不得将“研究建议”直接写成“已完成验证”。**


---

## 2026-10-10 追加补研（不改变上述历史核验结论）

用户要求：既然能够找回原先研究主题，就按真实可获得证据补做，不为了编号连续编造原始交付。

- **P2-AA**：历史会话找回的方向为“模型预测功能量与跨研究误差分解”；已**新完成** [P2-AA 解析审计](reports/p2-aa.md)，复算已见 Price/Rosano 的 Legacy 归一化 RMSE，分层列出长尾/基线/时钟/检测误差的不可分离性；这不是当时曾完成 P2-AA 的历史证明。
- **P2-AR（科学）**：历史提案为“利用 VNC54/Yaish 90min 真实配对采样约束 M2 可辨识参数”；已**新完成** [P2-AR 条件约束与合成反例](reports/p2-ar.md)；未读取个体 OSF 行数据、未进行真实 15 候选的逐人重拟合。工程 [PR #3](https://github.com/DevenirTwilight/HRT-Log/pull/3) 是另一个同名工作流。
- **P2-C**：[生产模型替换闸门](reports/p2-c.md)现已补齐审查文档，但**未执行、未授权**，状态仍为 HOLD。
- 只读独立计算：[程序](../../../../tools/pk-research/p2_missing_stages_reanalysis.py) 与 [回归](../../../../tools/pk-research/test_p2_missing_stages.py)。

本次追加不改变冻结 P2-X 参数、发布版本、应用引擎、数据库、个人健康数据或外测样本数（仍为 0）。
