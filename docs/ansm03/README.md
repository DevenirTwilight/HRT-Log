# ANSM-03A 工作交接

2026-10-16。授权范围：量纲合同、test-only 纯 Kotlin 原型、合成验证、03B 审批方案。
不接入库存/预测/日历/提醒，不改 Room10、PNBAK1 schema9、PK、UI、权限、版本或签名。
用户本轮要求避免共享 HANDOFF/REQUIREMENTS 等冲突，因此本目录记录新增要求与逐阶段交接。

基线：默认分支 `claude/new-session-1959qb`，`1e8754e9bab8b01034cb3de22ceaf9dc61a1438a`。
开工 PR45 OPEN，HEAD348c68def84ccc627a46be57ace463ade3635a15；PR48 OPEN，
HEADdf0a615f78589e9c7df5352fda75f2364ebcddaa；两者六检查成功但未合并。
PR49占用共享HANDOFF/STATUS/交付账本。本分支从默认分支创建，不继承45/48未合并内容。
01/02设计通过固定Git对象只读查看；工程绿不表示资料许可或生产接线通过。
未发现许可版本、FT/ST或官方数据分发负责人确认；继续保留阻断，不重分发临床原文。

## 现状数据流与文件边界

- `core/data/.../Entities.kt`：MedicationEntity单位/实际剂量/默认容量是用户配置；ProfileEntity只属PK。
  ContainerEntity只有capacity/initial_used_amount/used_amount及状态，没有自己的单位、CIS/CIP或强度。
  SupplyEntryEntity保存CONSUME/REVERSE/ADJUST标量，唯一操作键及反向引用。
- `MedicationSnapshot.kt`：v2冻结用户名称/成分/途径/单位/PK，不含官方商品和容器规格。
- `NotesRepository.complete/unscheduled`：事务保存实际记录与快照→SupplyLedger.allocate。
  计划完成重复调用直接返回；手动批量backfill只写历史不扣库存；导入关联也不补扣。
- `SupplyLedger.kt`：只分配ON_TIME/LATE未删除记录；快照单位与当前药物不同则全量未分配。
  已开容器按opened_on/id，随后SEALED按id；追加CONSUME，触发器更新used，耗尽置EMPTY。
  不足保留unallocated_supply_amount。未知快照单位当前不阻止分配，这是既有边界，不能据此建立新换算。
- 实际编辑/删除先REVERSE未冲销消耗，再修订记录；实际恢复可重新分配。不是改写原流水。
  `PackageCorrections.kt`：VOIDED拒绝存在CONSUME/REVERSE的包装；允许ADJUST历史，保存容器/账本/上下文哈希，
  重复作废幂等；恢复拒绝上下文变更。不能用删除/恢复绕开新量纲检查。
- `NotesViewModel.refresh`统一提交快照、取消过时读取，各页共享。`StockForecast.kt`汇总同med的IN_USE/SEALED标量，
  遍历Timeline未完成槽（跳过/改时/剂量覆盖/计划版本），已服不再减；366日窗口，7日低库存。
  日历/库存与`core/reminder/.../StockAlerts.kt`使用同一预测，日期差按当地日历/DST。
- `saveMedication`即使只保存元数据也会reconcile/更新规则快照；resizeContainers=true会改可调整的开封/未开容器容量。
  **未来官方引用更新必须独立事务，不调用这个路径。** 当前编辑可填小数但没有成分与片数换算。
- `NotesDatabase`运行版本10；`DataTransfer`显式业务表/列白名单、PNBAK1 format1；业务schema9。
  `BackupValidation`验证结构/关联/账本；未来新表不会自动得到备份支持。旧备份不能推断官方身份。

计量核心选择 `core/domain/src/test/kotlin/.../measurement/`，沿用既有JUnit5与JVM任务，
没有新增依赖或Gradle/CI修改，不进入main编译产物或Android。是可执行的隔离原型，不是正式库存集成。

## 阶段记录

1. 审计与合同已写，LegacyBoundaryTest 4/4 PASS，说明旧标量投影信息不足，不制造生产失败。
2. 计量实现/完整回归/最终CI：待实际执行；此时NOT_IMPLEMENTED/NOT_RUN。
3. ANSM-03B全部NOT_IMPLEMENTED，独立审批前不迁移、不接线。
