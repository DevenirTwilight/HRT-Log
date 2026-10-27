# MED-03：整次化验（LabPanel）附件目标 — 需求规格（2026-10-27）

**授权。** 负责人 2026-10-27：“授权 MED-03 做整次化验目标”（REQUIREMENTS §158）。本文是给 MED-03 实施方（Codex）的需求交接，由 UI-10 在核对代码后写成；**UI-10 不改存储、Room schema、PNMEDBK 或备份恢复**。下面的“当前代码事实”是 UI-10 读代码得到的，实施方开工前须自行复核。

## 1. 要解决的问题

一次化验（一次抽血）有多项结果，一份报告 PDF 对应整次，而 MED-02 的化验附件目标只能是单个 `LabValue`：一份 5 项报告只能挂在其中一项上，其他 4 项的页面看不到它。要让“整次化验”成为附件的主要目标。

## 2. 当前代码事实（UI-10 核对于 main `019c805d`）

- `attachment_record_identity.target_kind` 有数据库约束：`CHECK(target_kind IN('APPOINTMENT','LAB','DOSE_RECORD'))`（`AttachmentSchema.kt`）；`legacy_id` 是整数。SQLite 的 CHECK 不能就地修改，增加取值要重建表并写迁移。
- `attachment_link` 通过 `record_uuid` 指向 `attachment_record_identity`，与文档是多对一的关系（一个文档可有多条关联）。
- `AttachmentIdentity.bind(...)` 把 kind 映射到表（`LAB → lab_value`），要求目标行真实存在。
- `AttachmentBackupManifest.Kind { APPOINTMENT, LAB, DOSE_RECORD }`；`AttachmentBackupPreparation`、`AttachmentRestorePreflight`、`AttachmentCandidateMetadata`、`LibraryRestoreSequences` 都按 kind 映射到表。
- `MedicalAttachmentOperations.Kind { APPOINTMENT, LAB }`，`Target(kind, id)`。
- 批次回收已有一半语义：`AttachmentIdentity.trashed` 对 `Trash.LAB_PANEL` 会把批次内每个结果的身份都置为 TRASHED 并隔离其关联。
- 批次 ID 与化验项 ID 都是整数，现有 e2e 特意用“结果 ID ≠ 批次 ID”的数据防止混用。因此新目标必须是独立的 kind，**不得复用 `LAB`**。

## 3. 需求

- **R1 新目标类型。** 增加整次化验目标（暂名 `LAB_PANEL`），绑定 `lab_panel` 行（`legacy_id = panel.id`）。涉及：Room 迁移（重建约束表，旧数据不变）、`Kind` 枚举、`bind`、备份准备、恢复预检、候选元数据、恢复序列、`MedicalAttachmentOperations.Target.Kind`。必须有“批次 ID 与化验项 ID 不混淆”的负向测试。
- **R2 备份兼容。** 旧备份在新版本照常恢复；新备份若出现新的目标取值，旧版本必须明确拒绝恢复而不是误读（版本号或能力标记）。负责人此前禁止改动 PNMEDBK 格式；本次授权覆盖为此所需的**最小**扩展，必须在文档中写明改了什么、如何版本化，并通过完整备份/恢复/回滚验收。
- **R3 批次生命周期。**
  - 批次进回收站：批次关联和批次内各项的关联一起隔离（已有行为需扩展到批次级关联）；恢复同样一起恢复。
  - 从批次中移除一项（`removeFromLabPanel`）或向批次添加一项：不影响批次级关联。
  - 数据集退役、换库（epoch）：与 `LAB` 一致。
- **R4 读写接口。**
  - `attachments(target)` 接受批次目标：列出、导入（沿用确认、真实 COMMIT、只读核对、不重放）、解除关联。
  - 给“某个化验项”查询文件时，要同时给出它自己的关联和所属批次的关联，并标明范围（整次 / 单项），让 UI 在结果页显示整次化验的文件。
  - 与独立文件库的“把已存文件关联到另一条记录”接口一起设计，一份文件只存一份。
- **R5 已有的单项关联。** 不迁移、不改写；仍可读取、预览和解除；UI 在批次页里把它们显示为“挂在某一项上”。批次可用时 UI 不再新建单项关联（待 R6 的决定）。
- **R6 没有批次的旧分组——待负责人决定。** 旧记录里只是按时间和机构显示分组，不是实体，没有批次 ID。选项：(a) 先用现有的“保存为一次验血”变成正式批次，再关联整次；(b) 这类记录仍用单项关联。负责人尚未决定；在决定前保持现状（单项）。
- **R7 安全与验收。** 不按日期或文件名推断关联；陈旧数据集/epoch 被拒绝；配额不变；成功/失败/未知三态与只读核对；恢复失败可回滚；合成文件；不增加 INTERNET；API26/29/35 和低内存路径；不保存真实医疗资料。

## 4. UI-10 的消费方式（后端验收后）

- `RecordTarget.LabPanel`（已在 `attachments/ui/library/LibraryContract.kt`）；文件页的关联行显示“整份报告 · N 项”。
- 批次详情（现有 `LabPanelDialog`）增加“相关文件”区块；化验结果页显示所属批次的文件（只读，标“整次化验”）。
- “添加医疗文件”选择页：选一次化验即关联整次；旧分组按 R6 的决定处理。
- 在此之前，UI-10 的缓解已上线：结果页列出同一次化验的其他结果并可打开批次；选择页按一次化验分组；文件区块写明“只关联这一项”。
