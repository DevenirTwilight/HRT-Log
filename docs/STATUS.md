# HRT Log 当前状态（单一事实入口）

> 只写“现在是什么”。历史过程见 `docs/HANDOFF.md`，需求原文见 `docs/REQUIREMENTS.md`，审计细节见 `docs/audits/product-quality-2026-10/`。
> 每次合并、交付或做出产品决定时更新本文件。
> 最后核对：2026-10-12T14:30Z（审计分支 `claude/new-session-sbje5o`；**本文件尚未进入默认分支**）。

## 源码与分支

| 项 | 值 | 证据 |
|---|---|---|
| 默认分支 | `claude/new-session-1959qb` @ `f53726ff6fa52fc5c78806a1576fdb80fcf29987`（Merge PR #14） | `git rev-parse` |
| 默认分支 CI | Android and domain checks run 38137617053 success | GitHub Actions |
| 应用版本 | versionCode 25 / versionName 0.2.0 / Room Schema 9 | `app/build.gradle.kts`、`NotesDatabase.kt` |
| 最新待合并产品方向 | PR #19（M2-only 浓度页）@ `aecbd7da843abd3f4334c0979e8265a72e6c67b5`，OPEN，CI 全绿，**未合并** | GitHub |
| 公开 Release | 仅 v0.2.0（2026-10-06） | GitHub Releases |
| 已安装到用户手机的构建 | **UNKNOWN**（识别方法见审计 07 D-14） | — |
| 构建身份机制（审计分支） | `BuildConfig.SOURCE_SHA/ORIGIN/DIRTY/COMMIT_TIME_UTC`；Release 要求 origin=git 且干净；`scripts/apk_provenance.py` 分别读取源码、APK sha256 和签名证书。**只对今后的构建有效** | 审计 13 |

## 开放 PR 与依赖

| PR | 作用 | 状态 | 依赖 |
|---|---|---|---|
| #19 | M2-only 浓度显示 | OPEN | 自带 P2-AZ 输入副本，不依赖 #17 |
| #18 | P2-BA 能力矩阵（`m2_absolute_pgml=false`） | Draft | 叠在 #17 上 |
| #17 | P2-AZ 研究 | Draft | — |
| #16、#15、#10 | 研究/归档 | Draft/OPEN | — |
| #6、#3、#2、#1 | 早期实验/归档 | Draft | #3 叠在 #2 上 |

处置建议见审计 07 D-9（未决定）。

## 已批准、仍然有效的规则

- 只维护 full 变体，play 已废弃（CLAUDE.md，2026-10-07）。
- `PeriodStabilityTest` 是交付门槛（CLAUDE.md，2026-10-08）。
- P2-C HOLD：不替换生产 PK 引擎（HANDOFF 顶部，2026-10-11）。
- 研究成果必须实际入库（HANDOFF 顶部）。
- 浓度页 M2-only 与 APK 私下交付授权：REQUIREMENTS §65–66，**只存在于 PR #19 分支**。

## 2026-10-12 产品负责人决定（REQUIREMENTS §67）

| ID | 决定 | 状态 |
|---|---|---|
| D-13 | 方案 A：双域科研守卫 | **已实施**：`b2ba7f6`，CI 38257031094 全部 success |
| B1-UI | 关于页显示完整源码 SHA、提交时间、构建来源，可复制；四语言/字号/宽度验证 | **已实施**：`0b7a448`、`cf3e580`（审计 16） |
| D-4 | 今后正式交付递增 versionCode；不改 applicationId、签名证书、Schema；交付清单区分源码 SHA / APK SHA-256 / 证书指纹 | **规则已实施**：`docs/delivery/ledger.json` + `scripts/delivery_ledger.py`（`47e2716`）；下一次交付 versionCode ≥ 26 |
| D-1 | 方案 B + 科学边界（保留 M2 条件性情景曲线与 pg/mL，取消醒目的“当前浓度”数字；不改 `m2_absolute_pgml`） | **已实施，CI 全绿**（PR #19 分支 `bd20a5e`，审计 18）；整合已完成（`b649679`/`343a183`/`4db4770`，审计 17） |
| D-2 | D2-2：新 PDF / 新化验记录的舌下 E2 个体估算显示“暂不可用”及原因；历史快照不变；需改 Schema 时先另行请示 | **已实施，CI 全绿**（PR #19 分支 `c41cb4a`，审计 19；无 Schema/格式变更） |

未经再次批准：不合并 PR #19，不签名或发布 APK，不修改历史记录和生产 PK 内核。

§60 审批（`PD-2026-10-11-ORIGINAL-UI-CONCENTRATIONSCREEN`）：**OWNER_CONFIRMED**，2026-10-12 事后人工核验（REQUIREMENTS §72）；原日期 2026-10-11、提交 `425a7ab`、晚 23 分钟的需求记录均保留。

## 已知阻断 / 守卫现状

- 守卫已改为双域（审计 12 §9）：历史域从 `e89579a` 复现 164/164 哈希并在 `388ef0f` 回放研究；当前域实时锁定 43 个原研究文件 + 2 个研究测试资源，7 个 PK 显示文件需产品负责人批准链（`display-approvals.json`），其余 114 个产品文件正常开发。
- 缺少历史 git 对象、研究结果不一致、受保护文件未授权变更时守卫失败，不跳过。
- PR #19 分支仍带旧守卫。隔离整合（审计 17）发现：PR #19 自带测试断言旧守卫语义，需要改写，**等待产品负责人批准**；PR #19 新增的研究输入 `ResearchSublingualV01.kt` 未锁定；新增的 M2 显示文件 `UnifiedConcentrationScreen.kt` 不在守卫范围内。
- 交付版本：2026-10-09 至 10-12 的多个私下交付 APK 都是 versionCode 25，仅凭版本号无法区分手机上装的是哪一个。

## 最近的验证证据

- 默认分支 CI success（f53726f）；PR #19 CI 7/7 success（aecbd7d）。
- 本地（`0d99591`，干净克隆，SDK 37）：domain 58、pk-engine 58、importer 12、data 72、reminder 14、app 405（13 项跳过），`PeriodStabilityTest` 5/5，lint 0 错误，Debug/Release 构建、无 INTERNET 检查、研究守卫均通过（审计 13）。
- CI 38241151594（`6f13c66`）全部 success；加固后 CI 38244330717（`5b2993d`）全部 success；B0 CI 38257031094（`b2ba7f6`）全部 success（研究测试 273 项）。
- B1 本地（`47e2716`/`cf3e580`）：data 72、reminder 14、app 411（13 跳过），PeriodStability 5/5，lint 0 错误（修复后），Release APK 声明 cf3e580/git/clean（审计 16 §4）。CI 38262091486（`cf3e580`）全部 success。
- 最近有记录的真机反馈：2026-10-10，ARM64 包，用户手动检查（机型未记录）。

## 关键文档

`CLAUDE.md` · `docs/REQUIREMENTS.md` · `docs/HANDOFF.md`（历史） · `docs/BACKLOG.md`（顶部状态停在 §50） · `docs/audits/product-quality-2026-10/README.md` · `docs/pk-research/p2/README.md` · `docs/licensing.md`
