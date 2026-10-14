# 50 — MED-02B4 孤儿对象、保留与配额政策合同

2026-10-14。本文件是**测试协调器合同 + 默认保守产品建议**，不是已批准的永久清理规则，也不是生产实现（生产 src/main 没有附件 writer）。实现与测试见 `core/data/src/androidTest/.../gate2/Gate2Store.kt`、`Gate2StoreAndroidTest.kt`、`Gate2RestartAndroidTest.kt`、`Gate3ProcessLockAndroidTest.kt`。

## 默认保守建议（待负责人决定）

1. **不按天数自动永久删除**任何孤儿或隔离对象。是否允许周期 GC、保留多久，由负责人另行明确决定。
2. 只有能证明属于本 operation 的加密临时对象才可删除：持久记录的叶子 dev/inode，或“本 operation 新建并记录的父目录 dev/inode + 普通文件”；不信任字符串路径。
3. `releaseQuarantined(confirmed=true)` 只在用户明确确认、取得该 operation 的 maintenance 租约（无活动写入者）、且对象非 active / 非 COMMITTED 历史 / 无读取租约时执行；**存储压力不得静默触发**。
4. 配额超额时不得自动删除任何可能是唯一原件的对象；应允许用户查看与恢复旧数据。
5. 正式产品提示只在已解锁 PRIMARY 的存储管理界面出现，不经系统通知、不在伪装外壳出现“医疗文件”字样。附件 UI 未上线，本批只交付合同，不加假入口。
6. 旧 dataset 的 CURRENT/RETIRED 指针在 B3 完整备份恢复后如何切换，属 B3 设计依赖；本批不动生产 DB、不复活旧链接。

## 状态处理与容量核算矩阵

配额统计：`phase NOT IN ('ABORTED','CLEANED','RELEASED_FOREIGN')` 的预约计入（与 prepare 同一规则，`Gate2Store.reservedBytes()`）。

| reconcile 结果 / 阶段 | 是否计入配额 | reconcile 自动动作 | 确认释放 | 文件 |
| --- | --- | --- | --- | --- |
| `SKIP_LIVE_WRITER`（他人持锁） | 计入 | 不分类、不删除、不改阶段 | 拒绝（`Synthetic writer lease held`） | 保留 |
| `KEEP_ACTIVE`（COMMITTED 且为 active） | 计入 | 完整重认证后保留 | 拒绝（`Not a quarantined orphan`） | 保留 |
| `QUARANTINE_COMPLETE_ORPHAN`（AUTHENTICATED，或 COMMITTED 非 active） | 计入 | 仅隔离，不删除 | AUTHENTICATED：可，删自有文件→CLEANED；COMMITTED 历史：拒绝（交 GC 租约规则） | 自有才删 |
| `CLEANED_OWNED_INCOMPLETE`（PREPARED/WRITING/CLEANUP_PENDING 且自有或不存在） | 转为不计入 | 删除自有未完成叶子→ABORTED | 不适用（已终态） | 已删 |
| `QUARANTINE_FOREIGN_PATH`（路径上是无法证明属于本操作的文件） | 计入 | 不删除 | 可，**只释放本 operation 的预约，外来文件永不删除**→RELEASED_FOREIGN | 永不删除 |
| `CLEANUP_PENDING`（自有文件清理失败） | 计入 | 再次尝试清理自有文件 | 可，删自有文件→CLEANED | 自有才删 |
| `QUARANTINED_EPOCH`（旧 dataset/epoch） | 计入 | 不删除 | 非 active 且非终态：可（自有才删）；active 指针：拒绝，归 B3 | 自有才删 |
| `RETAIN_TERMINAL`（ABORTED/CLEANED/RELEASED_FOREIGN） | 不计入 | 无 | 拒绝（幂等：不重复释放） | — |

**对任务书“未判明所有权的对象绝不释放”的解释（需负责人确认）**：外来文件本身绝不删除、也不被当作本应用对象；`RELEASED_FOREIGN` 只释放本 operation 自己的预约记账，并在报告中保留“该路径存在外来文件”的事实。若负责人要求更保守（外来路径永不释放预约），可改为拒绝释放，代价是配额永久占用。

## 幂等与断点续做（本批新增测试）

- AFTER_CREATE 失败：立即 ABORTED；重复 reconcile 两次均 `RETAIN_TERMINAL`，配额不变；确认释放被拒。
- IN_COMMIT 回滚且清理失败（CLEANUP_PENDING，自有密文保留）：确认释放→CLEANED，配额恰好减少该预约；再次释放被拒；重复 reconcile 配额不变。
- 删除后、SQL 更新前崩溃（以“文件已不在、阶段仍 CLEANUP_PENDING”建模）：下一次确认释放完成为 CLEANED，配额只释放一次。
- 已提交历史与 active 永不作为孤儿释放。
- 真实 IN_COMMIT force-stop 后新 PID：reconcile=QUARANTINE_COMPLETE_ORPHAN→确认释放 CLEANED→再次释放被拒、配额不变、reconcile 稳定（`Gate2RestartAndroidTest`）。

证据等级区分：上述前三项为 REAL_SQLCIPHER + FAULT_INJECTION（异常注入或手工删除建模），最后一项为真实 force-stop + fresh PID。真实磁盘耗尽 `NOT_RUN_REAL_ENOSPC`：没有可证明不侵占 CI runner/用户设备空间的隔离存储预算，不模拟写满真实机器。

## 仍需负责人决定

保留期与是否允许周期 GC；确认主体与提示文案；外来路径预约是否允许释放（上文两种选项）；旧 epoch active 指针在 B3 恢复后的切换（B3）。
