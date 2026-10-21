# P4 非阻塞保存：产品验收变更提案（2026-10-21）

状态：**停止性能攻关继续有效；验收政策细节待负责人批准；当前执行代理负责后端与UI，继续推进。** PR #57仍Draft，正式入口OFF，不合并、不发布。本文不是原5秒预算PASS证明。

## 实施检查点（2026-10-21）

当前执行代理同时负责前后端。以下是已编码/已测范围，不是P4准入：

|部分|状态|证据/剩余边界|
|---|---|---|
|共享三状态与phase显示协议|IMPLEMENTED；3项PASS|domain/write/WriteProgress；只是显示协议，不是提交证明|
|Material 3四语内联状态卡片|IMPLEMENTED；操作3/布局8/翻译2 PASS|四语言各320dp/2x/深色与411dp/1x/浅色代表布局，非全矩阵|
|实际NotesApp状态区及导航|IMPLEMENTED；主壳2/原返回17 PASS|默认null；收起可展开，不取消业务；截图与32项同次结果见shell-first/|
|后台任务owner原型|TEST_ONLY；7项PASS|页面观察取消独立、结果不明不重试；不在runtime/release|
|真实Repository小库连接原型|TEST_ONLY；4项PASS|ecc6671b，真实事务/完整oracle/回滚/丢失结果窗口；原型7项同次PASS，repository-first/；不是SQLCipher原生|
|应用级真实任务服务与公开门面|NOT_IMPLEMENTED|需固定PRIMARY/scope、无重复请求及冲突入口覆盖|
|完整运行结果核对/进程死亡恢复|NOT_IMPLEMENTED|目前仅internal独立实际用药的历史观察；不能凭UUID存在/不存在判定。其他操作与持久token未接通，不自动补写|
|独立实际用药的原请求冻结/历史提交观察|IMPLEMENTED internal；全数据514 PASS|ae7688da，完整审计及原CONTEXT/配置/时间/实际载荷精确核对；NotObserved不授权重试；reference-context-red/及-green/。尚无App运行调用，其他操作未支持|
|验收政策/正式入口|PROPOSED / OFF|原5秒断言未变，Draft不合并；恢复稳定性NOT_PROVEN|

资源退出修正31303e64已接入：完整本地core:data506 PASS/0SKIP，原两项RED断言保持并转GREEN；证据resource-cleanup-applied/，旧RED仍保存。这不构成完整P4准入或LMK稳定证明。新组CI/native/真机/TalkBack均NOT_RUN，不重跑大库；最终ae7688da的Android测试源码/App源码编译PASS，先前4111c0b8的37项App/时期测试PASS，最后context字段修正后未重跑App测试（App源码未改）。下文保留政策与尚未实施的门面设计；其中“本轮不新增”指最初方向冻结检查点，不否认后续已授权UI和TEST_ONLY实现。

## 立即冻结与保留

停止缓存/全审计微优化、增量验证、方案C研究和新的大库跑分；取消§125“冻结后再启动两轮”的未执行计划。旧原生、LMK、RED和工具/编译失败全部保留。完整审计、票据、UUID/epoch/revision/ABA、追加流水、原子提交/回滚、SQLCipher及PNBAK1均不改变。

源55fcf2d5：data495、提醒21、生成Android测试编译通过；App1562通过+277既有跳过、Period5通过。新增资源退出合同在现有生产上2项RED，修正只作为在途WIP保存，不能冒充全面通过。没有新候选原生运行。恢复专项仍只有一轮完整恢复41.633s通过，另一轮准备LMK/恢复未运行，**恢复稳定性尚未证明，是独立安全阻塞**。

## 待批准的产品验收变化

|项目|当前已冻结证据/规则|建议变化（未生效）|
|---|---|---|
|普通写入含票据≤5秒|旧失败继续FAIL，现有断言保留|作为独立延迟指标记录，不单独阻断产品；不重标旧结果|
|用户等待|非阻塞交互尚未实现|提交即呈现真实PENDING；无需全屏等待，离开页面仍可在进程内继续执行|
|成功|必须真实提交|仅事务完整提交并可靠交付结果/核对提交后SUCCESS；不在checkpoint或入队时提前报成功|
|正确性与恢复安全|全部原不变式/回滚/低内存规则|继续硬门；LMK、数据不一致不能因异步UI而放行|

审计/预测15秒、备份/恢复120秒、API26/512MiB/192MiB、32MiB、100000行及迁移/版本拒绝不在本提案放宽范围。真实耗时仍完整记录，含票据、等待互斥、事务和结果交付；冷启动/恢复首写单列，不把等待移出计时。交互可用性与存储延迟分别验收，交互门槛的具体量化由UI验收另定，本文不虚构新数字。

## 最小后端评估：可复用，但还没有异步任务服务

|现有源码|已具备|仍需后续小幅接线/验证|
|---|---|---|
|NotesRepository.pinnedTo / transactionWithSql / measuredTransaction / recordMeasuredUse|suspend；IO线程；目标space在切换线程前捕获；Room withTransaction结束后才返回|应用级owner调用pinned PRIMARY，不能用页面rememberCoroutineScope/viewModelScope作任务寿命|
|MeasuredUseIntegration.create / completeScheduled等|票据校验；记录/来源/消耗/最终审计同事务；UUID同载荷重放与异载荷冲突检查|逐操作保留现有语义，不假定所有操作都有同一重放合同|
|MeasuredUseIntegration.state|票据包括record、库存和全局chain_sequence/hash|不同药物提交也可能使旧票据过期，不能只按记录ID判断冲突或自动刷新旧确认|
|MeasuredConsumptionStore.existingConsumption等|内部读取既有操作、请求及回执|没有公开、作用域完整的只读“提交结果核对”接口；仅找到UUID行不能替代完整验证|
|NotesApplication.startMaintenance|已有一次性IO协程和提醒WorkManager|不是可复用的保存任务owner；不复用/扩展提醒Worker，不新增持久队列|

P4接口及票据/回执多为core:data的internal类型，App模块不能直接调用。后续建议在core:data内部复用它们，提供最薄public facade/DTO；应用只注入一个进程级SupervisorJob作用域并观察StateFlow。不创建第二业务数据库、持久任务表、WorkManager或复杂调度器。本轮不新增这些类，也不把所有内部API改public。

该作用域只承诺**当前进程内**跨页面继续，不保证退后台后Android不杀进程；离开页面不取消提交，锁屏/伪装仅停止显示敏感状态，不能改投其他数据集。现有普通transaction在IO内部取space，故新门面必须使用pinned仓库及已捕获目标的P4方法，不泛用未固定空间的入口。

## 提供给UI的最小状态合同（提案，NOT_IMPLEMENTED）

每个事件含operationUuid、operationKind、受控space/dataset/epoch、稳定药物/记录/容器引用、请求指纹、状态及安全错误码；健康内容不进日志或未加密状态存储。实际剂量、来源、原请求和时间冻结，不因重试按当前规格重建。修订/协调含多个既有UUID时须保留原UUID组与原子性，不能靠其中一行猜整体完成。

|状态|唯一含义|UI与重试|
|---|---|---|
|PENDING|进程内正在提交，或提交结果仍待核对|可带phase=WRITING/RECONCILING/AWAITING_MANUAL_CHECK；后两者不能显示“仍在写入”。不增加新业务结果状态，不自动重试|
|SUCCESS|同作用域/同请求的真实提交已确认|返回已提交回执，再读当前事实；历史提交成功不等于记录仍未被后续修订/删除|
|FAILED|拒绝/回滚或未启动已被确认，本次无新增业务效果|显示原错误；是否可重试由原因决定，不将提交结果不明写成FAILED|

- operationUuid在首次提交前固定；同UUID同不可变请求复用同一任务/回执，同UUID异内容拒绝。重复点击不生成第二笔消费。UI不生成“先成功后补写”的假状态。
- 取消：未启动且能确认未调用Repository时可FAILED(CANCELLED_BEFORE_START)。已提交则V1建议只允许离开/停止观察，不把Job.cancel当“数据库撤销成功”。任何取消/超时/结果交付中断存在提交窗口时，先PENDING(RECONCILING)。真实撤销仍走原追加冲销流程，不能伪装取消。
- withContext可能在事务已提交后、返回结果前观察到取消；不能catch任意异常就宣布未保存。应用级owner与页面生命周期分离，后续必须测试此窗口。
- 核对与重试：先确认原任务已结束，在固定PRIMARY、同dataset/epoch的事务读取原操作及完整匹配请求，复用原完整审计/回执验证。确认已提交则SUCCESS；确认未提交且没有仍在运行的writer才可原UUID原载荷重试。过期票据不自动刷新；实际来源/规格或修订已变须用户重新确认。
- 进程死亡：重开后基于数据库核对，不能从旧整数ID或UI保存标志推断成功。若有经隐私审查保留的最小UUID/作用域token则核对该操作；无token则刷新真实记录并要求人工核对，不能恢复“丢失的后台队列”或自动补写。token持久化方式尚未批准，不新建健康载荷队列。
- 数据集已恢复/epoch改变、UUID遗失或验证失败：提交知识仍不明，保持PENDING(AWAITING_MANUAL_CHECK)并禁自动重试；不以“未找到”推断未发生消费。此人工核对出口的UI文案需负责人/前端确认，不能永久无解释地转圈。

## 冲突与页面可用性

V1建议每数据集最多一个已接受P4写任务，额外冲突提交立即返回busy且不入持久队列。它比按记录锁更保守，因为当前票据依赖全局操作链；不新增细粒度锁证明工程。允许浏览其他页面；数据库读可能等待写事务，显示上次真实结果及局部加载状态，不能保证IO查询都无等待或把旧库存装作新提交余额。

同记录/同药物库存的修改、包装VOID/RESTORE、旧库存路径、方案/药物规格编辑，以及整库恢复/重置须纳入冲突入口保护；现有Repository公开写入口并不经过拟议门面，故仅页面禁用按钮不构成安全证明。后续接线需覆盖真实入口并保留DB票据/事务为最终防线。restore/reset等整库动作不得与未结任务相互覆盖。确认该覆盖前P4继续OFF；本轮不改这些入口。

## 后续批准后的测试调整范围（现在不改测试门槛）

1. 在独立政策变更中逐项批准原模板`performance/LargeInventoryPerformanceProbe.kt`与生成器`scripts/prepare_ansm_native_performance.py`里的普通三写/额外冷codec写5秒gate如何改为非阻断指标；保留原始legacy budget结果、全部真实写入及独立oracle，不删除失败日志。API/内存、恢复和正确性硬断言继续保留。现有workflow及六必需CI不改。
2. 后端小合同：跨页面owner、固定空间、同UUID重复点击/异载荷、跨药物票据过期、同库存冲突、取消前/中/提交后、失败回滚、锁屏/伪装、进程重启核对、epoch/ID复用、没有token不自动重试。模拟注入窗口与真实SQLCipher分别标记，不能拿模拟通过声称LMK修复。
3. 当前执行代理同时负责后端与前端：同一真实状态源、不全屏阻塞、可浏览无冲突页、错误可恢复、返回/旋转/会话保持、四语/无障碍；不再指定其他代理或等待其接线。按阶段隔离修改并共同验收。

待批准的具体事项：延迟硬门→指标的政策与测试差分；pending核对/人工出口；最小token保存边界；门面公开DTO和真实写入口冲突覆盖。批准并完成后端/UI/安全验收之前，不转Ready、不合并。恢复LMK仍独立BLOCKED；不再用普通写入优化消耗运行次数。
