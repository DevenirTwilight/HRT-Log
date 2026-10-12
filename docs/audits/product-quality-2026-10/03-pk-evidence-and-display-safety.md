# 03 药代模型科学可信度与浓度显示安全（阶段 C，2026-10-12）

本报告只审查和提出方案，**不修改任何模型、参数、校准或 UI**。不提供个体治疗或剂量建议。

审计对象：
- 默认分支 f53726f 的 Legacy 正式实现；
- PR #19 head aecbd7d 的 M2-only 显示（未合并）；
- PR #18 head 68ceac2 的能力矩阵（Draft，未合并）。

## 1. 数据路径

### 1.1 默认分支（Legacy）

```
RecordEntity（实际服药，冻结 config_snapshot）
  → HistoricalContext.resolved / CalibrationEligibility（逐条资格，P1-B）
  → ConcentrationCalculator.compute（app/conc，Dispatchers.Default，可取消）
      → pk-engine Engine（按途径/酯的文献拟合核，pk-params.json）
      → LabFit（MAP + 拉普拉斯，200 次 MC → 5/25/75/95 分位，算法 2）
  → ConcentrationResult（timeH/e2/bands/labs/evaluateAt）
  → ConcentrationScreen 大号“当前估算”+ ConcChart（精确点击调用 evaluateAt）
  → 旁路 1：PdfReport.drawChart（Exports.kt:183-187，用 NotesViewModel.conc.value.result）
  → 旁路 2：LabEstimate.capture（冻结采样时未校准估算 + 5–95% 区间到 LabContext）
```

### 1.2 PR #19（M2-only）

```
records + ruleSnapshots
  → ExperimentalConcentrationComparison.audit（ResearchHistoricalSlAdapter：只收确认的 SL+E2 记录）
  → ResearchHistoryScope.inspect（其他途径/未知记录计数，未知不当作“无”）
  → ConcentrationPreview.compute
      → PreparedResearchHistory（冻结 p2x-39 核：transit n=10 + 慢吸收，线性叠加，无插值）
      → C(t) = B + A · Σ Dᵢ · H(t − tᵢ)，B = 24 pg/mL（假设背景，只加一次），A = 434.84（Price 1997 图 1 加权拟合）
      → 每小时网格 + 每剂 8 h 内 5 分钟网格 + 精细峰值
  → ConcentrationScreen(preview, m2Only=true)
      大号“M2 文献参考浓度 xxx pg/mL”；化验点叠加（“未用于 M2 拟合”）；Legacy 校准卡隐藏
  → 背景中 Legacy 计算照常进行（NotesViewModel.loadConcentration 未改）
  → 旁路 1/2 **未改**：PDF 图表和化验冻结估算仍是 Legacy（含舌下）
```

## 2. 证据等级（必须分开）

| 层级 | Legacy（默认） | M2 p2x-39（PR #19） | 依据 |
|---|---|---|---|
| ① 实现/数值正确性 | 有：`EngineTest`、`LiteratureValidationTest`、P0/P1 回放审计；本地 pk-engine 58/58 通过 | 有：PR 自述 Python/SciPy 独立重算、9 项变异测试、233 候选参考，CI 全绿 | 本地 JVM 测试；PR #19 check runs |
| ② 已见组级拟合/跨研究形状 | 各途径按文献均值拟合，验证标准是落在**同类文献**均值 ±1 SD（`docs/pk-model.md:133`），拟合与验证资料有重叠；舌下 Legacy 用口服模型生成的代理 AUC（`pk-model.md:152`） | P2-AZ/BA：3 个已见研究、18 个敏感性设置中，M2 的组级**归一化形状**全部优于 Legacy | PR #18 README |
| ③ 独立、未见、冻结后的外部人体验证 | **0** | **0**（`LOCKED_EXTERNAL=0`，P2-AV/AX 0/12、0/9 合格） | HANDOFF P2-AV/AX |
| ④ 个体绝对浓度预测（幅度/基线/检测误差/含服/重复给药/8–24 h 尾部） | 未验证；LabFit 个人校准只做参数不确定性，不含观测误差和结构误差（`pk_assumptions`） | 未验证；A 来自 **6 名绝经后女性**的组均值手工读图；B=24 是该研究背景假设；剂量线性和重复给药叠加未经人体检验 | `StudyAnchors.kt` 注释 |
| ⑤ 产品用途 | 估算展示 + 化验校准；明确“不作调整剂量依据” | 文献情景参考；PR 标签“非个人或总 E2 预测” | 字符串资源 |

**结论**：两个模型都只停留在①和②。“M2 形状在已见研究上更好”这一命题，**不能**推出“M2 对个人更准确”。Legacy 也不能被称为金标准。

## 3. 发现

### F-PK-1 [确认，PR #19] PDF 报告的浓度图仍然是 Legacy（含舌下）

- 设置 → 数据 → PDF 报告，勾选“含图表”，会调用 `NotesViewModel.exportPdf`（`NotesViewModel.kt:236-239`），取 `conc.value.result`，即 Legacy 结果。`PdfReport` 画 `r.e2` 和 5–95% 带（`Exports.kt:183-187, 246-260`）。
- PR #19 没有修改 `NotesViewModel.kt` 和 `export/*`（`git diff --stat` 为空）。
- 后果：页面上说舌下 Legacy 已退役、混合途径不提供合并总 E2；带到医生面前的 PDF 却是 Legacy 合并总 E2 曲线加区间。**同一个产品两种口径**。
- 建议：合并前在 PR #19 中决定 PDF 图的口径（与页面一致 / 暂时禁用含舌下的图），并加回归测试。
- 优先级 P1，需决策 D-2。

### F-PK-2 [确认，PR #19] 化验“附上采样时未校准估算”仍冻结 Legacy 舌下估算

- `LabDialog` 的复选框 → `NotesViewModel.saveLab(includeEstimate)` → `LabEstimate.capture`，调用 `ConcentrationCalculator.compute`（Legacy），把 E2 值和 5–95% 写入 `LabContext`（`conc/LabEstimate.kt:11-20`）。PR #19 未改。
- 后果：用户在 M2-only 产品中新存的化验，旁边会出现 Legacy 舌下估算及“已保存的 5–95% 区间”。
- 建议：舌下 E2 的新冻结估算改为“暂不提供”，或明确标注“旧兼容模型”。**旧的已冻结快照不得改写**。
- 优先级 P1，D-2。

### F-PK-3 [确认，PR #19] 页面同时出现两套说明

- 顶部仍是 Legacy 的 `pk_disclaimer_short`（“基于群体平均参数的估算……”），下面紧接 M2 的研究说明（含 `B=…、A=… pg/mL（1mg/1h增量）`、Price 1997 图 1、Cmax/Tmax 数字）。
- 底部“模型”卡片仍显示 `pk_source`（指向 Legacy 文献）。
- 对普通用户，术语密度高且口径混杂。
- 建议：M2 模式下换成一段通俗说明（“这是一项 6 人研究的平均曲线，按你记录的舌下剂量叠加，不是你的血药浓度”），技术参数收进“研究详情”。
- 优先级 P1。

### F-PK-4 [风险，需产品负责人决策] 个人历史驱动的绝对 pg/mL 大号数字，与 PR #18 能力矩阵冲突

- PR #18（`p2ba-results.json` → `model_and_product_use_capabilities`）规定：
  - `m2_absolute_pgml: allowed=false`（理由：个体基线、增益、检测校准和外部评估均缺失）；
  - `m2_default_engine_replacement: allowed=false`（P2-C HOLD）；
  - `m2_normalized_research: allowed=true, requires_opt_in=true, units=dimensionless`。
- PR #19 的做法：
  - 默认（无需 opt-in）把 M2 作为唯一舌下显示；
  - 以 `displayMedium` 显示“M2 文献参考浓度 xxx pg/mL”；
  - 数值随用户自己的剂量历史变化。
- 文案确实写了“非个人预测”。但“由我的记录算出、标着 pg/mL、显示为当前值”的数字，其功能就是个人绝对浓度估计。
- 用户在 REQUIREMENTS §65/§66（仅在 PR #19 分支上）明确授权了 M2-only 显示。因此这**不是**擅自实施，而是**两份未合并文档之间的冲突**，需要用户拍板（07 D-1）。
- 选项：
  - (a) 维持 PR #19；
  - (b) 默认显示归一化相对曲线，绝对值需要 opt-in；
  - (c) 保留绝对曲线但取消大号“当前值”，只在图上点读；
  - (d) 舌下暂不显示数值，只显示服药时间轴。
- 按本提示词规则，标为**高优先级风险（P1-High）**，不是 P0：证据显示文案没有宣称它是可信的个体实测或临床预测。

### F-PK-5 [确认] 化验点叠加在文献曲线上，暗示可比

- PR #19 把用户实测总 E2 点画在 M2 文献曲线上，图例“实际化验 · 未用于 M2 拟合”。用户自然会比较“曲线 vs 我的化验”，而两者的幅度、基线、检测方法并不一致。
- 建议：化验点改到独立轨道/独立图，或默认隐藏，可切换显示。

### F-PK-6 [风险] 人群和条件外推

- Price 1997：1 mg 微粒化 E2 舌下，n=6 绝经后女性，组均值手工读图。读图宽度是假设，不是 SD（`StudyAnchors.kt:31-33`）。B=24 也是该研究背景的假设。
- 应用用户多为使用抗雄激素的跨性别女性，内源 E2、SHBG、给药剂量（常 2–6 mg/天、分次）、含服时长都可能不同。
- 剂量线性（2 mg = 2 × 1 mg 增量）和多次给药线性叠加都没有人体验证。
- 该边界已在 PR #19 文案中部分说明（“8–24h 尾部不确定”），但没有提到“人群不同”。

### F-PK-7 [确认] 代表候选是在已见数据上选的

`p2x-39` 是 P2-X 中“已见数据伪损失最低”的候选（`ResearchSublingualV01.kt` 候选表，`ResearchDisplayProtocol.m2Id`），属于样本内选择。PR 文档也写明“并非临床赢家”。15 个候选的范围**不是** 90% 预测区间（PR #18：`m2_90pct_prediction_interval: false`）。PR #19 没有显示候选范围带，这一点做得对。

### F-PK-8 [风险，P3] M2 “当前值”的时间点可能滞后

- `asOf = remember(records, ruleSnapshots) { now() }`（`UnifiedConcentrationScreen.kt`）：只有记录变化或离开再进入页面时才更新。
- Legacy 的 `nowH`（图上的“现在”竖线和横轴窗口）每 30 s 随刷新更新。
- 长时间停留在页面时，大号 M2 数字对应的时刻会早于图上的“现在”线。
- 影响小，需真机确认。

### F-PK-9 [确认，产品取舍] 混合途径用户失去合并总 E2

PR #19 对“舌下 + 其他途径”的用户只显示舌下 M2 分量，并提示“暂不提供合并的总 E2 浓度”（`m2_partial_scope`）。默认分支此前会给出 Legacy 合并曲线。这是有意的安全取舍，但属于能力回退，需要在 D-1 中一并确认。

### F-PK-10 [确认，默认分支] Legacy “已按化验校准”的表述强度

- 默认分支在有合格化验时，大号数字下方显示“已按化验校准 · 基于 n 次服药记录”。区间只包含参数不确定性，这一点说明写在页面底部的 `pk_assumptions`。
- “校准”会让用户以为数值已经个体化验证。建议在大号数字旁直接注明“区间不含测量误差和模型误差”，或缩小数字字号。

### F-PK-11 [确认] 计算频率和性能

- `NotesViewModel` 每 30 s 执行 `refresh()`，其中**总是**调用 `loadConcentration()`（`NotesViewModel.kt:43, 68`），无论当前在哪一页，都会重新计算一次完整的 Legacy 浓度（历史窗口 + 预测窗口 + 区间）。`ConcentrationResult` 不是 data class，每次都会产生新状态。
- PR #19 在浓度页上再叠加一次 M2 计算（只在记录变化时）。
- PR 自述的 JVM 基准是 M2 11.75 s → 0.19 s，API35 x86_64 模拟器上 60 剂 40.6 ms。物理 ARM 设备的开页耗时为 **NOT_RUN**（PR 自述，本审计也无法运行）。
- 建议：后台只在浓度/化验页或导出时计算浓度（05 ARCH-1）。

## 4. 不应修改（保护）

以下内容不应修改：Legacy `pk-params.json` 与 Engine；冻结快照 `calculator_version=1/2`；`protocol-lock.json` / `production-baseline.json`；P2 研究归档；`ResearchSublingualV01` 的候选参数；PR #19 的数值合约测试。所有建议都只涉及**显示层级和文案**，或旁路口径的一致性。

## 5. 验收建议（针对 F-PK-1/2/3）

1. 单测：在 M2-only 模式下导出含图 PDF，断言没有出现 E2_SL 的 Legacy 曲线（或该部分按 D-2 的选择显示）。
2. 单测：在 M2-only 模式下保存含估算的舌下 E2 化验，断言冻结 JSON 不含舌下 Legacy E2 值（或按 D-2 标注）；同时断言旧快照的字节不变。
3. 截图：四语 × 字号 1.0/2.0，浓度页顶部只出现一套说明。
4. 失败判据：任何旧冻结快照、旧 PDF 生成记录摘要或 `PeriodStabilityTest` 发生变化。
