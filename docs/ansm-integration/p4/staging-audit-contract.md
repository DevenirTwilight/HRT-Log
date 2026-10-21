# 候选暂存库审计合同

2026-10-17。P4内部验证，正式备份接受仍关闭。

`auditProjection(NotesDatabase, ReferenceScope, containerUuid)` 保留运行库入口：同一数据库事务内先验证设备本地epoch，再读取稳定业务身份。它调用 `auditStagedProjection(SupportSQLiteDatabase, datasetUuid, containerUuid)` 的同一只读SQL实现。后者用于可信DDL创建的隔离内存暂存库；不要求也不恢复设备的 `reference_runtime`，不得用它绕过运行库作用域检查。

两者共用包装codec、有方向的换算和StockQuantum精确归并，不维护第二套数值算法。原SQL中的REAL仅在不可变PRESENTATION证明该容器为整数刻度模式后按有界整数检查；从未声称把旧Double恢复成原始精确计量。游标拒绝文本冒充REAL、非整数ID/修订及非有限数值。缓存差异只返回 `cacheMatches=false`，不修复、不写流水、不修改记录。

测试暂存库只复制受信当前表定义和合成业务行，不执行备份提供的DDL，不创建明文临时文件，不复制runtime。FK保持开启，使用原有LedgerReplacement/ReferenceDependencies的依赖顺序。运行库与内存副本结果相同，前后逐表数据不变；非法暂存行不影响原库。此辅助复制器只在historyTest，不是正式恢复实现。

## 不能据此启用备份或实际库存入口

单容器审计不是全局历史证明。后续全局候选校验必须依次覆盖：

1. 原有预算、严格列类型、FK、唯一性、数据集和稳定身份、引用链及摘要；原PRODUCT-only校验默认行为不能因候选模式被放宽。
2. 每个新模式容器的不可变包装/原单位、整数账本及物理分配配对，所有前缀范围和精确最终缓存。退役身份须按原稳定身份验证历史，不能重新接到复用整数ID；当前单容器入口明确要求live身份，不假装已覆盖退役恢复。
3. 全部操作而非仅有流水的操作：空来源/不可转换/容量不足/被作废包装的CONSUME，零差额ADJUST，以及无分配的REVERSE。重核输入、输出、原因、冻结源和CONFIRM捕获；未知不是零。
4. 操作、分配、原流水、CONFIRM集合双向覆盖，禁止孤儿或额外证据、重复消费/逆操作、异药物/修订/数据集；完整跨包装逆集合不能只验其中一盒。
5. 实盘因果锚与原消费先后、无差额观察、历史重放及观察协调；时钟不证明因果，当前余额相同不证明确认票据未过期。
6. 在整个暂存库通过之后才允许精确缓存重建；任何拒绝必须发生在替换目标或退休目标epoch之前。正式恢复失败注入需证明目标库逐表不变。

当前 `ReferenceSchema.validate` 仍拒绝PRESENTATION和measured_operation；业务备份Schema仍10，PNBAK1外层不变。此步骤没有更改正式恢复或预测。只有完整验证、新业务版本拒绝、旧数据兼容及统一预测/UI完成后，才可单独启用接线。
