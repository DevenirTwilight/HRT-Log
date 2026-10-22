# 72 — UI-07b：状态图标改为纯线条（2026-10-22）

- **任务书**：`sources/ui07b/ui07b-prompt.md`（SHA-256 `233ef8b5…e418b8d`），REQUIREMENTS §136。
- **背景**：负责人装上 build34 后，觉得实心圆图标在真机上效果不好，改用画布上对比过的“C · 纯线条”。
- **分支**：`feature/ui07b-line-icons-20261022`，基于默认分支 de0c4e55（build34 源码）。
- **性质**：只改外观。下列内容都没有改：
  - 状态判定、UI-06 令牌值、日历格底色；
  - 动效、分组列表、字体；
  - 数据和逻辑；
  - 依赖和权限。

## 1. 改动文件

| 文件 | 改动 |
|---|---|
| `core/ui/StatusIcon.kt` | 绘制改为纯线条，`StatusIconSpec` 精简为 `(glyph, color)`。新增两个可选参数：`onOwnContainer` 和 `tint`。新增测试用语义键 `StatusIconColorKey`，它不会被朗读。种类、尺寸、`StatusIconKindKey`、测试 tag 都不变。 |
| `ui/TodayCard.kt` | “已记录”的勾：`ring = onPrimary` 改为 `tint = onPrimary`。另有一处注释改字。 |
| `ui/CalendarScreen.kt` | 最近 7 天里今天的胶囊有标记时传 `tint = onPrimary`；月格和周格传 `onOwnContainer = true`。 |
| `ui/StatusUi.kt` | `StatusChip` 里的图标传 `onOwnContainer = true`，因为图标画在状态自己的底色上。 |
| `ui/LongitudinalScreen.kt`、`ui/UnconfirmedCard.kt` | 只把注释里的“实心圆”改掉。 |

调用点只调整了传入的颜色参数（任务书 §1.3），逐处如上表。被删掉的 `fill`、外圈 `stroke`、`dashed`、`thinStroke`、`half`、`glyphColor` 字段，只有 `StatusIcon.kt` 和它的测试在用。删除前用 grep 核对过，其他代码没有引用。

## 2. 纯线条规格（实现）

| 状态 | 图形 | 默认颜色 |
|---|---|---|
| 已服 | 勾 | success.accent |
| 部分完成 | 细圆环（外径 0.6d，线宽为主线宽的 0.6 倍），右半边实心半圆饼 | success.accent |
| 漏服 | 叉 | missed.accent |
| 跳过 | 短横 | skipped.accent |
| 未确认 | 问号（两段三次曲线 + 圆点） | unconfirmed.accent |
| 今天 | 不变：细实线环 + 中心圆点 | 调用方传入的 `ring` |

- 线宽为直径的 8%，最小 1.5dp：40dp 时 3.2dp，18dp 时 1.5dp。
- 线条中心线占直径的 52%，加上两端圆头（各 0.04d），图形整体约占 60%。
- 全部使用 round cap / round join。

## 3. 取色规则

`statusIconSpec(kind, sem, ring, onOwnContainer, tint)` 按以下顺序取第一条成立的规则：

1. **`tint`**：调用方给定颜色时用它。目前只在主色背景上传 `onPrimary`：今天卡的“已记录”，以及最近 7 天里今天那个有标记的胶囊。
2. **高对比度**：一律用 `onSurface`。
3. **`onOwnContainer`**：图标画在状态自己的着色日格或状态标签上时，用该令牌的 `onContainer`。用在月格、周格和 `StatusChip`。
4. **默认**：用 `accent`。

部分完成用 success 令牌。部分完成日格的底色是 missed.container，所以那里的线条是 success.onContainer 画在 missed.container 上；矩阵已覆盖这种组合，最低 9.66:1。

动态取色时只有 success 跟随主色，其余令牌固定，测试为 `dynamicColourMovesOnlyTheSuccessLine`。

## 4. 全调用点对比度矩阵（≥ 3:1）

`StatusIconContrastTest` 共 504 组参数，全部 PASS，组合为：

- 14 个调用点（含两处时间线背景，以及状态标签的两种页面底色）；
- × 每处能显示的状态；
- × 浅色 / 深色 × 标准 / 中 / 高三档对比度；
- \+ 浅色 / 深色各 3 组动态取色种子。

测试名含调用点、状态和主题，标准输出逐行打印背景色、线条色和实测比值。下表列出每个“调用点 × 状态”在所有主题中的**最低**比值：

| 调用点 | 状态 | 最低比值 | 出现于 | 背景 | 线条 |
|---|---|---|---|---|---|
| today-log | TAKEN | 6.42 | light/dynamic-blue | #FFFFFF | #415F91 |
| today-log | PARTIAL | 6.42 | light/dynamic-blue | #FFFFFF | #415F91 |
| today-log | MISSED | 5.16 | light/STANDARD | #FFFFFF | #7A6A63 |
| today-log | SKIPPED | 4.49 | light/STANDARD | #FFFFFF | #6F7977 |
| today-log | UNCONFIRMED | 4.49 | light/STANDARD | #FFFFFF | #6F7977 |
| confirm-card | TAKEN | 6.42 | light/dynamic-blue | #FFFFFF | #415F91 |
| confirm-card | MISSED | 5.16 | light/STANDARD | #FFFFFF | #7A6A63 |
| confirm-card | UNCONFIRMED | 4.49 | light/STANDARD | #FFFFFF | #6F7977 |
| recent-7.day | TAKEN | 4.95 | light/dynamic-blue | #E6E0E9 | #415F91 |
| recent-7.day | PARTIAL | 4.95 | light/dynamic-blue | #E6E0E9 | #415F91 |
| recent-7.day | MISSED | 3.98 | light/dynamic-blue | #E6E0E9 | #7A6A63 |
| recent-7.day | UNCONFIRMED | 3.46 | light/dynamic-blue | #E6E0E9 | #6F7977 |
| recent-7.today | TAKEN | 6.42 | light/dynamic-blue | #415F91 | #FFFFFF |
| recent-7.today | PARTIAL | 6.42 | light/dynamic-blue | #415F91 | #FFFFFF |
| recent-7.today | MISSED | 6.42 | light/dynamic-blue | #415F91 | #FFFFFF |
| recent-7.today | UNCONFIRMED | 6.42 | light/dynamic-blue | #415F91 | #FFFFFF |
| recent-7.today-ring | TODAY | 6.42 | light/dynamic-blue | #415F91 | #FFFFFF |
| month-day | TAKEN | 7.22 | dark/dynamic-blue | #284777 | #D6E3FF |
| month-day | PARTIAL | 9.66 | dark/dynamic-purple | #3B322E | #E9DDFF |
| month-day | MISSED | 8.12 | light/STANDARD | #EDE4DF | #4A3F3A |
| month-day | UNCONFIRMED | 8.85 | light/dynamic-purple | #FEF7FF | #3F4947 |
| week-day | TAKEN | 7.22 | dark/dynamic-blue | #284777 | #D6E3FF |
| week-day | PARTIAL | 9.66 | dark/dynamic-purple | #3B322E | #E9DDFF |
| week-day | MISSED | 8.12 | light/STANDARD | #EDE4DF | #4A3F3A |
| week-day | UNCONFIRMED | 8.85 | light/dynamic-purple | #FEF7FF | #3F4947 |
| legend | TAKEN | 6.12 | light/dynamic-blue | #F9F9FF | #415F91 |
| legend | PARTIAL | 6.12 | light/dynamic-blue | #F9F9FF | #415F91 |
| legend | MISSED | 4.91 | light/dynamic-purple | #FEF7FF | #7A6A63 |
| legend | UNCONFIRMED | 4.27 | light/dynamic-purple | #FEF7FF | #6F7977 |
| history.taken | TAKEN | 5.82 | light/dynamic-blue | #F7F2FA | #415F91 |
| history.chip | TAKEN | 7.22 | dark/dynamic-blue | #284777 | #D6E3FF |
| history.chip | MISSED | 8.12 | light/STANDARD | #EDE4DF | #4A3F3A |
| history.chip | SKIPPED | 7.21 | dark/STANDARD | #3F4947 | #DDE4E2 |
| history.chip | UNCONFIRMED | 8.42 | light/STANDARD | #EEF5F3 | #3F4947 |
| day-list.chip | TAKEN | 7.22 | dark/dynamic-blue | #284777 | #D6E3FF |
| day-list.chip | PARTIAL | 9.66 | dark/dynamic-purple | #3B322E | #E9DDFF |
| day-list.chip | MISSED | 8.12 | light/STANDARD | #EDE4DF | #4A3F3A |
| day-list.chip | SKIPPED | 7.21 | dark/STANDARD | #3F4947 | #DDE4E2 |
| day-list.chip | UNCONFIRMED | 8.85 | light/dynamic-purple | #FEF7FF | #3F4947 |
| timeline.missed | MISSED | 5.16 | light/STANDARD | #FFFFFF | #7A6A63 |
| timeline.missed.upcoming | MISSED | 4.91 | light/dynamic-purple | #FEF7FF | #7A6A63 |
| today-card.logged | TAKEN | 6.42 | light/dynamic-blue | #415F91 | #FFFFFF |

最低的一组是最近 7 天普通胶囊（surfaceContainerHighest）上的未确认问号：3.46:1（浅色，动态色）。

任务书举例的深色跳过，如果在跳过底色上用 accent，只有 2.94:1。现在改用 onContainer，达到 7.21:1。

不在此表的情况：月历中非本月的日期整格是 40% 透明度，这是 UI-01 已有的设计，文字和图标一起变淡，本任务不改。

## 5. 可辨识性

1. **状态文字保留**：
   - 列表行、记录行、确认卡、状态标签的文字没有改；
   - 日格的朗读不变（`CalendarSemanticsTest`、`Ui07RecentDaysTest` 通过）。
2. **灰度可分**：
   - 40dp：`Ui07StatusIconTest.fiveStatesStayApartInGrayscale` 未改动，仍然通过。覆盖浅色 / 深色 × 三档对比度，两两之间至少 40 个像素不同。
   - 18dp：新增 `Ui07bSmallIconTest`，见下一条。
3. **18dp 检查**（`Ui07bSmallIconTest`，1× 和 2× 密度，浅色 / 深色）：
   - 5 种状态两两比较，灰度差超过 32 的像素都至少占 6%。最接近的一对是 1× 下的勾和短横：37/324 像素不同，约 11%。
   - 每个图形的着墨面积在 6%–45% 之间（不糊成一团，也不会细到看不见）。实测 1× 下短横最少 20/324，部分完成最多 70/324。
   - 放大图和灰度图在 `docs/ui-audit/2026-10-22-ui07b/small/`。
4. **日历格**保留 UI-06 的底色和描边，图形只是辅助。

## 6. “不画实心圆和外圈”的证明方法

`Ui07bDrawingRecordTest` 使用**绘制记录**：

- 用 `java.lang.reflect.Proxy` 代理 Compose 的 `Canvas` 接口，记录每一次调用（方法名、参数、画笔样式）；
- 在 `CanvasDrawScope` 中对每种状态 × 4 档尺寸 × 1/2/3 倍密度调用 `drawStatusIcon`，然后检查记录。

断言如下：

- 只出现 drawCircle、drawArc、drawLine、drawPath 四种调用。
- 除“今天”外：
  - 实心圆只允许是问号下方的点，半径 ≤ 0.1d；
  - 描边圆只允许是部分完成的环，外缘 ≤ 0.3d，也就是没有外圈；
  - drawArc 只允许是部分完成的半圆饼，在环内；
  - 其余线条都是描边、round cap，线宽等于 8%（不低于 1.5dp）。
- “今天”的环仍画在整个直径上，并有一个中心点。

## 7. 断言迁移

| 原断言（UI-07） | 保护的含义 | 迁移后 |
|---|---|---|
| `StatusIconSpecTest.eachKindDrawsTheTaskBookShape`（填充 / 描边 / 虚线 / 图形色） | 每种状态有自己的外观 | `eachKindDrawsItsOwnGlyph`：6 种状态的图形互不相同；`colourFollowsTheBackground`：各状态的颜色，含任务书给出的浅色色值 |
| `highContrastOutlinesAndGlyphsUseOnSurface` | 高对比度用 onSurface | `highContrastDrawsInOnSurfaceOrTheGivenTint`：默认和着色格上用 onSurface，主色背景上用 onPrimary |
| `everyDoseStateHasItsIconOrNone` | 状态到图标的映射 | 不变 |
| `lineWidthsScaleAndStayLegible` | 线宽随尺寸缩放且可读 | 同名：3.2dp@40、1.5dp@18、单调不减、图形占比 0.6 |
| `StatusIconContrastTest`（图形对填充、边缘对页面，surface 和 surfaceContainerLowest） | 对比度达标 | 同名，改为按实际调用点的背景逐一检查，见第 4 节 |
| `Ui07StatusIconTest.everyKindAndSizeRendersSilently` | 尺寸可渲染、不朗读 | 未改动，通过 |
| `Ui07StatusIconTest.fiveStatesStayApartInGrayscale` | 灰度可分 | 未改动，通过；另加 18dp 检查 |

新增：

- `StatusIconSpecTest.dynamicColourMovesOnlyTheSuccessLine`；
- `Ui07bDrawingRecordTest`（2 项）；
- `Ui07bSmallIconTest`（mdpi、xhdpi）；
- `Ui07bCallSiteColourTest`（4 项）：在真实界面中读取每个调用点实际画出的颜色，与矩阵假设的取色一致。

## 8. 截图

`docs/ui-audit/2026-10-22-ui07b/`，全部为合成数据，药名用“药物 A/B/C”：

- `before/`（UI-07，de0c4e55）和 `after/`：
  - 页面：首页未服、首页已记录、首页有确认卡、月历、服药记录；
  - 组合：zh-CN、en × 320/411dp × 字号 1/2 × 浅色/深色。
- `hc/`：高对比度前后，zh-CN、en，浅色/深色。
- `hires/`：411dp、zh-CN、浅色/深色的原尺寸高清 PNG（xxxhdpi，未压缩），前后各一组。
- `compare/`：关键页前后并排（左为前，右为后）。
- `grayscale/`：zh-CN 411dp 的灰度前后图。
- `small/`：18dp 五种状态，1×（放大 8 倍）和 2×（放大 4 倍），彩色和灰度。

`before/`、`after/`、`hc/`、`compare/` 量化为 256 色以控制仓库体积，`hires/` 未压缩。

## 9. 验证

- `:core:ui:testDebugUnitTest`、完整 `:app:testFullDebugUnitTest` 和 `lintFullDebug` 的本地结果见 HANDOFF。
- PeriodStabilityTest 随完整测试运行。
- CI 全部 job 见 PR。

## 10. NOT_RUN

- 真机：18dp 日历图标是否清楚、深色模式、强光下的可读性。已加入真机清单第 15 节，由负责人回填。
- TalkBack 真机朗读：本任务没有改朗读，Robolectric 的语义测试已通过。
