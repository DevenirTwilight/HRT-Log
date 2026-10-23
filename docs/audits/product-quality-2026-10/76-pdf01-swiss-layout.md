# 76 — PDF-01：用瑞士风格重排 PDF 报告和就诊资料包（2026-10-23）

依据：负责人任务书 v2（`sources/pdf01/pdf01-prompt.md`，SHA-256 4f0f9986…58db；v1 另存 `pdf01-prompt-v1.md`），REQUIREMENTS §145。v2 的改动：章节之间不画线，只靠留白（上方 12pt、下方 16pt）和粗体标题区分。

## 1. 改动文件

| 文件 | 类别 | 改动 |
|---|---|---|
| `app/src/main/java/net/plainnotes/app/export/Exports.kt` | PK_DISPLAY_REVIEW | 只改 `PdfReport` 的版式。批准记录 `PD-2026-10-23-PDF01-EXPORTS`，哈希 407710c3… → 30067c52… |
| `app/src/test/.../export/LegacyPdfReport.kt` | 测试 | 新增：改动前的 `PdfReport` 冻结副本，作文字对照 |
| `app/src/test/.../export/Pdf01Test.kt` | 测试 | 新增 |
| `app/src/test/.../Ui08Test.kt` | 测试 | 迁移 `Ui08PdfSummaryUnchangedTest`（见第 4 节） |
| `docs/audits/.../display-approvals-after-archive.json` | 记录 | 追加一条批准 |

以下全部未改：CSV 导出、`VisitPack.kt`（事实计算与摘要，哈希 56d5e5a0… 在测试中固定）、PK 与校准、`ConcChart.kt`、`drawChart`、Room、字体、依赖、versionCode。

## 2. 结构

- `PdfReport.write()` 的签名不变。它把 `PdfDocument` 包成一个 `Sink`（开始页 / 结束页），再调用 `render()`。
- `render()` 多了两个参数，都只供测试用：`now`（生成时间）和 `trace`（每段文字写出前回报一次）。应用里用默认值，行为不变。
- 冻结副本 `LegacyPdfReport` 是基线 d2223c0a 上的 `PdfReport` 逐行复制，只有机械改动：
  - 写入 `Sink`，不写入 `PdfDocument`；
  - `now` 由参数传入；
  - 在 text、row、footer、chart 处调用 `trace`。
  
  与基线的完整差异是 36 行 diff，全部属于以上三类。副本的哈希 a9a2de73… 在 `Ui08PdfSummaryUnchangedTest` 中固定。

## 3. 版式（任务书第 2 节逐条）

| 规格 | 实现 |
|---|---|
| A4；左右 48、上 56、下 64pt | `W/H/M`、`TOP`、`BOTTOM`；正文排到 `CONTENT_BOTTOM = H − 64` 为止 |
| 12 栏，栏间距 12pt，4pt 基线 | `COL`、`colX(i)`、`span(n)`；块高度向上取整到 4pt |
| Roboto / Noto CJK，常规 + 真粗体 | `Typeface.create(SANS_SERIF, NORMAL/BOLD)`，不用 `isFakeBoldText` |
| 标题 26 粗、章节 13 粗、正文 10、元信息/页脚 8 | 共 7 种笔，字号只有 26/13/10/8 |
| 黑白 + #006A63 | 文字黑；元信息、页脚、日间小图为灰 #616161；表格行线 #BDBDBD；强调色只用于编号和页眉粗线 |
| 左对齐，数字列右对齐 | 全部 `ALIGN_NORMAL` / `Align.LEFT`；化验表“数值”列（表头和单元格）右对齐 |
| 页眉 | 标题 → 期间 / 生成时间 / 校验码三栏（各占 4 栏）→ 就诊信息行 → 1.5pt 强调色线 |
| 章节编号 | 01、02… 在第 1–2 栏，13pt 粗体强调色等宽数字；标题和内容在右 10 栏 |
| 章节之间不画线（v2） | 章节上方 12pt（页顶除外）、下方 16pt，章节之间不画任何线 |
| 表格 | 8pt 粗体表头 + 0.5pt 黑线；行线 0.25pt #BDBDBD；任何跨页（行之前，或某行的上下文说明中途）都会在新页顶部重复表头 |
| 页脚 | 上方 0.25pt 细线；左侧为原页脚说明文字，右侧为页码，8pt 灰 |
| 不留孤立标题 | 章节标题加两行正文放不下就整体移到下一页 |

“方案版本”“记录时的药物信息”这两个小标题原来也用章节标题的样式。现在改为 10pt 粗体小标题，不编号，因为它们是所在章节的一部分，不是独立章节。

## 4. 内容不变的证明与断言迁移

`Pdf01Test`（Robolectric，NATIVE 图形）用一个“录制画布”作为 `Sink`。它记录每次 drawText、drawTextRun、drawLine、drawPath、drawCircle、drawRect 的位置和画笔，并记录 `trace`。

- **文字相同**（`textIsIdenticalToTheOldLayout`）：
  - 覆盖 6 种情形（报告、就诊资料包、身心状态摘要，各有“全部章节”和“部分章节”两种）× 4 种语言（zh-CN / en / fr / zh-Hant-TW）。
  - 新旧版式写出的文字拆成原子（按 “ · ” 和换行拆开）后，作为多重集合完全相等。
  - 页脚逐页等于原模板。图表调用次数相同。
  - 画布上实际画出的每段文字，都是某段原文字的一部分，或是章节编号。没有出现其他新文字。
  - 原来页眉中“期间 · 生成时间”是一行，现在拆成两栏。原子仍然相同。
- **摘要不变**（`digestIsUnchanged`）：同样的输入得到同样的 64 位摘要；PDF 上显示的前 12 位一样；`VisitPack.kt` 的哈希固定为基线值。
- **PK 显示回归**（`chartIsDrawnTheSameWay`）：浓度图仍由原 `drawChart` 画出，参数相同（x=48，宽 499，高 180）。新旧两次录制的图表绘制调用逐个比对，种类、文字、颜色、线宽、字号、x 坐标都相同，y 只差一个固定的平移量。此外完整单元测试中的浓度、化验上下文、D-2 报告方案、`CausalTimeUiTest` 等原有测试都通过（第 6 节）。
- **版式断言**（`layoutFollowsTheGrid`，6 种情形加“长报告”（30 种药、70 条化验）× 4 种语言）：
  - 字号只有 26/13/10/8；8pt 只出现在页脚、页眉元信息、表头和日间小图的刻度上；
  - 只有常规和粗体两种字重，没有假粗体；
  - 颜色只有黑、灰、#BDBDBD、#006A63，强调色只出现在编号和页眉粗线上；
  - 没有居中：所有文字的起点落在网格栏或表格列上，或终点落在右边缘；
  - 编号从 01 起连续，并且画在第 1 栏；
  - **线只有**页眉强调色线（1 条，在第 1 页）、每页 1 条页脚线、表格线（数量等于表头次数加行数）和日间小图的折线，章节之间没有线；
  - 每一页有表格行时都先有表头，长报告的表格确实跨页；
  - 没有矩形填充。
- **无孤立标题**（`noOrphanSectionTitles`）：药物数量从 1 到 48 逐一渲染，任何章节标题都不会出现在页尾而内容在下一页。
- **源码守卫**（`sourceGuard`）：`PdfReport` 中没有 `isFakeBoldText`、`ALIGN_CENTER`、`Align.CENTER`、`Style.FILL`、`drawRect`；网格常量固定。
- **UI-08 迁移**：`Ui08PdfSummaryUnchangedTest` 原来逐字节固定 Exports.kt（407710c3…），用来保证身心状态 PDF 摘要不变。现在迁移为“文字相同 + 版式符合 PDF-01”：
  - 冻结副本 `LegacyPdfReport.kt` 的哈希固定在原位置；
  - 摘要的“全部 / 部分”两种情形在四种语言下与冻结副本逐字一致，由 `Pdf01Test` 证明；
  - 版式由同一测试的版式断言约束；
  - 该测试其余断言（WellbeingSummary.kt、ConcChart.kt、四种语言 strings 的基线哈希）原样保留。

测试过程中发现并修复了一处表格问题：某一行的上下文说明跨页时，新页上后面的行没有重复表头。现在改为表格进行期间任何换页都先画表头。

## 5. 偏离与说明

- **页脚文字**：任务书写“左侧为文档类型短名，右侧为‘第 n 页’”。为了遵守“唯一新增文字是章节编号”，页脚只拆分原有文字，不新增文档类型名：
  - 报告：原来是“第 n 页 · 数据由本人记录”，现在左侧是“数据由本人记录”，右侧是“第 n 页”；
  - 就诊资料包和身心状态摘要：原来是“说明 · n”，现在左侧是说明，右侧是页码 n。
  
  如需加文档类型短名，属于新增文字，要另行授权。
- **日间小图**：每日条目的小折线图和日记仍跟在“阶段回顾”内容之后，没有单独编号，出现条件和顺序同原来一样；结束后补上章节下方的 16pt 留白。折线颜色从 DKGRAY 改为同一种灰 #616161。
- **浓度图位置**：图仍占满内容宽度（x=48，宽 499），没有放进右 10 栏，因为图本身的绘制代码不改。图上方加了 8pt，避免它的顶部刻度文字碰到上一行说明。
- **正文行距**：列表行与行之间不再额外加 4pt，相邻两行间距为 16pt（基线的 4 倍）。

## 6. 验证

- `Pdf01Test` 7 项（`samples` 只在设置 `PDF01_SAMPLES` 时运行）与 `Ui08PdfSummaryUnchangedTest`：全部通过。
- `tools/pk-research` 的 `test_research.py` 32 项：通过（含追加式批准记录检查）。
- 完整 `:app:testFullDebugUnitTest`、lint 和 CI：见 PR。

## 7. 样张

`docs/ui-audit/2026-10-23-pdf01/`：报告、就诊资料包、身心状态摘要的“全部章节”情形，四种语言，改动前（`_before_`，即冻结副本）和改动后（`_after_`）逐页对照，共 57 页。

图片由同一套画布调用在 Robolectric 原生图形中以 2 倍分辨率画出，再量化为 64 色。数据是合成数据（`Pdf01Fixture`），没有真实健康数据。

## 8. NOT_RUN

- **原始 PDF 文件**：Robolectric 没有 `PdfDocument` 后端，本环境没有模拟器，因此没有附上真机生成的 PDF 原件。样张中的每一笔都来自 PDF 用的同一套 Canvas 调用，但与真机 PDF 在字体渲染细节上可能略有差异。需要时可以在设备测试或真机上导出补齐。
- 真机打印 / 阅读器检查。
