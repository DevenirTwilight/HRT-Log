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

## CI 结果

（待最终 HEAD 的 CI 结束后填写：run URL、各 job 结论、API26/29/35 原始日志中的测试计数、GATE3_KERNEL/GATE3_PROCESS 证据行、红绿输出。）
