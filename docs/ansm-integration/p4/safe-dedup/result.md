# P4 安全去重有限验收结论 — 2026-10-20

**PRODUCTION_BLOCKED / Draft / 入口OFF，不合并。** 真实生产候选f1c638ba622d28d90a6048c65055d7967cfb0465；基线证据6ee217f8，比较源码87e9df95；主线320171cdc882ec0b62830b60e2329a4174ffa31c。预先约定的两个候选原生运行已用完，全部保留，不启动第三次或继续毫秒微调。

## 实际改动与安全范围

生产只去掉MeasuredConsumptionStore私有同步只读审计中重复的第二次FK扫描。BackupValidation的首个扫描、独立备份/恢复验证入口、最终完整语义审计不变；没有第二次全量审计可删。证明在[decision.md](decision.md)，测试保留原删除点重放第二FK的test-only oracle、零预算全SQL路径、真实FK孤儿三入口拒绝/回滚、途中DML拒绝/回滚。RED原实现2次查询；GREEN只执行1次。完整data475 PASS/0SKIP、库存142 PASS、原生测试源码编译PASS、工具46 PASS；A/B反例、故障注入和原有业务断言均保留。

链头复用NOT_IMPLEMENTED：validate到append存在记录/context写入、回调、逆操作及协调的合法追加，未证明前缀保全和接受/错误决定完全等价，故仍独立验证next。不以最后oracle拦错、计数、TEMP journal或跨事务状态指纹替代证明。Room13/业务12不变；Schema、BackupValidation、DataTransfer、Integration、操作链、前端和PK Git对象与基线完全相同，见[production-isolation.json](production-isolation.json)。正式jar无测试观察器；无新权限/签名/发布变化。

## 冻结原生验收

两个候选均为API26 x86 SQLCipher，RAM520962048B、ART201326592B；14600记录、2000容器、32603行、19660924B JSON。没有修改5s/15s/120s、32MiB、100000行等任何门槛。官方ZIP SHA/独立下载/CRC逐一一致；第二次完整报告与pre-cleanup一致。

|指标（ms）|旧87，run39478425938|候选自动39486040353|候选预定dispatch39486045081|
|---|---:|---:|---:|
|完整审计|5381.742|6328.717 PASS|9066.895 PASS|
|共享预测|7999.644|5845.611 PASS|11446.250 PASS|
|加密备份|7106.396|6806.329 PASS|11660.860 PASS|
|加密恢复|24956.868|FAIL：恢复阶段LMK|42661.004 PASS|
|真实写入1，含票据|4979.277|NOT_RUN|7423.083 FAIL|
|真实写入2，含票据|4804.102|NOT_RUN|7170.355 FAIL|
|真实写入3，含票据|4727.139|NOT_RUN|7335.245 FAIL|

自动run job118846590122/artifact12292904917 SHA d41f18fdb2551981025e33facd5fb48b5dca0744ca7e37f46a4835e830710ae9；最后阶段full_encrypted_restore。内核lowmemorykiller明确杀死测试PID3848，终止时to free213472KiB，**不是进程峰值**。最后保存的Java堆178191976B只代表截至该报告的观测，不是恢复阶段最终峰值。恢复未完成，写入/额外分段均NOT_RUN，不把缺字段或空budget表解释为PASS。

Dispatch job118846611356/artifact12294676265 SHA 873d055d6a857b71d0b6c6e31ff4b2c9766414997434e7e6d2cdb61396be7fd2；DIAGNOSTIC_BUDGET_FAIL，三笔写入全超5s。Java堆观测峰198631952B=189.430MiB，仅余2.570MiB；非连续RSS/native峰，不能宣称内存问题解决。

### 额外第四笔：同次互斥归因，非原三笔分解

|互斥分段ms|旧87|候选dispatch|
|---|---:|---:|
|票据完整API|414.364|746.961|
|写内票据验证core下界|502.387|472.587|
|INSERT/UPDATE含trigger|18.635|27.003|
|COMMIT driver|5.652|3.995|
|完整audit core下界|4435.771|7686.647|
|其他driver|7.147|14.751|
|写内未归因|322.852|636.031|
|调用间隔/draft/UUID/finish|28.104|39.308|
|总计|5734.912|9627.281|

[comparison.json](comparison.json)保留整数纳秒，两次各自严格相加等于同次总值；观察器开销已包含，不另行相加/相减。候选仍audit1、链ticket1+write3，FK窗口仅1个387.738ms；post-commit独立完整oracle PASS。纯trigger、native COMMIT/fsync及native峰仍无法独立归因。

**真实提速没有证实。** 同配置、同夹具、同生成模板和预热顺序，但不同GitHub宿主，CPU调度/JIT/GC未受控。候选三写较旧一次绿色分别多2443.807/2366.253/2608.107ms；这只是观测差异，不证明去掉FK造成变慢。同样，旧第二次FK149.704ms绝不是本次实测收益，不能用总值相减宣称优化已提速。此次只证明重复工作消除及语义回归，没有证明稳定达标。

## CI与生命周期

源f1c638ba的PR六类必需CI均SUCCESS（Android run39486040221、science run39486040397）；push重复Android暂仍运行，单独记录，不把PENDING当PASS。API26/29/35真实SQLCipher库存合同各142 PASS/0FAIL/0SKIP，含3项新合同；独立运行和工件SHA见compatibility目录。构建工件127345036B/12293319416的官方SHA与下载一致、CRC通过；data475、reminder21、PeriodStability5全PASS；App1839总计1562 PASS+277 SKIPPED、0FAIL。不把skip当通过；额外原生两次FAIL独立于常规CI绿色。

新库冷首次、进程重启首次、恢复后未经预热首次、信任失效回退计时、真机/连续RSS均NOT_RUN。没有安装跨事务可信状态，三笔写入发生在恢复及独立全审计之后；不将它们改称冷恢复首次。P5/P6未实施。

## 停止微优化与下一步决策

本轮安全FK改进保留；不再添加字符串/SQL/缓存微调或重复跑分。当前不满足合并条件，**同时存在低内存恢复LMK和真实写入超预算**，不能归并为一个已解决的问题。优先承认恢复内存风险；不能声称方案C会自动解决它。

若继续P4，负责人需决定是否批准以下边界明确的结构性阶段，或另行调整产品/性能验收政策；本轮绝不修改冻结预算：

1. 方案C以既有[谓词与兼容设计](../validation-architecture/decision-2026-10-20.md)和[全写入矩阵](../validation-architecture/write-path-matrix.md)为准，把每项规则映射到数据库约束、单事务保全证明或仍需全局扫描的规则。部分CONSUME唯一索引只覆盖一个重复revision谓词。
2. 尚需证明request/header/JSON与来源一致、完整CONFIRM/分配集合、REVERSE/COUNT因果、跨包装历史、修订/回收站/旧库存及全库行/文本限制；无法直接约束的规则不能删除。避免复制第二套完整业务验证器。
3. 若需要新持久结构/约束，先审核Schema、全迁移链、业务备份版本、旧读者拒绝、恢复重建、连接覆盖、SAVEPOINT/强杀/提交失败原子性；不抢占下个Room号，不擅自改PNBAK1。
4. 冷打开、重启、恢复首次必须计算可信基线建立和失败恢复成本，不隐藏到后台/预算外；未知修改fail closed并保留完整oracle。
5. 恢复LMK需另有有界内存方案及同限制实证，不能通过增RAM/heap或取消完整恢复解决。完整性证明、故障/差分测试闭合后才有理由开展新架构原生验收；当前没有快速生产接线授权依据。

本轮结论：生产去重IMPLEMENTED且本地/部分原生正确性PASS；稳定性能FAIL，冷生命周期NOT_RUN，P4 PRODUCTION_BLOCKED。没有合并、发布或交付APK。

证据收尾为仅文档[skip ci]提交，避免同源码再次自动性能运行；CI属于f1c638ba，不宣称文档HEAD重新跑过六门，也不借此合并。push重复Android作业如仍PENDING则在receipt中保留，不能写成全部12实例已通过。
