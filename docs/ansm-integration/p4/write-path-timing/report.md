# P4 真实写入归因与重复审计决策（2026-10-20）

状态：测试计时合同已通过，原生分解待测；P4 DRAFT / PRODUCTION_BLOCKED，正式入口 OFF。没有生产修改、Schema/备份/加密格式变化，不自动合并 #57。

## 基线与已有原生证据

审查基线 HEAD `27135013ab19d617f5becc2d5cb5c2b7b9208142`，主线 `320171cdc882ec0b62830b60e2329a4174ffa31c`。最新已完成自动原生运行 [39465628009](https://github.com/DevenirTwilight/HRT-Log/actions/runs/39465628009)，工件12286116312，官方与下载ZIP SHA256均为 `3df88d23bcf68548548c9beeaeba82b2bbcdbc9f5fcce93766c5c9c52034a98a`，ZIP CRC通过。原始JSON、环境、生成源码摘要、ART摘要及收集凭据在 `native-27135013/`。

实际API26 SQLCipher、RAM520962048B / ART heap201326592B，14600记录 / 2000容器 / 32603行 / 19660924B未变。全量审计7843.283ms，预测8672.131ms，备份10417.356ms，恢复46808.818ms，恢复后独立审计8291.658ms；真实三写含票据8670.217 / 9835.226 / 9458.947ms，全部超过5000ms。审计/预测15秒、备份/恢复120秒均通过；写入仍是预算阻塞。

本轮没有证据证明此前5秒已经通过。dc54的三写5747/5694/5288ms同样全部失败；生产代码相同，不同宿主机运行不能证明代码因果回退，也不能断言只是宿主机问题。

旧诊断在原预算之后分别运行：票据1282.395ms、验证1044.135ms、操作链441.678ms、缓存全审计6935.732 / 7582.020ms、无缓存全审计13608.671 / 13036.260ms。它们不是同一次写入的互斥阶段，**禁止相加或相减推算实际INSERT/COMMIT成本**。ART记录也覆盖独立审计/票据探针，属于近似线程CPU采样，不是写入调用次数或墙钟分解。它确认SQLCipher游标填充、JSON预算/解析、UTF8和字符串转换等工作存在，不能单独给出真实写入比例。

## 真实调用链与检查位置

新建用药记录：

1. `NotesRepository.measuredWriteTicket` → `transactionWithSql` → `MeasuredUseIntegration.ticket`。检查scope/身份，`state`扫描全操作链并读取药品、profile、rules/times、容器、流水、reference事件和可选record，再序列化并SHA256。这是防陈旧写入票据，不是完整历史正确性证书；票据事务单独结束。
2. `NotesRepository.recordMeasuredUse` → IO调度 → Room外层事务 → `MeasuredUseIntegration.create`。再次检查scope/owner、输入及operation UUID幂等；`validate`重新生成state并比对ticket。
3. DAO插入dose_record；冻结context/identity/snapshot；`consumeNewRecord`检查record revision、presentation head、数量和分配、追加operation/CONFIRM及allocation/ledger。`MeasuredOperationChain.next`再次验证全链，再构造后继链。`supply_transaction` INSERT同步触发 `supply_cache` UPDATE supply_container；guard和不可变历史触发器同步执行。
4. STOCK_CAPTURED已拿到消费receipt；`create`调用一次 `auditStagedDataset`，然后构造返回对象；Room mark-success/endTransaction，真实COMMIT发生在最外层endTransaction。

**新建记录的成功非幂等路径只有一次全数据集审计，连票据共四次全链检查（ticket、validate、next、最终审计各一次）。** 幂等返回是独立分支，不能拿来代替真实写入验收。

完整审计入口清单（源码 `MeasuredUseIntegration.kt` / `MeasuredConsumptionStore.kt` / `NotesRepository.kt`）：

|路径|完整数据集审计数|是否同一状态的冗余|
|---|---:|---|
|create新建、completeScheduled新记录|1|否，最终写后状态|
|count / delete / packageAction|1|最终写后状态；幂等返回另计|
|revise已有ledger|2|consumeNewRecord内检查已冲销旧revision的中间状态；另一次检查新消费最终状态|
|restore|1或2|有旧ledger时同上，不同状态|
|reconcileRevise|最多3|reconcileOriginal前态、consume前已冲销态、最终态，期间有真实写入|
|reconcileDelete|2|reconcileOriginal前态与删除后最终态|
|createMeasuredPackage|1|建包后最终态|
|完整迁移/恢复验证|多次/不同数据库|staging、target、恢复后不同快照，非正常记录链上的重复|
|共享预测|1|独立读操作，不应计入一次recordMeasuredUse|

`MeasuredConsumptionStore.kt:811`的reconcileOriginal前审计、`:851`的consumeNewRecord旧ledger分支，以及Integration各末尾，是条件重复来源。新建record没有旧ledger，不走851。所有次数均指成功的非幂等分支；异常可能更早退出。

另有两种真实重复：`measuredScheduledTicket`在同事务先ticket(scope/owner预检)再ticket(含可选record)；`completeMeasuredSlot`在slot检查前validate，Integration.completeScheduled再validate。这重复的是state/全链检查，不能称作重复全数据集审计。

完整审计内部：`auditReadSnapshot`先调用`BackupValidation.validateMeasuredCandidateState`，后又执行`PRAGMA foreign_key_check`；前者的`validateState`第一句已执行同一PRAGMA。期间仅有读查询。该重复查询与两次完整审计是不同量级。

## 约束归属与最小可执行设计

|类别|现有保证及不足|允许的消重边界|
|---|---|---|
|数据库直接保证|主外键、已有唯一索引、SchemaGuards行值、不可变流水、supply_cache同步维护|不能据此推断完整业务历史合法|
|操作自身语义|owner/scope、ticket新鲜性、临床输入、幂等、presentation head、单位/数量转换、当前revision及冲销前置条件|保留操作检查；仅相同事务、相同快照、相同谓词才可复用|
|完整历史语义|列类型/总行数/文本预算、references/历史/化验/方案/trash、完整hash链、所有operation/CONFIRM/allocation集合、逐容器投影及跨容器冲销因果|当前仅全量oracle覆盖；局部投影或唯一索引不等价|

决策：不将未经证明的局部验证接生产。新建路径没有第二次全审计可删除，故“消除重复全审计”的理论与已证实收益均为 **0次全审计 / 0ms**；这不等于其余重复检查没有优化空间，也不声称所有路径都没有冗余。

最小候选按证明难度排列：

1. 同一次read-only `auditReadSnapshot`仅执行一次FK检查：保留`validateMeasuredCandidateState`的先验检查，删除后续相同PRAGMA。义务：无中间写入/回调/让出事务；同连接事务快照；所有独立oracle仍保留FK覆盖；构造坏外键并逐条对照原oracle，不能只验证正常夹具。收益上限是第二个FK查询的完整游标窗口，**不是一次完整审计**。本轮先测，不提前承诺收益或改生产。
2. scheduled路径复用同事务的state检查结果：需要区分首次owner预检与含record的ticket，证明中间timeline/slot读取无变更与重入、参数依赖一致、幂等分支/失败顺序/回滚行为不变；用事务内词法证明或显式私有已检查参数，不能保存跨事务信任缓存。与当前新建验收不是同一路径。
3. revise/restore/reconcile的中间全审计不能直接删：最终审计可能允许中间坏前态被修复或覆盖，或者冲销算法已在坏前态计算了新事实。必须证明 `valid(pre) && transition => valid(post)` 或最终验证可独立认证原有冲销前提，枚举所有正反向/负依赖、注入触发器、SAVEPOINT、跨连接及异常，差分对照冻结oracle。现有C部分唯一索引只阻止重复CONSUME，未覆盖其余语义。

A反例（SAVEPOINT后指纹匹配坏状态）禁止使用total_changes/data_version/schema/epoch作信任证书。B反例（合法FK/平衡投影/有效链的零效果重复CONSUME）禁止使用局部库存合法代替历史合法。C唯一索引原型只解决该唯一性谓词；不改变上述缺口，不占Room版本。

## 同一次真实调用的计时合同

仅新增historyTest适配器 `P4WritePathTiming`，包装真实SupportSQLiteOpenHelper/Database/Statement/Cursor，不改SQL、生产源码、事务或验证规则。Root分别围住真实Repository票据API、写入API；跟踪事务深度与mark-success，区分最外层提交、回滚和嵌套事务结束。Room缓存statement也始终包装，防止预编译遗漏。上限16线程/32core窗口/256模板/1024driver键/32FK窗口；超界或时间重叠令accounting_valid=false，不制造绿色结果。不保存SQL绑定参数。

写入wall的互斥归因是：validation core下界 + audit core下界 + cores外INSERT/UPDATE/DELETE(driver，含触发器) + cores外最外层endTransaction + cores外其他driver调用 + 剩余Java/IO调度/观察器/方法尾部。core从实际方法首次`inTransaction`调用前至该方法最后driver返回；**不是精确Java方法退出**，最终hash/返回尾部保留在未归因项。各core内driver明细和FK窗口只作嵌套信息，绝不再次求和。observer耗时仅能测下界，不能从预算扣除。checkpoint只输出嵌套时间点，receipt构造无法独立观测时仍记未归因。

纯触发器exclusive时间在现有SQLCipher4.10接口没有可用profile hook；INSERT/UPDATE时间包含对应触发器，不能再分出可相加的trigger项。最外层endTransaction包含引擎/驱动收尾，**不是单独fsync或纯原生COMMIT时间**。消费receipt构造与身份冻结的Java逻辑未被适配器完全隔离；必须明确未知。

原native模板SHA256 `9313167178989def0d4d89d785f84cdde764fba21033543cdf85e472c5511242`不变。生成器仅给测试库加适配器，原阶段 recorder关闭；原固定夹具、三次真实写入、所有预算和断言保留。全部原预算捕获、只读诊断和ART采样结束后，单独root增加一次真实已提交recordMeasuredUse（14600基准记录已完成原三写后，诊断为第四写，不能冒充原验收）。之后再次独立全审计验证2000投影/目标5quanta；小型共享合同另验证提交、STOCK_CAPTURED异常整笔回滚、dump完全相同和最终完整审计。

## 待补实测表

|互斥阶段|同一原生调用实测|证据限制|
|---|---|---|
|实际数据库INSERT/UPDATE/DELETE，含同步触发器|NOT_RUN|旧报告不能分离|
|最外层提交driver|NOT_RUN|纯引擎COMMIT仍不可分离|
|ticket API / 写入validate core|NOT_RUN|core仅下界；身份/receipt部分未独立分离|
|最终全数据集audit core|NOT_RUN|嵌套driver不能重复加总|
|其他driver与未归因残差|NOT_RUN|含Java、调度、观察器、方法尾部|

停止条件：只做一次有新证据价值的原生测量；若计时合同不成立则不发布伪分解。没有完整等价证明，不接入快验证器、不删除中间完整审计、不移动首次审计/恢复耗时到预算之外。本地首轮计时合同识别出观察器缺陷：Kotlin internal模块后缀/Robolectric方法名导致漏计audit，原断言失败保存在local-validation/observer-entry-name-red.xml。已按原方法名的$分段匹配修正，断言未放宽；修正后同一提交/回滚/内容一致/最终完整审计合同PASS（1 test，0失败/跳过），GREEN原始XML在同目录。最新CI与本次原生计时结果待补；P4仍不具备合并条件。
