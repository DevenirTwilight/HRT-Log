# 22 科研验证精简工程验收（MERGED_CI_VERIFIED）

2026-10-13；独立分支 feature/science-validation-simplification-20261013，起点 1e584285cb1e6f7a4b09a3e20585418d35a28735。B2 PR [20](https://github.com/DevenirTwilight/HRT-Log/pull/20) 开发期间未混入；2026-10-13 产品负责人批准后 PR20 以 `9c3b5d6` 合入默认分支，本分支随后合并默认分支以保留 B2 的设备脚本与测试（REQUIREMENTS §78）。S1–S3 已在 `97236d7` CI 通过；合并 B2 后的最终 CI 全绿，见文末。

## S0 实际调用与迁移

| 旧校验/测试（实际调用） | 科学或产品风险 | 新归宿 |
|---|---|---|
| android.yml jvm → 全量 unittest / research.run → check_protocol.check | 将日常 UI 与历史 Git/源码锁耦合 | 常规保留轻量数学、Kotlin 数值/UI/兼容；全量方法学移至 research-deep |
| pr19-calibration.yml → test_pr19_calibration_contract → check | 科学比较被 UI 授权链阻断 | 独立 SciPy 两套参考保留深度 CI，轻量校准边界常规执行 |
| historical_baseline / git_blobs | 164 文件历史身份，缺对象不能假绿 | 独立 research_archive，深度 CI |
| replay_research / compare_results | 五个原结果逐字节可重现，不能用当前源码替代 | 独立归档命令与篡改/缺对象负样本，深度 CI |
| classification / unclassified / dependency_scan / live_lock | 当前源码任意字节/新增文件均阻断，不能证明科学正确 | 归档停用；以模型数值、单位/峰值/AUC、兼容断言替代 |
| check_display / waiver / required_commits / APPROVED_* | 显示文件逐次审批及 Git 证据下载 | 原记录/常量完整归档；日常停用；档案自身不可覆盖 |
| test_guard_two_domain HistoricalDomainTest / HistoricalReplayTest | 历史对象/错提交/结果损坏 | 深度 CI，迁移独立档案接口 |
| test_guard_two_domain CurrentScienceTest | 当前研究工具与原科学结果语义一致 | 深度 CI，保留来源字段差异明确规则 |
| test_guard_two_domain ClassificationTest / CurrentDomainTest | 分类/审批/逐字节生产锁；另含档案损坏、研究分组泄漏 | 分类/生产字节/显示审批用例退役；档案损坏/分组泄漏迁移档案与方法学测试 |
| test_unified_protocol 两个测试类 | 逐文件授权链与实时 SHA；另含研究 provenance/医学结论边界 | 授权/实时字节用例归档停用；研究来源与临床 false 保留深度；导航由真实 Kotlin 行为回归保留 |
| test_research EvidenceGuardTest | 冻结输入、分组/来源/单位与旧 UI waiver 混合 | 科学方法/旧数值保留；两项 deviation/waiver 授权门槛退役，原记录作为档案验证 |
| production-baseline / protocol-lock / 原输入输出 | 历史版本被就地重写 | 原路径不改，深度档案验证；新证据追加版本/erratum |
| guard-classification* / display-approvals / protocol-deviations | 旧决策事实不能伪造 | 原文件保留，档案只读核验，不接受新审批作为开发门槛 |

## 工程与医学边界

常规保留 domain/PK/importer/data/reminder/app、PeriodStability、D-2、旧 calculator v1/v2、lint、Debug/unsigned Release、来源/账本/无 INTERNET/Schema、设备与伪装重启。B2 关键回归属于未合并 PR20；以后整合必须保留其 import cleanup 与 restore process death 脚本及测试，不在此分支复制实现。

M2 仍为 Price 1997 n=6 条件性文献情景；P2-C HOLD，m2_absolute_pgml=false。旧化验、Visit Pack、冻结估算不自动重算。改变方程/参数/候选/标定须说明旧新版本、证据、输入输出差异、独立数值与旧快照策略；不使用文件 SHA 审批替代科学验收。不改版本号/Schema/备份/签名，不合并或交付。

## 验收记录

待 S1–S4 实际测试后填写；不沿用 B2 成绩冒称本分支通过。真实 ARM 未验收。

S1 本地：独立命令实际164/164、5/5通过，原记录逐字节核验；回放1.777秒、总2.058秒（本环境单次，非CI或性能结论）。全科研305项28.756秒通过。隔离档案analysis.json把clinical_accuracy_established改true，由明确Archived record changed断言捕获；真实depth=1克隆缺对象抛HistoricalObjectMissing，未跳过。原历史对象/重放负样本迁至research_archive，当前锁尚待S2退出；日常workflow的历史fetch待S3集中移除。

S2 当前科研入口只核验冻结协议输入与训练/外部研究分组；日常不再消费生产文件SHA、显示审批、分类白名单或Git授权提交。原两套审批/字节锁测试源移至`legacy-guard-tests/`作为停用见证；归档损坏与浅克隆负样本已迁独立测试。其余研究261项（本地7.999秒）和当前research.py合成运行通过，报告`clinical_accuracy_established=false`。静态四语/已废实验页检查保留，但移除另一个`model-source-lock.json`生产字节门槛；32×13数值对照保留，去除Python工具自身的源码哈希阻断。

S3 CI设计：Android jvm维持domain/PK/importer及32×13数值参考/四语检查，不下载审批Git提交；Android单元、lint、full Debug/unsigned Release、来源/账本/Manifest/Schema、设备与重启原步骤不删。新增science-change-gate在每个push/PR显示结果；依据真实Git差异筛选research-deep，手动总是深测。深测完整历史、科研方法、原/候选两套SciPy独立重算；旧PR19入口仅手动保留。五类触发测试UI-only=false、PK core=true、research data=true、workflow=true、docs-only=false；新PK源=true。后续B2合并时必须检查其新增设备脚本仍在Android工作流。

## S2 实际正反样本（2026-10-13，本地，`97236d7` 临时工作树，样本后逐字节还原）

| 样本 | 改动 | 新日常守卫 | research-deep | 实际测试 |
|---|---|---|---|---|
| P1 正 | `ui/ConcentrationScreen.kt` 列间距 8→10dp | 退出 0，无需任何批准/哈希文件 | 不触发 | UnifiedConcentrationUiTest 15/15 通过 |
| P2 正 | `pk-engine/.../experimental/` 新增无用途辅助文件 | 退出 0，无需分类登记 | 触发 | M2OnlyNumericalContractTest 3/3 通过 |
| N1 负 | M2 情景输出 ×1.01 | — | 触发 | M2OnlyNumericalContractTest 失败（AssertionFailedError） |
| N2 负 | 新 PDF 方案为舌下画 Legacy 曲线 | — | 不触发（Exports.kt 属普通产品文件） | SublingualEstimatePolicyTest 失败（AssertionError），常规 Android 单元测试内 |
| N3 负 | 冻结 v1 估算显示被改写为“暂不可用” | — | 触发（conc/） | SublingualEstimatePolicyTest 失败（AssertionError） |

负样本均由数值/产品语义断言拦截，而非文件哈希。N2 说明 D-2 回归不依赖深度 CI。

## 精简前后对比（同一 P1/P2 改动，旧守卫在默认分支 `1e58428` 实测）

| 项目 | 精简前 | 精简后 |
|---|---|---|
| P1 一行 UI 间距 | `check_protocol.py` 退出 1：“PK display file differs from its last approved state: ConcentrationScreen.kt”；全科研 discover 293 项中 9 失败 60 错误（26 个测试方法）。须追加显示批准记录、更新哈希链与需求文档证据提交后才能通过 | 守卫退出 0，不改任何批准/哈希文件；UI 测试 15/15 |
| P2 新 PK 辅助文件 | 旧 `check_protocol.py`、全科研 302 项与源锁检查均通过（pk-engine experimental 未纳入旧分类），即旧锁对此类新增并无科学保护 | 通过，同时由 gate 触发 research-deep 做数值/档案深测 |
| 数值回归（N1） | 依赖同一 Kotlin 数值断言 | 同一断言保留于常规 CI |
| 常规 CI 中的科研步骤 | 每次 push 下载审批 Git 对象 + 全科研 discover（本地约 41–62 秒） + research.py 合成运行 | 32×13 数值参考 + 四语检查（数秒）；gate 4 秒 |
| 深度验证 | 与日常混在一起，随任意 UI 改动运行 | research-deep 仅在 PK/科研/工作流路径变化或手动时运行；`97236d7` 实测档案/方法学约 11 秒、两套 SciPy 约 66 秒，合计约 1.5 分钟 |
| Android 主工作流 | — | `97236d7` 运行 38373891829 09:05:56→09:18:10 success；Science validation 38373891825 success |

本地计时为本环境单次结果，不作性能结论。

## 最终 CI（合并 B2 后 head `1d90612`）

| 触发 | 运行 | jvm | android | device-tests | science-change-gate | research-deep |
|---|---|---|---|---|---|---|
| push | Android 38377979046 / Science 38377978995 | success | success（09:40:21→09:54:02） | success | success（5 秒） | success（约 1.6 分钟） |
| PR #21 | Android 38378010351 / Science 38378010352 | success | success（09:40:35→09:50:48） | success | success（6 秒） | success（约 1.6 分钟） |

（`1d90612` 与文档提交 `571503e` 均全绿；`571503e` push 仅改文档，research-deep 按规则 skipped，PR 运行 research-deep success。）

## 门控自保护修复（`968783c`，2026-10-13 最终任务书）

问题：`SCIENCE_FILES` 未包含门控脚本及其测试，单独修改门控时不会触发 research-deep；推送旧 HEAD 缺失时 `rev-parse` 直接报错；注释称“未知源码均深测”与实际不符。

修复：门控脚本、其测试与 M2/PR19 两个变异脚本加入深测范围（workflow 目录原本即触发）；新分支、旧 HEAD 缺失、PR 基线不可解析、手动及未知事件一律全量深测；`git diff` 本身失败仍使作业失败，不静默跳过。注释改为真实策略：普通 UI、翻译、项目文档不触发，由常规 Android 测试覆盖。未恢复任何哈希冻结、审批链或分类机制。

测试（`scripts/test_science_change_gate.py`，9 项，本地通过）：路径矩阵（门控脚本/测试、science 与 Android workflow、PK 内核与参数、科研数据与参考文件、新增 PK 源（pk-engine 与 app conc）、UI 间距、翻译、项目文档、普通产品脚本、空改动、混合改动、前缀不误匹配）；真实临时 Git 仓库验证 push 多提交范围、PR 以合并基点计算（基线后续提交不计入）、新分支/全零/缺对象/缺基线/手动/未知事件全量、命令写入 `deep=` 输出、diff 失败抛错。变异：删去门控自保护两项 → 3 项失败；缺对象静默返回空 → 1 项失败。本地另跑历史档案 164/164、5/5，全科研 Python、32×13 参考、四语、M2 契约 3/3、PeriodStability 5/5、D-2 10/10、LabEstimate 1/1 通过。

CI（`968783c`）：push Android 38381299493、Science 38381299642；PR Android 38381303849、Science 38381303857，jvm/android/device-tests/science-change-gate/research-deep 全 success。push 运行中 research-deep 实际被触发，证明仅改门控文件即深测。

合并：依据 REQUIREMENTS §79 授权，在本文档提交 CI 全绿且 PR HEAD 未变后以 merge commit 合并；合并 SHA 与默认分支 CI 记录于 HANDOFF。未签名、未交付、未发布。未验证：真实 ARM。

## 合并后验收

PR #21 在 head `0cc22db`（十项检查全 success）以 merge commit `d5dc9e5` 合入默认分支。默认分支 CI：Android 38385723304（jvm、android 含 unsigned Release/Manifest/账本/Schema、device-tests 含 B2 导入清理重启与恢复进程死亡脚本）全 success；Science 38385723291 science-change-gate 与 research-deep（档案 12 秒、两套 SciPy 66 秒）success。科研守卫改造至此结束，不再扩展。
