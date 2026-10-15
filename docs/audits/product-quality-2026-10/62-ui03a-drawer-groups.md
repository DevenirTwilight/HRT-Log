# 62 — UI-03A：抽屉按信息来源分组、入口改名、AGENTS.md 同步（2026-10-15）

- 任务书：`sources/ui03/ui03-prompt.md` 第 2 节。授权与禁止事项见 REQUIREMENTS §99。任务书写的是 §98，但 §98 已是 BDPM，所以顺延为 §99。
- 分支：`feature/ui03a-drawer-groups-20261015`，基于主线 8e72861。

## 1. 改动文件与保护矩阵类别

| 文件 | 类别 | 改动 |
|---|---|---|
| `app/src/main/java/net/plainnotes/app/ui/AppShell.kt` | HISTORY_ONLY | `DrawerSection` 类型与新 `DRAWER_GROUPS`；`HISTORY` 改指 `nav_history`；分隔线加 `drawer-divider` tag；顶栏标题加 `top-title` tag（只加 tag，不改外观） |
| `app/src/main/res/values{,-zh,-b+zh+Hant,-fr}/strings.xml` | HISTORY_ONLY | 新增 3 个组标题 key 与 `nav_history`；删除旧组标题 4 个 key；`timeline`、`medications`、`concentration` 原地改译文 |
| `app/src/test/.../Ui03DrawerTest.kt`（新） | 不在矩阵 | `Ui03DrawerTest`（64 例）、`Ui03ShellTitleTest`（4 例） |
| `app/src/test/.../Ui03ScreenshotTest.kt`（新） | 不在矩阵 | 截图证据，默认跳过（`UI03_SHOTS`） |
| `app/src/test/.../UiHierarchyTest.kt` | 不在矩阵 | 期望值更新，见第 5 节 |
| `app/src/androidTestFull/.../CalendarIntakeAndroidTest.kt` | 不在矩阵 | 查找抽屉入口的方式更新，见第 5 节 |
| `AGENTS.md` | 不在矩阵 | 补交付门槛规则，见第 6 节 |
| `docs/…` | 文档 | REQUIREMENTS §99、HANDOFF、任务书原文、人工清单、本报告、截图 |

PK_DISPLAY_REVIEW 文件（`ConcentrationScreen.kt` 等）一个都没有改。

## 2. 新抽屉结构（A1）

```
HRT 日志
  日历
  治疗时间线
日常记录                 ← heading，tag drawer-group:journal
  服药记录
  身心状态
用药管理                 ← heading，tag drawer-group:medication
  药物方案
  库存
  浓度估算
就医资料                 ← heading，tag drawer-group:records
  医疗档案
  复诊
  化验
──────────────           ← tag drawer-divider
  设置
  关于
```

- 实现：`DrawerSection.Top / Group(key, heading, items) / Divider` 只是抽屉的内部表示。`Destination` 枚举的顺序和定义完全没变，所以 `rememberSaveable` 和通知入口不受影响。
- 12 个入口全部保留；复诊仍是独立入口，名字不变。
- tag 规则变化：旧实现的 `drawer-group:<资源 id 整数>` 不稳定，现在改为 `drawer-group:<key>`（journal / medication / records）。没有任何既有测试用过旧 tag。

## 3. 四语对照

### 3.1 组标题（A2）

| key | zh-CN | zh-TW | en | fr |
|---|---|---|---|---|
| `drawer_group_journal` | 日常记录 | 日常紀錄 | Daily log | Journal |
| `drawer_group_medication` | 用药管理 | 用藥管理 | Medication | Traitement |
| `drawer_group_records` | **就医资料** | **就醫資料** | **Care records** | **Suivi médical** |

**第 3 组用的是任务书给出的备选名称。** 原因：
- 首选名称在四种语言里都和紧随其后的入口完全相同：医疗档案 / 醫療檔案 / Medical records / Dossier médical。
- TalkBack 会连读“医疗档案，标题”“医疗档案”，听起来重复，也分不清哪个是标题、哪个能点。
- 改用备选名称后，`Ui03DrawerTest.headingsReadDifferentlyFromEveryEntry` 在四语中验证：每个组标题都不和任何入口同名，3 个标题互不相同，12 个入口也互不相同。

删除的旧 key：`drawer_group_daily`、`drawer_group_treatment`、`drawer_group_medical`、`drawer_group_body`，四语各 4 个。删除前 grep 确认只在 `AppShell.kt` 里有引用。

### 3.2 入口改名（A3）

| Destination | key 处理 | zh-CN | zh-TW | en | fr |
|---|---|---|---|---|---|
| HISTORY | **新建 `nav_history`**，原 `history` 不动 | 历史 → 服药记录 | 歷史 → 服藥紀錄 | History → Dose log | Historique → Journal des prises |
| TIMELINE | 原地修改 `timeline` | 时间线 → 治疗时间线 | 時間線 → 治療時間軸 | Timeline → Treatment timeline | Chronologie → Chronologie du traitement |
| MEDICATIONS | 原地修改 `medications` | 药物 → 药物方案 | 藥物 → 藥物方案 | Medications → Medication plans | Médicaments → Plans de traitement |
| CONCENTRATION | 原地修改 `concentration` | 血药浓度 → 浓度估算 | 血藥濃度 → 濃度估算 | Estimated levels（不改） | Concentrations estimées → Niveaux estimés |

引用核查：

- **`history`**：`ConcentrationScreen.kt`（PK_DISPLAY_REVIEW）把它用作“补记实际剂量”的按钮文字。原地改译文会改变 PK 页面的显示，所以按规则新建 `nav_history`，`Destination.HISTORY.title` 改指它，原 key 不动。
- **`timeline`、`medications`、`concentration`**：只在 `Destination` 枚举中使用，其余引用都在测试里，没有出现在 PK_DISPLAY_REVIEW 文件或 `UnifiedConcentrationTranslationsTest` 中，所以原地改译文。
  - `DisguiseFlowAndroidTest` 用 `R.string.medications`、`R.string.concentration` 断言伪装外壳里不出现这些文字。断言没改，检查的是新名称，强度不变。
- **既有措辞对照**：fr 与 zh-TW 都采用任务书的新译法，理由如下。
  - fr “Concentrations estimées” 虽然准确，但“Niveaux estimés”与英文“Estimated levels”一致。
  - zh-TW“時間軸”是台湾常用说法。

全部采用后，抽屉、顶栏和返回后的页面名称一致：`Ui03ShellTitleTest` 在四语中逐一点开 12 个入口，比对顶栏标题与抽屉名称。设置总览不显示这 4 个名字。

### 3.3 遗留的不一致（等待 PK 显示审查授权，未改）

- 浓度页的“补记实际剂量”按钮仍显示 `history`（历史 / History）。点击它会打开现在叫“服药记录”的页面。
- `missing_actual_dose`（“缺少实际剂量，可在历史页编辑服药记录”）也显示在浓度页上，仍称“历史页”。

这两处都属于浓度页的可见文字。按任务书，PK_DISPLAY_REVIEW 范围不改，留给负责人决定是否单独授权统一措辞。

## 4. 测试（A6）

两个测试类都在实现之前先写好。在旧抽屉上运行（临时补回旧 key 以便编译）时失败：
- `orderAndGroupsMatchTheDecision` 与 `threeHeadingsAndNothingBeforeTheTopEntries`：16/16 失败（旧顺序、4 个标题、tag 是整数）；
- `Ui03ShellTitleTest`：4/4 失败。

| # | 要求 | 测试 |
|---|---|---|
| 1 | 顺序与分组符合 A1（四语 × 320/411dp × 字号 1/2） | `Ui03DrawerTest.orderAndGroupsMatchTheDecision`：按语义树读出抽屉的直接子节点（标题 key、分隔线、入口），与 A1 表逐项比较 |
| 2 | 3 个组标题都是 heading；顶部两项前没有标题和分隔线 | `threeHeadingsAndNothingBeforeTheTopEntries` |
| 3 | 12 个入口都可达并到达正确页面 | `everyEntryIsReachableAndPicksItsDestination`（16 种组合）；`Ui03ShellTitleTest`（真实外壳，四语） |
| 4 | 抽屉与顶栏名称一致 | `Ui03ShellTitleTest.drawerEntriesLandOnTheirPageWithTheSameTitle`，含 4 个改名的四语逐字比对 |
| 5 | 组标题与入口朗读可区分 | `headingsReadDifferentlyFromEveryEntry` |

本地实现后通过，167 例，0 失败，0 跳过。包括：
- 新测试：`Ui03DrawerTest` 64 例、`Ui03ShellTitleTest` 4 例；
- 交付门槛 `PeriodStabilityTest`；
- `TimelineAndDrawerHierarchyTest`、`DrawerScrollUiTest`、`AppShellBackNavigationTest`、`TranslationsTest`、`ButtonLayoutRegressionTest`；
- UI-02A 的 `SettingsOverviewTest`、`TimelineFilterAndHomeStockTest`、`Ui02ContrastTest`；
- `UnifiedConcentrationTranslationsTest`。

完整单元测试、lint 和设备测试的结果见 CI（PR 正文）。

## 5. 更新的既有断言（逐条说明）

1. `UiHierarchyTest.kt` → `TimelineAndDrawerHierarchyTest.drawerKeepsAllTwelveEntriesUnderHeadings`：
   - `DRAWER_GROUPS.flatMap { it.second }` 改为 `{ it.items }`，`sumOf { it.second.size }` 改为 `{ it.items.size }`。这只是新类型的访问方式；“全部 12 个入口、每个只出现一次”的含义不变。
   - `assertEquals(4, headings.size)` 改为 `assertEquals(3, headings.size)`，因为新分组正好 3 个标题。heading 语义检查保留。
   - 在 320dp、字号 2 下逐个滚动、显示并点击 12 个入口的断言，原样保留。
2. `CalendarIntakeAndroidTest`（设备测试）：
   - 抽屉中找“历史”入口的方式从 `s(R.string.history)` 改为 `s(Destination.HISTORY.title)`，因为入口文字现在来自 `nav_history`。
   - 测试目的（离开日历后到达服药记录页、返回键行为）不变，没有放宽任何断言。

没有其他测试文件改动。

## 6. AGENTS.md 与 CLAUDE.md 逐段比较（A4）

| 段落 | 修改前差异 | 处理 |
|---|---|---|
| 开头“开始任何工作前，按顺序阅读”1–3 | 相同 | — |
| 当前构建规则（2026-10-07） | AGENTS.md 缺少“时期稳定性测试是交付门槛（2026-10-08）”一条 | 按 CLAUDE.md 原文逐字补入，位置也相同（play 废弃规则之前） |
| 交接规则 4 条 | 相同 | — |

修改后 `diff AGENTS.md CLAUDE.md` 没有输出，两份文件完全一致。只改了规则文字。

## 7. 截图

保存在 `docs/ui-audit/2026-10-15-ui03/`，说明见该目录的 [README](../../ui-audit/2026-10-15-ui03/README.md)。

- 内容：抽屉、抽屉底部，以及 4 个改名页面。
- 组合：四语 × 320/411dp × 字号 1/2 × 明暗。
- 数量：改前（主线 8e72861）和改后各 192 张。
- 像素比较：184 张有变化。8 张一致，都是英文浓度页，它的名称没有改。

对比图：
- [抽屉 zh-CN 411dp](../../ui-audit/2026-10-15-ui03/compare/01_drawer_zh-rCN_411dp_1x_light.png)
- [抽屉 fr 320dp 字号 2](../../ui-audit/2026-10-15-ui03/compare/01_drawer_fr-rFR_320dp_2x_light.png)
- [抽屉底部 zh-TW 320dp 暗色](../../ui-audit/2026-10-15-ui03/compare/02_drawer_end_zh-rTW_320dp_1x_dark.png)
- [服药记录](../../ui-audit/2026-10-15-ui03/compare/10_history_zh-rCN_411dp_1x_light.png)
- [治疗时间线](../../ui-audit/2026-10-15-ui03/compare/10_timeline_zh-rCN_411dp_1x_light.png)
- [药物方案](../../ui-audit/2026-10-15-ui03/compare/10_medications_zh-rCN_411dp_1x_light.png)
- [浓度估算](../../ui-audit/2026-10-15-ui03/compare/10_concentration_zh-rCN_411dp_1x_light.png)

## 8. 人工验收（NOT_RUN）

`docs/ui-audit/2026-10-15-ui03/manual-checklist.md` 第 8 节共 4 项，全部 NOT_RUN：
- TalkBack 朗读顺序；
- 顶栏与抽屉名称一致；
- 320dp 加最大字号时能滚动到“关于”；
- 通知入口和伪装模式下抽屉一致。

UI-02 清单中旧分组的 1.3 已注明作废，并链接到新清单。

## 9. 留待负责人决定

- 3.3 的两处浓度页文字（“历史”按钮、“历史页”）是否单独授权，统一为“服药记录”。
- 其他事项沿用之前的待决定清单：底部导航（报告 61 第 6 节）；复诊并入医疗档案的时机（§42）；医疗文件库界面等待 B2–B6。
