> 2026-10-21 当前状态（优先于下方历史记录）：生产dc10ffef已实现进程owner、真实NotesApp状态观察和busy时受限只读刷新；正式提交入口仍OFF。完整data528 PASS、修正后真实App2项PASS；原生owner14合同API26/29/35报告核验PASS。fb43 CI仅API26三个SAVEPOINT测试失败，d9fa9340修正后本地3项及Android编译PASS，针对性run39626720171在途，不重跑大库。原大库三写6.387/6.285/5.666秒仍FAIL5秒，网络工件已通过授权连接器取回；全部实际耗时、证据及边界见 [verified-results.md](../shell-integration/verified-results.md)。尚不可合并，Draft/OFF/autoMerge=null；无可信缓存或Schema/备份/加密/PK变更。

# 进程内实际用药保存 owner：实现与剩余边界

2026-10-21。接手 c2fffbb0，主线基线 320171cdc882ec0b62830b60e2329a4174ffa31c。
本组只推进 §126/127 的后端运行合同；不恢复已停止的性能研究。
PR #57 必须保持 Draft / OFF / autoMerge=null。

## 已实现的调用链

`MeasuredSaveCoordinator.prepare` → 固定 PRIMARY 的 Repository.prepare → 原事务冻结票据、scope/dataset/epoch、药物、实际用量、原时间、配置摘要和原关联 CONTEXT。

`start` → DatabaseAccess 共用门的 PRIMARY reservation → 进程 SupervisorJob/IO → Repository.submitPreparedMeasuredActualUse 根事务：

- 没有同 UUID 操作：调用原 create，原票据校验、全部业务写入与最后完整审计保留；根 Room 事务返回后才 SUCCESS。
- 已有同 UUID 操作：只调用原 observeCreate 的完整审计及冻结请求核对，返回历史回执，不调用旧 create 的部分输入重放分支，不产生第二次扣减。
- Writer 异常、取消或丢失交付：writer 完成后另起只读事务观察。只有 Committed 才 SUCCESS；NotObserved、损坏、异请求、读取失败等全部 PENDING/MANUAL_CHECK，不宣告未提交、不重放、不释放冲突 reservation。

页面只有 StateFlow 观察权，没有 writer Job。相同活任务 UUID 与完整原请求返回同一任务；异输入拒绝。完成任务用弱引用索引，未确认 active 任务保持强引用；不持久化请求/token。进程重启不沿用内存任务、票据或任何数据库可信状态，须刷新真实数据并人工核对，无自动恢复写入。历史成功不表示原记录仍是当前版本或未被删除/冲销。

只复制 process 的 Job，不复制 Room transaction/context。否则嵌套事务的返回可能先于真正外层提交；实际合同覆盖外层回滚期间不得 SUCCESS。仅 QUEUED 且尚未进入 Repository 的显式取消可立即 FAILED；已进入一律不能以取消充当撤销。

## 共用冲突门范围

|入口|处理|理由|
|---|---|---|
|普通/固定空间 Repository 的 generic transaction|完整调用期间计数；有 PRIMARY owner 时拒绝|任意 DAO 回调可以修改；不能以读名称放行|
|SQL/measured 业务事务、旧实际记录/库存/关联等|同一门；owner 仅持内部 lease permit|票据依赖全局链，不能只锁同一药物|
|TransMemo 导入、HRT Tracker 导入等|直接导入或 generic transaction 共用门|导入可改多表|
|备份恢复|解密/解析前进入门，覆盖替换及清理|与整库替换隔离|
|备份导出|也进入门|export 内 RegimenHistory.seed 可能补写方案历史，非纯读|
|DatabaseAccess.close/destroy、Repository.destroyAll|同步计数覆盖整个关闭/删除范围|没有 check/use 的 reservation 竞争窗口|
|明确的 typed 只读查询、prepare、observe、ticket、预测|保留读取路径；可能等待数据库事务|不允许任意 DAO callback 冒充只读|
|DECOY|独立空间计数，PRIMARY owner 不改投|select 不重定向已接受的请求|

门是**单进程协调**，不是数据库合法性或多连接信任证明。SQLite/SQLCipher、原票据、数据库约束、完整审计与原子回滚继续负责正确性；未知原生写入不因门存在而获得信任。当前源码中 DatabaseAccess 的业务使用集中于 Repository，但这不是对未来或任意 raw DAO 写入口的永久证明。

## 实质反例与修正

`first-focused/`：9 个 owner + 12 个既有 Repository 合同，21 PASS。

`replay-red/`：在生产 14dda1ec 上，原请求合法重放 PASS；以下两例实际错误 SUCCESS，共 2 FAIL：

1. 原 UUID、相同临床输入，但历史操作 created 与原冻结请求不同。
2. 原 UUID 本身存在，但无关历史 dose_record 被损坏，独立冻结全审计已经拒绝。

原因是旧 create 重放分支只做部分请求匹配，直接返回而不全审计。新 owner 必须使用 prepared 提交包装；**旧入口/历史规则不重写，不把 RED 改成接受错误结果**。修正源 984219a3；后续 GREEN 必须使用逐字相同的测试。正常新增只保留原一次最终全审计；已有 UUID 的确认走完整只读审计。本次不声称加速。

`space-red/`：在生产984219a3上，PRIMARY owner已占门，另开DECOY事务后切换全局选择到PRIMARY；旧私有sql()确实在PRIMARY写入合成day_note，1项真实FAIL。仅在事务入口捕获target不足以覆盖旧helper。

修正源8b24ccf5：同Repository实例通过ThreadLocal+asContextElement在generic/SQL/measured事务协程上下文绑定原空间；space/db()/sql()在事务内使用该目标，退出上下文恢复。明确pinned仓库始终优先自己的空间，其他协程的页面选择不被全局冻结。这是事务路由，不持有数据库内容或信任判断；原反例源码保持不变。后续最终回归应为527项；先前526项只证明984219a3。

既有 A（SAVEPOINT/指纹）、B（零消耗重复 revision）与 C 唯一索引原型不改；不新增信任缓存、增量验证器、Schema 或迁移。原冻结全审计继续独立对照。

## 历史8b24检查点的剩余边界（当前进展见文件顶部）

- **App 未绑定 owner**，NotesApp 状态 host 仍默认 null；现有状态 UI 不代表真实保存流程已接通。
- NotesViewModel.refresh 及提醒等 generic transaction 混合读取和可能的迁移/对账。owner 活跃时这些入口会保守 busy。接线前应建立受限 typed snapshot/读取合同及明确延后维护的行为，不能放行任意 DAO 回调，也不能声称所有页面已可无冲突浏览。
- MANUAL_CHECK 持有 PRIMARY 门直到独立观察确证成功或进程结束。本组没有“人工确认即释放”接口；其产品恢复流程、进程死亡核对与隐私显示仍需具体实现验证。已知回滚测试通过，不授权把所有异常直接报失败。
- 仅支持独立实际用药创建；计划完成、修订、删除/恢复、实盘等没有运行 owner 的等价完整结果核对合同。
- 当前测试为小型合成数据库、Robolectric35/framework SQLite，不是 API26 SQLCipher、LMK、OS 强杀、手机/TalkBack 或性能证明。
- prepare仍是suspend且会读票据/冻结输入；本组没有测量或隐藏其耗时。没有“入队即完成”、300ms体验已达标或冷启动/恢复提速的结论。
- 原 ≤5 秒硬门仍有效，将其改为独立指标未经批准；后台化不改变实际写入耗时或计时边界。恢复 LMK NOT_PROVEN、最终精确 HEAD 必需 CI/原生仍阻塞。本组不调度已停止的大库/native，也不以 [skip ci] 伪称 CI 通过。

Room13/业务备份12/PNBAK1/加密参数/PK/权限/签名/版本/P5/P6均未变；无 UI、发布或合并。

## 当前验证

最终生产8b24ccf5：完整data **527 PASS**（owner13项）、PeriodStability **5 PASS**、reminder **21 PASS**，均0 FAIL/ERROR/SKIP；compileDebugAndroidTestKotlin、compileFullDebugKotlin **PASS**。同轮Gradle BUILD SUCCESSFUL，4m50s；各套XML时间戳均属于本轮。完整日志、45+1+7 XML、源码/主jar SHA、SHA256SUMS与范围收据集中在 `final-local/`。主jar包含运行owner，不含TEST_ONLY原型/冻结oracle。space-red测试源码SHA逐字一致，原重放反例也保留通过。

前轮984219a3的526/5/21及编译PASS在fb9f2c30，旧日志保留为984219a3-*；不覆盖真实RED或借旧结果冒充最终源。没有在途测试；最终必需CI/native/LMK仍NOT_RUN/NOT_PROVEN，无原预算新结论。
