# HRT Log — UI-04 任务书：日历页今天的服药只显示一次（2026-10-16）

> 交给 Claude Code 执行。负责人：DevenirTwilight。本任务书即本轮授权来源，开工时原文归档并写入 REQUIREMENTS（§101）。

## 0. 问题（负责人真机反馈）

UI-01 之后，日历页打开时默认选中“今天”，同一次服药会出现两遍：

1. 顶部“今天”卡：显示下一次服药、同一时刻的其他药、今天已服的记录；
2. 月历下方的当日列表（`DayHeader` 显示“今天”）：又用 `DoseCard` 和 `RecordLine` 把今天的未完成服药和记录全部列一遍。

代码位置：`CalendarScreen.kt` 中 `item(key = "today")` 和 `if (view != CalView.YEAR) { item(key = "dayhead") … }` 两段，它们对今天使用的是同一批 `displayedUpcoming` 和 `extra.records`。

## 1. 决定（负责人已同意）

**今天的服药只在“今天”卡里出现；月历下方的列表只用来查看其他日期。**

- 选中日期 = 今天：月历下方**不显示**当日区（不显示 `DayHeader`、`DoseCard`、`RecordLine`，也不显示“这天没有记录”）。
- 选中日期 ≠ 今天：当日区与现在完全相同。
- 在月历里点“今天”这一格、点 `PeriodBar` 的“今天”按钮，或从别的日期切回今天时，列表滚动到顶部，让今天卡可见（`LazyListState.animateScrollToItem`，目标是今天卡对应的 item）。周视图、日视图同样适用。

## 2. 今天卡必须补齐的内容

隐藏当日区后，今天卡要接住原来只在当日区出现的信息，**不能让任何信息或操作消失**：

1. **今天全部未完成的服药**。现在今天卡只显示“下一次”和“同一时间”的药，之后时间点的药（例如 21:00 那一次）原本只在当日区里。在“同一时间”之后加一个“稍后”分区，每行一个：时间 · 药名 · 剂量，加 ⋮ 菜单（与现有 `DoseMenu` 相同，含“现在服用”“改期或跳过”）。行内不放大按钮，避免抢“下一次”的主操作。
2. **今天未按时完成的记录**。现在“今天已完成”只列 ON_TIME / LATE。今天的 MISSED、未确认（`unconfirmed`）、SKIPPED 记录原本只在 `RecordLine` 里显示，需要并入今天卡的记录区，每条显示时间 · 药名 · 剂量 · **状态文字**（复用现有 `status_missed`、`status_unconfirmed`、`status_skipped`，不能只靠颜色）。分区标题由“今天已完成”改为“今天的记录”（新 key）。未确认记录保留现有说明 `unconfirmed_help` 的含义（可放在该行下方的小字）。
3. **今天的预约**。原本只在当日区以 `AppointmentCard` 显示，改为今天卡里的一行：类型 · 时间 · 医生/地点，点击仍调用现有 `onAppointment`。
4. 所有操作继续走现有回调（`onComplete`、`onChange`、`onAppointment`），**不新增写入路径**；药名、剂量继续用 `medOf()` 和记录的 `config_snapshot`。
5. 简洁模式下隐藏药名的规则保持不变。
6. 卡片变长时的层级规则：
   - “下一次”始终是最大、最先读到的部分；
   - “稍后”和“今天的记录”用较小字号；
   - 同类条目超过 3 条时，默认显示 3 条，加“显示全部（n）”展开按钮；展开状态用 `rememberSaveable` 保存。

进度“已服 x / y”的算法不变。

## 3. 测试

先写失败测试，再实现：

1. 默认打开日历（选中今天）时，同一个 slot key 在屏幕上只出现一次，同一条记录 id 只出现一次（遍历语义树断言 `DoseCard` / `RecordLine` 不存在于今天）。
2. 选中其他日期：当日区照常显示 `DoseCard` 和 `RecordLine`，内容与改动前一致。
3. 今天有 3 个不同时刻的药：下一次在主区，其余在“稍后”，每行 ⋮ 菜单打开的是与原 `DoseCard` 相同的对话框（复用 `CalendarCoreFlowTest` 的验证方式）。
4. 今天有 MISSED、未确认、SKIPPED 记录各一条：都出现在“今天的记录”，状态有文字。
5. 今天有预约：今天卡里有一行，点击后打开该预约，与原 `AppointmentCard` 行为一致。
6. 从其他日期点“今天”：列表滚到今天卡可见。
7. 超过 3 条时的折叠与展开；进程重建后展开状态保留。
8. TalkBack 朗读顺序：日期 → 进度 → 下一次 → 主按钮 → 稍后 → 今天的记录 → 预约。

必须通过（不得放宽）：
- `PeriodStabilityTest`（交付门槛）；
- `TodayCardTest`、`CalendarCoreFlowTest`、`CalendarSemanticsTest`、`CalendarSelectionRestoreTest`、`ButtonLayoutRegressionTest`、`NotesAppCrossPageFlowTest`、`AppShellBackNavigationTest`、`Ui03DrawerTest`、`TranslationsTest`；
- 设备测试 `CalendarIntakeAndroidTest`、`AfterIntakePromptAndroidTest`、`HomeAddStockAndroidTest`；
- 完整 `:app:testFullDebugUnitTest`、lint 和全部 CI job。

**断言迁移规则**：现有测试如果通过“今天的 `DoseCard`”完成服药，改为经今天卡的等价按钮完成，或改为选中另一天的 `DoseCard`。原断言的含义（打开同一对话框、记录结果一致、身心提示照常出现）必须保留，并在报告中逐条列出。不得删除断言。

截图：日历页四语 × 320/411dp × 字号 1/2 × 明暗，覆盖以下场景，前后对比：
- 今天只有一次服药；
- 今天多次服药，且有漏服和预约；
- 选中其他日期。

存入 `docs/ui-audit/2026-10-16-ui04/`，只用合成数据。

## 4. 文案

新增 key 四语同步（en、zh-CN、zh-TW、fr），`TranslationsTest` 通过。候选：

| 用途 | zh-CN | zh-TW | en | fr |
|---|---|---|---|---|
| 稍后分区 | 稍后 | 稍後 | Later today | Plus tard |
| 记录分区（替换“今天已完成”） | 今天的记录 | 今天的紀錄 | Today’s log | Journal du jour |
| 预约行标题 | 今天的预约 | 今天的預約 | Today’s appointment | Rendez-vous du jour |
| 展开 | 显示全部（%1$d） | 顯示全部（%1$d） | Show all (%1$d) | Tout afficher (%1$d) |

不再使用的 `today_done_heading` 在确认无引用后四语删除。

## 5. 范围与禁止

- 只改 `CalendarScreen.kt`、`TodayCard.kt`（均为 HISTORY_ONLY 或不在矩阵中）、字符串和测试。开工前核对 `B0-protected-file-matrix.json`；若必须触及 PK_DISPLAY_REVIEW 或更严格类别的文件，暂停并报告。
- 不改 ViewModel、仓库、Room schema、备份、加密、应用锁、伪装、PK 和浓度页。
- 不改抽屉、设置、库存、时间线、医疗档案页面。
- 不新增权限、网络、埋点或依赖。

## 6. 交付

1. 从最新默认分支建立 `feature/ui04-today-dedup-20261016`。新增 REQUIREMENTS §101，原文归档到 `docs/audits/product-quality-2026-10/sources/ui04/`。写报告 `65-ui04-today-dedup.md`（编号以实际为准），内容包括：改动文件、四语对照、前后截图、迁移的断言清单、NOT_RUN 项。
2. 最终 HEAD 的全部必需检查成功、评审线程已解决后，普通合并（不用 `--auto` 或 `--admin`，不改 ruleset），然后复验默认分支 CI。
3. 在 `docs/ui-audit/…/manual-checklist.md` 追加一项：真机上今天的每次服药只出现一次，结果留给负责人回填。
4. **APK（负责人如不需要可删除本条）**：合并且主线 CI 全绿后，按 §100 的同一流程交付一次正式签名 full Universal Release：
   - 原证书，密钥只在仓库外临时恢复，用后删除；
   - versionCode 27 → 28，versionName 仍为 0.2.0；
   - 核验来源、版本、签名、对齐、权限；
   - 用会话的私密文件发送功能交付；
   - 在 `docs/delivery/ledger.json` 追加记录。

   本条授权仅限这一次，不包括公开 Release 或标签。
5. 更新 `docs/HANDOFF.md`、`docs/STATUS.md`。
