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
