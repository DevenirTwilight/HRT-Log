# HRT Log 产品质量审计（2026-10）

> 性质：只读审计文档。本目录不包含任何生产代码、Schema、PK 参数、签名或发布变更。
> 状态：**阶段 A–D 全部完成（2026-10-12）。** 审计分支 `claude/new-session-sbje5o`，未开 PR、未合并。

## 快速结论

- **优势**：本地优先、多层隐私（SQLCipher + Keystore、Argon2id 锁与备份、伪装、通知中性化、无网络）；历史不可改写（快照、追加修订、数据库触发器、回收站）；时期引擎有稳定性门槛测试；四语完整；PK 证据边界意识强。
- **最大瓶颈（治理）**：科研冻结守卫锁定了 164 个生产文件（含全部 `strings.xml`、ViewModel、数据层），非 UI 文件和资源“永不豁免”。普通产品改进无法合规通过 CI（05 ARCH-10，决策 D-13）。
- **最高产品风险**：PR #19 默认显示由个人历史算出的 M2 绝对 pg/mL 大号数字，与 PR #18 能力矩阵 `m2_absolute_pgml=false` 冲突（03 F-PK-4，D-1）；PDF 图表与化验冻结估算仍走 Legacy，与页面口径不一致（F-PK-1/2）。
- **P0**：没有经证实的数据/安全/崩溃类 P0；有 1 项合规类 P0 候选（v0.2.0 许可，D-5）。
- **用户可感知的 P1**：返回键直接退出；没有备份提醒；构建无法追溯到源码（这也是无法确定手机安装版本的原因）；库存三种口径。
- **最推荐的第一项任务**：B1 构建可追溯（06 §5）。D-13 决定后再做 B0 守卫范围调整，以解锁其余批次。

## 1. 审计基准

| 项目 | 实测值 | 证据 |
|---|---|---|
| 审计日期 | 2026-10-12 | 会话日期 |
| 默认分支 | `claude/new-session-1959qb` | `git remote show origin` → HEAD branch |
| 默认分支 HEAD | `f53726ff6fa52fc5c78806a1576fdb80fcf29987`（2026-10-11 14:00 +0200，Merge PR #14 P2-AX） | `git rev-parse origin/claude/new-session-1959qb` |
| 默认分支 CI | Android and domain checks run 38137617053 **success**（f53726f） | GitHub Actions API |
| 审计工作分支 | `claude/new-session-sbje5o`，起点 = f53726f；工作树开工时干净 | `git status --short` 为空 |
| 应用版本（默认分支源码） | versionCode **25** / versionName **0.2.0**，minSdk 26，target/compileSdk 37 | `app/build.gradle.kts:4-6` |
| Room Schema | **9**（schemas 1–9 均已导出） | `core/data/.../NotesDatabase.kt:3`，`core/data/schemas/` |
| 模块 | `:core:domain` `:pk-engine` `:importer`（JVM）；`:app` `:core:data` `:core:reminder` `:core:ui`（Android） | `settings.gradle.kts` |
| 工具链 | Gradle wrapper 9.3.1；本容器 JDK 21.0.12 | `gradle-wrapper.properties`，`java -version` |
| 本容器 Android SDK | **无**（`ANDROID_HOME` 为空，无 sdk 目录） → Android 模块构建/测试本地 **BLOCKED** | `ls` |
| KVM / 模拟器 / 真机 | 无 → 设备测试本地 **BLOCKED**，只能引用 GitHub CI 记录 | — |
| 公开 Release | 仅 `v0.2.0`（2026-10-06） | GitHub Releases API |

## 2. 必须区分的“版本”

| 层面 | 当前状态 | 说明 |
|---|---|---|
| 默认分支源码 | f53726f，Build25/0.2.0/Schema9，浓度页为 Legacy 正式实现 | 不含 PR #19 的 M2-only 显示 |
| PR #19 最新源码 | head `aecbd7da843abd3f4334c0979e8265a72e6c67b5`（2026-10-12 09:33 UTC），**OPEN、未合并**，mergeable_state=clean，48 commits，235 文件（其中非 docs 64 个，+5081/−28）；已包含 f53726f | 版本号/Schema/Gradle 配置相对默认分支**无变化**；修改受保护的 `AppShell.kt`、`ConcentrationScreen.kt` 与守卫 `tools/pk-research/check_protocol.py`（PR 自述以偏离链授权，阶段 C 需复核） |
| PR #19 CI（head aecbd7d） | android / jvm / device-tests / reference 七个 check run 全部 success | `pull_request_read get_check_runs` |
| 私下交付 APK（PR #19 文档自述） | 源码 `e98d5e0`，12,798,836 B，SHA-256 `d9d874e8…5969`，原正式证书 | 仅为 PR 描述及其分支 REQUIREMENTS §66 的**自述**；本审计**未取得 APK，未核验** |
| 默认分支文档记录的最近正式包 | `hrt-log-build25-full-universal-compact-official-befd56f.apk` 12,753,780 B | HANDOFF“正式配置改为压缩原生库+资源裁剪”节，**未本地核验** |
| 公开 Release | v0.2.0（早于上述全部私下交付） | — |
| 用户手机已装版本 | **UNKNOWN**（文档仅记录 2026-10-10 ARM64 包手动验收；之后多次私下交付是否安装、安装哪一个无记录） | 需产品负责人确认 |

注意：Build 号在多次私下交付间一直是 25，**同一 Build25 对应多个不同源码/APK**（ARM64 e5447de、Universal b6262c3、compact befd56f、PR #19 系列多包）。只看版本号无法区分安装的是哪一包。这本身是一个可追溯性问题，列入后续报告。

## 3. 开放 PR 与分支依赖（2026-10-12 实测）

| PR | 状态 | base | 说明 |
|---|---|---|---|
| #19 | OPEN（非草稿），CI 全绿 | 默认分支 | M2-only 浓度显示；含 §59–§66 新需求文档（仅在该分支） |
| #18 | Draft | **#17 的分支**（叠放） | P2-BA 证据/能力矩阵 |
| #17 | Draft | 默认分支（旧 base 5be172f） | P2-AZ 回顾性组级比较，落后默认 9 commits |
| #16 | Draft | 默认分支 | P2-AY |
| #15 | OPEN | 默认分支 | 历史 P2 资料整合 |
| #10 | OPEN | 默认分支 | P2-AU 时钟敏感性（另一分支 #11 已合并） |
| #6、#3、#2、#1 | Draft | #3 叠在 #2（`ccr-8cffa954-5gedc0`）上 | 早期 M2/SL 实验与归档，均落后默认 45–62 commits |
| #4、#5、#7、#8、#9、#11、#12、#13、#14 | 已合并（merged_at 有值；GitHub 工具返回的 `merged:false` 字段与 merged_at 矛盾，以 git 祖先关系为准：f53726f 包含 #14） | — | — |

PR #19 描述称 M1 研究核来自 P2-AZ（#17 的内容），而 #17/#18 本身仍为草稿未合并——阶段 C 需核实 #19 是否自带所需研究文件，以及合并顺序是否存在依赖。

## 4. 受保护文件与科研守卫（禁止在未理解前修改）

- `docs/pk-research/p2/protocol-lock.json`、`production-baseline.json`：冻结协议与生产文件哈希基线。
- `docs/pk-research/p2/protocol-deviations.json`：追加式偏离登记（如 PD-2026-10-10-F1）。
- `tools/pk-research/check_protocol.py`：CI 中校验上述哈希；任何对受保护 UI/PK 文件的修改都会被拦截，除非登记精确旧→新 SHA。
- `app/src/test/java/net/plainnotes/app/PeriodStabilityTest.kt`：CLAUDE.md 规定的交付门槛，不得跳过或放宽。

本审计不修改上述任何文件。

## 5. 文档“当前 / 历史 / 未知”映射（初步）

| 文档 | 判断 | 证据 |
|---|---|---|
| `CLAUDE.md` / `AGENTS.md` | 当前规则 | full-only、PeriodStability 门槛、交接规则 |
| `docs/HANDOFF.md`（默认分支，1186 行） | **顶部部分过期**：P2-AX 节仍写“PR #14 Draft，默认分支尚不含本阶段文件”，但 #14 已于 2026-10-11 12:00 UTC 合并进 f53726f；顶部“截至本次交接更新：PR #4…#9 已合并”未含 #11–#14 | `HANDOFF.md:19`、`git log` |
| `docs/REQUIREMENTS.md`（默认分支） | 截至 §58（2026-10-10）；§59–§66（2026-10-11/12 的浓度页最新用户决定）**只在 PR #19 分支** | 两分支 `grep '^## '` |
| `docs/BACKLOG.md` | 顶部“最新状态”停在 §50（2026-10-09），未反映 P2-R 以后研究、F1、体积配置、PR #19 | `BACKLOG.md:1-7` |
| `README.md` | 基线 Build25/0.2.0/Schema9 与源码一致；“P2-C unimplemented” 与默认分支一致 | `README.md:4` |
| `docs/PLAN.md` | 历史架构决定（README 自述），不能覆盖新决定 | `README.md:7` |

结论：**当前最新产品决定（浓度页 M2-only）存在于未合并分支**，默认分支的需求/交接文档滞后于用户已安装可能使用的 APK。新接手的 AI 若只读默认分支，可能按过期决定（三模型选择 / Legacy 页）行事。这是治理层面的首要风险，阶段 D 会给出“单一当前事实入口”建议。

## 6. 报告索引

| 文件 | 状态 |
|---|---|
| `README.md`（本文件） | 阶段 A 完成 |
| `08-verification-log.md` | A–D 命令记录 |
| `01-current-product-inventory.md` | 阶段 B 完成 |
| `02-ui-ux-and-user-journeys.md` | 阶段 B 完成 |
| `03-pk-evidence-and-display-safety.md` | 阶段 C 完成 |
| `04-data-security-reliability.md` | 阶段 C 完成 |
| `05-architecture-performance-testing.md` | 阶段 C 完成 |
| `06-roadmap-prioritized.md` | 阶段 D 完成 |
| `07-decisions-needed.md` | 阶段 D 完成 |
| `evidence-index.md` | 阶段 D 完成 |
