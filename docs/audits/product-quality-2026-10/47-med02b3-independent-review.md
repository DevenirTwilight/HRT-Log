# 47 — MED-02B3 / B2-SECURITY-GATE2 独立复核（PR #31）

- 复核日期：2026-10-14
- 复核对象：`feature/med02b3-b2-security-gate-20261014` @ `37b813d2694475671dcc28f4d0060825c0b1f4a5`
- 父 PR：#30 @ `814d355`，仍 OPEN、未合并；默认分支 `cfe600e` 未变
- 方式：先只读复核，再按负责人“接手 #31 推到全绿”的指示在同一分支追加修正提交（只动测试与文档）；未合并
- 性质：这是作者以外的 AI 工程复核，**不是**外部独立安全专家审核。`NOT_REVIEWED_EXTERNALLY` 保持不变。

## 结论

**没有发现阻断问题。** 结论 `RESEARCH_GO / IMPLEMENTATION_PENDING_OWNER_AND_EXTERNAL_REVIEW`、生产 `NO-GO` 有代码和 CI 支撑，证据等级的标注也基本诚实。

发现 2 个中等问题。它们都是**将来生产 B2 设计必须补上的缺口**，不是这个测试协调器本身的错误；建议写进报告 45 的风险登记和最小 B2 审批范围。另有 7 个小问题。

## 已独立核实

| 项目 | 方法 | 结果 |
|---|---|---|
| 生产代码未改 | 和 `814d355` 做 `git diff`，查 `src/main`、`build.gradle`、清单 | `src/main` 零改动；只有 `core/data` 加了 androidTest 依赖和测试清单，app 无改动 |
| Release 隔离 | 读 `check_attachment_release_isolation.py` 的改动；本地跑测试 | 新增 `Gate2StreamProvider`、`B2-SECURITY-GATE2-TEST-ONLY` 两个禁止标记；3 个测试通过 |
| 设计校验 | 本地跑 `test_attachment_design_validation.py` | 58 个通过 |
| 纯 JVM | 本地跑 `-PjvmOnly :core:domain :pk-engine :importer :attachment-poc test` | 261 个，0 失败、0 错误、0 跳过，与报告 46 一致 |
| 上下文向量 | 用 Python 标准库独立重算 114 字节转录 | 与 `gate2-context-vector.json` 和 Kotlin 合同完全一致 |
| API26 设备证据 | 下载 CI 任务 116074028905 原始日志 | data 43、app 67，全部完成，0 跳过、0 失败；红→绿对照与 WRITE/IN_COMMIT/AFTER_COMMIT 三次强停都输出 PASS |
| 只在模拟器运行 | 读 `Gate2EmulatorGuard` 和两个脚本 | 不是模拟器就**直接失败**（不是跳过）；只对独立测试包 `net.plainnotes.app.data.test` 执行 force-stop，没有 `pm clear` |
| 生产开关 | 读测试夹具 | 每个夹具结束都断言 `feature_enabled=0`、`attachment_version` 为空；PNBAK 序列化仍为 9，且不含附件表 |
| 测试数量说法 | 数测试方法 | Store 16、SAF 3、Restart 1、上下文 2、TinkPDF 6，与报告 46 一致 |
| 最终 HEAD 的 CI | 读 check runs | push 和 PR 两次运行中，jvm、device-tests、API26/29、Science gate/deep 都成功；push 那次的 android 也成功。PR 那次的 `android` 任务随后于 20:18 UTC 成功，37b813d 共 14 项全部成功 |

## 中等问题（生产设计缺口）

### M1 `reconcile()` 没有排除仍在运行的写入者

`Gate2Store.reconcile()` 会无条件把 `PREPARED/WRITING/CLEANUP_PENDING` 改成 `ABORTED`，并删除属于自己的未完成密文。

- 只有在"进程启动时、确定没有其他写入者在跑"这个前提下，这样做才正确。
- 测试协调器没有任何锁或租约来保证这个前提。测试是串行调用的，所以没出问题。
- 报告 45 的最小 B2 审批范围写了"journal 恢复"，但没有把"恢复与写入互斥（单写者租约或只在启动时运行）"列为必备条件。
- 将来生产实现如果在运行中触发 reconcile（例如切换空间、重新解锁），就可能中止或删除另一个线程正在写的对象。

建议：在报告 45 的审批范围里明确写入"恢复与写入者互斥"，并在将来的生产实现中配一个对应的负向测试。

### M2 被隔离的孤儿对象永远占着配额和存储

`QUARANTINE_COMPLETE_ORPHAN`、`QUARANTINE_FOREIGN_PATH`、`QUARANTINED_EPOCH` 三种状态都没有释放路径。

- 配额统计只排除 `ABORTED/CLEANED`，所以这些行的预约额度一直算在已用里。
- 每次在 IN_COMMIT 崩溃都会留下一个完整孤儿。多次崩溃会逐步耗尽配额，造成"安全但永久无法导入"的拒绝服务，密文文件也一直留在盘上。
- 还有一个崩溃窗口：`CREATE_NEW` 建好文件之后、自动提交写入 `owned_device/owned_inode` 之前。这里崩溃，自己的文件会被永久认作"外来文件"。三个强停点都在这个窗口之后，所以没有覆盖到它。

这些都是"宁可泄漏也不误删"的安全方向，但报告 45 的风险登记没有提到孤儿保留期、人工清理、配额回收和用户提示。建议补一行 P1 风险，并在将来的生产 B2 里决定孤儿怎样经确认后回收。

## 小问题

1. **负向测试不检查失败原因。** `fails{}` 会吞下任何 Throwable。替换包裹密钥、O_EXCL/NOFOLLOW、外来会话、承诺被篡改等测试，即使失败来自无关的 bug 也会通过。其中一部分靠阶段断言兜住了，例如 `PREPARED` 说明解包在写入前就失败了。建议断言异常类型或消息。
2. **双写者测试少断言一半。** `twoRealSqlWritersOneCasWinner` 对应报告 43 的"失败者只清自己的密文"，但测试没有断言失败者文件已删、胜者文件仍在，只检查了两个阶段和胜者可读。
3. **两处"被阻塞"的证据靠计时。** `inCommitRevokeWaitsForEntireNativeCommit` 和 PDF 的"renderer 关闭与 page 生命周期串行"都靠"100ms 内还没完成"来证明。这能说明有阻塞，但不是严格证明，报告里最好标成计时型证据。
4. **两个撤权测试没有经过内核。** `revokedAfterDecryptBeforeDelivery…` 和 `ownerCloseRecyclesNativeBitmap…` 直接调用 `callback.onRead`，没有走内核的 ProxyFD 路径。建议在报告 46 里注明是直接回调。
5. **报告 46 内部前后矛盾。** 开头写 `00cb4cf` 完整 CI 已通过，但"先失败/修正与运行状态"一节末尾仍写"完整 Android unit/lint/… 最终 push/PR CI 仍 PENDING"。属于没更新的旧文字。
6. **向量文件写的生成器不存在。** `gate2-context-vector.json` 写着 `generator: Python stdlib`，但仓库里没有对应的 Python 生成脚本。本次已独立重算并确认一致；建议补上生成脚本，或在 `test_attachment_design_validation.py` 里加一条重算断言，让外部审查者能直接复现。
7. **Tink 坐标写死。** `core/data/build.gradle.kts` 用的是硬编码的 `com.google.crypto.tink:tink-android:1.23.0`，没有走版本目录。将来升级时容易和 app、attachment-poc 的版本不一致。

另外，`mimeClaimIsNotFormatValidation` 只验证了测试 provider 本身，没有验证导入器会拒绝 MIME 与魔数不符的输入。报告写的是"MIME 不是格式鉴定"，措辞是诚实的。但任务书 S2 第 1 项里"MIME 与魔数不符的拒绝"实际上应视为 `NOT_RUN`，因为生产仍禁止 `VALIDATED`。

## 任务书第 11 节逐项对照

| 要求 | 状态 |
|---|---|
| 最新主线、PR #30、本 PR 的真实状态和父子关系 | 满足（报告 43、46） |
| 新增的真实证据与 B2 原 20 项合同分开计 | 满足 |
| Tink 与 JCA 判定、仍 BLOCKED、缺外部审核 | 满足（报告 44：推荐 A，未冻结，`NOT_REVIEWED_EXTERNALLY`） |
| SQLCipher+FS/Android/PDF/撤权各项的设备与结果 | 满足；全部是 x86_64 模拟器，已如实标注 |
| 失败注入、ENOSPC、ARM、掉电、敌意 PDF、外审的证据等级 | 满足（`FAULT_INJECTION_ONLY`、`NOT_RUN`） |
| 生产文件 diff、Release DEX/清单、Room10、PNBAK9、build26、PK 不变 | 满足；本次独立核实了 diff |
| 最终 HEAD 的 CI 链接 | 37b813d 满足（14 项全部成功）；修正提交的最终 HEAD 见 PR31 正文 |
| 最小 B2-IMPLEMENTATION 申请 | 满足；建议补上 M1、M2 |
| 结尾声明 | 满足 |

## 处理状态（接手后）

| 编号 | 处理 |
|---|---|
| M1 恢复与写入者互斥 | 写入报告45风险登记（P0）与最小B2审批前置；测试协调器不改语义 |
| M2 孤儿隔离保留/配额 | 写入报告45风险登记（P1）与审批前置 |
| 小1 不检查失败原因 | 已修：`failsWith`/`failsAs`，16项Store测试中的拒绝全部断言原因 |
| 小2 双写者少断言 | 已修：断言失败者密文已删、胜者保留且为CURRENT |
| 小3 计时型证据 | 已在报告46标注TIMING_BASED |
| 小4 直接回调 | 已在报告46标注DIRECT_CALLBACK |
| 小5 报告46旧文字 | 已改为历史说明 |
| 小6 向量生成器缺失 | 已修：新增`test_gate2_context_vector.py`，设计校验58→61 |
| 小7 Tink坐标写死 | 保持：app与attachment-poc既有约定同样写死1.23.0，三处一致；单独改目录会扩大本PR |
| MIME与魔数拒绝 | 报告46标注NOT_RUN |

## 建议

- 合并前：等最终 HEAD 的全部 CI 结束（已按下表处理小问题 5 与 M1、M2）。
- 小问题 1–4、6、7 可以留到下一轮，或作为外部审查包的已知问题提交。
- 生产 B2 继续 NO-GO。本复核不替代外部专家审核，也不替代负责人批准。
