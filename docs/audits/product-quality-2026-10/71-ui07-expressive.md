# 71 — UI-07：Material 3 Expressive 表现层，只用稳定 API（2026-10-21）

- **任务书**：`sources/ui07/ui07-prompt.md`（SHA-256 `49cafbd9…eb9f1`），REQUIREMENTS §116。
- **前置**：UI-06 #91 合并为 4ec11437；该提交在默认分支上的 CI 六项必需检查全部 PASS（另有 research-deep 按规则 skipped）。
- **分支**：`feature/ui07-expressive-20261021`，基于 4ec11437。
- **性质**：只改表现层。
  - 撤销按钮和确认卡调用的都是已有的回调；没有新写入路径。
  - 状态判定规则和 UI-06 色值未变。
- **约束遵守情况**：
  - Compose 仍为 BOM 2025.10.00 / Material 3 1.4.0；
  - 没有使用 `@ExperimentalMaterial3ExpressiveApi` 或 `@ExperimentalMaterial3Api` 的新组件；FlowRow 用到的 `ExperimentalLayoutApi` 属于 foundation，代码库中已有使用；
  - 不新增依赖、权限、网络或埋点；
  - 不改 versionCode 和签名，不交付 APK。

## 1. 改动文件与保护矩阵类别

| 文件 | 类别 | 改动 |
|---|---|---|
| `core/ui/StatusIcon.kt`（新） | 不在矩阵 | A：实心圆状态图标 |
| `core/ui/Expressive.kt`（新） | 不在矩阵 | B：按下形变；C：强调字体；D：减少动画和弹簧参数；E：分组列表 |
| `core/ui/Tokens.kt` | 不在矩阵 | `Corners.xs…full`，`Radii` |
| `core/ui/SemanticColors.kt`、`NotesTheme.kt` | 不在矩阵 / HISTORY_ONLY | 令牌记录对比度档位和 onSurface（供高对比度图标使用）；`NotesTheme.typeExtra`；在主题中提供 `LocalReduceMotion` |
| `ui/TodayCard.kt` | 不在矩阵 | 今天卡、今天的记录和预约改为分组列表、撤销、动效、首页库存卡 |
| `ui/CalendarScreen.kt` | HISTORY_ONLY | 日格、周条、图例使用 StatusIcon；最近 7 天；确认卡回答后的状态 |
| `ui/UnconfirmedCard.kt` | 不在矩阵 | 28dp 圆角、按下形变、回答后收起 |
| `ui/StatusUi.kt` | 不在矩阵 | `StatusChip` 使用 StatusIcon |
| `ui/HistoryScreen.kt`、`MedicalRecordsScreen.kt` | HISTORY_ONLY / 不在矩阵 | 改为分组列表 |
| `ui/StockScreen.kt` | HISTORY_ONLY | 余量用 statNumber；包装展开后显示分组列表 |
| `ui/LongitudinalScreen.kt` | HISTORY_ONLY | 漏服事件用 StatusIcon；“第 n 天”用 statNumber |
| `ui/AppShell.kt` | HISTORY_ONLY | 撤销接到已有的 `model.deleteRecord` |
| `res/values*/strings.xml` | HISTORY_ONLY | 5 个文案键 × 四语 |

未触及的文件：
- 浓度页、ConcChart、化验页（LabsScreen、LabContextSection）、Exports，以及所有 LIVE_LOCK_RESEARCH_INPUT 文件；
- 设置页和提醒代码；
- ViewModel、仓库、Room、备份。

没有需要暂停的子项。

## 2. 新组件 API

```kotlin
// A
enum class StatusIconKind { TAKEN, PARTIAL, MISSED, SKIPPED, UNCONFIRMED, TODAY }
object StatusIconSizes { XL = 40.dp; L = 32.dp; M = 22.dp; S = 18.dp }
@Composable fun StatusIcon(kind: StatusIconKind, size: Dp = StatusIconSizes.M, modifier: Modifier = Modifier, ring: Color = Color.White)
fun statusIconKind(state: DoseState): StatusIconKind?          // OVERDUE / PENDING / SOON 没有图标
fun statusIconSpec(kind, sem, ring): StatusIconSpec            // 纯函数：填充、半填充、描边、虚线、图形及其颜色
// B
object Radii { xs 8, s 12, m 16, l 20, xl 28, xxl 32, full };  Corners.xs … Corners.full
@Composable fun rememberPressMorph(interaction, rest: Dp, pressed: Dp = 16.dp): PressMorph   // radius + scale
fun Modifier.pressMorph(m: PressMorph): Modifier                                              // 缩放 + 测试用语义
// C
NotesTheme.typeExtra: TypeExtra(heroNumber, statNumber, sectionTitle, cardTitle)
// D
val LocalReduceMotion; object ReduceMotion { fromSystem(context); override }; fun <T> motionSpec(reduce): FiniteAnimationSpec<T>
// E
@Composable fun <T> GroupedList(items, modifier, color, onClick, itemModifier, content);  @Composable fun GroupedItem(index, count, color, onClick, modifier, content)
```

`StatusIcon` 的语义：
- 不设 contentDescription，也没有文字；状态由所在行或日格朗读。
- 只有一个测试用的 `StatusIconKindKey`，TalkBack 不读它。

## 3. 规格表

### 3.1 状态图标（只取 UI-06 令牌）

| 状态 | 容器 | 图形 | 描边 |
|---|---|---|---|
| 已服（含晚服） | `success.accent` 实心圆（浅色即 #006A63） | 勾：浅色主题白色；深色主题 accent 较亮，改为深色勾以保证 ≥3:1 | 无 |
| 部分完成 | `missed.container`，右半边 `success.accent` | 无 | `missed.accent` 细线（见下） |
| 漏服 | `missed.container` | `missed.onContainer` 色叉 | `missed.accent`，40dp 时 1.5dp |
| 跳过 | `skipped.container` | `skipped.onContainer` 色短横 | `skipped.accent` 细线（见下） |
| 未确认 | 无填充 | `unconfirmed.onContainer` 色问号 | `unconfirmed.accent` 虚线，3:3 |
| 今天（最近 7 天） | 无填充 | 中心圆点 | onPrimary 实线环 |

- **线宽**：图形为直径的 6.5%（40dp 时 2.6dp），最小 1.25dp；描边为 1.5dp × 直径 / 40，最小 1dp；细线为其三分之二，最小 0.75dp。全部 round cap / round join。
- **高对比度**：描边与图形一律用 onSurface。
- **与任务书的差异**：部分完成和跳过的浅色容器与页面背景只有约 1.1–1.2:1。为满足“容器或描边与背景 ≥ 3:1”，给它们加了令牌 accent 色的细描边。灰度下各状态仍可区分，见 §5。

### 3.2 图标实测对比度

数据来自 `StatusIconContrastTest`：228 例全部 PASS（门槛 3:1），原始数据在 `evidence/ui07/icon-contrast-measured.tsv`。导出数据时漏了一行，下表据其余 227 行生成。

- **glyph**：图形对其所在底色。
- **edge**：图标边缘（填充或描边）对页面背景。

| 图标对 | 浅·标准 | 浅·中 | 浅·高 | 深·标准 | 深·中 | 深·高 | 动态色最低 |
|---|---|---|---|---|---|---|---|
| `TAKEN.edge/surface` | 6.17 | 9.56 | 17.12 | 10.86 | 12.22 | 16.27 | 6.12 |
| `PARTIAL.edge/surface` | 6.17 | 9.56 | 17.12 | 10.86 | 12.22 | 16.27 | 6.12 |
| `MISSED.glyph/fill` | 8.12 | 14.83 | 13.67 | 9.46 | 10.87 | 9.67 | 8.12 |
| `MISSED.edge/surface` | 4.92 | 6.49 | 17.12 | 6.08 | 8.66 | 16.27 | 4.91 |
| `SKIPPED.glyph/fill` | 7.22 | 14.04 | 13.28 | 7.21 | 8.19 | 7.21 | 7.21 |
| `SKIPPED.edge/surface` | 4.28 | 5.74 | 17.12 | 5.84 | 8.60 | 16.27 | 4.27 |
| `UNCONFIRMED.glyph/surface` | 8.87 | 17.25 | 17.12 | 10.88 | 14.35 | 16.27 | 8.85 |
| `UNCONFIRMED.edge/surface` | 4.28 | 5.74 | 17.12 | 5.84 | 7.56 | 16.27 | 4.27 |
| `TAKEN.glyph/fill` | 6.48 | 10.04 | 17.12 | 10.86 | 12.22 | 14.32 | 6.42 |
| `TAKEN.edge/surfaceContainerLowest` | 6.48 | 10.04 | 17.12 | 11.35 | 12.77 | 16.27 | 6.42 |
| `PARTIAL.edge/surfaceContainerLowest` | 6.48 | 10.04 | 17.12 | 11.35 | 12.77 | 16.27 | 6.42 |
| `MISSED.edge/surfaceContainerLowest` | 5.16 | 6.81 | 17.12 | 6.36 | 9.05 | 16.27 | 5.16 |
| `SKIPPED.edge/surfaceContainerLowest` | 4.49 | 6.02 | 17.12 | 6.10 | 8.99 | 16.27 | 4.49 |
| `UNCONFIRMED.glyph/surfaceContainerLowest` | 9.31 | 18.11 | 17.12 | 11.37 | 15.00 | 16.27 | 9.31 |
| `UNCONFIRMED.edge/surfaceContainerLowest` | 4.49 | 6.02 | 17.12 | 6.10 | 7.90 | 16.27 | 4.49 |
| `PARTIAL.half/fill` | 5.17 | 8.01 | 13.67 | 7.34 | 8.26 | 9.67 | 5.13 |

### 3.3 形状

- **档位**：xs 8、s 12、m 16、l 20、xl 28、xxl 32、full。
- **卡片**：今天卡 32dp；确认卡、首页库存条、最近 7 天卡 28dp。
- **⋮ 按钮**：56dp 描边方块，18dp 圆角，与 56dp 主按钮同高。

### 3.4 强调字体

| 名称 | 规格（字号 1） | 用在 |
|---|---|---|
| heroNumber | 64sp / 行高 68 / 字重 800 / 字距 −2sp / 等宽数字 | 今天卡“下一次”的时间 |
| statNumber | 28sp / 行高 34 / 字重 800 / 等宽数字 | 库存余量、时间线“第 n 天” |
| sectionTitle | 16sp / 字重 700 | 今天卡分区、今天的记录、最近 7 天、服药记录的日期 |
| cardTitle | 20sp / 字重 700 | 今天卡药名、确认卡标题 |

正文仍为字重 400，没有引入新字体。

**为什么给大号数字限幅**：heroNumber 和 statNumber 随系统字号放大到 1.3 倍后停止，其余文字照常放大。做法是用 `LocalDensity.fontScale` 计算 `cappedSp`。理由：
- 64sp × 1.3 ≈ 83sp，已远超 WCAG 的大字标准（18pt / 14pt 粗体）；
- 继续放大只会把主按钮挤出首屏；
- 时间与“已过时间”标签改为可换行的 FlowRow，2× 字号、320dp 下不截断（`Ui07TodayTest.twiceFontAt320dpNothingCut`）。

“下一次服药（已记录状态下）”用 statNumber 这一项未单独实现：已记录时今天卡显示“已记录 · 撤销”横条，下面的下一次时间仍用 heroNumber。

## 4. 动效

| 场景 | 实现 | 参数 |
|---|---|---|
| 按下形变（今天卡“现在服用”、确认卡两个按钮、首页“添加库存”） | `animateDpAsState` + `animateFloatAsState`，交互源 `collectIsPressedAsState` | 圆角 → 16dp，缩放 → 0.96；spring(dampingRatio 0.6, Spring.StiffnessMediumLow) |
| 记录完成 | 新记录行从上方 14dp 滑入；StatusIcon 从 0.3 倍放大到 1 倍并轻微回弹 | 滑入 spring(0.6, MediumLow)；放大 spring(0.45, StiffnessLow)，约 0.5 秒 |
| 今天卡切换到下一次 | `AnimatedContent`：淡出，新内容淡入并上移 1/8 | spring(0.6, MediumLow) |
| 确认卡回答后 | 该行收起；结果行用 `expandVertically` 展开；卡片高度 `animateContentSize` | spring(0.6, MediumLow) |
| 展开 / 折叠 | “显示全部”用 `animateContentSize`；库存“包装记录”用 `AnimatedVisibility` 加 `expandVertically` / `shrinkVertically` | spring(0.6, MediumLow) |

**减少动画**：
- `LocalReduceMotion` 由 `NotesTheme` 读取 `Settings.Global.ANIMATOR_DURATION_SCALE == 0`；测试可通过 CompositionLocal 或 `ReduceMotion.override` 开关。
- 开启时上述动效全部用 `snap()`，或 `EnterTransition.None` / `ExitTransition.None`。

没有装饰性动画或循环动画。

## 5. 两个条件项的决定

### 5.1 撤销：已实现

核实结果：服药记录页的“删除记录”（`NotesRepository.deleteRecord`）
- 用 `SupplyLedger.reverse` 回退库存消耗；
- 软删除记录，并按删除路径照常放入回收站；
- 之后 `ReminderCoordinator.mutate` 按日历重建提醒：该时段重新变为未服，提醒恢复；已发出的提醒仍记为已发出，与服用前相同；
- 今天进度只统计未删除的记录。

三项都能完整回退，因此今天卡在新记录出现后显示 4 秒“已记录 · 撤销”。撤销调用 AppShell 中同一个 `model.deleteRecord`，没有新写入路径。

`Ui07UndoShellTest` 在真实 AppShell 和内存数据库上验证，撤销与在服药记录页“删除 → 确认”相比：
- 库存容器与服用前逐字段相同；
- 今天进度文字相同；
- 两条被删记录除 id、删除时间、服用时间外逐字段相同；
- 回收站各增加 1 条，没有其他写入。

### 5.2 最近 7 天：已实现

- 首页默认显示“最近 7 天”胶囊条（以今天结尾），右上角有“完整日历”。
- “完整日历”打开原有的日、周、月、年日历，该日历的右上角有“最近 7 天”可以返回。选择用 `rememberSaveable` 保存，进程重建后保留。
- 日数据复用 `dayInfos`，不改计算。
- 朗读与月视图相同（`dayDescription`），图标为 18dp 的 StatusIcon；今天的胶囊用主色填充。
- 年视图的日格只有约 6–10dp，放不下 18dp 图标，仍沿用颜色方块；月视图和周视图使用 StatusIcon。

## 6. 四语文案

| 键 | en | zh-CN | zh-TW | fr |
|---|---|---|---|---|
| `today_logged` | Logged | 已记录 | 已記錄 | Noté |
| `today_undo` | Undo | 撤销 | 復原 | Annuler |
| `confirm_result_not_taken` | Noted: not taken | 已记下：没有服 | 已記下：沒有服 | Noté : non pris |
| `recent_7_days` | Last 7 days | 最近 7 天 | 最近 7 天 | 7 derniers jours |
| `full_calendar` | Full calendar | 完整日历 | 完整日曆 | Calendrier complet |

## 7. 测试

| 测试 | 内容 | 结果 |
|---|---|---|
| core/ui `StatusIconSpecTest`（4） | 每种状态的容器、描边、图形正确；高对比度用 onSurface；状态与图标的映射；线宽 | PASS |
| `StatusIconContrastTest`（228） | 3 档对比度 × 浅 / 深，加 3 组动态色种子；图形 ≥ 3:1，边缘 ≥ 3:1，部分完成两半之间 ≥ 3:1 | 228/228 PASS |
| `ExpressiveTokensTest`（3） | 形状档位；数字在字号 1 / 1.3 / 2 / 0.85 下按限幅规则计算的尺寸；分组列表圆角 | PASS |
| `Ui07StatusIconTest`（2） | 6 种状态 × 4 档尺寸可渲染且尺寸正确，没有 contentDescription 或文字；5 种状态 × 浅 / 深 × 三档对比度去色后两两可区分（≥40 像素灰度差 > 32） | PASS |
| `Ui07PressMorphTest`（2） | 按下时有中间帧，最终到达 16dp / 0.96，松开后恢复 28dp / 1；减少动画时没有中间帧 | PASS |
| `Ui07GroupedListTest` | 1 / 2 / 4 项的圆角规则、≥ 56dp、3dp 缝隙、点击回调 | PASS |
| `Ui07TodayTest`（5 × 减少动画开 / 关） | heroNumber 实际字号符合限幅；2× 字号 320dp 下不截断；记录完成、今天卡切换、撤销 4 秒、显示全部、确认卡收起——开启减少动画时立即完成，关闭时最终状态相同 | 10/10 PASS |
| `Ui07RecentDaysTest`（四语） | 7 天范围、今天在最后且有今天图标；状态图标；朗读与月视图相同、日期数字不重复朗读；选择日期；完整日历与返回；进程重建后保留选择 | 4/4 PASS |
| `Ui07UndoShellTest` | 见 §5.1 | PASS |

### 7.1 全量回归（本地）

- core:domain 135、core:ui 422、core:data 263、core:reminder 20：全部 PASS。
- app：1935 项，0 失败；跳过的 361 项是截图类测试，需要环境变量才运行。
- `PeriodStabilityTest` 未改动，已运行。
- lint：首轮有 1 个错误（`AnimatedContent` 内容没有使用目标状态），修复后 0 错误；修复后今天卡相关测试 99/99 PASS。
- androidTest Kotlin 编译：PASS。

### 7.2 测试发现并修正的问题

- `ButtonLayoutRegressionTest`：2× 字号、320dp 下，最近 7 天胶囊里的星期被截断（16 例），法语标题被“Calendrier complet”挤窄（2 例）。修复：胶囊文字按宽度缩小（与月视图日期徽章相同的做法），标题行改为可换行的 FlowRow。没有放宽断言。
- lint：见 §7.1。
- 截图复核（fr、320dp、2× 字号、深色）发现两处挤压。“之前”组同一配置也有，并非 UI-07 引入；没有截断，所以按钮布局测试未报。这两处在本轮改动的首页卡片上，顺手修复：
  - 今天卡日期行被进度胶囊挤成每行几个字母：改为可换行的 FlowRow，胶囊放不下时换到下一行；
  - 首页库存条药名被“添加库存”挤成每行一个字：字号大于 1.3 倍时按钮移到文字下方。
  修复后相关测试 288/288 PASS。

### 7.3 断言迁移（未删除任何断言）

| 测试 | 原定位 | 新定位 | 保留的含义 |
|---|---|---|---|
| `TodayCardTest`、`Ui04TodayDedupTest`、`Ui05TodayCardTest` 的 `inCard` | 祖先为 `today-card` | 祖先为 `today-section` | 今天的记录和预约移出今天卡、改为紧随其下的分组列表；`today-section` 包含今天卡和这两个列表。“今天的内容只出现一次，并且在今天的区域里”不变 |
| `Ui04TodayDedupTest` 朗读顺序 | 从 `today-card` 遍历 | 从 `today-section` 遍历 | 顺序列表不变：日期 → 进度 → 下一次 → 主按钮 → 稍后 → 今天的记录 → 预约 |
| `Ui04TodayDedupTest.goingBackToToday…` | 首页默认月视图 | `full = true` | “今天”按钮在完整日历的期间栏里，先显式打开完整日历 |
| `Ui05TodayCardTest` 主按钮和菜单 | 52dp | 56dp（测试改名为 `…FiftySixDp…`） | 任务书 B 的新规格；“同一行、同高”不变 |
| `Ui05HomeStockTest` | 库存条在 `calendar-card` 之前 | 在 `recent-card` 之前 | 首页的日历现在是“最近 7 天”卡 |
| `CalendarSemanticsTest`、`CalendarSelectionRestoreTest`、`CalendarDayMarkTest`、`Ui06AccessibilityTest` | 默认月视图 | `initialFull = true` | 按任务书，先显式切换到完整日历再断言 |
| `CalendarSemanticsTest` 选择明天后 | 直接断言“明天” | 先滚动到“明天” | 今天卡更高（64sp），日列表可能在首屏以下 |
| `CalendarCoreFlowTest` | 等月历日格出现 | 先点“完整日历” | 用户的实际路径；7/35/42 格、宽度内、主操作可达等断言不变 |
| `Ui06AccessibilityTest` 卡片朗读顺序 | 行内标签顺序 | 过滤掉装饰性的 `unconfirmed-icon` | 图标没有文字、不被朗读（`Ui07StatusIconTest` 已断言）；标题 → 药名 → 我服过了 → 没有服 的顺序不变 |

## 8. 截图与动效

位置：`docs/ui-audit/2026-10-21-ui07/`。测试 `Ui07ScreenshotTest`，设置 `UI07_SHOTS` 时运行；同一文件在 4ec11437 上渲染“之前”一组。
- **页面**：首页（未服 / 刚记录 / 有确认卡）、服药记录、库存（展开包装）、医疗档案。
- **范围**：4 语 × 320 / 411dp × 字号 1 / 2 × 浅 / 深，加高对比度和 hi-res（411dp、字号 1、zh-CN、浅 / 深，xxxhdpi 原图），前后对比。
- **动效**：本环境没有模拟器（没有 `emulator`、system image 和 `/dev/kvm`）。用 Robolectric 暂停时钟每 33ms 截一帧，合成 `motion/take-now.gif`：按下 → 松开 → 记录 → 下一次切换 → 新行与图标弹入 → 已记录 · 撤销。这不是模拟器录屏；真机手感列入真机清单。

## 9. NOT_RUN

- 真机和 TalkBack：真机清单第 13 节全部 NOT_RUN。
- 模拟器录屏：本环境无模拟器，用 Robolectric 逐帧 GIF 代替。
- 原生 androidTest 执行：本地只编译，执行交给 CI。
- 不出 APK，不签名，不改 versionCode。
