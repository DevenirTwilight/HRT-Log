# 05 架构、性能、测试、发布与维护治理（阶段 C，2026-10-12）

## 1. 架构概况

| 模块 | 职责 | 体量（字节，main） | 评价 |
|---|---|---|---|
| `:core:domain`（JVM） | 规则展开、时期、持续模式 | 小 | 纯逻辑，测试充分（58 项） |
| `:pk-engine`（JVM） | Legacy 核、LabFit；PR #19 增加 `experimental/*` | 中 | 与 Android 隔离，正确 |
| `:importer`（JVM） | Trans Memo / HRT Tracker 解析 | 小 | 12 项测试 |
| `:core:data` | Room/SQLCipher、仓库、备份、回收站、账本 | `NotesRepository.kt` 62.9 KB / 698 行 | **单一仓库类过大** |
| `:core:reminder` | 闹钟、通知、库存提醒 | 中 | 职责清晰 |
| `:app` | 一个 Activity、一个 ViewModel、全部 Compose 页面 | `NotesViewModel.kt` 28.5 KB；`AppShell.kt` 22 KB | 见下 |

依赖方向（domain ← data ← app，pk-engine 独立）是正确的。主要的结构问题集中在 app 层。

## 2. 发现

### ARCH-1 [确认，P1] 30 秒全量轮询，并在每次轮询中计算浓度

- `NotesViewModel.init` 每 30 s 调用一次 `refresh()`（`NotesViewModel.kt:43`）。`refresh()` 在一个事务里做以下事情：
  - 读取几乎所有表（全部记录、库存、评分、笔记、症状、方案、时期、化验、上下文、复诊、回收站……）；
  - 调用 `repo.calendar(...)`，以及 `repo.planned(now, now+366 天)`；
  - 运行时间线迁移规划（可能写库，见 04 D-8）；
  - 最后**无条件**调用 `loadConcentration()`，再读一遍全部记录，并在 Default 线程上运行完整的 Legacy 浓度计算（`:68, 89-103`）。
- 进入日历、历史、库存、身心、时间线、复诊等页面会再触发一次，`onResume` 也会触发（`MainActivity.onResume → model.sync() → refresh()`）。
- 影响：
  - CPU 和电量开销与历史长度成正比，在不看浓度页时也一样；
  - `ConcentrationResult` 不是 data class，每 30 s 都会产生新状态，导致浓度页和化验页重组。
- **没有实测数据**（无设备），记为“源码确认的结构问题，影响程度 UNVERIFIED”。
- 建议：
  - 改用 Room 的失效通知（`InvalidationTracker` / Flow）驱动刷新；
  - 浓度只在浓度页、化验页或导出时计算；
  - 时钟相关的状态（SOON/OVERDUE）用轻量的定时器单独更新。
- 成本 M。需要 `DataRefreshTest` 和 `PeriodStabilityTest` 保持通过。

### ARCH-2 [确认，P2] Compose 主线程上的重计算

- `AppShell.kt:80-82`：每当 `extra` 变化，就在组合阶段构建 `PeriodTimelineProjection` + `HistoryLabels`，**无论当前在哪一页**。
- `LongitudinalScreen.kt:33-35`：每 60 s 更新 `now`，导致投影重算。
- `VisitPackDialog` 的事实预览在 `remember` 中计算（`VisitsScreen.kt:184-187`）。
- `ExtraState` 是 data class，没有数据变化的轮询不会触发上述重算。这一点已核实，并据此修正了 01 的描述。
- 建议：把投影移到 ViewModel，在 Default 线程计算后作为 StateFlow 暴露。

### ARCH-3 [确认，P2] 错误被吞成空结果

- `historyLabels` 和 `recordLabels()` 都写成 `runCatching { … }.getOrDefault(emptyMap())`（`AppShell.kt:81`、`NotesViewModel.kt:227-230`）。
- 投影一旦抛异常，历史页的时期标签和 PDF/Visit Pack 中的标签会**静默消失**，用户无从得知。
- 建议：至少显示一行“时期标签暂不可用”，可以复用 `period_recognition_unavailable` 的模式。

### ARCH-4 [确认，P2] 对话框状态不可保存

`NotesApp` 用约 25 个 `remember`（不是 `rememberSaveable`）保存对话框状态（`AppShell.kt:87-114`）。旋转屏幕或进程被回收时，正在填写的服药、补录、化验对话框内容会丢失。D-5 的“旋转后重新锁屏”会叠加这个问题。

### ARCH-5 [确认，P3] 死代码和不可达分支

- `ImportedPlanDialog` / `prepareImportedLink` 没有调用方；
- `ComingSoonScreen` 分支不可达（全部入口 `ready=true`）；
- `Space.DECOY` 是旧存储，仅为升级保留（有注释）。
- 建议：删除前两项，或补上入口。删除需要回归测试，并检查 `releaseAcceptanceTest` 是否引用。

### ARCH-6 [确认，P2] 代码风格影响可维护性

大量单行超过 200～400 字符，例如 `NotesViewModel.kt:64`、`LongitudinalScreen.kt:160`、`CalendarScreen.kt:100`。普通格式化工具无法帮助阅读，diff 难以审查。PR 评审主要依赖 AI 代理。建议引入 ktlint/detekt 的**新增代码**行宽规则，不做全量重排，以免破坏协议哈希锁定的文件。

### ARCH-7 [确认，P1] 构建无法追溯到源码

- `versionCode=25` / `versionName=0.2.0` 在多次私下交付中一直没变（HANDOFF 记录了 ARM64 e5447de、Universal b6262c3、compact befd56f，以及 PR #19 的多个包）。
- 应用内（关于页）没有显示源码 SHA 或构建时间（`app/build.gradle.kts` 没有 `buildConfigField`；`BuildConfig` 在 main 中未被引用）。
- 这直接导致“不知道手机装的是哪个包”。
- 建议：
  1. 每次交付递增 `versionCode`（同证书、递增版本可正常覆盖安装）；
  2. 在 `BuildConfig` 中注入 `GIT_SHA` 和构建时间，并显示在“关于”页。
- 不改 Schema，不改签名。成本 S。
- 注意：递增 versionCode 属于“版本号变更”，需要用户授权（07 D-4）。

### ARCH-8 [风险，P2] 协议守卫越来越依赖硬编码特例

- PR #19 在 `tools/pk-research/check_protocol.py` 中硬编码了 4 组“已授权”偏离记录（含精确 SHA），并要求 `protocol-deviations.json` 与之完全一致。
- 守卫没有被普遍放宽：未登记的文件仍走原逻辑。但“授权”由修改代码的同一个代理写进守卫本身，属于**自我证明**。每次修改浓度页都要改守卫，维护成本持续上升。
- 建议：授权记录只放在追加式 JSON 里，并要求附带用户确认的出处（PR 评论链接/提交）。守卫只验证链条完整，不内嵌具体条目。属于治理层面，需决策。

### ARCH-10 [确认，P1，最高优先级治理问题] 科研冻结守卫实际上冻结了整个产品

- **证据**：
  - `docs/pk-research/p2/production-baseline.json`（start_sha `e89579a0…`，最后修改于 614ed16，2026-10-09）锁定了 **164 个文件**的 SHA-256。按目录：app/src 72、core/data 30、core/reminder 10、core/domain 8、pk-engine 6、core/ui 2、importer 2，外加研究文档 34。
  - 被锁定的不只是 PK 代码，还包括 `MainActivity.kt`、`NotesViewModel.kt`、`Exports.kt`、`AppLock.kt`、全部 `ui/*.kt`，以及**四种语言的 `strings.xml`、`colors.xml`、`themes.xml`**。
  - `tools/pk-research/test_research.py:107` 在 CI 的 jvm 作业中对**当前工作树**执行 `guard.check()`。
  - `check_protocol.py` 只允许为 `app/src/main/java/net/plainnotes/app/ui/` 下、文件名不含 Conc/Lab/Chart 的文件登记偏离。**非 UI 文件和资源文件“永不豁免”**。
- **后果**：
  - 自 2026-10-09 起，修改 ViewModel、数据层、提醒、导出或任何翻译字符串的普通产品改动，都会让 CI 失败。
  - 历史上已经发生：F1 修复导致 CI 失败（HANDOFF）。PR #19 为绕开锁定的 `strings.xml`，把新文案放进单独的 `strings_unified_concentration.xml`，并在守卫里硬编码授权（ARCH-8）。
  - 06 中的 R-03/R-05/R-08/R-11/R-12 等任务，**在现行规则下无法合规地通过 CI**。
- **根因**：研究的可复现性本应固定“研究输入在某个提交上的版本”（研究已经记录了 `start_sha`），却实现成了“禁止生产代码继续演进”。
- **建议（需决策 D-13，不得由代理自行放宽）**：
  - 只保留真正影响研究结论的锁定：`pk-params.json`、Engine/LabFit、研究数据和协议；
  - 其他生产文件改为：研究报告引用 `start_sha`，CI 从 git 历史中按该 SHA 取文件复算；
  - 或者，正式重新冻结一份新基线，并在协议偏离记录里说明理由。
  - 无论选哪种，都要保留现有的负向测试（伪造 SHA、错序、额外字节必须失败）。

### ARCH-9 [信息] 默认分支名

默认分支叫 `claude/new-session-1959qb`，是一个会话名而不是 `main`。新接手者（人或 AI）容易误以为它是临时分支。建议改名为 `main`；GitHub 会自动重定向。这会影响 CI 的分支过滤，例如 `release-assets.yml` 写死了这个分支名，需同步修改。

## 3. 测试与质量门槛

| 层 | 内容 | 本审计实测 | CI（默认分支 f53726f） | 说明 |
|---|---|---|---|---|
| JVM：domain/pk-engine/importer | 58/58/12 | **通过**（本地，0 失败 0 跳过） | success | — |
| Python 研究/协议 | 252 | **通过**（本地） | success | 含协议哈希守卫 |
| Robolectric：app/data/reminder | app 约 53 个测试文件；data 17；reminder 5 | BLOCKED（无 SDK） | success | PR #19 自述 718 项、13 项跳过：`ExportTest` 在 Robolectric 下 `assumeTrue(PdfDocument)`，`ButtonAuditTest` 需要 `BUTTON_AUDIT=1`，都是环境条件，不是隐藏失败 |
| `PeriodStabilityTest`（交付门槛） | 5 项 | BLOCKED | success（在 `testFullDebugUnitTest` 中运行） | — |
| 设备：API35 x86_64 模拟器 | data/app 仪器测试 + 伪装重启脚本 | BLOCKED | success | **模拟器，不是真机** |
| Release 验收 A–F | x86_64 模拟器 | — | 历史 success（HANDOFF） | ARM64 硬件运行 BLOCKED（无 KVM），见 HANDOFF P1 |
| 物理设备 | — | — | — | 只有一次用户手动检查（2026-10-10，ARM64 包，机型未记录）；PR #19 的 APK 未见真机反馈 |
| lint | — | BLOCKED | 历史 0 错误 / 131 警告（BACKLOG） | 未逐类复核 |

### 覆盖空洞（按功能）

- 日历“确认服药”端到端 UI 流程：没有专门的 UI 测试（`CalendarModelTest` 只测模型）。
- 库存页和库存对话框：没有 UI 测试（`SupplyLedgerTest`、`StockAlertsTest` 是逻辑测试）。
- 化验录入对话框：没有 UI 测试（`LabContextUiTest` 只覆盖上下文区块）。
- TalkBack/语义：没有测试。
- 返回键行为：没有测试。

### 证据等级用语

本审计严格区分以下状态：

- COMPILED：CI 编译通过；
- TESTED：CI 或本地测试通过；
- DEVICE_VERIFIED：模拟器通过，**不是**真机；
- SIGNED / DELIVERED：只有 HANDOFF 和 PR 的自述，本审计没有拿到 APK，无法核验；
- INSTALLED / USER_ACCEPTED：仅 2026-10-10 ARM64 包有用户手动反馈；
- RELEASED：只有 v0.2.0。

## 4. APK 体积

本容器无 SDK，**无法重建或测量**。历史测量（HANDOFF，同一 unsigned 口径）：

| 配置 | 字节数 |
|---|---|
| Universal 默认 | 23,506,758 |
| 压缩原生库 + 资源裁剪（现行正式配置） | 12,707,995 |
| PR #19 自述 | 12,768,827～12,798,836（signed） |

主要体积来自 SQLCipher 的多 ABI 原生库。现行配置已经吃掉了大部分收益，**不建议再把缩包作为优先事项**。ARM64 单 ABI 包不兼容 32 位 ARM 和 x86，HANDOFF 也建议保留 Universal。

## 5. 维护治理

1. **单一当前事实入口缺失**：
   - HANDOFF 共 1186 行，按时间倒序堆叠，顶部的 P2-AX 状态已过期（README 阶段 A）；
   - BACKLOG 的“最新状态”停在 §50；
   - 最新需求 §59–66 只在 PR #19 分支上。
   - 建议新建一个 ≤100 行的 `docs/STATUS.md`，只写：当前默认分支 SHA、开放 PR 及其作用、最新交付包（SHA/大小/对应源码）、P0/P1 清单、硬性规则链接。HANDOFF 保留为历史日志。每次合并时由代理更新 STATUS。
2. **科研与产品文档混在一起**：`docs/pk-research/` 体积大、更新频繁，`docs/pk-model.md` 的标题也被研究进度覆盖（前几个标题都是“最新状态”）。建议 `pk-model.md` 只描述现行生产模型，研究进度只写在 `pk-research/p2/README.md`。
3. **许可合规（07 D-5）**：`docs/licensing.md` 记录，公开的 v0.2.0 Release、标签和 Git 历史中仍含来自**无许可证上游**的移植代码；授权请求“尚未发出”。当前源码树已经是独立重写。v0.2.0 仍在公开分发，这是需要用户决定的合规风险。
4. **开放 PR 积压**：#1、#2、#3、#6、#10、#15、#16、#17、#18 共 9 个，其中两处叠放（#3 叠在 #2 上，#18 叠在 #17 上），多数落后默认分支 9～62 个提交。建议逐个标注“合并 / 关闭（已被取代）/ 继续”，避免新代理误把旧草稿当作当前方向。已核实：PR #17 的 head 不是 PR #19 的祖先；PR #19 自带 P2-AZ 输入副本（`docs/design/pr19-calibration-evidence/p2az-results.json`、`docs/design/unified-model-inputs/p2az-design.json`）。因此合并 #19 **不依赖** #17 先合并，但这两份副本与 #17 的原件可能分叉，合并 #17 时需要比对。
