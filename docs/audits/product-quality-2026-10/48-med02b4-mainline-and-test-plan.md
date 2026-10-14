# 48 — MED-02B4 / B2-GATE3 主线基线与测试计划

2026-10-14。**命名澄清**：MED-02B4 是项目工作批次编号（B2-GATE3 + PRIVACY-P0），**不是**附件路线中的 B4（图片资料库），也**不是** B3（完整加密医疗备份）。本批次拆成两个互不依赖的 PR：PR-A（本报告与49–52，只含隔离测试/测试构建/文档）与 PR-B（隐私声明与应用内入口）。

## 实时基线（启动时核查，非历史值）

| 项 | 实际值 |
| --- | --- |
| 默认分支 | `claude/new-session-1959qb`（不是 main） |
| 主线 HEAD | `9d99fd9027c3e5f3e4789c0a30e92a4c8d090ac3`（Merge PR #31） |
| PR #30 | MERGED，merge `f41158d7374770486fd6f6818ced9ec0408f7d4b`，验收 HEAD `814d355c` |
| PR #31 | MERGED，merge `9d99fd9027c3e5f3e4789c0a30e92a4c8d090ac3`，验收 HEAD `c74b41d7`；合并树 `7de3d46` 与该 HEAD 的 CI 树完全一致 |
| 主线合并后 CI | [Android 38637930685](https://github.com/DevenirTwilight/HRT-Log/actions/runs/38637930685) 5 job 全部 success（jvm、android、device-tests、API26、API29）；[Science 38637934425](https://github.com/DevenirTwilight/HRT-Log/actions/runs/38637934425) success。#30 合并的中间 push run 38637884054 因工作流 `cancel-in-progress` 被 #31 合并取代而 CANCELLED，不计为通过；其内容已包含在 9d99fd9 中 |
| 工作流名称 | `Android and domain checks`（jvm / android / device-tests / attachment-poc-compatibility 26,29），`Science validation`（science-change-gate / research-deep） |
| 工作树 | 新分支 `feature/med02b4-b2-gate3-20261014` 自 9d99fd9 创建，干净 |
| 其他 OPEN PR | 仅历史研究 PR（#1–#18 系列 draft/research），与本批无关 |
| 版本 | Room runtime 10；PNBAK1 结构化序列化 9；versionCode 26 / 0.2.0（正式 build26 为私人交付，与主线代码不同对象，本批不触碰） |

## 继承事实（不重做）

PR30：Tink 1.23 仅测试、同 FD 完整 EOF 认证后才发租约。PR31：真实 SQLCipher+FS+Keystore 协调器、三 API Tink→ProxyFD→PdfRenderer 良性 PDF、AFTER_CREATE/WRITE/IN_COMMIT/AFTER_COMMIT 强停；复核 M1/M2 已在**测试协调器**修复（不是 src/main）。

## 本批新发现（PR-A 必须先修）

**c74b41d 的 M1 锁实现在测试协调器里有真实缺陷**：POSIX fcntl 记录锁属于进程，关闭该文件的**任一**描述符会释放本进程在其上的**全部**锁。旧实现的 maintenance 探测在与写入者同一进程里新开 channel、得到 `OverlappingFileLockException` 后关闭 channel——同进程内 JVM 锁表仍阻止其他线程（所以原测试通过），但 OS 级锁已被释放，另一进程此后可取得同一 operation 的租约，形成双写者。仅影响测试协调器，生产 src/main 无此代码。修正与先红后绿证据见49。

## PR-A 测试计划与证据等级

| 缺口（报告47/45） | 本批做法 | 证据等级 |
| --- | --- | --- |
| 内核 ProxyFD 撤权/页缓存 | `KernelProxyFdGate3AndroidTest`：真实 `pread`、`lseek+read`、`dup()`，计数回调实际被内核调用次数；撤权后未读远端 offset 必须 EIO；同 offset 重读只观察是否缓存命中，若命中只能等于撤权前已交付字节；撤权后开始的读取不得返回明文；红对照=租约绑定无关 capability | REAL_KERNEL_PROXY_FD（x86_64 emulator） |
| 跨进程活动写入者 | `Gate3ProcessLockAndroidTest` + `:gate2peer` 独立进程 provider：peer 持锁暂停 WRITING 时本进程 reconcile=SKIP_LIVE_WRITER、第二写入者被拒、其他 operation 并行；杀死 peer 后内核释放锁，只清理其自有叶子；同进程探测不再丢 OS 锁（红=旧探测） | REAL_TWO_PROCESS（emulator） |
| 孤儿/配额 | 幂等与断点续做：AFTER_CREATE、IN_COMMIT（含 cleanup 失败）、删除后 SQL 未更新的续做、重复释放拒绝且不重复释放配额；真实 IN_COMMIT 强停后二次释放 | REAL_SQLCIPHER + FAULT_INJECTION / 真实 force-stop 分开标注 |
| 格式误报 | 测试专用 admission 把既有 `B2InputPolicy` 接入 `Gate2Store.import`：声明 MIME 与魔数不符（PNG↔PDF）、不安全显示名在提交前拒绝；匹配声明可导入（仅签名，非解析器验证） | REAL_SQLCIPHER（test-only admission） |
| 外部审查包 | 报告51，固定 SHA、问题与待填审查字段 | NOT_REVIEWED_EXTERNALLY |
| ENOSPC / 掉电 / ARM / 敌意 PDF | 不模拟写满 runner 或用户设备 | NOT_RUN_REAL_ENOSPC / NOT_RUN / NOT_RUN_ARM |

仍不做：生产 wire 协议冻结、src/main writer/reader、密钥服务、Room/guard/PNBAK 变更、附件 UI、正式签名/发布。正式 B2 继续 NO-GO。

## 合并条件（任务书 §12）

PR-A 只含隔离测试、测试辅助构建、CI 接线与文档；在最终 PR HEAD 的全部必需检查实际 SUCCESS、红绿对照与新增方法在 API26/29/35 真实执行、无未解决 P0/P1 合并阻断、Release DEX/清单/Room10/PNBAK9/PK/签名版本未变后，按授权以普通 merge commit 合并（仓库 `allow_auto_merge=false`，不用 `--auto`、不改设置），合并后复验主线 CI。
