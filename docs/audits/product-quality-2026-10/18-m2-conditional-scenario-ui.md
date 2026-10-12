# 18 D-1：M2 条件性文献情景显示

依据：REQUIREMENTS §67 第 4 项（D-1 方案 B，附科学边界）与 §69 第 4 项（整合 CI 通过后实施）。
分支：PR #19 的 `feature/unified-concentration-20261011`。**PR 未合并，未签名，未发布。**

## 1. 提交

| 提交 | 内容 | 回滚 |
|---|---|---|
| `bd20a5e` | D-1 显示改动、测试、变异用例、三条显示批准记录 | `git revert bd20a5e` |

只改显示层，共 3 个受保护显示文件：`ConcentrationScreen.kt`、`UnifiedConcentrationScreen.kt`、`ConcentrationPreview.kt`（只增加一个快照时间字段）。每个文件都在 `display-approvals.json` 中追加了一环批准记录，证据为 `b2ba7f6:docs/REQUIREMENTS.md` 中的 “D-1：选择方案 B”，并注明需要人工核对改动是否在决定范围内。

以下内容**都没有修改**：M2 参数、候选（`p2x-39`）、内核、研究结果、PR #18 能力矩阵（`m2_absolute_pgml` 仍为 false）、Schema、历史记录。

## 2. 页面行为（CONFIRMED_SOURCE + TESTED_LOCAL）

| 要求 | 实现 |
|---|---|
| 取消醒目的个人“当前浓度” | M2 卡片只有标题“舌下 E2 文献情景（不是你的血药浓度）”和一段说明，**没有任何数字** |
| 一段普通用户能读懂的说明 | 卡片正文：套用 Price 1997（6 名绝经后女性平均值）的曲线形状和幅度；是条件性文献情景，不是个人血药浓度预测；不能用来判断高低或调整剂量；个人幅度、基线、重复给药和 8–24 小时长尾未经充分验证 |
| 不同时出现两套免责声明 | M2 页隐藏原 Legacy 顶部提示（“群体平均参数估算……”），该提示移到它所描述的“其他药物”卡片中 |
| 图表与点读标明情景 | 图表标题“M2 舌下 E2 文献情景曲线（非个人预测）”；每个点读显示“xxx pg/mL（情景值）”；保留 pg/mL 与 pmol/L |
| 化验不叠加 | 实测化验单独成卡（“实际化验 · 未用于 M2 拟合”），说明“不能与上方情景曲线直接比较”；不在情景曲线上画化验点 |
| 专业信息可折叠 | “研究依据”折叠区：候选、版本、B = 24、A ≈ 435 pg/mL、Price 人群、未验证项、单剂与历史窗口峰值 |
| 避免虚假精度 | pg/mL 取整数，小时保留 1 位小数（原来是 3 位小数） |
| 时间基准一致 | `ConcentrationPreview.asOfH` 是唯一的时间基准，曲线、“现在”竖线、点读和“情景计算于 HH:mm”都来自它；页面停留期间每分钟刷新一次，刷新时不弹出加载遮罩；不看此页时不计算 |
| M2 不读取化验或 LabFit | 保持不变（原有测试）；新增测试：修改化验后，情景曲线所有数值逐字节相同 |

## 3. 测试（TESTED_LOCAL）

| 测试 | 结果 |
|---|---|
| `M2ScenarioDisplayTest`（3 项：快照时间基准、化验不上图、曲线与化验和刷新时间无关） | 通过 |
| `UnifiedConcentrationUiTest`（15 项，含新增的“停留一分钟后时间基准一致”测试；原来用大号数字作对照的用例，改为读取页面实际收到的预览对象） | 通过 |
| `UnifiedConcentrationTranslationsTest`、`ConcentrationPreviewTest`、导航、数据路径、观测测试 | 通过 |
| 变异测试 `run_m2_only_mutations.py`：原有 9 个中保留 8 个；`legacy_value_relabelled_m2` 的锚点代码已随大号数字删除，换成 `m2_current_number_restored`；另新增 4 个（化验上图、Legacy 时间基准、点读不标情景、快照不刷新）。`candidate_range_as_human_90`、`sl_only_claimed_total_e2` 两个原用例的目标改为研究依据所在的 `UnifiedConcentrationScreen.kt`，还原文件列表同步补上该文件 | **13/13 都被执行的断言杀死**（不是超时或编译错误）。首轮有 2 个新用例是靠等待超时杀死的，被运行器判为不合格；已把对应测试改成显式断言后重跑 |
| `M2ScenarioVisualAuditTest`（需设 `M2_VISUAL_AUDIT=1`）：4 种语言 × 字号 1.0/1.3/2.0 × 320/411dp × 浅/深色，展开研究依据 | **48/48 无截断**；已人工查看 en、zh-rCN、zh-rTW、fr 的截图 |
| 研究与守卫测试 | 301/301 |
| 全量回归（`bd20a5e`） | 见 §5 |

调整过的原有测试和理由：
- `fixedM2KernelOwnsHero…` 改名为 `m2ShowsNoCurrentNumberAndItsCurveIsTheFixedKernel`：大号数字已按 D-1 取消；断言改为“页面上不存在该数字”，同时情景值仍等于冻结内核的独立计算值。
- 单位切换和重建测试：原来依赖卡片上的单位文字，现在改为检查点读气泡带“情景值”标记，以及 pmol/L 选项在重建后仍被选中。
- 研究依据折叠后，相关断言在展开后检查；没有删除任何检查。

## 4. 仍然存在的证据张力（必须如实记录）

PR #18 能力矩阵的 `m2_absolute_pgml=false` 表示：**个人绝对 pg/mL 预测缺乏证据**。D-1 方案 B 仍然根据个人服药记录画出有 pg/mL 单位的曲线。它降低了误读风险（没有当前值、每个数都标“情景”、化验不比较、不做高低判断），但**没有让个人预测变得可信**，也没有消除与能力矩阵的冲突。

这是产品负责人明确接受的用途定义（条件性情景），不是对能力矩阵的修改。能力矩阵保持 false。

## 5. 全量回归与 CI

| 运行 | 结果 |
|---|---|
| JVM：domain / pk-engine（含 M2 数值合约）/ importer（`bd20a5e`） | BUILD SUCCESSFUL |
| `core:data` / `core:reminder` / `app` 单测（`bd20a5e`） | data 72、reminder 14、app 500，0 失败；61 项跳过 = 13 项原有环境性跳过 + 48 项默认不运行的视觉审计（需 `M2_VISUAL_AUDIT=1`，已单独运行并通过） |
| 第一次 Release 构建 | **被 `verifyReleaseProvenance` 拒绝**：构建开始配置时，本报告还是工作树中的未跟踪文件。这是 B1 的预期行为（有未跟踪文件不允许构建正式包）。把文件移出后在干净树上重跑 |
| lint + Debug + Release + 两个 androidTest APK（干净树） | lint 0 错误；全部构建成功；Release APK 声明 `bd20a5e`、git、干净 |
| `check_release_manifest.py`、`check_unified_concentration.py`、`check_protocol.py` | 全部通过 |
| GitHub Actions | 推送后记录 |

## 6. 未验证

- NOT_DEVICE_VERIFIED：没有物理 ARM 设备。
- TalkBack：只检查了语义（卡片无数字、按钮有文字），没有用真实读屏走一遍。
- 横竖屏：Android 仪器测试中原有的旋转用例已适配，结果以 CI device-tests 为准。
