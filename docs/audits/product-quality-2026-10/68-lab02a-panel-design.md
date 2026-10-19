# 68 — LAB-02 阶段 A：化验单数据层设计与合成验证（2026-10-18）

- 任务书：`sources/lab02/lab02-prompt.md`（SHA-256 `b8727135…1c1718`）；REQUIREMENTS §112（阶段 A，以及负责人采纳推荐答案）、§110 第 2 条（阶段 B 的批准）。
- 分支：`feature/lab02a-panel-design-20261018`。
- 本 PR 只含文档、测试和测试专用代码：不改生产 Room 实体、迁移、DAO、备份代码和 UI。草案类只在 test 源集，不会进入 release 构建。

## 1. ADR 摘要

全文见 `docs/design/lab-panel-adr.md`。

- **新表与新列**：
  - 新表 `lab_panel`：`id`、唯一 `uuid`、`sampled_utc`、`sampled_zone`、`laboratory`、`note`、`created_utc`。没有软删除列：化验单像化验一样用回收站快照删除。
  - `lab_value.panel_id` 可为空，外键 RESTRICT，并建索引。
- **一致性**：时间、时区、机构在化验单和成员上各存一份，因为 PK、校准、导出、时间线和上下文快照都逐条读取 `lab_value`，不得修改这些读取方。一致性由触发器、恢复校验和单一事务保证。不加 `specimen`。
- **迁移**：Room 11→12 只做 `CREATE TABLE` 和 `ALTER TABLE … ADD COLUMN`，旧数值的 `panel_id` 全部为空，不自动归组。
- **旧记录归组**：只能由用户在 LAB-01 的多项卡上点“保存为一次化验”并确认，不在后台进行。
- **编辑**：
  - 整体改时间时，成员同步，并按现有规则追加 `SAMPLE_CHANGED` 上下文修订；
  - 单条改时间或机构时，自动移出化验单（Q1）；
  - 化验单成员为 0 时，删除空化验单。
- **回收站**：
  - 新种类 `LAB_PANEL`，整张化验单和全部成员、上下文作为一个条目；
  - 单条仍按 `LAB` 删除和恢复，按化验单 **uuid** 重新归入，找不到就作为单条恢复（Q9）。
- **备份**：
  - 结构化版本 10→11，新增 `lab_panel` 和 `lab_value.panel_id`；
  - 旧备份恢复后 `panel_id` 为空；
  - 旧应用读到新备份时用现有的 `NewerBackup` 拒绝；
  - PNBAK 加密参数不变。
- **导入导出**：
  - HRT Tracker 导入不建化验单；
  - 仓库里并没有 “HrtTrackerWriter 导出” 功能，`HrtTrackerWriter` 是导入写入器，已如实说明；
  - `Exports.kt` 不改；
  - `VisitDigest` 计算时去掉 `panel_id`（Q10），避免已生成的资料包全部显示“已变化”。
- **附件**：将来新增 `attachment_record_identity` 的 kind `LAB_PANEL`。本任务只写设计，不实施。
- **时间线**：不合并显示（Q2），`LongitudinalProjection` 和 `PeriodTimeline` 不改。

## 2. 待决问题与答复

ADR 第 11 节 Q1–Q12。负责人 2026-10-18 看过草稿后回复“你推荐吧，lab02做完给我个apk”，推荐答案全部采纳（REQUIREMENTS §112 第 2 条）。负责人可随时推翻任一答案。

## 3. 合成验证（只在测试中运行）

| 测试 | 内容 | 结果 |
|---|---|---|
| `core:data` `LabPanelDraftTest.draftMigrationKeepsEveryRowAndLeavesPanelIdNull` | 用导出的 v11 schema 建合成库（数值、冻结上下文、回收站中的化验、SchemaGuards），执行草案 11→12。所有 v11 表的每一行逐列不变；`panel_id` 全为空；`lab_panel` 为空；`foreign_key_check` 通过；外键 `lab_panel:RESTRICT` 和 `lab_analyte:RESTRICT` 都在；上下文仍不能改写 | 通过 |
| `…aFailingDraftMigrationRollsBackToV11` | 迁移事务中途失败，库完整回到 v11，没有 `lab_panel` | 通过 |
| `…draftGuardKeepsMembersConsistentWithTheirPanelAndRestrictBlocksOrphans` | 一致性触发器拒绝成员单独改时间、改机构，也拒绝插入与化验单不一致的成员；RESTRICT 拒绝删除有成员的化验单；先移出再改时间可以（Q1） | 通过 |
| `…draftBackupVersionIsRejectedByTheCurrentReaderAndOldBackupsHaveNoPanels` | 现有 `readVersion` 对版本 11 抛 `NewerBackup`（旧读取器拒绝新备份）；草案列清单只增不减；旧备份行没有 `panel_id` | 通过 |
| `app` `Lab02PanelPkInvarianceTest` | 在真实 Room v11 库下执行草案 SQL，把一次采样的 3 条数值归入化验单。归组前后逐项相同：化验行、浓度化验点、校准资格、校准条数、E2 曲线、时间轴、当前值，以及每条的 `LabEstimate.capture` 结果 | 通过 |

说明：

- app 测试看不到 `core:data` 的测试类，所以 `Lab02PanelPkInvarianceTest` 里复制了同一段草案 SQL，并注明了出处。
- 阶段 B 会用生产迁移和真实 `MigrationTestHelper` 取代这两段草案。

## 4. 风险清单

见 ADR 第 12 节。主要风险：

- 成员与化验单不一致：由触发器和恢复校验防止；
- `VisitDigest` 变化：Q10；
- 回收站新旧负载：新字段可选；
- 备份版本 +1：旧应用拒绝新备份；
- 迁移失败：只加表加列，并有回滚测试；
- `LabsScreen.kt` 受保护：阶段 B 追加批准记录。

## 5. 阶段 B 文件清单与测试清单

见 ADR 第 13、14 节。

## 6. 状态

阶段 B 已获负责人批准（§110 第 2 条，并按 §112 采纳推荐答案），本 PR 合并后直接开始。阶段 B 合并且主线 CI 全绿后，出 build 32。
