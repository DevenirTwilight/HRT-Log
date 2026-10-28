# 79 · SL-CAL-01 用化验校准 E2 合计（研究与设计阶段）

日期：2026-10-28。任务书：`docs/pk-research/sl-cal/TASK_BOOK_ORIGINAL.md`；REQUIREMENTS §163；研究报告 `docs/pk-research/sl-cal/sl-cal-01-report.md`。分支 `research/sl-cal-01-20261028`。**只研究，不改任何生产行为。**

## 1. 本阶段做了什么
- 在 `tools/pk-research/sl_cal/` 新增研究代码（全部为新文件）：合成用户生成、四种校准方案（A 只校舌下、B 两系数独立、B2 两系数部分合并、C 共同系数、D 复用 P1-A 框架）、峰值附近策略（全部/排除 1.5·2.5·4 小时/delta 降权）、可辨识性分析、外部条件性诊断、资格规则参考实现、可选的本地验证脚本、汇总表生成。
- 结果（合成，机器可读）：`results/sl-cal-01-synthetic.json`（17,640 行：72 个基线单元×100 人、10 个敏感性扫描×30 人，含留一化验）、`results/sl-cal-01-identifiability.json`、`results/sl-cal-01-external-diagnostic.json`、汇总表 `results/sl-cal-01-tables.md`。种子 20261028 / 20261029。
- 测试 `tools/pk-research/test_sl_cal_01.py`（29 项）：常数与生产源码对账、M2 数值（p2x-39，Price 幅度 434.835722）、独立标量求解器对照、可辨识性、策略行为、确定性与单元顺序无关、资格规则、失败关闭输入、可选脚本不泄露不写文件、研究守卫（正向与负向）、结果文件声明、已记录结果抽样复现。

## 2. 守卫与回放结果
| 项 | 结果 |
|---|---|
| LIVE_LOCK_RESEARCH_INPUT / LIVE_LOCK_EVIDENCE 文件（55 个）逐个与冻结基线/登记哈希比对 | 全部一致（测试 `test_no_live_lock_or_frozen_file_was_touched`） |
| `verify_historical_archive.py`：历史 164 文件基线 + 5 个研究结果文件回放，225 条归档记录 | 通过（164/164，5/5 逐字节一致） |
| 研究依赖扫描：`tools/pk-research/sl_cal/*.py` 引用的生产文件都是受保护文件 | 通过；负向测试放入一个引用未保护文件的探针必须被拒绝 |
| 新文件不落在 `unclassified_scan` 前缀内（`pk-engine/src/main/`、`pk-engine/src/test/resources/`、`conc/`、`experimental/`、`export/`） | 通过，无需改 `guard-classification.json` |
| 阶段范围守卫 `phase_guard.py`：本分支改动只能是研究文件、测试、三份项目文档、审计报告 | 通过；负向测试覆盖 `app/`、`pk-engine/`、`core/`、`.github/`、`Exports.kt`、`pk-params.json`、UI 文件 |
| `docs/pk-research/README.md` | **曾被误改，已发现并还原**：它是 `research_archive.verify_records` 字节锁定的历史记录，守卫立即报错 `Archived record changed or missing`；入口改放 `docs/pk-research/sl-cal/README.md` |
| 研究 Python 全套 `unittest discover`（tools/pk-research，290 项） | 通过（还原 README 并提交后；此前仅一项失败：阶段范围守卫发现 README 被改） |
| 生产代码、`ConcentrationCalculator`、`LabFit`、`pk-engine`、`pk-params.json`、M2 候选、UI、`Exports.kt`、Room、备份 | 未改；`m2_absolute_pgml` 仍为 `false` |

## 3. 结论摘要
见 REQUIREMENTS §163 与报告第 0 节：可做、只能到整体比例校正；两个系数在单一采样类型下分不开；推荐方案 C + delta 降权 + 信息增益拒绝 + 先验 sd≈0.9；区间不可靠，不建议显示；背景基线与形状错误是校准修不了的主要误差。

## 4. NOT_RUN / 未做 / 不适用
- **负责人自己的化验：NOT_RUN**（可选，由负责人决定是否本地运行 `owner_local_check.py`；真实数据不进仓库）。
- 外部临床验证：未做、不可做；外部条件性诊断只用仓库已有汇总统计，不是验证。
- 真机、TalkBack、Android 单元与设备测试：不适用（未改任何 Android 代码），由 CI 的既有任务照常运行；完整 `:app:testFullDebugUnitTest`、lint、设备测试的 CI 结果见 HANDOFF 的合并记录。
- 按真值幅度分层的报告、多种形状偏离候选、依从性不规则、剂量比例性：未建模，已写入报告第 10 节。
- 不改 Kotlin：SL-CAL-02 另开，需负责人批准（报告第 11 节）。
