# 81 · SL-CAL-02 步骤 4：把化验校准接到浓度页

日期：2026-10-29。负责人原话「继续步骤4，把校准接到界面，都做完给apk」；依据 REQUIREMENTS §166（第 ④ 步）与 §167。分支 `feature/slcal02-ui-20261029`（建立在 UI-12 合并之后、校准引擎 PR #151 之上）。不占 `UI-*` 编号。

## 1. 结论

有舌下记录且 M2 可用时，校准开关可用（默认开，沿用 `calib_enabled`）。开启后，合计曲线为 `C = B + k·((M2−B)+N)`：`B`（24 pg/mL）只计一次、不缩放；`M2−B` 是舌下部分，`N` 是其他途径的兼容曲线；`k` 是由化验得出的一个共同系数（引擎见报告 79 与 §166）。关闭、没有化验、或化验被拒绝时，曲线与改前逐点相同。**没有改**任何模型、参数、候选、LIVE_LOCK 文件、导出、PDF、就诊资料包、化验快照、Room、备份；系数不持久化。

## 2. 页面上看到什么

- 估算卡：标题行出现「已按化验校准」标签，大数字是校准后的合计。
- 校准卡（开关 + 状态标签）：用了几次化验；信息档位（充分 / 有限，阈值 0.75 为设计选择）；「用其余化验预测其中一次，相差约 x%」（只有一次化验时写「无法互相核对」）；使用了你记录的抽血时间；降权的化验数（距上次舌下服药 <2.5 小时）；未使用的化验按原因分行计数（历史记录不足 24 小时 / 只记了日期 / 抽血前没有带时间的舌下记录 / 数值或时间无法使用）。
- 被拒绝（没有可用化验，或化验对系数说得太少）：标签「未应用」，写明原因，曲线保持未校准。
- 不画区间带（报告 79：名义 90% 区间在合成验证中只覆盖 53%–80%）。
- 没有舌下记录：仍是旧的兼容校准卡（未改）。M2 不可用：开关禁用并写明原因。

## 3. 改动文件

| 文件 | 类别 | 说明 | 批准 id |
| --- | --- | --- | --- |
| `ui/ConcentrationScreen.kt` | PK_DISPLAY_REVIEW | 校准卡 `SublingualCalibrationCard`、合计与校准结果的异步构建、新参数 `labs` | `PD-2026-10-29-SLCAL02-CONCENTRATIONSCREEN` |
| `experimental/ConcentrationPreview.kt` | 需审批（B0/§69） | 只读字段 `sublingualDoseH` | `PD-2026-10-29-SLCAL02-PREVIEW` |
| `ui/E2Calibration.kt` | 新文件 | 适配器：化验行 → 引擎输入（只读） | — |
| `ui/E2Total.kt` | UI-12 新文件 | `curve(..., calibration)`：校准后的合计 | — |
| `ui/AppShell.kt`（HISTORY_ONLY） | 仅接线 | 传 `labs = conc.labs` | — |

`ConcChart.kt`、`UnifiedConcentrationScreen.kt`、`ChartViewport.kt` 未改。批准记录追加在 `display-approvals-after-archive.json`（旧条目逐字节未动）。

## 4. 资格判定（适配器）

化验行取已保存的 E2（pg/mL 或 pmol/L，换算沿用 `LabFit.toPgMl`）。每次化验按顺序判定：数值有效 → 历史门（既有 `CalibrationEligibility`，未改，且首次服药早于化验至少 24 小时）→ 采样时间不是本地 00:00 → 上次舌下服药时间已知 → 距上次舌下服药 <2.5 小时则降权。**已知限制（E1）**：数据库没有「时间是否精确」标记，只能把恰好本地 00:00 当作日期占位而拒绝；真正区分需要 Room 迁移，另行请示。抽血前没有任何带时间的舌下服药记录的化验按「上次舌下服药时间未知」拒绝（边缘情形，宁可拒绝不猜测）。

## 5. 测试

| 测试 | 断言 |
| --- | --- |
| `E2CalibrationTest`（新，7） | 每次化验按规则判定且一个不隐藏；距上次舌下服药的小时数取自舌下服药时刻；日期占位（含时区）；校准合计 = B + k·((M2−B)+N)（1e-9），首剂之前整条曲线 = B，读数与「现在」一致；k=1 与未校准逐位相同；被拒绝时曲线与未校准逐位相同；没有化验 / 没有 M2 时无结果 |
| `Ui12ConcentrationTest`（迁移 + 新增） | 校准后的合计与卡片文字、开关开/关、混合途径、被拒绝的原因文字、M2 不可用时开关禁用；四语 × 320dp/2× 与 411dp/1× × 明暗，无被裁切文字 |
| `UnifiedConcentrationUiTest` / `UnifiedConcentrationAndroidTest`（迁移） | 「有舌下记录时开关禁用」被 §166/§167 取代；「化验不改 M2 数值」仍断言（开启校准后 `lastPreview.values` 逐位不变） |
| `TotalE2CalibrationParityTest` / `TotalE2CalibrationTest` | 随 #151，引擎与 Python 参考逐位对照与规则 |

本地：合并 UI-12 后的全量 `:app:testFullDebugUnitTest` **3451 个测试，0 失败 0 错误**（601 个按配置跳过），`lintFullDebug` 通过；`tools/pk-research` 293、`tools/pk-audit` 13、`scripts` 244 个 Python 测试通过，`delivery_ledger check` 通过。`PeriodStabilityTest` 未放宽。

## 6. 截图

`docs/ui-audit/2026-10-29-slcal02-ui/{before,after}`：before = UI-12 的「校准不可用」；after = 校准卡（已校准 / 关闭 / 混合途径 / 两次化验距服药很近）；四语 × 320dp 字号 2× / 411dp 字号 1× × 明暗。Robolectric 合成渲染，非真机。

**如实记录**：法文 + 320dp + 字号 2× 时，图表横轴的日期标签互相重叠。这是 UI-12 已有的问题，不是本步引入；本步未改图表（`ConcChart.kt` 未动），列入待办。

## 7. NOT_RUN / 待负责人复核

- 真机与人工 TalkBack：未做。
- 信息档位阈值 0.75；E1 的午夜占位规则；引擎未登记进守卫（归档不可改）。
- 本页专门的文字对比度与减少动态断言：未新增（沿用既有主题测试）。
- 导出 / PDF / 就诊资料包仍不含校准后的合计（§67 D-2，未改）。
