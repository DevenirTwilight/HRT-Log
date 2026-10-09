# 浓度模型文献调研（M4a）

每个主题一份可读报告（`.md`）和一份机器可读数据（`.json`：`references` 文献列表，`entries` 参数条目，每条注明数值、单位、出处、在原文中的位置、研究人群）。数值只取自实际查看过的说明书或 PubMed 摘要/全文；没有可靠来源的写明"no reliable source found"。

| 文件 | 内容 | 状态 |
|---|---|---|
| `transdermal_cpa.*` | 透皮凝胶、贴片、醋酸环丙孕酮 | 完成 |
| `estradiol_oral_sl_im.*` | 戊酸雌二醇和 17β-雌二醇口服、舌下、戊酸雌二醇肌注 | 完成 |
| `spironolactone_progesterone.*` | 螺内酯（含坎利酮）、口服微粒化孕酮 | 完成 |

以上为历史M4a调研记录；文献引擎与参数已经实现，不再是待编写任务。

2026-10-09最新入口：[舌下P0科学审查](sublingual-v2.md)、[校准审计](sublingual-calibration-audit.md)、[P1规划（未实施）](sublingual-p1-design.md)、[自动对照](results/sublingual-p0-report.md)、[复现工具](../../tools/pk-audit/README.md)。独立来源数据在pk-engine/src/test/resources/sublingual-literature-validation.json；训练、外部、定性分组严格分开。科学外部验证未确立，不以旧拟合测试作为临床准确性证明。
