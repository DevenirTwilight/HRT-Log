# 70 — UI-06：语义色与状态体系（2026-10-21）

- 任务书：`sources/ui06/ui06-prompt.md`；REQUIREMENTS §115。
- 分支：`feature/ui06-semantic-status-20261021`，基于默认分支 320171cd。
- 性质：只改展示层的颜色、形状、状态符号，以及首页一张确认卡。确认卡复用已有的补记对话框和 `confirmMissed`，没有新增写入路径。
- 未改动：
  - ViewModel、Repository、Room、备份、加密、应用锁、伪装、PK、提醒逻辑、任何状态判定规则；
  - 受保护的 PK_DISPLAY_REVIEW 文件：ConcentrationScreen、ConcChart、LabsScreen、LabContextSection、Exports；
  - Compose 版本，依赖、权限、网络；
  - versionCode 和签名；本轮不出 APK。

## 1. 改动文件与保护矩阵类别

| 文件 | 类别 | 改动 |
|---|---|---|
| `core/ui/SemanticColors.kt`（新） | 不在矩阵 | 7 个语义令牌、三档对比度推导、动态色规则；唯一状态表 `StatusStyles` |
| `core/ui/NotesTheme.kt` | HISTORY_ONLY | 在主题中提供令牌；新增 `notesColorScheme()` 供测试使用 |
| `core/ui/Tokens.kt` | 不在矩阵 | `Corners.badge` 改名为 `Corners.tag`，新增 `Corners.pill`（50%） |
| `ui/StatusUi.kt`（新） | 不在矩阵 | `StatusChip`、`statusOutline`（实线 / 虚线）、`dayStyle`、文字与图标映射 |
| `ui/UnconfirmedCard.kt`（新） | 不在矩阵 | 首页“还没有记录”卡 |
| `ui/CalendarScreen.kt` | HISTORY_ONLY | 日格、周条、年视图、图例、当天剂量卡、记录行；插入确认卡 |
| `ui/TodayCard.kt` | 不在矩阵 | 已过时间胶囊、记录图标颜色 |
| `ui/HistoryScreen.kt` | HISTORY_ONLY | 依从性条、计数、状态胶囊 |
| `ui/LongitudinalScreen.kt` | HISTORY_ONLY | 漏服事件类型配色 |
| `ui/StockScreen.kt` | HISTORY_ONLY | 三处提醒改为 attention，并加测试标签 |
| `ui/AppShell.kt` | HISTORY_ONLY | 确认卡回调接到已有的 `historyDialogs.editRecord`、`model.confirmMissed` 和服药记录筛选 |
| `ui/Components.kt` | HISTORY_ONLY | `StatusPill` 使用 `Corners.pill` |
| `ui/SettingsScreen.kt`、`VisitsScreen.kt`、`SourceIdentity.kt`、`LabBatchEntry.kt` | HISTORY_ONLY / 不在矩阵 | 共 4 处“需要注意”的提示从 error 改为 attention |
| `res/values*/strings.xml` | HISTORY_ONLY | 11 个文案键 × 四语（见 §6） |

没有触及 PK_DISPLAY_REVIEW 或更严格类别的文件。没有需要暂停的子项。

## 2. 令牌与实测对比度

### 2.1 令牌取值

固定值来自任务书：

| 令牌 | 浅色 container / onContainer / accent | 深色 container / onContainer / accent |
|---|---|---|
| attention | #FFDBCE / #5C2410 / #8E4D35 | #71361F / #FFDBCE / #FFB599 |
| success | #B8EDE6 / #00201D / #006A63 | #00504A / #9EF2E8 / #82D5CC |
| missed | #EDE4DF / #4A3F3A / #7A6A63 | #3B322E / #E9DED9 / #A39089 |
| unconfirmed | 透明 / #3F4947 / #6F7977（虚线） | 透明 / #BEC9C6 / #889391 |
| skipped | #DAE5E2 / #3F4947 / #6F7977 | #3F4947 / #DDE4E2 / #889391 |
| action | primary / onPrimary / primary | 同左 |
| danger | errorContainer / onErrorContainer / error | 同左 |

对比度推导：
- **中对比度**：onContainer 向黑（浅色）或白（深色）插值一半；accent 向 onContainer 插值 40%。
- **高对比度**：onContainer 取纯黑或纯白；accent 取 scheme.onSurface，与 `withContrast` 的做法一致。

动态色规则：
- 只有 action、success（primaryContainer / onPrimaryContainer / primary）和 danger 跟随动态色；
- attention、missed、unconfirmed、skipped 保持固定值，含义不随壁纸改变；
- 这一规则由 `StatusStylesTest.dynamicColourMovesOnlyActionSuccessAndDanger` 断言。

### 2.2 实测对比度（WCAG）

数据来自 `SemanticContrastTest`：180 例全部 PASS，每例的测试名都含令牌名，例如 `missed.accent/container dark/HIGH`。原始数据见 `evidence/ui06/contrast-measured.tsv`。

- 最低要求：文字 4.5，描边和符号 3.0。
- “动态色最低”一列取三组合成壁纸种子（蓝、紫、橙的 Material 色调值）在浅色和深色下的最低值。
- `action.text` 在 light/STANDARD 的数值不在导出日志中，因为解析 XML 时漏了一行；该测试本身已运行并通过。它与 light/MEDIUM 用的是同一对颜色（`withContrast` 不改 primary），所以数值同为 6.48。

| 令牌对 | 最低 | 浅·标准 | 浅·中 | 浅·高 | 深·标准 | 深·中 | 深·高 | 动态色最低 |
|---|---|---|---|---|---|---|---|---|
| `attention.text` | 4.5 | 9.49 | 14.94 | 16.28 | 7.24 | 8.23 | 9.34 | 7.24 |
| `attention.accent/container` | 3.0 | 4.99 | 6.51 | 13.27 | 5.48 | 6.12 | 7.23 | 4.99 |
| `attention.accent/surface` | 3.0 | 6.13 | 8.00 | 17.12 | 10.85 | 12.11 | 16.27 | 6.12 |
| `success.text` | 4.5 | 13.31 | 15.85 | 16.29 | 7.27 | 8.27 | 9.35 | 7.22 |
| `success.accent/surface` | 3.0 | 6.17 | 9.56 | 17.12 | 10.86 | 12.22 | 16.27 | 6.12 |
| `missed.text` | 4.5 | 8.12 | 14.83 | 16.76 | 9.46 | 10.87 | 12.49 | 8.12 |
| `missed.accent/container` | 3.0 | 4.12 | 5.44 | 13.67 | 4.11 | 5.85 | 9.67 | 4.11 |
| `missed.accent/surface` | 3.0 | 4.92 | 6.49 | 17.12 | 6.08 | 8.66 | 16.27 | 4.91 |
| `missed.accent/surfaceContainerHigh` | 3.0 | 4.23 | 5.58 | 14.02 | 4.74 | 6.75 | 11.16 | 4.21 |
| `unconfirmed.text/surface` | 4.5 | 8.87 | 17.25 | 21.00 | 10.88 | 14.35 | 21.00 | 8.85 |
| `unconfirmed.accent/surface` | 3.0 | 4.28 | 5.74 | 17.12 | 5.84 | 7.56 | 16.27 | 4.27 |
| `unconfirmed.accent/surfaceContainerLowest` | 3.0 | 4.49 | 6.02 | 17.12 | 6.10 | 7.90 | 16.27 | 4.49 |
| `skipped.text` | 4.5 | 7.22 | 14.04 | 16.29 | 7.21 | 8.19 | 9.31 | 7.21 |
| `danger.text` | 4.5 | 13.26 | 13.26 | 13.26 | 7.24 | 7.24 | 7.24 | 7.17 |
| `action.text` | 4.5 | 6.48 | 6.48 | 6.48 | 7.74 | 7.74 | 7.74 | 6.42 |

skipped 不画描边，只用底色和“—”符号，所以没有测 skipped.accent。它的 accent 与 container 只有 3.48（浅色）和 2.94（深色）。

## 3. 状态映射（唯一的表：`core/ui StatusStyles`）

| 状态 | 令牌 | 符号 | 文字 | 描边 |
|---|---|---|---|---|
| ON_TIME | success | ✓ | 已按时服用 | — |
| LATE | success | ✓ | 已服（迟服）：颜色同 ON_TIME，只多“迟服”二字 | — |
| PARTIAL | missed | ½ | 部分完成 | — |
| MISSED | missed | ✕ | 已漏服 | accent 实线 |
| UNCONFIRMED | unconfirmed | ? | 未确认 | accent 虚线 |
| SKIPPED | skipped | — | 已跳过 | — |
| OVERDUE | attention | 文字 | 已过时间 · 可以补记 | — |
| PENDING | 无 | 无 | 待服 | — |
| SOON | 无（今天卡保留 primary） | 无 | 即将到时 | — |

`SlotState`、`DoseStatus` 和已存记录（`ofRecord`：AUTO_MISSED 记作 UNCONFIRMED）都只经过这张表。日历的 `DayKind` 也一样：
- TAKEN → ON_TIME；PARTIAL → PARTIAL；MISSED → MISSED；UNCONFIRMED → UNCONFIRMED；
- PLANNED、NONE 不上色。

应用这张表的界面：月历日格、周条、年视图、图例、当天剂量卡、今天卡、服药记录（依从性条、计数、胶囊）、治疗时间线（漏服事件类型）、库存提示。

“提醒未送达”从未出现在日历上，由源码守卫 `calendarNeverShowsReminderNotDelivered` 断言。

## 4. error → attention / missed 的改动清单

| 位置 | 改前 | 改后 |
|---|---|---|
| 月历日格：漏服底色 | errorContainer | missed.container + accent 实线描边 |
| 月历日格：漏服描边和 ✕ | error | missed.accent |
| 月历日格 / 周条：“库存用完”图标、库存不足圆点 | error | attention.accent |
| 周条：漏服时间文字 / 库存不足时间文字 | error | missed.onContainer / attention.onContainer |
| 年视图：漏服 / 部分完成 | error / tertiary | missed.accent / missed.container |
| 图例：漏服样例与 ✕ | errorContainer / error | missed（描边）；新增未确认的虚线 ? 样例 |
| 当天剂量卡 OVERDUE：描边、底色、时间文字 | error / errorContainer 35% / error | attention.accent / attention.container 35% / attention.accent |
| 当天记录行：漏服胶囊 | errorContainer | `StatusChip`（missed + 描边） |
| 今天卡：“已过时间”胶囊 | errorContainer | attention，文案改为“已过时间 · 可以补记” |
| 服药记录：依从性条和计数中的漏服 | error | missed.accent；迟服 tertiary → success.container |
| 服药记录：漏服胶囊 | errorContainer | `StatusChip` |
| 治疗时间线：漏服事件类型 | errorContainer | missed.container / onContainer |
| 库存：预测不足、快过期、“没有开封的容器” | error | attention.accent |
| 设置：检查未通过的图标和文字 | error | attention.accent |
| 复诊：“没有上一次就诊”的说明 | error | attention.accent |
| 关于：源码有未提交改动 | error | attention.accent |
| 化验批量录入：超出参考范围标记 | error | attention.accent |

## 5. 保留红色的地方

以下是真正的错误、校验失败或不可撤销的操作，保持 danger（colorScheme.error）：
- 数据页：清空 / 删除的图标和按钮，导入覆盖警告，导入缺少选择；
- 应用锁密码错误（LockScreen、PrivacySection）；
- 保存失败：时间线 `operation_error`（源码守卫唯一允许的一行）、医疗档案、参考资料、化验批量保存；
- 校验错误：药物编辑、医疗档案时间、批量范围、未来时间警告、HT 导入重复选择、就诊无分区、历史上下文冲突、化验重复；
- 化验详情：删除按钮。

受保护的页面一律未改，红色原样保留：
- ConcentrationScreen：校准离群、警告、取值标签；
- ConcChart：“现在”线；
- experimental/ModelComparisonChart。

需要 owner 确认的一处：**症状页紧急来源引用**（SymptomsScreen 第 28、54 行）。它是安全警示（建议立即就医的原文引用），本轮保持红色、没有改为 attention。如需调整请告知。

## 6. 四语文案

| 键 | en | zh-CN | zh-TW | fr |
|---|---|---|---|---|
| `legend_partial`（改） | Partly done | 部分完成 | 部分完成 | Partiellement fait |
| `status_past_time` | Past time · you can still log it | 已过时间 · 可以补记 | 已過時間 · 可以補記 | Heure passée · vous pouvez encore la noter |
| `confirm_title_yesterday` | No entry yet for %1$s yesterday | 昨天 %1$s 还没有记录 | 昨天 %1$s 還沒有記錄 | Rien de noté pour hier à %1$s |
| `confirm_title_today` | No entry yet for %1$s today | 今天 %1$s 还没有记录 | 今天 %1$s 還沒有記錄 | Rien de noté pour aujourd'hui à %1$s |
| `confirm_title_date` | No entry yet for %1$s | %1$s 还没有记录 | %1$s 還沒有記錄 | Rien de noté pour %1$s |
| `confirm_body` | If you remember, just add it. | 记得的话补上就好。 | 記得的話補上就好。 | Si vous vous en souvenez, ajoutez-le simplement. |
| `confirm_body_with` | %1$s. If you remember, just add it. | %1$s。记得的话补上就好。 | %1$s。記得的話補上就好。 | %1$s. Si vous vous en souvenez, ajoutez-le simplement. |
| `confirm_took` | I took it | 我服过了 | 我服過了 | Je l'ai pris |
| `confirm_not_taken` | I didn't take it | 没有服 | 沒有服 | Je ne l'ai pas pris |
| `confirm_more` | %1$d more | 还有 %1$d 条 | 還有 %1$d 筆 | %1$d de plus |
| `status_you_skipped` | You skipped | 你跳过了 | 你跳過了 | Vous avez sauté |

`today_overdue` 不再被引用。lint 只报告 UnusedResources 警告；它与其他旧键留待统一清理。

## 7. 首页“还没有记录”卡（C）

- **范围**：只看最近 48 小时，区间两端都包含：`now − 48h ≤ scheduled_utc ≤ now`。条件是“未确认”（`status=MISSED` 且 `origin=AUTO_MISSED`），并排除已删除的记录。最新的排在前面。
- **显示**：
  - 最多 2 条，多出的显示“还有 n 条 → 服药记录”；点它进入服药记录，并只显示这些条目（复用已有的 `importedRecordIds` 筛选）。
  - 没有未确认的剂量时整张卡不出现。
  - 外观：虚线 unconfirmed 描边，每行带 ? 图标；标题有 heading 语义。
  - 简洁模式隐藏药名，只显示剂量。
- **“我服过了”**：设置 `historyDialogs.editRecord`，打开的正是服药记录“补记”用的 IntakeDialog（标题 `history_backfill`）。
- **“没有服”**：调用已有的 `model.confirmMissed(id)`。
- **按钮**：两个按钮的最小高度都是 48dp。

## 8. 测试

所有新测试都先运行过：确认卡测试最初因测试本身的问题失败（见 §8.3），修正后全部通过。

| 测试 | 内容 | 结果 |
|---|---|---|
| `core/ui StatusStylesTest`（7） | 状态表穷举 DoseState / SlotState / DoseStatus / ofRecord；没有状态用 danger；每个上色的状态都有符号或文字；灰度下各状态的符号、描边、虚线组合互不相同；动态色只影响 action / success / danger；任务书取值 | 7/7 PASS |
| `SemanticContrastTest`（180） | 3 档对比度 × 浅 / 深，加 3 组动态种子 × 浅 / 深；测试名含令牌名 | 180/180 PASS |
| `Ui06ConfirmCardTest`（5） | 48h 边界（含两端，−1ms 和 +1ms 排除，已删除 / 已确认 / 已服排除）；最多 2 条加“更多”，回调拿到全部 3 条；按钮只回调、不自行写入；为空时不渲染；简洁模式 | 5/5 PASS |
| `Ui06ConfirmCardShellTest`（2，真实 AppShell 加内存数据库） | “我服过了”打开的对话框，文字逐项等于服药记录“补记”打开的对话框，打开再关闭不写库；“没有服”后的记录与服药记录“确认漏服”的结果逐字段相等（origin=APP，revision=2），记录总数不变 | 2/2 PASS |
| `Ui06AccessibilityTest`（16） | en / zh-CN / zh-TW / fr × 浅 / 深，320dp，2× 字号：卡片朗读顺序为标题 → 药名剂量 → 我服过了 → 没有服；标题有 heading；触控目标 ≥ 48dp；按钮不超出卡片；日格朗读含“未确认 / 已漏服”；图例含未确认 | 16/16 PASS |
| `Ui06ShapeTest` | `Corners.tag` = 10dp，`Corners.pill` = 50%，不再有 badge；`StatusPill` 和 `StatusChip` 都用 pill | PASS |
| `Ui06SourceGuardTest`（3） | 日历、今天卡、服药记录、时间线、StatusUi、确认卡中没有 error / errorContainer（唯一允许的是 `operation_error` 一行）；库存两处提示使用 attention；日历不出现“提醒未送达” | 3/3 PASS |
| `Ui06GrayscaleTest` | 浅 / 深各 6 个上色状态，文字相同（空）时去色后两两之间仍有 ≥ 20 个像素灰度差 > 24 | PASS |

主按钮保持默认的完全圆角，没有改动。

### 8.1 全量回归（本地）

- core:domain 135/135；core:ui 187/187；core:data 263/263；core:reminder 20/20。
- app：1873 项，319 个既有 SKIPPED（截图类测试，需要环境变量才运行）。首轮有 4 项失败（§8.2），修正后 CalendarCoreFlowTest 10/10 PASS。
- `PeriodStabilityTest` 包含在 app 测试中，未改动、未跳过。
- lint：0 错误。
- androidTest Kotlin 编译：PASS。

### 8.2 迁移的断言（逐条说明）

| 测试 | 原断言 | 新断言 | 原因 |
|---|---|---|---|
| `Ui02ContrastTest` "today overdue" | onErrorContainer / errorContainer ≥ 4.5 | attention.onContainer / container ≥ 4.5 | 已过时间改为 attention |
| `Ui02ContrastTest` "badge missed" | onErrorContainer / errorContainer ≥ 3 | missed.onContainer / container ≥ 3 | 漏服改为 missed |
| `Ui02ContrastTest` "mark missed" | error / errorContainer ≥ 3 | missed.accent / container ≥ 3 | 同上 |
| `TodayCardTest`、`Ui05TodayCardTest`：today-next-status | `today_overdue`（“已过时间”） | `status_past_time`（“已过时间 · 可以补记”） | 任务书 B 的文案 |
| `CalendarDayMarkTest` | DayKind → DayMark 只有 3 项 | 增加 `UNCONFIRMED to QUESTION` | 未确认日有了 ? 符号 |
| `CalendarCoreFlowTest.selectTomorrow` / `layoutHolds` | 直接等待日格出现；滚到顶部后数日格 | 先滚动到日格，再等待和计数；断言内容（7/35/42 格、全部在屏幕宽度内、主操作可达）不变 | 该夹具过去 3 天的剂量是未确认的，确认卡出现在今天卡下方；在 320×568 上月历不在首屏，LazyColumn 不会组合它 |

另有一处实现修正：`LocalSemanticColors` 在 NotesTheme 之外（直接用 MaterialTheme 包裹的测试或预览）回退为浅色标准令牌，不再抛异常。

### 8.3 测试自身的修正（不是产品问题）

- 朗读顺序断言起初把行容器自己的标签也算了进去。
- 灰度测试改用 decorView 绘制截取，因为 `captureToImage` 在 Robolectric 下不稳定。
- AppShell 测试改为点“取消”关闭对话框，而不是直接 dismiss 窗口。

## 9. 截图

位置：`docs/ui-audit/2026-10-21-ui06/`。测试 `Ui06ScreenshotTest`，设置 `UI06_SHOTS=<label>` 时运行。“之前”一组用同一文件在 320171cd 上渲染。

| 目录 | 内容 |
|---|---|
| `after/`、`before/` | 4 语 × 320 / 411dp × 字号 1 / 2 × 浅 / 深；首页（顶部和 3 个滚动位置）、周视图、服药记录、库存、时间线。各 256 张，缩为 1/3 并量化 |
| `hc/` | 高对比度：4 语 × 浅 / 深，前后各一组 |
| `hires/` | 411dp、字号 1、zh-CN、浅 / 深，xxxhdpi 原图；占位药名 药物 A / B / C |
| `compare/` | 前后并排（左前右后）：zh-CN 411 浅 / 深、en 320 2×、fr 320 2× 深 |
| `grayscale/` | 首页月历和服药记录去色后的前后对比：改前未确认日与计划日无法区分，改后是虚线框加 ? |

## 10. NOT_RUN

- 真机和 TalkBack 实测：真机清单第 12 节全部 NOT_RUN。
- 系统“灰度”模式与真实动态壁纸：本地只用合成种子和去色截图代替。
- 原生 androidTest 执行：本地只编译，执行交给 CI。
- 不出 APK，不签名，不改 versionCode。
