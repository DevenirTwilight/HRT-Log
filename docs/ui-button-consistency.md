# 按钮大小一致性审计与修复


## 当前修复（2026-10-08，REQUIREMENTS §41 已授权）

起始 SHA `5441e6efac2a9dcb3b38c0a3fb2b85b4d3c2880e`，Build 25/schema 9。§41 最终审计有 **48 个 case**（§42 新增时间线菜单/方案详情后，当前为50）；下方 49 页、旧行号和“等待审核”仅是历史档案。修复复用 Compose 内容测量、等尺寸操作组和不足宽度时的纵向选择；保留动作、样式和数值模型。星期标签已经使用 FlowRow，未重复实现。

常规 CI 门槛 `ButtonLayoutRegressionTest` 覆盖四语 × 320/411dp × 浅/深色，字号 2：实际库存、日期时间、日历、设置，断言文本完整、按钮触控范围至少 48dp、操作组尺寸一致和点击回调。另测窄屏时间输入的 12/24 小时值及非法输入禁止确认。

可选审计仍只报告而不因发现失败；`BUTTON_AUDIT=1` 时禁止 Gradle 复用旧测试结果。可用 `BUTTON_AUDIT_LOCALES`、`BUTTON_AUDIT_SCALES`、`BUTTON_AUDIT_WIDTHS`、`BUTTON_AUDIT_THEMES=LIGHT,DARK,HIGH` 和 `BUTTON_AUDIT_CASES` 选择矩阵；错误 case 名直接失败。产物目录为 `app/build/button-audit/<语言>_<字号>_<宽度>_<主题>/`。

初次完整矩阵因原生截图内存导致 Gradle daemon 退出；已回收截图 Bitmap，并改为按单个配置独立运行。最终审计结果见下表与机器可读汇总。Robolectric 的 `hasVisualOverflow`/隐藏字符有边界及横向滚动误报，验收采用每行实际几何、可见控制触控范围与截图复核。合成长药名的可横向滚动筛选不是丢失操作。

### 修复后验收结果

| 条件 | 配置数 × case | 结果 |
| --- | --- | --- |
| 411dp / 浅色 / 四语 / 字号1、1.3、2 | 12 × 48 = 576 | 裁切、越窗、重叠、零尺寸、动作或测量失败信号均为0 |
| 320dp / 四语 / 字号2 / 浅色、深色、高对比度 | 12 × 48 = 576 | 严重信号0；12条私密笔记标题省略号属于可点击打开全文的预览设计 |
| 常规回归门槛 | 48测试（四语 × 320/411dp × 浅/深色 × 3类测试） | 全通过：库存/日期时间/日历/设置/历史正文；12/24h输入与非法值、日期非法值与闰日；资料包确认取消不重叠、等尺寸 |

共同14个case的修复前基线有362条文字裁切/隐藏信号（含Robolectric/只读文字误报，**不是362个独立缺陷**）；修复后相同四语/三字号/411dp范围信号为0。真实严重例子是库存第三按钮0dp、AM/PM与日历双位数字不完整、窄屏记录正文和单位标题被挤压、长确认与取消重叠。新增加的320dp条件没有修复前全矩阵基线，不伪造前后对比。

[机器汇总](ui-audit/2026-10-08-summary.json) 保存每个配置的48 case覆盖数、信号计数、预期省略和共同范围对比。合成数据修复后截图：[库存](ui-audit/2026-10-08-stock-after.png)、[日历](ui-audit/2026-10-08-calendar-after.png)、[时间](ui-audit/2026-10-08-time-after.png)、[窄屏日期](ui-audit/2026-10-08-narrow-date-after.png)、[资料包按钮](ui-audit/2026-10-08-visit-actions-after.png)。功能源码 `069e61d`；完整 [CI 37849334335](https://github.com/DevenirTwilight/HRT-Log/actions/runs/37849334335)（297daca）jvm/android/device-tests均success，CI常规UI48项也已下载报告核对。Build 25、schema9，不包含新正式签名交付。

局限：API35原生Robolectric窗口高1800dp，不是实机。真实短屏/输入法弹出、系统字体、TalkBack、应用锁启用后依赖Keystore的选项、HRT Tracker完整向导和所有时期编辑状态仍需补充设备验收；未将这些称为已通过。日期窄屏输入仍需用户确认真实IME体验。

### 本轮修改文件

- 新增 `app/src/main/java/net/plainnotes/app/ui/AdaptiveControls.kt`；修改 UI：`CalendarScreen.kt`、`Components.kt`、`ConcentrationScreen.kt`、`DataSection.kt`、`Dialogs.kt`、`HistoricalContextDialog.kt`、`HistoryScreen.kt`、`LongitudinalScreen.kt`、`MedicationEditor.kt`、`SettingsScreen.kt`、`StockScreen.kt`、`SummaryExportDialog.kt`、`VisitsScreen.kt`（均在 `app/src/main/java/net/plainnotes/app/ui/`）。
- `app/src/main/res/values/strings.xml`、`values-zh/strings.xml`、`values-b+zh+Hant/strings.xml`、`values-fr/strings.xml`：日期/时/分输入标签。
- `app/src/testFull/java/net/plainnotes/app/audit/ButtonLayoutRegressionTest.kt`（新增）、`ButtonAuditTest.kt`、`app/src/test/java/net/plainnotes/app/TimelineV2FlowTest.kt`；`app/build.gradle.kts` 禁止审计复用旧结果。
- `.github/workflows/android.yml`：构建照常验证，产物改测试/lint报告，不公开上传APK；本轮此前自动生成的4个build-results已移除，设备报告保留。
- 文档：`README.md`、`docs/REQUIREMENTS.md`、`HANDOFF.md`、`PLAN.md`、`hrt-roadmap-2026-10-07.md`、本文件；新增 `BACKLOG.md`、`timeline-v2-regression-2026-10-08.md`；5张合成证据截图及1份JSON汇总在 `docs/ui-audit/`。

## 2026-10-07 历史审计（修复前档案）

要求见 REQUIREMENTS §29：同一行或同一组的按钮，在任何字体缩放和语言下大小一致，不溢出、不截断。本节记录当时的问题和建议；当时尚未授权改界面，现已由 §41 授权并重新验证。行号以审计时的 `ab983c5` 为准。审计期间另一会话已在同一分支交付 build 12–13，重写了时间线页面（本文 `LongitudinalScreen.kt` 相关结论仅适用于旧版），其余文件的行号修复前需重新核对。

## 方法

- 审计测试 `app/src/testFull/java/net/plainnotes/app/audit/ButtonAuditTest.kt`：在 Robolectric（API 35，411 dp 宽，xxhdpi）里用合成数据渲染 49 个页面和对话框（含伪装外壳、复诊页、问题清单和资料包对话框），覆盖英语、简体中文、繁体中文、法语 × 字体缩放 1.0 / 1.3 / 2.0，共 12 种组合。
- 每个页面保存截图，并直接读取语义树：同一父容器内纵向重叠的带文字控件视为一行，记录每个控件的高、宽、文字行数；另外检查控件是否超出窗口，以及控件内文字是否被省略号截断、宽度或高度被裁掉。
- 只在设置环境变量时运行：`BUTTON_AUDIT=1 ./gradlew :app:testFullDebugUnitTest --tests 'net.plainnotes.app.audit.ButtonAuditTest'`。平时的测试和 CI 中自动跳过。全量截图在 `app/build/button-audit/<语言>_<缩放>/`（构建产物，不入库）；下面引用的证据截图已裁剪后放进 `docs/ui-audit/`。
- 局限：
  - Robolectric 的文字渲染与真机略有差异。字体缩放按 Android 14+ 的非线性规则计算，所以 2.0 的实际字号小于 2 倍。
  - 截图窗口高 1800 dp，最下面的少数控件只有测量数据、没有画进截图（标“仅测量”）。
  - 应用锁的“离开后锁定”一行需要 Android Keystore，Robolectric 无法开启应用锁，未渲染，只做了文字长度静态检查。HRT tracker 导入向导未纳入。
  - “文字后段被隐藏”这个信号在 Robolectric 下有误报（例如历史页筛选芯片在截图里显示完整），所以只采用截图能证实的条目。

## 结论一览

| 级别 | 位置 | 现象 |
|---|---|---|
| 严重 | 库存卡三按钮 `StockScreen.kt:105` | 英语 1.0 起就不齐；法语 1.0 “Ajouter du stock”被挤成 11 dp 宽、16 行；2.0 下第三个按钮宽度为 0，完全看不见 |
| 严重 | 复诊详情“编辑 / 删除预约” `VisitsScreen.kt:92`（本批新加） | 法语 2.0 删除按钮被挤成 4 行，文字被裁 |
| 严重 | 日期/时间按钮 `Components.kt:46`（所有选时间的对话框） | 英语 1.3 起时间按钮丢掉 “PM”；繁中 1.3 起只剩“下午”，**时间数字整个看不到** |
| 高 | 编辑药物频率 `MedicationEditor.kt:135` | 法语 1.3 起 “Jours fixes” 被截成 “Jours”，两个选项看起来同名 |
| 高 | 设置“主题”与“对比度” `SettingsScreen.kt:87/96` | 中、繁、法 2.0 主题行变高，英语 2.0 对比度行变高，两行高度不同（即用户在真机上看到的问题） |
| 中 | 其余分段按钮、并排按钮、日历日期格 | 大字体下换行导致同排高度不齐，或数字被裁 |

## 逐项清单

### A. 分段按钮（SegmentedButton）文字换行后高度不齐

| 位置 | 出现条件 | 现象 | 截图 |
|---|---|---|---|
| `SettingsScreen.kt:87` 主题 | 简中、繁中 2.0（“跟随系统”）；法语 2.0（“Système”） | 行内高度不齐 92 / 54.7 dp；与对比度行（54.7 dp）不一致 | `ui-audit/settings_theme_contrast_zh-rCN_2.0.png`；法语仅测量 |
| `SettingsScreen.kt:96` 对比度 | 英语 2.0（“Medium”“Standard”）；法语 2.0（“Standard”） | 行内 90.7 / 53.3 dp；英语 2.0 对比度行 90.7 dp，主题行 53.3 dp，两行不一致 | 仅测量 |
| `CalendarScreen.kt:82` 日/周/月/年 | 英语 2.0（Month）；法语 1.3（Semaine）、2.0（Semaine、Année） | 高度不齐 90.7 / 53.3 dp | `ui-audit/calendar_view_switch_fr-rFR_2.0.png` |
| `ConcentrationScreen.kt:106`、`:132` 校准方式 | 英语 2.0（Retrospective） | 90.7 / 53.3 dp | 仅测量 |
| `ConcentrationScreen.kt:188`、`:263` 时间范围 | 简中、繁中 2.0（60天、14天） | 90.7 / 54.7 dp | `ui-audit/concentration_range_zh-rCN_2.0.png` |
| `MedicationEditor.kt:135` 频率（`maxLines = 1`） | 法语 1.3、2.0（Jours fixes）；英语 2.0（Weekdays） | **截断**：高度一致，但文字被裁，法语出现两个 “Jours” | `ui-audit/editor_frequency_fr-rFR_1.3.png` |
| `DataSection.kt:69` PDF 时间范围（对话框） | 简中、繁中 2.0（180天、365天） | 90.7 / 53.3 dp | `ui-audit/pdf_period_dialog_zh-rCN_2.0.png` |
| `PrivacySection.kt:40` 离开后锁定 | 未渲染（见局限） | 静态：法语 “Immédiatement / 30 secondes / 5 minutes”最长，推测法语 2.0 会换行 | — |
| `DisguiseSection.kt:148` 外壳选择 | 12 种组合均正常 | — | — |
| `HistoryScreen.kt:73`、`WellbeingScreen.kt:83` 7/30/90/全部 | 12 种组合均正常（标签短） | — | — |
| 补查：`WellbeingHub.kt:24` 身心状态三标签（TabRow） | 法语 1.0 起换行，2.0 达 4 行；英语 1.3 起 | 标签高度由 TabRow 统一，未不齐；但大字体下标签很高 | `ui-audit/wellbeing_tabs_fr-rFR_1.0.png` |

### B. 不同样式的按钮并排，各按文字定宽高

| 位置 | 出现条件 | 现象 | 截图 |
|---|---|---|---|
| `StockScreen.kt:105` 开新的一盒 / 修正剩余 / 添加库存（FilledTonal / Outlined / Text 三种样式） | 英语 1.0（添加库存换行，56 / 40 dp）；英语 1.3（36 dp 宽、8 行）；法语 1.0、1.3（11 dp 宽、16 行）；简中、繁中 1.3（2 行）；所有语言 2.0（第三个按钮宽 0、看不见） | 高度不齐、宽度不齐、挤压到不可见；三种样式本身宽度也各不相同 | `ui-audit/stock_buttons_en_1.0.png`、`stock_buttons_en_1.3.png`、`stock_buttons_fr-rFR_1.0.png`、`stock_buttons_zh-rCN_2.0.png` |
| `HistoryScreen.kt:93` 批量补录 / 记录服药（自定义 `contentPadding = 14.dp`） | 英语 2.0（74.7 / 40 dp）；法语 1.3、2.0（112 / 40 dp，3 行） | 高度不齐 | `ui-audit/history_actions_fr-rFR_2.0.png` |
| `VisitsScreen.kt:92` 编辑 / 删除预约（本批新加，Outlined + Text） | 英语 2.0（90.7 / 53.3）；法语 1.3（2 行）、2.0（4 行，被裁） | 高度不齐、截断 | `ui-audit/visit_detail_actions_fr-rFR_2.0.png` |
| `CalendarScreen.kt:352` 待服卡上的按钮 | 12 种组合均正常 | — | — |
| `LongitudinalScreen.kt:59` 筛选 / 添加里程碑 | 12 种组合均正常 | — | — |
| `ConcentrationScreen.kt:67` 竖排 TextButton | 竖排，未发现问题 | — | — |

### C. 等宽并排，但高度可能不齐

| 位置 | 出现条件 | 现象 | 截图 |
|---|---|---|---|
| `Components.kt:46` 日期 / 时间（宽度 1.4 : 1），用于服药、补录、预约、化验对话框 | 英语 1.3、2.0（“11:12 PM”只显示“11:12”）；繁中 1.3、2.0（“下午11:46”只显示“下午”，时间数字丢失）；简中、繁中 2.0（“10月7日周三”只显示“10月7日周”） | **截断**；简中 2.0 两按钮高度差 1.4 dp | `ui-audit/datetime_row_intake_en_1.3.png`、`datetime_row_intake_zh-rTW_1.3.png`、`datetime_row_intake_zh-rCN_2.0.png` |
| `Dialogs.kt:146` 批量补录起止日期 | 英语 2.0、法语 2.0 | 122.7 / 85.3 dp（一侧换成 2 行） | `ui-audit/batch_add_dates_en_2.0.png` |
| `HistoricalContextDialog.kt:38` 起止日期 | **繁中 1.0** 就出现（“2026年10月5日 週一”换行） | 56.7 / 40 dp | `ui-audit/historical_context_dates_zh-rTW_1.0.png` |

### D. 不能横向滚动的选项行

| 位置 | 出现条件 | 现象 |
|---|---|---|
| `Dialogs.kt:80` 部位选择（FilterChip 一行） | 合成数据只有 2 个部位（凝胶左/右），12 种组合均未溢出 | 未复现；部位多（如注射轮换）或法语 2.0 时可能溢出，没有换行或滚动 |

### 补查到的其他问题

| 位置 | 出现条件 | 现象 | 截图 |
|---|---|---|---|
| 日历月视图、周视图的日期格 | 四种语言 2.0 | 两位数日期上半部被裁（格子高度固定） | `ui-audit/calendar_month_days_zh-rCN_2.0.png` |
| 时间选择器上午/下午切换（Material TimePicker） | 繁中 2.0 | “上午/下午”被裁 | `ui-audit/time_picker_ampm_zh-rTW_2.0.png` |
| 伪装外壳（计算器、便签、私人便签） | 12 种组合均正常 | — | — |
| 本批复诊页其余部分（列表、问题清单、资料包对话框、问题对话框） | 除上面的编辑/删除行外均正常 | — | — |

## 统一修复方案（建议）

1. **同一行按钮统一等高，宽度按规则分配**
   - 新增一个共用的按钮行组件。同一行的按钮使用 `Modifier.height(IntrinsicSize.Min)` 加 `fillMaxHeight()` 保持等高，统一最小高度（48 dp 触控尺寸）。
   - 同一行只用一种按钮样式：主操作实心，其余描边，不在同一行混用 TextButton。
   - 去掉 `HistoryScreen.kt:93` 等处的自定义 `contentPadding`，回到全局默认。
2. **放不下时自动换行或改竖排**
   - 按钮行改用 `FlowRow`，放不下时整按钮换到下一行，**按钮内部文字不再被挤成多行**。
   - 库存卡三按钮：主操作一行，次要操作移到下一行，或收进“更多”菜单。
   - 复诊页“删除预约”改到菜单或页面底部，不与“编辑”同排。
3. **分段按钮在大字体或长文字时改为可换行的选择列表**
   - 提供一个自适应的单选组件：先测量所有选项单行时的宽度；全部能单行放下时，用分段按钮；否则改为竖排单选列表（RadioButton 行，整行可点）。
   - 同一组（例如“主题”和“对比度”）共用同一判定结果，两行永远同一种形式、同样高度。
   - 去掉 `MedicationEditor.kt:135` 的 `maxLines = 1`，绝不靠截断来对齐。
4. **日期 / 时间按钮**
   - 字号放大或宽度不足时，日期和时间改为上下两行各占整行。
   - 时间文字禁止截断：上午/下午和 AM/PM 必须完整显示。
5. **日历格子**：格子高度随字体缩放增加，或在大字体下改用更小的固定字号，并为日期数字设置最小高度，确保两位数不被裁。
6. **选项行（部位选择等）**：改 `FlowRow`，或横向滚动并显示可滚动提示。
7. **防回归**：把本审计测试的“同排高度差 ≤ 1 dp、无宽度为 0、控件内文字不被截断”做成断言版，挑主题/对比度、库存卡、日期时间行、复诊页等关键页面，在法语、简中 2.0 下运行，进入常规测试。

请审核以上清单和方案；确认后再修改界面代码。

## §42 时间线精简专项（2026-10-08）

卡片精简与更多菜单/方案详情验证见 [专项验收](timeline-ui-cleanup-verification.md)：四语、320/411dp、字号1/1.3/2、浅深高对比度72配置×3case=216渲染，严重信号0。新增普通菜单回归16项，常规布局共64项。审计修正Compose Popup窗口采集，当前50case；之前48case的最终矩阵是历史证据。复诊导航及已有功能保留，医疗档案仅规划文档，未实施。
