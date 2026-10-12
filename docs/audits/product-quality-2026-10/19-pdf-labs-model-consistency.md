# 19 D-2：新 PDF 与新化验估算不再静默使用舌下 Legacy 模型

依据：REQUIREMENTS §67 第 5 项（D-2 选择 D2-2）与 §69 第 4 项。分支：PR #19 的 `feature/unified-concentration-20261011`。**PR 未合并，未签名，未发布。**

## 1. 提交

| 提交 | 内容 | 回滚 |
|---|---|---|
| `c41cb4a` | D-2 规则、显示、PDF、测试、守卫登记与批准记录 | `git revert c41cb4a` |

## 2. 行为（CONFIRMED_SOURCE + TESTED_LOCAL）

| 记录情况 | 新 PDF | 新化验估算 |
|---|---|---|
| 纯舌下 E2 | 不画 Legacy 舌下曲线和 5–95% 带；写明“舌下 E2 个体模型估算暂不可用”及原因 | 不保存 E2 值；化验详情、PDF 化验行、Visit Pack 都显示同一条“暂不可用”说明 |
| 舌下与其他途径混合 | 不画图；说明不可用，并写明“不提供合并的总 E2 估算” | 不保存总 E2；其他药物（CPA 等）的估算照常保存 |
| 纯其他途径 E2 | 与以前完全相同（照常画图） | 与以前**逐字节相同** |
| 无剂量记录 | 不出现浓度部分（与以前一致），不显示 0 | 显示原有的“无估算”空状态，不显示 0 |
| 未知途径的历史记录 | — | 不假设它不是舌下，E2 照样不保存（“未知历史不猜”） |

实现要点：
- `LabEstimate.capture` 和 `ConcentrationCalculator` 是实时锁定的研究文件，**没有修改**。新的 `SublingualEstimatePolicy` 在 `NotesViewModel` 保存路径上，在 capture 之后执行。
- 判断依据是估算自身记录的输入（`inputs[].input_snapshot` 的成分和途径），不是界面状态。
- PDF 中“混合”根据记录判断。原因是 Legacy 的舌下模型本身含吞咽的口服部分：纯舌下时模型集合里也会出现 `E2_ORAL`，用模型键判断会出错（测试中发现并修正）。
- 其他药物缺少输入（例如 CPA 缺体重）**不会**掩盖舌下不可用的说明（测试中发现并修正）。

## 3. 兼容性（不需要额外审批的理由）

- **数据库 Schema 不变**；没有迁移；Room schema 导出文件不变（CI 检查 `core/data/schemas`）。
- **持久化格式不变**：估算 JSON 的结构与字段完全相同，只是新记录的 `values` 中少了 E2 一项。`LabContext.validate` 原本就接受这种形式（缺体重等情况早已如此）。备份封装格式不变。
- **历史快照不变**：`calculator_version=1/2` 的已保存估算、历史化验、Visit Pack 数据都不重算、不改写；含有 E2 的旧估算原样显示（有专门测试）。
- “重新生成化验上下文”会产生**新的修订版本**，按新规则保存；旧修订保留。

## 4. 守卫

- 新文件 `conc/SublingualEstimatePolicy.kt` 位于 PK 源码目录，按完整性规则必须分类。它登记为“需审批的显示文件”，起点是**空文件哈希**，所以整个首个版本需要一条产品负责人批准记录。
- `LabContextSection.kt`、`Exports.kt` 各追加一条批准记录。
- 证据都是 `b2ba7f6:docs/REQUIREMENTS.md` 中的 “D-2：选择 D2-2”。需要人工核对改动是否在决定范围内。

## 5. 测试

| 测试 | 结果 |
|---|---|
| `SublingualEstimatePolicyTest`（10 项：上表 4 种情况，加舌下 + CPA、缺体重、未知历史、冻结 v1 原样显示、ViewModel 保存路径、PDF 方案） | 10/10 通过 |
| 原有 `LabEstimateTest`（含加密备份两次往返、v1/v2 读回、未知 v3 拒绝）、`LabContextUiTest`、`VisitPackTest`、`ExportTest`、翻译测试 | 通过 |
| 设备测试 `SublingualPdfAndroidTest`（4 种途径组合，Android 原生 PDF） | 只在 CI device-tests 中运行，结果见 §6 |
| 手工变异：跳过策略、静默缺失 E2、PDF 为舌下画图 | 3/3 被断言杀死 |
| 研究与守卫测试、scripts、pk-audit | 301/301，全部通过 |
| 全量回归 | 见 §6 |

## 6. 全量回归与 CI

| 运行（`c41cb4a`，干净树） | 结果 |
|---|---|
| domain / pk-engine / importer | 58 / 124（含 M2 数值合约 3/3）/ 12，全部通过 |
| data（含迁移 1→9）/ reminder / app | 72 / 14 / 510，0 失败；61 项跳过 = 13 项原有环境性跳过 + 48 项默认不运行的视觉审计；`PeriodStabilityTest` 5/5 |
| lint + Debug + Release + 两个 androidTest APK | lint 0 错误；全部构建成功；Release APK 声明 `c41cb4a`、git、干净 |
| 发布清单（无 INTERNET）/ 模型锁 / Schema 不变 | 全部通过 |
| GitHub Actions（`de64f7e`，含 `c41cb4a`） | android.yml push 38273875224 / pull_request 38273880913：jvm、android、device-tests 全部 success。device-tests 中 app 仪器测试 73 项 0 失败（含新增的 `SublingualPdfAndroidTest`，用 Android 原生 PDF 生成四种途径组合的报告）；2 项跳过是进程重启测试，由随后的 `run_disguise_restart.py` 单独运行并通过。校准回放 38273875213 / 38273880892 success |

## 7. 未验证与后续

- NOT_DEVICE_VERIFIED：没有物理 ARM 设备。
- PDF 写到用户选择的位置时是明文文件，这是已知的隐私问题（审计 04），属于后续的隐私任务；本批没有扩大范围。
- 本地单元测试环境（Robolectric）没有原生 PDF 实现，PDF 文本内容无法在单测中读取，因此 PDF 行为由纯函数测试和设备上的 PDF 生成测试共同覆盖。
