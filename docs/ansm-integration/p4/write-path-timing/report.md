# P4 真实写入分解与重复审计结论

本轮完成了有效的同次调用归因，**没有实施生产提速**。测试源码 `87e9df955f8132b58cd111c5188c295a0d4b0726`，接手基线40408b77（承接27135013/a697已有成果），默认分支320171cd。P4保持Draft / 正式入口OFF / 不自动合并。

## 已核验原生证据

[run39478425938](https://github.com/DevenirTwilight/HRT-Log/actions/runs/39478425938)，job118820363674，artifact12290661559，ZIP121216B；官方及独立下载SHA256 `b26ca0e48292a9c817b64cec11adde7828b619738d8f91209f3dcdcc40f03b25`、CRC通过。原始JSON、确定性压缩ART及机器分解在 `native-87e9df95-light-observer/`。预清理完整报告与收集报告逐字段一致；ART原始SHA匹配、解析器重放结果相同，但ART仍是独立只读探针的近似采样，不能用于拆分这笔写入。

真实API26 SQLCipher；AVD512MiB/ART192MiB，实际可用RAM520962048B、Runtime.maxMemory201326592B；14600记录、2000容器、32603行、19660924B原始JSON，冻结模板SHA93131671…不变。测试/收集退出码均0，预算列表为空。原模板的api26_low_memory_acceptance与physical_device_acceptance字段仍为NOT_RUN，不改写成正式准入签认；此处PASS仅指本次实际低内存SQLCipher执行的冻结预算。

|原冻结预算项目|本轮ms|门槛ms|结果|
|---|---:|---:|---|
|完整审计|5381.742|15000|PASS本轮|
|共享预测|7999.644|15000|PASS本轮|
|加密备份|7106.396|120000|PASS本轮|
|加密恢复|24956.868|120000|PASS本轮|
|恢复后三写，均含票据|4979.277 / 4804.102 / 4727.139|每次5000|PASS本轮|

Java堆采样观测峰199241928B，小于192MiB；不是native RSS或连续精确峰，距离上限约2MiB。第一笔距5秒仅20.723ms。以往27135013的8.670/9.835/9.459s、a697的7.671/7.544/7.696s及旧OOM/LMK全部保留，**不能因本轮绿色宣布稳定通过或生产代码提速**。生产代码未变，没有同机受控提速对照；不同运行差异的原因未证实。本轮不再重复跑分求绿。

## 同一笔真实提交的互斥分解

这是原冻结三写和只读诊断之后的**额外第四笔**真实Repository写入；完整COMMIT后独立全审计PASS，2000容器投影、目标5quanta断言成立。观察器在原三写期间关闭，没有替代原验收计时。第四笔包含计时自身的直接及间接扰动，不能把其各阶段强行赋给前三笔。它含票据总计5734.912240ms，写入API自身5292.444980ms。referenceScope读取与原冻结脚本一样在计时前，UI点击至返回和冷路径不是本轮测量。

|同次互斥项目|ms|精确含义|
|---|---:|---|
|票据API完整窗口|414.363680|含自身事务/读取/序列化/哈希/调度及观察器；其提交0.126910ms已经包含，不能再加|
|写事务validate core下界|502.387070|真实validate入口至最后驱动返回；不包括之后的方法尾部，非全部身份处理|
|6条显式INSERT执行，含同步触发器|18.562620|dose_record3.697300、reference_identity1.826300、measured_operation1.947620、supply_transaction1.118730、measured_allocation5.232240、record_reference_capture4.740430|
|1条显式UPDATE执行，含触发器|0.072400|容器状态更新；触发器内部UPDATE已包含在所属INSERT中，不重复计数|
|DELETE|0|此笔没有显式DELETE|
|写事务最外层COMMIT驱动|5.652330|真实endTransaction调用；不是单独fsync或纯引擎COMMIT|
|最终完整audit core下界|4435.771450|入口至ReferenceAuditReads.finish最后schema_version游标close；包含其内部读取/校验/观察器|
|cores外其他驱动|7.147160|查询open/close、prepare、BEGIN、嵌套事务结束等，未逐getter计时|
|写API内未归因部分|322.851950|core尾部、游标读取、receipt/序列化/转换、调度和未独立分离的观察器/GC等；不能假定全是Java|
|draft/UUID、观察器finish及API间隔|28.103580|两个API窗口之外、同一诊断总窗口之内|
|合计|5734.912240|所有整数纳秒精确相加一致；不是不同运行探针拼接|

显式写执行与写提交合计24.287350ms，约占该总窗口0.424%；票据API、validate下界与全审计下界合计约93.33%。这不等于其余全部是数据库之外的工作：游标延迟填充/读取与纯Java尚有未分离部分。

**不可再次加总的嵌套信息：**写事务观察到4次begin、3次nested_finish、1次真实外层commit；audit1次、finish marker1次、validate1次。全操作链SELECT：票据1次，写内3次。两个FK完整游标窗口150.785670/149.703910ms均在audit内；观察器自身可测下界票据14.294860ms、写入41.647110ms也已在总时长内，不能扣掉后声称自然耗时。CONTEXT_CAPTURED到STOCK_CAPTURED为278.409590ms，是整个消费处理的嵌套窗口，含写入、读取与构造，不能视为纯SQL或再次加入合计。

**仍无法独立归因：**纯触发器exclusive、纯COMMIT/fsync、所有游标迭代、部分身份/receipt/方法尾部、GC/调度和观察器间接扰动。SQLCipher4.10现有SupportSQLite接口没有提供本次可用的纯触发器/纯提交profile回调；本轮不改触发器、不引入JNI或第二套数据库来猜测这些数值。原只读ART仅证实游标填充、JSON预算/解析、UTF8等成本存在，不是写入墙钟比例。

## 完整调用链和审计次数

`NotesRepository.measuredWriteTicket → MeasuredUseIntegration.ticket/state`：独立票据事务，读状态/完整操作链并生成摘要。

`NotesRepository.recordMeasuredUse → MeasuredUseIntegration.create → validate/state → DAO记录/context → MeasuredConsumptionStore.consumeNewRecord → 冻结来源/操作/分配/流水/receipt → auditStagedDataset → 外层COMMIT`。正常新记录没有旧ledger，不走Store851的条件分支。

|成功的非幂等路径|完整数据集audit次数|能否直接视为重复|
|---|---:|---|
|新建实际记录、完成未消费计划、count、delete、package VOID/RESTORE、createMeasuredPackage|各1|不能；正常新增的1次已由本轮原生确认|
|revise / restore|1或2|有旧ledger时，consumeNewRecord检查已冲销的中间态，之后另检最终态|
|reconcileRevise|最多3|原前态、已冲销/实盘后的中间态、新消费最终态不同|
|reconcileDelete|2|协调前态与删除最终态不同|
|export/restore/迁移/共享预测|独立边界/可能不同数据库|不能算作同一新增写入的重复|

所有实际调用位置仍在Integration各操作末尾、Store.reconcileOriginal811/consumeNewRecord851、Repository.createMeasuredPackage128/export784/forecast978，以及DataTransfer.validateDestination173（恢复验证）。正常新增最后的Integration85是唯一完整审计。普通新增含票据共4次操作链校验（ticket、validate、next、最终audit）；**操作链校验不是完整数据集审计**。

真正的同快照重复包括：audit内部在BackupValidation.validateMeasuredCandidateState136先查FK，Store184又查一次；scheduled ticket/complete的同事务重复state检查（与当前普通新增验收不是同一路径）。

## 约束、语义与最小可执行消重方案

数据库已经保证主外键、已有唯一索引、部分行值/不可变流水和supply_cache维护。操作仍须检查scope/owner、票据新鲜性、幂等、revision、实际包装确认、单位与冲销前置条件。完整历史还包括列类型/总行数/文本预算、references/历史/化验/方案/trash、全hash链、零效果operation、完整CONFIRM/allocation集合和跨容器因果；外键与容器余额不能替代这些语义。

**正常新增可删除的重复完整审计为0次。**不存在删掉“第二次全审计”可省数秒的方案。没有实施生产前后对照，实测提速NOT_IMPLEMENTED，不能把未测收益写成0ms或4.436s；后者是仍有安全职责的真实审计窗口。

最小候选依次为：

1. **仅消除同一read-only审计内第二次FK查询。**保留BackupValidation的第一次FK和完整oracle；证明同连接事务快照、期间无DML/让出/回调，ReferenceAuditReads的写入/schema fence保持；以坏外键、坏类型及故障回滚与原oracle差分。第二个149.704ms窗口是同次可移除工作的参考，不是已测提速，去掉它也不等于少一次全审计。本轮不为一次归因改生产，不追加优化跑分。
2. **复用不可变操作链头作为append构造输入，仍执行最终独立全链/全历史审计。**在validate已经完成的词法事务内将头/sequence显式传给append，而不是再扫描同一前缀；不能把头当作合法性证书。先验证回调/Raw SQL/并发、UUID冲突、ABA/回滚、追加链及失败顺序；过期头必须被原最终oracle拒绝并整笔回滚，其他直接Store调用保留旧路径。该成本尚不能从本次聚合core独立剥离，无秒级收益承诺；未实现。
3. scheduled同事务state消重需证明参数/slot/record依赖一致且中间只有读取；不是本轮新建瓶颈。revise/restore/reconcile跨真实写入的中间audit不得直接删，需证明原冲销前提不会被新状态修复/掩盖，并逐转移差分。

A的SAVEPOINT指纹反例、B的合法FK/投影/链的零效果重复CONSUME均保留；C唯一索引只覆盖一个谓词。没有可信基线/完整语义闭包证明，不使用TEMP journal/跨事务缓存跳过最终审计。生产仍O(全部历史)，本轮绿色不消除扩展性问题。

## 验证与交接状态

本地完整data472 PASS（0FAIL/0SKIP）、生成native Kotlin编译PASS、工具51PASS；观察器不在生产jar。接手40408未完成的chain漏计、后台未结束帧、透明代理入口与只读窗口负向检查已有RED/GREEN证据，原业务断言未削弱。根因及修复只涉及historyTest，不改SQL、生产事务或数据。

源87e9df95的push/PR六类必需CI共12实例全部SUCCESS（final-ci-source-receipt.json）；API26/29/35实际SQLCipher库存合同各139项PASS/0SKIP，HTML及官方工件SHA/CRC已核验。最终build-results工件同样核验：data472 PASS、reminder21 PASS、PeriodStability5 PASS；App共1839项，1562 PASS、277 SKIPPED、0FAIL。277项不计作通过；未改测试注解或跳过配置。原生分解与原冻结预算本轮PASS。文档证据提交[skip ci]避免相同源码重复原生，不冒用源87检查为文档HEAD检查。无真机、冷启动/重启/冷恢复首写和规模增长曲线证据，标为NOT_RUN；恢复后三写之前已有独立完整audit，不冒充冷首次。

P4仍Draft/OFF，不自动合并。主要剩余障碍是：尚无可证明完整、安全且能稳定满足预算的低历史依赖写入机制。此次解决的是归因证据缺口，没有生产提速、Schema/备份/加密/PK/UI/权限/版本/签名/发布变化。以前的FAIL/OOM/LMK、重观察器46.418s及全部原始预算/断言均保留。
