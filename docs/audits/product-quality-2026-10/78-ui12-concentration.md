# 78 · UI-12 浓度页统一：一条 E2 合计曲线

日期：2026-10-29。任务书原文：`docs/design/ui12-concentration-2026-10-28/TASK_BOOK_ORIGINAL.md`；负责人决定与保持项：REQUIREMENTS §162。分支 `feature/ui12-concentration-20261028`。

## 1. 结论

浓度页现在只有一张「雌二醇（E2）估算」卡和一条合计曲线：舌下部分取 M2 情景曲线，其余途径取既有兼容计算，按小时相加；读数、「现在」数值、剂量标记、图例、依据说明都由同一条曲线给出。**只改显示层**：没有改任何模型、参数、候选、校准规则、导出、Room schema、备份。

## 2. 合计怎么算（显示层）

- 舌下部分 = M2 `ConcentrationPreview` 的 `valueAt`，数值不变。M2 自带的背景 B（`C = B + A*H`）**只计一次**。
- 其余途径 = 同一个 `ConcentrationCalculator.compute` 再跑一次，**只喂非舌下的 E2**（`E2Total.nonSublingualInputs`，`calibrate=false`），所以任何舌下剂量（含计划中的舌下剂量）不会被算两次；这部分没有自己的背景。
- 时间轴取两条曲线时间点的并集；兼容部分在并集点上线性插值（仅用于画线）。**点读数是精确值**：M2 `valueAt(h)` + 兼容 `evaluateAt(h)`，不经插值。
- 有舌下记录但 M2 无法覆盖（Legacy 或待定）→ **不显示合计**，不用只含部分途径的曲线冒充总数（不少算）。
- 只有一种途径 → 与改前同一个数、同一条线（`E2TotalTest` 逐点断言，容差 0）。
- 化验校准：只在没有任何舌下记录时可用；有舌下记录时开关禁用，旁边写「暂不能用」及原因，混合合计从不标「已校准」。

## 3. 改动文件与批准记录

| 文件 | 类别 | 改前 SHA-256（default d18b2c91） | 改后 SHA-256 | 批准 id |
| --- | --- | --- | --- | --- |
| `ui/ConcentrationScreen.kt` | PK_DISPLAY_REVIEW | `7ed4e28f…3142` | `426925ca…3e7` | `PD-2026-10-29-UI12-CONCENTRATIONSCREEN` |
| `ui/ConcChart.kt` | PK_DISPLAY_REVIEW | `51d0f88b…6c26` | `9fca1d03…023a` | `PD-2026-10-29-UI12-CONCCHART` |
| `experimental/UnifiedConcentrationScreen.kt` | 需审批（B0/§69） | `29c09d46…def5` | `01808b49…161c` | `PD-2026-10-29-UI12-UNIFIEDSCREEN` |
| `experimental/ConcentrationPreview.kt` | 需审批（B0/§69） | `846017d2…afdc16` 起 | `1f51e19c…1d5b` | `PD-2026-10-29-UI12-PREVIEW` |

完整哈希与证据 commit 在 `display-approvals-after-archive.json`（追加式，旧条目逐字节未动）。`ChartViewport.kt` 经核对无需改动，不登记。新增 `ui/E2Total.kt`（新文件，纯显示层）。`NotesViewModel.kt`、`AppShell.kt`（HISTORY_ONLY）仅接线：第二次兼容计算 `ConcState.nonSublingual`，传 `e2NonSublingual`、`doseTimes`。LIVE_LOCK 文件与 `Exports.kt` 未改。

## 4. 数值对照（`E2TotalTest`，合成数据）

| 场景 | 断言 |
| --- | --- |
| 仅舌下 | 合计的时间轴与数值与 M2 曲线逐点相同（容差 0）；剂量数 3；未校准 |
| 仅其他途径 | 与改前兼容曲线逐点相同；「现在」值相同；途径=口服 |
| 舌下 + 口服 | 并集点上 = M2 精确值 + 兼容插值（1e-9）；精确读数 = 两部分精确读数之和；首剂之前整条曲线 = M2 背景；兼容部分无背景 |
| 舌下/计划舌下剂量 | 非舌下输入里没有任何舌下记录或计划；CPA 等非 E2 不进入 |
| M2 不可用 | 返回 null，不是 Legacy 曲线 |
| 途径顺序 | 按加入顺序、每种一次 |
| 校准 | 仅口服时与校准结果逐点相同；混合时不标已校准 |

失败先行证据：`docs/design/ui12-concentration-2026-10-28/evidence/e2total-red-before-implementation.txt`（实现前测试编译失败）。

## 5. 迁移的断言（原含义如何保留）

`UnifiedConcentrationUiTest` 与 `M2ScenarioDisplayTest` 中每处迁移都在测试里以 `UI-12 migration of <原测试名>` 注释标明：

- 原卡片与日历控件不出现模型选择器 → 仍断言无选择器；
- M2 不显示当前数值、曲线为固定核 → 数值合约不变，现由合计曲线承载；
- 单位切换换算且每个读数标明情景 → 换算与读数标注仍断言，读数改为浮动卡 `chart-readout`；
- 个人化验不能缩放 M2、旧校准不当作 M2 → 仍断言，校准开关禁用并写明原因；
- 点击、缩放、平移 → 手势不变，读数气泡换为读数卡；
- 「混合途径只是部分、绝不当作总数」→ **被负责人决定取代**（§162：改为相加成一条总曲线），改成合计断言；「部分范围/无合计」提示被「未识别记录」提示取代；
- `Ui08PdfSummaryUnchangedTest` 对 `ConcChart.kt` 的哈希锁：接受原哈希或本任务批准后的哈希。
- 其余：`UnifiedConcentrationTranslationsTest`（去掉 `m2_chart_title`）、`ButtonAuditTest`、`M2ScenarioVisualAuditTest`、`UnifiedConcentrationAndroidTest`。
- `PeriodStabilityTest` 与 PK 显示回归未放宽、未删减。

## 6. 被取代与保持

- 取代：D-1 / §65 中「混合途径不得合成总数」「M2 与兼容曲线分开两张卡」这两点，范围见 §162。
- 保持：§67 D-2（导出与新保存快照不输出合并的舌下总 E2）；`Exports.kt` 未改；`m2_absolute_pgml=false` 未改。**如实记录的张力**：页面显示合计数字，而能力矩阵仍标「未验证的绝对值」。页面保留提醒（`e2-reminder`）与「模型与文献」窗口里的「研究情景，非个人预测」说明，由负责人日后另行决定。

## 7. 字符串

删除（被新字符串取代）：`uc_original_tail`、`m2_chart_title`、`m2_compatibility_current`、`m2_compatibility_chart`、`m2_calibration_unavailable`、`m2_calibration_compatibility_scope`、`m2_scenario_title`（四语言）。
保留：其余 `uc_*`、`m2_*` 与全部 PK 研究字符串。
新增（四语言 `ui12_concentration.xml`，各 28 条）：合计与途径标签、依据与提醒、读数朗读、校准不可用原因、「模型与文献」窗口、背景只计一次说明、未识别记录提示、预测尾部图例等。

## 8. 验证

- 全量 `:app:testFullDebugUnitTest`（合并 default d18b2c91 之后）：3367 个测试，0 失败 0 错误（601 个按配置跳过）；`lintFullDebug` 通过。
- 截图：`docs/ui-audit/2026-10-28-ui12/{before,after,compare}`，场景 混合 / 仅口服 / 无记录 / 需权重 / pmol / 模型与文献窗口；四语 × 320dp 字号 2× / 411dp 字号 1× × 明暗；`compare` 为改前、改后并排。样图对照见同目录 `compose_compare.py` 的输出。
- `Ui12ConcentrationTest` 按四语 × 320/411dp × 字号 1×/2× × 明暗参数化，断言：无被裁切文字、大字号下大数字与单位不截断、图例用文字加不同形状而不只靠颜色、读数卡点击打开再点关闭。**没有为本页新增文字对比度和减少动态的专门断言**（沿用既有主题与全局对比度测试，页面未新增动画）；这两项列入 NOT_RUN。
- CI 必需检查见 PR；合并后复验 default 分支 CI。

## 9. NOT_RUN（未验证）

- 真机（物理设备）：未运行。截图均为 Robolectric 合成渲染。
- 本页专门的文字对比度与减少动态断言：未新增（见第 8 节）。
- 人工 TalkBack：未做；读数的 `liveRegion`/朗读文本只做了语义断言。
- 411dp 下嵌套对话框（「模型与文献」窗口叠在其它对话框上）：未手测。
- 化验校准在混合合计下的整曲线缩放：不实现。SL-CAL-01（报告 79）建议的起点只是研究结论，未接入页面。
