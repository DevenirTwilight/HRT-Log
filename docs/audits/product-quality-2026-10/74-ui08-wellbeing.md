# 74 — UI-08：身心状态页重做（2026-10-22）

- **任务书**：`sources/ui08/ui08-prompt.md`（SHA-256 `c026c7b6…52749a9f5`），REQUIREMENTS §138。
- **前置**：UI-07b（#96）和 LAB-04（#97）都已合并，本分支基于默认分支 aaab9669。
- **分支**：`feature/ui08-wellbeing-20261022`。
- **性质**：只改身心状态的界面。
  - 数据结构、取值范围（1–5，未记录为 null）、`onScore` 和 `onNote` 都不变。
  - 症状目录和来源、阶段回顾字段、PDF 摘要、服药后提示、首页都没改。
  - 没有改 ConcChart、PK、Room、备份；没有新增依赖或权限；没有使用实验性 API 或外部图片。

## 1. 改动文件与保护矩阵类别

| 文件 | 类别 | 改动 |
|---|---|---|
| `ui/WellbeingDaily.kt`（新） | 不在矩阵 | 包括：胶囊标签 `WellbeingTabs`、日期行 `WellbeingDateRow`、大卡片 `WellbeingDayCard`、太阳 `drawLineSun`、条目行 `CheckinItemRow`、按钮组 `FiveLevelButtons`、`FiveLevelInput`（改用按钮组）、`trendShape` 和 `drawWellbeingTrend`、`WellbeingTrendLine` |
| `ui/WellbeingScreen.kt` | HISTORY_ONLY | “每日”标签改用新组件；“最近的记录”改用 `WellbeingTrendLine`，不再调用 ConcChart；管理对话框行高调整；去掉底部“管理条目”按钮 |
| `ui/WellbeingHub.kt` | HISTORY_ONLY | 共享日期；症状标签加日期行；拆出不依赖 ViewModel 的 `WellbeingHubContent`；去掉底部“管理条目”按钮 |
| `ui/AppShell.kt` | HISTORY_ONLY | 顶栏“管理条目”图标按钮，调用原来的 `wellbeingDialogs.manageItems` |
| `res/values*/strings.xml`（4 种语言） | HISTORY_ONLY | 新增 15 条文案；已有文案没有改，见第 6 节 |
| `ConcChart.kt` | PK_DISPLAY_REVIEW | **未改**，测试固定了它的 SHA |
| `export/WellbeingSummary.kt`、`export/Exports.kt` | — | **未改**，测试固定了它们的 SHA |

## 2. 组件说明

- **`FiveLevelButtons`**：
  - 5 个等宽胶囊，每个至少 48dp，放在 `selectableGroup` 里，每个按钮是 `Role.RadioButton`；
  - 选中：action 令牌（主色）实心、白字、放大 1.06 倍；未选中：浅底加 `outline` 细描边；
  - 点已选中的按钮不回调；清除只能用条目上的“清除”；
  - 朗读用 `wb_level_spoken`，例如“今天的心情，4，共 5 级”，“已选中”由 TalkBack 按选中状态追加。
- **`WellbeingDayCard` 和太阳**：
  - 底色 success.container，圆角 32dp；
  - 太阳是自绘的圆加 8 条圆头射线，150dp，放在右上角并超出卡片被裁掉；颜色由 `sunColor()` 取比卡片略浅（深色主题下略深）的色，不朗读；
  - 角度 = 已记项数 × 22°，用弹簧动画过渡；`LocalReduceMotion` 开启时直接到位。
- **`WellbeingTrendLine`**：
  - `trendShape()` 只在相邻两天都有值时连线，缺记录的日子断开；
  - 每个数值画一个与线同色的小点（半径为线宽的 0.9 倍）；单独一天的点稍大（1.2 倍）。负责人 2026-10-22 看过截图后要求点要小、与线同色，不要大圆点或色差大的点；`Ui08TrendDrawingTest` 检查点的大小和颜色；
  - 纵轴只在左侧标“5”和“1”；用主色绘制；
  - 整行朗读为一句，例如“今天的心情，最近 30 天记录了 12 天”。
- **共享日期**：`WellbeingHubContent` 持有一个 `rememberSaveable` 日期，“每日”和“症状”都用它。日期文字可点，打开现有的日期选择器。

## 3. “不判断”原则的逐项落实

| 原则 | 落实 |
|---|---|
| 不计分 | 计数只是“已记录 x / y 项”，不算总分或平均分 |
| 不判断 | 选中颜色与分值无关：选中 1 和选中 5 的颜色相等，都是 action，不是 error 或其他状态色（`radioGroupSemanticsAndEveryChosenLevelLooksTheSame`）；趋势图没有均值线、参考线或范围（`Ui08TrendDrawingTest` 用绘制记录检查） |
| 不给建议 | 新文案只描述记录本身（`noStreaksBadgesOrJudgementWords` 检查禁用词：坚持、打卡、连续、得分、正常、异常、偏低、偏高） |
| 不鼓励打卡 | 不记连续天数，不给徽章；太阳只跟当天的项数有关 |
| 没记录就是没记录 | 不点按钮就不写入（`tapsWriteTheLevelClearWritesNullUntouchedWritesNothing`）；没有记录的日子在图上留空 |

## 4. 断言迁移

| 原断言 | 原含义 | 迁移后 |
|---|---|---|
| `WellbeingUiTest.untouchedSliderIsUnrecordedAndCanBeRecordedThenCleared`（Slider `SetProgress(4)`，contentDescription 为条目名） | 未触碰不记录，能记 1–5，能清除 | `untouchedLevelsAreUnrecordedAndCanBeRecordedThenCleared`：点“…，4，共 5 级”按钮，写入序列仍为 `[4, null]` |
| `WellbeingAndroidTest.optionalDailySliderDoesNotWriteUntilTheUserChooses`（设备，`SetProgress(5)`） | 同上 | `optionalDailyLevelsDoNotWriteUntilTheUserChooses`：点 5 号按钮，写入序列仍为 `[5, null]` |
| `WellbeingUiTest.newDailyKeysHaveLabelsAndHistoricalItemsHaveNoAverage` | 名称存在，没有“Average” | 不变，只去掉了已删除的 `onManage` 参数 |
| `ButtonAuditTest` 的身心状态三个用例 | 按钮审计 | 只去掉了 `onManage` 参数；标签仍按文字点击 |
| `ScreenshotTest.wellbeingScreen` | 截图 | 只去掉了 `onManage` 参数 |

所有断言都保留，没有删除。

## 5. 新增测试

- `Ui08LevelButtonsTest`：
  - 点 1–5 依次写入，点已选中的不写，“清除”写入 null，未点不写；
  - 与旧实现相同的调用序列 `[4, null]`；
  - radio 语义；选中颜色中性且相同。
- `Ui08DayCardTest`：
  - 0 / 部分 / 全部三种标题；计数只算启用的条目；
  - “后一天”在今天时禁用，前后切换正常；
  - 太阳角度随项数变化，有弹簧过渡；减少动画时没有过渡；
  - 禁用词检查。
- `Ui08TrendShapeTest`、`Ui08TrendDrawingTest`：断开规则、单点画圆点；绘制记录中只有相邻日之间的短线段和圆点，没有贯穿全图的线。
- `Ui08RecentTest`：范围切换（7 / 30 / 90 / 全部），朗读文本随之变化；没有数据的条目不画线；说明文字存在。
- `Ui08SharedDateTest`：“每日”切到昨天后切到“症状”，日期仍是昨天；经过 `StateRestorationTester` 重建后保留；标签是 `Role.Tab`、至少 44dp。
- `Ui08LayoutTest`：
  - 四种语言 × 320/411dp × 字号 1/2 × 明暗，共 32 组；
  - 标题、副标题、计数不被截断，并在卡片内；
  - 5 个按钮和前后一天按钮都至少 48dp，radio 语义正确。
- `Ui08ContrastTest`：三档对比度 × 明暗，共 42 组。检查大卡片文字（包括压在太阳上的部分）、计数胶囊、选中和未选中的按钮、按钮描边、趋势线；最低为浅色标准档的按钮描边 4.49:1（要求 3:1）。
- `Ui08PdfSummaryUnchangedTest`：
  - `WellbeingSummary.kt`、`Exports.kt`、`ConcChart.kt` 的 SHA-256 等于基线；
  - 四种语言的 strings.xml 去掉 UI-08 新增段后，与基线逐字节相同；
  - 由此 PDF 摘要的代码和用到的文案都与改动前一致，输出因此不变。说明：没有直接比较 PDF 字节，PDF 写入器在 Robolectric 中不可用于逐字节对照；设备测试 `WellbeingAndroidTest` 仍检查 PDF 分页。

## 6. 文案（四种语言，`TranslationsTest`）

| 键 | zh-CN | en | zh-TW | fr |
|---|---|---|---|---|
| wb_card_title_none_today | 今天感觉怎么样？ | How are you feeling today? | 今天感覺怎麼樣？ | Comment vous sentez-vous aujourd’hui ? |
| wb_card_title_none_other | 这天感觉怎么样？ | How did you feel that day? | 這天感覺怎麼樣？ | Comment vous sentiez-vous ce jour-là ? |
| wb_card_title_some | 已经记了一些 | You've noted some | 已經記了一些 | Vous en avez renseigné quelques-uns |
| wb_card_title_all | 都记好了 | All noted | 都記好了 | Tout est renseigné |
| wb_card_subtitle | 记几项就好，不需要全填。不打分，也不评判。 | Note a few, no need to fill everything. No scores, no judgement. | 記幾項就好，不需要全填。不打分，也不評判。 | Quelques éléments suffisent, inutile de tout remplir. Pas de score, pas de jugement. |
| wb_card_count | 已记录 %1$d / %2$d 项 | %1$d of %2$d noted | 已記錄 %1$d / %2$d 項 | %1$d sur %2$d renseignés |
| wb_item_unrecorded | 还没记录 | Not noted yet | 還沒記錄 | Pas encore renseigné |
| wb_item_value | 记为 %1$d / 5 | Noted %1$d of 5 | 記為 %1$d / 5 | Renseigné : %1$d sur 5 |
| wb_level_spoken | %1$s，%2$d，共 5 级 | %1$s, %2$d of 5 | %1$s，%2$d，共 5 級 | %1$s, %2$d sur 5 |
| wb_note_title | 今天的备注 | Today's note | 今天的備註 | Note du jour |
| wb_note_placeholder | 想写什么都可以，只有你自己能看到。 | Write anything you like. Only you can see it. | 想寫什麼都可以，只有你自己看得到。 | Écrivez ce que vous voulez. Vous seul·e pouvez la voir. |
| wb_recent_title | 最近的记录 | Recent entries | 最近的紀錄 | Saisies récentes |
| wb_recent_note | 只显示你自己记下的数值，没有记录的日子留空。 | Only values you noted are shown; days without an entry are left blank. | 只顯示你自己記下的數值，沒有記錄的日子留空。 | Seules les valeurs que vous avez saisies sont affichées ; les jours sans saisie restent vides. |
| wb_trend_spoken | %1$s，最近 %2$d 天记录了 %3$d 天 | %1$s, noted on %3$d of the last %2$d days | %1$s，最近 %2$d 天記錄了 %3$d 天 | %1$s, renseigné %3$d jours sur les %2$d derniers |
| wb_trend_spoken_all | %1$s，记录了 %2$d 天 | %1$s, noted on %2$d days | %1$s，記錄了 %2$d 天 | %1$s, renseigné %2$d jours |

zh-TW 和 fr 沿用现有术语：“記錄”“renseigner”与 `wb_recorded_days`、`wb_unrecorded` 一致。**请负责人核对 zh-TW 和 fr 的语气。**

- fr 的占位文字用了包容写法“seul·e”。
- fr 副标题中的“score”与英文原文“No scores”对应；禁用词表是中文词，这里没有冲突。

## 7. 偏离与说明

- **“管理条目”按钮**：在应用顶栏（`AppShell`）。身心状态页本身没有自己的顶栏。
- **按钮下方的两端文字**：沿用 `wb_low_*` / `wb_high_*`；旧条目和自定义条目显示“1”“5”。
- **症状和阶段回顾标签**：只换了胶囊标签和共享日期行；内容组件已在用 `SectionCard` 令牌，没有改。阶段回顾里满意度原本是滑杆，现在用同一个按钮组，数据不变。
- **布局测试的图形模式**：`Ui08LayoutTest` 用 Robolectric NATIVE 图形模式。旧图形模式不测量文字，量出的宽度没有意义。

## 8. 截图

`docs/ui-audit/2026-10-22-ui08/`，全部为合成数据：

- `before/`（UI-07b + LAB-04 基线 aaab9669）和 `after/`：
  - 页面：每日 0 项、部分、全部（顶部，以及滑到“最近的记录”）、症状标签顶部、管理条目对话框；
  - 组合：四种语言 × 320/411dp × 字号 1/2 × 浅色/深色。
- `hc/`：高对比度，zh-CN 和 en，浅色/深色，前后各一组。
- `hires/`：411dp、zh-CN、浅色/深色的原尺寸高清 PNG。
- `compare/`：前后并排。
- `grayscale/`：zh-CN 411dp 的灰度前后图。
- “管理条目”对话框：对话框是独立窗口，所以用 `manageDialog()` 直接画它自己的窗口。只有 320dp 的 16 组；411dp 下 Robolectric 打开对话框后一直等不到空闲（改前改后都一样，与 UI-08 无关），所以 411dp 的对话框截图记为 NOT_RUN。

before 是在基线上用同一份截图测试渲染的；因为那时还没有 `WellbeingHubContent`，基线版本用旧的标签行加旧的 `WellbeingScreen` 拼出页面。

## 9. 验证

- 本地 `:core:ui:testDebugUnitTest` 加完整 `:app:testFullDebugUnitTest`：共 2941 项，0 失败。另有 492 项跳过，是默认关闭的截图测试等，按设计不跑。
- 时期稳定性测试、UI-01 至 UI-07b、LAB-01 至 LAB-04、`ButtonLayoutRegressionTest`、`TranslationsTest` 和各项对比度断言都在上面的测试中，全部通过。
- `lintFullDebug` 通过。
- CI 全部 job 的结果见 PR。

## 10. NOT_RUN

- 真机：按钮手感、太阳转动、TalkBack 朗读、大字号下的大卡片。已加入真机清单第 17 节，由负责人回填。
- 负责人核对 zh-TW 和 fr 的语气。
- “管理条目”对话框 411dp 的截图（Robolectric 限制）。
