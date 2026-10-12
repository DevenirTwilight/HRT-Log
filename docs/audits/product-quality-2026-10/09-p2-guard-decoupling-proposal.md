# 09 P2 科研冻结守卫与产品演进解耦：设计方案（B0-DESIGN，2026-10-12）

> **状态：仅设计，`REQUIRES_APPROVAL`（D-13）。**
> 本文件没有修改 `check_protocol.py`、`test_research.py`、`production-baseline.json`、`protocol-lock.json`、`protocol-deviations.json`、`validation-protocol.md` 或任何研究结论。
> 附带的机器可读分类表 `09-p2-guard-file-map.json` 是**提议**，不是生效的配置。

基准：默认分支 `f53726ff6fa52fc5c78806a1576fdb80fcf29987`；PR #19 `aecbd7da843abd3f4334c0979e8265a72e6c67b5`；核查时间 2026-10-12T10:32Z。本地仓库已从浅克隆补全为完整历史（`git fetch --unshallow`）。

## 1. 现行机制（逐项核实）

| 事实 | 证据 | 分类 |
|---|---|---|
| `production-baseline.json` 记录 `start_sha=e89579a02da3c92a9e4976baea729af9d532437e`，锁定 **164** 个路径的 SHA-256 | 直接解析 JSON | CONFIRMED_CODE |
| 这 164 个哈希**全部**能从 git 对象 `e89579a:<path>` 逐字节复现（164/164，0 缺失，0 不一致）；`e89579a`（2026-10-09T14:29Z）是默认分支的祖先 | 本审计脚本（§7） | CONFIRMED_BEHAVIOR |
| 当前默认分支只有 1 个锁定文件与基线不同：`ui/AppShell.kt`（已登记偏离 PD-2026-10-10-F1）；PR #19 有 2 个：`AppShell.kt`、`ConcentrationScreen.kt` | 同上 | CONFIRMED_BEHAVIOR |
| 检查入口 `check_protocol.check()`，依次执行：①`protocol-lock.json` 中 6 个研究输入文件的哈希；②协议正文/数据集哈希；③对**当前工作树**逐一比对 164 个基线文件，不一致时查 `waiver()`；④研究分组、训练记录、盲法角色的一致性 | `tools/pk-research/check_protocol.py:27-50` | CONFIRMED_CODE |
| `waiver()`：只允许 `app/src/main/java/net/plainnotes/app/ui/` 下、文件名不含 Conc/Lab/Chart、`pk_relevant=false`、新旧哈希都精确匹配的偏离。其他文件抛出“永不豁免” | `check_protocol.py:11-26` | CONFIRMED_CODE |
| CI 执行条件：只有 `android.yml` 的 jvm 作业运行全部 `tools/pk-research/test_*.py`，其中 `EvidenceGuardTest.test_lock_and_production_and_immutable_old_results` 调用 `guard.check()`。每次 push/PR 都运行。其他 P2 专项工作流只跑各自的测试文件，不调用守卫 | `android.yml:21`；`test_research.py:106-107`；其余 `p2*.yml` 的 `-p` 参数 | CONFIRMED_CODE |
| `android.yml` 的 checkout 使用默认深度 1，CI 中**拿不到** `e89579a` 对象 | `android.yml` 无 `fetch-depth`（仅 `release-assets.yml` 有 `fetch-depth: 0`） | CONFIRMED_CODE |
| 研究 Python 脚本读取的唯一生产输入是 `pk-engine/src/main/resources/pk-params.json`，并且读的是**当前工作树**（`research.py:115`、`p2e_price_rosano.py:78`、`p2k_model_balance.py:13` 等），不是冻结提交 | grep | CONFIRMED_CODE |
| P0–P1 Kotlin 审计回放（`SublingualP0AuditTest` 等 + `tools/pk-audit/*_compare.py`）执行当前的 `Engine`/`LabFit`/`ConcentrationCalculator`/`CalibrationEligibility`，并与已提交的 JSON 比较 | `android.yml` android 作业 | CONFIRMED_CODE |
| 已提交的 `docs/pk-research/p2/results/analysis.json` 记录守卫自身哈希为 `88aecd64…`（9051875 与 388ef0f 中的版本），而当前 `check_protocol.py` 是 `257b0c23…`（b6262c3 的 F1 改动之后）。也就是说，**历史研究结果的代码出处早已只能靠 git 历史复现**，当前工作树并不能复现它 | `analysis.json:10`；`sha256sum` | CONFIRMED_BEHAVIOR |
| `ConcentrationCalculator`/`LabEstimate` 直接 import `HistoricalContext`、`MedicationSnapshot`、`Entities`（core/data）和 `Model.kt`（`TimelineEntry`/`SlotState`） | import 语句 | CONFIRMED_CODE |

**结论**：守卫把“研究在某一时刻的输入”实现成了“产品永远不能变”。实际上只有很少的文件会真正影响研究的复算；历史字节已经可以从 git 无损取回。

## 2. 164 个文件的重新分类（提议）

完整清单见 `09-p2-guard-file-map.json`（每个文件都有基线哈希、提议类别和理由）。

| 提议类别 | 数量 | 内容 | 当前 CI 中的处理（方案 A） |
|---|---|---|---|
| `LIVE_LOCK_RESEARCH_INPUT` | 9 | `pk-engine` 全部 6 个（`Engine`/`FittedModels`/`LabFit`/`Types`/`Units`/`pk-params.json`）；`app/conc/` 3 个（`ConcentrationCalculator`、`CalibrationEligibility`、`LabEstimate`） | **继续逐字节实时锁定**；只能经 PK 协议偏离（需另行授权）修改 |
| `LIVE_LOCK_EVIDENCE` | 34 | `docs/pk-research/results/sublingual-p0…p1c2-*` | 继续逐字节锁定（追加式证据，不应修改） |
| `PK_DISPLAY_REVIEW` | 7 | `ConcentrationScreen`、`ConcChart`、`ChartViewport`、`LabsScreen`、`LabContextSection`、`HistoricalContextDialog`、`export/Exports.kt` | 不属于研究冻结，但修改时必须附带**用户批准记录**，并通过 PK 显示回归 |
| `HISTORY_ONLY` | 114 | 其余全部：四语 `strings.xml`、`NotesViewModel`、`MainActivity`、数据层、提醒、domain、导入器、其他 UI、Schema JSON、资源等 | 不再实时锁定；由历史回放验证 `start_sha` 时的字节；当前修改走常规测试（含 Schema `git diff` 检查、迁移测试、`PeriodStabilityTest`） |
| 其中的 `indirect_pk_input` 子标记 | 4 | `HistoricalContext.kt`、`MedicationSnapshot.kt`、`Entities.kt`、`Model.kt` | 不字节锁定，但 PR 中修改它们时必须说明理由；行为变化由 P0–P1 回放比较拦截 |

分类原则：

- 没有凭“位于 pk-engine”一刀切。`pk-engine` 下的 6 个文件全部被研究脚本读取或被审计回放执行，所以保守地全部保持锁定。
- `app/` 下凡是被审计回放执行的计算路径（`conc/`）都锁定。
- “是否影响研究结论”的判据：研究脚本或已提交审计的复算会读取或执行该文件。

## 3. 方案比较

### 方案 A（推荐，待批准）：双层守卫

**A1 历史回放层**（新增）：
1. 取得 `start_sha` 对象：CI 中 `git fetch --depth=1 origin e89579a…`；如果拿不到，**输出 BLOCKED 并让作业失败**，不能静默通过。
2. 对 164 个路径，用 `git show <start_sha>:<path>` 计算 SHA-256，与 `production-baseline.json` 比对，必须 164/164。
3. 按研究实际运行的版本复算：`analysis.json` 记录 `code_revision=388ef0fc36dccf3299ff2aac62be94a8914a1f59`、`protocol_freeze_commit=9051875`。经核实，在 `388ef0f` 中 `models.py`/`research.py`/`check_protocol.py`/`test_research.py` 的 SHA-256 与 `analysis.json` 中的 `code_sha256` 四项完全一致（之后直到守卫改动之前的 20 个提交也一致）。注意 `9051875` 中还**没有** `models.py`/`research.py`。因此回放应 `git worktree add <tmp> 388ef0f`，在其中运行 `research.py`，比较 `analysis.json` 中确定性的字段（参数、比较、结论），排除 `code_revision` 等环境字段。
4. 结果写成机器可读文件（§5）。

**A2 当前产品层**（替换现行第③步）：
- `LIVE_LOCK_*`（43 个）：继续逐字节比对当前树，`waiver` 规则与现在相同（这些文件永不豁免）。
- `PK_DISPLAY_REVIEW`（7 个）：哈希变化时，必须在 `protocol-deviations.json` 中找到一条**按顺序追加**的批准记录（字段见 §6）。现有的硬编码链可以原样迁移成记录。
- `HISTORY_ONLY`（114 个）：不检查当前字节。
- 分类表本身用新增的 `guard-classification.json` 保存，并由一个新的锁文件 `guard-classification-lock.json` 固定其哈希。**不修改** `protocol-lock.json`。修改分类表等同于修改守卫，同样需要批准。

**优点**：
- 研究可复现性比现在更强：现在 `analysis.json` 记录的守卫哈希已经和当前代码对不上，A1 会真正按冻结版本复算；
- 产品可以正常修改翻译、数据层、ViewModel；
- 真正的 PK 输入仍然实时锁定。

**缺点**：
- CI 需要多拉取一个历史对象，多一个约 1 分钟的作业；
- 守卫代码变复杂；
- A1 的“确定性字段”要在实现时逐项确认（例如计时类字段要排除）。

**回滚**：只需 revert 守卫/测试/工作流的提交。`production-baseline.json`、`protocol-lock.json`、协议正文都不改，revert 后行为与现在逐字节一致。

### 方案 B：保持实时全量冻结，但正式重新冻结

把当前树作为新的基线 v2（新 `start_sha`、新的 baseline 文件），原基线保留为 v1。

- 优点：守卫逻辑不变。
- 缺点：问题会周期性复现，每次产品迭代都要重新冻结一次；资源文件仍然“永不豁免”，不能解决翻译和数据层修复；研究结果和多份基线的对应关系越来越复杂。**不推荐。**

### 方案 C：维持现状，扩大可豁免范围

在 `waiver()` 中允许非 UI 文件登记偏离。

- 优点：改动最小。
- 缺点：等于放宽守卫；每次修改都要登记精确哈希，偏离记录会膨胀到数百条；守卫的“授权”继续由改代码的同一代理写入（05 ARCH-8 的自我证明问题）。**不推荐。**

### 方案 D（A 的变体）：历史回放放在独立工作流

把 A1 放进单独的 `p2-historical-replay.yml`（`workflow_dispatch` + 每周定时 + 修改 `tools/pk-research`/`docs/pk-research` 时触发）。主 CI 只运行 A2。

- 优点：主 CI 更快。
- 缺点：历史回放失败不会挡住普通 PR。
- 可以作为 A 的实施细节由用户选择。

## 4. 负向测试清单

**必须保留的现有测试**（`test_research.py` `EvidenceGuardTest`）：
- 6 个研究输入文件任意追加 1 字节 → 失败；
- 单位/统计量/来源被篡改 → 失败；
- 偏离记录哈希不匹配、偏离文件缺失 → 失败；
- PK 文件/非 UI 文件不可豁免；
- 伪造协议哈希 → 失败；
- 研究分组、无伪盲法外测。

**新增测试（方案 A）**：

| ID | 构造 | 期望 |
|---|---|---|
| N1 | 历史回放：篡改 `production-baseline.json` 中任一哈希 | FAIL |
| N2 | 历史回放：`start_sha` 对象不可用（模拟浅克隆） | **BLOCKED（非零退出）**，不能是 PASS |
| N3 | 历史回放：用一个在某个路径内容不同的伪造提交替代 | FAIL |
| N4 | 当前层：`pk-params.json` 或 `Engine.kt` 改 1 字节 | FAIL，且无法豁免 |
| N5 | 当前层：`docs/pk-research/results/*` 改 1 字节 | FAIL |
| N6 | 当前层：`ConcentrationScreen.kt` 改动但没有批准记录 | FAIL |
| N7 | 批准记录：新哈希错误、顺序错乱、重复、缺 `approver`/`evidence_url`、文件不在 `PK_DISPLAY_REVIEW` 中 | 全部 FAIL |
| N8 | 当前层：修改 `values-zh/strings.xml`、`NotesViewModel.kt`（HISTORY_ONLY） | 当前层 PASS，**同时**历史回放仍 PASS（证明解耦且历史不变） |
| N9 | 篡改 `guard-classification.json`，把 LIVE_LOCK 文件改成 HISTORY_ONLY | FAIL（分类锁不匹配） |
| N10 | 删除一个 HISTORY_ONLY 文件 / 删除一个 LIVE_LOCK 文件 | 前者 PASS，后者 FAIL |
| N11 | 历史回放的研究复算结果与已提交 `analysis.json` 的确定性字段不一致 | FAIL |
| N12 | 研究数据泄漏：分组/训练记录被改（现有检查保留在历史层和当前层） | FAIL |

## 5. 机器可读验证契约（提议）

```json
{
  "guard_version": 2,
  "result": "PASS | FAIL | BLOCKED",
  "historical": {
    "start_sha": "e89579a02da3c92a9e4976baea729af9d532437e",
    "baseline_files": 164,
    "reproduced": 164,
    "mismatches": [],
    "blocked_reason": null,
    "research_replay": {"tool_commit": "388ef0fc36dccf3299ff2aac62be94a8914a1f59", "deterministic_fields_equal": true}
  },
  "current": {
    "classification_sha256": "<guard-classification.json 的哈希>",
    "live_locked": 43,
    "live_lock_violations": [],
    "display_review_changed": [{"file": "...", "approval_id": "..."}],
    "history_only_changed": ["..."]
  }
}
```

`BLOCKED` 必须让 CI 失败（非零退出），并在日志中写明原因。

## 6. 用户授权记录机制

每条 `PK_DISPLAY_REVIEW` 批准记录的字段：
- `id`、`file`、`previous_sha256`、`new_sha256`、`date`；
- `approver`：GitHub 登录名，这里只能是产品负责人 `DevenirTwilight`；
- `scope`：一句话说明批准了什么；
- `evidence_url`：指向用户本人的 PR 评论、Issue 评论或 REQUIREMENTS 提交的链接；
- `recorded_by`：写入记录的代理或人。

规则：
- **代理不得替用户填写 `approver`，也不得把聊天中的“建议”写成批准。**
- 守卫离线运行，无法联网核实评论内容，只能验证格式和链条。真实性靠 PR 审查时由人核对 `evidence_url`。这个局限会写进守卫文档。

## 7. 对 PR #19 的影响

- PR #19 在 `check_protocol.py` 中硬编码了 4 组 AppShell/ConcentrationScreen 授权链（05 ARCH-8）。
- 如果先合并 PR #19，再实施方案 A：
  - `AppShell.kt` 归入 `HISTORY_ONLY`，不再需要偏离；
  - `ConcentrationScreen.kt` 归入 `PK_DISPLAY_REVIEW`，PR #19 的 PD-2026-10-11/12 记录转换为批准记录，`evidence_url` 指向 REQUIREMENTS §60/§65 的提交；
  - 守卫中的硬编码常量删除。
- 如果先实施方案 A：PR #19 需要合入新的守卫，并删除它自己的硬编码，冲突集中在 `check_protocol.py` 一个文件。
- 两种顺序都不改 PR #19 的应用代码和数值测试。**建议**：先就 D-1/D-2 做出决定并完成 PR #19 的修订，再按方案 A 改守卫，避免同一文件被两次大改。

## 8. 批准后需要修改的文件（现在不改）

| 文件 | 改动 |
|---|---|
| `tools/pk-research/check_protocol.py` | 拆成 `historical_replay()` 和 `current_guard()`；`check()` 调用两者 |
| `tools/pk-research/test_research.py` | 保留现有测试；新增 N1–N12 |
| `docs/pk-research/p2/guard-classification.json`（新增） | 由 `09-p2-guard-file-map.json` 经批准后生成 |
| `docs/pk-research/p2/guard-classification-lock.json`（新增） | 分类表的哈希 |
| `docs/pk-research/p2/protocol-deviations.json` | **只追加**一条记录，说明守卫 v2 的引入、批准人和链接 |
| `.github/workflows/android.yml` | jvm 作业中显式 `git fetch` `start_sha`（e89579a）和研究运行提交（388ef0f）；或采用方案 D 新建工作流 |
| `docs/pk-research/p2/README.md` / `RESEARCH_ARCHIVAL_POLICY.md` | 追加说明，不改历史段落 |

**不改**：`production-baseline.json`、`protocol-lock.json`、`validation-protocol.md`、`evidence-catalog.json`、`split-manifest.json`、`analysis.json` 及全部研究结果。

## 9. 本节使用的核查脚本（可复跑）

```bash
git fetch --unshallow origin   # 浅克隆时
python3 - <<'EOF'
import json,hashlib,subprocess
b=json.load(open('docs/pk-research/p2/production-baseline.json'))
ok=sum(hashlib.sha256(subprocess.run(['git','show',f"{b['start_sha']}:{p}"],capture_output=True,check=True).stdout).hexdigest()==h for p,h in b['files'].items())
print(ok,len(b['files']))   # 2026-10-12：164 164
EOF
```
