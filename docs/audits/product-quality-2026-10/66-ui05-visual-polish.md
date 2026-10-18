# 66 — UI-05：视觉精修，吸收样图设计、保留现有功能（2026-10-18）

- 任务书：`sources/ui05/ui05-prompt.md`（SHA-256 `7556d42b…d1230`）；REQUIREMENTS §109。
- 分支：`feature/ui05-visual-polish-20261018`，基于默认分支 1abfeea，后合入 #74（医疗中心/药品资料中心）并解决冲突：医疗档案保留中心入口，列表中的“记录就诊”按钮仍按本轮移到顶栏 +。A–G 每节一个提交。
- 性质：只改视觉和布局。所有按钮仍调用原回调；没有新增写入路径；剂量、库存预测、时期投影、浓度计算都没有改。

## 1. 改动文件与保护矩阵类别

| 文件 | 类别 | 改动 |
|---|---|---|
| `ui/TodayCard.kt` | 不在矩阵 | A：今天卡；B：首页库存横条；共用 `lowStockMedications`、`LocalTodayClock`（只用于显示）、`tnum()` |
| `ui/CalendarScreen.kt` | HISTORY_ONLY | B：库存横条移到今天卡下方；月历卡加测试标签 |
| `ui/StockScreen.kt` | HISTORY_ONLY | C：途径·频率行、“约 n 天”、长句移到进度条下 |
| `ui/LongitudinalScreen.kt` | HISTORY_ONLY | D：只改展示层；顶栏 + 菜单宿主、胶囊、内嵌列表、类型配色 |
| `ui/AppShell.kt` | HISTORY_ONLY | D/F：顶栏 + 动作；E：抽屉库存角标 |
| `ui/MedicalRecordsScreen.kt` | 不在矩阵 | F：顶栏添加、下次就诊卡、年度内嵌列表 |
| `ui/Components.kt` | HISTORY_ONLY | `SectionCard` 增加可选 `shape`（默认不变）；`sectionTitleStyle` |
| `res/values*/strings.xml` | — | 10 条新文案 × 四语 |

没有触及 PK_DISPLAY_REVIEW 或更严格类别的文件。没有改动 `LongitudinalProjection`、`PeriodTimeline`、`TimelineEdits`、ViewModel、仓库、Room、备份、加密、锁、伪装、PK、浓度页、PDF、提醒、设置页、药物页和参考资料页。

## 2. 每节：采用了样图的什么，保留了应用的什么

| 节 | 采用样图 | 保留应用 |
|---|---|---|
| A 今天卡 | 进度胶囊（onPrimary 16% 底）；下一次旁“还有 n 小时 / n 分钟”（只用 `slot.at` 与当前时间，每分钟刷新，逾期不显示）；“现在服用”加勾号、52dp；⋮ 改为 52dp 圆形描边按钮；“下一次”与下方分区之间的分隔线；记录行前 28dp 状态图标（已服 ✓、漏服 ✕、跳过 –、未确认 ?） | 同一时间 / 稍后 / 今天的记录 / 今天的预约四个分区、超过 3 条折叠、简洁模式隐藏药名、状态文字、全部回调 |
| B 首页库存 | 每种药一条紧凑横条（tertiaryContainer、圆角 16、图标 +“[药名]快用完了”+“约 n 天后用完”+ 添加库存按钮），整条点进库存页；移到今天卡正下方；最多 3 条 +“还有 n 种” | 判定仍为 `StockForecast.isLow`；“添加库存”仍打开同一个 `AddStockDialog`；首页不显示总说明句 |
| C 库存卡 | 药名下“途径 · 频率”（`choiceLabel` / `scheduleText`）；余量旁“约 n 天”（forecast 已算出的天数）；长句改为进度条下的次要小字 | 单一主按钮与 ⋮ 菜单、低库存变色与胶囊、包装折叠与管理入口、“开新的一盒”文案 |
| D 时间线 | 添加里程碑 / 新建时期移入顶栏 +；时期类型胶囊；“[起] 至今 · 第 n 天”或“[起] – [止]”；每种药一行加圆点；事件画成所属分组卡下方的白色内嵌列表（36dp 彩色图标、类型 · 名称 / 日期两行、右箭头）；即将发生的事件为虚线卡 | 筛选标签（含“其他”）、“第 n 天”、全部 `EventKind` 的图标和文字、⋮ 菜单与全部编辑对话框；事件归属完全来自原投影的块顺序 |
| E 抽屉 | 库存入口的数字角标（tertiaryContainer 胶囊，语义“库存：n 种即将用完”，0 不显示） | 分组、名称、图标、应用名不变 |
| F 医疗档案 | “记录已发生的就诊”移到顶栏 +；下次就诊卡：日期小方块（月/日/星期）、“下次就诊 · n 天后”、类型、时间·医生·地点、内嵌两行（想问的问题 · n 条 / 就诊资料包）；已确认就诊改为年度内嵌列表 | “去过了吗？”卡（只统一圆角）、相关资料、回收站、时区显示规则、“日期接近不是关联”（§87）；资料包状态本页读不到，不显示、不新增查询 |
| G 全局 | 分区标题 15sp 粗体；卡片 24 / 内嵌列表 16 / 胶囊 10（UI-02A `Corners`）；时间与余量用等宽数字 | 只在本轮改过的文件中改 |

类型配色（D）：化验 tertiaryContainer、就诊 secondaryContainer、里程碑 primaryContainer、阶段回顾 inverseSurface、其他 surfaceVariant；漏服仍用 errorContainer（是状态信息，文字“漏服”同时存在）。

## 3. 测试

先写失败测试，再实现。红测数为实现前在同一分支上运行的结果。

| 新测试 | 结果 | 修复前 |
|---|---|---|
| `Ui05TodayCardTest` | 9/9 | 7 项中 6 项失败（逾期不显示倒计时原本即成立） |
| `Ui05HomeStockTest` | 4/4 | 2/4 失败 |
| `Ui05StockCardTest` | 4/4 | 3/4 失败 |
| `Ui05TimelineTest` | 8/8 | 6 项中 5 项失败 |
| `Ui05DrawerBadgeTest` | 3/3 | 1/3 失败 |
| `Ui05MedicalHomeTest` | 3/3 | 3/3 失败 |
| `Ui05ContrastTest` | 1/1 | 新增检查 |

时间线归属：`everyEventIsDrawnInTheGroupTheProjectionGivesIt` 对同一组合成数据逐个事件比对，渲染所在分组与 `PeriodTimelineProjection.build(...)` 给出的时期 / 未关联 / 即将发生完全一致。

对比度：`Ui05ContrastTest` 与 `Ui05TimelineTest` 在浅色、深色 × 标准、中、高对比度下检查全部新增胶囊与图标底色，文字 ≥ 4.5:1、图标 ≥ 3:1，全部通过；五种类型颜色在每个主题内互不相同。

本地完整回归（本分支 041c1ea）：`core:reminder` 20、`core:data` 247、`app:testFullDebugUnitTest` 1458 项，0 失败（237 skip：205 为既有环境门控截图/审计类，32 为新截图测试的参数组合，默认关闭）；`PeriodStabilityTest` 通过；lint、`assembleFullRelease`、设备测试编译通过；`check_release_manifest.py`、`check_attachment_release_isolation.py` PASS。CI 结果以 PR 为准。

## 4. 断言迁移（未删除任何断言）

| 测试 | 原断言 | 现在 | 含义如何保留 |
|---|---|---|---|
| `HomeStockCardTest.lowOrOutListsOnlyThoseWithOneButton` | 行内有文字恰为药名 | 药名作为“[药名]快用完了”的子串 | 这一行属于该药 |
| 同上 | 2 个 `home-stock-view`，点第一个进库存页 | 标签挂在整条横条上，原断言原样通过 | “查看库存”由整条横条承担 |
| `LongitudinalUiTest` ×2 | 点击列表中的“添加里程碑” | 点击顶栏 + → “添加里程碑” | 其后对话框断言（必填标题、日期、失败保留草稿、状态恢复）全部不变 |
| `TimelineEditUiTest.cardMenuDeletes…` | 简洁模式下 `timeline-new-period` 存在 | + 存在，且时间线登记的新建时期回调存在并可用 | 菜单项调用该回调由 `Ui05TimelineTest` 证明 |
| `MedicalHomeTest.nextVisitIsOnlyTheNearestFutureOne` | 点击 `medical-next-open` | 点击 `medical-next-questions` | 打开的是最近一次就诊 |
| `MedicalRecordsUiContract.archiveSeparates…` | `performScrollToKey("visit:701")` | 按行标签滚动 | 已确认就诊成为年度内嵌列表的行；其后可见、Button 角色、可点击、打开该就诊不变 |

焦点滚动：时间线移除了按钮项，`focusKey` 滚动索引由 +3 改为 +2。

测试环境发现：Robolectric 在 `w411dp-h891dp` 限定符下打开时间线上的对话框会一直不空闲（原主线代码同样），去掉限定符即正常；这是测试环境问题，不是应用缺陷，`Ui05TimelineTest` 因此不加尺寸限定符。

## 5. 四语文案

| key | zh-CN | zh-TW | en | fr |
|---|---|---|---|---|
| `today_in_hours` | 还有 %1$d 小时 | 還有 %1$d 小時 | In %1$d h | Dans %1$d h |
| `today_in_minutes` | 还有 %1$d 分钟 | 還有 %1$d 分鐘 | In %1$d min | Dans %1$d min |
| `home_stock_low` | %1$s快用完了 | %1$s快用完了 | %1$s is running low | %1$s : bientôt épuisé |
| `home_stock_more` | 还有 %1$d 种 | 還有 %1$d 種 | %1$d more | %1$d de plus |
| `stock_about_days` | 约 %1$d 天 | 約 %1$d 天 | about %1$d days | environ %1$d jours |
| `period_since` | %1$s 至今 | %1$s 至今 | Since %1$s | Depuis le %1$s |
| `drawer_stock_badge` | 库存：%1$d 种即将用完 | 庫存：%1$d 種即將用完 | Stock: %1$d running low | Stock : %1$d bientôt épuisé(s) |
| `medical_in_days` | %1$d 天后 | %1$d 天後 | in %1$d days | dans %1$d jours |
| `medical_next_questions` | 想问的问题 · %1$d 条 | 想問的問題 · %1$d 條 | Questions to ask · %1$d | Questions à poser · %1$d |
| `medical_next_pack` | 就诊资料包 | 複診資料包 | Visit pack | Dossier de consultation |

另：原有 `medical_next_prepare` 的简体中文条目是英文（未翻译），该按钮已被两行入口取代，不再显示。

## 6. 截图

`docs/ui-audit/2026-10-18-ui05/`，由 `Ui05ScreenshotTest`（`UI05_SHOTS=<label>`）生成，合成数据，药名为中性占位：

- 页面：首页、库存、时间线（顶部与滚动后）、抽屉、医疗档案；四语 × 320/411dp × 字号 1/2 × 明暗。
- `before/`（基线 1abfeea，同一测试去掉标记为“UI-05:”的新参数）与 `after/`（本分支）各 192 张，缩到 360px 宽并量化；192/192 不同（五个页面均有改动，抽屉多了角标）。
- `compare/`：左旧右新拼图 12 张（zh-CN 411dp 浅色、en 320dp 字号 2 暗色）。
- `hires/`：411dp、字号 1、浅色、zh-CN 的原尺寸 PNG 6 张（1233 px 宽，未缩小、未量化），供宣传图使用。这些是设计实现的截图，不含真实数据。

## 7. NOT_RUN

真机清单第 10 节（倒计时刷新、首页横条、时间线内嵌列表、TalkBack 朗读）待负责人回填；国产 ROM；真机 TalkBack；深色模式下的实际观感。
