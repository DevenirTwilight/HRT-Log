# 61 · UI-02A：UI-01 收尾、设置子页面、间距 token（2026-10-15）

任务书原文：`sources/ui02/ui02-task-book-2026-10-15.md`；授权范围见 REQUIREMENTS §97。

前置条件已满足：
- UI-01（#38）已合并为 47bd1b2；
- 合并后主线 Android 38749639264 与 Science 38749638770 全部成功。

本 PR 只改界面和展示层：
- 没有新的写入路径，没有新权限、网络或第三方库；
- 未改 schema、备份、加密、应用锁、伪装外壳、PK、浓度页、PDF；
- 没有触及 PK_DISPLAY_REVIEW 文件；
- 不签名、不交付。

## 1. 改动文件与保护矩阵

| 文件 | 类别 | 改动 |
|---|---|---|
| `ui/SettingsScreen.kt` | HISTORY_ONLY | A2：总览 + 6 个子页面；各 section 原样迁入 |
| `ui/AppShell.kt` | HISTORY_ONLY | A2：子页面状态（`rememberSaveable`）、顶栏返回箭头与标题、返回顺序、总览摘要；A1：首页“添加库存”接线 |
| `ui/TodayCard.kt` | 不在矩阵（UI-01 新文件） | A1：首页库存行两个按钮 |
| `ui/CalendarScreen.kt` | HISTORY_ONLY | A1：新增 `onAddStock` 参数并转交 |
| `ui/LongitudinalScreen.kt` | HISTORY_ONLY | A1：类型筛选 chip（纯前端） |
| `core/ui/.../Tokens.kt`（新） | 不在矩阵 | A3：`Spacing`、`Corners` |
| `res/values*/strings.xml` | — | 新增 18 个 key，删除 5 个，四语同步 |
| `debug/CalendarTestActivity.kt` | debug 测试宿主 | 暴露 `model`，供设备测试刷新数据 |

## 2. 实现说明

### A1 · UI-01 收尾
- **删除 5 个字符串**：`period_timeline_intro`、`timeline_zone`、`forecast_title`、`forecast_note`、`forecast_add`，四语同时删除。
  - 删除前已 grep 确认 `src/main`、`src/full`、`src/debug` 和 core 都没有引用。
  - 唯一的测试引用在 `TodayCardTest`，断言“首页不显示这句通用脚注”。改为按原英文文本断言它不存在，原意不变。
- **首页库存卡“添加库存”**：
  - 每行在“查看库存”之外加“添加库存”。它调用 `CalendarScreen.onAddStock`，AppShell 中设为 `addStock = it`，与库存页传入的是同一个状态变量，因此打开的是同一个 `AddStockDialog`，保存走同一个 `model.addContainers`。没有复制对话框。
  - 两个按钮用 `AdaptiveActions`：放得下就并排，放不下就纵向排列。
- **时间线类型筛选**：全部 / 化验 / 就诊 / 里程碑 / 其他。
  - 只过滤显示的事件行；时期卡、即将发生和未知分区保留；导入记录块只在“全部”和“其他”中显示。
  - 选中状态用 `rememberSaveable` 保存；每个 chip 高度至少 48dp，并带 `selected` 语义。
  - 如果保存里程碑后需要聚焦的事件被当前筛选隐藏，先切回“全部”再滚动过去。
- **对比度**：
  - `Ui02ContrastTest` 在标准、中、高三种对比度 × 明暗下，计算今天卡、低库存卡（文字和两个按钮）、事件类型徽标、日历标记和设置“需要处理”文字所用 token 对的 WCAG 对比度：正文 ≥4.5，徽标和标记等图形 ≥3.0。
  - 全部 6 种组合都达标，因此无需修改 `NotesTheme`。
  - `Ui01ScreenshotTest` 新增环境变量 `UI01_CONTRAST`，可在中/高对比度下渲染同一组 5 个页面。

### A2 · 设置总览与子页面
- 总览有 6 行，每行 ≥56dp，包含图标、标题、一行摘要和右箭头。整行是一个按钮，朗读内容为“标题 + 摘要”。
- 摘要：
  - 提醒：“全部正常”，或 tertiary 色加粗的“n 项需要处理”。它与子页面的三项检查来自同一组 `remember(resumeRevision())` 状态，没有分别计算。
  - 外观与语言：主题 · 语言。
  - 身心状态：地区名称或“未设置”。
  - 隐私与安全：应用锁已开启 / 未开启（每次切换页面时重新读取 `AppLock.enabled`）。
  - 数据与备份：最近备份时间或“从未备份”（取自 `backupHistory.state`）。
  - 回收站：条目数。
- 子页面中的提醒、外观、主题、对比度、语言、身心提示、地区、`PrivacySection`、`DisguiseSection`（full 变体）、`DataSection`、`RecycleBinSection` 都是原 composable，回调、对话框和测试 tag 都没有改，只是换到对应页面渲染。
- 返回顺序：
  - 子页面 → 总览 → 日历；
  - 新增的 BackHandler 注册在原“回到日历”处理器之后，所以优先生效；
  - 抽屉打开时先关抽屉；对话框仍最优先；
  - 伪装模式与应用锁下（`backToCalendar = false`）这个处理器不启用，行为不变。
- 顶栏：子页面显示返回箭头和子页面标题，不显示菜单按钮。从抽屉进入设置时总是回到总览。“关于”仍是抽屉里的独立入口。
- “删除全部数据”仍在数据页，流程未改；`LocalDataWipeCoverageTest` 通过。

### A3 · Token
- 新增 `Spacing`（xs 4 / s 8 / m 12 / l 16 / xl 24dp）和 `Corners`（card 24 / list 16 / badge 10）。
- 只用在本 PR 新写或改动的代码中，替换的都是等值的数字，渲染尺寸不变。
- 本 PR 没有去改其他文件里的字面数字。

## 3. 测试

| 任务书要求 | 测试 | 本地结果 |
|---|---|---|
| 1 首页添加库存与库存页同一对话框、结果一致 | `TimelineFilterAndHomeStockTest.homeStockRowHasViewAndAddWhichCallTheirCallbacks`（回调为同一个药物）；`HomeAddStockAndroidTest`（原生 emulator，真实 AppShell：先从首页保存，再从库存页 ⋮ 保存，两条新包装除 id 外完全相同） | Robolectric 通过；设备测试见 CI |
| 2 时间线筛选 | `TimelineFilterAndHomeStockTest.eachFilterShowsOnlyItsKindAndSurvivesRecreation`：每种筛选只显示对应事件，“全部”恢复，状态恢复后筛选保留，chip ≥48dp 且带 selected 语义 | 通过 |
| 3 对比度 | `Ui02ContrastTest`：3 种对比度 × 明暗 | 通过 |
| 4 设置总览 | `SettingsOverviewTest`，4 语 × 320dp × 2×：6 行可达、≥56dp、朗读标题 + 摘要、提醒摘要与子页面需要处理的项数一致 | 8/8 |
| 5 返回键 | `AppShellBackNavigationTest` 新增 5 项：逐页 子页面 → 总览 → 日历；顶栏箭头；对话框优先；状态恢复后停留在原子页面；伪装模式下返回交给 activity | 12/12 |
| 6 迁移后回调一致 | `SettingsOverviewTest.movedSectionsKeepTheirCallbacks`（测试提醒、高可靠性开关、可靠性说明、身心提示开关、4 个 slot 各在其页面且只在其页面）；`SettingsButtonInteractionTest` 48 项（外观/对比度三组按钮的选中与持久化） | 通过 |

必须保持通过的既有测试（本地）：
- PeriodStabilityTest 5（交付门槛）
- ButtonLayoutRegressionTest 64（现已覆盖每个设置子页面）
- DrawerScrollUiTest 2、AppShellBackNavigationTest 12
- CalendarSemanticsTest 6、CalendarCoreFlowTest 10
- TodayCardTest 9、HomeStockCardTest 4、StockCardActionsTest 5
- TimelineAndDrawerHierarchyTest 3
- MedicalHomeTest 4、MedicalHomeConfirmFlowTest 1
- SettingsButtonInteractionTest 48、LocalDataWipeCoverageTest 2
- PrivacyNoticeLayoutTest 16、TranslationsTest 1
- LongitudinalUiTest 5、ImportedTimelineUiTest 11、TimelineEditUiTest 2

完整单测、lint、设备测试和全部 CI job 以 CI 为准。

### 发现的既有问题：Robolectric 无法让 AddStockDialog 空闲
在 Robolectric 中，无论从首页还是从库存页（UI-02 之前就有的路径）打开 `AddStockDialog`，测试都等不到空闲。
- 对照：单独渲染这个对话框可以空闲；NotesApp 中有低库存数据、滚动、点击“查看库存”也都可以空闲。
- 仓库里 `CalendarIntakeAndroidTest` 已经记录过同类限制（带输入框的对话框）。
- 因此端到端验证改在原生 emulator 上做（`HomeAddStockAndroidTest`）。这不是本 PR 引入的问题。

### 迁移的定位方式（断言语义不变）
1. `SettingsButtonInteractionTest`：传入 `page = APPEARANCE`（主题和对比度按钮所在的页面），断言不变。
2. `SettingsButtonsAndroidTest`（设备）：先在外观页检查主题和对比度两组，再切到隐私页检查锁定时间组和隐私开关。断言和顺序都不变，`PrivacySection` 改由 `privacySlot` 传入。
3. `ButtonAuditTest`（整类原本 skip）：原来一个“settings”用例，现拆成每页一个，内容相同。
4. `ButtonLayoutRegressionTest`：原 p=3 的设置页现在是总览；另外新增 p=5..10 逐个覆盖 6 个子页面，检查与原来相同（不截断、触控 ≥48dp）。这是加强。
5. `TodayCardTest`：被删除的 `forecast_note` 改为按其原英文文本断言不存在。

## 4. 四语字符串对照

新增 18 个 key：

| key | en | zh-CN | zh-TW | fr |
|---|---|---|---|---|
| `timeline_filter_all` | All | 全部 | 全部 | Tout |
| `timeline_filter_labs` | Labs | 化验 | 檢驗 | Analyses |
| `timeline_filter_visits` | Visits | 就诊 | 就診 | Consultations |
| `timeline_filter_milestones` | Milestones | 里程碑 | 里程碑 | Étapes |
| `timeline_filter_other` | Other | 其他 | 其他 | Autres |
| `settings_reminders` | Reminders | 提醒 | 提醒 | Rappels |
| `settings_appearance_language` | Appearance and language | 外观与语言 | 外觀與語言 | Apparence et langue |
| `settings_privacy_security` | Privacy and security | 隐私与安全 | 隱私與安全 | Confidentialité et sécurité |
| `settings_data_backup` | Data and backup | 数据与备份 | 資料與備份 | Données et sauvegarde |
| `settings_all_ok` | All OK | 全部正常 | 全部正常 | Tout est en ordre |
| `settings_n_need_action` | %1$d need attention | %1$d 项需要处理 | %1$d 項需要處理 | %1$d point(s) à traiter |
| `settings_lock_on` | App lock on | 应用锁已开启 | 應用程式鎖已開啟 | Verrouillage activé |
| `settings_lock_off` | App lock off | 应用锁未开启 | 應用程式鎖未開啟 | Verrouillage désactivé |
| `settings_backup_last` | Last backup %1$s | 最近备份：%1$s | 最近備份：%1$s | Dernière sauvegarde : %1$s |
| `settings_backup_never` | Never backed up | 从未备份 | 從未備份 | Jamais sauvegardé |
| `settings_bin_count` | %1$d items | %1$d 项 | %1$d 項 | %1$d élément(s) |
| `settings_back` | Back to settings | 返回设置 | 返回設定 | Retour aux réglages |
| `settings_appearance_summary` | Theme: %1$s · Language: %2$s | 主题：%1$s · 语言：%2$s | 主題：%1$s · 語言：%2$s | Thème : %1$s · Langue : %2$s |

删除 5 个 key（四语同时删除）：

| key | en（删除前） | zh-CN（删除前） |
|---|---|---|
| `forecast_add` | Add stock | 添加库存 |
| `forecast_note` | Based on your schedule and the open and sealed packages in Stock. | 按服药计划和库存页里已开封、未开封的包装推算。 |
| `forecast_title` | Stock forecast | 库存预计 |
| `timeline_zone` | Viewing time zone: %1$s | 查看时区：%1$s |
| `period_timeline_intro` | Treatment periods from saved plans and stable patterns in actual history. Individual doses are in History. | 已保存方案和稳定历史记录共同组成治疗时期。每一次服药请在历史中查看。 |

修改的已有 key： 无


## 5. 截图

均为合成数据，Robolectric 原生图形渲染（不是真机截图），存放在 `docs/ui-audit/2026-10-15-ui02/`，缩到宽 300px、96 色：
- `after/` 288 张：`Ui02ScreenshotTest`（`UI02_SHOTS`）渲染，4 语 × 320/411dp × 字号 1/2 × 明暗 × 9 个画面（设置总览、6 个子页面、首页库存卡、时间线“化验”筛选）。
- `before/` 96 张：同一测试文件在基线 47bd1b2 上的改动前版本，同条件渲染旧的单页设置（同样的 section）、首页库存卡、无筛选的时间线。
- `contrast/` 72 张：UI-02 的 9 个画面 × 中/高对比度 × en、zh-CN × 411dp × 字号 1 × 明暗。
- `contrast-ui01/` 40 张：UI-01 的 5 个页面，同样的对比度组合（`UI01_CONTRAST`）。
- `compare/` 6 张前后并排对比：设置、首页库存卡、时间线 × {zh-CN 411dp 1× 浅色, en 320dp 2× 深色}。

人工抽查后的修正：主题和语言都跟随系统时，总览摘要原来显示“跟随系统 · 跟随系统”，改为“主题：… · 语言：…”（新增 `settings_appearance_summary`）。

数据页截图中的 `DataSection` 需要应用的 ViewModel，所以渲染的是占位文字；该 section 本身没有改动。

## 6. 底部导航评估（只评估，不实施）

- **候选入口（4 个）**：日历（今天）、库存、医疗档案、更多（打开现有抽屉）。
  - 时间线、浓度、化验等低频页面继续放在抽屉。
  - 设置和关于保留在抽屉底部。
- **与返回键的冲突**：
  - 目前 R-06 规定，任意顶级页面按返回都回到日历。
  - 底部导航通常期望“返回回到上一个标签”或“返回首个标签”。两者可以统一为“回到日历”，但需要重写 `AppShellBackNavigationTest` 中的预期，并重新确认设置子页面、复诊详情等二级页面的返回顺序。
- **与伪装模式的冲突**：
  - 伪装外壳绝不能出现任何真实入口。底部导航只能在已解锁的真实应用中渲染，必须有与 `ShellPrivacyBoundaryTest` 同等的守卫。
  - 底栏常驻屏幕底部，在任务切换缩略图中更容易暴露“这是什么应用”，需要评估 FLAG_SECURE 和缩略图策略。
- **与抽屉的关系**：
  - 两者并存会重复入口。建议底栏只放高频入口，抽屉保留完整列表，并把抽屉的“每天”分组与底栏对齐。
  - 小屏（320dp）加 2× 字号时，底栏会占用约 80dp 高度；日历页的浮动“添加”按钮需要上移，可能遮挡今天卡下方内容，需另做截图验收。
- **建议**：暂不实施。先观察 UI-01/UI-02 之后真机人工清单的结果（尤其是返回键和大字号两项），再由负责人决定是否单独立项。

## 7. NOT_RUN 与待负责人决定
- 真机 TalkBack、系统大字号、短屏、输入法、返回键、伪装模式与应用锁：全部 NOT_RUN。清单见 `docs/ui-audit/2026-10-15-ui02/manual-checklist.md`，由负责人回填。
- 对比度截图只覆盖任务书指定的组合（en、zh-CN × 411dp × 1× × 明暗 × 中/高对比度）。动态取色没有截图。
- 待决定事项：
  - 底部导航是否立项（见第 6 节）；
  - 时间线“其他”筛选目前包含服药、漏服、身心、复盘、计划等全部其余类型，是否需要细分。
