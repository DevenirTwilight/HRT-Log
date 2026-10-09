# 舌下 E2 P0/P1-A 可复现对照（仅测试/研究）

不写生产参数，不执行旧`tools/pk-fit/fit.py`（它会覆写已发布参数）。Python仅标准库；与Featherline对照是公开数字加独立通用ODE算式，不含其源码。原始人体证据必须来自手工核实的JSON，不能由模型生成。

## 重新执行实际 Kotlin 与 Python

在仓库根目录，Java21、Android SDK已配置：

```sh
./gradlew -PjvmOnly :core:domain:test :pk-engine:test :importer:test --rerun-tasks --no-parallel --max-workers=2
./gradlew :app:testFullDebugUnitTest --tests '*SublingualCalibrationAuditTest' --tests '*ConcentrationCalculatorTest' --tests '*LabEstimateTest' --no-parallel --max-workers=2
python3 -m unittest discover -s tools/pk-audit -p 'test_*.py'
python3 tools/pk-audit/sublingual_compare.py --engine docs/pk-research/results/sublingual-p0-engine.json --calculator docs/pk-research/results/sublingual-p0-calculator.json --output /tmp/hrt-sl-p0
python3 tools/pk-audit/sublingual_p1a_compare.py --engine docs/pk-research/results/sublingual-p1a-engine.json --calculator docs/pk-research/results/sublingual-p1a-calculator.json --stability docs/pk-research/results/sublingual-p1a-stability.json --output /tmp/hrt-sl-p1a
```

历史P1-A Gradle输出曾写入`pk-engine/build/reports/pk-p1a/`和`app/build/reports/pk-p1a/`（当前输出改为pk-p1b，见末节），含engine-outputs/calculator-audit/stability JSON。P1-A报告写入`docs/pk-research/results/sublingual-p1a-*`。P0原始输出与报告只读保留，用显式输入路径重放旧结果，不以新测试覆盖旧证据。新数值/单参数验收见`sublingual-p1a-verification.md`；P0尚未修复的问题仍保留特征化断言。

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

## 旧版非舌下黄金夹具与性能基线

实际在旧提交执行，不是从新实现生成预期结果。临时detached工作树仅供基线测试，不建立长期开发分支；路径自行选择：

```sh
git worktree add --detach /tmp/hrt-p1a-legacy 2761ed95c49e1ae74f0accd46aaf66bb87254040
cp tools/pk-audit/legacy_non_sl_baseline.kt /tmp/hrt-p1a-legacy/pk-engine/src/test/kotlin/net/plainnotes/app/pk/NonSlBaseline.kt
cd /tmp/hrt-p1a-legacy
PK_BASELINE_OUTPUT=/tmp/non-sl-v1.json ./gradlew -PjvmOnly :pk-engine:test --tests '*NonSlBaseline' --rerun-tasks --no-parallel --max-workers=2
```

提交的`non-sl-calibration-v1.json`来自本轮实际旧版执行（原执行路径/workspace/tooling/pk-p1a/old-tree）；生成器独立编写，无第三方源码。比较五途径的幅度、速率、协方差、未校准/先验/后验中心与分位数，容差1e-9。计时采用同主机独立JVM、120事件、2881网格点、200样本、2热身、5次，记录每次秒数；它是局部微基准，不是跨设备性能承诺。原计时不含断言，新计时也不含。

独立Python还比较方案A归一化核的交点极限与r=1等价性；A仅研究，生产选择B。7项Python测试必须通过；P1-A报告`numerical_acceptance`与`clinical_accuracy_established`分开标记。CI生成新报告并保留旧P0重放，全部合成数据。

## P1-B：资格门控与旧证据只读重放

当前测试输出写`pk-p1b`目录。不要使用P0/P1-A默认输出去覆盖旧机器证据；明确传入已保存基线并输出到build或/tmp：

```sh
./gradlew -PjvmOnly :pk-engine:test :core:domain:test :importer:test --rerun-tasks --no-parallel --max-workers=2
./gradlew :app:testFullDebugUnitTest --no-parallel --max-workers=2
python3 tools/pk-audit/sublingual_compare.py --engine docs/pk-research/results/sublingual-p0-engine.json --calculator docs/pk-research/results/sublingual-p0-calculator.json --output /tmp/hrt-p0-replay
python3 tools/pk-audit/sublingual_p1a_compare.py --engine docs/pk-research/results/sublingual-p1a-engine.json --calculator docs/pk-research/results/sublingual-p1a-calculator.json --stability docs/pk-research/results/sublingual-p1a-stability.json --output /tmp/hrt-p1a-replay
python3 tools/pk-audit/sublingual_p1b_compare.py --engine docs/pk-research/results/sublingual-p1b-engine.json --calculator docs/pk-research/results/sublingual-p1b-calculator.json --eligibility docs/pk-research/results/sublingual-p1b-eligibility.json --output /tmp/hrt-p1b-replay
python3 -m unittest discover -s tools/pk-audit -p 'test_*.py'
```

P1-B脚本检查实际Kotlin的资格/观测计数、两个修复反例、三个合格正例及部分合格子集；人口曲线/P1-A拟合/先验带与保存基线比对。新报告与engine/calculator/eligibility机器输入独立命名`sublingual-p1b-*`。9项Python含两项资格报告/反例污染检测；不能通过删除原始观测或添加假基线使报告通过。工程成功与科学临床准确性标志分开。

起始b9a的实际双假基线重跑方法见P1-B验收记录及`legacy_p1b_baseline.kt`。该生成器只复制进旧detached工作树的测试目录运行，输出P1-B独立before JSON；不要把它放进正式应用/当前测试集。

## P1-C1：同采样前缀的查询及旧证据重放

当前Gradle生成路径为app/pk-engine的`build/reports/pk-p1c1`，不覆盖任何旧P0/P1-A/P1-B机器结果。

```sh
./gradlew -PjvmOnly :core:domain:test :pk-engine:test :importer:test --rerun-tasks --no-parallel --max-workers=2
./gradlew :app:testFullDebugUnitTest --rerun --no-parallel --max-workers=2
python3 tools/pk-audit/sublingual_p1c1_compare.py
python3 -m unittest discover -s tools/pk-audit -p 'test_*.py'
```

11项Python。旧三轮均显式传保存输入并用/tmp或build输出（以上命令），只读重放其历史结论，不把旧C1错误标志视作当前未修。新报告检查实际P1-B合格未来400/800/同时间增删输入：历史中心/四条分位数/摘要诊断不变，采样后生效；人体现有准确性仍false。旧生成器`legacy_p1c1_baseline.kt`仅复制到d45a67a detached树的app测试，环境`P1C1_BEFORE_OUTPUT`指定独立before JSON。

同机旧/新`p1c1_performance.kt`仅临时复制为app测试运行，环境`P1C1_PERF_OUTPUT`及`P1C1_PERF_COMMIT`指定输出/源码标签；2热身、5次、每批4个相同完整Calculator调用，不将临时测试留下进入完整app套件。heap delta受GC影响、不是峰值。复现/完整命令/失败修正及结果见P1-C1验收文档。
