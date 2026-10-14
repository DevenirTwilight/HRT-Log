# 52 — MED-02B4 / B2-GATE3 验证记录（PR-A）

2026-10-14。本报告只记录 PR-A（B2-GATE3）的证据；PR-B（PRIVACY-P0）有独立报告与 CI，不合并计算。最终 PR HEAD 的全部 CI 结果在其结束后以追加提交写入本节与 `52-med02b4-verification.json`；在此之前不称通过。

## 本地结果（提交前）

- 纯 JVM：domain/PK/importer/attachment-poc 共 262（较主线 +1：`displayNamesArePlainNamesNeverPaths`），0 fail/error/skip。
- Python 设计校验 61、Release 隔离检查器 3（禁止标记新增 `Gate2PeerProvider`、`KernelProxyFdGate3`）全部通过。
- `:core:data:assembleDebugAndroidTest`、`:app:assembleFullDebugAndroidTest` 编译成功。本机无 KVM，设备测试只能由 CI 的 x86_64 emulator 运行。

## 新增/修改的测试（全部 test-only）

| 测试 | 位置 | 运行 API |
| --- | --- | --- |
| `KernelProxyFdGate3AndroidTest`（2 方法） | app androidTest attachmentpoc | 26/29（attachment-poc 包）、35（完整 app） |
| `Gate3ProcessLockAndroidTest`（3 方法，含第二进程 `:gate2peer`） | core/data androidTest | 26/29（类列表已加入）、35（完整 data） |
| `Gate2StoreAndroidTest#orphanHandlingIsIdempotentAndAnInterruptedReleaseResumes` | core/data androidTest | 26/29/35 |
| `Gate2SafAndroidTest#candidateAdmissionRejectsMimeMagicConflictsAndUnsafeNamesBeforeCommit` | core/data androidTest | 26/29/35 |
| `Gate2RestartAndroidTest` IN_COMMIT 新 PID 二次释放幂等 | 外部强停脚本 | 26/29/35 |
| `B2InputPolicyContract#displayNamesArePlainNamesNeverPaths` | JVM + app androidTest | JVM、26/29/35 |
| `scripts/run_attachment_gate3_red.py`：两项红对照→同断言绿 | CI 两个设备 job | 26/29/35 |

## 不变量（diff 审计）

生产 `src/main`、Room schema/guards、PNBAK1、PK、版本/签名、`gradle/libs.versions.toml`、`app/build.gradle.kts` 均无改动；只改 androidTest 源码与测试清单、tools contractTest、scripts、CI 工作流与文档。

## 未运行（如实标注）

真实 ARM（NOT_RUN_ARM）、受控真实 ENOSPC（NOT_RUN_REAL_ENOSPC）、硬件掉电、敌意/密码 PDF 与隔离解析 worker deadline、外部专家审查（NOT_REVIEWED_EXTERNALLY）、真机 TalkBack。

## CI 结果（证据 HEAD 76909b8）

首轮 9cd28be：push/PR 各有设备 job 失败（peer 进程打开同一数据库导致 SQLITE_SCHEMA 17 / disk I/O 522；API29 等待条件竞态），根因与修正见报告49 §B。修正提交 76909b8 的两次运行全部成功：

| 运行 | 结论 | job |
| --- | --- | --- |
| [Android PR 38644133964](https://github.com/DevenirTwilight/HRT-Log/actions/runs/38644133964) | success | jvm、android、device-tests（API35）、attachment-poc-compatibility 26/29 全部 success |
| [Android push 38644129077](https://github.com/DevenirTwilight/HRT-Log/actions/runs/38644129077) | success | 同上五项 success |
| [Science PR 38644133912](https://github.com/DevenirTwilight/HRT-Log/actions/runs/38644133912) | success | science-change-gate、research-deep success |
| [Science push 38644129153](https://github.com/DevenirTwilight/HRT-Log/actions/runs/38644129153) | success | science-change-gate success；research-deep 按条件 skipped |

原始日志（PR 运行，job 116126705337 / 116126704875 / 116126705248）中的计数：API26 与 API29 data 51、app 70（显式类列表含 `Gate3ProcessLockAndroidTest`）；API35 data 118、app 260。6 处 `Process crashed` 均来自既有重启脚本（`run_attachment_foundation_restart.py`、`run_attachment_gate2_restart.py`）有意终止进程的阶段，不是失败。

GATE3 证据行（三个 API 一致，PID 每 API 不同）：

```
GATE3_KERNEL test=kernelRevocation red=false readsBeforeRevoke=2 readsAfterRevoke=2 farOffset=ERRNO(EIO) sameOffset=CACHE_OR_READ(4096) deliveredAfterRevoke=0
GATE3_KERNEL test=kernelRevocation red=true  readsBeforeRevoke=2 readsAfterRevoke=1 farOffset=CACHE_OR_READ(4096) deliveredAfterRevoke=4096   ← 红对照，预期失败
GATE3_KERNEL test=dupAfterRevoke midBeforeRevoke=CACHE_OR_READ(4096) farAfterRevoke=ERRNO(EIO) deliveredAfterRevoke=0
GATE3_PROCESS test=sameProcessProbe red=false peerSees=HELD_OTHER_PROCESS
GATE3_PROCESS test=sameProcessProbe red=true  peerSees=FREE   ← 红对照，预期失败
GATE3_PROCESS test=peerWriter afterRelease=CLEANED_OWNED_INCOMPLETE
GATE3_PROCESS test=killedPeer reconcile=CLEANED_OWNED_INCOMPLETE
PASS: both GATE3 red controls failed as expected; fixed paths pass the same assertions
```

peer PID 与测试进程 PID 在每个 API 上都不同（例如 API35 pid=2267 / peerPid=2322），killedPeer 后下一次调用得到新 PID（断言通过）。`sameOffset=CACHE_OR_READ(4096)` 是撤权后重读已交付 offset 的记录值：只证明没有新明文交付（`deliveredAfterRevoke=0`），不证明内核缓存已清空。

合并前条件（任务书 §12）核对：PR 基于真实默认分支 `claude/new-session-1959qb`，mergeable_state=clean；默认分支 ruleset 要求 0 个批准、6 项必需检查（jvm、android、device-tests、attachment-poc-compatibility 26/29、science-change-gate，需基于最新 HEAD）与评审线程解决，无人工批准要求；无评审意见；diff 不触及 `src/main`、版本目录或构建脚本。本节写入后形成仅文档的最终 HEAD，合并以该 HEAD 的必需检查全部成功为准，结果记入 HANDOFF/STATUS。

B2 生产结论不变：NO-GO；外部审查 NOT_REVIEWED_EXTERNALLY。
