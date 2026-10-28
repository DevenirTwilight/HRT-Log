# 77 · UI-11 药品资料中心与商品资料关联重设计

日期：2026-10-28。任务书：`docs/design/ui10-drug-center-2026-10-27/TASK_BOOK_ORIGINAL.md`（原称 UI-10，负责人确认改称 UI-11，REQUIREMENTS §158.A、§159）。分支 `feature/ui11-drug-center-20261027`。

## 1. 结论

两个页面（药品资料中心、商品资料关联）已改为 UI-06/UI-07 体系；只改展示与导航，不改业务（目录数据、搜索与排序、关联/解除/自行录入逻辑、确认对话框、Room schema、备份都没有动）。

**关联状态能否区分“官方”和“我录入的”：能，且不需要改 schema。** 现有关联记录 `MedicationReference.product` 是一份冻结快照：从 BDPM 目录选择产生的快照带 `bdpm`（来源溯源：数据库哈希、来源日期、CIP7、包装描述、下载地址），自行录入的快照 `bdpm == null`（`validatedUserReferenceProduct` 强制 `require(product.bdpm==null)`）。所以：

| 现有数据 | 页面显示 | 语义色 |
| --- | --- | --- |
| 无关联，或关联被解除（`product == null`） | 未关联产品来源 | 中性灰 |
| `product.bdpm != null` | 已关联 BDPM 官方资料 | success |
| `product != null && bdpm == null` | 已关联我录入的资料 | attention |

读取方式：`MedicationReferenceController.links()`（只读，逐药调用既有的 `medicationReferenceState`）；`savedSourceCount()`（只读，既有的 `savedReferenceProducts`）。关联或解除成功后 `changes` 计数加一，药品中心据此刷新；药品中心本身没有任何写入路径。

## 2. 改动清单

### A. 共用返回按钮
- 新组件 `NavBackButton`：48dp、← 图标，无障碍名称用新增的四语言 `nav_back`（Back / 返回 / 返回 / Retour），不复用 `cancel`。
- 用于药品资料中心（`drug-center-close`）、商品资料关联页（`reference-close`）、`RecordedReferenceDialog`（`record-reference-close`）。tag 与行为不变；系统返回键不变；商品资料关联页保存中（`busy`）时仍不可点（与原来一致）。

### 删除两个按钮
- 药品中心里的“药物”（`drug-center-plans`）和“库存”（`drug-center-stock`）按钮已删除，`DrugReferenceCenterScreen/Dialog` 的 `onPlans`/`onStock` 参数和 `MedicationsScreen`/`AppShell` 的 `onStock` 传递一并删除。抽屉里的“药物”“库存”不变（`DrugCenterNavigationTest` 里验证）。

### B. 药品资料中心（`DrugReferenceCenterScreen.kt`，重写）
- 主卡：success 容器色、最大一档圆角（UI-07 `Corners.xxl`）；“法国 · ANSM / BDPM 参考”位置胶囊；标题“查找商品资料”；新的 `drug_center_scope`（只读资料、不提供用药建议、不改变剂量和库存）；白色胶囊搜索入口（≥56dp，tag `drug-center-catalogue`）。
- 我的药物：GroupedList（24/6 圆角、3dp 间距）；40dp 浅色图标砖；名称；关联状态标签；已归档多一个“已归档”标签且名称改用次要文字色（不用透明度，文字对比度保持）；箭头；组下方小注。tag `drug-center-medication:<id>`，整行可点。
- 资料来源：两行（BDPM 官方目录：离线 + 数据日期，“官方”标签；我录入的资料：条数 + “未经核实”，“未核实”标签）。数据日期来自随包的 BDPM manifest（`downloaded_on`），条数来自 `savedReferenceProducts`；读不到时显示固定说明，不编造；条数达到页上限时显示“n 条以上”。
- 了解这些资料：产品身份与来源、包装、状态含义三行，默认收起，点整行展开，箭头旋转，用 UI-07 弹簧动效并遵守减少动态；展开状态在旋转和进程重建后保留（`rememberSaveable`）。
- 尚未开放的模块：surface 分组行，灰色线条图标，描边“尚未开放”标签，**不可点击**；TalkBack 读“名称，尚未开放”；五个模块的名称和原说明文字不变；组下方加一句说明。`UnavailableModuleCard.kt` 已删除。

### C. 商品资料关联（`MedicationReferenceDialog.kt`、`BdpmCataloguePanel.kt`）
- 顶栏：返回、标题“商品资料关联”、小号副标题为当前药物名（`AppShell` 传入，新建药物草稿浏览时无副标题）。
- 边界卡：surface、24dp 圆角、信息图标；加粗一句“关联不会改变剂量、方案或库存”，其下保留原 `ref2_boundary` 全文。
- 当前关联卡（有关联才显示）：白色 28dp 圆角；来源标签（BDPM 官方 / 我录入 · 未核实）、“已指定包装”标签（有 CIP 或包装描述时）、名称、成分 · 剂型 · 途径、CIS 和包装（CIP13）的标签/值（等宽数字）、“查看资料”（tonal 胶囊）、“解除关联”（文字按钮，仍先弹确认）。
- 分段胶囊控件（与 UI-08 身心状态页同一做法）：“法国 HRT 药品目录”/“我录入的资料”，`role=tab`，选中语义。
- 目录页：胶囊搜索框 + 主要胶囊按钮“离线搜索”；状态行“n 条结果 · BDPM 离线 · 数据 日期”；结果为 GroupedList（名称、成分 · 剂型 · 途径、“BDPM 官方”标签、CIS）；一次只展开一条；展开后显示完整成分行、包装单选卡（选中 = primary 描边 + 浅色填充）、“仅选择产品，不指定包装”、主按钮“关联此资料”。
- 我录入的资料：药名/成分胶囊搜索框；剂型、规格、途径三个胶囊筛选；结果 GroupedList，带“我录入 · 未核实”标签（官方快照显示“BDPM 官方”）和录入日期。
- 底部：描边胶囊“＋ 自行录入商品资料”和说明“找不到时可以手动录入，资料会标为‘未核实’。”

### 与任务书不同之处（均已在此说明）
1. **包装卡/主按钮保留原确认流程。** 点包装卡仍然直接打开原来的“确认前预览”，再走“确认资料变更”。主按钮“关联此资料”在已选包装后可重新打开同一预览；它不跳过任何确认步骤。这样 `BdpmReferenceFlowContract`（点包装后立即期待预览）不需要改动。
2. **当前关联的只读详情默认展开**，由“查看资料 / 收起资料”切换。原因：既有断言（`DrugCenterNavigationTest`、`BdpmReferenceFlowContract`）在重新打开页面后直接查找 `reference-details`，不能为此放宽断言。
3. **筛选“药丸”是胶囊形的小输入框**（`BasicTextField`，保留原 tag 与过滤逻辑，一行放不下时自动换行；未输入时显示“剂型/规格/途径”），不是点开才输入的药丸。原因同上：既有测试直接向这些输入框输入。
4. **“尚未开放”行的 TalkBack 只读“名称，尚未开放”**，每个模块较长的说明文字只在屏幕上显示（任务书要求的朗读格式）；组下方的说明文字可朗读。
5. 原来药品中心里的 `bdpm_available`、`drug_center_user_sources` 两段说明被“资料来源”两行取代（含义由“离线”“未经核实”和标签承载）；字符串仍在资源里，未删除。

## 3. 迁移的既有断言（仅限任务书允许的两类：“取消→返回”和两个被删按钮，另加“五个模块不可点击”这一任务书明确要求的变化）

| 测试 | 变化 | 原因 |
| --- | --- | --- |
| `DrugCenterNavigationTest` | 五个模块不再点开 `center-unavailable-dialog`，改为断言无点击动作且不开对话框；“库存”按钮断言改为按钮不存在，并从抽屉到达库存页（top-title 为库存） | 模块不可点击；库存按钮已删除 |
| `DrugCenterMatrixTest` | 五个模块改为“无点击动作、无对话框”；“产品身份/包装”两行现在可展开，改为断言展开开关有点击动作 | 模块不可点击；说明区改为可展开 |
| `CenterModuleScreenshotsTest` | 药品中心的五个模块改为无点击、无对话框 | 同上 |
| `CenterStateRestorationTest` | 去掉 `MedicationsScreen(onStock=…)` 参数 | `onStock` 已删除 |
| androidTestFull `InAppNavigationLockAndroidTest` | 五个模块改为 `assertHasNoClickAction`，不再要求返回键关闭对话框 | 同上（真机未运行，见 NOT_RUN） |

没有放宽 `MedicationReferenceControllerTest`、`MedicationReferenceUiTest`、`MedicationReferenceMatrixTest`、`RecordedReferenceUiTest`、`BdpmReferenceFlowContract`（经 `BdpmReferenceFlowTest`）的任何断言；“关联/解除/重开不改变药物、库存、记录上下文”的断言全部保留并通过。

## 4. 新增测试

`app/src/test/.../ui11/Ui11CenterTest`（144 例）和 `Ui11LinkTest`（72 例，其中 16 例因 411dp 嵌套对话框在 Robolectric 下不空闲而跳过，见 NOT_RUN），四语言 × 320dp/2×字体 × 411dp/1× × 浅/深色：
- 返回按钮：四语言名称 = 期望值（独立于资源）且 ≠ 取消，≥48dp，有点击动作，点击返回；
- 两个按钮已删除；
- 主卡内容、搜索入口 ≥56dp 且可点；
- 三种关联状态 + 已归档标签：文字、语义色调（official/user/neutral）、整行点击、分组圆角；
- 标签颜色全部取自 UI-06 语义令牌，文字对比度 ≥4.5:1（浅色、深色），官方与我录入色调不同，不用危险色；
- 来源两行不编造版本和条数；
- 说明区默认收起、独立展开/收起、状态语义；展开状态经保存状态还原后保留；
- 五个模块不可点击、朗读为“名称，尚未开放”；
- 商品资料关联页：返回按钮与副标题与边界卡；分段 Tab 语义与切换；目录结果一次只展开一条、包装为单选、主按钮在选包装前不可用、预览→取消不写入；当前关联卡（官方/我录入）；解除仍先确认且不改药物；真实流程仍到达同一卡片；手动录入说明；关联后 `changes` 计数加一。

## 5. 截图

`docs/ui-audit/2026-10-28-ui11/`（真实渲染，同一批合成数据）：
- `before/`：UI-10 合并后的 main（`5d383a2d`）用旧组件渲染；
- `after/`：本分支；药品资料中心（关联/未关联/已归档/来源/展开/尚未开放）与关联页（无关联/当前关联/目录展开/我录入的资料），四语言 × 320dp 2× × 411dp 1× × 浅/深色；
- `compare/`：前后并排，以及与三张样图（01/02/03）的并排。

## 6. NOT_RUN / 未验证

- 物理设备与真机 TalkBack：NOT_RUN。
- 411dp 下嵌套对话框（预览、确认、手动录入表单）：Robolectric 不空闲，这些流程只在 320dp 测试；411dp 覆盖页面本身。
- 独立外审：NOT_REVIEWED_EXTERNALLY。
- 全量 `:app:testFullDebugUnitTest`、lint、device-tests 由 CI 运行；本地只跑了相关子集（见 HANDOFF）。
