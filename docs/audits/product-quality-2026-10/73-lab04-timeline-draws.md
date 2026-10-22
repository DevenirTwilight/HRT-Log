# 73 — LAB-04：时间线把一次化验合并成一条（2026-10-22）

- **任务书**：`sources/lab04/lab04-prompt.md`（SHA-256 `4438829d…edbb854a42`），REQUIREMENTS §137。
- **决定**：负责人推翻 LAB-02 ADR Q2（§112 原为“时间线不合并显示”）。§112 下已追加推翻记录，原文保留。
- **分支**：`feature/lab04-timeline-draws-20261022`，基于默认分支 bdba7e45。
- **性质**：只改显示层。
  - `LongitudinalProjection.kt`、`PeriodTimeline.kt` 一个字节都没改；测试固定了它们在基线上的 SHA-256。
  - 没有改 `LabsScreen.kt`、`LabContextSection.kt`、`Exports.kt`，也没有改任何 PK 或校准文件。
  - 不写数据，不建化验单；不新增依赖或权限；不改 versionCode。

## 1. 改动文件与保护矩阵类别

| 文件 | 类别 | 改动 |
|---|---|---|
| `ui/TimelineLabDraws.kt`（新） | 不在矩阵 | 合并规则：`mergeable` 复用 `labDraws()`，`fold` 按容器折叠；合并行 `TimelineLabDrawRow` |
| `ui/LongitudinalScreen.kt` | HISTORY_ONLY | 列出事件时折叠化验；筛选和定位能识别合并行；新增 `onLabPanel` 回调参数 |
| `ui/AppShell.kt` | HISTORY_ONLY | 把 `onLabPanel` 接到现有的化验单详情 `labDialogs.panel`（`LabPanelHost` 是全局的，在时间线页面也能弹出） |
| `res/values*/strings.xml`（4 种语言） | HISTORY_ONLY | 新增 5 条文案，见第 6 节 |

## 2. 分组一致性证明

- 时间线和化验首页用**同一个函数** `labDraws()`（定义在 `LabBatchEntry.kt`），时间线没有另写分组规则。
  - `TimelineLabDraws.mergeable` 把时间线上所有化验事件的数值交给 `labDraws()`，只保留 2 项以上的组。
  - 再检查同一组的成员是否都在同一个容器里：同一时期，并且同为“即将发生”或同不是。
- `Lab04GroupingTest.sameGroupsAsTheLabsPage`：对同一组合成数据，时间线合并的每一组，与化验首页 `labDraws()` 的 2 项以上的组逐一相等（成员 id 和顺序都相同，`LabDraw` 整体相等）。
- 合成数据覆盖的情况：
  - 6 项化验单，其中 2 项超出范围；
  - 1 项 PRL；
  - 无化验单、同时同机构的两项：合并；
  - 同时、不同机构：不合并；
  - 同一天、同机构、不同时间：不合并。

## 3. 投影不变的对照测试

`Lab04ProjectionUnchangedTest` 做两项检查：

1. **输出对照**：
   - 输入：固定的合成数据（一个时期、6 项化验单、无化验单的数值、一个将来的化验），固定时间 2026-04-01、UTC。
   - 把 `PeriodTimelineProjection.build` 的输出逐字段写成文本：时期的 key / from / until；每个事件的 key、kind、at、date、displayPeriodKey、exactRegimenIds、dateOnly、source。
   - 对这段文本取 SHA-256。
   - 期望值 `3c598bea…51c3292` 是在**基线提交 bdba7e45 上运行同一个测试文件**得到的。本分支结果与之相同。
2. **源码固定**：`LongitudinalProjection.kt` 和 `PeriodTimeline.kt` 的 SHA-256 等于基线字节。

`PeriodStabilityTest` 随完整测试运行，结果见第 8 节。

## 4. 合并行

- 沿用 UI-05 / UI-07 的事件行：化验类型图标、文字、右箭头。
- **第一行**：“化验 · 6 项”。有超出范围的项时，标签“2 项超出范围”在同一行末尾。标签用 UI-06 的 attention 令牌，不用红色，`Lab04GroupingTest.noRedInTheMergedRow` 检查源码。
  - 偏离：法语、320dp、2 倍字号下标题和标签放不下一行，标签会换到标题下面。最初放在行尾时，标签把整行挤成竖排，见截图。
- **第二行**：“日期 · 机构”，机构为空时省略。机构取化验单上的，没有化验单时取第一项的。
- **第三行**：项目名，次要小字，单行省略。名称用化验首页的 `analyteLabel()`；中文为全名加缩写，例如“卵泡刺激素（FSH）”，顺序与化验首页相同。
- **超出范围计数**：用化验首页的 `labFlag()`，见 `outOfRangeCountIsLabFlag`。
- **TalkBack**：整行一个节点，朗读“化验，6 项，日期，机构，项目名……”，有超出时追加“2 项超出参考范围”。四种语言见 `Lab04SpokenTest`。
- **点击**（`Lab04TimelineUiTest.tapsOpenPanelLabsPageOrTheSingleDetail`）：
  - 有化验单：打开现有的化验单详情 `LabPanelDialog`，经 `labDialogs.panel`；
  - 没有化验单的显示组：打开化验首页。化验首页没有“定位到某一组”的参数，所以只打开页面，不定位；
  - 单项：在时间线上打开原单项详情，行为不变。

每个数值仍能从时间线到达：合并的数值经化验单详情，或化验首页的组卡片；其余数值经原单项详情。`panelSixToOneLooseByTimeAndLaboratory` 检查所有数值都在某一行里。

## 5. 其他显示

- **筛选“化验”**：按合并后的条数列出，`oneRowPerDrawAndTheLabFilterCountsMergedRows` 检查为 5 条。筛选标签本身不显示数字。
- **“即将发生”和“方案未知”区**：用同一个 `fold`，规则相同。
- **未统一的位置**（本任务不改，仍逐条显示，供负责人决定是否另立任务）：
  1. 医疗档案的“时间范围内的化验”（`MedicalRecordsScreen`）；
  2. 复诊页的化验列表（`VisitsScreen`）；
  3. 导出 PDF（`Exports.kt`）；
  4. 浓度页上的化验点（`LabContextSection` 和浓度图）。

## 6. 文案（四种语言，`TranslationsTest`）

| 键 | zh-CN | en | zh-TW | fr |
|---|---|---|---|---|
| timeline_lab_draw_title | 化验 · %1$d 项 | Lab test · %1$d results | 檢驗 · %1$d 項 | Analyse · %1$d résultats |
| timeline_lab_draw_out_of_range | %1$d 项超出范围 | %1$d out of range | %1$d 項超出範圍 | %1$d hors intervalle |
| timeline_lab_draw_spoken_out | %1$d 项超出参考范围 | %1$d outside the reference range | %1$d 項超出參考範圍 | %1$d en dehors de l’intervalle de référence |
| timeline_lab_draw_spoken_title | 化验，%1$d 项 | Lab test, %1$d results | 檢驗，%1$d 項 | Analyse, %1$d résultats |
| lab_names_join | 、 | , | 、 | , |

zh-TW 和 fr 沿用现有术语：“檢驗”与 `labs` 一致；“intervalle de référence”与 `lab_range_optional` 一致。最后两条是朗读和名称连接用的附加键。

## 7. 断言迁移

- 已检查现有时间线测试：`Ui02Test`、`LongitudinalUiTest`、`Ui05TimelineTest`、`TimelineV2FlowTest`、`TimelineEditUiTest`、`ImportedTimelineUiTest`、`TimelineAndDrawerHierarchyTest`。
- 用到化验的只有 `Ui02Test` 和 `LongitudinalUiTest` 中的单个化验 `lab:8`。只有 1 项时仍是单条，键和点击行为不变，所以这些断言**无需迁移**，也没有删改。
- 完整测试结果见第 8 节。

## 8. 验证

- 本地完整 `:app:testFullDebugUnitTest` 2049 项，0 失败；394 项为默认关闭的截图测试等，按设计跳过。
- `lintFullDebug` 通过。
- LAB-04 新增测试 14 项：Grouping 6 项、ProjectionUnchanged 2 项、TimelineUi 2 项、Spoken 4 种语言；
- 必须通过的测试全部 PASS：PeriodStabilityTest、时间线五项、LAB-01 至 LAB-03、`Lab01UnaffectedTest`、`ButtonLayoutRegressionTest`、`TranslationsTest`。

CI 全部 job 见 PR。

## 9. 截图

`docs/ui-audit/2026-10-22-lab04/`，全部为合成数据：

- `before/`（基线 bdba7e45）和 `after/`：
  - 内容：治疗时间线的三个位置：显示组、单项、6 项化验单；
  - 组合：zh-CN、en、fr、zh-TW × 320/411dp × 字号 1/2 × 浅色/深色。
- `compare/`：前后并排，左为前，右为后。

截图量化为 256 色以控制仓库体积。

## 10. NOT_RUN

- 真机：时间线上一次化验是否只显示一条，点击能否打开详情。已加入真机清单第 16 节，由负责人回填。
- 真机 TalkBack。
