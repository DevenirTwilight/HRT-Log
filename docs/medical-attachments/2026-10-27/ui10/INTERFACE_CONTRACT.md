# UI-10 ⇄ MED-03 接口合同（2026-10-27）

**这是接口需求，不是实现授权。** MED-03 仍是候选（REQUIREMENTS §156）。本合同让 Claude Code（UI-10）和 Codex（MED-03）可以并行而不改同一批文件。

## 1. 所有权

| 内容 | 持有者 | 位置 |
|---|---|---|
| 布局、展示组件、字符串、UI 测试、截图 | Claude Code / UI-10 | `app/.../ui/*Page*.kt`、`attachments/ui/*Ui.kt`、`attachments/ui/library/`、`res/*/ui10_redesign.xml`、`.../ui10/` 测试 |
| 持久化、权限、加密、关联事务、回收/恢复、备份一致性、`RuntimeLibrarySession`、`MedicalAttachmentOperations` | Codex / MED-03 | `core/data`、`attachments/*`（非 ui）、Room、PNMEDBK2 |
| 共享的 `AppShell.kt`、`ShellHosts.kt`、`NotesViewModel.kt` | 按顺序集成：先 MED-03 合同验收，再由 UI-10 接线；不同时修改 | — |

UI 层不新建第二套实体、数据库、解密流程或状态系统；下表的类型只是 UI 需要的形状，实际实现复用代码里已有的类型（`library/LibraryContract.kt` 是可替换的适配层，不是新存储）。

## 2. UI 所需的类型（逻辑名称）

| 类型 | 字段 | 说明 |
|---|---|---|
| `DocumentSummary` | `document: UUID`、`title`、`kind: PDF/IMAGE/OTHER`、`link: Unlinked/Linked(count)`、`category?`、`addedLabel?` | 只有后端提供的字段才画；日期、机构、大小不从文件名或历史推断 |
| `DocumentDetail` | `summary`、`links: List<DocumentLink>`、`sizeLabel?`、`inBin` | |
| `DocumentLink` / `RecordTarget` | `Visit(id,label)`、`LabValue(id,label)`、`LabPanel(id,label,valueCount)` | 面板与单项是不同目标，互不推断 |
| `ImportDraft` / `ImportState` | 文件名、标题、类别?、`LinkChoice`（已有就诊 / 已有化验或批次 / 新建记录并关联 / 暂不关联）、已选记录? | 状态沿用 `Import`：Idle、Confirming、Saving、Saved、NotSaved、Cancelled、Unknown、Checking |
| `LinkOperationState` | 沿用 `UnlinkStatus`（IDLE、WORKING、REMOVED、REMOVED_LIST_STALE、UNKNOWN），扩展到 link | 未知结果只读核对，不重放 |
| `RecoveryState` | `inBin`，恢复结果同上三态 | 永久删除不在范围内 |
| `LibraryCapabilities` | `standaloneImport`、`search`、`categories`、`bidirectionalLinks`、`panelLinks`、`chooseExisting`、`recycle` | 生产默认全 false；每个能力在对应后端验收后才置 true |

## 3. 每个 UI 操作

状态：**可用**＝现有生产接口直接可用；**MED-03**＝需要新增接口；**不可用**＝当前没有，UI 禁用并写明原因。

| UI 操作 | 状态 | 现有接口 / 需求 |
|---|---|---|
| 列出一次就诊或一项化验的文件 | 可用 | `RuntimeLibrarySession.attachments(target).documents()` |
| 从手机选文件 → 确认 → 保存 | 可用 | `startImport` / `picked` / `confirmImport`，只有真实 COMMIT 才“已保存” |
| 预览（JPEG/PNG 整图、API28+ PDF 第一页） | 可用 | `AttachmentViewer` + capability + AppLock/epoch |
| 解除某条关联 | 可用 | `unlink`（保留存储文件） |
| 选择一次就诊/一项化验作为添加目标 | 可用 | 纯 UI，复用已加载的记录列表 |
| 新建化验/就诊后直接添加文件 | **MED-03（小）** | 保存接口需返回已保存记录的稳定身份（`saveMedicalVisit`/`saveLabs` 目前只回成功标志）；保存成功与文件提交保持两个独立结果 |
| 没有关联的文件导入、统一文件库列表 | **MED-03** | 独立文件身份 + `list(filter)` 返回 `DocumentSummary`；导入时 `link=Unlinked` |
| 给已存文件添加关联（访视、单项、批次） | **MED-03** | `link(document, RecordTarget)`，幂等、原子；`LabPanel` 另有目标类型 |
| 选择已有文件（记录页） | **MED-03** | 同上，列表来自统一文件库 |
| 搜索标题/类别、类别统计、待整理计数 | **MED-03** | 类别字段 + 计数投影；只在用户设置后有类别 |
| 回收站、恢复 | **MED-03**（永久删除不在范围） | `moveToBin` / `restore`，状态 `inBin` |
| 文件大小、导入日期 | **MED-03（可选）** | DTO 提供才显示 |
| “完整性校验正常” | **不可用** | 只有真实密文校验结果存在时才可能显示，目前不显示 |
| 多页 PDF 阅读器、Android 8 的 PDF 预览 | **不可用** | 产品决定待定 |

## 4. 约束（对 MED-03）

- 一份文件只存一份；关联多个记录不复制文件。
- 关联、解除、回收都要有明确的“成功/失败/未知”三态；未知只读核对。
- 备份、PNMEDBK2、Room 变更、加密由 MED-03 自行审定；UI-10 不做。
- 不增加 INTERNET 权限；合成文件测试，不用真实医疗文件。

## 5. 接线顺序

1. MED-03 后端合并并验收，`LibraryCapabilities` 对应项由生产配置给出。
2. UI-10 增加一个只读适配层（`MedicalFileLibrary` → `DocumentSummary`），在医疗文件区里把「暂未开放」卡换成真实入口（`library/` 组件已就位，带 Robolectric 测试）。
3. AppShell/ShellHosts 的共享文件按顺序集成，不并发修改。
