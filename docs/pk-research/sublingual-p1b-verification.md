# P1-B：历史资格门控实施与验收

2026-10-09，起始 `b9a5393a7435accd3afe02407b38709a0e40f179`，既有开发分支 `claude/new-session-1959qb`；初始工作区干净，与远端一致。§47授权。生产实施、本地完整验收及API35 CI通过，实际报告/日志均已回查；模拟器不等于真机。

## 实际生产链路与选择规则

DAO读取全部未删除保存记录，Repository不截断。`NotesViewModel.loadConcentration`在事务读取records/labs/rules后交Default计算，明确传入全量保存读取声明/读取终点和协程取消检查；Calculator原有180天截断发生在构建模拟事件时。本轮先保留所有实际给药的冻结证据，再由纯`CalibrationEligibility`逐条判断。`LabFit.bands/fit/lastDiagnostics`和UI摘要共享同一合格子集；图层仍保留全部合法原始化验。

[设计](sublingual-p1b-design.md)规定COMPLETE仅指已保存且该模型所需的输入，不证明未记录的现实暴露。未知时间/剂量/配置、相关混合暴露缺项、采样超出窗口或读取终点、未声明读取范围、资源限制均不拟合，逐条显示原因。明确非E2的缺项不阻断E2。有效实际用药按taken瞬时，不使用计划、跳过、删除或当前药物配置补过去。

已知采样前E2事实→NOT_PRE_TREATMENT；没有这种证据→UNKNOWN，绝不自动当治疗前。LabFit基线只能来自显式`confirmedBaselineLabIds`；生产没有真实确认字段/入口，始终传空。未来确认及持久化另案，不加Schema。窗口前未知或非SL暴露保守阻断；已知固定SL核仅在累计解析遗漏上界/采样时刻已含SL贡献≤1e-6时放行，对数域避免下溢伪装为零。这是数学工程预算，不是人体残余保证。

## 实际合成反例与正例

旧结果取只读P0实测JSON，并在起始b9a5393的detached工作树实际独立重跑两个反例；新结果由当前正式Calculator/Kotlin测试输出，不从预期值伪造。精确输入、资格状态及记录证据见 [JSON](results/sublingual-p1b-eligibility.json)，比较见 [报告](results/sublingual-p1b-report.md)。

| 情景 | 修复前 | 修复后 | 原观测/实际拟合 |
| --- | --- | --- | --- |
| 201天前用药、200天前500化验、46分钟前2mg | 错误baseline500，当前777.767987 | 当前277.767987，baseline null；EXCLUDED/窗口外+遗漏暴露，NOT_PRE_TREATMENT | 1/0 |
| 12h前缺快照、6h前220化验、46分钟前2mg | 错误baseline220，起始提交实测当前497.767987 | 当前277.767987，baseline null；NEEDS_REVIEW/缺配置+治疗前未知 | 1/0 |
| 完整单次SL、合成400化验 | 人口277.767987 | 校准368.318370，速率1、无基线，ELIGIBLE | 1/1 |
| 完整真实口服EV+SL、合成400化验 | 人口298.847391 | 校准375.417966，非SL保留调速率，ELIGIBLE | 1/1 |
| 一合格一后续未知暴露 | 人口279.037294 | 只用合格点→496.310975，摘要/diagnostics/bands一致 | 2/1 |
| 已知窗口前SL、残余上界通过 | 人口277.767987 | 校准368.318370；不将180天直接当零 | 1/1 |

四语逐条采样值/状态/原因可在320dp、字体2的详情中滚动查看；无合格点时解释人口曲线，开关、模式与化验管理仍可用。既有模式控件移出非空摘要条件，防止门控使其消失；不改变样式或设置保存。

## 本地实际命令与结果

环境Java21、SDK37，API35用于Robolectric；本地无可用模拟器，不是真机验收。Gradle环境：`JAVA_HOME=/workspace/jdk21 GRADLE_USER_HOME=/workspace/gradle-home`；会话网络另设置http/https代理，非项目代码。

```sh
./gradlew -PjvmOnly :core:domain:test :pk-engine:test :importer:test --rerun-tasks --no-parallel --max-workers=2
./gradlew :core:data:testDebugUnitTest --rerun :core:reminder:testDebugUnitTest --rerun :app:testFullDebugUnitTest --rerun lintFullDebug :app:assembleFullDebug :app:assembleFullRelease :core:data:assembleDebugAndroidTest :app:assembleFullDebugAndroidTest --no-parallel --max-workers=2
./gradlew :app:testFullDebugUnitTest --tests '*CalibrationEligibilityTest' --rerun :app:assembleFullDebugAndroidTest --no-parallel --max-workers=2
python3 -m unittest discover -s tools/pk-audit -p 'test_*.py'
python3 tools/pk-audit/sublingual_compare.py --engine docs/pk-research/results/sublingual-p0-engine.json --calculator docs/pk-research/results/sublingual-p0-calculator.json --output /tmp/hrt-p0-replay
python3 tools/pk-audit/sublingual_p1a_compare.py --engine docs/pk-research/results/sublingual-p1a-engine.json --calculator docs/pk-research/results/sublingual-p1a-calculator.json --stability docs/pk-research/results/sublingual-p1a-stability.json --output /tmp/hrt-p1a-replay
python3 tools/pk-audit/sublingual_p1b_compare.py
python3 scripts/check_release_manifest.py
git diff --check
```

| 本地任务 | 总数/通过/跳过/失败 |
| --- | --- |
| PK完整 | 40/40/0/0 |
| domain | 58/58/0/0 |
| importer | 12/12/0/0 |
| data | 72/72/0/0 |
| reminder | 14/14/0/0 |
| app完整（含四语5项UI、新13项资格） | 367/354/13/0 |
| 最后资格聚焦重跑 | 13/13/0/0 |
| Python | 9/9/0/0 |

JVM真实重跑；Android主命令5m32s成功，Full Debug/Release及instrumentation编译通过。Lint0错误/132警告（与P1-A产物对比：原131条保留，新增资格数量字符串的PluralsCandidate建议，未宣称零警告）；manifest fullRelease检查通过。早期新代码跨模块nullable smartcast及测试nullable interpolate导致编译失败，分别改局部val/夹具断言，最终无失败。最后聚焦会覆盖本地app XML，完整367计数来自此前完整执行，CI还将重新全量执行并保存产物。

P0只读重放算术误差4.77e-10；P1-A重放数值验收true、临床false。新PK人口9组×13点与P1-A误差0，既有合成拟合及先验区间逐字段相同。10k证据×1000化验资格索引本地约0.044s，仅本机指标；3000实际记录、取消、残余预算另测。新残余工作上限200000、相关事实50000；超过待审查。DAO仍全量读取，不承诺任意数据库规模内存有界。

## 历史与回滚边界

`pk-params.json` SHA256 `b2768a6947d29d65f272b1d20e31fd59b9661acf8166b437f2a3b179f235b16b`，Build25/0.2.0/schema9、LabFit算法2、calculator_version2、envelope1不变，旧1/新2读取兼容。纯SL完整核速率固定、仅幅度；非SL原行为黄金回归仍通过。无数据库/备份格式变化，LabEstimate.capture/validator、不可变历史估算、VisitPack/PDF/用户事实均无修改。全量data/app包含冻结、备份、PDF相关测试，设备回查另列。

P0/P1-A机器结果不覆盖，仅新`sublingual-p1b-*`；关闭校准保持人口曲线与先验区间。资格/拟合参数仅内存，无旧参数持久化复用。回滚本轮生产提交不需迁移；必须保留P1-A算法2及旧1/新2 validator兼容，且需明确回滚会恢复本轮假基线缺陷。

## 仍未完成

P1-C：历史因果插值跨未来端点、摘要未按历史知识时点筛选；所有离群时排除标记与实际重拟合不一致。未来wall-now采样会被读取终点资格拒绝，但历史时刻仍可泄漏，测试换为历史端点继续复现，不宣称因果修复。下一最小单元：对过去求值时刻统一因果端点与摘要合格点，独立验收；之后单独处理全离群一致性。P2新人口模型/人体外部准确性仍研究。用户真实治疗前确认、非SL历史残余边界及大数据库有界读取未实施。工程绿灯不是临床验证。

## 起始提交独立复现

`tools/pk-audit/legacy_p1b_baseline.kt`是本项目独立合成生成器，在b9a5393的临时detached工作树复制为app测试执行，未建长期分支、未修改旧源码。实际命令：

```sh
git worktree add --detach /tmp/hrt-p1b-before b9a5393a7435accd3afe02407b38709a0e40f179
cp tools/pk-audit/legacy_p1b_baseline.kt /tmp/hrt-p1b-before/app/src/test/java/net/plainnotes/app/P1bBeforeTest.kt
# 配置同一SDK/Java21，进入该工作树
P1B_BEFORE_OUTPUT=/tmp/p1b-before.json ./gradlew :app:testFullDebugUnitTest --tests '*P1bBeforeTest' --no-parallel --max-workers=2
```

实际第二次6s，1/1测试通过；第一次生成器错误使用未依赖的kotlinx JSON，改为既有org.json，只修临时夹具。机器输出独立保存`sublingual-p1b-before.json`（500→777.767987、220→497.767987），重放后移除临时测试及工作树。旧P0/P1-A机器文件未被改写。

## CI设备产物实际回查

源码`1ad95600416a046f4d225c2432d32aa8e71b0843`，[Actions 37922137125](https://github.com/DevenirTwilight/HRT-Log/actions/runs/37922137125)。device-tests job113792344820已success。下载artifact11612890589共23文件、无APK；不是仅看徽章。API35 data HTML14/14通过（迁移7、SQLCipher7）；app HTML57总数、55通过、2跳过、0失败，新`CalibrationEligibilityAndroidTest`4/4。两个常规跳过为ProcessRestart的独立执行预留；实际日志另有prepare/verify各`OK (1 test)`。进度日志Finished59含该两项的计数差异，验收采用实际HTML57及独立重启日志，不混淆。模拟器不是实机验收。

JVM日志确认domain/PK/importer的test任务实际执行而非UP-TO-DATE；37s成功，完整数量40/58/12另见本地XML（该job未上传独立XML，不能伪称下载了它）。Android完整任务10m29s成功；研究对照/产物上传及所有job最终success，详见下。

## 最终完整CI回查

同一Actions37922137125的jvm/android/device-tests全success；正式代码与测试源码为`1ad95600416a046f4d225c2432d32aa8e71b0843`。末尾只更新研究生成器、独立before机器证据和文档，Python9再执行全部通过；生产和Kotlin测试无后续变化。最后提交触发的CI须按实时状态报告，不能把旧run冒充末尾run。

下载build-results artifact11612074303共205文件，无APK；app HTML367总数/354通过/13原有跳过/0失败，data72、reminder14全部通过。PK聚焦HTML22/22（P0八项、P1-A十项、显式基线四项）；全PK40由jvm实际执行，XML计数本地40。Lint0错误/132警告，Full Debug/Release及两测试APK编译成功，manifest与Schema/PLAN无差异步骤success。四语UI5及资格13全部在完整app中执行。

实际日志Python9/9、PK聚焦6s成功；P0重放最大误差4.767457539855968e-10，科学验收未确立；P1-A数值true/临床false；P1-B工程资格true/临床false。CI engine/calculator JSON与入库逐字段相同，eligibility仅排除runner计时后完全相同。CI同时证明两反例不拟合、三个正例有效及原始点保留。没有APK/Release/PR发布、签名更换或真实健康数据上传。
