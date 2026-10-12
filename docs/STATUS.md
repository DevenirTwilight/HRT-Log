# HRT Log 当前状态（单一事实入口）

> 只写“现在是什么”。历史过程见 `docs/HANDOFF.md`，需求原文见 `docs/REQUIREMENTS.md`，审计细节见 `docs/audits/product-quality-2026-10/`。
> 每次合并、交付或做出产品决定时更新本文件。
> 最后核对：2026-10-12T10:32Z（审计分支 `claude/new-session-sbje5o`；**本文件尚未进入默认分支**）。

## 源码与分支

| 项 | 值 | 证据 |
|---|---|---|
| 默认分支 | `claude/new-session-1959qb` @ `f53726ff6fa52fc5c78806a1576fdb80fcf29987`（Merge PR #14） | `git rev-parse` |
| 默认分支 CI | Android and domain checks run 38137617053 success | GitHub Actions |
| 应用版本 | versionCode 25 / versionName 0.2.0 / Room Schema 9 | `app/build.gradle.kts`、`NotesDatabase.kt` |
| 最新待合并产品方向 | PR #19（M2-only 浓度页）@ `aecbd7da843abd3f4334c0979e8265a72e6c67b5`，OPEN，CI 全绿，**未合并** | GitHub |
| 公开 Release | 仅 v0.2.0（2026-10-06） | GitHub Releases |
| 已安装到用户手机的构建 | **UNKNOWN**（识别方法见审计 07 D-14） | — |

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

## 等待产品负责人决定（阻断后续开发）

| ID | 问题 | 文档 |
|---|---|---|
| D-13 | 科研守卫是否改为双层：历史回放 + 只实时锁定 43 个研究文件 | 审计 09 |
| D-1 | M2 浓度页的绝对 pg/mL 显示方式 | 审计 03、07 |
| D-2 | PR #19 下 PDF 图与化验冻结估算的口径 | 审计 03、07 |
| D-4 | 是否每次交付递增 versionCode | 审计 07 |
| B1-UI | 是否批准登记关于页的 UI 协议偏离（显示源码 SHA） | 审计 10 |

## 已知阻断

- P2 守卫逐字节锁定 164 个文件（含 `strings.xml`、`NotesViewModel`、数据层）。非 UI 文件和资源不可豁免（审计 05 ARCH-10、09）。

## 最近的验证证据

- 默认分支 CI success（f53726f）；PR #19 CI 7/7 success（aecbd7d）。
- 本地：JVM 58/58/12、研究/协议 252 项通过（2026-10-12）。
- 最近有记录的真机反馈：2026-10-10，ARM64 包，用户手动检查（机型未记录）。

## 关键文档

`CLAUDE.md` · `docs/REQUIREMENTS.md` · `docs/HANDOFF.md`（历史） · `docs/BACKLOG.md`（顶部状态停在 §50） · `docs/audits/product-quality-2026-10/README.md` · `docs/pk-research/p2/README.md` · `docs/licensing.md`
