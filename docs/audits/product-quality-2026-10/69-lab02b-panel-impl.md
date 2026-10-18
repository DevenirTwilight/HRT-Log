# 69 — LAB-02 阶段 B：化验单数据层与界面（生产实施）（2026-10-18）

- 依据：
  - REQUIREMENTS §110 第 2 条：负责人批准阶段 B；
  - §112：采纳 ADR 推荐答案，阶段 B 合并后出 build 32；
  - ADR `docs/design/lab-panel-adr.md`；
  - 阶段 A 报告 68。
- 分支：`feature/lab02b-panel-impl-20261018`。
- Room 11 → 12，PNBAK 结构化版本 10 → 11。PNBAK 的 magic、`FORMAT_VERSION`、Argon2id、GCM 参数都不变。

## 1. 改动文件

| 文件 | 类别 | 改动 |
|---|---|---|
| `core/data/Entities.kt` | 矩阵外 | `LabPanelEntity`；`LabValueEntity.panel_id`（可空，外键 RESTRICT，加索引）；`legacyString()`（Room 11 时的文本，供资料包摘要使用，Q10） |
| `core/data/NotesDatabase.kt`、`Migrations.kt`、`schemas/12.json` | 矩阵外 | 版本 12；`migration11To12` 只做 `CREATE TABLE`、`ALTER TABLE ADD COLUMN` 和建索引 |
| `core/data/SchemaGuards.kt` | 矩阵外 | 两组新触发器：成员的时间、时区、机构必须与所属化验单完全一致；化验单本身的有效性（uuid、时区、机构不能只含空格）。只在 `lab_panel` 表存在时安装；`validateRestored` 同样检查。回收站种类新增 `LAB_PANEL` |
| `core/data/NotesRepository.kt` | 矩阵外 | `saveLabs` 同时建化验单（Q6）；新增 `saveLabPanel`、`addToLabPanel`、`removeFromLabPanel`、`groupAsLabPanel`、`deleteLabPanel`；`writeLab` 处理单条修改时自动移出（Q1）和空化验单的删除；`deleteLab` 让成员记住化验单 uuid，最后一条会带走空化验单；`restoreTrash` 按 uuid 重新归入（Q9）；`restoreBackup` 拒绝自称旧版本、却带有化验单的备份 |
| `core/data/Trash.kt`、`AttachmentIdentity.kt` | 矩阵外 | `LAB_PANEL` 快照种类；整张化验单删除时，每条成员的附件身份随同一个回收站条目变化。附件协议不变，附件功能仍关闭 |
| `core/data/RecordBackupCompatibility.kt`、`DataTransfer.kt` | 矩阵外 | `SCHEMA = 11`；`lab_panel` 和 `lab_value.panel_id` 列；`DOMAIN_TABLES` 中 `lab_panel` 排在 `lab_value` 之前；为旧版本构造测试备份时，`dump` 跳过表里还没有的列（正式导出仍要求所有列都存在） |
| `app/NotesViewModel.kt` | 矩阵外 | `ExtraState.labPanels` 和五个化验单操作 |
| `app/visit/VisitPack.kt` | 矩阵外 | 摘要使用 `legacyString()`，已生成资料包的摘要不变（Q10） |
| `app/ui/LabBatchEntry.kt` | 矩阵外 | `labDraws`：真实化验单优先，其余结果仍按 LAB-01 规则显示；卡片显示备注、标题可点开详情、“保存为一次化验”按钮 |
| `app/ui/LabPanelDetail.kt`（新） | 矩阵外 | 化验单详情（整体改时间、机构、备注；成员可打开或移出；新增项目时同一化验单内不能重复；整体删除需确认）和“保存为一次化验”确认对话框 |
| `app/ui/LabsScreen.kt` | **PK_DISPLAY_REVIEW** | 新增 `panels`、`onPanel`、`onSaveAsPanel` 参数，列表改用 `labDraws`（+6 / −4 行），已登记批准记录 |
| `app/ui/ShellHosts.kt`、`AppShell.kt`、`RecycleBin.kt` | HISTORY_ONLY / 矩阵外 | 接线；回收站显示“化验单 · n 项”；最后一条成员的回收站标题按 `lab_value` 表读取 |
| 四语文案 | — | 12 条 |

不改的文件：`LabEstimate`、`CalibrationEligibility`、`LabFit`、`ConcentrationCalculator`、`Exports`、`LabContextSection`、`LongitudinalProjection`、`PeriodTimeline`（时间线不合并显示，Q2），以及 PNBAK 加密参数和附件协议。

## 2. 批准记录

- 文件：`docs/audits/product-quality-2026-10/display-approvals-after-archive.json`。研究归档中的 `display-approvals.json` 已冻结，见 #79。
- id：`PD-2026-10-18-LAB02B-LABSSCREEN`。
- 修改前哈希 `27e2d80b…`（LAB-01 之后），修改后哈希见该文件。
- 证据：本分支提交中 REQUIREMENTS §112 第 5 条含此 id。

## 3. 测试

| 测试 | 内容 |
|---|---|
| `LabPanelDataContract`（Robolectric 的 `LabPanelDataTest`；设备上的 `LabPanelDataAndroidTest`，CI 在 API 26/29/35 运行） | Room 11→12：所有旧行逐列不变，`panel_id` 全为空，不归组，外键完整，上下文仍不能改写；迁移失败时留在 11。整体编辑：成员同步、追加 `SAMPLE_CHANGED`、旧修订保留，只改机构时不追加修订。单条修改：只改数值时留在化验单，只差空格时留下，改时间或机构时移出（Q1），化验单空了就删除。新增项目沿用化验单的时间等字段；数据库拒绝不一致的成员和 RESTRICT 删除。“保存为一次化验”：只接受未归组、且采样完全相同的结果，不追加上下文。回收站：整张化验单一起删除、恢复、彻底删除；单条按 uuid 回到化验单，化验单已改时间时作为单条恢复；最后一条会带走空化验单，并一起恢复。备份：v11 往返；v10 备份恢复后不归组；声称 v10 却带化验单的备份被拒绝；成员不一致的备份被拒绝且原数据不变；v10 读取器拒绝 v11（Q8）；`legacyString` 与 Room 11 的文本一致 |
| `LabPanelRestartAndroidTest` + `scripts/run_lab_panel_restart.py`（CI 在 API 35 和 26/29 运行） | 在两个独立进程之间：化验单、回收站中的化验单、加密备份都保留；从回收站恢复整张化验单；从备份恢复后完全一致 |
| `Lab02PanelUiTest` | 化验单显示为卡片（1 项也是），标题可打开详情；没有化验单的多项卡有“保存为一次化验”；确认对话框；详情中的保存、打开、移出、新增时防重复、整体删除确认；`labDraws` 的排序与归属 |
| `Lab02PanelPkInvarianceTest` | 改为走正式路径：批量保存建化验单，以及逐条保存后“保存为一次化验”。归组前后，浓度化验点、校准资格、校准条数、E2 曲线、时间轴、当前值、每条估算逐项相同 |
| `Lab01UnaffectedTest`、`LabBatchSaveTest` | 断言迁移见第 4 节 |

阶段 A 的 `LabPanelDraftTest` 是草案 SQL 的测试，已删除。它覆盖的内容由上面针对正式代码的测试接替：迁移、回滚、触发器和备份版本。

## 4. 因版本升级与化验单而迁移的既有断言（逐条，未删除任何断言）

| 测试 | 原断言 | 现在 | 理由 |
|---|---|---|---|
| ReferenceMigrationContract、AttachmentMigrationContract、AttachmentFoundationContract、Ansm03b0InventoryContract、HistoricalVisitContract、MigrationBaselineTest、AttachmentFoundationRestartAndroidTest、UndoSkippedRestartAndroidTest | Room 版本 `11` | `12` | 当前版本 |
| 同上，以及 ReferencePersistenceContract、Gate2StoreAndroidTest | 备份结构版本 `10` | `11` | 当前版本 |
| Ansm03b0InventoryContract、ReferencePersistenceContract | 把 `11` 当作“更新的版本”，期望被拒绝 | 改为 `SCHEMA + 1` | 本意是测试“比当前更新的版本被拒绝” |
| AttachmentMigrationContract、MigrationBaselineTest、ReferenceMigrationContract（注入失败的那项） | `addMigrations(… migration10To11)` | 加上 `migration11To12` | 需要迁移到当前版本 |
| ReferenceMigrationContract 的 Room 10 指纹、AttachmentMigrationContract 的 Room 9 指纹 | 按 `SELECT *` 和完整 DDL 逐字节比较 | 只比较旧版本已有的列，并且只排除这一个确切的列子句 `, \`panel_id\` INTEGER REFERENCES …` 和 `index_lab_value_panel_id` 索引 | 旧列和其余 DDL 仍然逐字节比较；`panel_id` 全为空由 LabPanelData 测试单独断言 |
| AttachmentFoundationContract | 备份含 35 张表 | 36 张 | 新增 `lab_panel` |
| AttachmentMigrationContract：构造 Room 9 备份 | 导出全部非参考表 | 同时去掉 `lab_panel`（Room 9 没有这张表） | 构造旧版本备份时只用当时存在的表 |
| LabBatchSaveTest | 批量保存的记录与逐条保存逐字段相等 | 去掉 `panel_id` 后逐字段相等，并断言恰有一张化验单、全部成员属于它、化验单的时间和机构与成员一致 | Q6 |
| Lab01UnaffectedTest | 两库的行、时间线事件和范围查询完全相等 | 去掉 `panel_id` 后相等（时间线中的 Lab 事件携带行数据）；PK 结果仍逐项相同；另断言批量保存一侧有两张化验单 | 同上 |

## 4.1 本地回归

- 完整回归：
  - `core:reminder` 20 项；
  - `core:data` 261 项，0 失败；
  - `app:testFullDebugUnitTest` 1634 项。唯一失败是 `PrivacyNoticeFactsTest`：它固定检查备份结构版本，已更新为 11，`PRIVACY.md` 和 `docs/privacy-evidence.md` 中原本过时的“serialization 9”也一并改为 11。随后定向复跑通过。
  - 跳过 237 项，都是既有的默认关闭用例。
- 修复行布局后，`*Lab0*` 共 172 项复跑通过。
- `lintFullDebug` 通过；app 和 core:data 的设备测试编译通过。
- 设备上的迁移和进程重启以 CI 为准：API 35 跑完整设备测试，API 26/29 跑兼容任务。

## 5. NOT_RUN

- 真机清单第 11 节的化验单部分（整体编辑、保存为一次化验、回收站），由负责人回填。
- 国产 ROM。
- 真机 TalkBack。
