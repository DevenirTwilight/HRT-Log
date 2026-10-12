# M1/M2完整历史加载优化（2026-10-12）

用户反馈：原浓度页面Legacy秒开，M1/M2需持续转圈。本次沿用实际交付分支 `feature/unified-concentration-20261011`；默认分支尚不含该页面。源提交470b194，前版9a06fc794e82a7ed079ee8011c0fe9ed57f4d46c。

## 原因与实现

ConcentrationPreview逐绘图点/峰值搜索点调用冻结studyScenario，每次遍历全部剂量，并对每剂重算1h归一化、阶乘对数和Poisson上尾。半年360剂会有数万次完整历史求值；构造时刻缓存只能避免重复时刻，无法消除每个新时刻的全历史成本。

新增非冻结 `PreparedResearchHistory`，不修改原核/候选/Price参数。常数准备一次，近期保留原Poisson级数；冻结实现当 `(ka-ke)*age>=100` 已直接返回上尾1，因此老剂量是三个指数项的线性和，可按排序剂量时间预累积。对每个消除/吸收率r，`S[i]=mg[i]+exp(-r*(t[i]-t[i-1]))*S[i-1]`；查询用二分定位满足同一饱和条件的完整前缀，再衰减至查询时刻。绝对epoch不参与指数，以免溢出。近等吸收/消除率回退逐剂稳定Bateman，不用相减不稳的前缀公式。

不删减旧剂量，不截断慢尾，不插值，不降低采样/极值搜索密度，不改背景只一次、精确点击、单位、原UI或Legacy。所有数据为合成输入，未读取用户历史。

## 验证与复现

`prepared-history-tests.xml`保存47候选的完整半年/不规则/重复时间/饱和边界逐点对照，以及近等速、极慢尾、空历史和360剂10443查询的同机新旧求值基准。121项PK回归实际全部通过。性能值只是一轮JVM测量，包含JIT差异，不能当物理Android开页耗时/FPS或临床准确性。

```
./gradlew -PjvmOnly :pk-engine:test --max-workers=2
python3 scripts/check_unified_concentration.py
python3 tools/pk-research/check_protocol.py
python3 scripts/benchmark_research_preview.py
./gradlew :app:testFullDebugUnitTest --tests '*ConcentrationPreviewTest' --tests '*UnifiedConcentrationUiTest' --tests '*PeriodStabilityTest' --max-workers=2
```

完整preview脚本从固定旧提交提取真实旧生产preview到临时测试源，保持原核/采样/缓存；对相同360剂分别计时新旧完整compute，并逐点比较旧图坐标的精确值、当前值和窗口峰。退出时删除临时测试源；不会将旧实现替换到生产源码。需要完整JDK和SDK37，JAVA_HOME/ANDROID_HOME依本机设置。

完整preview、full应用/时期/UI/Release/lint/CI和正式签名交付状态见results.json、HANDOFF顶部；进行中的结果不得冒充通过。
