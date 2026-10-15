# HRT Log — UI-03 任务书：抽屉按信息来源重新分组 + 完成 UI-02B + UI 收尾（2026-10-15）

> 交给 Claude Code 执行。负责人：DevenirTwilight。本任务书即本轮授权来源，开工时原文归档并写入 REQUIREMENTS。
> 本轮做完后，当前阶段的界面工作即告完成（医疗文件库界面仍受 B2–B6 安全审批约束，不在本轮）。

## 0. 角色、目标与顺序

你是 HRT Log 的执行工程师。本轮依次完成三件事：

- **PR-A（UI-03A）**：抽屉改为“按信息来源”分组，4 个入口改名；同步 `AGENTS.md` 的交付门槛；更新真机验收清单。
- **PR-B（UI-02B）**：执行已归档 UI-02 任务书第 3 节的 `NotesApp` 状态拆分（纯重构）。
- **收尾**：两个 PR 合并后写 UI 阶段总结。

必须严格按 A → B → 收尾的顺序，前一个合并并复验主线后才开始下一个。

优先级：数据与隐私安全 > 现有行为不变 > 无障碍与四语 > 视觉改进。

## 1. 开工前必须做

1. 按 `CLAUDE.md` 顺序阅读 `docs/REQUIREMENTS.md`（重点 §42、§80、§87、§96、§97）、`docs/HANDOFF.md`、`docs/licensing.md`、报告 60 和 61、`docs/BACKLOG.md` 顶部。
2. 刷新真实 GitHub 状态，确认默认分支已包含 UI-01（#38）与 UI-02A（#40），且最新默认分支 CI 全部成功。
3. 在 `docs/REQUIREMENTS.md` 末尾新增 **§98 UI-03（2026-10-15）**，概括本任务书授权范围、禁止事项和下文的分组决定；原文归档到 `docs/audits/product-quality-2026-10/sources/ui03/`。
4. 核对 `B0-protected-file-matrix.json`：只改 HISTORY_ONLY 类文件和不在矩阵中的新文件。**任何 PK_DISPLAY_REVIEW 或更严格类别的文件一律不改**；若某项必须触及，暂停该子项并在报告中说明，其余子项继续。
5. 每完成一小节就提交、推送并更新 `docs/HANDOFF.md`。

## 2. PR-A：UI-03A 抽屉分组与改名

分支：`feature/ui03a-drawer-groups-20261015`，从最新默认分支建立。

### A1. 分组决定（负责人已决定）

分类标准统一为**“信息从哪里来”**，不再混用“使用频率”和“领域”两种标准。依据：

- 卡片分类和树测试的 IA 方法；
- Apple 健康“健康记录”、MyChart、法国 Mon espace santé 都把化验结果归入病历或医疗档案；
- FHIR 把化验报告（DiagnosticReport，含原件）和单项数值（Observation）作为临床记录的一部分。

请把以上依据写进 §98。

修改 `AppShell.kt` 中的 `DRAWER_GROUPS`：

| 位置 | 入口（顺序即显示顺序） | 组标题 key |
|---|---|---|
| 顶部（无标题、无分隔线） | `CALENDAR`、`TIMELINE` | 无 |
| 第 1 组 | `HISTORY`、`WELLBEING` | `drawer_group_journal` |
| 第 2 组 | `MEDICATIONS`、`STOCK`、`CONCENTRATION` | `drawer_group_medication` |
| 第 3 组 | `MEDICAL`、`VISITS`、`LABS` | `drawer_group_records` |
| 分隔线后 | `SETTINGS`、`ABOUT` | 无（保持现状） |

- 当前 `DRAWER_GROUPS` 的类型是 `List<Pair<Int?, List<Destination>>>`，其中 `null` 表示“分隔线”。顶部无标题组需要和“分隔线组”区分开：可以把它改成一个小的 sealed 类型或 data class（如 `DrawerSection.Top / Group / Divider`），**只改抽屉内部表示**，不改 `Destination` 枚举的顺序和定义，以免影响 `rememberSaveable` 与通知入口。
- 12 个入口全部保留；复诊仍是独立入口，**不改名**（§42/§449）。
- 组标题继续使用 heading 语义与 `drawer-group:<key>` 测试 tag 规则。
- 旧组标题 key（`drawer_group_daily`、`drawer_group_treatment`、`drawer_group_medical`、`drawer_group_body`）在四语中删除；删除前 grep 确认没有其他引用。

### A2. 组标题四语（新 key）

| key | zh-CN | zh-TW | en | fr |
|---|---|---|---|---|
| `drawer_group_journal` | 日常记录 | 日常紀錄 | Daily log | Journal |
| `drawer_group_medication` | 用药管理 | 用藥管理 | Medication | Traitement |
| `drawer_group_records` | 医疗档案 | 醫療檔案 | Medical records | Dossier médical |

注意：第 3 组标题与“医疗档案”入口同名。如果 TalkBack 连续朗读“医疗档案 标题”“医疗档案 按钮”会显得重复，就把 zh 组标题改为“就医资料”，zh-TW 为“就醫資料”，en 为“Care records”，fr 为“Suivi médical”，并在报告中说明理由。用测试证明两者朗读可区分。

### A3. 入口改名

| Destination | 当前 key | zh-CN | zh-TW | en | fr |
|---|---|---|---|---|---|
| HISTORY | `history` | 历史 → **服药记录** | 服藥紀錄 | History → **Dose log** | Journal des prises |
| TIMELINE | `timeline` | 时间线 → **治疗时间线** | 治療時間軸 | Timeline → **Treatment timeline** | Chronologie du traitement |
| MEDICATIONS | `medications` | 药物 → **药物方案** | 藥物方案 | Medications → **Medication plans** | Plans de traitement |
| CONCENTRATION | `concentration` | 血药浓度 → **浓度估算** | 濃度估算 | （已是 Estimated levels，不改） | Niveaux estimés |

规则：

- 先 grep 每个 key 的全部引用。
  - 若该 key **只**用作导航和页面标题，直接改译文。
  - 若还有其他含义的用法（比如某按钮上的“历史”指别的东西），则为导航新建 `nav_*` key，`Destination.title` 改指新 key，原 key 不动。
- `concentration` 的引用若出现在 PK_DISPLAY_REVIEW 类文件或浓度页相关测试（如 `UnifiedConcentrationTranslationsTest`）中，**不得改原 key**：只为抽屉和顶栏新建 `nav_concentration`；若连这样也会触碰受保护文件或断言，就暂停这一个改名并报告。
- 现有译文先读出来，fr 与 zh-TW 若已有更贴切的措辞，可以保留，但要在报告里给出对照与理由。
- 改名后，顶栏标题、抽屉、设置总览等引用处应一致；`TranslationsTest` 通过。

### A4. `AGENTS.md` 与 `CLAUDE.md` 同步

`CLAUDE.md` 里有一条“时期稳定性测试是交付门槛（2026-10-08）”：`PeriodStabilityTest` 必跑，不得跳过、删减或放宽。`AGENTS.md` 缺少这一条。

- 把这条原样补进 `AGENTS.md`。
- 逐段比较两份文件，其余规则若有差异也一并对齐，并在报告中列出差异。
- 只改这两份说明文件的规则文字，不改其他行为。

### A5. 真机验收清单更新

在 `docs/ui-audit/2026-10-15-ui02/manual-checklist.md` 追加 UI-03A 部分（或新建 `2026-10-15-ui03/manual-checklist.md` 并互相链接）。内容包括：

- 抽屉分组的 TalkBack 朗读顺序；
- 改名后顶栏标题与抽屉一致；
- 320dp 宽度与最大系统字号下可滚动到“关于”。

结果留给负责人回填，不得标记为通过。

### A6. 测试

先写失败测试，再实现：

1. 抽屉顺序与分组完全符合 A1 表格（四语 × 320/411dp × 字号 1/2）。
2. 3 个组标题都是 heading，顶部两项前没有标题和分隔线。
3. 12 个入口全部可达，点击后到达正确页面。
4. 改名在抽屉和顶栏一致。
5. A2 的朗读可区分性。

必须通过（不得放宽）：

- `PeriodStabilityTest`（交付门槛）；
- `DrawerScrollUiTest`、`AppShellBackNavigationTest`、`TimelineAndDrawerHierarchyTest`、`TranslationsTest`、`ButtonLayoutRegressionTest`；
- UI-02A 新增的设置页、返回键、对比度测试；
- 完整 `:app:testFullDebugUnitTest`、lint、Android 设备测试和全部 CI job。

`TimelineAndDrawerHierarchyTest` 等既有测试若写死了旧分组，按新分组更新期望值，同时保留“全部入口可达、heading 语义、可滚动”等原有断言含义，并在报告中逐条说明。

截图：抽屉与 4 个改名页面的顶栏，四语 × 320/411dp × 字号 1/2 × 明暗，前后对比，存入 `docs/ui-audit/2026-10-15-ui03/`，只用合成数据。

报告：`docs/audits/product-quality-2026-10/62-ui03a-drawer-groups.md`（编号以实际下一个为准）。

## 3. PR-B：UI-02B `NotesApp` 状态拆分

分支：`feature/ui02b-appshell-state-20261015`，在 PR-A 合并并复验主线后，从最新默认分支建立。

**完整执行已归档任务书 `docs/audits/product-quality-2026-10/sources/ui02/ui02-task-book-2026-10-15.md` 的第 3 节，全部规则不变**。其中最关键的几条：

- 纯重构：不改 `NotesViewModel`、仓库、数据类、PK 相关文件、字符串和可见布局。
- **不允许修改任何既有测试断言**；只允许因 composable 移动修改 import 或调用方签名，并逐条列出。
- 重构前后用截图测试生成同条件截图，逐张比较，要求**像素一致**，差异必须修正，不得接受。
- 跨页面流程（通知入口、复核横幅、PK 免责声明首次弹出、医疗保存失败 snackbar、服药后身心提示）现有测试未覆盖的，先补测试并在重构前跑通，再重构。
- `rememberSaveable` 与 `remember` 的语义逐个保持，不顺手修改。

另外，PR-A 新增的抽屉结构也要包含在重构后的截图比对中。

报告：下一个编号，`…-ui02b-appshell-state.md`。

## 4. 收尾：UI 阶段总结

两个 PR 都合并并复验主线后，写 `docs/audits/product-quality-2026-10/<下一个编号>-ui-phase-summary.md`：

- UI-01、UI-02A、UI-03A、UI-02B 各自改了什么，各一段；附关键前后对比截图链接。
- 信息架构的最终形态（抽屉结构图、设置结构图）。
- 测试资产清单：新增测试、截图测试的开关和用法、对比度断言。
- 仍为 NOT_RUN 的项（真机 TalkBack 等）和负责人清单的位置。
- 留待负责人决定的事：
  - 底部导航是否立项（引用报告 61 第 6 节）；
  - 复诊何时并入医疗档案（§42 条件）；
  - 医疗文件库界面等待 B2–B6。
- 更新 `docs/BACKLOG.md`：删除已完成的 UI P2 项，只保留上面的待决定事项；更新 `docs/STATUS.md`。

## 5. 明确不做

- 不改 Room schema、实体、迁移、备份格式、PNBAK、加密、应用锁、伪装外壳及其隔离。
- 不改 PK 引擎、参数、浓度页及图表、导出 PDF 内容（任何 PK_DISPLAY_REVIEW 文件）。
- 不新增权限、网络、埋点或第三方库。
- 不实施底部导航；不合并复诊与医疗档案；不开放附件或医疗文件入口；不改 MED-02B6 演示。
- 不改 versionCode/versionName，不签名、不上传、不交付 APK。
- 不删除、跳过或放宽任何既有测试断言（PR-A 的分组期望值更新除外，且需逐条说明；PR-B 适用更严格的“零断言修改”）。

## 6. 交付与合并

- 每个 PR 的正文包括：改动文件清单（含保护矩阵类别）、四语对照、前后截图、测试与 CI 链接、迁移或更新的断言清单、NOT_RUN 项、待负责人决定事项。
- **合并**：每个 PR 在最终 HEAD 全部必需检查成功、评审线程已解决后，执行普通合并（不用 `--auto`、不用 `--admin`、不改 ruleset），随后复验默认分支 CI 并记录。任何检查失败都不得合并。
- 全部完成后同步更新 `docs/HANDOFF.md`、`docs/STATUS.md`、`docs/BACKLOG.md`。最后在 PR 或报告里用一段中文向负责人总结：UI 阶段完成了什么、还需要她亲自做什么（真机清单）。
