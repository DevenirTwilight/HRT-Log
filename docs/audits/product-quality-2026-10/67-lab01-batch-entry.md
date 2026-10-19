# 67 — LAB-01：一次化验批量录入（只改界面，不改数据库）（2026-10-18）

- 任务书：`sources/lab01/lab01-prompt.md`（SHA-256 `d087ba27…e516c6`）；REQUIREMENTS §111。
- 分支：`feature/lab01-batch-entry-20261018`，基于 UI-05 分支头开发，UI-05（#75）合并后合入默认分支。
- 性质：新增批量录入表单和化验页按次显示。每条化验仍是一条普通记录，写入方式与单项保存相同。没有改 Room schema、实体、迁移、备份格式，也没有改 PK、浓度页、导出 PDF 和提醒。

## 1. 改动文件与保护类别

| 文件 | 类别 | 改动 |
|---|---|---|
| `ui/LabBatchEntry.kt`（新） | 矩阵外 | 批量表单 `LabBatchDialog`、校验 `LabBatchRules`、常用组合 `LabPreset`、显示分组 `labGroups`、范围判断 `labFlag`、卡片 `LabGroupCard` |
| `ui/LabsScreen.kt` | **PK_DISPLAY_REVIEW** | 只改批准的三处：筛选前加“全部”（默认）并按次显示；多项卡的行打开现有详情；单项编辑对话框的分组提示（新增可选参数 `inGroup`）。+13 / −2 行 |
| `ui/ShellHosts.kt` | HISTORY_ONLY | “添加化验”打开批量表单；编辑已有记录仍用 `LabDialog`；保存失败时表单保留 |
| `ui/AppShell.kt` | HISTORY_ONLY | 把化验列表传给编辑对话框（用于判断是否在多项卡中） |
| `NotesViewModel.kt` | 矩阵外 | `saveLabs(values, includeEstimate, onDone)`；`saveLab` 不变 |
| `core/data/.../NotesRepository.kt` | 矩阵外 | `saveLabs` 在一个事务内逐条调用与 `saveLab` 相同的私有写入 `writeLab`（`saveLab` 行为不变） |
| `res/values*/strings.xml` | — | 16 条新文案 × 四语 |

不改的文件：`LabContextSection.kt`、`Exports.kt`（PK_DISPLAY_REVIEW），`LabEstimate.kt`、`CalibrationEligibility.kt`、`LabFit.kt`（LIVE_LOCK_RESEARCH_INPUT），`ConcentrationCalculator.kt`。没有遇到必须改动它们的情况。

## 2. 批准记录

登记在 `docs/audits/product-quality-2026-10/display-approvals-after-archive.json`：

- id：`PD-2026-10-18-LAB01-LABSSCREEN`
- 前 `fff4590c…d53ab`，后 `27e2d80b…cee30f`
- 证据：提交 `890ae4ca`，其中的 `docs/REQUIREMENTS.md` §111 含此 id，并引用任务书第 1 节第 3 条原文。

更正（2026-10-18）：最初把记录追加到了 `docs/pk-research/p2/display-approvals.json`。这个文件属于已冻结的研究归档，`research_archive.verify_records` 要求它与归档提交逐字节相同，所以 #78 合并后 research-deep 失败。现已恢复该文件原样，记录改放在新文件中，内容不变。

说明：`LabsScreen.kt` 在本任务前已经和冻结基线（`11dc6b23…`）不同，此前没有该文件的批准记录。当前守卫（`check_protocol.check`）已不再校验显示文件的批准链（显示改动由 UI 测试把关）。所以本条记录的“前”哈希取本任务开始时的文件，而不是冻结基线。

PK 显示回归：浓度页、化验上下文、PK 显示相关的现有测试均通过（见第 5 节）。`Lab01UnaffectedTest` 另外证明，浓度的化验点、校准资格和曲线结果，与逐条保存时逐项相同。

## 3. 实现要点

- **表单**：全屏对话框，标题“记录一次化验”。
  - 日期时间（`DateTimeRow`）和机构只填一次。
  - 每行有：项目、孕酮检测方法（同单项）、数值、单位（随项目切换）、可折叠的参考范围、备注、删除。
  - 底部固定“取消 / 保存”和失败提示；“同时保存当时的浓度估算”勾选框含义不变。
- **校验**与单项对话框逐条一致：数值有限且 > 0；范围两端为空或是数字，且下限 ≤ 上限。
  - 完全空白的行（数值、范围、备注都没填）忽略。
  - 至少一行有效才能保存。
  - 范围无效时自动展开，不会被折叠藏起来。
- **重复**：同一次录入中两个非空行是同一项目代码时，两行都标红并显示“同一次化验中这个项目重复了”，同时禁止保存。
  - 孕酮的三种检测方法是不同代码，不算重复。
- **常用组合**：
  - 只添加还没有的项目，任一孕酮方法都算已有孕酮。
  - 表单只有一行且完全未动时，用组合替换这一行。所以从新表单点“激素六项”正好得到 6 行；第一行已填 E2 时再点，只补 5 行。
- **原子保存**：
  - `saveLabs` 要求至少一条，且全部是新记录（id 为 0）。
  - 写入在一个 Room 事务里。任何一条失败（包括估算失败）都会整体回滚，表单保留内容，并显示“没有保存任何一项，填写的内容仍保留”。
- **分组显示**：
  - 规则：`sampled_utc` 完全相同，且机构文字去首尾空格后完全相同；两边都为空视为相同。
  - 卡片按时间倒序；时间相同时按最大 id 倒序。
  - 卡内项目按 `ANALYTES` 顺序排列。
  - 只有 1 条的组仍用原单条卡片。
  - 分组只在显示时计算，不写入数据库、备份或导出。
- **卡片**：
  - 标题行是“日期时间 · 机构 · n 项”。
  - 内嵌白色列表的每行是“项目 / 数值 单位”加右箭头；超出范围时另起一行文字“偏高 / 偏低”。偏高偏低在同一单位下比较，单位能换算时先换算，换算不了就不标。
  - TalkBack 朗读“项目，数值，单位”，超出范围时加“，偏高 / 偏低”。
  - 点击打开现有的 `LabDetailDialog`，从详情里点“编辑”进入原编辑流程。
- **“全部”与单项视图**：化验页筛选前新增“全部”，默认选中。选某个项目时，与改动前相同：显示趋势图和逐条记录，带 ⋮ 菜单（编辑、删除）和上下文。删除只在单项视图的 ⋮ 菜单里。这是本轮的实现决定，写在 §111，负责人可推翻。
- **编辑提示**：编辑一条位于多项卡中的记录时，只要采样时间或机构（去首尾空格）与原值不同，保存按钮上方就会显示“修改时间或机构后，这一项会从这次化验中单独显示”。这只是提示，不阻止保存。

## 4. 四语文案

| key | zh-CN | zh-TW | en | fr |
|---|---|---|---|---|
| `lab_batch_title` | 记录一次化验 | 記錄一次檢驗 | Record a blood test | Enregistrer une prise de sang |
| `lab_batch_add_row` | 添加项目 | 新增項目 | Add a test | Ajouter une analyse |
| `lab_batch_presets` | 常用组合 | 常用組合 | Common sets | Ensembles courants |
| `lab_preset_hormones` | 激素六项 | 激素六項 | Six hormones | Six hormones |
| `lab_preset_liver` | 肝功能 | 肝功能 | Liver | Foie |
| `lab_preset_kidney` | 肾功能与电解质 | 腎功能與電解質 | Kidney and electrolytes | Rein et électrolytes |
| `lab_group_count` | %1$d 项 | %1$d 項 | %1$d tests | %1$d analyses |
| `lab_batch_duplicate` | 同一次化验中这个项目重复了 | 同一次檢驗中這個項目重複了 | This test is already in this entry | Cette analyse figure déjà dans cette saisie |
| `lab_group_split_hint` | 修改时间或机构后，这一项会从这次化验中单独显示 | 修改時間或機構後，這一項會從這次檢驗中單獨顯示 | Changing the time or lab will show this result on its own | Si vous modifiez l'heure ou le laboratoire, ce résultat s'affichera seul |
| `lab_flag_high` / `lab_flag_low` | 偏高 / 偏低 | 偏高 / 偏低 | High / Low | Élevé / Bas |
| `lab_filter_all` | 全部 | 全部 | All | Tout |
| `lab_batch_remove_row` | 删除第 %1$d 项 | 刪除第 %1$d 項 | Remove test %1$d | Supprimer l'analyse %1$d |
| `lab_row_spoken` | %1$s，%2$s，%3$s | %1$s，%2$s，%3$s | %1$s, %2$s, %3$s | %1$s, %2$s, %3$s |
| `lab_row_spoken_flag` | ，%1$s | ，%1$s | , %1$s | , %1$s |
| `lab_batch_failed` | 没有保存任何一项，填写的内容仍保留。 | 沒有儲存任何一項，填寫的內容仍保留。 | Nothing was saved. Your entries are still here. | Rien n'a été enregistré. Votre saisie est conservée. |

zh-TW 沿用现有术语“檢驗”“機構”；fr 沿用“analyse”“laboratoire”。

## 5. 测试

| 测试 | 覆盖任务书第 3 节 | 结果 |
|---|---|---|
| `core:data` `LabBatchSaveTest` | 1（6 项逐字段等同逐条保存，含上下文快照、按条请求估算、`lab_analyte`）；2（第 4 条估算失败 → 0 条新增；无效行也整体回滚）；只接受新记录且至少一条 | 3/3 |
| `Lab01BatchEntryTest` | 3（空行忽略、重复阻止、孕酮方法与单位、无效范围自动展开）；4（激素六项 6 行、已有 E2 不重复、肝功能、肾功能）；2 的表单一侧（失败时内容保留、可重试）；估算勾选传递 | 7/7 |
| `Lab01ValidationParityTest` | 3：12 组数值/范围用例分别跑单项对话框和批量表单，“保存”是否可用逐条一致 | 24/24 |
| `Lab01GroupDisplayTest` | 5（同时同机构合并，同时不同机构分开，机构都为空合并，晚 1 分钟不合并，单条按原样）；6（编辑时出现提示、改时间后单独显示）；TalkBack 朗读；偏高偏低文字和单位换算；行打开详情再编辑；选单个项目时趋势图不变 | 7/7 |
| `Lab01UnaffectedTest` | 7：同一组数据分别批量保存和逐条保存。比较数据库行和上下文、浓度的化验点、校准资格、E2 曲线、校准条数、时间线事件、医疗档案“时间范围内的化验”、备份恢复后的行和上下文，全部相同 | 1/1 |
| `Lab01LayoutTest` | 8：四语 × 320/411dp × 字号 1/2 × 明暗（32 组）。三个组合都加上后，能滚到第 11 行和估算勾选，“保存”“取消”始终在屏幕内；化验页能渲染多项卡 | 64/64 |

先写失败测试：本轮的测试和实现在同一组改动里完成。新 API（`LabBatchDialog`、`saveLabs`、`labGroups` 等）在改动前不存在，所以这些测试在基线上无法编译，没有单独的“改动前运行失败”记录。

测试环境发现：带类级 `qualifiers = "w411dp-h891dp"` 的 Robolectric 测试一打开对话框就一直不空闲，与 UI-05 记录的现象相同。新测试因此不加类级尺寸限定符。布局测试改用 `RuntimeEnvironment.setQualifiers` 设定尺寸，在 64 组下均正常。

本地完整回归和 CI 结果见第 8 节。

## 6. 截图

`docs/ui-audit/2026-10-18-lab01/`，由 `Lab01LayoutTest`（`LAB01_SHOTS=<label>`）生成，只用合成数据和中性名称：

- `after/`：
  - `1_labs`：化验页，含一张 5 项卡和一条单条记录；
  - `2_form_empty`：空表单；
  - `3_form_presets`：加上三个组合后的表单。
  - 各 32 组合。
- `before/`（共 48 张）：
  - 基线上的化验页，32 组合；
  - 原来的单项“添加化验”对话框，只有 320dp 的 16 组合。411dp 下这个 AlertDialog 在 Robolectric 中一直不空闲，与第 5 节记录的现象相同，所以没有生成。
- `compare/`：左旧右新拼图 7 张：
  - 化验页 3 张（zh-CN 411dp 字号 1 浅色、en 320dp 字号 2 深色、fr 320dp 字号 2 浅色）；
  - 空表单 2 张；
  - 加上组合后的表单 2 张。
- 所有图片缩到 360px 宽并量化。

## 7. NOT_RUN

- 真机清单第 11 节：用一张真实化验单走完整流程、改时间后分出、最大字号、TalkBack。由负责人回填。
- 国产 ROM。
- 真机 TalkBack。

## 8. 本地回归与 CI

本地完整回归（本分支合入 #75 后）：

- `core:reminder` 20 项；
- `core:data` 250 项；
- `app:testFullDebugUnitTest` 1629 项，0 失败。
  - 跳过 237 项，都是既有的默认关闭用例：205 项既有环境门控截图/审计用例，32 项 UI-05 截图参数组合。
  - `Lab01LayoutTest` 不截图时也会断言，所以计入通过。
- `PeriodStabilityTest`、`LabEstimateTest`、`LabContextUiTest`、`SublingualEstimatePolicyTest`、`MedicalRecords*`、`ButtonLayoutRegressionTest`、`TranslationsTest` 均通过；
- `lintFullDebug` 通过。

CI 结果以 PR 为准。
