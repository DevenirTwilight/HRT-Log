# 49 — MED-02B4 内核 ProxyFD 撤权与跨进程写入者租约

2026-10-14。全部为 test-only（`androidTest`/`tools`），合成数据，只在 `ro.kernel.qemu=1` 的 x86_64 emulator 上运行。实测结果与原始日志见报告52（按最终 PR HEAD 的 CI 填写；未运行者不称通过）。

## A. 内核 ProxyFD（`KernelProxyFdGate3AndroidTest`）

调用链：合成 4MiB 明文 → Tink 流式加密 → 同 FD 完整 EOF 认证 + 可信 evidence → 绑定 owner capability 的租约 → `StorageManager.openProxyFileDescriptor` → **真实内核 FD**：`Os.pread`、`Os.lseek`+`Os.read`、`ParcelFileDescriptor.dup()`。回调只计数并通过租约解密，从不被测试直接调用（直接调用 `onRead` 的旧测试仍只算 DIRECT_CALLBACK 辅助证据）。PdfRenderer 经同一内核路径渲染的证据沿用 `TinkPdfGate2AndroidTest`（三 API 已运行）。

测得的安全契约（与内核缓存行为无关地成立）：
1. `pread(0)` 必须真的触发应用回调（回调计数增加），返回正确明文；`lseek(2MiB)+read` 同样。
2. 撤权后，**撤权之后才开始的读取，应用回调一律不返回明文**（`deliveredAfterRevoke == 0`）。
3. 撤权后读取**从未交付过的远端 offset**（3.5MiB，远超预读窗口）必须得到 errno（EIO），缓冲区不被写入。
4. 撤权后重读**已交付的 offset 0**：只观察并记录（`CACHE_OR_READ` 或 `ERRNO`）。若内核页缓存命中返回字节，只允许等于撤权前已交付的字节——**不宣称“撤权后没有任何字节可读”**，也不宣称能清空 OS 缓存。
5. `dup()`：拥有者关闭自己的 PFD 后，dup 出的 FD **仍可继续读取**（记录为风险事实：关闭自身 FD 不是撤权）；撤权后 dup 读取远端 offset 被拒；`onRelease` 只在最后一个 FD 关闭后发生。

红对照：`gate3_kernel_red=true` 时租约绑定到无关 capability（模拟“PFD 未与 session 绑定”的缺陷），撤权后远端读取返回明文 → 同一断言 `Revoked kernel read delivered new plaintext` 预期失败 1 次；绑定 owner 时同断言通过。

生产含义（需在 B5/PDF 与 AppLock 接线时落实）：不向第三方进程暴露附件 PFD；撤权必须在回调层执行而不能依赖关闭自身 FD；锁定/切换空间时同时关闭 renderer 与所有本进程 FD，并在发布位图/结果前检查最新 session（`EncryptedTinkPdfGate2` 已有该门）；已交付到内核缓存或其他进程的字节不可撤回，文案不得承诺可撤回。

## B. 跨进程写入者租约（`Gate3ProcessLockAndroidTest` + `Gate2PeerProvider`）

第二个真实 Android 进程：测试 APK 内 `android:process=":gate2peer"` 的 provider（仅 androidTest 清单，emulator-only，unstable client 调用，peer 死亡不连带杀死测试进程）。peer 只取得同一 per-operation fcntl 锁并用 CREATE_NEW+NOFOLLOW 创建写入者自己的叶子，**从不打开 SQLCipher 数据库**；SQL 状态（WRITING 与叶子 dev/inode）由本进程按写入者的方式记录。

首轮 CI（9cd28be）教训：最初让 peer 也打开同一数据库，API26 出现 `SQLITE_SCHEMA (17)` 与 `disk I/O error (522)`。原因是生产 `SchemaGuards.onOpen` 每次打开都 DROP/CREATE 触发器与索引，另一进程的连接因此遭遇 schema 变更；生产应用是单进程，从不在两个进程打开同一数据库。故改为上述设计，测试的仍是跨进程租约与文件所有权本身。API29 另有一处等待条件竞态（只等 WRITING+持锁，叶子可能尚未创建），改为等待 peer 显式写出的 parked 标记。

**缺陷修正**：POSIX 记录锁属于进程，关闭文件的任一描述符会释放本进程在该文件上的全部锁。c74b41d 的 maintenance 探测在写入者同进程内新开并关闭第二个 channel，会静默丢失写入者的 OS 锁。修正为进程内注册表：每个锁文件在本进程只有一个 channel，已持有时不再打开探测 channel；`LeaseState` 区分 `FREE`、`HELD_THIS_PROCESS`、`HELD_OTHER_PROCESS`；锁调用异常（不支持/失败）直接抛出，fail-closed，从不当作允许并发写。

测试：
1. `sameProcessMaintenanceProbeNeverDropsALiveWritersOsLock`：本进程写入者暂停在 WRITING，本进程 reconcile=`SKIP_LIVE_WRITER` 后，peer 进程看到的必须仍是 `HELD_OTHER_PROCESS`。红对照 `gate3_red=true` 恢复旧探测 → peer 看到 FREE → 预期失败 1 次；注册表路径同断言通过。
2. `peerProcessWriterBlocksReconcileAndSecondWriterButNotOtherOperations`：peer 持锁并持有其叶子；本进程 reconcile=`SKIP_LIVE_WRITER`，叶子、阶段、配额不变；本进程作为第二写入者与确认释放均被拒；另一个 operation 可在本进程完整提交；peer 未提交即释放租约后，reconcile 只清理其持久记录 dev/inode 的叶子，CURRENT 与旧对象保留。
3. `killedPeerWriterReleasesLeaseAndOnlyItsOwnLeafIsCleaned`：peer 持锁时被 `Process.killProcess`（同 UID、独立测试包）；内核释放锁后本进程 reconcile=`CLEANED_OWNED_INCOMPLETE`，只删除其持久记录 dev/inode 的叶子；旧 CURRENT 与外来文件保留；下一次调用得到新的 peer PID。

不做：`pm clear`、强停 `net.plainnotes.app` 用户实例、操作真实 PRIMARY 数据库。真机需另行批准测试设备与数据目录（NOT_RUN_ARM）。
