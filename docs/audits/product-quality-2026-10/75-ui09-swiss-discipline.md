# 75 — UI-09：借用瑞士风格的排版纪律（应用内小幅精修）（2026-10-23）

依据：负责人任务书 `sources/ui09/ui09-prompt.md`（SHA-256 09e39ce7…），REQUIREMENTS §146。风格方向、颜色令牌、圆角、动效、字体、字号下限都没有改。红色仍只用于错误。

## 1. 左对齐审计（A）

扫描范围：`app/src/main` 中所有 `TextAlign.Center`、`Alignment.CenterHorizontally`、`Arrangement.Center`。基线 d2223c0a 上共 12 处，现在剩 10 处。

**改为左对齐（2 处，同一个组件）**

| 位置 | 原用途 | 改动 |
|---|---|---|
| `Components.kt` `EmptyState` 的 Column | 图标、标题、正文整体居中 | 改为 `Alignment.Start`；左右内边距由 32dp 改为 16dp（网格页边距），上下仍为 48dp |
| `Components.kt` `EmptyState` 的正文 | `TextAlign.Center` | 去掉，默认左对齐 |

所有使用 `EmptyState` 的空页面（服药记录、库存、治疗时间线、化验等）都随之改为左对齐。点击区域和语义没有变化：可选的操作按钮（`action`）仍是同一个按钮，大小不变，只是随列左对齐；文字节点和顺序不变。

**保留居中的例外（10 处）**

| 位置 | 内容 | 例外类别 |
|---|---|---|
| `CalendarScreen.kt:219` | 月份标题，夹在左右箭头之间 | 日历月份标题 |
| `CalendarScreen.kt:234` | 星期表头，对齐下面的日期列 | 日历格 |
| `CalendarScreen.kt:180/181` | 首页“本周”格子中的星期和日期 | 日历格 |
| `CalendarScreen.kt:267` | 月历日期格 | 日历格 |
| `CalendarScreen.kt:300` | “最近 7 天”格子 | 日历格 |
| `MedicalRecordsScreen.kt:124` | 下次复诊的日期块（月 / 日 / 星期） | 胶囊内的文字 |
| `WellbeingDaily.kt:84` | 前一天 / 后一天箭头之间的日期 | 与月份标题同类：夹在箭头之间 |
| `AdaptiveControls.kt:88` | 分段控件的标签 | 分段控件内部 |
| `LockScreen.kt:29` | 应用锁的 PIN 页，标题和圆点在数字键盘上方居中 | 按钮组（键盘）及其上方的短标签；属于应用锁，本轮不改 |

不在扫描结果中、但也是居中的情况：
- 按钮、图标按钮、徽章内部的 `Box(contentAlignment = Alignment.Center)`：属于按钮或胶囊内部，不涉及段落文字。
- `PrivateNotesScreen` 的伪装空状态：属于伪装外壳，保持原样。

`Ui09SourceGuardTest.onlyTheListedExceptionsStayCentred` 固定了这份清单（按文件计数），并断言 `EmptyState` 中没有居中用法。之后新增居中用法会被测试拦下，需要在报告中说明理由。

## 2. 网格与基线（B）

- `core/ui` `Tokens.kt` 在 `Spacing` 旁新增 `Grid`：
  - 4 栏，页边距 16dp，栏间距 16dp，基线 4dp；
  - 常用间距 4/8/12/16/24/32；
  - 卡片内边距 16 / 20。
- 本轮涉及的页面中，`padding`、`spacedBy`、`PaddingValues`、`Spacer` 里不是 4 的倍数的数值，都改为最接近的 4 的倍数。共 47 处：
  - 6→8：19 处；
  - 10→8：15 处；
  - 2→4：6 处；
  - 14→16：5 处；
  - 3→4：2 处。
- 每处位置变化都不超过 4dp，没有超过的情况。

| 文件 | 处数 | 行号（原值→新值） |
|---|---|---|
| `TodayCard.kt` | 2 | 93（10→8）、126（10→8） |
| `CalendarScreen.kt` | 13 | 92（14→16）、93（10→8）、127（10→8）、170（6→8）、179（10→8）、180（6→8）、181（6→8）、269（2→4）、299（6→8）、300（3→4）、325（2→4）、330（2→4）、476（14→16） |
| `StockScreen.kt` | 4 | 77（10→8）、139（2→4）、225（10→8）、269（6→8） |
| `LongitudinalScreen.kt` | 4 | 183（10→8）、234（10→8）、452（10→8）、484（10→8） |
| `MedicalRecordsScreen.kt` | 3 | 121（14→16）、130（2→4）、163（2→4） |
| `VisitsScreen.kt` | 4 | 99（6→8）、108（6→8）、128（6→8）、134（6→8） |
| `WellbeingDaily.kt` | 6 | 67（10→8）、115（6→8）、123（6→8）、159（10→8）、192（6→8）、242（6→8） |
| `WellbeingScreen.kt` | 2 | 49（14→16）、76（6→8） |
| `HistoryScreen.kt` | 8 | 112（6→8）、115（6→8）、127（6→8）、128（10→8）、157（6→8）、197（14→16）、198（3→4）、212（6→8） |
| `StageReviewScreen.kt` | 1 | 41（10→8） |

`UnconfirmedCard.kt` 和 `WellbeingHub.kt` 也在检查范围内，原本就没有非 4 倍数的值。

只改了间距调用中的数值。图标尺寸、线宽、圆角、最小触控尺寸等不属于间距，没有改。

## 3. 细线分隔（C）

新增 `core/ui` `Swiss.kt`：
- `SectionRule`：1dp `HorizontalDivider`，颜色为 `outlineVariant`，高对比度模式下为 `outline`（`sectionRuleColor()`）；
- `NumberedHeading`、`NumberedSection`：带编号的标题和区块。

使用位置：
- **复诊详情**：复诊信息卡和“确认已就诊”卡仍是卡片（属于操作区）。其下的区块（医疗模式下依次为化验、问题清单、资料包；普通模式下为问题清单、资料包）去掉浅色卡片底，改为细线分隔的编号区块。第一个区块上方不画线。
- **阶段回顾**：效果、耐受、风险因素、满意度四部分之间原有的 `HorizontalDivider` 换成 `SectionRule`，高对比度下自动变为 `outline`。
- **服药记录**：每个日期分组标题下方加一条细线。

## 4. 编号（D）

- 阶段回顾：`01 效果`、`02 耐受`、`03 风险因素`、`04 满意度`。
- 复诊详情：按显示顺序编号，见第 3 节。
- 编号样式：
  - `labelLarge` 加 `tnum`（等宽数字），比标题（`titleMedium`）小一档；
  - 主色，SemiBold；
  - 与标题在同一行，左对齐。
- TalkBack：编号节点使用 `clearAndSetSemantics`，只保留一个测试用的 `SectionNumberKey`，没有 Text 或 ContentDescription，所以不会朗读。标题仍是 heading。
- 编号只在显示时生成，不写入数据，也不进入导出。

## 5. 测试

先写失败测试，再实现（`Ui09Test.kt`）：

| 测试 | 断言 |
|---|---|
| `Ui09SourceGuardTest.onlyTheListedExceptionsStayCentred` | 居中用法只剩第 1 节的例外清单；`EmptyState` 中没有居中 |
| `…spacingOnTheTouchedPagesIsOnTheFourDpGrid` | 12 个文件中所有间距调用的 dp 值都是 4 的倍数；`Grid` 令牌的数值 |
| `…visitDetailUsesNumberedSections` | 复诊详情的区块是 `NumberedSection`，不再是 `SectionCard` |
| `…exportCodeIsTheBase` | 导出代码不引用编号组件；`WellbeingSummary.kt` 与基线逐字节相同（见第 6 节） |
| `Ui09ScreenTest.emptyStateIsStartAligned` | 标题和正文的左边缘都在 16dp，不居中 |
| `…historyDayHasARuleAndSixteenDpMargins` | 服药记录的日期细线存在；内容左边距 16dp |
| `…numberedSectionDrawsTheNumberAndUsesOutlineInHighContrast` | 高对比度下细线颜色为 `outline`；编号在标题左侧；屏幕上没有可读的“02”文字 |
| `…exportsCarryNoNumbering` | `reviewLines` 与 CSV 导出（zip 内全部文件）中没有“01 ”“02 ”等编号 |
| `Ui09StageReviewTest.stageReviewPartsAreNumberedRuledAndNotReadAloud` | 四部分编号 1–4（`SectionNumberKey`），编号节点没有 Text 或 ContentDescription；细线颜色为 `outlineVariant` |

PDF 中不出现阶段回顾或复诊详情的编号：PDF 的文字由 PDF-01 的逐字对照测试固定（报告 76），PDF 里的 01、02… 是 PDF-01 自己的章节编号，与应用内编号无关。

## 6. 断言迁移

没有修改或删除任何既有断言。UI-01 至 UI-08、LAB-01 至 LAB-04 的测试都没有改动，并且全部通过（第 8 节）。

`Ui09SourceGuardTest.exportCodeIsTheBase` 最初逐字节固定 Exports.kt，后来改为检查导出内容。原因是并行的 PDF-01（§145）经单独批准修改 Exports.kt 的 PDF 版式。UI-09 本身没有动导出代码，“导出不含编号”由 `exportsCarryNoNumbering` 直接检查导出结果。

## 7. 截图

`docs/ui-audit/2026-10-23-ui09/`，合成数据，由 `Ui09ScreenshotTest`（设置 `UI09_SHOTS` 后运行）生成：

- **页面**：首页（顶部、滚动后）、库存、治疗时间线、医疗档案首页、复诊详情（顶部、滚动后）、身心状态、服药记录（顶部、滚动后），共 10 个画面。
  - 每个画面覆盖 4 种语言（zh-rCN、en、fr、zh-rTW）× 320/411dp × 字号 1/2 × 明暗，即 32 种组合。
  - 前后各 320 张。
- **阶段回顾对话框**：只有 320dp 的 16 种组合，前后各 16 张。411dp 见第 9 节。
- `before/` 用基线 d2223c0a 渲染：同一个测试文件临时复制到基线工作树，渲染后删除。`after/` 用本分支渲染。
  - 两组都缩到一半大小，并量化为 64 色。
- `compare/` 共 21 张，左为改前、右为改后。每个画面各取两种组合：en / 411dp / 字号 1 / 明，以及 zh-rCN / 320dp / 字号 2 / 暗。

位置变化：

- 每个间距数值的变化都不超过 4dp（第 2 节）。在长列表中，多处小改动会累加，后面的条目整体下移几 dp，但条目之间的相对位置变化仍不超过 4dp。
- 以下两处超过 4dp，原因都是任务书 C、D 本身要求的结构变化，不是间距取整：
  - **复诊详情**：编号区块去掉了卡片底，卡片的 16dp 内边距随之去掉，所以区块内容向左外移 16dp，对齐页边距。区块之间改为“12dp + 细线 + 12dp”。
  - **服药记录**：每个日期标题下方增加了细线（1dp 加上 4dp 下边距），所以后面的条目每组下移约 8dp。

## 8. 验证

- `Ui09Test` 9 项全部通过；`Ui09ScreenshotTest` 两次渲染（改前、改后）均为 0 失败。
- 完整 `:app:testFullDebugUnitTest`、`:core:ui:testDebugUnitTest`、`:app:lintFullDebug`：见 PR 描述。
- CI：见 PR。

## 9. NOT_RUN

- **阶段回顾对话框在 411dp 下的截图**：在 Robolectric 中，对话框窗口在 411dp 时会不停地重新布局，始终无法进入空闲状态。UI-08 的对话框截图也遇到同样问题。320dp 的 16 种组合已经覆盖了编号和细线的效果。
- **真机 TalkBack 朗读核对**：单元测试已断言编号节点没有可读文字。
