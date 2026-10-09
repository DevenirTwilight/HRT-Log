# P1-C1 验收记录

起始d45a67a4f5309f1e951b2dfe69e9295fa42234cb，开发分支claude/new-session-1959qb。全为合成数据。复现/设计检查点1f1ad77；最终生产提交及CI核验将在下面补充。

## 真实旧缺陷与修复

NOW=2026-10-25T01:10Z，t0=00:40Z，2mg舌下实际用药23:54Z，未来化验00:45Z，已在NOW之前且P1-B合格。起始正式Calculator独立执行1项通过，正确非干涉断言在旧代码失败。旧机器输入/输出及legacy_p1c1_baseline.kt保留，不是复制当前模型生成假旧证据。

| 输入 | 旧中心pg/mL | 新中心pg/mL | 新历史个人摘要数 | 采样后数 |
|---|---:|---:|---:|---:|
|无化验|277.768202062|277.768202062|0|0|
|未来400|286.365404445|277.768202062|0|1|
|未来800|312.803642308|277.768202062|0|1|
|同时间两条|285.421993800|277.768202062|0|2|

无化验旧四分位=98.433871190/188.397977980/420.870374890/690.129101709；未来400旧四分位=111.098677251/199.960243700/422.833783865/676.396082290。新四情景四分位均等于无化验。旧未来400摘要1/最近诊断400，新历史摘要0/诊断null；采样后曲线仍实际改变，未禁用校准。机器报告含全部输入、向量、资格/原始点数及后采样验证。

方案A直接单点评估会改变原人口网格显示，故采用B局部同前缀插值：先按sample≤min(t,NOW)选择合格化验，拟合一次，用同模型计算原网格两个端点及固定种子MC分位，再插值。恰在网格单点，范围外直接单点。不是对两个不同拟合前缀插值。纯SL独立幅度/随机抽样oracle对照；混合途径分位插值仍为网格近似，非精确连续分布分位。

中心/四区间/参数/协方差/计数/最近诊断共用查询前缀；诊断排除自身及同采样时刻，但保留前1ms样本。原始未来观测与原资格原因保留，时间尚不可用单独提示。资源资格预算按(time,id)消费原200000上限，避免未来样本取消早期资格；不放宽历史证据或残余阈值。

ChartViewport/ConcChart及新生成普通PDF动态图只在同拟合段连接中心和区间；视窗/NOW切点不跨段插值，点击异步可取消查询求值。没有隐藏全部未来预测。PDF字段/格式/旧文件和Visit Pack冻结资料不变。

## 实际命令与结果

环境Java21、Android SDK，GRADLE_USER_HOME=/workspace/gradle-home；代理仅用于依赖下载。Gradle均--no-parallel --max-workers=2，完整测试用--rerun-tasks。

- `./gradlew :pk-engine:test :core:domain:test :importer:test --rerun-tasks`：48/58/12，零失败，最终7s。
- `./gradlew :core:data:testDebugUnitTest :core:reminder:testDebugUnitTest :app:testFullDebugUnitTest lintFullDebug :app:assembleFullDebug :app:assembleFullRelease :core:data:assembleDebugAndroidTest :app:assembleFullDebugAndroidTest`：data72/reminder14/app384（371通过13既有跳过），完整本地通过；最终全app/构建重复5m34s。
- 最后新增PDF分段后：`:app:testFullDebugUnitTest --tests '*CausalTimeUiTest' --tests '*CausalTimeBoundaryTest' --tests '*ExportTest' --tests '*LabEstimateTest' --rerun lintFullDebug :app:assembleFullDebug :app:assembleFullRelease :app:assembleFullDebugAndroidTest`：20项19通过1跳过，4m28s成功；Lint0错误131既有警告。四语320dp/font2真实点击及PDF像素检测4/4。
- `python -m unittest discover -s tools/pk-audit -p 'test_*.py'`：11/11；`python tools/pk-audit/sublingual_p1c1_compare.py`：software_validation_passed=true，clinical_accuracy_established=false。旧P0/P1-A/P1-B工具使用显式旧输入及临时输出只读重放通过，不覆盖历史JSON。
- 初次UI四项超时是测试时钟未推进；显式推进后四项均通过。旧代码正确断言失败为预期红灯，最终无遗留失败。

新PK8/应用13/API35原生4覆盖±1ms、未来400→800/同时间增删、后采样有效、后退查询、forecast NOW上限、回顾式、混合途径、空/单网格/范围外、取消/长期120事件、未知基线、冻结/开关/单位、时间区/DST、资源预算及绘图不跨断点。完整旧回归保留，P1-C2错误特征化不删除。

## 数值与性能边界

未校准显示/分位最大误差1.4472334442e-10pg/mL；直接人口核278.8647796不变。人口/P1-A拟合/先验/P1-B六资格案例最大误差全部0。参数SHA256 b2768a6947d29d65f272b1d20e31fd59b9661acf8166b437f2a3b179f235b16b，算法2/Calculator2/envelope1及旧1/2读取、Schema9/Build25不变，无持久化个人参数迁移。

同一主机旧/新独立JVM，四次Calculator一批、预热2/测量5：旧中位0.047573086s，新0.063535758s，观察约+33.5%；GC和样本数限制，不是稳定性能比率，堆差非峰值。query只追加1/2点200抽样、支持取消；段模拟避免重复整条网格。完整命令/输入/数组在性能JSON及p1c1_performance.kt；不宣称性能提升。

采样时间重建不等于结果当时已知：无resultKnownAt字段。区间不覆盖全部结构/研究异质性，不是临床可信区间。P1-C2全离群一致性未修、P2人口模型未实施；下一最小单元明确全离群回退/排除语义，使实际拟合集合、excluded计数、诊断一致并保留原观测。真实设备未验收。

## CI

生产推送后核对，当前本地无模拟器；instrumentation编译不当设备通过。无APK上传、Release、PR、正式签名变更。

## 独立旧复现与性能重跑命令

以下helper只在临时detached树运行，输出路径由环境指定，运行完删除临时测试；不提交到生产test目录。旧复现命令实际在起始树运行（旧树JAVA_HOME/SDK/Gradle同上）：

```sh
git worktree add --detach /workspace/tooling/pk-p1c1/repro d45a67a4f5309f1e951b2dfe69e9295fa42234cb
cp tools/pk-audit/legacy_p1c1_baseline.kt /workspace/tooling/pk-p1c1/repro/app/src/test/java/net/plainnotes/app/P1c1BeforeTest.kt
cd /workspace/tooling/pk-p1c1/repro
P1C1_BEFORE_OUTPUT=/workspace/tooling/pk-p1c1/before-reproduced.json ./gradlew :app:testFullDebugUnitTest --tests '*P1c1BeforeTest' --rerun --no-parallel --max-workers=2
```

性能helper同法复制`p1c1_performance.kt`为`P1c1PerformanceTest.kt`，旧/当前树分别运行：

```sh
P1C1_PERF_OUTPUT=/workspace/tooling/pk-p1c1/performance.json P1C1_PERF_COMMIT=<对应源码标签> ./gradlew :app:testFullDebugUnitTest --tests '*P1c1PerformanceTest' --rerun --no-parallel --max-workers=2
```

实际旧1/1 25s、新1/1 16s，生产未留下临时测试，detached树已清理。旧/新输入相同，性能JSON逐次耗时及非峰值堆差只作有限样本观测。正式MC固定种子20261006；新回归覆盖1/2/40/200抽样和多个历史时刻，固定种子独立oracle复算，并未引入可配置随机种子或宣称临床不确定性验证。

P1-B允许未来400：读取完整至NOW，实际剂量有合法冻结配置，化验发生在该剂量后、读取截止前，纯SL早于窗口残余可证明/不存在未知基线；它相对查询未来而非相对读取未来。查询时间门控与历史资格是独立条件，不更改资格来让案例通过。

### 已回查的CI JVM/API35

生产源码47f53eec993a61b066713b9ca4f5e06e95e61eeb，[CI37933171917](https://github.com/DevenirTwilight/HRT-Log/actions/runs/37933171917)。JVM成功，artifact11616973214/20文件，XML独立计数PK48/domain58/importer12零失败/跳过。API35 device-tests成功，artifact11616469137/24文件，数据库14（MigrationBaseline7、EncryptionIntegration7）全部通过；app61/59通过2常规跳过零失败，CausalTimeAndroidTest4/4。独立重启prepare/verify各`OK (1 test)`。日志Finished63包含重启测试，HTML正式主套件61，不能混用。两份报告均无APK；真实设备仍未验收。Android主job尚在运行，最终状态另补。

### 最终CI与报告回查：全部通过

CI37933171917三个任务success；生产代码与测试SHA **47f53eec993a61b066713b9ca4f5e06e95e61eeb**。Android主步骤8m28s成功，Python11/11，PK聚焦30/30；旧三阶段只读对照及新因果非干涉software=true，clinical=false。Full Debug/Release和两份测试APK构建、no-INTERNET manifest/Schema差异检查成功，未上传APK。

下载build artifact11617427993（216文件），HTML主报告app384/371通过13既有跳过0失败，data72/72、reminder14/14；lint XML0错误131警告。新四語UI4/4、因果应用13/13、PK8/8及API35新增4/4可独立查阅。JVM/XML artifact20、device artifact24前述计数再次确认，无APK。

实际engine/calculator JSON与本地入库完全相同；资格六案例相同（资源计时不比较）；causal-after仅seconds不同，五值/计数/资格/原始点/后采样数组一致。保存独立CI摘要`sublingual-p1c1-ci.json`，不覆盖旧机器证据。最后仅文档记录提交，不改变全绿生产代码/Kotlin测试；该文档提交触发的新CI若排队，不宣称其已经通过。

未解决P1-C2全离群一致性、结果获知时间及人体外部准确性，详见最新Backlog；无真实设备验收。P1-C1可交付的数学/软件范围完成，不声称所有个人校准问题已修复。
