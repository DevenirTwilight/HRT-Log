# 15 B0 守卫 v2：独立 CI 最终结论与专项核验

核查时间：2026-10-12 ≈14:05Z。分支 `claude/new-session-sbje5o`。
状态：**CI_VERIFIED_ON_WORK_BRANCH**。B0 只在工作分支上，**未进入默认分支**，未合并，未发布。

## 1. S0 实时状态（TESTED_LOCAL / GitHub API）

| 项 | 实测值 |
|---|---|
| 工作树 | 干净（`git status --short` 无输出） |
| 本地 HEAD = 远端工作分支 | `f8b3d7c2498a4a3be08ea21a3975285c4efc0f83` |
| 默认分支 `claude/new-session-1959qb` | `f53726ff6fa52fc5c78806a1576fdb80fcf29987`（未变） |
| PR #19 head（`feature/unified-concentration-20261011`） | `aecbd7da843abd3f4334c0979e8265a72e6c67b5`（未变，未合并） |
| B0 代码提交 | `b2ba7f63784ea965377d39ba83b6c9a86a043475`，在工作分支上；8 个文件，+651/−5 |
| B0 之后的提交 | `c51717e`、`f8b3d7c`：都只改 `docs/` |

## 2. B0 提交的独立 CI（TESTED_CI）

Run 38257031094：<https://github.com/DevenirTwilight/HRT-Log/actions/runs/38257031094>，head_sha = `b2ba7f63784ea965377d39ba83b6c9a86a043475`，attempt 1。这次运行没有被 concurrency 取消：下一次推送（`f8b3d7c`）发生在 13:58Z，三个作业此时都已结束。

| 作业 | 结论 | 完成时间（UTC） | 关键步骤 |
|---|---|---|---|
| jvm | success | 13:39:06 | domain/pk-engine/importer 测试；“Fetch frozen P2 history objects”拉到 `e89579a`/`388ef0f`/`d892e9b`；`tools/pk-research` **Ran 273 tests … OK**；`research.py` 输出 339 个画像、`clinical_accuracy_established: false` |
| android | success | 13:46:44 | core:data/reminder/app 单测（含 `PeriodStabilityTest`）、`lintFullDebug`、Debug + Release 构建、两个 androidTest APK；舌下 P0–P1-C2 回放；发布清单（无 INTERNET）检查；Schema/PLAN 不变检查 |
| device-tests | success | 13:43:34 | API 35 x86_64 模拟器：`core:data` 与 `app` 的 connected 测试、伪装重启脚本 |

之后的 run 38259460351 对应纯文档提交 `f8b3d7c`。它证明的是该提交，**不是** B0 提交本身的独立验收。

## 3. 守卫入口：独立命令保障什么，CI 额外保障什么（CONFIRMED_SOURCE）

| 入口 | 覆盖内容 | 不覆盖 |
|---|---|---|
| `python3 tools/pk-research/check_protocol.py`（`check()`） | 协议锁、原始协议、研究分组；分类锁与 43/7/114 精确划分；43 + 2 个实时锁定文件；7 个显示文件的批准链；偏离链与 git 对象；依赖扫描；**164/164 历史基线**（从 `e89579a` 的 git 对象读取） | **不**做研究回放（5/5） |
| `python3 -m unittest discover -s tools/pk-research -p 'test_*.py'`（CI jvm 作业） | 上面全部，加上 `HistoricalReplayTest`：在 `388ef0f` 的独立 worktree 重跑研究，与 `results/` 中 5 个文件逐字节比较；以及 21 个正向/负向用例 | — |

- 5/5 回放**只靠单元测试**完成。`test_guard_two_domain.py` 里没有任何 `skip`/`skipIf`/`expectedFailure`。CI 命令用 `discover` 运行全部 `test_*.py`，没有 `-k` 过滤。
- 如果以后把 CI 命令改成过滤形式，回放就会被绕过。防线是 `android.yml` 本身的代码审查。为了保持独立命令的速度，没有把回放并入 `check()`，这是有意为之。

## 4. 分类、审批与依赖扫描复核（TESTED_LOCAL）

1. 43/7/114 互不重叠，合起来正好等于 `production-baseline.json` 的 164 个文件。2 个附加资源的 SHA 与工作树一致。
2. `guard-classification-lock.json` 的哈希与分类文件一致。**两者在同一仓库，可被一起篡改**；这只是防止无意改动，不是外部不可篡改证明（HUMAN_REVIEW_REQUIRED）。
3. 依赖扫描只认 `git ls-files` 列出的文件，`app/build/...` 等生成物不计入。
4. 未覆盖范围：
   - 扫描只看 `tools/pk-research/*.py` 和 `tools/pk-audit/*.py` 中**字面量**路径。
   - `tools/pk-fit/fit.py` 不在扫描范围。它唯一引用的生产文件 `pk-params.json` 本身已实时锁定，所以目前没有缺口，但新增的工具目录不会被自动发现。
   - 运行时拼接的路径（`os.path.join`、f-string）扫描不到。现有工具中只有 `check_protocol.py` 自身使用 `glob()`。
   - Kotlin 测试（`pk-engine/src/test`）间接读取的资源，只靠 2 个附加锁覆盖已知部分。
   - **不能声称以后的任意改动都安全。**
5. `display-approvals.json` 目前为空。守卫只能核验格式，以及“证据提交中包含批准 id”。**`approver=DevenirTwilight` 加上提交中包含 id，不能自动证明是用户本人批准**，必须人工把用户原话与具体文件差异一一对应（HUMAN_REVIEW_REQUIRED）。
6. `protocol-deviations.json` 现有 1 条（PD-2026-10-10-F1，AppShell.kt），并通过 `d892e9b` 的 git 对象核验。PR #19 分支上另有偏离记录。整合时必须**追加**保留，不能覆盖（见 17 号报告）。
7. 用例实测：改 `pk-params.json`、`LabFit.kt`、`ConcentrationCalculator.kt`、`LabEstimate.kt`（app/conc 下的两个文件属于实时锁定）或归档证据都会失败；改 `SettingsScreen.kt`、`strings.xml`（HISTORY_ONLY）时，守卫域通过，产品测试照常运行。

## 5. 证据等级

| 结论 | 等级 |
|---|---|
| 164/164 历史基线、5/5 回放、273 项研究测试 | TESTED_LOCAL + TESTED_CI |
| 负向用例逐条按预期原因拒绝 | TESTED_LOCAL（12 §9）；CI 运行同一测试文件 |
| 审批真实性、分类表与锁同仓库 | HUMAN_REVIEW_REQUIRED |
| 物理 ARM 设备 | NOT_DEVICE_VERIFIED（守卫与设备无关） |

## 6. 回滚

`git revert b2ba7f63784ea965377d39ba83b6c9a86a043475` 即可恢复旧的 164 文件守卫。文档提交可单独保留或回退。不允许强推。
