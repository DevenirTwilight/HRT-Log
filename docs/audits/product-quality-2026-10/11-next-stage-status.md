# 11 下一阶段状态（S0 报告 + 决策与批次状态），2026-10-12

本文件即任务书要求的 S0 报告（`S0-status-and-b1-verification`）；B1 细节见 13。

## 1. S0：实时核对（2026-10-12 11:24Z 起）

| 项 | 实测 |
|---|---|
| 工作分支 | `claude/new-session-sbje5o`，跟踪 `origin/claude/new-session-sbje5o`；核对开始时 HEAD `6f13c66…`，工作区干净 |
| 默认分支 | `claude/new-session-1959qb` @ `f53726ff6fa52fc5c78806a1576fdb80fcf29987`（未变） |
| PR #19 | OPEN @ `aecbd7da843abd3f4334c0979e8265a72e6c67b5`（未变）；PR #17 head 不是其祖先，自带 P2-AZ 输入副本 |
| PR #18 | Draft @ `68ceac2717b487029d894a76f4185cdf1f890d35`（未变），base 为 #17 分支 |
| 与上一报告的差异 | 无代码差异；审计目录、`docs/STATUS.md`、`10-about-source-identity.patch` 均已在 `6f13c66` 中提交 |
| CI 38241151594（`6f13c66`） | 11:24Z 时 jvm、device-tests success，android 进行中 → 随后 **success**（三个作业全部成功） |
| 本地仓库 | 曾为浅克隆，已补全完整历史 |

## 2. 本轮提交

| 提交 | 内容 | 授权 |
|---|---|---|
| `0d99591` | B1 加固：`SOURCE_ORIGIN`、本项目 checkout 校验、未跟踪文件计入 dirty、注入 SHA 比对、Release fail-closed、`apk_provenance.py` 及测试 | APPROVED（B1 复核与必要修正） |
| 下一个文档提交 | 11、12、13、14、`B0-protected-file-matrix.json`、补丁 v2、STATUS / HANDOFF / 08 更新 | 仅文档 |

| `b2ba7f6` | B0 双域守卫（D-13 方案 A） | APPROVED（REQUIREMENTS §67） |

PR：**未创建**。Release / 签名 / 交付：**无**。

## 3. 决策门与批次状态

| Gate / 批次 | 状态 | 文档 |
|---|---|---|
| G0 / D-13 科研守卫双域 | **DONE**（2026-10-12 批准方案 A；提交 `b2ba7f6`；CI 38257031094 全部 success） | 12 §9 |
| B1 构建侧 | **DONE**（本地全量回归 PASS；CI 38244330717 @5b2993d 全部 success） | 13 |
| B1 关于页 | **APPROVED，下一批实施**（B0 后 `SettingsScreen.kt` 为仅历史文件，正常修改，无需偏离） | 13 §7 |
| D-4 versionCode | **APPROVED**（今后正式交付递增；不改 applicationId / 签名 / Schema） | REQUIREMENTS §67 |
| G1 / D-1 M2 显示 | **APPROVED 方案 B（附科学边界），排在关于页之后，在 PR #19 分支实施** | 14、REQUIREMENTS §67 |
| G2 / D-2 PDF / 化验估算 | **APPROVED D2-2，与 D-1 同批** | 14、REQUIREMENTS §67 |
| B2 隐私/备份 | 不再被守卫阻断（相关文件已是仅历史文件）；待排期 | 06 |
| B3 UX / 无障碍 | 不再被守卫阻断；PK 显示的 7 个文件仍需批准链 | 06 |
| B4 性能 | 需要设备基准 | 06 |
| B5 功能扩展 | 后置 | 06 |

## 4. 本轮新发现

1. **现行守卫的保护空洞**：研究/审计实际读取的 `pk-engine/src/test/resources/non-sl-calibration-v1.json`、`sublingual-literature-validation.json` 不在基线中，修改它们不会被守卫发现（12 §2）。
2. **历史研究可以精确回放**：在 `388ef0f` 回放后，5/5 结果文件逐字节一致；当前树的科学字段也完全一致（12 §3）。
3. B1 初版的 6 个身份可信度问题已修正（13 §2）。
4. 同一源码在本机两次构建得到相同的 unsigned APK sha256（13 §4）。
