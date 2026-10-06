# 浓度模型文献调研（M4a）

每个主题一份可读报告（`.md`）和一份机器可读数据（`.json`：`references` 文献列表，`entries` 参数条目，每条注明数值、单位、出处、在原文中的位置、研究人群）。数值只取自实际查看过的说明书或 PubMed 摘要/全文；没有可靠来源的写明"no reliable source found"。

| 文件 | 内容 | 状态 |
|---|---|---|
| `transdermal_cpa.*` | 透皮凝胶、贴片、醋酸环丙孕酮 | 完成 |
| `estradiol_oral_sl_im.*` | 戊酸雌二醇和 17β-雌二醇口服、舌下、戊酸雌二醇肌注 | 进行中（快照） |
| `spironolactone_progesterone.*` | 螺内酯（含坎利酮）、口服微粒化孕酮 | 进行中（快照） |

下一步：据此写 `docs/pk-model.md`、`pk-engine/src/main/resources/pk-params.json`，以及"移植模型参数 vs 文献参数"对照表，交用户审核。
