# 22 科研验证精简工程验收（进行中）

2026-10-13；独立分支 feature/science-validation-simplification-20261013，起点 1e584285cb1e6f7a4b09a3e20585418d35a28735。B2 PR [20](https://github.com/DevenirTwilight/HRT-Log/pull/20) OPEN，未混入本分支。不是 COMPLETE；S1–S4 与最终 CI 尚待实际验收。

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
