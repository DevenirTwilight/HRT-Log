# 65 — UI-04：日历页今天的服药只显示一次（2026-10-16）

- 任务书：`sources/ui04/ui04-prompt.md`；REQUIREMENTS §101。
- 分支：`feature/ui04-today-dedup-20261016`，基于主线 a3146df。

## 1. 问题与决定

负责人真机反馈：UI-01 之后，默认选中今天时，同一次服药既出现在顶部的“今天”卡里，又出现在月历下方的当日列表里（DayHeader “今天”、DoseCard、RecordLine）。

决定：今天的内容只放在今天卡里，月历下方的列表只用于其他日期。

- **选中今天时**：不显示当日区，包括 DayHeader、DoseCard、RecordLine、预约卡和“这天没有记录”。没有药物时页面不显示今天卡，此时当日区仍照常显示，避免丢失今天的预约。
- **选中其他日期时**：与改动前完全一致。截图 `2_other_date` 的 64 张逐像素相同。
- **回到今天时**：点今天这一格、点“今天”按钮、用前后箭头回到今天，或从年视图选回今天所在的月份并落在今天时，列表用 `animateScrollToItem` 滚到今天卡。月、周、日视图都适用。

## 2. 今天卡补齐的内容（没有任何信息或操作消失）

| 原来只在当日区出现的内容 | 现在在今天卡中的位置 |
|---|---|
| “下一次”和“同一时间”之后的未完成服药 | **稍后**：每行“时间 · 药名 · 剂量”加 ⋮ 菜单，与“下一次”的菜单相同（“现在服用”“改期或跳过”）；行内不放大按钮 |
| 今天的漏服、未确认、已跳过记录（RecordLine） | **今天的记录**（取代“今天已完成”）：每条“时间 · 药名 · 剂量 · 状态文字”，状态文字复用 `status_on_time/late/missed/unconfirmed/skipped`；未确认的记录下方保留 `unconfirmed_help` 小字 |
| 今天的预约（AppointmentCard） | **今天的预约**：每个预约一行，“类型 · 时间 · 医生 · 地点”；点击调用同一个 `onAppointment`；角色是按钮，最小高度 48dp |

另外：
- **数据来源不变**：所有操作仍走 `onComplete`、`onChange`、`onAppointment`，不新增写入路径；药名和剂量仍取自 `medOf()` 和记录的 `config_snapshot`。
- **简洁模式**：仍然隐藏药名。
- **层级**：“下一次”用 headlineMedium，仍然最大、最先读到；三个分区的标题用 labelLarge 并带 heading 语义，行文字用 bodyMedium。
- **折叠**：任一分区超过 3 条时，默认显示 3 条，加“显示全部（n）”；展开状态用 `rememberSaveable` 保存，进程重建后保留。
- **进度**：“已服 x / y”的算法不变，x 仍然只数按时和较晚服用的记录。

## 3. 改动文件

| 文件 | 保护矩阵类别 | 改动 |
|---|---|---|
| `ui/CalendarScreen.kt` | HISTORY_ONLY | 选中今天时隐藏当日区；`LazyListState` 加上回到今天的滚动；把今天的全部记录和预约传给今天卡；当日区各项加测试 tag（`day-head`、`day-dose:`、`day-record:`、`day-appt:`） |
| `ui/TodayCard.kt` | 不在矩阵中 | 新增“稍后”“今天的记录”“今天的预约”三个分区和折叠逻辑；`recordStatus()` |
| 四语 `strings.xml` | HISTORY_ONLY | 新增 4 个 key，删除 `today_done_heading` |
| 测试与文档 | — | 见下文 |

没有改 ViewModel、仓库、Schema、备份、加密、锁、伪装、PK 和浓度页，也没有改抽屉、设置、库存、时间线和医疗档案页面。没有改任何 PK_DISPLAY_REVIEW 文件。

## 4. 四语对照

| key | zh-CN | zh-TW | en | fr |
|---|---|---|---|---|
| `today_later` | 稍后 | 稍後 | Later today | Plus tard |
| `today_log_heading` | 今天的记录 | 今天的紀錄 | Today’s log | Journal du jour |
| `today_appointment` | 今天的预约 | 今天的預約 | Today’s appointment | Rendez-vous du jour |
| `today_show_all` | 显示全部（%1$d） | 顯示全部（%1$d） | Show all (%1$d) | Tout afficher (%1$d) |

删除 `today_done_heading`（四语）：删除前 grep 确认只有 `TodayCard.kt` 引用它。

## 5. 测试

`Ui04TodayDedupTest` 共 8 例，在实现之前写好，在 UI-01 版日历上运行时 7 例失败。唯一通过的是“其他日期照常”，它本来就应该在改动前后都通过。

| # | 任务书要求 | 测试 |
|---|---|---|
| 1 | 选中今天时，同一服药、记录、预约只出现一次 | `todaySelectedShowsEachDoseRecordAndAppointmentOnce`：滚动整个列表收集语义树 tag，断言没有 `day-head`、`day-dose:`、`day-record:`、`day-appt:`，今天的各项都在今天卡中 |
| 2 | 其他日期照常 | `anotherDateStillShowsItsDayList`：DoseCard、RecordLine、预约卡都在，点击“完成服药”传出同一条目 |
| 3 | 三个时刻：下一次在主区，其余在“稍后”，菜单相同 | `laterDosesAreListedWithTheSameMenu`：“稍后”行没有大按钮，⋮ 的两项分别调用 `onComplete` 和 `onChange`，与 DoseCard 的按钮是同一组回调，在 AppShell 中打开同一个 IntakeDialog / OverrideDialog |
| 4 | 漏服、未确认、跳过都有状态文字 | `todaysRecordsSayTheirStatusInWords`：另外检查了未确认说明，以及进度仍为 0 / 4 |
| 5 | 预约一行，点击打开 | `todaysAppointmentIsARowThatOpensIt` |
| 6 | 从其他日期点“今天”滚回今天卡 | `goingBackToTodayScrollsTheTodayCardIntoView`（320×640）；临时去掉滚动代码时，这一例确实失败 |
| 7 | 超过 3 条时折叠和展开，重建后保留 | `moreThanThreeCollapseAndStayExpandedAfterRestoration`（`StateRestorationTester`） |
| 8 | TalkBack 顺序 | `talkBackReadsDateProgressNextButtonLaterLogAppointment`：日期 → 进度 → 下一次 → 主按钮 → 稍后 → 今天的记录 → 预约 |

本地通过：`Ui04TodayDedupTest`、`TodayCardTest`、`HomeStockCardTest`、`PeriodStabilityTest`（交付门槛）、`CalendarCoreFlowTest`、`CalendarSemanticsTest`、`CalendarSelectionRestoreTest`、`ButtonLayoutRegressionTest`、`NotesAppCrossPageFlowTest`、`AppShellBackNavigationTest`、`Ui03DrawerTest`、`TranslationsTest`、CalendarDayMark、`TimelineFilterAndHomeStockTest`、`Ui02ContrastTest`。

完整单元测试、lint 和设备测试（`CalendarIntakeAndroidTest`、`AfterIntakePromptAndroidTest`、`HomeAddStockAndroidTest` 等）见 PR 的 CI。

## 6. 断言迁移清单

没有删除、放宽或修改任何断言。

1. **`CalendarCoreFlowTest.selectTomorrow()`**：
   - 今天卡现在也列出今天的记录：测试数据同步后会生成一条今天的自动漏服记录，所以卡片变高。
   - 在 320×568 的屏幕上，月历中明天那一格被推到了屏幕下方（y=736），原来的触摸点击落在屏幕外，选择没有发生。
   - 现在先把列表滚到那一格（同一文件中已有“把要检查的项滚入视野，像用户在短屏上那样”的做法）。再用它的点击操作激活，因为滚到底边后，浮动“添加”按钮正好盖在右侧的格子上。设备测试 `CalendarIntakeAndroidTest` 出于同样原因已经这样做。
   - 后续断言（明天被选中、“完成服药”可见可点、布局在四语、两种字号、明暗下成立、没有写入）全部原样保留。
2. **`TodayCardTest.severalMedicationsAtTheSameTimeEachHaveAnAction`**：只改了一行注释（“稍后的服药留在下方当日列表”改为“在‘稍后’分区”）。断言 `today-also:<later>` 不存在仍然成立，没有改。
3. **`TodayCardTest`** 中 `today-done:<id>` 的断言照常通过：记录行沿用这个 tag，并在文字末尾加上状态。

4. **`HomeAddStockAndroidTest`**（设备测试）：
   - PR 首轮 CI 中失败：首页库存卡“添加库存”的点击没有打开对话框。
   - 用同一个 `CalendarTestActivity` 在 320×640 下复现：今天卡变高后，库存卡成了屏幕下方的最后一项；`performScrollToNode` 只滚到刚好可见，按钮停在底边（y 588–640），正好被浮动“添加”按钮（y 568–624）盖住。主线上同一位置是 y 476–528，不重叠。
   - 列表底部本来就留了 96dp 内边距，用户把列表滚到底，最后一张卡就会离开浮动按钮。
   - 测试改为先滚到底再点击：复现中按钮回到 y 476–528，与主线相同。
   - 断言（打开同一个对话框、写入相同数据）原样保留。

另外三个设备测试 `CalendarIntakeAndroidTest`、`AfterIntakePromptAndroidTest` 和 `NotificationEntryAndroidTest`，都通过选中明天的 DoseCard 或通知入口操作，不经过今天的当日区，所以没有修改。

## 7. 截图

保存在 `docs/ui-audit/2026-10-16-ui04/`，说明见该目录的 [README](../../ui-audit/2026-10-16-ui04/README.md)，改前和改后各 192 张：

| 场景 | 结果 |
|---|---|
| 其他日期 | 64/64 一致 |
| 今天一次服药 | 64/64 改变 |
| 今天多次服药加漏服和预约 | 54/64 改变（其余 10 张是大字号顶部截图，只拍到未改的“下一次”部分） |

对比图示例：[一次服药，月历下方](../../ui-audit/2026-10-16-ui04/compare/0b_one_dose_below_zh-rCN_411dp_1x_light.png)、[多次服药，顶部](../../ui-audit/2026-10-16-ui04/compare/1a_several_top_zh-rCN_411dp_1x_light.png)。

## 8. NOT_RUN

`docs/ui-audit/2026-10-15-ui03/manual-checklist.md` 第 9 节新增 5 项，全部 NOT_RUN，结果留给负责人回填：
- 今天每次服药只出现一次；
- “稍后”的菜单；
- 记录的状态文字；
- 回到今天时滚动；
- TalkBack 朗读顺序。

## 9. 交付

合并且主线 CI 全绿后，按 §101 和 §100 交付 build28 正式签名 APK，结果另行记录在交付账本中。
