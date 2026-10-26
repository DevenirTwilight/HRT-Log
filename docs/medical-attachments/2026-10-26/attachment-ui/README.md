# MED-02 医疗附件交互界面（隔离，默认关闭）— 2026-10-26

依据：负责人任务书“MED-02 下一阶段：收尾 #128，开发默认关闭的附件交互界面”（2026-10-26），REQUIREMENTS §149。

**状态**：NOT_REVIEWED_EXTERNALLY。`feature_enabled=0`、`PUBLIC_ENTRY_ENABLED=false`，正式用户无法进入。真实医疗原件 OFF，永久删除 OFF，不签名、不交付 APK、不发布 Release。

## 1. 入口与隔离

- 界面代码在 `app/src/main/java/net/plainnotes/app/attachments/ui/`（`internal`）。正式源码中没有任何文件引用它，`MedicalAttachmentUiGuardTest` 固定了这一点。正式版 R8 会移除这些未被引用的代码。
- 唯一入口是 debug 构建里的 `MedicalAttachmentUiTestActivity`：
  - 不导出，无启动图标，开启 `FLAG_SECURE`；
  - 只在模拟器上运行（`SyntheticAttachmentFixture` 有设备检查）；
  - 使用独立的随机 SQLCipher 和密钥命名空间，不碰应用自己的数据库、PIN 或用户记录；
  - 只接受合成 DocumentsProvider（`net.plainnotes.app.test.synthetic-medflow`）返回的文件，其他来源一律按“取消”处理。
- `scripts/check_attachment_release_isolation.py` 新增 `MedicalAttachmentUiTestActivity` 标记，正式 APK 中出现即失败。

## 2. 结构（全部复用生产接口）

| 层 | 文件 | 说明 |
|---|---|---|
| 网关 | `MedicalAttachmentGateway.kt` | 绑定一个明确存在的就诊或单个化验 `Target`。生产实现 `RuntimeAttachmentGateway` 只调用 `RuntimeLibrarySession.attachments`、`AttachmentFlowController` 和 `AttachmentViewer`，没有第二套存储或解密流程。 |
| 状态 | `MedicalAttachmentsController.kt` | 列表、导入、查看器、撤权的状态机；不保存 URI，不写 SavedState。 |
| 界面 | `MedicalAttachmentsUi.kt` | `MedicalAttachmentsSection`（列表、添加、确认、状态）和 `MedicalAttachmentViewer`（单页预览）。无状态 Compose。 |
| 文案 | `res/values*/medical_attachments.xml` | 简体中文、繁体中文、英语、法语。 |

## 3. 参考样图

参考了库中产品负责人的样图 `docs/design/claude-ui-2026-10-17/assets/`。设计审查已写明：这些是概念稿，不构成功能授权。

- **06 医疗档案·文件库 → 列表行**：彩色圆角文件块（PDF / IMG / FILE 字样）、标题、类型副标题、白色分组卡片、填充的“＋ 添加文件”按钮。
- **07 文件详情 → 查看器**：左上返回箭头；灰色圆角预览区里放白色页面卡片；右下页码角标；下方是类型标签和大标题。

有意不采用（不在本轮授权或后端没有对应事实）：

- 06 的搜索、按类别统计、待整理、“完整备份含医疗原件”状态卡、未关联文件、回收站入口；
- 07 的“加入下次就诊”、添加关联、出具机构和日期、“完整性校验正常”。审查要求只在真实校验时显示这一行，而且本轮要求对普通用户隐藏加密细节；
- 08 的导出勾选清单，属于 Visit Pack，需要另行批准。

页码角标只写“第 1 页”，不写“1 / N”，因为查看器目前不提供总页数。

## 4. 交互规范

1. **列表**：每行显示标题、类型（PDF / 图片 / 其他文件）和状态（已保存 / 不可用）。TalkBack 一次读出“标题，类型，状态”。只有可打开的行是按钮；不可用的行（例如在回收站）不能点击。
2. **添加文件**：
   - 打开系统文件选择器（SAF，单个文件，临时读取授权，不持久化）。
   - 选完文件后，不建立任何操作，也不读取文件内容。
   - 确认对话框写明“将添加到：<记录>”，名称可改，默认是“就诊文件”或“化验单”。
   - 只有用户点“添加”后，才用最终名称建立一次导入操作，读取一次文件并提交。
3. **进度与结果**：
   - 保存过程中显示“正在保存文件…”和不确定进度条。后端是一次性读取加事务提交，没有可靠的百分比，所以不显示假的进度数字。
   - **只有真实 COMMIT 回执才显示“文件已保存”**。如果提交成功但随后刷新列表失败，显示“文件已保存，但列表没能刷新”，不会说“未保存”。
   - 失败显示“文件没有保存”；取消（包括在选择器里返回）显示“没有添加文件”。
4. **结果不确定**：显示“还不能确认文件是否已保存”，只提供“检查状态”。它用同一个操作和文档标识做只读核对（`reconcile`），不会再读文件，也不会再次写入。
5. **查看**：
   - 图片（PNG/JPEG）显示整图；PDF 只显示第一页，右下角角标“第 1 页”。
   - 其他类型不读取内容，提示“这种文件无法在这里预览。文件仍然保存着”。
   - Android 8.x 上 PDF 提示“需要 Android 9 或更高版本”（原因见 #128）。
   - 解码失败统一提示“这个文件没能显示”，并关闭读取。
   - 关闭查看器（按钮或返回键）时，先让界面不再引用页面，再回收像素、关闭读取器。
6. **撤权**：
   - **应用锁定**：先清空界面状态（页面引用、列表、未确认的选择），再由会话撤权回收像素、关闭读取器。解锁后只重新显示列表，查看器不会自动重新打开。
   - **数据集切换**：页面永久失效，显示“数据已切换。请重新打开此页面查看文件”，不会把旧编号绑到新数据上。
   - **保存过程中被锁定**：不会悄悄放弃这次保存。解锁后显示“结果不确定”，用户可以只读核对。
   - **页面重建**：只保留中性的记录类型和编号。未确认的选择、选择器结果和预览都不恢复。

本轮不实现解除关联、回收站和永久删除；这三者是不同的操作，均未获授权。

## 5. 验收分层（必须区分）

| 层次 | 证据 | 状态 |
|---|---|---|
| UI 演示（无后端） | `MedicalAttachmentsUiTest`：Robolectric，4 种语言 × 320dp/2 倍字体与 411dp/1 倍字体，检查文字、48dp 触控区域、TalkBack 标签和角色、实时区域、撤权后清空 | 本地通过（48 项，4 项按设计跳过：411dp 对话框是 Robolectric 已知限制，由模拟器测试覆盖） |
| 状态机（假网关） | `MedicalAttachmentsControllerTest`：取消不留操作、只有回执算保存、提交后刷新失败仍算已保存、结果不确定时只读核对、撤权先清空、切库永久失效 | 本地通过（10 项） |
| 隔离守卫 | `MedicalAttachmentUiGuardTest` | 本地通过（3 项） |
| 真实生产接口调用、实际 COMMIT、经认证读取 | `MedicalAttachmentUiAndroidTest`（模拟器）：<br>- 合成记录 → 真实 DocumentsUI 选择 → 明确确认 → 实际 COMMIT；<br>- 经认证读取，字节与原文件相同，只关联到该记录；<br>- 打开查看器 → 关闭后回收；<br>- 锁定后清空页面，旧请求被拒；解锁不自动打开；<br>- 切换数据后旧读取被拒，输出清零；<br>- 取消和页面重建不留操作 | 待 CI：API35 的 device-tests 会运行全部 app 仪器测试；API26/29 矩阵作业已加入这个类（`android.yml` 中仪器测试类列表） |
| 物理设备、人工 TalkBack | — | NOT_RUN |
| 伪装模式实际切换 | 与锁定共用同一条撤权路径（`RuntimeLibrarySession` 的 pause/invalidate），但合成宿主里没有伪装开关 | NOT_RUN（本轮未单独验证） |
| 独立外部安全审查 | — | PENDING / NOT_REVIEWED_EXTERNALLY |

## 6. 上线前仍缺

1. 独立外部安全审查，覆盖本界面层和 #128 外审包所列文件（网关、控制器、查看器、会话撤权、ProxyFD 线程、像素回收）。
2. 负责人批准公开入口位置：就诊详情、单个化验详情。届时需要接入 `NotesViewModel`/`AppShell` 的锁定、伪装、数据集切换生命周期，这需要先确认文件所有权。
3. 真机验证：人工 TalkBack、低内存、伪装切换、物理断电。
4. 解除关联和回收站的交互规范与授权；永久删除另行授权。
5. 产品决定：Android 8.x 是否要通过隔离进程提供 PDF 预览；是否支持多页 PDF 浏览（目前只显示第一页）。
6. WebP 仍按不透明二进制处理（不在 MIME 白名单内），本轮没有改变。

## Stage C lifecycle candidate — 2026-10-26 (not yet accepted)

Baseline `9eb6d0f51db21e3629019c2757a5f35724d793a7`; #130 still OPEN at682c42a8 during branch creation. No visit-pack selection files modified. MainActivity.onPause → NotesViewModel.sensitivePresentationHidden → existing RuntimeLibrarySession notification → RuntimeAttachmentGateway.onHidden → controller.closeViewer. This clears UI state before resource teardown, including rotation and real disguise exit. Notification conveys no authorization. Runtime pause/invalidate/close still performs actual capability revocation; an ended runtime terminates the page. The file-picker pause clears only viewing, preserving existing SAF permission semantics; confirmed imports still use original runtime authority and receipts. No auto-reopen or persisted URI/capability is added.

Production ViewModel integration is an internal extension in the UI package, with PUBLIC_ENTRY_ENABLED checked before accessing the runtime/record. There is no formal route, menu, deep link or notification entry. Existing release-isolation guard remains unchanged. Debug synthetic hosts still bind only their isolated runtime. Future proposed locations are explicit visit detail or individual lab detail, subject to separate owner enablement approval.

Controller rendering has a monotonic presentation epoch and synchronized publication: close/revoke/end invalidates old render completions even after resume or a new Opening state. Epoch is ordering only, not a second scope/authority. Refreshes cannot republish old list data after revoke. Existing real COMMIT and read-only reconcile rules remain; no change to storage transactions.

Tests added: real MainActivity AppLock/background/recreation event witness; actual Disguise.exit to calculator event witness; scripted controller late-render rejection. These are different evidence layers: native event transport is not a complete formal-user attachment flow, and scripted races are not native decoder races. Prior #129 synthetic UI/storage/native results remain with their source SHA; new candidate tests are PENDING until CI, physical/TalkBack/power interruption NOT_RUN. Do not claim all Stage C scenarios accepted solely from compilation.

### Product decisions (proposal only; no lifecycle actions implemented)

API26/27 PDF remains fail-closed; current encrypted file storage unchanged. Long-term Android8 policy still needs owner confirmation. WebP stays opaque. Multi-page is deferred: propose authenticated renderer pageCount only after successful open, bounded current page, release before demand-loading previous/next, no prefetch, request epoch to discard fast-navigation/lock/background results. Add explicit page index/total to Preview, verify API28/29/35 plus native malformed documents and low-memory page-turn/revocation races before implementation; do not call pageCount on Android8 or manufacture totals from filenames.

| Action | Recommended semantics (NOT APPROVED/NOT IMPLEMENTED here) |
|---|---|
| Unlink | Append link-history change for explicit target; retain object, versions and other links; no quota/key release. |
| Trash | Soft state change; retain all history and ciphertext in full backup/RETAINED inventory; no budget release. |
| Restore | Restore object availability only after scope/integrity check; relationships need explicit validated confirmation, no automatic resurrection. |
| Permanent destruction | Separate protocol/authorization: prove ownership and no references in ACTIVE/RETAINED/pending operations; account for backups outside app control and unrecoverable key loss. Retain authorized minimal evidence; never promise erasure of external backups. No deletion or reclaimed quota before a durable, independently reviewed destruction outcome. |

All four actions wait for explicit approval; no handlers or public controls added. Retained generations and non-reclaimable key reservations remain counted until an approved release/destruction protocol proves otherwise.
