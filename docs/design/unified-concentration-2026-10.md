# 统一血药浓度：架构审计与验收（2026-10-11）

状态：正在实施，尚无本轮测试通过声明。默认分支初始提交见PR审计JSON；五PR都open/draft、未合并。#3基于#2，#18基于#17。以默认f53726f为基，逐文件复用而非整体merge；保留新归档和正式资源裁剪/压缩四ABI配置。

## 实际架构及依赖

AppShell.CONCENTRATION → ConcentrationScreen → ConcentrationCalculator / ConcChart / ChartViewport；NotesViewModel持有conc和extra，Room仓库提供冻结记录/规则快照及labs。这些现有计算核及数据库不改。默认没有ExperimentalPkScreen、UnifiedConcentrationScreen或研究Kotlin核。

PR1 ResearchSublingualV01为15套P2-X固定M2参数的归一核。PR2只读ResearchHistoricalSlAdapter复用ConcentrationCalculator历史上下文门槛；ExperimentalPkScreen只是宿主。PR3 UnifiedConcentrationScreen → ExperimentalConcentrationComparison → ConcentrationModelComparison及StudyAnchors，ModelComparisonChart复用ChartViewport。其Legacy默认、缺M1、相对对比/Price独立幅度与实验入口不满足本次产品决定。

PR17 P2-AZ有32套手设网格M1、216套新网格M2，分别跨已见三研究留一选择；这不是P2-X冻结15或P2-V拟合。PR18按真实三研究计数并区分研究/个人预测用途，保留M2对M1的Rosano反例。此次不改变两PR研究结果/政策或宣称18/54项独立外测。

## 最终集成方案

一个CONCENTRATION导航目的地；复用PR3共享chart、历史适配、固定研究核及测试。M2优先，M1/P2-AZ32手动候选，Legacy原历史显示保留在同页视图内。研究视图共同显式B/A（示例人为25/100pg/mL），相对以各模型单1mg/1h归一，Price模式仅特定群体1mg单剂+共同1h图读锚点，不使用用户记录/化验。研究条件模式可显示真实化验点但不拟合/反求B/A，图例明确观察与假设。所有曲线同unit/时间轴，可叠加/单显，精确函数点击和0–4/8/24h指标。候选范围非人体置信区间，默认单条完整固定参数，不绘制逐点中位为中心线。

删除旧ExperimentalPkScreen宿主、EXPERIMENTAL_PK入口及预览Activity；复用其有价值的计算/数据测试。不删除科研数学/报告或Legacy内核。不修改其他保护文件；AppShell最小导航变更单独精确偏离及变异测试，不扩大普通豁免规则。

## 验证状态

待执行：完整数值/版本/参考程序、UI/状态恢复/数据只读、旧时期与加密迁移回归、Debug/Release、lint、CI设备、APK前后体积。本地无KVM，设备验证通过独立GitHub CI核对实际报告；不能用旧PR绿灯冒充本轮结果。所有未执行项目将如实列出。
