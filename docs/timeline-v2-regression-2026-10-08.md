# Build 25 时间线与历史一致性核对

基线 `5441e6efac2a9dcb3b38c0a3fb2b85b4d3c2880e`，Build 25/schema 9。最新规则为 REQUIREMENTS §40 和 [V2 设计](design/timeline-editing-v2.md)，本轮 §41 要求针对性回归。没有复现周期数据一致性缺陷，未修改引擎、DAO、Schema、迁移或备份格式。

| 核对项 | 实际证据与边界 |
| --- | --- |
| 历史时期创建、编辑、删除、恢复 | `TimelineV2FlowTest`：系统邻接拆分成固定用户时期、缩短用户时期后系统背景重现、删除恢复原身份 |
| 用户时期不会自动合并 | `TimelineV2Test.equalUserAndSystemStandardsKeepAllUserBoundaries`：同标准用户/系统以及相邻用户边界仍独立 |
| 同药重叠 | `TimelineV2FlowTest.fullyCoveredPeriodGoesToBinAndCannotRestoreOverANewUserPeriod`，`TimelineEdits.saveV2` 的 overlap changes；完整覆盖入回收站，恢复不能覆盖新用户时期 |
| 自动/用户区别 | `TimelineV2Test.systemRecognitionIsEffectiveWithoutConfirmation`：系统识别有独立 SpanKind/覆盖证据；用户时期保存明确标准和边界；不把导入间距当原始处方 |
| “至今”结束 | `ongoingEndsAtAPlanChangeSavedAfterTheUserStatement`：声明之后新保存的标准变化结束用户时期；声明时已经知道的版本（包括未来日期）不擅自结束；`deletingAnOngoingPeriodDoesNotExtendItPastTheSavedChange` 保持准确删除边界 |
| 旧时期兼容 | `migrationPreservesMergedDisplayedBoundsAndIsIdempotent`、`migrationKeepsSeveralExactChangesInOneCivilDayOnOneCard`；Flow 的旧确认数据转换一次、备份恢复和旧回收站恢复 |
| 回收站关联 | `TrashDataTest`、`TimelineEditFlowTest`、V2 Flow；旧删除范围转换背景可恢复，不制造无效引用 |
| 事实不被时期编辑重写 | 本轮新增 `timelineEditsDoNotRewriteFrozenFactsStockOrReminderMappings`：真实调用创建/修改/删除/恢复，逐步比较 15 表序列化内容；含原始记录、库存容器/流水、规则与时间、冻结方案、化验/完整参数估算上下文、Visit Pack 不可变生成记录、提醒映射与 PK 设置 |
| Schema 正式迁移 | `MigrationBaselineTest`：导出 v1/v3/v4/v5/v6/v7/v8 到 v9（含 SQLite 约束与原事实检查）；v2 经 allMigrations 链，独立 v2 fixture 不在现有测试里。本轮 Schema 不变，不新增迁移 |
| 非均匀剂量时刻/改期/覆盖 | `RegimenHistoryTest.nonuniformReminderMovePreservesSlotDosesFrozenVersionsAndBackupLinks`：1mg/2mg 时刻改动仍对应、旧定义不变、备份链接一致；缺少对应关系的改时调用拒绝且原库不变。`ScheduleTest`：DST 保持名义身份、改期跨窗口可找到、跳过/撤销覆盖和冻结计划事实 |

新增测试中的 PDF 证据是冻结生成元数据/事实摘要，未声称检查用户外部 SAF 文件字节；外部 PDF 内容不由时间线编辑重写。PK 冻结化验估算和参数文档被严格比较，普通历史曲线的当前体重/当前引擎策略是既定边界，不是已经实现完整模型回放。

旧规则的稳定业务 slot identity、无法识别的旧非均匀剂量如何人工对齐，是待决定的产品/迁移规则。未发现可复现缺陷时不重写周期或数据库。真实用户数据库、正式覆盖安装和 OEM 行为未在此测试，仍列入设备验收；所有测试数据均为合成。

本轮 V2单测6、Flow6、PeriodStability5、旧TimelineEditFlow4、data72全部通过；[CI 37849334335](https://github.com/DevenirTwilight/HRT-Log/actions/runs/37849334335)（297daca）三个任务success，API35迁移/加密存储14项通过，报告已下载核对。运行结果及边界见 [HANDOFF](HANDOFF.md) 最新章节；常规 CI 包含上述 JVM/Android 单测，API35 模拟器运行实际迁移测试，不等于真机验收。
