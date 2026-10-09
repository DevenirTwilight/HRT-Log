# 舌下 E2 P0 可复现对照（仅测试/研究）

不写生产参数，不执行旧`tools/pk-fit/fit.py`（它会覆写已发布参数）。Python仅标准库；与Featherline对照是公开数字加独立通用ODE算式，不含其源码。原始人体证据必须来自手工核实的JSON，不能由模型生成。

## 重新执行实际 Kotlin 与 Python

在仓库根目录，Java21、Android SDK已配置：

```sh
./gradlew -PjvmOnly :core:domain:test :pk-engine:test :importer:test --rerun-tasks --no-parallel --max-workers=2
./gradlew :app:testFullDebugUnitTest --tests '*SublingualCalibrationAuditTest' --tests '*ConcentrationCalculatorTest' --tests '*LabEstimateTest' --no-parallel --max-workers=2
python3 -m unittest discover -s tools/pk-audit -p 'test_*.py'
python3 tools/pk-audit/sublingual_compare.py
```

Gradle输出：`pk-engine/build/reports/pk-p0/engine-outputs.json`、`app/build/reports/pk-p0/calculator-audit.json`。对照输出：`docs/pk-research/results/sublingual-p0-report.{json,md}`和`...-curves.csv`。测试运行写入build，研究脚本写入output目录，不碰正式数据。

## 没有 SDK 时重算已保存研究输入

```sh
python3 tools/pk-audit/sublingual_compare.py --engine docs/pk-research/results/sublingual-p0-engine.json --calculator docs/pk-research/results/sublingual-p0-calculator.json --output /tmp/hrt-sl-p0
```

此命令只重算已有Kotlin输出的对照，**不是重新运行Android测试**。记录输入/脚本/参数的SHA256，生产参数版本`0.1.0-literature-draft`，生产参数SHA256`b2768a6947d29d65f272b1d20e31fd59b9661acf8166b437f2a3b179f235b16b`。结果JSON包含输入哈希。原文获取URL、日期、文件哈希及全文/摘要等级在验证数据JSON；研究全文仅临时读取，不复制进仓库。全部给药/化验输入为合成数据，不是研究参与者个人记录。

## 算术与科学分开验收

- 9组历史×13时刻×2模型=234条**模型点**；0.005h积分/峰值网格；停止后不继续给未来剂量。实际Kotlin与稳定独立计算最大差异约4.77e-10pg/mL。脚本检查采样差异≤1e-6、积分/峰值差异≤2e-6；检查不同速率近相等极限、长时间有限性及单位。
- 26项PK测试含新增8项；6项Calculator审计；5项Python测试。特征化测试保留当前缺陷的证据，不代表未来必须维持错误行为。
- 外部研究仅比较已实际报道的指标；Pines的±未确证SD/SE、Yaish90min是固定采样而非连续Cmax、Burnier是倍数条件比较。模型点不能增加人体样本量。
- Gamma族只检验结构不可辨识性；AUC是旧拟合脚本的模型代理，不是人体观测。这不是第三个已验证人口模型。未找到可可靠核对完整参数的第三候选，保留为缺口。
- CLI成功仅表示算术一致/文件完整；结果明确`scientific_acceptance: NOT established`。不能据此宣称任一模型临床准确。

CI在Android单测后执行上述Python测试、PK审计和对照，上传build下研究报告，不上传APK或个人健康数据。
