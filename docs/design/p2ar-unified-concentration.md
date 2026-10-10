# P2-AR：统一血药浓度页面与 M2 定量比较（2026-10-10）

分支 `p2ar/unified-concentration-comparison`，基于 PR #2 头 `cd56688`（包含完整 M2 实现）。默认分支 `claude/new-session-1959qb` 仍为 `f69d892`；PR #1（`c0b9b6c`）与 PR #2（`cd56688`）均为未合并的 Draft。

## Stage A：仓库现状审计

### 数据流

- 正式浓度页：`AppShell`（`Destination.CONCENTRATION`）→ `NotesViewModel.loadConcentration()` → `ConcentrationCalculator.compute`（快照资格、180 天历史、30 天计划预测、化验校准、蒙特卡洛区间）→ `Engine.simulate` / `LabFit.bands` → `ConcentrationScreen` + `ConcChart`（单序列，pg/mL 或 pmol/L）。
- M2 实验页（PR #2）：`AppShell`（`Destination.EXPERIMENTAL_PK`）→ `extra.records` + `state.ruleSnapshots` → `ResearchHistoricalSlAdapter.audit`（只读，冻结快照）→ `ExperimentalSlModelView`（48 h、0.5 h 网格；旧模型取自 `ResearchShapeComparisonV01`）→ `ExperimentalPkScreen` 自绘 Canvas。

### 现有缺陷（与需求对照）

1. 模型选择在独立抽屉页，不在浓度页。
2. 实验页图表没有纵轴刻度、不能缩放平移、不能点击读数，与正式图表交互不一致。
3. 没有峰值、峰时、AUC、8/12/24 h 响应等定量指标。
4. 网格固定 0.5 h：舌下峰约在 0.9–1 h，0.5 h 采样会低估峰值、偏移峰时（Stage B 用测试量化）。
5. 时间窗口固定为"过去 48 小时"，没有"相对某次给药"的时间原点，多次给药时不能定义峰时和积分窗口。
6. 量纲标签"相对响应（无量纲）"不完整：`relativeHistory` = Σ(Dᵢ/1 mg)·h(t−tᵢ)/h(1 h)，即"相当于单次 1 mg 给药后 1 h 响应的倍数"。数值随剂量线性增加，应这样标注。
7. 没有任何 pg/mL 研究情景。`ExperimentalStudyAnchor` / `studyScenario` 已存在，但没有经过核实的锚点。

### 受保护文件与校验规则

`check_protocol.py` 对 `production-baseline.json` 中 164 个文件做 SHA-256 校验：

- `ConcentrationScreen.kt`、`ConcChart.kt`、`ChartViewport.kt`、`ConcentrationCalculator.kt`、`Engine.kt`、`pk-params.json`、四种语言的 `strings.xml`、`AndroidManifest.xml`、`NotesViewModel.kt`：**永不可豁免**（不在 ui/ 下，或文件名含 Conc/Lab/Chart）。
- `AppShell.kt`：只接受已登记的 `PD-2026-10-10-M2-ENTRY` 精确哈希 `9f7658e7…`。任何新改动都需要新的、由负责人批准的精确登记，以及检查器中对应的写死条目。

### 方案（不修改任何受保护文件）

- **计算层（pk-engine，新文件）**：
  - `ConcentrationModelComparison`：对同一组合格舌下 E2 剂量，在同一时间网格上计算 Legacy（复用正式 `Engine.simulate`）和 M2（复用 `ResearchSublingualV01`）。返回 15 个候选的范围、给药标记、长尾标记，以及相对所选给药原点的指标。
  - `PriceFigureAnchor`：用冻结的 Price 1997 Figure 1 估读数据和 P2-X 冻结拟合规则，逐个候选重算 1 mg/1 h 幅度；`studyScenario` 产生 pg/mL 研究情景。
- **图表**：`ConcChart` 只能画单序列，且永不可豁免，所以新建 `ModelComparisonChart`。它复用 `ChartViewport`（调用，不修改）的采样裁剪和分段，交互与正式图表一致：捏合缩放、拖动平移、点击读数，日历时间轴格式相同。同时删除实验页旧的自绘 Canvas，避免出现第三套图表。
- **统一页面**：新建 `UnifiedConcentrationScreen`，顶部是模型选择器（Legacy PK 默认 / Experimental M2 / Model comparison）。Legacy 模式直接嵌入未修改的 `ConcentrationScreen`，结果对象原样传入，所以数值不可能回归；另两个模式显示新的比较面板。
- **入口**：把它放进"血药浓度"页面需要改 `AppShell.kt` 的路由，属于受保护改动。按要求只提交补丁、精确哈希和登记草案，**不应用**，等负责人批准。在此之前，新的统一页面先通过已批准的"实验药代模型"抽屉入口提供（该入口路由不变，`ExperimentalPkScreen` 改为承载统一页面）。

### 主要风险

- 用户可能把研究情景的 pg/mL 当作个人浓度：情景模式用独立标签、独立颜色，并常驻"文献研究情景，非个人血药浓度预测"。
- 多次给药时把叠加峰误当单次 Tmax：分别给出"单次 1 mg Tmax（模型属性）"和"窗口内峰值时刻（含叠加）"。
- 性能：15 个候选 × 1441 点 × 剂量数，在后台线程计算，并受计算核的 1 万条剂量上限保护。
