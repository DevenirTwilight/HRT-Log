# 12 B0 守卫迁移：实施方案、依赖闭包与验证计划

> **状态：已实施（提交 `b2ba7f63784ea965377d39ba83b6c9a86a043475`，2026-10-12）。** 产品负责人于 2026-10-12 批准 D-13 方案 A（REQUIREMENTS §67）。实施结果见本文 §9。`test_research.py`、`production-baseline.json`、`protocol-lock.json`、`protocol-deviations.json`、`validation-protocol.md` 及全部研究产物**未修改**；修改的是 `check_protocol.py`（原 `waiver()` 保留）。
> 本文件扩展并部分取代 `09-p2-guard-decoupling-proposal.md`；09 保留为历史。逐文件矩阵见 `B0-protected-file-matrix.json`（取代 `09-p2-guard-file-map.json` 作为当前提议）。

核查基准：工作分支 `claude/new-session-sbje5o` @ `0d99591`；默认分支 `f53726f`；时间 2026-10-12 ≈11:40Z。

## 1. 旧机制的问题（已核实）

- 守卫把冻结时（`start_sha=e89579a`）的 164 个文件哈希，与**当前工作树**逐字节比对（`check_protocol.py:34-38`）。CI 在 `android.yml` 的 jvm 作业中通过 `test_research.py:107` 执行这项比对。
- 非 UI 文件和资源“永不豁免”，因此普通产品改动（翻译、ViewModel、数据层）必然让 CI 失败。
- 守卫保护的是“字节没变”，不是“研究结论没变”：
  - CI 只运行 `research.py --output /tmp/...`，**从不与已提交结果比较**；
  - 研究实际读取的两个 `pk-engine/src/test/resources/*.json` **不在**锁定范围内（见 §2）。

## 2. 实测依赖闭包

| 依赖项 | 方法 | 结论 |
|---|---|---|
| Python import | `388ef0f` 中 `models.py`/`research.py`/`check_protocol.py` 的 import 语句 | 只有标准库（argparse、csv、hashlib、itertools、json、math、pathlib、subprocess、time、dataclasses）及彼此。无 numpy/scipy |
| 读取的文件 | 静态扫描 `ROOT/…`、`directory/…` 路径 | `docs/pk-research/p2/{evidence-catalog,experiment-settings,split-manifest,protocol-lock,production-baseline}.json`、`validation-protocol.md`、`pk-engine/src/main/resources/pk-params.json`，外加守卫逐字节读取的 164 个基线文件 |
| 其他研究脚本 | 扫描 `tools/pk-research/*.py`、`tools/pk-audit/*.py`、`tools/pk-fit/*.py`、`pk-engine/src/test/**/*.kt`（共 67 个文件），查找对 114 个 HISTORY_ONLY 文件的任何路径或文件名引用 | **0 处真实引用**。2 处命中均为假阳性：`table1.json`/`v1.json` 子串，以及 `Migrations.kt` 只出现在守卫负向测试的名单里 |
| 审计回放读取的、**未被锁定**的输入 | 同一扫描 | `pk-engine/src/test/resources/non-sl-calibration-v1.json`（`sublingual_p1a_compare.py:30`、`SublingualP1ATest.kt:143`）、`pk-engine/src/test/resources/sublingual-literature-validation.json`。**现行守卫的保护空洞**，方案 A 中应加入实时锁定 |
| 生成器 | `tools/pk-fit/fit.py` 生成 `pk-params.json` | 生成器本身未锁定，但产物锁定，足够 |
| 运行环境 | `research.py` 用 `subprocess` 调 `git rev-parse` 写入 `code_revision` | 输出依赖 git 工作目录，所以回放必须在对应提交的 worktree 中运行 |
| 间接依赖（Kotlin） | `ConcentrationCalculator`/`LabEstimate` 的 import | `HistoricalContext`、`MedicationSnapshot`、`Entities`、`Model.kt` → 标记为 `indirect_pk_input`，由 P0–P1 回放比较覆盖 |

## 3. 实测回放结果

| 实验 | 结果 |
|---|---|
| 164 个基线哈希从 `e89579a` 复现 | 164/164 |
| 在 `388ef0f` 的隔离 worktree 中运行 `research.py`（Python 3.13.16） | 5/5 已提交结果文件（`analysis.json`、`candidate-parameters.json`、`output-manifest.json`、`report.md`、`study-comparisons.csv`）**逐字节一致** |
| 在当前树（0d99591）运行 `research.py` | `candidate-parameters.json`、`study-comparisons.csv` 逐字节一致；`analysis.json` 只有 `code_revision`、`code_sha256` 两个出处字段不同，其余 31 个键完全一致；`report.md`/`output-manifest.json` 随之不同 |

结论：研究的**科学内容**在当前树上不变；字节锁定 114 个产品文件对保护这一内容没有贡献。

## 4. 方案 A：双域检查

### 域 A：历史科研，强不可变

1. CI 显式 `git fetch --no-tags --depth=1 origin e89579a… 388ef0f…`。拿不到对象时作业**失败**并输出 `BLOCKED: missing historical object`；不得退回用当前树。
2. 对 164 个路径用 `git cat-file -p <start_sha>:<path>` 计算哈希，与 `production-baseline.json` 比较，必须 164/164。
3. `git worktree add <tmp> 388ef0f`，在其中运行 `research.py --output <tmp2>`，与已提交的 `docs/pk-research/p2/results/*` **逐字节**比较，必须 5/5。
4. 只读：不写回任何结果文件，不更新基线。
5. 原有检查全部保留，在历史域和当前域都执行：协议锁、协议/数据集哈希、分组一致性、训练记录、无伪盲法外测。

### 域 B：当前产品

| 类别 | 数量 | 检查方式 |
|---|---|---|
| LIVE_LOCK_RESEARCH_INPUT | 9 + 2 个新增测试资源 | 当前树逐字节等于基线（新增的 2 个用新的 `guard-classification-lock.json` 固定）；只能经 PK 协议偏离修改 |
| LIVE_LOCK_EVIDENCE | 34 | 逐字节；不允许修改 |
| PK_DISPLAY_REVIEW | 7 | 变化时须有追加式批准记录（§6） |
| HISTORY_ONLY | 114 | 不检查字节；依靠常规测试 |
| 研究内容等值（新增） | — | 当前树运行 `research.py`，与已提交结果比较，除 `code_revision`/`code_sha256` 外**全部相等** |

### 回滚

单独提交，revert 即可恢复现行守卫。`production-baseline.json` 等冻结文件始终不改。

## 5. 权限模型

- 批准记录必须引用**仓库外部可核查**的来源：用户本人在 GitHub 上的 PR/Issue 评论 URL，或用户上传指令的日期和原文摘录。记录格式见 09 §6。
- 守卫在 CI 中可以通过 `GITHUB_TOKEN` 读取评论，校验 `user.login == DevenirTwilight` 且评论正文包含记录 `id`。**这一点需要用户确认**：它让守卫依赖 GitHub API（只在 CI 中，不涉及应用）。离线运行时只能校验格式，结果标为 `APPROVAL_UNVERIFIED_OFFLINE`，不能算通过。
- 同一代理只改 `check_protocol.py` 或只写 `authorized_by` 字段，都不能构成批准：分类表锁和批准记录都必须指向外部证据。

## 6. 负向测试计划（实施后必须逐条真实执行）

| # | 任务书要求 | 构造方法 | 期望 | 现有测试覆盖 |
|---|---|---|---|---|
| 1 | 篡改冻结输入 1 字节 | 临时目录复制 P2，改 `evidence-catalog.json` | FAIL | 有（`test_all_input_mutations_fail…`） |
| 2 | 篡改冻结协议 | 改 `validation-protocol.md` / 伪造 `protocol_sha256` | FAIL | 有 |
| 3 | 篡改研究结果摘要 | 改临时副本中已提交的 `analysis.json` 一个数值，再做历史回放比较 | FAIL | **新增** |
| 4 | 源表换行/增加字节 | `study-comparisons.csv` 末尾加 `\n` | FAIL | **新增** |
| 5 | 替换 split / 已见资料冒充 LOCKED_EXTERNAL | 改 `split-manifest.json` 角色 | FAIL | 有（`Unblinded/incorrect role`） |
| 6 | 缺失历史 git 对象 | 在浅克隆、不 fetch 的情况下运行 | BLOCKED（非零退出） | **新增** |
| 7 | 错误的历史提交 SHA | 把 `start_sha` 替换成存在但不对应的提交 | FAIL（哈希不符） | **新增** |
| 8 | 当前源码冒充历史源码 | 回放函数传入当前树路径 | FAIL（检测到 worktree HEAD ≠ 388ef0f） | **新增** |
| 9 | 研究脚本间接依赖改变 | 在临时副本中修改 `non-sl-calibration-v1.json` | FAIL（新增的 2 个锁） | **新增**；现行守卫**检测不到** |
| 10 | 未授权修改 PK 参数/模型核 | `pk-params.json` / `Engine.kt` 改 1 字节 | FAIL，且无法豁免 | 部分已有（`test_pk_or_non_ui_files_can_never_be_waived`） |
| 11 | 没有可核查来源的“用户批准” | 记录缺 `evidence_url`，或 URL 指向非用户评论 | FAIL / APPROVAL_UNVERIFIED | **新增** |
| 12 | 调乱/删除追加式偏离链、伪造前后哈希 | 删除 PD-2026-10-10-F1、调换顺序、改 `new_sha256` | FAIL | 部分已有（哈希不匹配、文件缺失） |
| 13（正向） | 普通产品修改不误报 | 改 `values-zh/strings.xml`、`NotesViewModel.kt` | 当前域 PASS，历史域仍 PASS | **新增** |
| 14（正向） | 历史回放 | 388ef0f worktree | 5/5 一致 | 本文件 §3 已手工验证 |

## 7. 批准后的提交拆分

1. `test(pk-research)`：先只加负向/正向测试和历史回放函数，**不改**现有 `check()` 行为。全部测试应通过，新增测试单独运行。
2. `build(ci)`：`android.yml` jvm 作业中 fetch 两个历史对象。
3. `guard`：`check()` 切换为双域；追加 `protocol-deviations.json` 记录（引用用户批准原文）。
4. 每一步分别跑全量 CI；任何一步失败都可单独 revert。

## 8. 对 PR #19 的影响

见 09 §7，结论不变。建议顺序：先决定 D-1/D-2 → PR #19 修订 → 再实施 B0。

## 9. 实施结果（2026-10-12）

| 项 | 内容 |
|---|---|
| 提交 | `b2ba7f63784ea965377d39ba83b6c9a86a043475`（单独一个提交；回滚 = revert 该提交） |
| 新增 | `docs/pk-research/p2/guard-classification.json`（43/7/114 + 2 个新锁定资源）、`guard-classification-lock.json`、`display-approvals.json`（空的追加式批准链）、`tools/pk-research/test_guard_two_domain.py`（21 项） |
| 修改 | `tools/pk-research/check_protocol.py`（双域；`waiver()` 原样保留）、`.github/workflows/android.yml`（jvm 作业显式拉取 `e89579a`/`388ef0f`/`d892e9b` 三个提交，拉取失败即作业失败）、`docs/pk-research/p2/README.md`（追加说明）、`docs/REQUIREMENTS.md`（§67） |
| 未修改 | `production-baseline.json`、`protocol-lock.json`、`validation-protocol.md`、`protocol-deviations.json`、`results/*`、`test_research.py` 及其全部断言 |
| 与设计的差异 | 依赖扫描只认 **git 跟踪**的文件（本地构建生成的 `app/build/...` 报告不算生产文件）；批准记录的证据为“提交 + 文件中包含批准 id”，守卫离线核验，人工审查核对内容 |

### 实测

| 检查 | 结果 |
|---|---|
| 新测试 `test_guard_two_domain.py` | 21/21 PASS |
| 全部 `tools/pk-research` 测试（原 252 + 新 21） | 273/273 PASS |
| `tools/pk-audit` 测试 | PASS |
| 历史基线 | 164/164 从 `e89579a` 复现 |
| 历史研究回放 | `388ef0f` worktree，5/5 结果逐字节一致；worktree 自动清理 |
| 当前树重跑研究 | 科学字段与已提交结果一致（仅 `code_revision`/`code_sha256` 不同） |
| 模拟 CI 浅克隆、**不**拉取历史对象 | 261 项中 11 项 **ERROR**（`HistoricalObjectMissing`），退出码 1：失败，非跳过 |
| 模拟 CI 浅克隆、拉取三个提交后 | 273/273 PASS；`research.py` 正常 |

### 负向用例及其实际拒绝原因（逐条打印核对，见 08 #39）

| 用例 | 拒绝信息 |
|---|---|
| 清空偏离链 / 伪造首条 | Append-only deviation chain was reordered or truncated |
| F1 记录 new_sha256 改为全 0 | Deviation record does not match git history |
| F1 记录 baseline 哈希被改 | Deviation baseline hash does not match the frozen baseline |
| `pk-params.json` 改 1 字节 | Production or old evidence changed |
| 新锁定的测试资源改 1 字节 | Research input changed |
| `ConcentrationScreen.kt` 改动、无批准 | PK display file changed without owner approval |
| 非产品负责人或无法核验的批准 | 拒绝（3 种变体） |
| 历史提交不存在 | HistoricalObjectMissing |
| 错误的历史提交（`d892e9b`） | Historical baseline not reproduced at … AppShell.kt |
| 用当前源码冒充历史源码 | Research tools at <HEAD> are not the ones that produced the recorded results |
| 结果摘要篡改 / CSV 加换行 | Replayed research differs from recorded results |
| 分类表被改而锁未更新 | Guard classification changed without its lock |
| 分类表把研究输入改成仅历史（锁已更新） | Research tooling depends on unlocked production files |
| 研究分组泄漏 | 原检查拒绝 |
| 未改动的副本 | 通过 |

### 残留风险

1. 批准链只能离线核验“证据提交中包含批准 id”。证据文本是否真的是产品负责人的决定，仍要靠 PR 审查的人工核对。
2. 分类锁和分类表在同一仓库，有写权限的人可以同时修改两者。防线是：依赖扫描、计数断言（43/7/114）和代码审查。
3. 依赖扫描只识别工具源码中**字面量路径**引用的文件；动态拼接的路径扫描不到。当前工具中未发现这种用法。
4. 历史回放依赖 git 能取到 `388ef0f`。如果历史被改写或远端删除了这些对象，CI 会失败（这是有意设计，不会静默通过）。
5. CI 每次 push 多运行约 10–15 秒的回放。
