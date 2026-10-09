# 舌下 E2 P0：执行记录与验收边界

2026-10-09。起始提交`8d94c67cfc346386ca05df417a00bda34641bdee`；研究/最终测试工具提交`991987c573881fca554b0e9df4d26c2aef426d82`。Build25/0.2.0、Schema9不变，生产源文件与起始提交相同。

## 实际执行

本机Python3.12.14、Java21；`JAVA_HOME=/workspace/jdk21`，`GRADLE_USER_HOME=/workspace/gradle-home`，Android SDK=/workspace/android-sdk。Gradle经环境代理运行，`JAVA_TOOL_OPTIONS`指定HTTP/HTTPS proxy host=proxy、port=8080；不包含认证材料。标准重现命令与保存输入版本见[工具README](../../tools/pk-audit/README.md)。

| 已完成命令/检查 | 实际结果 |
| --- | --- |
| `./gradlew -PjvmOnly :core:domain:test :pk-engine:test :importer:test --rerun-tasks --no-parallel --max-workers=2` | 15任务实际执行，12s；domain58、PK26、importer12，全部通过，无跳过 |
| `./gradlew :core:data:testDebugUnitTest :core:reminder:testDebugUnitTest :app:testFullDebugUnitTest lintFullDebug --no-parallel --max-workers=2` | 1m49s；data72、reminder14全部通过；app349，336通过/13既有可选跳过/0失败；Lint0错误/131既有警告 |
| 最终资源与测试命名调整后`:pk-engine:test --no-parallel --max-workers=2` | 3s，26项再次通过 |
| `python3 -m unittest discover -s tools/pk-audit -p 'test_*.py'` | 5/5通过，约0.4s |
| `python3 tools/pk-audit/sublingual_compare.py` | 完成9情形/13时点/2模型及Cmax/Tmax/AUC/谷/停药；独立Kotlin最大差4.76745754e-10pg/mL |
| `python3 tools/pk-audit/sublingual_compare.py --engine docs/pk-research/results/sublingual-p0-engine.json --calculator docs/pk-research/results/sublingual-p0-calculator.json --output /workspace/tooling/pk-p0/replay` | 保存Kotlin/Calculator合成输入离线重放成功；报告中所有5项输入SHA256与仓库文件逐项一致 |
| `git diff --exit-code 8d94c67 -- pk-engine/src/main app/src/main core/data/src/main core/data/schemas app/build.gradle.kts` | 无差异；生产公式、已发布参数、UI、历史、Schema和Build未修改 |

新增PK8项、Android/JVM Calculator6项、Python5项，详见测试源码。首次新Calculator测试编译失败为JUnit4断言参数顺序，改正测试后实际执行通过；没有修改生产代码来让证据测试变绿。

## 科学结果与未完成项

普通检查通过仅表示实现可重现、文献输入有来源与缺陷证据成立。**不表示当前模型通过外部科学验证**。Pines固定60min/Yaish固定90min及Burnier条件倍数比较有差异，方法/人群/基线/历史缺口不允许合并成普遍精度排名。Doll为拟合资料，不算独立命中；其绝对AUC未取得，旧拟合AUC为模型代理。

5篇尚未核对全文；没有完整独立8–24h浓度序列、分钟与吸收份额关系或可靠第三候选人口模型；未取得个体原始数据，不能做人群到个体的精度/区间覆盖率验证。0.24%无生理可辨识依据，2/4mg及重复给药比例属外推。详见[科学审查](sublingual-v2.md)。

速率病态、假基线、因果插值/摘要和全离群标识是已确认软件缺陷，**P0未修复**；[审计](sublingual-calibration-audit.md)给出影响与独立规格。尾部是否普遍低估、研究间异质性如何拆分仍是科研问题。P1尚未实施；[建议最小单元](sublingual-p1-design.md)是历史完整性门控。真实结果获知时间、基线确认和离群排除需产品决定。

未进行真实设备或真实用户数据验收。模拟器只验普通软件功能/迁移，不能验证生理模型准确性。无签名、APK交付、Release/PR或默认分支合并。最终CI已逐项回查：[37905173240](https://github.com/DevenirTwilight/HRT-Log/actions/runs/37905173240)，源码提交991987c573881fca554b0e9df4d26c2aef426d82，jvm/android/device-tests全部success。JVM完整任务实际执行（27s）；Android单测/lint/Full Debug/Release/两个instrumentation包构建全部通过（主步骤10m22s）；manifest检查及Schema/PLAN无差异检查通过。新增Python5项和PK审计8项再次执行通过，自动研究报告生成成功，**报告仍明确科学验收未确立**。

下载两份报告回查：build-results188文件、device-test-results21文件，均无APK。CI app349/336通过/13跳过/0失败，data72、reminder14、聚焦PK审计8全部通过；全量PK在JVM任务中执行，聚焦报告不冒充26项报告。Lint0错误/131警告。CI生成的研究结果JSON与本地保存JSON完全相同，包含所有输入哈希；外部比较6行是2模型×3研究摘要，不能称6个独立人体观测。

API35模拟器data14迁移/SQLCipher全部通过；app53/51通过/2既有常规跳过/0失败；独立伪装重启prepare/verify各1项通过。无实机验收。本轮末尾只更新README/研究说明/验收记录/HANDOFF等文档，代码、参数、证据输入及测试与此全绿源码提交相同；无需将仅文档提交的后续流水线启动当新增代码验证。
