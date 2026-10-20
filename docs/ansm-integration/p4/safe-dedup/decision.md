# P4 安全去重：执行合同（2026-10-20）

基线6ee217f8（实测源码87e9df95），主线320171cd；#57保持Draft/入口OFF，不自动合并。不改Room13/业务12、PNBAK1、UI、PK、P5/P6、权限或发行。

## 冻结验收安排

在生产源码固定前写定：本地先RED→GREEN及完整data回归、Android测试编译；提交一次生产候选，执行PR自动原生运行一次，再针对完全相同源码workflow_dispatch一次。最多两个候选原生运行；失败、LMK、OOM全部保留，不补跑求绿。API26/29/35兼容由同一候选CI执行。首次/重启/恢复后首次的生命周期不由预热的三笔写入代表；没有独立证据仍标NOT_RUN。

比较对象是已归档87e9df95，不是从其他运行拼接的分段。相同镜像、固定夹具、预算和准备流程；GitHub独立宿主不是同机AB对照，宿主、JIT、GC不可控。因此可以报告本轮结果和历史差异，不能宣称严格因果提速或证明长期稳定。若两次不足以达标，停止毫秒优化，回到方案C/产品准入决策。禁止改RAM/heap/14600记录/2000容器/32603行/32MiB/100000行或原断言。

## 外键检查等价性

`auditStagedDataset`要求事务，构造`ReferenceAuditReads`，同步调用`auditReadSnapshot`，finally执行`finish()`。`BackupValidation.validateMeasuredCandidateState`先执行完整FK检查，游标use关闭；随后所有验证函数只查询/解析，两个覆盖/UUID碰撞SELECT也use关闭，无suspend、外部回调、自定义SQL函数注册或间接DML。SQLite没有SELECT触发器；同一底层SupportSQLiteDatabase、同一事务快照不因另一连接提交而变更。只读adapter拒绝execSQL/statement/insert/update/delete，检查每次读取仍在事务；finally拒绝实际写入或schema变化。

因此仅移除这个私有审计路径中的第二次FK检查；保留BackupValidation的首个检查，独立备份/恢复/候选验证仍各自完整检查。这里的计数/模式栅栏只用于同步只读作用域，不是跨写入信任证书；A的SAVEPOINT反例完全保留。证明范围为应用可信SQLCipher驱动及固定验证代码，不声称可检测恶意数据库驱动、物理篡改或有副作用的任意外部函数。

测试要求：真实FK孤儿拒绝+事务回滚、独立普通/候选验证仍拒绝孤儿、在原删除点重放FK的test-only oracle结果一致、零预算全SQL oracle一致、间接DML拒绝、现有故障注入和A/B反例不变。观察器FK计数从2变1，链读取保持4，最终全审计保持1。

## 操作链复用：当前不接生产

验证到append之间并非只读作用域：记录写入、冻结context、检查点回调均发生于此；reconcile还会先追加reverse/count。旧头不能直接授权append；只检头/长度或total_changes不能证明不可变前缀和完整历史语义。最终审计能拒绝陈旧链，但这不证明旧/新路径接受决定和错误类型完全等价。缺少覆盖所有写入入口与回调的闭包证明，暂保留next中的独立验证，不增加未经证明的内存证书。第二项状态NOT_IMPLEMENTED。

## 若收益不足

方案C的部分UNIQUE只能覆盖重复CONSUME(record_uuid,revision)，不能证明request/input/header一致、CONFIRM/分配集合完整、逆操作/实盘因果和历史修订及旧库存覆盖。需先把每项全局语义映射为数据库约束或可组合的受控事务规则，明确未覆盖规则；不得再维护未经差分证明的第二庞大验证器。若需要持久检查点/约束变更，须独立批准Schema、迁移、业务备份升级、旧读者拒绝、恢复全量验证和崩溃原子性，不能抢占下个Room版本。冷启动/重启/恢复不能隐去建立基线成本；无法证明时继续全审计并保持BLOCKED，由负责人决定是否批准该结构性工作或调整产品验收政策，当前预算不变。
