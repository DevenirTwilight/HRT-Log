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


---

## 2026-10-11：P2-AR 真实个体数据后续研究（不重写历史）

此前 P2-AR 于 2026-10-10 补做的是解析证明与合成反例，当时并未重新读取 OSF Excel。现已从用户授权保存的 VNC54 原始 XLSX（与 P2-AQ 存档 SHA-256 相同）真正重算 11 名不同受试者的 20 次给药前/名义90min后配对访视，在 **五个明确为假设的日内给药时钟** 下，对正式 Legacy 与冻结 P2-X 15 套 M2 进行非负背景/自由增益的条件相容性检查。

完整交付见 [P2-AR 真正读取原始资料的研究报告](../p2ar-real/P2-AR-REAL-REPORT.md)、[仅聚合 JSON](../p2ar-real/p2ar-real-aggregate.json)、[审计源码](../../../../tools/pk-research/p2ar_real_visit_audit.py)和[13 项合成/聚合测试](../../../../tools/pk-research/test_p2ar_real_visit_audit.py)。原始逐人 XLSX/ZIP **没有加入公开 GitHub**，获得授权的持有人可以按 [README](../p2ar-real/README.md) 在私人环境重新生成聚合结果。

该资料此前已被模型开发接触；真实给药钟、慢性剂量史和逐次采样实时时刻缺失。**即使 M2 在已见 Price/Rosano 组曲线拟合更好，也不得从该条件测试认定 Legacy 或 M2 的个人浓度预测更优。** 此阶段标记为 **`REAL_DATA_RETROSPECTIVE_CONDITIONAL_AUDIT`**，独立外部验证仍然为 0，P2-C 继续 HOLD。历史原始 P2-AR ZIP 仍未知，并没有冒充找回。
