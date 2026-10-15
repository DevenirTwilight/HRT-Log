# 60 · UI-01 界面层级与信息架构改进：验证记录（2026-10-15）

任务书原文：`sources/ui01/ui01-task-book-2026-10-15.md`。授权范围与禁止事项见 REQUIREMENTS §96。负责人在会话中追加了“自动合并”的指示，因此本 PR 在必需检查全部成功后普通合并。

**只改展示层**：
- 未改 Room schema/实体/迁移、备份/PNBAK、加密、应用锁、伪装外壳、PK 引擎、浓度页、导出 PDF。
- 未新增权限、网络、埋点或第三方库。
- 未改版本号或签名，也未交付 APK。
- 没有新的写入路径：所有按钮都调用已有回调。

## 1. 涉及文件与保护矩阵

| 文件 | 保护矩阵类别 | 改动 |
|---|---|---|
| `ui/CalendarScreen.kt` | HISTORY_ONLY | A 今天卡接入、DoseCard 上下排列；B 首页库存卡；C 日历双编码 |
| `ui/TodayCard.kt`（新） | 不在矩阵（新文件） | A `TodayCard`；B `HomeStockCard` |
| `ui/StockScreen.kt` | HISTORY_ONLY | D 单一主按钮、⋮ 菜单、包装记录折叠、低库存 tertiary |
| `ui/Components.kt` | HISTORY_ONLY | `SectionCard` 增加可选 `container` 色参数，默认值不变 |
| `ui/LongitudinalScreen.kt` | HISTORY_ONLY | E 仅展示层 |
| `ui/AppShell.kt` | HISTORY_ONLY | F 抽屉分组；G 回调接线 |
| `ui/MedicalRecordsScreen.kt` | 不在矩阵 | G 医疗档案首页 |
| `res/values*/strings.xml` | — | H 新增 29 个 key，四语同步 |

没有触及任何 PK_DISPLAY_REVIEW 或更严格类别的文件。`LongitudinalProjection`、`PeriodTimeline`、`TimelineEdits` 未改。

## 2. 各节实现

### A. 今天卡

在复核横幅和空状态之后、月历之前显示，使用主色容器（primary/onPrimary），圆角 24dp。内容包括：
- 日期；
- “已服 x / y”：x 是今天 ON_TIME 或 LATE 的记录数，y 是今天全部记录数加上今天未完成的条目数；
- 下一次未完成服药：大号时间、药名、剂量 · 途径；
- 主按钮“现在服用”和一个 ⋮ 菜单（“其他时间”“改期或跳过”）；
- 同一时刻的其他药物逐行列出，每行有自己的“现在服用”和 ⋮；
- “今天已完成”列表：时间 · 药名 · 剂量。

接线：
- 今天卡的按钮调用 `CalendarScreen` 的 `onComplete(e)` 或 `onChange(e)`。这正是 DoseCard 用的回调；AppShell 据此打开同一个 `IntakeDialog`（含 `afterIntake()`）或 `OverrideDialog`。
- 药名和剂量用 `medOf(e)`，与 DoseCard 共用同一个函数：先取 `ruleSnapshots` 冻结快照，没有快照才用当前药物。
- 已完成行使用记录自身的 `config_snapshot`，与 RecordLine 相同。
- 简洁模式下不显示药名，与 DoseCard 相同。

各种状态：
- 无药物：保持原 EmptyState，不显示今天卡。
- 今天无计划：显示“今天没有计划服药”。
- 全部完成：今天没有未完成条目、至少有一条记录、且没有 MISSED 时显示“今天已全部完成”。
- OVERDUE：时间旁有“已过时间”文字块，不只靠颜色。

DoseCard 改为时间在上、药名在下，并去掉了 `maxLines=1`，长药名可以占满整卡宽度。

### B. 首页库存卡

改为只显示 `StockForecast.isLow(today, zone)` 为真的药物。这与库存页“低库存”标签和库存提醒使用的是同一函数、同一 7 个日历日的窗口，没有新阈值。
- 每条一行：药名、`stockForecastText` 的同一句预计文案，以及“查看库存”按钮（跳转库存页）。
- 未追踪、无计划和覆盖充足的药物不再出现在首页，库存页仍完整显示。
- 全部正常时整张卡不渲染。

### C. 日历双编码

月视图格子在颜色之外绘制矢量标记：已服 ✓、部分漏服 ½、已漏服 ✕。
- 漏服格子背景改为 errorContainer，加 error 色描边。
- 今天保持原有的主色圆形日期高亮。
- 图例使用同样的矢量标记。

标记位于格子已有的装饰区（`clearAndSetSemantics` 内），TalkBack 仍只读格子原有的四语描述，`CalendarSemanticsTest` 未改。为便于测试，标记另外记在一个不会被朗读的自定义语义键 `DayMarkKey` 上。标记大小固定为 10dp，不随字号缩放，因此 2× 字号下不会挤压日期数字。

### D. 库存卡

- 每卡只有一个主按钮 `stock-primary-<id>`：有未开封包装时为“开封新的一盒”，否则为“添加库存”。
- 其余已有操作（开封、修正余量、添加库存）在 ⋮ 菜单 `stock-more-<id>` 中，菜单项的 tag 分别为 `stock-replace/check/add-<id>`。
- 包装记录作为默认折叠的分区 `stock-packages-<id>`（标题“包装记录（n）”）。展开后是原有的“管理包装（n）”`stock-manage-<id>`，打开原 `PackagesDialog`；详情、撤销、作废、恢复和分页流程都未改动。
- 低库存卡片使用 tertiaryContainer，并显示“约 n 天后用完”标签；当前库存已经不够下一次服药时，显示原有的 `forecast_empty`。
- 未追踪的药物显示一句说明，加次要（Outlined）按钮“开始记录库存”。

### E. 时间线（只改展示层）

- 删除顶部开发说明 `period_timeline_intro`。
- 时区行只在查看时区规则与设备时区不同时显示，文案为“按 [时区名称] 显示”，用名称而不是 ID。
- 事件行前加 `EventTypeBadge`（圆形底色加图标），全部 12 个 `EventKind` 都有不同的图标；行内文字仍包含类型名，不靠颜色或图标单独区分。
- 时期卡：标题与 ⋮ 放在同一行（tag `period-menu:<key>` 不变），标题有 heading 语义；当前时期显示“第 n 天”（由时期起点日期纯展示计算）。
- 可选的类型筛选 chip 未做。

### F. 抽屉

`DRAWER_GROUPS` 按每天、治疗、医疗、身心分组，分隔线后是设置、关于。
- 12 个入口全部保留，复诊仍是独立入口、名称不变。
- 4 个小标题有 heading 语义。
- `Destination` 枚举的顺序未改，因此 `rememberSaveable` 与通知入口不受影响。

### G. 医疗档案首页

- **无任何文件或附件入口。**
- 顶部只保留一个“记录已发生的就诊”按钮，tag 仍为 `medical-add`。
- “下次就诊”卡（`medical-next:<id>`）只取最近一条未来预约，显示类型、日期时间、医生、地点；“问题清单和就诊资料包”打开该次复诊详情。
- “去过了吗？”：已过去但未确认的就诊，提供“确认已就诊”（`medical-confirm:<id>`）和“编辑或删除”（打开详情）。“确认已就诊”接到复诊详情在医疗档案入口中已使用的同一写入：`saveMedicalVisit(a,a)` → `NotesRepository.saveHistoricalVisit`，写入 `completed_utc`，并带过期记录检查。撤回使用详情中已有的“撤回”（`retractHistoricalVisit`）。
  - 为什么不是 `setVisitCompleted`：两者都是既有的 `completed_utc` 写入（L536–573 一带）；选用前者是为了与详情页完全一致。
- 已确认就诊按年份分组（最新年份在前），每行显示类型图标、日期、医生/机构，以及“有备注”和“问题：n”（只读汇总）。
- 时间旁只在就诊时区与设备时区不同时才显示时区，而且用名称。
- “相关资料”分区：化验、治疗时间线两张入口卡；回收站移到页面底部的文字按钮。
- 不把时间接近显示为关联（§87）。

### H. 文案

新增 29 个 key，四语同步，见第 4 节。没有修改任何已有 key 的译文。

以下旧 key 不再在界面显示，但本 PR 未删除，留给后续清理：
- `period_timeline_intro`、`timeline_zone`
- `forecast_title`、`forecast_note`、`forecast_add`

## 3. 测试

先写新测试（第一次运行时 TodayCard 3 项、HomeStock 2 项失败），再实现。

| 任务书要求 | 测试 | 结果（本地） |
|---|---|---|
| 1 今天卡 | `TodayCardTest`，9 项：无药物、无计划、部分完成、全部完成、OVERDUE、同时刻多药、⋮ 菜单两项、冻结快照取名、朗读顺序（日期→进度→下一次→按钮） | 9/9 |
| 1 与 DoseCard 同一对话框 | 回调同一，由 `CalendarCoreFlowTest`（10 项）证明 DoseCard 的流程不变 | 10/10 |
| 2 首页库存 | `HomeStockCardTest`，4 项：未追踪/无计划不渲染、覆盖不渲染、第 7 天边界与 `isLow` 一致、低/用完各一行一个按钮且回调库存页 | 4/4 |
| 3 日历双编码 | `CalendarDayMarkTest`，4 语 × 320dp × 2× × 暗色：✓/½/✕ 标记、文字描述仍在；另有 `CalendarSemanticsTest` 6 项 | 4/4，6/6 |
| 4 库存卡 | `StockCardActionsTest`，5 项：两种主按钮、恰好一个主按钮、⋮ 含全部原操作、折叠/展开后打开同一对话框、低库存文字 | 5/5 |
| 5 时间线 | `TimelineAndDrawerHierarchyTest`：全部 `EventKind` 有不同图标和文字类型；时区行只在不同时出现且不显示 ID | 通过 |
| 6 抽屉 | 同上：12 个入口全部可达、4 个 heading、320dp/2× 可滚动点击到“关于”；`DrawerScrollUiTest` 2、`AppShellBackNavigationTest` 7 | 通过 |
| 7 医疗档案 | `MedicalHomeTest` 4 项：下次就诊只取最近未来、无未来预约时不显示、确认与编辑回调、年份分组顺序、无附件或文件入口。`MedicalHomeConfirmFlowTest` 1 项（真实 NotesApp 加 Room）：确认写入 `completed_utc`，在详情中撤回后变回 null | 5/5 |

必须保持通过的既有测试（本地）：
- `PeriodStabilityTest` 5/5（交付门槛）
- `ButtonLayoutRegressionTest` 64/64
- `CalendarSemanticsTest` 6、`CalendarCoreFlowTest` 10、`CalendarSelectionRestoreTest` 3
- `StockConsistencyTest` 1、`StockCheckUiTest` 12、`VoidedPackagesUiTest` 20、`PackageHistoryPaginationTest` 24
- `TimelineV2FlowTest` 6、`TimelineEditUiTest` 2、`ImportedTimelineUiTest` 11、`LongitudinalUiTest` 5
- `MedicalRecordsUiTest` 40、`MedicalVisitSavingTest` 3、`MedicalRecordIndexTest` 7
- `TranslationsTest` 1
- `ButtonAuditTest` 12（既有的 12 项均为原本 skip 的审计类）

完整 `:app:testFullDebugUnitTest`、lint、Android 设备测试和全部 CI job 以 CI 为准（本地完整运行会超出 30 分钟上限）。

### 迁移的既有断言

这些断言因控件从按钮行移入 ⋮ 菜单或折叠区，需要先打开菜单或分区；每处的原有含义都保留。

1. `StockCheckUiTest.usedUpOnlyStillOffersCheckStockNotJustAdd`：先点 `stock-more-1` 再点 `stock-check-1`，仍断言调用 check 而非 add；`stock-manage-1` 改为先展开 `stock-packages-1` 后存在。
2. `VoidedPackagesUiTest.normalStockCountExcludesVoided`：新增断言折叠标题计数为 2（不含作废），展开后 `stock-manage-1` 仍是“管理包装（2）”；“3”仍不存在，作废哨兵文字仍不存在。
3. `PackageHistoryUiContract.homeKeepsOneSummaryAndZeroValidPackagesRemainUntracked`：先展开 `stock-packages-1`（断言计数 501），后续断言不变（恰好一个、未组合任何包装行、只剩作废包装时仍为未追踪）。
4. `ButtonLayoutRegressionTest`（p=0 库存页）：原断言是“三个等宽等高按钮，点添加计数 1”。现在只有一个主按钮，改为：
   - 主按钮是“添加库存”，点击计数 1；
   - ⋮ 菜单内“开封新的一盒”“修正余量”各自 ≥48dp、文字一致，点击依次计数 2、3。

   全页文字不截断和 ≥48dp 触控目标的检查仍覆盖库存页。
5. `MedicalRecordsUiContract.archiveHasDiscoverableCorrectionAndHistoryRoutes`：化验和时间线移到“相关资料”，回收站移到底部，因此由“滚到 actions 项”改为“滚到各自的 tag”；仍断言三者可见、可点击、各回调一次。

## 4. 四语字符串对照（新增 29 个 key，无已有 key 修改）

| key | en | zh-CN | zh-TW | fr |
|---|---|---|---|---|
| `today_progress` | %1$d of %2$d taken | 已服 %1$d / %2$d | 已服 %1$d / %2$d | %1$d sur %2$d prises |
| `today_next` | Next | 下一次 | 下一次 | Prochaine prise |
| `today_take_now` | Take now | 现在服用 | 現在服用 | Prendre maintenant |
| `today_other_time` | Record at another time | 其他时间 | 其他時間 | Noter une autre heure |
| `today_change_or_skip` | Reschedule or skip | 改期或跳过 | 改期或跳過 | Reporter ou sauter |
| `today_all_done` | All of today’s doses are done | 今天已全部完成 | 今天已全部完成 | Toutes les prises du jour sont faites |
| `today_nothing` | No doses planned today | 今天没有计划服药 | 今天沒有計畫服藥 | Aucune prise prévue aujourd’hui |
| `today_overdue` | Past time | 已过时间 | 已過時間 | Heure passée |
| `today_same_time` | Also at this time | 同一时间 | 同一時間 | À la même heure |
| `today_done_heading` | Done today | 今天已完成 | 今天已完成 | Faites aujourd’hui |
| `today_more` | More options for this dose | 这次服药的更多操作 | 這次服藥的更多操作 | Plus d’options pour cette prise |
| `home_stock_title` | Running low | 库存即将用完 | 庫存即將用完 | Stock bientôt épuisé |
| `home_stock_view` | View stock | 查看库存 | 查看庫存 | Voir le stock |
| `stock_runs_out_in` | Runs out in about %1$d day(s) | 约 %1$d 天后用完 | 約 %1$d 天後用完 | Épuisé dans environ %1$d jour(s) |
| `stock_packages_section` | Package records (%1$d) | 包装记录（%1$d） | 包裝紀錄（%1$d） | Historique des boîtes (%1$d) |
| `stock_more_actions` | More stock actions | 更多库存操作 | 更多庫存操作 | Autres actions de stock |
| `medical_were_you_there` | Did this visit happen? | 去过了吗？ | 去過了嗎？ | Ce rendez-vous a-t-il eu lieu ? |
| `medical_next_visit` | Next visit | 下次就诊 | 下次就診 | Prochain rendez-vous |
| `medical_next_prepare` | Questions and visit pack | 问题清单和就诊资料包 | 問題清單和就診資料包 | Questions et dossier de consultation |
| `medical_related` | Related records | 相关资料 | 相關資料 | Données associées |
| `medical_has_note` | Has notes | 有备注 | 有備註 | Notes enregistrées |
| `medical_question_count` | Questions: %1$d | 问题：%1$d | 問題：%1$d | Questions : %1$d |
| `medical_edit_or_delete` | Edit or delete | 编辑或删除 | 編輯或刪除 | Modifier ou supprimer |
| `timeline_shown_in` | Shown in %1$s | 按 %1$s 显示 | 按 %1$s 顯示 | Affiché en %1$s |
| `period_day_n` | Day %1$d | 第 %1$d 天 | 第 %1$d 天 | Jour %1$d |
| `drawer_group_daily` | Daily | 每天 | 每天 | Au quotidien |
| `drawer_group_treatment` | Treatment | 治疗 | 治療 | Traitement |
| `drawer_group_medical` | Medical | 医疗 | 醫療 | Santé |
| `drawer_group_body` | Body and mind | 身心 | 身心 | Corps et esprit |

## 5. 截图

均为合成数据，由 `Ui01ScreenshotTest` 在 Robolectric 原生图形模式渲染，平时默认跳过，设 `UI01_SHOTS=<label>` 时运行：
- 4 语 × 320/411dp × 字号 1/2 × 明/暗 = 32 组；
- 每组 5 个页面：日历、库存、时间线、抽屉、医疗档案；
- “改动后”160 张；“改动前”160 张，用同一测试文件在基线 f16c9d6 上按相同条件生成。

存放位置 `docs/ui-audit/2026-10-15-ui01/`，为控制仓库体积，已缩到宽 360px、128 色：
- `before/`、`after/`：文件名为 `<页面>_<语言>_<宽度>_<字号>_<明暗>.png`；
- `compare/`：10 张前后并排对比（5 个页面 × {zh-rCN 411dp 1× 浅色, en 320dp 2× 深色}）。

人工抽查发现并已修正两处：
- 暗色低库存卡的预计文字原为 error 色叠在 tertiary 底上，改为 onTertiaryContainer；
- “下次就诊”卡的按钮原为与卡同色的 tonal 按钮，改为主按钮。

截图不是像素断言，也不是真机截图。

## 6. CI

CI_RESULTS

## 7. 未覆盖（NOT_RUN）与待负责人决定

- 真机 TalkBack、真实短屏设备、输入法交互：NOT_RUN（只有 Robolectric 语义测试和 CI emulator）。
- 高对比度、中对比度与动态取色三种模式只通过 `NotesTheme` token 复用来保证，截图只覆盖标准对比度的明暗两种。
- 时间线类型筛选 chip（可选）未做。
- 待决定：
  - 不再显示的 5 个旧字符串是否删除；
  - “下次就诊”卡是否加入就诊前提醒摘要；
  - 首页库存卡是否需要直接“添加库存”（目前跳转库存页，避免在首页复制 AddStockDialog 的接线）。
- P2 已写入 BACKLOG：NotesApp 状态拆分、设置页子页面、底部导航评估。
