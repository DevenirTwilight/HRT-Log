# 20 PR #19 正式合并与主线验收

2026-10-12。**应用合并终态：MERGED_CI_VERIFIED。** 产品负责人本轮明确授权标准 merge commit；没有重复实施 B0/B1/D-1/D-2，没有额外应用修复，没有签名、发包或发布。

## 1. 实际身份与提交图

| 对象 | 完整身份 |
|---|---|
| 仓库 / PR | [DevenirTwilight/HRT-Log #19](https://github.com/DevenirTwilight/HRT-Log/pull/19) |
| 实际默认分支 | `claude/new-session-1959qb` |
| 合并前 base | `f53726ff6fa52fc5c78806a1576fdb80fcf29987` |
| 附件所述 head | `b381d05b5c3f12709a865b34336f28746c3da7fb`（不是最终 head） |
| 本轮重新验收的实际 head | `30b802454923d557d80c5e6bdedf6eec96e583be` |
| 远端 merge commit / 合并后主线基准 | [`4c88c1c7ecaa8ede67d7a0e432dcfbb7262d1d8f`](https://github.com/DevenirTwilight/HRT-Log/commit/4c88c1c7ecaa8ede67d7a0e432dcfbb7262d1d8f) |
| GitHub 合并时间 | `2026-10-12T17:08:10Z` |
| 第一父提交（原默认分支） | `f53726ff6fa52fc5c78806a1576fdb80fcf29987` |
| 第二父提交（被验收 PR head） | `30b802454923d557d80c5e6bdedf6eec96e583be` |
| 合并树 / PR head 树 | 两者均 `54970a48e3e1449f009cc82ac8aeb875aa3770d8` |

`30b8024` 相对 `b381d05` 仅修改 HANDOFF、STATUS、审计17三个文档，增加5行。应用、测试、工作流完全没有差异；仍重新等候 **30b8024 对应** 的完整 CI，没有冒用旧 head 的成功。

执行 GitHub 标准 merge API，`merge_method=merge`、`expected_head_sha=30b8024…`；返回 `merged=true` 和上述完整 merge SHA。随后重新读取 PR：closed/merged，merge SHA 与本地 fetched 对象一致。`git show -s --format=%P` 确认两个父提交；`git merge-base --is-ancestor <PR-head> <merge>` 返回0；`git diff --exit-code <PR-head> <merge>` 返回0。没有 squash、rebase、force push 或删除源分支。

## 2. S0 合并前门槛

- PR 当时 OPEN、非 Draft，base 为实际默认分支，mergeable=true/mergeable_state=clean。真实 PR 文件列表共288项；已核对源码变化范围、最后一提交、提交图和预计合并树。没有最后一刻应用变更或冲突。
- 仓库允许 merge commit，`delete_branch_on_merge=false`。branch API 明确 `protected=false`、required status contexts/checks为空；rulesets和适用branch rules均为空，PR reviewDecision为空。专用 protection 接口因 GitHub App权限返回403，**没有把403当成规则已验收**；上述可访问的分支与规则接口确认当前没有适用保护。没有改保护配置或绕过限制。
- 最新head的7个checks全部completed/success后才调用merge；调用前再次核对head/base未变化。
- 合并前两域守卫、302项科研/协议测试、194项JVM测试、21项工具测试均实际通过。164/164历史文件哈希、5/5历史结果逐字节回放成功。

| 合并前 CI | 实际结果 |
|---|---|
| [push 38280535241](https://github.com/DevenirTwilight/HRT-Log/actions/runs/38280535241) | jvm/android/device-tests 全 success，head30b8024 |
| [pull_request 38280543664](https://github.com/DevenirTwilight/HRT-Log/actions/runs/38280543664) | jvm/android/device-tests 全 success，head30b8024 |
| [reference 38280543634](https://github.com/DevenirTwilight/HRT-Log/actions/runs/38280543634) | reference success，head30b8024 |

`git diff --check base..head` 的非零来自既有冻结CSV的CRLF、历史patch/生成HTML的空白和测试文件EOF空行，不是集成/测试失败；本轮未整理这些字节或改科研哈希。没有发现新的冲突标记。本轮新增文档另行执行diff检查。

## 3. G1 新的默认分支 CI

以下全部是 **merge commit 4c88c1c 的 push**，不是PR阶段的旧验收。先全部完成，再开始独立文档更新，避免`cancel-in-progress`取消主线检查。

| 工作流 / 作业 | ID | 实际状态 |
|---|---:|---|
| [Android and domain checks](https://github.com/DevenirTwilight/HRT-Log/actions/runs/38282204769) | 38282204769 | completed/success |
| [jvm](https://github.com/DevenirTwilight/HRT-Log/actions/runs/38282204769/job/114921921649) | 114921921649 | completed/success |
| [android](https://github.com/DevenirTwilight/HRT-Log/actions/runs/38282204769/job/114921922035) | 114921922035 | completed/success |
| [device-tests](https://github.com/DevenirTwilight/HRT-Log/actions/runs/38282204769/job/114921921365) | 114921921365 | completed/success |
| [PR19 independent literature calibration replay](https://github.com/DevenirTwilight/HRT-Log/actions/runs/38282204802) | 38282204802 | push实际触发，completed/success |
| [reference](https://github.com/DevenirTwilight/HRT-Log/actions/runs/38282204802/job/114921920964) | 114921920964 | completed/success |

本轮下载并解析主线`jvm-test-results`、`build-results`、`device-test-results`，校验统计而不是机械复制旧数量。归档id、ZIP SHA-256、检查列表和日志摘录见[机器核验记录](20-pr19-validation-evidence.json)。CLI附件重定向下载曾403；改用GitHub连接器支持的工件下载成功，没有绕过目的地址限制，也没有公开临时下载令牌。

| 主线实际测试 | 数量 / 失败 / 跳过 |
|---|---|
| JVM domain / PK / importer | 58 / 124 / 12，总194；0失败、0错误、0跳过 |
| M2OnlyNumericalContractTest | 3/3；包含在124项PK内，不重复累计 |
| PreparedResearchHistoryTest / ResearchDisplayProtocolTest / StudyAnchorsTest | 3/3、4/4、5/5；包含在PK内 |
| 科研/两域守卫 Python | 302项通过；collector另3项通过 |
| data / reminder Android单测 | 72 / 14；0失败、0跳过 |
| app fullDebug单测 | 510项；0失败、61跳过：48项M2视觉审计需显式开关，12项ButtonAudit需显式开关，1项PDF写入受Robolectric限制 |
| PeriodStabilityTest | 5/5，0跳过；包含在app510项内 |
| LabEstimateTest / SublingualEstimatePolicyTest | 1/1、10/10；包含在app内，旧冻结兼容及D-2路径通过 |
| 旧生产PK Python审计 / scripts | 13 / 21项通过；Android日志实际执行 |
| API35 x86_64 data仪器 | 14项通过、0跳过 |
| API35 x86_64 app主套件 | 71项，0失败、2跳过；两个重启方法随后分别新进程执行，各`OK (1 test)` |

61项条件性跳过不计为本轮已通过；既有48项视觉及13次源变异的独立执行证据保留在审计18，本轮没有重做它们。主线重新执行现有数值合约和302项守卫/数学测试，包括短SHA、伪造审批、缺日期、破坏审批链与未授权变更等负向用例。没有删测试或放宽断言。

lint XML：**0 Error、174 Warning**。分类为UnusedResources106、PluralsCandidate41、UseKtx18、ApplySharedPref2、MissingQuantity2、ObsoleteSdkInt2、UnusedAttribute1、LocalContextResourcesRead1、ModifierParameter1。总数不是174个高危缺陷，未因合并批次擅自清理资源/翻译。CI的Node/setup-java弃用与runner迁移提示是环境警告，未升级工作流。

Debug、unsigned fullRelease及两个仪器测试APK构建成功。Release日志实际报告：`SOURCE_SHA=4c88c1c7ecaa8ede67d7a0e432dcfbb7262d1d8f`、`SOURCE_ORIGIN=git`、`SOURCE_DIRTY=false`。`apk_provenance.py --expect-source-sha "$GITHUB_SHA"`通过；无Git/脏树Release约束及相关7项工具回归保留。manifest检查通过、无INTERNET，schema/PLAN漂移检查通过。unsigned不是正式签名或交付。

## 4. 科研历史、审批和数据兼容

合并后在干净4c88c1c检出上独立执行`check_protocol.check()`、`historical_baseline()`、`replay_research()`、`compare_results()`，并再次执行302项科研/协议测试，全部返回0。

- 历史域：从冻结Git对象重现 **164/164** 哈希，研究结果 **5/5** 逐字节一致；不使用当前工作树替代历史。
- 当前域：43个原始锁定文件、2个原研究测试资源，以及PR19新增10个实时研究依赖保持锁定；7个原显示文件+8个新增显示文件按审批链核验；114个历史域产品文件正常开发。新增依赖中包含`ResearchSublingualV01.kt`、PreparedHistory、Extrema、代表/标定与独立参考资源。
- `production-baseline.json`、`protocol-lock.json`、原protocol、原偏离、分类和审批文件与PR head逐字节相同。原始生产PK、LabFit、ConcentrationCalculator、LabEstimate、pk-params、Room9和备份实现没有因合并改变。
- §60审批为`OWNER_CONFIRMED`，`owner_confirmation.date=2026-10-12`，证据提交`0c3843c9175ea7788038cd27ece2168cbd7a0630`。代码提交`425a7ab0c75c625c070a17b116cc4f99aa79ff0d`、书面记录`8216f309c0d883cc7b54bcd391f1351a1dc043ca`与23分钟归档延迟保持；没有提前批准时间。以上提交及`b381d05…`都是合并提交的可达祖先。
- REQUIREMENTS §§59–72保留；本轮不改这些段落，不修改历史calculator1/2、化验、Visit Pack、PDF或真实用户记录。
- PR18仍OPEN/Draft，head`68ceac2717b487029d894a76f4185cdf1f890d35`，依赖PR17；`m2_absolute_pgml.allowed=false`及`P2_C_HOLD=true`仍成立，没有改成true，也没有关闭或删除研究PR。

## 5. 独立文档与发行状态

本轮应用合并和随后文档更新分开：STATUS更新当前事实、HANDOFF追加简明交接、审计索引标明旧结论的历史身份、新增本报告/机器证据，旧交付报告只追加“新主线已合并但本轮未发包”的事实。文档提交的实际SHA和其后CI在本轮终态回执/对应GitHub checks中记录，不在尚未发生时预写success。

`docs/delivery/ledger.json`哈希不变，0个政策后新交付条目，政策前最高versionCode25，下次实际交付至少26。公开Release API元数据和附件清单合并前后完全一致。没有读取私有签名材料、签名、私下交付新正式APK、发布Release/标签或安装到手机。versionCode25/versionName0.2.0、applicationId、Schema9、PNBAK1及签名政策不变。

科学边界：M2是套用Price小样本人群假设的条件性文献情景；保留pg/mL与个人实际记录输入的用途仍与PR18能力矩阵存在证据张力，D-1降低误读并没有证明个人浓度准确。P2-C HOLD；不提供剂量建议。新PDF仍可能是用户主动导出的明文，合并没有解决B2全部隐私问题。

本地Android构建/仪器BLOCKED（无SDK/adb/设备）；GitHubAPI35x86_64已验证。**物理ARM、Release实机覆盖安装、真实数据库保留、OEM提醒、TalkBack和用户手机版本验收NOT_RUN/UNKNOWN**；不能用模拟器代替。

## 6. 后续与回滚边界

- B2明文导出提醒、TransMemo临时文件清理、备份提醒可作为下一独立批次；本轮未启动，需要后续范围授权。
- B4/B5库存预测口径、轮询、导航/无障碍、Lab Panel/医疗附件继续留在路线图。
- 正式签名交付和ARM验收另立任务，沿用原签名、递增版本并保持历史数据；不得要求卸载或清库。
- 旧公开v0.2.0授权/来源处置是独立事项，不能因当前源码合并而宣布历史授权问题自动消失。

本轮没有执行回滚。若未来确需回滚，先取得独立授权；候选`git revert -m 1 4c88c1c7ecaa8ede67d7a0e432dcfbb7262d1d8f`以第一父提交为主线，生成新提交，不重写历史。它会撤销整批B0/B1/D-1/D-2，而PR head仍留在祖先图中，随后重新merge同一head不能自动恢复，需明确撤销revert或重新应用变更。新产品开发若已前进还需重新评估冲突/兼容性，不直接执行该命令。
