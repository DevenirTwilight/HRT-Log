# 43 — MED-02B3 / B2-SECURITY-GATE2 基线与假设

2026-10-14。TEST_ONLY / RESEARCH_GO；不是路线B3完整备份，也不是正式附件库。

联网查询原始状态及输入文件SHA：[baseline.json](43-med02b3-baseline.json)。main cfe600eeb6efcca60668763a31969af07a2892b9，PR27/28/29已合并；PR30 OPEN、未合并，814d355c60396a9246ab6883ea70d3ccf67438cc，push/PR Android与Science全部完成success。主线Android38598762817/Science38598762944已完成success（文档push deep既有门控）。新分支feature/med02b3-b2-security-gate-20261014以814为parent，stacked base=feature/med02b2-v2-import-gate。新最终SHA/检查不能借用上述绿灯，见46与新PR。

任务书原样归档：[source](sources/med02b3/execution-task.md)。报告32–42、四份MED02B2资料来源和欧盟原稿/修订保留，不再称SOURCE_MISSING。历史报告中Room9/OPEN stacked是当时事实，以本基线实际主线Room10为准。

## 接口边界与继承/新增

| 接口/证据 | 继承 | 本次新增与限制 |
| --- | --- | --- |
| AttachmentSession / AttachmentTransaction | B1同步Room整事务commit与撤权共用monitor，IO不持门 | 新真实SQLCipher+密文IO IN_COMMIT竞争、提交前撤权与fresh PID；仍非生产AppLock registry |
| AttachmentOperations / AttachmentIdentity | B1真实PRIMARY CURRENT、幂等PREPARED、三身份及typed UUID /旧恢复 | 原始B1日志只PREPARED；独立synthetic_gate2_*表模拟有效状态，不改生产guard，也不称实际生产READY |
| AttachmentSchema | Room10八表、feature0/VERIFIED/VALIDATED硬拒 | 每fixture检查feature0、版本表空；没有放宽约束 |
| B2StreamingCandidate | Tink1.23真实IO，同FD顺序认证至EOF→随机读；SQL evidence原optional实验 | 新协调器从真实SQL读取expected长度+双hash，实际设备角色包裹、稳定FD接ProxyFD；仍test-only |
| B2ImportSandbox | memoryCAS/目录seam模型，父PR验收 | 新Gate2Store用native SQLCipher同步CAS、O_EXCL/NOFOLLOW、file force/目录fsync及journal重启，不将旧模型重记为新增 |
| RecordBackupCompatibility | PNBAK1 serialization9，真实B1 Room迁移/回收站链 | 新密文存在时实际旧备份恢复→fresh epoch/typed UUID，重开后隔离旧cipher；不实现完整附件备份 |
| PDF/图片旧POC | SYNPOC01公共API/合成图片及内存采样已验证 | 新四页Tink端到端，不复述旧POC为新候选验证；图片原回归保留，不增加生产viewer |

## 负向合同与可复现证据

新增native测试位于core/data/src/androidTest/.../gate2，合成库和alias只属于standalone library APK net.plainnotes.app.data.test。真实调用SQLCipher、AndroidKeystore、FileChannel CREATE_NEW+NOFOLLOW、Os.fstat/lstat、file/dir fsync；不动真实用户库/密钥/私有构建。

- post-commit确认丢失不能删active：保留显式unsafeCleanupForRedTest控制，仅测试协调器假设。脚本先跑预期1失败，再跑相同断言的修正路径；不是退化B1/B2或伪造生产漏洞。
- 8个提交前异常seam：旧CURRENT/哈希/版本保持；IN_COMMIT SQL真正rollback。提交后ack异常：新CURRENT保留，reconcile不重复CAS。
- 两个真实Room连接与两个线程同expected revision：最多一个CAS胜者，失败者只清own cipher。
- cleanup失败持久日志；parent替换保留foreign路径；persisted device/inode防重试误删；GC读lease与active保护。这个证据不声称抵抗root/同UID任意祖先目录替换。
- alias缺失重开不重建；stage/active AAD角色与跨对象包裹不能替代；真实SQL commitment错误拒绝。
- 真可靠pipe未知长度/虚假长度/closeWithError、持续零进展/16MiB+限制、取消；MIME不是格式鉴定。
- restart脚本分别在WRITE、IN_COMMIT、AFTER_COMMIT阻塞并force-stop测试包，新PID按SQL journal还原。普通JUnit运行同seam异常rollback，不冒称新PID。

API26/29/35为Android x86_64 emulator，证据等级REAL_SQLCIPHER_TEST / REAL_CIPHER_TEST / ANDROID_EMULATOR，不是ACTUAL_ARM_DEVICE。硬件掉电/真实ENOSPC/Root/敌意PDF/生产AppLock/外部专家都NOT_RUN；缺口详45。源文件哈希固定父提交；新增最终代码范围及实际运行结果见46。
