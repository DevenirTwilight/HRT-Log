# HRT Log — UI-06 任务书：语义色与状态体系（2026-10-21）

> 交给 Claude Code 执行。负责人：DevenirTwilight。本任务书即本轮授权来源，开工时原文归档并写入 REQUIREMENTS（下一个 § 编号，当前最新为 §114）。
> 负责人已确认画布上的 UI-06 样图（首页浅色、首页深色、语义色与状态规范）。执行环境无法访问画布，以本任务书文字为准。

## 0. 问题与目标

现状：

1. **状态颜色各页自己写**：同一种状态在不同页面可能用不同颜色。
2. **漏服一律用红色**：日历、服药记录、时间线、今天卡里的漏服都用 `error` / `errorContainer`，“已过时间”也是红色。HRT 用户漏一次就看到满屏红色，会造成不必要的焦虑。
3. **形状命名错误**：`Tokens.kt` 里 `Corners.badge = 10dp` 被当作“胶囊”，但 10dp 只是圆角矩形。
4. **库存页把“没有已开封包装”“即将过期”也标成红色。**

目标：

- 建立**语义色令牌**，所有状态颜色只从令牌取；
- **红色只留给真正的错误**；
- 修正形状命名；
- 把无障碍验收写具体。

**保留青绿主色和陶土色，不换品牌色。**

## 1. 开工前

1. 按 `CLAUDE.md` 阅读 `docs/REQUIREMENTS.md`（重点 §96–§114）、`docs/HANDOFF.md`、`docs/licensing.md`，以及报告 60–69。
2. 从最新默认分支建立 `feature/ui06-semantic-status-<日期>`。
3. 核对 `B0-protected-file-matrix.json`：
   - `NotesTheme.kt`、`CalendarScreen.kt`、`CalendarModel.kt`、`HistoryScreen.kt`、`LongitudinalScreen.kt`、`StockScreen.kt`、`Components.kt` 均为 HISTORY_ONLY，可以修改；
   - `TodayCard.kt`、`Tokens.kt` 不在矩阵中，可以修改；
   - **不改**：`ConcentrationScreen.kt`、`ConcChart.kt`、`LabsScreen.kt`、`LabContextSection.kt`、`Exports.kt`（PK_DISPLAY_REVIEW），以及所有 LIVE_LOCK_RESEARCH_INPUT 文件。这些页面里的红色本轮不动，在报告里列出，留作后续。
   - 若必须触及受保护文件，暂停该子项并报告，其余继续。
4. **不升级 Compose**。当前 BOM 2025.10.00 对应 Material 3 稳定版 1.4.0。1.5.0 仍是 beta，不使用任何 `@ExperimentalMaterial3ExpressiveApi` 组件，全部用稳定 API 加自定义令牌实现。
5. 每完成一节提交、推送并更新 `docs/HANDOFF.md`。

## 2. 实现

### A. 语义令牌（`core/ui`，新文件如 `SemanticColors.kt`）

新增 `SemanticColors` 数据类，通过 `CompositionLocal` 在 `NotesTheme` 中提供，访问方式为 `NotesTheme.semantic.xxx` 或同等写法。每个令牌包含 `container`、`onContainer`、需要时加 `accent`（描边或符号色）。

浅色与深色的初值如下。负责人已确认样图中的这些值，对比度已实测：

| 令牌 | 用途 | 浅色 container / on / accent | 深色 container / on / accent |
|---|---|---|---|
| `action` | 现在要做的事：现在服用、保存、主按钮 | 沿用 `primary` / `onPrimary` | 沿用 `primary` / `onPrimary` |
| `attention` | 需要留意但不是错：已过时间、库存不足、即将过期、没有已开封包装 | #FFDBCE / #5C2410 / #8E4D35 | #71361F / #FFDBCE / #FFB599 |
| `success` | 已服、按时、完成 | #B8EDE6 / #00201D / — | #00504A / #9EF2E8 / — |
| `missed` | 已确认没服：中性暖灰 | #EDE4DF / #4A3F3A / #7A6A63 | #3B322E / #E9DED9 / #A39089 |
| `unconfirmed` | 过去的时间点没有记录：透明底加虚线 | 透明 / #3F4947 / #6F7977 | 透明 / #BEC9C6 / #889391 |
| `skipped` | 用户主动跳过：中性 | #DAE5E2 / #3F4947 / — | #3F4947 / #DDE4E2 / — |
| `danger` | 只用于真正的错误和不可撤销的删除 | 沿用 `errorContainer` / `onErrorContainer` / `error` | 同左 |

要求：

- **对比度档位**：中、高对比度模式下，按现有 `withContrast` 的思路派生更深或更浅的值（高对比度时边框和符号色用 `onSurface`）。三档 × 明暗全部写进测试。
- **动态取色**：开启时，`action` 和 `success` 跟随动态 `primary` 系。`attention`、`missed`、`unconfirmed`、`skipped` 保持固定值，保证语义不随壁纸变化。`danger` 跟随动态 `error`。
- **集中定义**：在 `core/ui` 提供一个函数，把服药状态（`SlotState` / `DoseStatus` / `DayKind` / 记录 status 字符串）映射到令牌加符号加文字 key，所有页面都调用这一个函数，不再各写一份。

### B. 状态映射（全应用统一）

| 状态 | 令牌 | 符号 | 文字 |
|---|---|---|---|
| ON_TIME | success | ✓ | 已服 / 按时 |
| LATE | success | ✓ | 已服，另加文字“晚了”，不变色 |
| PARTIAL（日历的一天） | missed | ½ | 部分完成 |
| MISSED（已确认） | missed，加 accent 描边 | ✕ | 漏服 |
| UNCONFIRMED（含 MISSED + unconfirmed 记录） | unconfirmed，加虚线 | ? | 未确认 |
| SKIPPED | skipped | — | 已跳过 / 你跳过了 |
| OVERDUE（今天已过时间，未记录） | attention | 文字 | 已过时间 · 可以补记 |
| PENDING / SOON | 不着色，SOON 保留现有 primary 强调 | — | 现有文案 |

应用到以下位置，**只改颜色、符号和形状，不改数据和行为**：

- `CalendarScreen.kt`：月、周、年视图的日格，图例，`DoseCard`，`RecordLine`，周视图状态点。日格中漏服用 missed 底加 accent 描边，未确认用虚线描边。
- `TodayCard.kt`：OVERDUE 标签改用 attention；记录行的状态图标按上表。
- `HistoryScreen.kt`：状态标签。
- `LongitudinalScreen.kt`：`EventKind.MISSED` 改用 missed。
- `StockScreen.kt`：“没有已开封包装”“即将过期”和预测警告从 `error` 改为 attention。真正的错误提示（如保存失败）保持 danger。
- 其他页面中凡是用 `error` 表示“需要留意但不是错误”的，按同一原则改为 attention，并在报告中逐处列出。表单校验错误、保存失败、数据损坏、备份失败、删除确认保持 danger。

### C. 首页“还没有记录”确认卡（`CalendarScreen.kt` / `TodayCard.kt`）

在今天卡下方显示一张卡，内容为**最近 48 小时内**处于“未确认”状态的服药（超过 48 小时的仍在服药记录页处理，首页不显示）。

- **样式**：虚线描边（unconfirmed）、? 图标、标题“昨天 21:00 还没有记录”（今天的就写“今天 08:00”）、副标题“药物 · 剂量。记得的话补上就好。”
- **两个按钮**：
  - “我服过了”：打开**现有**补记对话框，即服药记录页“补记”使用的 `IntakeDialog`（`history_backfill` 路径）；
  - “没有服”：调用**现有**的 `confirmMissed`。
  - 不新增任何写入路径。
- 多条时最多显示 2 条，其余用“还有 n 条 → 服药记录”跳转到服药记录页，并选中这些记录（复用现有 `selectedRecordIds` 机制，如果适用）。
- 没有未确认记录时整张卡不显示。
- **语气**：不使用“警告”“失败”“错过”等词。

### D. 形状

- `Tokens.kt`：
  - 把 `Corners.badge` 改名为 `Corners.tag`（10dp，用于筛选标签和小标记），**不再称为胶囊**；
  - 新增 `Corners.pill = RoundedCornerShape(percent = 50)`，用于胶囊按钮和状态标签。
  - 更新所有引用。
- `StatusPill`（`Components.kt`）改用 `Corners.pill`。
- 主操作按钮确认使用完全圆角（Material 3 默认的 `ButtonDefaults.shape` 已是完全圆角，凡是被覆盖成其他圆角的，改回来）。
- 日历日格圆角 14、大卡 24、内嵌列表 16 保持不变。

## 3. 无障碍验收（每一项都必须有测试或明确标为 NOT_RUN）

1. **对比度**：所有语义令牌的文字与底色 ≥ 4.5:1；符号、描边、图标与相邻底色 ≥ 3:1。覆盖标准、中、高三档 × 浅色、深色，以及开启动态取色的一组代表性壁纸色。用现有对比度断言工具做参数化测试，测试名中带令牌名。
2. **不只靠颜色**：每个状态都有符号和文字；灰度渲染下仍可分辨（截图测试加一组灰度滤镜对比）。
3. **字体放大**：2× 字号、320dp 下，日历格、确认卡、今天卡不截断、不重叠，能滚动到所有按钮。
4. **TalkBack**：日格朗读完整状态，例如“10月3日，漏服”“10月18日，未确认”。确认卡的朗读顺序为：标题 → 药物 → “我服过了” → “没有服”。四语都要验证。
5. **触控目标**：所有可点元素 ≥ 48×48dp，包括日历格、确认卡按钮和 ⋮。
6. **深色模式**：1–5 在深色下同样通过。

真机 TalkBack 和最大系统字号标为 NOT_RUN，写入真机验收清单，由负责人回填。

## 4. 测试

先写失败测试，再实现。

1. **映射函数**：每个状态返回规定的令牌、符号和文字 key，并对所有枚举值做穷举测试。
2. **全局扫描**：日历、今天卡、服药记录、时间线中，漏服、已过时间、未确认、跳过**都不使用** `colorScheme.error` 或 `errorContainer`。用源码守卫或语义树颜色断言实现，二选一，并说明理由。
3. **库存页**：上述三类提示使用 attention；真正的错误仍是 danger。
4. **确认卡**：
   - 48 小时窗口的边界；
   - 最多 2 条，加跳转；
   - “我服过了”打开的对话框与服药记录页补记完全相同；
   - “没有服”与服药记录页的确认漏服结果逐字段一致；
   - 无未确认记录时不渲染。
5. **形状**：`StatusPill` 和主按钮是完全圆角；`Corners.badge` 已无引用。
6. 第 3 节的 1–6 项。

必须通过（不得放宽）：
- `PeriodStabilityTest`（交付门槛）；
- UI-01 至 UI-05、LAB-01 至 LAB-03 的全部测试；
- `ButtonLayoutRegressionTest`、`CalendarSemanticsTest`、`CalendarCoreFlowTest`、`TodayCardTest`、`TranslationsTest` 和现有对比度断言；
- 完整 `:app:testFullDebugUnitTest`、lint、设备测试和全部 CI job。

**断言迁移**：如果现有测试断言了旧颜色（例如“漏服为 errorContainer”），改为断言新令牌，并在报告中逐条说明：原断言保护的是“漏服可以和已服区分”，新断言仍然保护这一点。

截图：首页（含确认卡）、日历月视图（包含全部 5 种状态）、服药记录、时间线、库存。覆盖四语 × 320/411dp × 字号 1/2 × 浅色/深色，以及高对比度一组，前后对比，存入 `docs/ui-audit/<日期>-ui06/`。另外导出一组 411dp、字号 1、zh-CN、浅色和深色的**原尺寸高清 PNG**，药名使用中性占位（药物 A/B/C）。

## 5. 文案（四语同步，`TranslationsTest` 通过）

| 用途 | zh-CN | en |
|---|---|---|
| 已过时间 | 已过时间 · 可以补记 | Past time · you can still log it |
| 确认卡标题 | 昨天 %1$s 还没有记录 / 今天 %1$s 还没有记录 | No entry yet for %1$s yesterday / today |
| 确认卡说明 | 记得的话补上就好。 | If you remember, just add it. |
| 我服过了 | 我服过了 | I took it |
| 没有服 | 没有服 | I didn't take it |
| 更多 | 还有 %1$d 条 | %1$d more |
| 部分完成 | 部分完成 | Partly done |
| 你跳过了 | 你跳过了 | You skipped |

- zh-TW 和 fr 按现有术语翻译，在报告中列出，并请负责人在真机上核对语气。
- 现有的“部分漏服”文案改为“部分完成”，四语同步，并在报告中列出所有受影响的位置。

## 6. 范围与禁止

- 不改 ViewModel、仓库、Room schema、备份、加密、应用锁、伪装、PK、浓度页、化验页、导出 PDF、提醒逻辑。
- 不改任何状态的判定规则，例如什么时候算漏服或未确认，只改它们的样子。
- **“提醒未送达”不在日历上显示**。只有在应用真实记录到发送失败时，才能在设置的提醒检查里出现。本轮不新增这类状态。
- 不升级 Compose，不使用实验性 API，不新增依赖、权限、网络或埋点。
- 不改 versionCode 和签名，不交付 APK。

## 7. 交付

- 写报告（下一个编号，当前最新为 69）`…-ui06-semantic-status.md`，包括：
  - 令牌表（含三档 × 明暗的实测对比度）；
  - 状态映射表；
  - 所有从 `error` 改为 attention 的位置清单；
  - 受保护页面中未改动的红色清单；
  - 四语对照、截图、断言迁移清单、NOT_RUN 项。
- PR 按 §106 自动合并，合并后复验默认分支 CI。
- 在真机验收清单追加 UI-06 项：确认卡、深色模式、TalkBack 朗读状态，由负责人回填。
- 更新 `docs/HANDOFF.md`、`docs/STATUS.md`。
