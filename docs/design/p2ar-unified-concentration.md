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

## Stage B：计算层与图表（`d2112b5`、`d5112b8`）

- `pk-engine/.../ConcentrationModelComparison.kt`：
  - 只选一次合格的舌下 E2 剂量，两个模型共用这组剂量和同一等间距网格：Legacy 用正式 `Engine.simulate`，除以最新一次给药含服档下 1 mg/1 h 的响应；M2 用冻结的 `ResearchSublingualV01`。
  - 显示网格最多 2001 点，最细 1 分钟。指标用以原点给药为起点、0–24 h 的 1 分钟网格计算。
  - 指标：窗口峰值和峰值时刻（含叠加给药，明确不是单次 Tmax）、单次 1 mg Tmax（模型属性，另行计算）、梯形 AUC 0–4/0–8/0–24 h、8/12/24 h 响应。
  - `valuesAt` 在任意时刻直接调用两个计算核取值，不插值，使用的剂量与归一化和曲线完全相同。比值只在分母为正且有限时给出。
- **量纲核实**：`relativeHistory` = Σ(Dᵢ/1 mg)·h(t−tᵢ)/h(1 h)，即"相当于该模型单次 1 mg 给药后 1 h 响应的倍数"，随剂量线性变化。界面单位已按此改写。AUC 单位为（×1 mg/1 h 响应）·h。
- **采样精度**：0.5 h 网格会把 M2 单剂峰值低估 0.08–5.2%（15 个候选）。1 分钟网格的峰值与 AUC 和 1/600 h 网格相比，相对差 < 1e‑4（有测试）。单剂 Tmax 为 0.85–0.98 h。15 个候选的 AUC0–24 与冻结 CSV 的 `AUC_normalized_0_24` 一致（相对差 < 2e‑4，网格不同所致）。
- `ModelComparisonChart`：`ConcChart` 只能画单序列且不可豁免，所以新建此图表。它调用 `ChartViewport.samples` 做裁剪采样，交互与正式图表相同（捏合缩放、拖动平移、点击选择时刻）。另外有纵轴刻度、可见范围内的峰值标注、候选范围带、给药三角标记、长尾阴影、"现在"线、研究读数点和误差线；横轴可以是"原点后 +h"，也可以是日历时间。实验页旧的自绘 Canvas 已删除。

## Stage C：统一血药浓度页面（`d5112b8` + 待批准补丁）

- `UnifiedConcentrationScreen`：顶部是模型选择（Legacy PK 默认 / Experimental M2 / Model comparison，后两项带实验标记）。
  - Legacy 模式直接渲染传入的正式 `ConcentrationScreen` 及其原有结果对象，pg/mL/pmol/L、预测、化验点、校准、区间、其他药物和交互全部不变。
  - 另两个模式显示比较面板：窗口 0–4/0–8/0–24/0–48 h（相对所选给药）或日历 7 天；原点可选任一合格给药；点击读数；指标表；候选；覆盖范围与排除原因；可展开的"模型假设与不确定性"。
  - 切换只改界面状态，不写数据库、记录或设置。
- 混合途径：M2 只处理舌下游离 E2；口服 E2、舌下 EV、其他途径和药物计入排除，并提示"正式页面的总浓度包含它们，此处只含舌下 E2，不能直接比较"。
- **当前可用入口**：已批准的"实验药代模型"抽屉项，`ExperimentalPkScreen` 现在承载统一页面；没有正式页嵌入时，Legacy 以相对曲线显示，并注明正式估算在浓度页。
- **放进"血药浓度"页面需要改 `AppShell.kt`（受保护）**：完整的待批准补丁见 `docs/design/p2ar-entry-approval.patch`，未应用。

## Stage D：文献锚定研究情景

- **证据核查**：
  - 冻结 P2-X 拟合在固定 Price 背景 B 下对 Price 1997 Figure 1 读数做闭式加权最小二乘，得到幅度 A。逐个候选重算后，A·∫₀²⁴H 与冻结 CSV 的 `Price_model_auc` 在全部 40 行上相对差 ≤ 7e‑16（Python 复算），15 个候选 ≤ 1e‑9（Kotlin 测试）。所以这个锚点有据可查，不是编造的。
  - A = 431–444 pg/mL；B 为 0/6/12/18/24 pg/mL，是重建该论文的敏感性假设，不是用户基线。
- **实现**：`PriceFigure1997` / `StudyAnchors`。M2 模式可切换到"Price 1997 研究情景（pg/mL）"，C(t) = B + A·Σ Dᵢ·f(t−tᵢ)，用于用户已记录的舌下 E2 剂量。范围带为 15 个候选各自用自己的锚点。常驻"文献研究情景，非个人血药浓度预测"。另有"单次 1 mg 重建"小图，叠加 Figure 1 读数点与读图宽度。
- **AUC 冲突照实显示，不强制匹配**：
  - 模型在 B 之上的面积为 954–1534 pg·h/mL，因候选而异。
  - Figure 1 读数梯形（0 h 假设为 0）为 1557.5 pg·h/mL。
  - Table 1 为 2109 ± 1031 pg·h/mL（逐人扣除给药前值）。
  - 页面写明三者不一致且原因未解释。模型峰值同时与 Table 1 Cmax 451 ± 162 pg/mL 并列显示。
- **拒绝的锚点**（不显示 pg/mL）：
  - Doll 2022：单一时间点，给药前基线未知，背景与幅度不可分。
  - Rosano 1997：只有 0–1 h，基线未知。
  - Komesaroff 1998：只有 0–30 min，1 h 幅度只能外推。
- **不能用作个人浓度预测的原因**：组均值来自 6 人的读图；背景是论文重建假设；没有个人校准或外部验证；8 h 以后的形状不可辨识；剂量线性没有在个人身上验证；不用化验值；Legacy 的 pg/mL 不会被借给 M2。
