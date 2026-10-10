# M2 舌下 E2 实验药代模型：正式 App 集成（2026-10-10）

分支：`ccr-8cffa954-5gedc0`（独立集成分支，基于主开发分支 `claude/new-session-1959qb` 的 `f69d892`）。
研究来源：`research/experimental-sl-v01` @ `c0b9b6c`（Draft PR #1，未合并）。

## 阶段 A：只读审计结果

### 1. Git 状态

- 开始时工作树干净；`HEAD = f69d892`（"docs: record adoption and private delivery of the compact universal release"），与远端 `claude/new-session-1959qb` 一致。
- 本地克隆起初是浅克隆，远端跟踪引用缓存为旧的 `95b82bc`，fetch 显示 "forced update"。补全历史（`--unshallow`）后核实：`95b82bc` 和 `ccr-5165d4ec-vof9xq`（`847df84`，visit pack）**都是** `f69d892` 的祖先，历史是线性的，没有被改写；"forced update" 只是浅克隆的缓存假象。
- APK 压缩相关提交 `7d1bea6`、`befd56f`、`6fd4df9`、`f69d892` 都在基线里，压缩工作已结束（HANDOFF 记录了 A–F 场景全部通过和正式包交付）。本分支不改动 `claude/new-session-1959qb`。
- 研究分支相对主线的合并基为 `7a45741`；之后 53 个提交，净差异**只有 17 个新增文件**，没有修改任何已有文件。

### 2. APK 压缩方案（必须保持）

- `app/build.gradle.kts` release：`isMinifyEnabled = true`、`isShrinkResources = true`、`packaging { jniLibs { useLegacyPackaging = true } }`；不做 ABI 过滤（四个 ABI 全部保留）；SQLCipher 本地库以 DEFLATED 存储、安装时解压。
- 记录的正式未签名 universal：12,707,995 bytes（HANDOFF 第 3 行）。
- 验收：`.github/workflows/release-acceptance.yml` + `scripts/release-acceptance/`（A–F 场景）；`scripts/check_release_manifest.py`（无 INTERNET 等）。
- 本次集成**不修改** `app/build.gradle.kts`、ProGuard 规则、签名、`applicationId`、版本号或数据库。

### 3. 旧浓度预测的调用路径（不改）

`AppShell.kt`（`Destination.CONCENTRATION`）→ `NotesViewModel.loadConcentration()`（读取 meds/profiles/records/labs/weight/planned/rule snapshots）→ `ConcentrationCalculator.compute(...)`（历史快照资格、180 天历史、30 天预测、化验校准）→ `Engine.simulate` + `LabFit.bands` → `ConcentrationScreen` / `ConcChart`（pg/mL）。

### 4. M2 研究代码可复用部分

- `pk-engine/.../experimental/ResearchSublingualV01.kt`：15 个冻结候选（P2-X，`loss ≤ min + 0.10`）、transit（Erlang 卷积，闭式）+ 慢 Bateman 两路输入、按 1 mg 在 1 h 的自身响应归一化、任意时间戳叠加、解析 AUC。直接复用，不改。
- `ResearchShapeComparisonV01.kt`：旧模型与 M2 的同输入相对曲线；旧模型只取 SL-E2 事件并以"最新事件的含服档、1 mg、1 h"归一化。直接复用；它按 Price 背景分层取伪损失最小的候选，这一选择规则**不**用于正式页面（见下）。
- `app/.../conc/experimental/ResearchHistoricalSlAdapter.kt`：只读资格判断。审计结论见第 6 节。
- Python 复现（`tools/pk-research/p2x_reproduce_standalone.py`）与 Kotlin 一致性测试一起带入。

### 5. P2 冻结协议对集成的限制（关键阻塞）

- `tools/pk-research/check_protocol.py` 对 `production-baseline.json` 列出的 164 个文件做 SHA-256 校验（CI `jvm` 作业运行）。**`app/src/main` 下现有的每个文件都被锁定**，包括 `AndroidManifest.xml`、`MainActivity.kt`、`NotesViewModel.kt`、`AppShell.kt`（导航抽屉）、`SettingsScreen.kt`、`ConcentrationScreen.kt`、`ConcChart.kt`、四种语言的 `strings.xml`。
- 豁免机制（`protocol-deviations.json`）只接受 `app/src/main/java/net/plainnotes/app/ui/` 下、文件名不含 Conc/Lab/Chart、且 `pk_relevant: false` 的精确哈希登记。资源、清单、ViewModel 永远不可豁免。
- 结论：**新文件**不受哈希锁约束，可以新增独立的计算模块、页面和资源文件；但要让正式 App 出现进入实验页的入口，至少要改一个被锁文件（最小的是 `AppShell.kt` 里的一个导航项）。给 PK 研究页加入口本身与药代相关，登记为 `pk_relevant: false` 不真实；改检查器放宽豁免范围则等于扩大豁免。按任务要求，**本分支不修改任何被锁文件，入口接线作为阻塞上报**，并附上隔离方案（见第 8 节）。

### 6. `ResearchHistoricalSlAdapter` 资格审计

与 `ConcentrationCalculator.compute` 的生产规则逐条对照：

| 要求 | 适配器 | 结论 |
|---|---|---|
| 分子 E2 | 快照 `molecule == "E2"` 且档案 `ester == "E2"` | 满足 |
| 途径 sublingual | 快照 `route == "SUBLINGUAL"` 且 `pk_route == "sublingual"` | 满足；口服 E2、舌下 EV、注射/贴片/凝胶都被排除 |
| 剂量有限正数 | `actual_dose` 非空、有限、> 0 | 满足 |
| 已执行 | `status ∈ {ON_TIME, LATE}`、`taken_utc` 非空 | 满足；SKIPPED、MISSED、AUTO_MISSED、计划槽位被排除 |
| 删除 | `deleted_at_utc == null` | 满足（记录以修订号原地更新，不存在旧修订行） |
| 历史快照 | `HistoricalContext.resolved`（只补缺、不覆盖；导入记录不补）+ `MedicationSnapshot.decode`，再用生产 `missingFor` | 满足；用的是给药时冻结的配置，不读药物当前设置 |
| 时间窗 | `asOf − 180 天 ≤ t ≤ asOf` | 满足；未来事件被排除 |

审计发现的差异/缺口（阶段 B 处理）：

1. 适配器不检查记录的 `medication_id` 与快照是否属于同一药物——生产也不检查，快照按 `medication_id` 解码，属同一语义，不改。
2. 适配器只返回事件，不报告被排除的数量和原因；页面需要"无有效记录"的明确提示，需新增只读的排除计数。
3. 计算核的 `require` 失败（例如 1 万条以上的超长历史）会抛异常；页面层需要捕获并显示错误，而不是让 App 崩溃。

### 7. 计算核审计要点

- 时间轴：适配器把 UTC 毫秒转成绝对小时（`epochMillis / 3.6e6`），两个模型都在同一绝对时间轴上求值，不受夏令时影响。
- 叠加：M2 对每个时间点逐剂量求和（O(网格 × 剂量 × 候选)）；比较器先截断到显示窗口前 30 天。最慢的 M2 衰减率是 `min(k_slow, k_elim) = 0.0517/h`，30 天后贡献约 e^−37，可忽略；阶段 B 用测试量化截断误差，而不是只凭推算。
- 归一化：M2 各候选除以**自身** 1 mg 在 1 h 的响应；旧模型除以"最新一次事件的含服档、1 mg、1 h"的旧模型响应。两条曲线各自无量纲，**不共享绝对尺度**，页面必须写明。
- 候选选择：研究比较器按 Price 背景分层取伪损失最小者。正式页面不把任何候选标为"最准确"：候选按 ID 排列、由用户选择，并显示全部 15 个候选在当前输入下的取值范围（"已探索参数集合的情景范围，不是 95% 置信区间"）。Price 背景只作为论文重建的敏感性假设出现在候选说明里，不作为个人基线、不可编辑。

### 8. 预期修改文件清单

全部是**新增**文件，不修改任何现有文件（`docs/HANDOFF.md`、`docs/REQUIREMENTS.md` 除外，它们不在冻结清单内）：

- 研究导入（原样）：上面列出的 17 个文件。
- `pk-engine/.../experimental/ExperimentalSlModelView.kt`（新）：页面用的只读计算入口，包含按 ID 选候选、全候选范围、截断报告和长尾标记；附 JVM 测试。
- `app/.../conc/experimental/ResearchHistoricalSlAdapter.kt`：增加只读的排除计数（不改变现有判断），附测试。
- `app/.../experimental/ExperimentalPkScreen.kt`（新）：独立页面（Legacy PK / Experimental M2 / Model comparison），自己的 Canvas 图，不复用、不改动 `ConcChart`。
- `app/src/main/res/values*/strings_experimental_pk.xml`（新，四种语言）+ 翻译完整性测试。
- Robolectric 界面测试；Debug 变体里用合成记录承载正式页面的 Activity 与模拟器测试。
- 入口（**阻塞，未应用**）：`AppShell.kt` 增加一个导航项的补丁草案，见 `docs/design/experimental-m2-entry.patch`。

### 9. 主要风险

- 入口必须改被锁文件（见第 5 节），需要产品负责人另行做协议决定。
- 用户可能把相对曲线当作 pg/mL：页面单位固定为"相对响应（无量纲）"，警示常驻，测试断言页面不出现 pg/mL。
- 长历史性能：页面计算放在后台线程，并受计算核的 1 万条上限保护。
- 无入口时，R8 会把未被引用的页面代码从 Release 中移除；资源裁剪也会去掉未引用的字符串。所以在入口接线前，Release 体积预计基本不变。

### 10. 用户提供的离线研究材料包（2026-10-10）

用户上传 `HRT_Log_M2_交给Claude_科研与集成材料包.zip`（SHA-256 `c93c24a04a367a40d4728f1e8a14c2c3ec5e0ea660db88adf6e223b0c08fd0ec`）。只在会话临时目录解压阅读，**未提交进仓库**（其中含 Price 1997 原论文扫描 PDF，有版权，且仓库已有 P2 冻结证据）。核对与读取情况：

- 包内 `05_已校验Kotlin计算核/ResearchSublingualV01.kt` 与 PR #1 `c0b9b6c` 中的同名文件逐字节一致（SHA-256 `55c62670…c527`，Git blob `166fc57d…`），也与本分支导入的文件一致。
- 已阅读的必读包：P2-X（报告）、P2-AF、P2-AG、P2-AJ、P2-AK（报告正文）。02 辅助审计与 03 早期追溯包未逐一复现，本分支也不声称复现了它们。

对集成有直接影响的结论（均为组级/读图/合成情景，不是个人数据）：

1. **P2-AF**：在兼容参数集合内，早期归一化 AUC0–8 只相差约 1.1–1.3 倍，而 24 h 谷值指数相差几十到数百倍；`k_s` 与 `k_e` 存在 flip-flop 对称。→ 页面把"距最近一次给药超过 8 小时"的曲线部分标为**长尾、形状高度不确定**；并说明 15 个冻结候选的范围**小于**这些研究里显示的长尾不确定性。
2. **P2-AG**：M2 训练伪损失接近零，但跨研究留出外推并未优于更简单的 M1。→ 页面不得暗示 Experimental M2 比 Legacy PK 更准确；两者只是不同的形状假设。
3. **P2-AJ / P2-AK**：Price 1997 Figure 1 重新读图后 1 mg 组 0–24 h 面积约 1550.6 pg·h/mL，Table 1 为 2109，差异仍未解释；低剂量组有大量遮挡，只能用区间。→ 不强行拟合 2109；Price 背景（0/6/12/18/24 pg/mL）只作为候选的"论文重建敏感性假设"显示，不能编辑，不代表用户本人背景。
4. 冻结候选、计算核和参数**一律不改**；以上结论只影响页面上的标注和说明文字。

## 阶段 B：独立集成（已实施）

全部为新增文件，另外只改了本分支自己新增的适配器、Debug 清单和 CI 的一个检查步骤；**没有修改任何 P2 锁定文件**（`check_protocol.py` 通过）。

| 文件 | 作用 |
|---|---|
| `pk-engine/.../experimental/ExperimentalSlModelView.kt` | 页面的只读计算入口。旧模型曲线直接取自 `ResearchShapeComparisonV01.compare`（同一归一化）；M2 用 `ResearchSublingualV01.coherentCandidateSeries`，按用户选择的候选 ID 取一条，另给出 15 个候选的逐点范围；窗口为过去 48 小时、无预测；窗口前 30 天回看并报告被截掉的条数；距最近一次给药 > 8 小时的点标为长尾。 |
| `app/.../conc/experimental/ResearchHistoricalSlAdapter.kt` | 新增 `audit()`：按"第一个不满足的条件"统计排除原因（未服用、删除、无时间、未来、超出 180 天、剂量无效、无法确认快照、不是舌下 E2、缺含服档）。哪些记录合格与原实现完全相同，`verifiedEvents` 改为调用它。 |
| `app/.../experimental/ExperimentalPkScreen.kt` | 实验页：常驻警示；Legacy PK（默认）/ Experimental M2 / Model comparison 三种显示；自绘 Canvas（不复用、不改 `ConcChart`）；15 个候选以 ID 芯片排列并显示速率、Price 背景假设（论文单位，不可编辑）、Rosano 上限；输入与排除计数；不能做什么的说明；"返回浓度页面"。只读内存中的记录与不可变规则快照，不访问数据库，不写任何东西，选择只保存在界面状态里。 |
| `app/src/main/res/values*/strings_experimental_pk.xml` | 四种语言 49 条文字，放在独立文件里，避免改动被锁定的 `strings.xml`；不含任何浓度单位。 |
| `app/src/debug/.../ExperimentalPkPreviewActivity.kt` + Debug 清单 | 只在 Debug 包中，用合成记录承载真实页面，供模拟器测试；`exported=false`。 |
| `scripts/check_experimental_release.py`（CI `android` 作业新增一步） | Release 合并清单无 Debug 研究页、无 INTERNET；Release APK 四个 ABI、每个 ABI 都有 `libsqlcipher.so`、原生库仍为 DEFLATED、dex 中不含 Debug 宿主类。 |

### 入口（阻塞，未应用）

`docs/design/experimental-m2-entry.patch` 是对 `AppShell.kt` 的 3 处改动草案：导航抽屉新增 "Experimental PK model"，进入时加载记录，页面上的"返回浓度页面"回到原浓度页。它会改变被锁文件的哈希，按现行协议不能在本分支应用。可选的处理方式（需要产品负责人决定）：

1. **登记一条明确标注为与药代相关的协议偏离**：在 `protocol-deviations.json` 追加 `AppShell.kt` 的精确旧/新哈希，并由负责人授权修改 `check_protocol.py`，允许"`pk_relevant: true`、只新增导航项、不改任何 PK 计算"的单独类别。这会扩大豁免范围，所以只能由负责人决定。
2. **P2 研究结束后重新冻结**：按新的协议版本生成新的基线，再接入口。
3. **维持现状**：实验页只在 Debug 包可见（Release 中无入口，R8 会移除未引用代码）。

## 阶段 C：工程验收（本地，2026-10-10）

环境：本会话容器，JDK 21、Gradle 9.3.1、Android SDK 37 / Build-Tools 37.0.0（仓库外安装）、Robolectric 运行时 jar 用 `-ProbolectricDir` 离线提供（SHA-1 与 Maven Central 一致）。Maven Central 有 429 限流，只对网络失败重试，测试失败不重试。

| 检查 | 结果 |
|---|---|
| `:pk-engine:test`（含研究测试 16 项、新视图测试 12 项） | 86 通过 / 0 跳过 / 0 失败 |
| `:core:data:testDebugUnitTest` | 72 / 0 / 0 |
| `:core:reminder:testDebugUnitTest` | 14 / 0 / 0 |
| `:app:testFullDebugUnitTest` | 417 项：404 通过 / 13 跳过（原有）/ 0 失败；`PeriodStabilityTest` 5/5、`ConcentrationCalculatorTest` 6/6、新测试 Experimental* 13 项与研究适配器 3 项全部通过 |
| Python `tools/pk-research`（含 P2-X 源到核复现） | 172 通过 |
| `check_protocol.py` | 通过（未改任何锁定文件） |
| `lintFullDebug` | 0 错误 / 132 警告（基线 131；新增 1 条是 `xpk_title` 未使用，它只被未应用的入口补丁引用） |
| `assembleFullDebug`、`assembleFullRelease`、两个 instrumentation APK | 成功 |
| `check_release_manifest.py`、`check_experimental_release.py` | 通过：无 INTERNET、Release 清单无 Debug 研究页、四个 ABI、每个 ABI 都有 `libsqlcipher.so`、原生库 DEFLATED |

首次 lint 发现 `xpk_legend_range` 中的 "95%" 被当作格式串（`StringFormatInvalid`），已在四种语言中加 `formatted="false"` 修复，未放宽任何 lint 规则。

### APK 体积（同一环境、未签名 fullRelease）

| | bytes |
|---|---:|
| 基线 `f69d892` | 12,707,919 |
| 本分支 | 12,725,439 |
| 差值 | **+17,520（+0.138%）** |

逐项比较：8 个原生库字节完全相同；`classes.dex` 解压后大小相同（R8 移除了 Release 中没有入口、不可达的实验页代码，dex 中找不到相关类名）；`resources.arsc` +17,444（四种语言 49 条新字符串，资源裁剪保守保留）；`META-INF/version-control-info.textproto` +74（构建元数据）。压缩策略、ABI、SQLCipher 都未改变。入口接线后，dex 预计会增加实验页与计算核的代码（纯 Kotlin，无新依赖）。

### Release 验收工具发现的问题（已修复）

手动触发的 release-acceptance（run 38063999138，`9290b8f`）A–F 六个场景都在"构建 APK"步骤失败：`compileFullReleaseAndroidTestKotlin` 找不到 Debug 专用的 `ExperimentalPkPreviewActivity` 和从研究分支导入的 `ExperimentalSlComparisonActivity`。原因是这两个模拟器测试放在 `androidTestFull`，它也会编进 Release 测试 APK；研究分支本身就有这个问题，只是当时没有跑 release-acceptance。修复：把两个测试原样移到变体专用的 `androidTestFullDebug`（内容不变）。本地 F 场景 exact 与 functional 两种模式均可构建，Debug 测试 APK 中仍含这两个测试。

## 入口接线：协议偏离 PD-2026-10-10-M2-ENTRY（产品负责人 2026-10-10 授权，REQUIREMENTS §56）

- 实施前核对：`AppShell.kt` 仍为已登记的 F1 状态 `00f2c283…`，与基线 `f69d892` 相同；补丁可干净应用。只应用了三处导航改动（+4/−1）。新 SHA-256 `9f7658e768b0867cac523325d8eeb10fbe57f40e220d0b4d66ba8a05940809ed`。
- `protocol-deviations.json` 追加（不改旧条目）：`pk_relevant: true`、`category: navigation_wiring_only`、冻结基线 `b8df023f…`、前一状态 `00f2c283…`（F1）、新哈希、具体 diff、原因、授权和检查规则。
- `check_protocol.py`：新增常量 `APPROVED_PK_DEVIATION`，写死 id、文件、类别和三个哈希。`pk_relevant: true` 的登记必须与它逐字段相同，并且前一状态必须是同一文件已登记的非 PK 状态，否则报错。原来针对普通非 PK UI 文件的规则不变。`production-baseline.json`、`protocol-lock.json`、协议正文、研究数据和参数都没有改动。
- 新增负向测试（`tools/pk-research/test_research.py`，在临时副本上运行）：
  - 改动 `ConcentrationCalculator.kt`、`Engine.kt`、`ConcChart.kt`、`ConcentrationScreen.kt`、`pk-params.json`、`FittedModels.kt`、P0/P1 研究结果或 `evidence-catalog.json` 时，无论是否伪造 `pk_relevant` 为 true 或 false 的登记，校验都失败。
  - `AppShell.kt` 再有任何改动、批准条目的任一字段被改、去掉前一状态登记、或把同样字段套用到其他 UI 文件，校验都失败。
  - 基线与锁文件哈希不变，且只存在一条 PK 相关登记。
  - 把检查器临时削弱成"接受任何 pk_relevant=true"后，这些测试会失败（已恢复）。
- 应用侧：抽屉"实验药代模型 / Experimental PK model"紧跟浓度页之后（`ExperimentalPkNavigationTest`，含中文）；`ExperimentalPkDataPathTest` 用真实 Room 数据库、仓库和 ViewModel 走完整数据路径。结果：读取 3 条合格的舌下 E2 记录；口服被排除；已删除记录在 DAO 层就不会进入页面；整个过程不写数据库；之后把当前药物的含服档改为 3，旧结果不变。

### 接线前最后一次 Release 验收（run 38067123854，`2f109f1`）

A、C、D、E 通过；B 和 F 只有 `exact-runtime` 中的 `everyReferencedStringResolvesWithTheSourceValueInAllFourLocales` 失败（"en: referenced string xpk_axis_max missing"）。B、F 的其余阶段都通过，包括 functional-all、进程重启、拒绝提醒权限、冷启动和重启后提醒恢复。原因：验收工具把源码里引用过的字符串都视为必须存在；未接线时 R8 删掉了不可达的实验页代码，资源裁剪随后把 `xpk_*` 字符串换成了空占位，而源码仍然引用它们。这是对"未接线"状态的真实发现，不是偶发失败。接线后这些字符串可达，本地 Release 的资源表中四种语言都有完整值（`aapt2 dump resources` 已核对）。以接线后的提交重新跑 A–F 为准。

### 接线后的 universal APK 体积（同一环境，未签名 fullRelease，真正包含 M2 页面代码）

| | bytes |
|---|---:|
| 基线 `f69d892` | 12,707,919 |
| 接线后（`070a0ea` 的工作树） | 12,759,479 |
| 差值 | **+51,560（+0.41%）** |

- `classes.dex`：压缩后 +16,355，解压后 +30,688，是实验页、计算核和适配器的代码，没有新依赖。
- `resources.arsc`：+35,144，只新增 49 条 `xpk_*` 字符串，四种语言（已用 `aapt2` 逐条核对）。
- 其余：构建元数据和 baseline profile，几十字节。
- 8 个原生库与基线逐字节相同，仍为 DEFLATED；四个 ABI（arm64-v8a、armeabi-v7a、x86、x86_64）都有 `libsqlcipher.so`；无 INTERNET；Release 清单中没有 Debug 研究页。

本地全量（接线后）：
- app 421 项：408 通过、13 项原有跳过、0 失败；PeriodStability 5/5，`DrawerScrollUiTest` 2/2。
- core:data 72、core:reminder 14；Python 研究测试 176 项。
- lint 0 错误、131 警告（与基线相同）；Debug/Release 构建、两项 Release 检查、P2 校验均通过。
