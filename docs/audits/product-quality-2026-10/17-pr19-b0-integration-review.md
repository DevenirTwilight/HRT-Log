# 17 PR #19 与 B0/B1 的隔离整合审查

时间：2026-10-12。方法：在会话临时 worktree 中，以 PR #19 head `aecbd7da843abd3f4334c0979e8265a72e6c67b5` 为基础建立本地分支 `tmp/integration-pr19-b0`，`git merge --no-ff --no-commit` 审计分支 `47e2716`。
**没有推送、没有提交合并、没有修改 PR #19 分支或默认分支。** 两个父分支保持原样。

## 1. 结论

| 状态 | 说明 |
|---|---|
| **NEEDS_AUTHORIZATION** | 文本冲突只有 1 处，可以无损解决。但 PR #19 自带的 3 个测试文件把**旧守卫语义**写成了断言，与 D-13 已批准的双域规则直接矛盾。要让整合后的分支通过 CI，必须改写这些测试的预期。这属于任务书 §11 的停止条件（“合并方案会丢失……测试”），也触及用户“不得通过修改测试预期掩盖失败”的要求，因此先请示，不擅自改写。 |

D-1/D-2 依赖这次整合：
- D-2 要改的 `export/Exports.kt` 在旧守卫下**不能豁免**（只有 `ui/` 目录可豁免）。
- D-1 要改的 `ConcentrationScreen.kt` 在旧守卫下只能继续追加硬编码豁免，而这正是 D-13 要淘汰的机制。

所以 D-1/D-2 的生产代码**尚未开始**。

## 2. 差异范围（CONFIRMED_SOURCE）

| 项 | 数值 |
|---|---|
| merge-base | `f53726ff6fa52fc5c78806a1576fdb80fcf29987`（默认分支） |
| 审计分支相对 merge-base | 45 个文件 |
| PR #19 相对 merge-base | 235 个文件 |
| 两边都改的文件 | `.github/workflows/android.yml`、`docs/HANDOFF.md`、`docs/REQUIREMENTS.md`、`tools/pk-research/check_protocol.py` |
| 直接文本冲突 | 仅 `docs/REQUIREMENTS.md`：两边都在 §58 之后追加。按“PR #19 的 §59–§66 在前、审计的 §67 在后”保留，编号和时间顺序连续，无内容丢失 |
| 自动合并 | `android.yml`、`HANDOFF.md`、`check_protocol.py` 自动合并成功（需要逐段复核，见 §3） |

## 3. 语义不兼容（TESTED_LOCAL：在合并树上运行 `tools/pk-research` 全部测试）

在合并树上运行：283 项测试，**2 failures、8 errors**。全部源于同一个原因。

1. **B0 的 `check()` 不再调用 `waiver()`。** PR #19 把三段 UI 授权链（`APPROVED_NAVIGATION`、`APPROVED_ORIGINAL_UI`、`APPROVED_M2_ONLY_UI`）硬编码在 `waiver()` 里。合并后这些常量还在，但成了不会执行的代码。
2. **`ConcentrationScreen.kt` 在 B0 中属于 7 个 PK 显示文件**，需要 `display-approvals.json` 中的产品负责人批准链。当前批准链为空，所以 `check()` 报错：`PK display file changed without owner approval`。这一处导致 8 个 ERROR（含 `test_research`、`test_unified_protocol`、`test_pr19_calibration_contract`、`test_guard_two_domain` 中直接调用 `check()` 的用例）。
3. **`AppShell.kt` 在 B0 中是仅历史文件**（114 个之一），可以正常开发。PR #19 测试的断言与此冲突：
   - `test_unified_protocol.test_no_frozen_protocol_or_baseline_was_rewritten`：要求除 AppShell/ConcentrationScreen 外，164 个基线文件都与基线逐字节相同。B1 修改了 `SettingsScreen.kt`、`strings.xml`（D-13 允许的普通开发），因此 FAIL。
   - `test_unified_protocol.test_one_extra_appshell_byte_fails` 和 `test_each_navigation_authorization_field_is_exact` 等：要求 AppShell 多一个字节或改动授权字段就失败。B0 下 AppShell 已不受锁定，**预计**这些用例在其他错误修好后会失败。这是根据代码推断的，目前被第 2 点和 §3.1 的错误掩盖，**尚未实测**。
4. `test_guard_two_domain.test_reclassifying_a_research_input_as_history_only_is_caught` 在合并树上 FAIL：错误信息先命中了第 2 点，而不是预期的依赖扫描。这是测试顺序问题；补上批准链后应恢复，需要复测。

### 3.1 补上 3 条显示批准记录后的试验（只在临时 worktree）

按 §6 第 2 步在临时 worktree 中加入 3 条 `ConcentrationScreen.kt` 批准记录后，显示批准链本身**通过**。但依赖扫描暴露出两个新问题，测试仍为 2 FAIL / 8 ERROR：

- **真实的研究输入未锁定**：PR #19 新增的研究工具 `tools/pk-research/m2_only_reference.py` 读取生产文件 `pk-engine/src/main/kotlin/net/plainnotes/app/pk/experimental/ResearchSublingualV01.kt`。该文件不在基线中，也没有实时锁定。整合时必须把它加入 `live_lock_additional`（锁定当前哈希），否则 M2 数值合约所依赖的核可以无声改动。
- **守卫自引用的误报**：PR #19 把 AppShell 的授权链硬编码在 `check_protocol.py` 里，依赖扫描因此认为守卫本身依赖 AppShell.kt。应把守卫文件排除在扫描之外，或把这些常量移到数据文件。
- `test_guard_two_domain.test_pk_display_change_without_approval_fails` 的报错先命中了依赖扫描，不是预期的批准链错误。修好上面两项后需要复测。

## 4. 七项必答

| # | 问题 | 结论 |
|---|---|---|
| 1 | 直接冲突 | 仅 `docs/REQUIREMENTS.md`，已无损解决（仅在临时 worktree 中） |
| 2 | 不冲突但语义不兼容 | 见 §3：`waiver()` 硬编码链失效；PR #19 的 3 个测试文件断言旧语义 |
| 3 | 7 个 display_review 文件是否需要新的批准链 | 需要。PR #19 只改了其中的 `ConcentrationScreen.kt`，哈希链为 `f21a68d4…`（基线）→ `75152ada…`（§60）→ `96c49efe…`（§65）→ `584dc8c7…`（§65 空状态）。D-1 会再加一环，D-2 会给 `Exports.kt`（以及可能的 `LabsScreen.kt`）各开一条链 |
| 4 | 旧精确偏离记录能否沿 git 对象核验 | 能。PR #19 的 `protocol-deviations.json` 保留 F1 在首位（`commit: "d892e9b"` 为短 SHA，B0 的 `git cat-file` 可以解析，前提是对象已拉取），其余 6 条追加在后。B0 的 `check_deviations()` 校验通过（顺序、基线哈希）。这 6 条没有 `commit` 字段，因此只能核验格式，不能核验 git 历史 |
| 5 | REQUIREMENTS 是否保留 §59–§67 | 是，按 §59…§66、§67 顺序保留 |
| 6 | 冻结研究与历史快照是否无字节改变 | 是：`protocol-lock.json`、`production-baseline.json`、`results/*` 在两侧都未改；43 + 2 个实时锁定文件在合并树中通过校验（失败点都在显示批准链） |
| 7 | PR #18 的 `m2_absolute_pgml=false` 是否被改写 | 否。PR #18 不在这次合并中，PR #19 和审计分支都没有修改它 |

## 5. 新发现的守卫缺口

`experimental/UnifiedConcentrationScreen.kt` 是 PR #19 新增的文件，承载 M2 页面的主要显示逻辑（包括 `asOf = remember(records, ruleSnapshots) { now() }`）。它不在冻结基线里，因此**不属于** 43/7/114 中的任何一类，B0 守卫完全不检查它。依赖扫描只覆盖研究工具对生产文件的引用，不覆盖新增的 UI 文件。

建议在整合时把它加入 `display_review`。这需要修改分类表并更新分类锁，按 D-13 的规则同样要有记录。

## 6. 建议的整合方案（等待批准）

1. 在 PR #19 分支上合并审计分支（merge commit；不改写历史；REQUIREMENTS 按 §4 第 5 条保留）。
2. 在 `display-approvals.json` 中为 `ConcentrationScreen.kt` 追加 3 条记录，**沿用 PR #19 的偏离 id**。证据指向 `aecbd7d:docs/pk-research/p2/protocol-deviations.json`（该文件含有这些 id 和授权摘录）。人工审查时把每一环对应到 REQUIREMENTS §60、§65。守卫只能核验“证据提交中包含该 id”，不能证明是用户本人批准（HUMAN_REVIEW_REQUIRED）。
3. 守卫加固：批准记录的证据提交必须是完整 40 位 SHA；`check_protocol.py --required-commits` 列出历史域需要的全部提交，CI 据此拉取，不再手写清单。
4. 把 `pk-engine/.../experimental/ResearchSublingualV01.kt` 加入 `live_lock_additional`（研究输入，见 §3.1）；依赖扫描排除守卫自身。
5. 把 `experimental/UnifiedConcentrationScreen.kt`（以及 D-1 新增的 M2 显示文件）加入 `display_review`，重新生成分类锁；同步调整 B0 测试中的 43/7/114 数量断言（会变成 43/8+/114）。
6. **需要你批准的测试改写**（PR #19 自带测试，均为旧守卫语义）：
   - `test_no_frozen_protocol_or_baseline_was_rewritten`：从“164 个文件全部不变”改为“43 + 2 个实时锁定文件不变、显示文件只能处于已批准的状态”。
   - AppShell 相关的 4 个负向用例：AppShell 已是仅历史文件，改为对 `ConcentrationScreen.kt` 执行同样的负向检查（多一个字节、伪造授权字段、删除链条都必须失败）。
   - `APPROVED_*` 常量与 `docs/design/*-authorization.json` 的一致性测试：保留，作为历史记录核对。
   - 旧的 `waiver()` 硬编码链：保留代码和记录作为历史，不删除。
7. 然后再分别提交 D-1、D-2，每次提交都追加对应的批准记录（证据为 REQUIREMENTS §67 中的 “D-1：选择方案 B”/“D-2：选择 D2-2” 原文），并完整回归。

## 7. 复现

```bash
git worktree add -b tmp/integration-pr19-b0 <dir> aecbd7da843abd3f4334c0979e8265a72e6c67b5
cd <dir> && git merge --no-ff --no-commit 47e2716      # REQUIREMENTS.md 冲突
# 解决冲突：保留 HEAD 的 §59–§66，再接 §67
python3 -m unittest discover -s tools/pk-research -p 'test_*.py'   # 283 项：2 FAIL / 8 ERROR
```

## 8. 实施结果（2026-10-12，产品负责人按 REQUIREMENTS §69 批准后）

在 PR #19 分支 `feature/unified-concentration-20261011` 上提交，未改写历史，未合并默认分支，PR 未合并：

| 提交 | 内容 |
|---|---|
| `b649679` | 保留历史的 merge commit（父提交 `aecbd7d` + `539c52e`）。唯一冲突是 REQUIREMENTS，按 §59–§66、§67–§69 的顺序保留 |
| `343a183` | 守卫整合：分类、批准记录迁移、测试改写 |

**分类**（实际文件逐个确认，不只看数量）：
- PR #19 新增的 10 个文件改为实时锁定：M2 内核、候选表、标定、比较代码，以及 `unified-m1-python-reference.json`，其中包括 `ResearchSublingualV01.kt`。
- 7 个 M2 显示文件需要批准链，起点是 `aecbd7d` 时的哈希：`UnifiedConcentrationScreen.kt`、`ConcentrationPreview.kt`、两个历史适配器、`ResearchDisplayProtocol.kt` 等。
- 新规则：PK 源码目录下的任何未分类文件都直接失败。
- 7 条历史偏离记录用规范化哈希锁定，改字段、删除或重排都会失败。
- 依赖扫描扩展到 `tools/pk-fit` 和 PR #19 的参考/变异脚本，守卫文件自身排除在外。

**批准迁移**（逐项核对）：三次 `ConcentrationScreen.kt` 改动的哈希链 `f21a68d4 → 75152ada → 96c49efe → 584dc8c7`，分别与 git 提交 `425a7ab`、`b509acd`、`c1f49e6` 的文件内容一致。每次 diff 都只涉及显示，没有调用计算、参数、LabFit 或数据库。对应的决定是：
- `425a7ab` 对应 §60。§60 的文字在代码提交后 23 分钟才写入 `8216f30`，内容与改动一致，但记录晚于改动，**已标为需要人工核查**。
- `b509acd`、`c1f49e6` 对应 §65，写入时间（`644fe93`）早于代码。

**测试改写**（没有删除任何检查）：
- AppShell 的字节冻结测试改为两部分：确认它已是普通产品文件；确认真实导航行为由 `UnifiedConcentrationNavigationTest` 固定。
- “164 个文件全部不变”改为三项检查：锁定文件与冻结哈希一致；显示文件处于最后一次批准的状态；164 个文件仍能从 git 复现。
- 新增负向用例：未批准修改、哈希篡改、非产品负责人或短 SHA 或无关证据、伪造的追加批准、链条断裂或倒序、未分类新文件、重复登记。**每个用例都断言失败原因**；沙箱在未修改时必须先通过，避免测试因为环境不完整而“误通过”。
- 整合过程中发现并修正了一个原测试的假阳性：沙箱只复制 164 个文件，导致负向用例总是因为缺少文件而失败。

**本地回归**（`343a183`，干净树）：
- 研究与守卫测试 301/301，pk-audit 通过，scripts 测试通过。
- domain 58、pk-engine 124（含 `M2OnlyNumericalContractTest` 3/3）、importer 12、data 72、reminder 14、app 448（13 项是原有的环境性跳过），0 失败；`PeriodStabilityTest` 5/5。
- lint 0 错误；Debug 和 Release 构建通过；Release APK 声明 `343a183`、git、干净。
- `check_release_manifest.py` 通过；`check_unified_concentration.py` 通过。
- **CI**：`c294f48` 的 android.yml（push 38267754193、pull_request 38267765921）三个作业全部 success。PR #19 自带的校准回放工作流（pr19-calibration.yml）在 `c294f48` 上**失败**：它调用守卫，却没有拉取历史提交，于是按设计以 `HistoricalObjectMissing` 失败（不是静默跳过）。修复提交 `4db4770` 给该工作流加了同样的拉取步骤（本地先用浅克隆复现了失败和修复）。`4db4770` 上四个运行全部 success：android.yml push 38269882411 / pull_request 38269890606，校准回放 push 38269882393 / pull_request 38269890544。

## 9. 对照产品负责人第二份批准逐项核对（2026-10-12）

| 条件 | 证据 |
|---|---|
| 历史 164 个文件哈希可复现 | `historical_baseline()` 在 CI jvm 作业与本地均为 164/164；`test_no_frozen_protocol_or_baseline_was_rewritten` 也断言 164 |
| 43 + 2 个研究文件实时锁定 | 分类不变（43/7/114 + 2）；另加 PR #19 的 10 个 M2 内核/参考数据文件；`test_partition_is_exact…` 按文件名逐一断言，不只看数量 |
| 受保护显示文件的未授权变化必须失败 | `ConcentrationScreenApprovalChainTest`（未批准修改、哈希篡改、非负责人、短 SHA、无关证据、伪造追加、链条断裂/倒序/清空）与 `test_pk_display_change_without_approval_fails`，每个用例断言失败原因；沙箱未修改时必须先通过 |
| 偏离链、研究结果、数值合约不变 | 7 条偏离记录按规范化哈希锁定；5/5 研究结果回放逐字节一致；`M2OnlyNumericalContractTest` 3/3；PR #19 校准回放工作流 success |
| AppShell 正常开发，导航有回归测试 | AppShell 为仅历史文件；`UnifiedConcentrationNavigationTest`（真实导航到唯一浓度入口、无模型选择器）保留，并由 `test_appshell_is_ordinary_and_its_navigation_is_pinned_by_behaviour` 固定其关键断言 |
| 三次 ConcentrationScreen 变更逐项核实 | 见 §8；`display-approvals.json` 中 `review_status`：§60 对应的 `PD-2026-10-11-ORIGINAL-UI-CONCENTRATIONSCREEN` 起初为 **HUMAN_REVIEW_REQUIRED**（需求文字晚于代码 23 分钟），2026-10-12 产品负责人事后确认后改为 **OWNER_CONFIRMED**（REQUIREMENTS §72，提交 `0c3843c`；原日期、提交、哈希和时间差保留不变）；另两条为 VERIFIED_AGAINST_GIT_AND_REQUIREMENTS。测试固定该状态，不能被悄悄改为已核实 |
| 扫描 PR #19 新增依赖 | 64 个非文档文件逐一归类（§8）；PK 源码目录下的未分类文件直接失败 |
| 未删除测试、未降低断言 | 改写的测试都换成了等价或更强的检查（见 §8）；变异测试 13/13 与 D-2 手工变异 3/3 证明负向测试能发现违规 |

## 10. §60 审批的事后确认（2026-10-12）

产品负责人本人确认：2026-10-11 恢复原浓度 UI、只增加模型选择，是当时提出的要求。处理方式：
- 先在 REQUIREMENTS §72 记录确认原文（提交 `0c3843c9175ea7788038cd27ece2168cbd7a0630`），再把审批记录的 `review_status` 改为 `OWNER_CONFIRMED`，新增 `owner_confirmation`（日期 2026-10-12，“事后人工核验”，证据指向 `0c3843c`）。
- 原记录的日期 2026-10-11、证据提交 `425a7ab`、哈希 `f21a68d4… → 75152ada…`、“晚 23 分钟”的说明都原样保留，没有提前批准时间。
- 守卫新增校验：记录了 `owner_confirmation` 时，必须有日期和完整提交，且该提交的文件中包含审批 id；CI 自动拉取该提交。新增负向测试：短 SHA、无关提交、缺日期都会失败。
- CI（`b381d05`）：android.yml push 38278491154 / pull_request 38278499303，jvm、android、device-tests 全部 success；校准回放 38278491180 / 38278499243 success。
