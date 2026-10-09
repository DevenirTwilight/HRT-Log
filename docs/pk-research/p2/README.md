# P2-A/B 舌下人口模型研究（已完成隔离研究，未投产）

[原始证据审计](evidence-audit.md)、[目录](evidence-catalog.json)、[获取记录](source-manifest.json)、[整研究分割](split-manifest.json)。Cortez fresh全文、Yaish P0全文重核，其他五研究仅摘要。TRAIN1/DESIGN_EXPOSED3/LOCKED_EXTERNAL0/QUALITATIVE3/NOT_COMPARABLE0。

[拟合前冻结协议](validation-protocol.md)和机器设置/lock在9051875独立提交推送；研究实现138b981、报告回归af3767e在后。没有科学协议偏离。P0/P1已完成、原文件和生产hash锁定只读。研究没有Android依赖。

[候选数学](candidate-models.md)、[可辨识性](identifiability.md)、[按研究外部条件比较](external-validation.md)、[敏感性](sensitivity.md)、[结论与P2-C门槛](model-selection.md)、[实际验收](verification.md)。[机器报告](results/analysis.json)、[全部参数](results/candidate-parameters.json)、[逐研究误差](results/study-comparisons.csv)、[可读表](results/report.md)。339固定形状/基线情景各只拟合一个幅度；不是339人体采样。全部负结果保留，没有选赢家。

**external_validation_insufficient / clinical_accuracy_established=false**。数学正确、数值稳定不等于人体有效。保留现有人口模型；P2-C未实施且需独立授权。Build25/Schema9/P1校准/历史冻结/备份/PDF/签名不变，不发APK/Release/PR。

复现（Python3.10+标准库，无AndroidSDK、无网络依赖）：

```sh
python3 tools/pk-research/check_protocol.py
python3 -m unittest discover -s tools/pk-research -p 'test_*.py'
python3 tools/pk-research/research.py --output /tmp/hrt-p2-new-empty
```

输出必须为空目录，不能覆盖旧结果；SHA和完整命令/计数见验收。新输入/假设只能记录新的显式协议偏离，不静默修改冻结文件。研究CLI不自认证软件/临床成功，软件证据单独记录。
