# ADR：化验单（LabPanel / 一次采样）数据层（LAB-02 阶段 A，2026-10-18）

- 任务书：`docs/audits/product-quality-2026-10/sources/lab02/lab02-prompt.md`；REQUIREMENTS §112（阶段 A，以及负责人采纳推荐答案），§110 第 2 条（负责人对阶段 B 的批准）。
- 状态：**已采纳**。负责人 2026-10-18 看过本文草稿后回复“你推荐吧，lab02做完给我个apk”，即第 11 节的推荐答案全部采纳。负责人仍可随时推翻任一答案，推翻后按新答案修改。
- 适用范围：只设计数据层和界面草案。附件关联只写设计，不实施。

## 0. 现状（实施前事实）

| 项 | 现状 | 位置 |
|---|---|---|
| Room 版本 | 11 | `core/data/.../NotesDatabase.kt` |
| 迁移命名 | `migrationNToM`，注册在 `allMigrations(today)`；最新 `migration10To11` | `Migrations.kt` |
| 导出 schema | `core/data/schemas/net.plainnotes.app.data.NotesDatabase/1..11.json`（同时是测试资源） | `core/data/build.gradle.kts` |
| `lab_value` | `id`、`analyte_code`（FK → `lab_analyte.code`，RESTRICT）、`value`、`unit`、`sampled_utc`、`sampled_zone`、`reference_*`、`laboratory`、`note`；**没有 uuid** | `Entities.kt` |
| 上下文快照 | `lab_context_revision.lab_id` FK → `lab_value.id` ON DELETE CASCADE；`SchemaGuards` 禁止对它 UPDATE（只能追加修订） | `Entities.kt`、`SchemaGuards.kt` |
| 稳定身份 | `attachment_record_identity`（store_uuid, record_epoch, record_uuid, target_kind ∈ APPOINTMENT/LAB/DOSE_RECORD, legacy_id, state ACTIVE/TRASHED/PURGED, trash_id, evidence_digest）；PURGED 为终态墓碑，行不可删除 | `AttachmentEntities.kt`、`AttachmentSchema.kt`、`AttachmentIdentity.kt` |
| 回收站 | 化验是“快照类”：删除时 `lab_value` 行和全部上下文修订写入 `trash_item.payload_json` 后从表中删除；恢复按原整数 id 原样插回（CONFLICT_ABORT）；一个条目已可包含多张表的行 | `Trash.kt`、`NotesRepository.deleteLab/restoreTrash/purgeTrash` |
| 备份 | PNBAK1：magic `PNBAK1`、`FORMAT_VERSION = 1`、Argon2id 32 MiB / t3 / p1、AES-GCM；结构化序列化版本 `RecordBackupCompatibility.SCHEMA = 10`（与 Room 版本独立）；读到更高版本抛 `NewerBackup`（旧应用拒绝新备份，无降级）；每张表固定列清单 | `RecordBackupCompatibility.kt`、`DataTransfer.kt` |
| PK 读取 | `ConcentrationCalculator.compute(labs: List<LabValueEntity>)` 逐条过滤 E2 → `CalibrationEligibility.evaluate`（(id, 小时) 对）→ `LabFit` 只见 `LabResult`；`LabEstimate.capture(dao, lab)` 单条 | 各文件 |
| 时间线 | `LongitudinalProjection`、`PeriodTimeline` 每条化验一个 `EventKind.LAB` 事件，key `lab:{id}` | 同名文件 |
| 导入导出 | HRT Tracker 导入只写 E2 数值（`HrtTrackerWriter`，直接 `insertLab`，不建上下文）；Trans Memo 导入无化验；`Exports.kt` 的 `labs.csv` 与 PDF 化验表逐条；就诊资料包摘要 `VisitDigest` 对每条 `LabValueEntity.toString()` 求哈希 | 同名文件 |
| LAB-01 | 批量录入保存为多条普通记录；化验页按“同一 `sampled_utc` + 同一去空格机构”显示为一张卡，只是显示 | 报告 67 |

## 1. 模型

新表 `lab_panel`：

| 列 | 类型 | 说明 |
|---|---|---|
| `id` | INTEGER PK autoincrement | 本地整数 id，只在本库内使用 |
| `uuid` | TEXT NOT NULL UNIQUE | 稳定身份（随机 UUID v4，创建时生成，备份和回收站中保持不变） |
| `sampled_utc` | INTEGER NOT NULL | 采样时间 |
| `sampled_zone` | TEXT NOT NULL | 采样时区（IANA） |
| `laboratory` | TEXT NULL | 化验机构 |
| `note` | TEXT NULL | 整张化验单的备注 |
| `created_utc` | INTEGER NOT NULL | 创建时间（只用于审计和排序的稳定性） |

`lab_value` 增加 `panel_id INTEGER NULL REFERENCES lab_panel(id) ON DELETE RESTRICT`，并建索引 `index_lab_value_panel_id`。

决定与理由：

1. **不加 `deleted_at_utc`。** 化验在回收站中是“快照类”：删除时行进入 `trash_item` 负载并从表中移除。化验单沿用同一机制（第 5 节），表里不会出现“已删除”的行。加软删除列会让所有读取方都要多一个过滤条件，与现有化验不一致。
2. **外键 ON DELETE RESTRICT。** 删除化验单时，仓库在同一个事务里先把成员写入回收站负载并删除，再删除化验单。RESTRICT 保证任何遗漏都以失败告终（整体回滚），而不会悄悄留下孤儿或解组。没有选 SET NULL：它会让一个错误的删除变成“静默解组”。也没有选 CASCADE：它会绕过回收站和上下文快照。
3. **时间和机构冗余存放，加一致性校验。** 成员 `lab_value` 继续保存自己的 `sampled_utc`、`sampled_zone`、`laboratory`，并且必须与所属化验单完全相同。理由：
   - PK、校准、LabFit、导出、时间线、上下文快照全部逐条读取 `lab_value` 的这些列。不允许改这些读取方（任务书第 1 节第 3 条），所以不能改成“只从化验单读时间”。
   - 一致性由三处保证：
     - 新增 `SchemaGuards` 触发器：`lab_value` 插入或更新时，`panel_id` 不为空，就要求时间、时区、机构与 `lab_panel` 一致，否则 ABORT。
     - 仓库事务先改化验单，再逐条改成员。
     - `validateState` / `validateRestored` 检查全部成员一致。
   - 机构比较用原文。写入时统一去掉首尾空格后保存，所以与 LAB-01 的显示规则一致。
4. **不加 `specimen`（标本类型）字段。** 当前没有任何界面或导入来源能提供它，加了也只会长期为空；报告 39 也说明标本不是当前的 typed target。将来需要时再加一列可空字段，做非破坏性迁移。
5. **一条数值最多属于一张化验单**（单个 `panel_id`）。不做多对多。

## 2. 迁移 11 → 12

`migration11To12`（非破坏性，只新增）：

```sql
CREATE TABLE IF NOT EXISTS `lab_panel` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uuid` TEXT NOT NULL,
  `sampled_utc` INTEGER NOT NULL, `sampled_zone` TEXT NOT NULL, `laboratory` TEXT, `note` TEXT, `created_utc` INTEGER NOT NULL);
CREATE UNIQUE INDEX IF NOT EXISTS `index_lab_panel_uuid` ON `lab_panel` (`uuid`);
ALTER TABLE `lab_value` ADD COLUMN `panel_id` INTEGER REFERENCES `lab_panel`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT;
CREATE INDEX IF NOT EXISTS `index_lab_value_panel_id` ON `lab_value` (`panel_id`);
```

- 只用 `ALTER TABLE … ADD COLUMN`，不重建 `lab_value`，所以已有行、整数 id 和 `lab_context_revision` 的外键都不动。
- 新列默认 NULL：**迁移后所有旧数值的 `panel_id` 为空，不自动归组**（遵守“范围不能自动构造 LabPanel”和“同日不同机构不合并”）。
- Room 对 12 的 schema 校验必须接受这一形状。阶段 A 用合成 v11 库验证 SQL；阶段 B 用真实 `MigrationTestHelper` 和导出的 `12.json` 验证。
- 失败回滚：迁移在 Room 的事务里执行，任何一步失败都会让数据库保持 v11。阶段 B 要仿照 `ReferenceMigrationContract` 中 10 → 11 的回滚测试，补一个 11 → 12 的回滚测试。

## 3. 把旧记录归入化验单

- 只能由用户明确操作。没有化验单、但按 LAB-01 规则显示成多项卡的旧记录，卡片上提供“保存为一次化验”。
  - 确认对话框列出将要归入的项目（项目、数值、单位）以及时间和机构，用户确认后才写入。
  - 写入在一个事务里完成：新建化验单，时间、时区、机构取自这些记录（它们本来就相同），然后为每条设置 `panel_id`。
  - 数值的时间没有改变，所以**不追加**上下文修订。
- 不在后台批量执行，不在迁移中执行，不按日期接近或同一天归组，也不推断与就诊的关联。
- 只有一条的旧记录不显示这个按钮（一条就是它本身）。

## 4. 编辑语义

- **整体编辑化验单的时间、时区或机构**：在一个事务内先更新化验单，再按现有的 `writeLab` 逐条更新成员。
  - 成员的采样时间或时区改变时，按现有规则追加一条 `SAMPLE_CHANGED` 上下文修订，保留旧修订、不改写历史。
  - 只改机构不触发新修订，与现有单条编辑一致：机构不在 `sampleChanged` 判断里。
- **整体编辑备注**：只改 `lab_panel.note`。
- **增删项目**：
  - 添加：新数值继承化验单的时间、时区和机构，按 `AT_ENTRY` 捕获上下文，与批量录入相同。
  - 移除：只把 `panel_id` 置空，数值保留，作为单条记录显示。
  - 把成员删除到回收站：见第 5 节。
- **单独修改某一条的时间或机构**：**推荐自动移出化验单**（`panel_id` 置空并在同一事务保存）。保存前沿用 LAB-01 已有的提示“修改时间或机构后，这一项会从这次化验中单独显示”。理由：
  1. 与用户在 LAB-01 已经看到的行为一致；
  2. 化验单的时间是“一次采样”的定义。单条时间不同，本来就说明它不属于这次采样；
  3. 禁止修改会迫使用户先移出再修改，多一步，而且容易让人误以为必须整张一起改。
- 单独修改数值、单位、参考范围、备注：照旧，不影响化验单。
- **化验单只剩 0 个成员时**（全部移出）：在同一事务里删除空化验单，不进回收站（它没有数据可恢复）。

## 5. 删除与回收站

- 新增快照类回收站种类 `LAB_PANEL`。需要同步修改 `SchemaGuards` 的种类检查和 `BackupValidation` 的负载检查。
- **整体删除化验单**：一个回收站条目，`ref` 为化验单 uuid，日期为采样当地日期。负载按父在前的顺序包含：
  - `lab_panel` 行；
  - 全部成员 `lab_value` 行；
  - 全部成员的 `lab_context_revision` 行。

  这些行一起恢复、一起彻底删除。
- **化验单内的单条删除**：沿用现有 `LAB` 条目，负载额外记录它当时所属化验单的 uuid。
- **恢复**：
  - 整体恢复：按原样插回，化验单 uuid 不变。
  - 单条恢复：按负载中的**化验单 uuid** 查找化验单（不按整数 `panel_id`）。
    - 找到且时间、机构仍然一致：重新归入。
    - 找不到，或时间、机构已不一致：作为单条记录恢复（`panel_id` 为空），并在恢复结果中说明。
  - 附件身份照旧由 `AttachmentIdentity.restored` 按 `trash_id` 和摘要处理。
- **彻底删除**：整体条目一起清除；附件身份按现有规则写 PURGED 墓碑。
- 删除化验单的最后一个成员时，空化验单与该成员写入同一个 `LAB` 条目的负载，恢复时一起回来。

## 6. 备份与恢复

- 结构化序列化版本 `RecordBackupCompatibility.SCHEMA` 10 → 11：
  - `columns` 增加 `lab_panel`；
  - `lab_value` 列清单增加 `panel_id`；
  - `DOMAIN_TABLES` 中 `lab_panel` 排在 `lab_value` 之前；
  - `restoreBackup` 的必需表检查对 `schema >= 11` 要求 `lab_panel`。
- 旧版本（1–10）备份恢复：没有 `lab_panel`，`lab_value` 行里也没有 `panel_id`。恢复后 `panel_id` 全部为空，不按时间、机构或整数 id 自动归组。
- 新备份（11）被旧版应用读取：旧应用的 `readVersion` 抛 `NewerBackup` 并拒绝，提示需要更新应用。这是现有行为，不做降级。理由：降级必然丢掉化验单这一层，用户却以为恢复成功。
- 恢复失败的回滚：沿用现有“先在内存暂存库恢复并校验，成功后再替换”的流程。新增的一致性校验（成员与化验单时间、机构一致，uuid 唯一，外键完整）放在 `validateRestored`。
- **PNBAK 加密参数一律不改**：magic、`FORMAT_VERSION`、Argon2id 参数、GCM nonce、tag 和附加数据都不变。变化只在加密内部的 JSON 结构版本。

## 7. 导入导出

- HRT Tracker 导入：继续只写普通 E2 数值，`panel_id` 为空，不建化验单。
- Trans Memo 导入：没有化验数据，不受影响。
- 现在并没有一个“HrtTrackerWriter 导出”：`HrtTrackerWriter` 是导入写入器，代码里也没有按 HRT Tracker 格式导出化验的功能。这里如实说明，无需处理。
- `Exports.kt` 的 `labs.csv` 和 PDF 化验表：本任务不改（受保护文件），仍然逐条导出。按化验单分组的 PDF 另立任务。
- 就诊资料包摘要（`VisitDigest`）：当前对 `LabValueEntity.toString()` 求哈希。给实体加 `panel_id` 会改变每条化验的字符串，已生成资料包的摘要会全部变成“内容已变化”。**推荐**阶段 B 在摘要中使用“去掉 `panel_id` 的旧字段清单”，让已有资料包的摘要保持不变。这需要改 `VisitPack.kt`（不在保护矩阵），并用测试证明 v11 与 v12 下同一组数据的摘要相同。

## 8. 将来与附件的关系（只设计）

- 化验单作为附件链接的 typed target，需要在 `attachment_record_identity.target_kind` 中新增 `LAB_PANEL`。`legacy_id` 填 `lab_panel.id`，`record_uuid` 用化验单自己的 uuid（不另造）。
- 回收站流程 `trashed`、`restored`、`purged` 已按 `trash_id` 工作，整体条目可以直接沿用。
- 新的 kind 需要附件协议版本和 AttachmentSchema 迁移。**本任务不实施，也不改附件协议，不开放附件功能。**

## 9. PK 不变性

- 浓度、校准资格、LabFit、导出 PDF 读取的都是 `List<LabValueEntity>`，逐条使用 `id`、`analyte_code`、`value`、`unit`、`sampled_utc`（以及导出用的范围、机构、备注）。化验单只新增 `panel_id` 一列，不改变这些列的值，也不增删行。
- 一致性触发器保证成员的时间、机构与化验单相同。编辑化验单时，成员的值按与单条编辑相同的 `writeLab` 更新。
- 证明方法：
  - 阶段 A：同一组合成数据，一份不分组，一份按草案分组（只在测试中给数值附加化验单）。对比浓度计算的化验点、校准资格、E2 曲线和校准条数，逐项相同。
  - 阶段 B：在真实 v12 库中，分别用“建化验单”和“不建化验单”两种方式保存同一组数据，再比较同样的结果，并比较导出的 CSV/PDF 化验表文本。

## 10. 界面草案

- **化验页**：
  - “全部”视图中，有化验单的显示为化验单卡片：标题是“日期时间 · 机构 · n 项”，下面是内嵌列表，可选显示备注。
  - 没有化验单的旧记录仍按 LAB-01 规则显示。其中的多项卡额外提供“保存为一次化验”。
  - 选单个项目时的趋势图和逐条记录不变。
- **化验单详情**（点卡片标题）：
  - 时间、机构、备注可编辑（整体生效）；
  - 成员列表：点一行进入现有单条编辑；
  - 复用 LAB-01 的行组件添加项目；
  - 从化验单移除某一项；
  - 整体删除（进回收站）。
- **批量录入**：保存时在同一事务内同时创建化验单，原子性不变。
- **“保存为一次化验”确认**：列出项目、时间和机构，提供“保存为一次化验”和“取消”。
- **时间线**：推荐阶段 B **不合并**，仍然逐条显示，`LongitudinalProjection` 和 `PeriodTimeline` 不改。合并显示（“化验 · 6 项”）另立任务。

## 11. 需要负责人决定的问题与推荐答案（已采纳）

负责人 2026-10-18 采纳以下全部推荐答案（“你推荐吧”）；阶段 B 按此实施，负责人可随时推翻：

| # | 问题 | 推荐答案 | 理由（摘要） |
|---|---|---|---|
| Q1 | 单独修改某一条的时间或机构：禁止，还是自动移出化验单？ | **自动移出**，保存前提示 | 与 LAB-01 行为一致；单条时间不同本来就不是同一次采样 |
| Q2 | 时间线是否把一张化验单合并为一个事件？ | **不合并**（逐条显示，时间线代码不改） | 降低风险，合并显示另立任务 |
| Q3 | 外键删除策略 | **RESTRICT**，仓库先删成员再删化验单 | 遗漏时失败回滚，不静默解组，不绕过回收站 |
| Q4 | 时间和机构：只存在化验单，还是冗余存放加一致性校验？ | **冗余加校验**（触发器 + 恢复校验） | PK 等读取方逐条读取，不得修改 |
| Q5 | 是否加 `specimen` 字段 | **不加** | 无来源、会长期为空；将来可做非破坏性加列 |
| Q6 | 批量录入只有 1 项时是否也建化验单 | **建**（批量表单保存的一律是一次化验） | 之后可在详情中添加项目；行为可预期 |
| Q7 | 只有 1 个成员的化验单如何显示 | **显示为化验单卡片**（“1 项”） | 详情和“添加项目”入口要可达 |
| Q8 | 新备份被旧应用读取 | **拒绝**（现有 `NewerBackup`），不降级 | 降级会悄悄丢掉化验单 |
| Q9 | 单条从回收站恢复时原化验单已不在或已改时间 | **作为单条恢复**并说明 | 不按整数 id 或日期重新关联 |
| Q10 | 就诊资料包摘要 | **用去掉 `panel_id` 的字段计算**，已生成资料包的摘要不变 | 避免升级后所有资料包显示“已变化” |
| Q11 | 化验单备注 `note` | **保留**，在详情中编辑 | 一次采样常有共同说明（如空腹、周期第几天） |
| Q12 | “保存为一次化验”的入口 | **只在 LAB-01 多项卡上**，确认后写入 | 不在后台、不按日期推断 |

## 12. 风险清单

| 风险 | 影响 | 缓解 |
|---|---|---|
| 成员与化验单的时间或机构不一致 | PK 与界面看到的时间不同 | 触发器、恢复校验，所有写入走同一事务 |
| `VisitDigest` 因实体字符串变化而改变 | 已有资料包显示“已变化” | Q10，并加测试 |
| 回收站负载格式变化 | 旧负载不能恢复 | 新字段可选；旧 `LAB` 负载照常恢复为单条；`BackupValidation` 覆盖新旧两种 |
| 备份版本 +1 | 旧应用不能读新备份 | 现有 `NewerBackup` 提示；不改加密参数 |
| 迁移失败 | 应用无法打开 | 只做加表加列；有回滚测试；在 API 26/29/35 做设备迁移和进程重启测试 |
| 外键 RESTRICT 让删除失败 | 用户删不掉 | 仓库始终先删成员；测试覆盖整体删除和单条删除 |
| `LabsScreen.kt` 受保护 | 需要批准记录 | 阶段 B 追加 `PD-…-LAB02B-LABSSCREEN`，负责人批准见 §110 |

## 13. 阶段 B 文件清单（预计）

- `core/data`：
  - `Entities.kt`：`LabPanelEntity`，`LabValueEntity.panel_id`；
  - `NotesDatabase.kt`：版本 12，新实体；
  - `Migrations.kt`：`migration11To12`；
  - `schemas/…/12.json`；
  - `SchemaGuards.kt`：一致性触发器，回收站种类；
  - `NotesDao.kt`；
  - `NotesRepository.kt`：化验单的增、改、删、恢复，`saveLabs` 建化验单，“保存为一次化验”；
  - `Trash.kt`；
  - `RecordBackupCompatibility.kt`：版本 11 与列；
  - `DataTransfer.kt`：`DOMAIN_TABLES`；
  - `BackupValidation.kt`。
- `app`：
  - `NotesViewModel.kt`；
  - `ui/LabBatchEntry.kt`：卡片、详情、“保存为一次化验”；
  - `ui/LabsScreen.kt`：受保护，需登记批准；
  - `ui/ShellHosts.kt`；
  - `visit/VisitPack.kt`：Q10；
  - 四语文案。
- 不改：`LabEstimate.kt`、`CalibrationEligibility.kt`、`LabFit.kt`、`ConcentrationCalculator.kt`、`Exports.kt`、`LabContextSection.kt`、`LongitudinalProjection.kt`、`PeriodTimeline.kt`、附件协议、PNBAK 加密参数。

## 14. 阶段 B 测试清单

- 迁移：
  - 真实 `MigrationTestHelper` 11 → 12，以及 1..10 → 12 的全路径；
  - 迁移失败回滚；
  - 迁移后 `panel_id` 全部为空，数值、上下文、回收站、稳定身份不变；
  - `PRAGMA foreign_key_check` 为空。
- 备份：
  - v11 新格式往返；
  - v1..v10 旧格式恢复后 `panel_id` 为空；
  - 用 `supported = 10` 读取 v11 被拒绝；
  - 一致性被破坏的备份恢复失败且原数据不变。
- 回收站：
  - 整体删除、恢复、彻底删除；
  - 单条删除、恢复后重新归入；
  - 原化验单不在时作为单条恢复；
  - 删除最后一个成员。
- 编辑：
  - 整体改时间：成员同步，追加 `SAMPLE_CHANGED`，旧修订保留；
  - 整体改机构：无新修订；
  - 单条改时间时自动移出（Q1）；
  - 触发器拒绝不一致的写入。
- PK 不变性：有无化验单两种情况下，浓度化验点、校准资格、曲线、导出 CSV/PDF 化验表逐条相同；`VisitDigest` 不变（Q10）。
- UI：
  - 四语 × 320/411dp × 字号 1/2 × 明暗截图；
  - TalkBack；
  - “保存为一次化验”的确认与取消；
  - 化验单详情的各项操作。
- 设备测试：API 26、29、35 上的迁移与进程重启。
- 必须通过：`PeriodStabilityTest`，全部现有化验、备份、回收站、迁移测试，PK 显示回归和全部 CI。
