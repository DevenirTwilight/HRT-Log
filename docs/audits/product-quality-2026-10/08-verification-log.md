# 08 验证日志（阶段 A，2026-10-12）

环境：Claude Code 云容器，Linux x86_64，JDK 21.0.12，Gradle wrapper 9.3.1，Python 3（系统）。**无 Android SDK、无 KVM、无模拟器、无物理设备。** 工作分支 `claude/new-session-sbje5o`，源码等同 `f53726f`（审计文档除外）。

| # | 命令 | 退出状态 | 结果 |
|---|---|---|---|
| 1 | `git status --short`、`git fetch origin --prune`、`git rev-parse` | 0 | 工作树开工时干净；默认分支 HEAD f53726f；本地与远端一致 |
| 2 | `./gradlew -PjvmOnly test`（第 1 次） | 1 | 插件解析失败（缓存了 Kotlin 插件“不存在”的负结果）；`curl` 同一 URL 返回 200 → 环境问题，不是项目缺陷 |
| 3 | `./gradlew -PjvmOnly help --refresh-dependencies` | 0 | 插件解析恢复 |
| 4 | `./gradlew -PjvmOnly test`（第 2–5 次） | 1 | Maven Central 返回 **HTTP 429 Too Many Requests**，每次下载更多依赖；按退避重试 |
| 5 | `./gradlew -PjvmOnly test`（第 6 次） | **0** | `:core:domain` **58/58**、`:pk-engine` **58/58**、`:importer` **12/12** 通过，0 失败 0 跳过（JUnit XML 汇总） |
| 6 | `python3 -m unittest discover -s tools/pk-research -p 'test_*.py'`（与 `android.yml:21` 相同） | **0** | **252** 项通过（含协议锁/冻结守卫测试） |
| 7 | `python3 -I tools/pk-research/check_protocol.py` | 0 | 打印协议锁 JSON（PRE_FIT，`protocol_deviations: []`）；守卫的实际拦截由第 6 项单测执行 |
| 8 | GitHub API：默认分支 `android.yml` 运行 | — | run 38137617053（f53726f）**success** |
| 9 | GitHub API：PR #19 check runs（head aecbd7d） | — | 7 个 check run 全 success（android×2、jvm×2、device-tests×2、reference） |

## NOT_RUN / BLOCKED

| 项目 | 状态 | 原因 |
|---|---|---|
| `:app:testFullDebugUnitTest`（含 `PeriodStabilityTest`）、`:core:data`、`:core:reminder` 单测 | **BLOCKED（本地）** | 无 Android SDK；只能引用 CI（第 8、9 项） |
| lint、assembleFullDebug/Release、APK 体积 | **BLOCKED（本地）** | 同上 |
| 仪器测试 / 截图 / TalkBack | **BLOCKED** | 无模拟器/设备 |
| PR #19 私下交付 APK 的 SHA/证书核验 | **NOT_RUN** | 审计不持有 APK，也不访问签名材料 |
| 用户手机安装版本 | **UNKNOWN** | 需产品负责人提供 |

# 阶段 B–D 补充（2026-10-12）

阶段 B–D 只做源码阅读、git 比较和 GitHub API 读取，**没有运行新的 Android 测试**（仍为 BLOCKED）。

| # | 命令/操作 | 结果 |
|---|---|---|
| 10 | `git diff --stat f53726f aecbd7d -- . ':!docs'` 及受保护 UI 文件的完整 diff | PR #19：64 个非文档文件，+5081/−28；`NotesViewModel`/`export`/`LabEstimate`/数据层未改 |
| 11 | `git show aecbd7d:<M2 源码>`（UnifiedConcentrationScreen、ConcentrationPreview、StudyAnchors、ResearchDisplayProtocol、PreparedResearchHistory、ResearchSublingualV01） | 已阅读，见 03 |
| 12 | `git show 68ceac2:docs/pk-research/p2/p2ba-evidence-use/p2ba-results.json` | 能力矩阵已提取（`m2_absolute_pgml=false` 等） |
| 13 | Python 脚本：比较四语 `strings.xml` 键集合 | en/zh/zh-Hant/fr 各 1069 键，缺失 0 |
| 14 | Python 脚本：解析 `production-baseline.json` | 164 个被锁定的路径 |
| 15 | `grep` 扫描 `@Ignore`/`assumeTrue` | 只有 `ExportTest`（PdfDocument）和 `ButtonAuditTest`（环境变量开关）是条件跳过 |
| 16 | `git ls-files` 扫描密钥/数据库/APK/备份扩展名 | 只有 `app/debug.keystore`（标准调试密钥） |
| 17 | GitHub API `search_repositories user:DevenirTwilight` | `HRT-Log` public，`-` private |
| 18 | `git merge-base --is-ancestor <PR17 head> <PR19 head>` | 否；PR #19 自带 P2-AZ 输入副本 |
| 19 | 读取仓库已提交截图 `docs/ui-audit/calendar_month_days_zh-rCN_2.0.png`、`2026-10-08-stock-after.png` | 前者是修复前的历史记录；后者显示历史包装列表 |

仍然 BLOCKED/NOT_RUN：所有 Android 单测/仪器测试/lint/构建/APK 体积；新截图；TalkBack；物理设备；性能计时；PR #19 交付 APK 的核验。

# S0 / B0-DESIGN / B1（2026-10-12，用户 B1 指令）

| # | 命令 | 结果 |
|---|---|---|
| 20 | `git fetch --prune`；GitHub PR 列表 | 默认 f53726f、PR #19 aecbd7d、PR #18 68ceac2、审计分支 d4aadd1，均未变化 |
| 21 | `git fetch --unshallow`；按 `start_sha` 复现 164 个基线哈希 | 164/164（本地原为浅克隆，补全前 0/164，是“对象缺失”而不是“不一致”） |
| 22 | 当前树/PR #19 与基线的差异 | 默认分支 1 个（AppShell，已有偏离）；PR #19 2 个 |
| 23 | 研究工具哈希回溯 | `analysis.json` 的 code_revision 388ef0f 与四个工具文件哈希一致 |
| 24 | 安装 Android SDK（platform 37、build-tools 37.0.0）+ 仅本会话的 Maven Central 镜像 init 脚本（Central 返回 429） | 成功；仓库文件无变化 |
| 25 | `:app:testFullDebugUnitTest` | 400 项：1 项 error（Robolectric 下载网络失败）→ 单独重跑 5/5 通过；13 项条件跳过；PeriodStability 5/5；BuildProvenance 3/3 |
| 26 | `:core:data`/`:core:reminder` 单测、`lintFullDebug`、`assembleFullDebug`、`assembleFullRelease`、`check_release_manifest.py` | 全部 PASS；lint 0 错误 / 131 警告；Release 12,708,171 B（加 keep 规则后） |
| 27 | 无 `.git` 源码副本：`verifyReleaseProvenance` / Debug BuildConfig / `-PsourceSha` 合法与非法注入 | FAIL（预期）/ unknown / PASS / FAIL（预期） |
| 28 | `python3 -m unittest discover -s tools/pk-research` | 252 项 PASS |

物理设备、模拟器、截图仍为 NOT_RUN/BLOCKED（容器无 KVM）。

# 下一阶段任务书（2026-10-12 11:24Z 起）

| # | 命令 | 结果 |
|---|---|---|
| 29 | `git fetch --prune`；PR 列表；CI 作业列表 | 分支/PR 均未变；CI 38241151594 最终 success（jvm/android/device-tests） |
| 30 | `git worktree add 388ef0f` → `python3 tools/pk-research/research.py --output …`（Python 3.13.16） | 5/5 结果文件与已提交 `docs/pk-research/p2/results/*` 逐字节一致 |
| 31 | 当前树运行 `research.py` | 只有 `code_revision`、`code_sha256` 不同 |
| 32 | 扫描 67 个研究/审计/拟合/测试源码，查找对 114 个 HISTORY_ONLY 文件的引用 | 0 处真实引用（2 处假阳性已核实） |
| 33 | B1 场景矩阵 S1–S7（干净克隆 @0d99591） | 全部符合预期（见 13 §4） |
| 34 | 干净克隆全量：domain/pk-engine/importer/data/reminder/app 单测、lint、Debug、Release、manifest 检查、P2 守卫 | 全部 PASS；app 405 项/13 跳过 |
| 35 | `scripts/apk_provenance.py` 读取 Release | origin=git、dirty=false、SHA=0d99591…；apk_sha256 bfa58c96…；unsigned |
| 36 | 临时应用关于页补丁 v2 + 临时 Robolectric 测试；运行守卫测试 | 测试 1/1 PASS；守卫 FAIL（SettingsScreen.kt 被锁定，符合预期）；已还原 |
| 37 | GitHub Actions android.yml（加固后代码） | run 38244314171 @1e7b79d：cancelled（被下一次推送按 concurrency 取消）；run 38244330717 @5b2993d：jvm/android/device-tests 全部 success |
