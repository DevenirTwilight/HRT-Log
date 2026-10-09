# P1-C2验收记录

起始95199ab19867a72e93a3ba5a6fae2c7320075577，既有开发分支claude/new-session-1959qb，远端一致、初始干净。Build25/schema9。只用合成数据。生产最终SHA/CI回查另补。

## 修复前实际证据

2mg舌下/tier2、体重80kg，单次剂量后100h采样200pg/mL。旧LabFit actual_post_count=1却excluded={tail}；应用实际冻结剂量NOW−101h、化验NOW−1h，P1-B合格、时间前缀可用，actual_count=1/excluded={l1}。实际起始代码1项通过5s保存before，正确「参与不能同时排除」断言1项失败。使用旧生成器legacy_p1c2_baseline.kt可独立重跑；旧P0机器文件不覆盖。

```sh
# 在95199ab起始detached树/原树临时复制生成器为app/src/test/java/net/plainnotes/app/P1c2BeforeTest.kt
P1C2_BEFORE_OUTPUT=/workspace/tooling/pk-p1c2/before.json ./gradlew :app:testFullDebugUnitTest --tests '*P1c2BeforeTest' --rerun --no-parallel --max-workers=2
```

## 政策与单一来源

全warning保留所有候选、X为空；部分warning只一次移除并重拟合；无warning全部参与，无合法候选保持人口/先验/空个人摘要。warning是首轮残差提示、不是事实错误；used/excluded来自实际solve集合，不是UI猜测。candidate为时间可用的数值合法剂量后集合，U∩X为空、U∪X=C；确认基线和无效/未确认治疗前/无暴露/冲突ID另列。集合均只在内存，不增加持久化排除功能。

最新诊断严格使用更早采样前缀，剔除自身和同时间全部观测；(time,id)排序稳定。诊断自身残差警告与首轮screening不同，used/excluded来自传入的同一最终fit。回顾式可用后来合格点，因果式受sample≤min(query,NOW)，未来warning/参与集合不可泄漏。没有获知时间字段，不能叫严格当时已知回放。

原单点MAP logAmplitude=9.210617529599556，covAmplitude=0.06697646966989096，旧/新不变；警告仍在、真实排除由1变0，参与保持1。大幅度不是证明人体准确性；本轮不改变MAP/人口核。部分异常可用保留子集独立复算MAP、协方差及全部分位；纯SL幅度公式与固定种子oracle也独立验证。单/全/部分/正常/空完整ID/曲线见after/report JSON。

## 实际本地命令与结果

Java21、SDK、本地GRADLE_USER_HOME=/workspace/gradle-home。依赖代理-Dhttp.proxyHost=proxy/-Dhttps.proxyHost=proxy端口8080；Gradle串行--no-parallel --max-workers=2。

- `./gradlew -PjvmOnly :pk-engine:test :core:domain:test :importer:test --rerun-tasks`：58/58/12零失败/跳过，5s。
- `:app:testFullDebugUnitTest --tests '*OutlierDispositionTest' --tests '*OutlierDispositionUiTest' --rerun`：14零失败，20s；UI8方法×6字体/主题组合=48。
- 第一完整Android回归398项计算路径通过、UI8新增测量断言失败，13既有跳过；根因是默认图形后端/整数像素取整/节点裁切，以及原生LazyColumn未组合离屏项。改用原生图形、真实字形边界1物理px取整容差、无省略号/完整末尾、滚动后48dp检查和Lazy容器定位。断言保留且最终矩阵通过，不改生产模型来迎合测试。
- `python3 -m unittest discover -s tools/pk-audit -p 'test_*.py'`：13/13；`python3 tools/pk-audit/sublingual_p1c2_compare.py`：software=true、clinical=false。旧四阶段显式只读重放，输出临时位置；当前输出pk-p1c2，旧JSON不覆盖。

新增PK10/应用6/UI8/API35原生3：单/全/部分/无warning/无候选、子集重算、独立幅度/MC oracle、单位、非法/近零/1e300/ln4边界、同时间乱序/自我预测、未来状态不变、旧窗口/快照门控、1/2/4mg和30天6/12/24h、抽样1/2/40/200、取消。非SL黄金及冻结/备份/VisitPack/PDF测试由完整旧套件保留执行，不重写C1绘图代码。

## 兼容性与科学限制

不改pk-params.json（SHA256 b2768a6947d29d65f272b1d20e31fd59b9661acf8166b437f2a3b179f235b16b）、文献来源、算法2/Calculator2/envelope1、冻结1/2、Schema9/Build25、数据库/备份/事实/旧PDF；fit参数未持久化。无网络权限/签名/版本/APK交付/Release/PR变更。仅合法异常标签/集合修复，实际MAP输入政策保持；非法非有限值不再制造NaN或假计数。

P2人口模型和严格结果获知时间未实施；人体外部准确性未确立，ln4不是什么经验证的医学标准。新性能、完整本地与CI/API35最终结论另补；没有真实设备验收。

## 同主机性能与内存记录

沿用p1c1_performance.kt临时复制helper，旧95199ab detached树与当前生产工作树，同Java21/Gradle/主机、2预热5测量、每批4次完整Calculator查询。旧中位0.069379281s、新0.037286096s、观测变化-46.26%；小样本/GC限制，不作稳定速度或临床准确性推断。逐次耗时与heap_delta_bytes_not_peak在独立performance-before/after JSON；堆差不是峰值，不据此断言无泄漏。新增处置计算为每次已有fit中线性集合构造，不增加每网格拟合；诊断复用最终fit，取消传播保持。

命令：`P1C1_PERF_OUTPUT=<输出> P1C1_PERF_COMMIT=<源码标签> ./gradlew :app:testFullDebugUnitTest --tests '*P1c1PerformanceTest' --rerun --no-parallel --max-workers=2`。旧1/1通过31s、新1/1通过3s（含构建时间不可当计算比率）；helper运行后删除、detached树清理，未留下env依赖测试或额外长期分支。

## 最终本地完整验收

实际命令：`./gradlew :core:data:testDebugUnitTest --rerun :core:reminder:testDebugUnitTest --rerun :app:testFullDebugUnitTest --rerun lintFullDebug :app:assembleFullDebug :app:assembleFullRelease :core:data:assembleDebugAndroidTest :app:assembleFullDebugAndroidTest --no-parallel --max-workers=2`。7m11s成功，data72/reminder14全部通过；app398（385通过/13既有可选跳过/0失败），新增14全部通过。Lint XML0错误131既有警告，Full Debug/Release及两模块测试APK构建成功。release manifest身份/入口/no-INTERNET检查成功，git diff --check通过。没有APK交付或实际本地设备运行。

旧P0/A/B/C1只读重放实际命令均与README显式命令相同、输出`/workspace/tooling/pk-p1c2/replay-*`，P0算术误差4.77e-10、A/B/C1软件验收true、临床准确性均false。当前C2对照与Python13再通过；人口/合成拟合/先验/资格案例误差0、C1原显示误差1.4472334442e-10。全部旧机器文件无修改。

## CI进行中

生产SHA aa3edd0700fc5e2aababfcc722eba7103adee5dd，[CI37943163077](https://github.com/DevenirTwilight/HRT-Log/actions/runs/37943163077)。JVM成功，artifact11621784974下载回查XML：PK58/domain58/importer12零失败/跳过，21文件无APK。Android/device-tests尚在运行，最终日志/报告计数另补。没有真实设备验收，不把instrumentation编译当原生通过。

### API35实际验收完成

device-tests success，artifact11623147665下载25文件、无APK。HTML：data14/14（MigrationBaseline7/EncryptionIntegration7），app64（62通过/2常规跳过/0失败）；OutlierDispositionAndroidTest3/3包括实际全warning保留、部分子集oracle及历史未来状态非干涉。独立重启prepare/verify各`OK (1 test)`。日志Finished66含重启，主HTML64应分开报告；不是实机验收。Android主job仍在运行，最终综合结论另补。

## 最终CI综合验收：全部通过

生产源码 **aa3edd0700fc5e2aababfcc722eba7103adee5dd**，[CI37943163077](https://github.com/DevenirTwilight/HRT-Log/actions/runs/37943163077)三个job success。Android主步骤7m38s，完整app398（385通过/13跳过/0失败）、data72/reminder14；PK聚焦40/40，Python13/13；Lint XML0错误131既有警告、Full构建/no-INTERNET manifest/Schema检查通过。JVM PK58/domain58/importer12和API35 data14/14、app64（62通过/2常规跳过）、重启各1通过前述日志与报告均已核验。

下载build artifact11623187726（223文件），另jvm21/device25，全部无APK。真实Kotlin/app after与本地归档完全一致；当前人口/合成正常拟合/先验/历史资格误差0，C1原显示差1.447e-10。旧四阶段只读对照software true、clinical false；新C2亦software true/clinical false，不能将徽章当人体科学验收。CI独立机器摘要sublingual-p1c2-ci.json保存任务/产物ID和计数。

最后仅文档/机器验收追溯记录提交，生产/Kotlin测试仍与上述全绿SHA一致；文档SHA触发的新CI若尚在排队不能宣称完成。远端/最终SHA在最终报告给出，无发布或签名操作。

P0证据研究及P1-A/B/C1/C2工程修复已完成，当前已知全异常集合矛盾已修。尚未确立人体外部准确性、研究异质性/结构误差的完整区间及严格结果获知时间；真实设备未验收。下一建议为P2先完成独立外部验证和受限候选规格，避免凭复杂度或匹配另一应用替换模型；结果获知字段另案产品与历史兼容决策，不在本轮实施。
