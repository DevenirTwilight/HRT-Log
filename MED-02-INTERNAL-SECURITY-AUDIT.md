# MED-02 V1 内部对抗性安全审计

审计日期：2026-10-26（UTC）  
仓库：`DevenirTwilight/HRT-Log`  
唯一受审提交：`9fafe3c27d563c32f5638029ff084db3e1b59d7f`  
性质：独立于原开发验收流程的 AI 内部审计；**不是独立第三方安全认证，不构成 SECURITY_APPROVED、发布或启用授权。**

## 2026-10-27 工程状态更正与修复跟踪

#131 已于2026-10-26T20:18:50Z正常合并：受审HEAD为9fafe3c27d563c32f5638029ff084db3e1b59d7f，合并提交为03f724f41e91a1a866d98de31de673594d1105af。GitHub现有记录中该HEAD所列CI作业均SUCCESS，详见[保留快照](docs/security/med02-fixes-2026-10-27/evidence/pr131-merged.json)。原报告沿用冻结收尾文档的BLOCKED_ENGINEERING，**不再代表当前工程合并状态**。历史失败/LMK根因未证实不能因后续绿色结果改写为已修复。

2026-10-27有限修复：F01/F02/F03已实现；6eb350ba的真实API26/27针对性作业40464182728通过（含实际PID死亡、COMMIT后只读核对、阻塞管道worker/根事务检查）。F04主线程close阻塞在这些样本中NOT_REPRODUCED，其他平台/IO仍POTENTIAL；正式最终HEAD CI与合并状态以修复记录和最终交付回执为准。

后续 db6053b0 精确HEAD的 API29 CI 暴露 fcntlInt 非公开 API 回归（FAIL），Featherline 精确HEAD低内存作业也发生前台 LMK（FAIL）；原件保留。随后公开 FileChannel 修正 dbac92c3 在747ed6e2的API26/27/29/35原生针对性测试全部PASS（40469859893）；F04仍POTENTIAL。最终精确SHA完整CI尚未封账，不得称已合并或LMK根因已修复。

本轮获授权的有限修复状态另见[修复记录](docs/security/med02-fixes-2026-10-27/README.md)。下面保留原审计发现和当轮执行边界；F01–F03原始CONFIRMED不因代码修复删除，修复验证与F04的真实Android判定单独记录。原始报告和探针完整封存在[原evidence ZIP](docs/security/med02-fixes-2026-10-27/evidence/original-audit-evidence.zip)。仍NOT_REVIEWED_EXTERNALLY，不构成独立第三方认证，附件仍OFF。

## 结论与适用边界

发现 **3 项 CONFIRMED 实现问题、1 项 POTENTIAL 风险**。优先修复普通 AppLock 的故障恢复失败开放条件；在附件候选开放前，还应修复 UNKNOWN 状态的新写入口和 PNMEDBK2 输出流撤权遗漏。本轮没有证明 SQLCipher/Tink 可被破解、医疗记录可被跨库替换，或存在可用的 native 解码器代码执行漏洞；这不表示这些范围安全。

确认等级针对下文明确的行为：本轮运行了宿主 JVM 隔离探针，没有 Android SDK、adb 或模拟器，**没有执行 Android 仪器测试、实际 SQLCipher 事务、真实 SAF/provider 攻击、ProxyFD/native 解码、设备进程死亡或跨设备恢复**。AppLock 探针使用 Android 8 的实际 `AtomicFile` 类，但仍是宿主故障注入。其余探针的替身边界在各项中注明。

当前源码的 `PUBLIC_ENTRY_ENABLED=false`，附件元数据触发器强制 `feature_enabled=0`；F02/F03 是尚未开放候选实现的问题，不能称为当前 release 用户可直接利用的附件入口漏洞。F01 在普通 AppLock 路径上，**不依赖附件启用**。本轮未修改生产代码、开 PR、自动合并、签名、构建交付包、启用附件，未使用真实医疗资料。

参考过 `FINAL-ENGINEERING-CLOSEOUT.md` 的范围和历史失败记录，但没有接受其安全结论。该冻结文档当时记为 `BLOCKED_ENGINEERING`，三 API/低内存最终验收未闭环（已由上述2026-10-27合并/CI事实更正），Featherline 前台 LMK 为 `NOT_FIXED`。本报告不将这些历史执行结果计入本轮通过项，也不替代其工程阻断结论。

## 方法、证据与评级

从攻击者可以控制的输入追踪到最终提交或像素发布：不可信 content URI/流、恶意文件字节、损坏或被调换的备份、锁定与换库时序、丢失回执和进程中断。重点复核权限检查实际执行的位置、SQLite 提交边界、密钥生成与 unwrap 的分离、数据集/typed UUID/版本身份、完整保留集合及撤权后的资源所有权。以 MASVS 的 STORAGE、CRYPTO、AUTH、PLATFORM、CODE、PRIVACY 领域和 MASTG 的动态验证思路组织覆盖；未做完整 MASVS 合规判定。

- **CONFIRMED**：受审源码中的具体行为已通过本轮可复现隔离探针确认；不自动扩展为 Android 端到端利用成功。
- **POTENTIAL**：有具体路径和机制，但真实平台上的必要攻击条件尚未证明。
- **NOT_REPRODUCED**：本轮明确的攻击样本被拒绝；只覆盖该样本集。
- **NOT_TESTED**：本轮没有执行相应动态验证；阅读源码或历史 PASS 不改变此状态。

严重性为定性评价，结合医疗隐私/完整性影响和攻击前提，不伪造 CVSS 精确分数。HIGH 表示可以绕过访问控制而暴露健康资料；MEDIUM 表示有条件的数据重复、恢复/写入不可用或资源失控。默认 OFF 会降低 F02/F03 当前可达性，但不能消除将来开放前的修复需要。

证据目录：[docs/audits/med02-internal-2026-10-26](docs/audits/med02-internal-2026-10-26/README.md)。所有源码位置均对应上述冻结提交。原外审包 `source-inventory.json` 的 **132 项 SHA-256 全部一致**；这仅证明字节匹配，不代表 132 项全部做过逐行安全验证。重点阅读路径及其哈希另见 [reviewed-source-sha256.json](docs/audits/med02-internal-2026-10-26/evidence/reviewed-source-sha256.json)。

## 发现清单

| ID | 状态 | 严重性 | 具体问题 | 当前可达性 |
|---|---|---|---|---|
| F01 | CONFIRMED | HIGH，有窄故障窗口 | AppLock 忽略可恢复的 `.bak`，普通启动可能跳过 PIN | 普通 AppLock；不要求开启 MED-02 |
| F02 | CONFIRMED | MEDIUM | UNKNOWN 仍可选择新输入并创建新操作，随后覆盖待核对身份 | 附件候选/隔离宿主；正式入口 OFF |
| F03 | CONFIRMED | MEDIUM | PNMEDBK2 writer 未将输出流纳入 capability 撤权 | 完整医疗备份候选；正式入口 OFF |
| F04 | POTENTIAL | MEDIUM，有条件 | 同步资源清理可能阻塞锁定/生命周期调用线程 | 依赖真实 Android FD/provider 行为，未证明 |

### F01 — AppLock 对仅剩备份凭据的状态失败开放

**源码位置：** [AppLock.kt:28](https://github.com/DevenirTwilight/HRT-Log/blob/9fafe3c27d563c32f5638029ff084db3e1b59d7f/app/src/main/java/net/plainnotes/app/security/AppLock.kt#L28) 的 `enabled` 只检查 `store.baseFile.exists()`；同文件 51–57 行更新 PIN 时调用 `AtomicFile.startWrite()`，77–78 行又在 `enabled` 为真时才调用可恢复备份的 `readFully()`。 [MainActivity.kt:74](https://github.com/DevenirTwilight/HRT-Log/blob/9fafe3c27d563c32f5638029ff084db3e1b59d7f/app/src/main/java/net/plainnotes/app/MainActivity.kt#L74) 根据 `lock.enabled` 决定首次启动是否要求 PIN；`security/Session.kt:25` 的 `requiresAppPin` 在 `pinEnabled=false` 时返回 false。

**攻击前提与影响：** 普通模式（伪装关闭），先前已经设置 PIN；Android 8 `AtomicFile` 更新既有凭据时，将旧文件改名为 `.bak`，在新 base 文件创建之前进程中断。之后有权操作已解锁设备、但不知道应用 PIN 的人启动应用。只有备份文件时，代码把“仍有旧锁凭据”判成“没有启用锁”，普通医疗业务界面可能直接开放。这不是读取私有目录或 root 攻击，也不是伪装入口/PRIVATE 授权绕过。故障窗口很窄，需要 PIN 更新期间恰好中断；自然崩溃/杀进程可以留下同样状态。Android 新版本的写入策略及 OEM 差异未实测。

**本轮复现：** [LockAtomicProbe.kt](docs/audits/med02-internal-2026-10-26/probes/LockAtomicProbe.kt) 直接编译冻结的 `AppLock.kt`，加载 `android-all:8.0.0_r4-robolectric-r1` 的实际 `android.util.AtomicFile`。仅替换 Context/SharedPreferences；准备 60 字节合成凭据占位，在 `File.renameTo` 已完成真实磁盘改名后注入中断，未删除 base 来伪造结果。日志为：

```text
Injected death immediately after Android 8 AtomicFile backup rename
Interrupted-update fixture: base=false backup=true AppLock.enabled=false
Android 8 AtomicFile.readFully recovered original credential: true
```

占位字节未执行 PIN 解密；其意义是确认“可恢复凭据存在”却返回未启用。有效凭据的文件存在性分支完全相同。**CONFIRMED 是该失败开放判定；真实 Android PIN 设置→杀进程→MainActivity 无 PIN 展示仍为 NOT_TESTED。** 原始输出见 [lock-atomic.txt](docs/audits/med02-internal-2026-10-26/evidence/lock-atomic.txt)。

**最小修复建议：** 识别 base 或合法恢复备份存在为“需要认证”，优先按 `AtomicFile` 恢复语义读取；将“未设置”“已设置”“损坏/恢复失败”明确区分，恢复失败保持锁定。不要通过删凭据或重建密钥消除错误。至少增加 backup-only、损坏文件、更新前后中断和显式禁用的回归，并在 API26/27 做真实杀进程启动验证。

### F02 — UNKNOWN 状态允许新导入并丢弃前次核对身份

**源码位置：** [MedicalAttachmentsController.kt:88](https://github.com/DevenirTwilight/HRT-Log/blob/9fafe3c27d563c32f5638029ff084db3e1b59d7f/app/src/main/java/net/plainnotes/app/attachments/ui/MedicalAttachmentsController.kt#L88) 的 `startImport()` 只阻止 Saving/Checking 等状态，不检查 Unknown 或 `unresolved`，并立刻把状态改为 Idle；114–142 行重新调用 `gateway.newImport()`，新结果 Saved 时先清空 `unresolved`。[MedicalAttachmentsUi.kt:55](https://github.com/DevenirTwilight/HRT-Log/blob/9fafe3c27d563c32f5638029ff084db3e1b59d7f/app/src/main/java/net/plainnotes/app/attachments/ui/MedicalAttachmentsUi.kt#L55) 在 Unknown 时仍启用“添加”。`RuntimeLibrarySession.kt:79–90`/`AttachmentFlowController.kt` 为新尝试生成新的 operation/document UUID；底层幂等性针对操作身份，不能替上层阻止新 UUID 的重复保存。

**攻击前提与影响：** 附件保存已提交但回执丢失，或其他异常使结果进入 Unknown。用户受到失败/不确定结果误导，再次选择同一合成或真实附件并确认。新 UUID 绕开原操作幂等保护，允许产生两个附件事实并消耗两份保留资源；第二次成功后无法再从此控制器核对第一次操作。这需要第二次用户确认，**不是无交互写入，也未证明会复制服药/化验业务行或关联到错误患者**。进程/调度异常或用户离开可能触发此不确定工作流；provider 的读取发生在提交之前，不能仅凭它能更改文件字节就断言它能制造提交后丢回执，provider 端到端诱导未实测。

**本轮复现：** [UnknownProbe.kt](docs/audits/med02-internal-2026-10-26/probes/UnknownProbe.kt) 运行实际 `MedicalAttachmentsController`、实际 `AttachmentFlowController` 和实际 `AttachmentSession`。在 backend 边界用内存合成 COMMIT 事实模拟“先提交，随后丢回执”；没有使用 Room。其结果为：

```text
After synthetic COMMIT and lost response: state=Unknown, imports=1, commit facts=1
startImport while UNKNOWN returned picker: true; state=Idle
New operation/document UUID accepted: imports=2, distinct commit facts=2; state=Saved(listStale=false); first unresolved identity no longer checkable
```

**CONFIRMED 是实际控制器允许第二个写请求并清除核对身份；SQLCipher 双行、真实 SAF 故障及设备重启后的 UI 行为为 NOT_TESTED。** 不把合成 COMMIT 当成真实 COMMIT。输出见 [unknown.txt](docs/audits/med02-internal-2026-10-26/evidence/unknown.txt)。此外 `unresolved` 只存在内存，原件导入的操作 UUID 不像完整恢复操作那样保存为中性待核对元数据；进程死亡后无法依靠这份内存状态继续核对。

**最小修复建议：** 在 controller 和 UI 两层阻止 `Unknown/Checking` 或存在未解决操作时启动新导入，先对相同身份只读核对。对应用自己仍在执行的操作，不能仅凭一次 NOT_FOUND 就允许重放。保存仅含作用域和 operation/document UUID 的中性核对信息，不保存 URI、明文或认证能力；成功/明确终态后再清除。不可为了幂等而按文件名/日期猜关联，亦不应把全局内容哈希去重作为替代修复。

### F03 — PNMEDBK2 输出流没有注册到撤权资源集合

**源码位置：** [MedicalCollectionWriter.kt:16–24](https://github.com/DevenirTwilight/HRT-Log/blob/9fafe3c27d563c32f5638029ff084db3e1b59d7f/core/data/src/main/java/net/plainnotes/app/data/MedicalCollectionWriter.kt#L16) 创建 `sink` 和子 capability 后没有 `cap.own(sink)`；20 行执行外部 `out.write()`，直到 95 行 finally 才关闭 sink。相对地 PNMEDBK1 `MedicalArchiveWriter.kt:26` 明确注册了 sink。[MedicalLibraryGateway.kt:53](https://github.com/DevenirTwilight/HRT-Log/blob/9fafe3c27d563c32f5638029ff084db3e1b59d7f/app/src/main/java/net/plainnotes/app/attachments/MedicalLibraryGateway.kt#L53) 在 `.use` 内等待整个导出；取消只撤权 lease，不直接拥有该输出流。 [LibraryCollectionCapture.kt:15](https://github.com/DevenirTwilight/HRT-Log/blob/9fafe3c27d563c32f5638029ff084db3e1b59d7f/core/data/src/main/java/net/plainnotes/app/data/LibraryCollectionCapture.kt#L15) 将 writer 包在 rootWork 原生根事务中，72 行才调用 writer。

**攻击前提与影响：** 完整医疗备份候选向不可信 SAF provider 导出，provider 返回的目标在写入期间阻塞，例如不消费管道。取消、AppLock 或换库撤权后，sink 没有收到关闭；只靠下一次 `cap.check()` 无法打断正在进行的 `write()`。worker/FD 会继续存活；按调用链，根事务也可能长期占用，妨碍后续医疗数据写入和恢复。输出始终为密文，本轮没有发现撤权后可向 provider 输出医疗明文，也未观察到成功回执或既有数据被删除。

**本轮复现：** [CollectionCancelProbe.kt](docs/audits/med02-internal-2026-10-26/probes/CollectionCancelProbe.kt) 执行实际 PNMEDBK2 writer、实际密码学/KDF 与 capability，以受控快照和行计数边界提供一个空附件集合，用 latch 使目标停在首个 `write()`。撤权后日志为：

```text
actual PNMEDBK2 writer blocked inside target.write; after capability revoke: target.closed=false, worker.finished=false
after external unblock: target.closed=true, worker.finished=true; no successful receipt
```

**CONFIRMED 是 writer 的输出资源撤权遗漏；真实 Android 管道、SAF provider 和 SQLite 根锁阻塞的端到端影响尚未实测。** 该探针不是完整集合备份/恢复测试，快照及行计数替身不能证明任何真实医疗备份有效。见 [collection-cancel.txt](docs/audits/med02-internal-2026-10-26/evidence/collection-cancel.txt)。

**最小修复建议：** 仿照 PNMEDBK1，尽早把单一、幂等关闭的 sink 注册为 capability 拥有资源，失败/取消统一关闭，避免只依赖 worker 的 finally。关闭外部资源应在可控清理执行器处理，不能把 provider 的阻塞转移到主线程。真实管道写是否会被关闭打断必须单独测试；若不能，应采用有界的隔离 IO/进程方案，确保失去响应的 provider 不无限持有根写事务。不要为绕过阻塞而削弱集合快照一致性或真实 COMMIT 判定。

### F04 — 同步 close 链可能阻塞锁定调用者

**源码位置：** [AttachmentSession.kt:27](https://github.com/DevenirTwilight/HRT-Log/blob/9fafe3c27d563c32f5638029ff084db3e1b59d7f/core/domain/src/main/kotlin/net/plainnotes/app/domain/AttachmentSession.kt#L27) 的 ResourceLease 同步执行 `resource.close()`；91–96 行 revoke 顺序执行 release。[AttachmentInputStream.kt:77](https://github.com/DevenirTwilight/HRT-Log/blob/9fafe3c27d563c32f5638029ff084db3e1b59d7f/core/domain/src/main/kotlin/net/plainnotes/app/domain/AttachmentInputStream.kt#L77) 将 source 注册进该集合。`RuntimeLibrarySession.kt:99` 的 pause 和 `MainActivity.kt:123` 的隐藏通知、transfer cancel 等生命周期路径会同步进入资源释放链。

**攻击前提与影响：** 必须有某个真实 Android 输入/输出 FD 或其清理包装在 close 时长时间不返回，才可能拖住主线程上的锁定/暂停处理，造成 ANR 或可见界面切换延迟。**不能假设外部 ContentProvider 可以直接向客户端注入任意 Java InputStream.close 实现**；Android 的本地 FD 关闭可能及时完成，恶意 provider 单纯阻塞 read 也不证明 close 阻塞。因此本项为 POTENTIAL，而非已确认的 SAF 锁屏绕过。

**本轮证据：** [BlockingProbe.kt](docs/audits/med02-internal-2026-10-26/probes/BlockingProbe.kt) 使用实际 `AttachmentSession`/`AttachmentInputStream` 与合成阻塞 close，证明 revoke 在阻塞期间不返回；同时 `cap.check()` 已拒绝后续发布。见 [blocking.txt](docs/audits/med02-internal-2026-10-26/evidence/blocking.txt)。这证明同步清理机制，**没有证明 Android provider 利用、旧像素截取或医疗明文泄露**。现有 UI 先清除 viewer 引用，默认 FLAG_SECURE 也仍是有效缓解，不能忽略。

**最小修复建议：** 保留同步撤权及清屏的线性化点，把可能阻塞的外部资源关闭交给有界清理线程；在真实 pipe/ProxyFD/provider 上验证超时和并发关闭，并检查锁定帧是否先于清理出现。避免简单 `withTimeout` 包装阻塞 Java IO后就宣称底层工作已终止。

## 已尝试但未复现的攻击

### N01 — Tink 认证前泄露 Reader / 撤权后读取：NOT_REPRODUCED（限定样本）

本轮直接编译冻结的 `AttachmentObjectStreams.kt`、`AttachmentObjectContext.kt`、`AttachmentSession.kt` 和 `AttachmentInputStream.kt`，使用同版本 **Tink JVM 1.23.0**（应用实际依赖为 tink-android 1.23.0），执行 140000 字节合成内容往返、跨 64KiB 段随机读取，并验证：

- 错 DEK、错误 object AAD；
- 最后一个密文段修改、截断、添加尾字节；上述三项同时更新预期密文长度/SHA，使测试继续检验 AEAD 而非只撞上外层 hash 拒绝；
- 错误明文承诺；
- Reader 发放后撤权再读。

六个认证负向样本全部在返回 Reader 前失败，拥有的原始 DEK 被清零、输入 channel 被关闭；撤权后的读取被拒绝，目标范围清零。见 [CryptoProbe.kt](docs/audits/med02-internal-2026-10-26/probes/CryptoProbe.kt) 与 [crypto.txt](docs/audits/med02-internal-2026-10-26/evidence/crypto.txt)。这些测试以 `-Xmx192m` 运行；**不等价于 512MiB Android 设备/192MiB ART heap 的低内存验收**，不验证 Tink 内部复制密钥是否即时擦除，也不验证 AndroidKeyStore 或 PDF/image native parser。

### N02 — 意外开放附件：源码检查未发现，APK 验证 NOT_TESTED

源码中的两个 production Page 均在触及真实 runtime 前检查 OFF 门；debug host 与测试 provider 没有被主 Manifest 直接声明。原项目发布隔离测试 **5/5**、设计守卫 **61/61** 在本轮运行通过。它们是静态/合成守卫，**并未扫描本轮构建的真实 APK**，不能证明最终合并 Manifest、R8/DEX 或安装后组件一定隔离。没有发现可直接绕过 OFF 的现有公开 route；动态组件枚举和恶意 Intent 测试为 NOT_TESTED。

## 六个重点范围的覆盖与剩余风险

| 范围 | 本轮源码复核所见 | 本轮实际验证及边界 |
|---|---|---|
| 加密/密钥生命周期/SQLCipher/Tink/认证失败 | object AAD 包含 vault/object/version/attempt/handle；device wrap 另绑定本地 dataset、物理代、operation、role、wrap epoch；新建 alias 与 unwrap 分离，unwrap 缺 key 拒绝。SQLCipher 原始 key 在连接生命周期保留，close 后擦除；缺失/损坏 wrap 不走删库降级。备份为固定 Argon2id 64MiB/3次/1并行及 HKDF 域分离；源码未见弱密码学回退。 | N01 实际 JVM adapter 负向验证；F01 实际 enabled 判定。SQLCipher native、WAL/临时文件、Keystore 缺失/失效/生物识别密钥行为、硬件 key 和内存取证均 NOT_TESTED。不能从参数阅读推导完整密码协议安全。 |
| 不可信 SAF、恶意 PDF/图片、ProxyFD | 原件只读一次、有 16MiB 实际字节上限，不依赖 MIME/扩展名；PDF API<28 拒绝预览，较新版本第一页；图片先探测尺寸，2048边/16MiB像素限制。ProxyFD 回调使用 scoped authenticated read，失败清零；PdfRenderer 使用 dup 描述符。 | F03/F04 的宿主流行为；真实 provider 的阻塞 open/read/write/close、返回 pipe/proxy、FD 重用/关闭竞态、pdfium/BitmapFactory 畸形输入与压缩膨胀均 NOT_TESTED。有限图片尺寸不保证解析峰值内存有界。 |
| 事务原子性、UNKNOWN、重复写与恢复完整性 | 原件持久化按 PREPARED→WRITING→AUTHENTICATED→COMMITTED；真实 endTransaction 在 capability 发布栅栏中。恢复只切换加密根 authority，未完成候选不自动采用；失败对象保留。 | F02 控制器路径已确认。原生 COMMIT 丢响应、ENOSPC、WAL 截断、并发进程、每个死亡点及异常后的 reopen、真实状态核对均 NOT_TESTED。宿主合成回执不是事务证明。 |
| 多资料库/ACTIVE/RETAINED/跨设备 | v2 index 要求一个 ACTIVE、唯一有序库身份，聚合预算；全部历史版本/inner AEAD/outer EOF 验证后才返回 archive。捕获对比完整保留 inventory；恢复分配新本地 namespace/alias，保留 typed UUID，用一个 root COMMIT 选择 ACTIVE，其他 RETAINED 不按整数 ID 合并。 | F03 writer 资源所有权已测。A→B新增→全量导出→销毁来源keys→独立C恢复、混库/缺库/错角色/旧reader、重开/再导出等**全部 NOT_TESTED**；未把原流程的成功视频或日志当成本轮证明。 |
| AppLock/伪装/切库/旧请求/像素 | runtime 固定 generation/dataset/epoch，不重标旧整数；最终 bitmap state 发布经过 native read-capability fence 和 presentation epoch；隐藏先清 viewer 引用，再关资源。伪装 PRIMARY/PRIVATE 内存授权与普通 PIN 分离。 | F01 普通模式故障状态确认；N01 仅 capability 层撤权。真实 AppLock/Disguise.exit、跨任务 Intent、延迟 native render、Surface/GPU缓冲、recents/截图/IME/无障碍泄露均 NOT_TESTED。未声称 bitmap.recycle 能擦除 GPU。 |
| 低内存与进程死亡 | 16MiB 原件 scratch + 实际内容副本、64MiB KDF pool、多个结构化 JSON 副本、native 解码和 SQLCipher 连接都可能叠加；资源/磁盘配额不是峰值内存证明。中断后的保留候选和 UNKNOWN 会拒绝完整导出，属于保守保留策略。 | JVM crypto/KDF探针在192MiB max heap内完成，但未测 ART/LMK/设备总RAM/真实峰值。冻结文档中已有前台LMK历史失败，根因未证明且未修复；本轮不将其改成 PASS。真实杀进程、断电及恢复后逐字段/历史/关系核验均 NOT_TESTED。 |

未发现实际的跨 typed record 错误关联或保留库被静默遗漏的可利用反例；这些结论主要停留在源码检查，不能标为已验证安全。`LibraryLifecycle` 对 UNKNOWN 和无法完整表示的失败资源拒绝导出，而非自动删除/退款，是明确的完整性取舍；这会影响可用性。没有将“保守拒绝”本身误报成越权，也没有建议为了导出成功清除未知 alias/候选。

## 原工程声明的独立复核结果

| 原声明/关注点 | 本轮判断 |
|---|---|
| 认证完整后才可发放读取能力 | N01 的实际 object adapter 样本支持；archive 全集合/native层未动态证实。 |
| UNKNOWN 只读核对，不重复写入 | **附件控制器不满足，见 F02。** 不能只因底层 operation UUID 幂等就接受该声明。 |
| 撤权关闭所有保留资源 | **v2输出流存在遗漏，见 F03。** 清理不阻塞平台锁定也尚未证实。 |
| 默认 OFF、不能把 DB flag 直接置1启用 | 源码可确认，触发器仍强制0；未构建/安装APK。 |
| 多库真实迁移与故障恢复闭环 | 本轮未执行；保持 NOT_TESTED，不能从 source inventory/文档推导完成。 |
| 低内存阻断已解决 | 冻结文档并未如此声明；本轮也没有证据改写 NOT_FIXED。 |

## 复现与证据保全

在冻结 checkout、JRE/JDK21 和网络可访问公共 Maven 的独立 scratch 中：

```bash
bash docs/audits/med02-internal-2026-10-26/probes/run-probes.sh /absolute/scratch/med02-probes
python3 -m unittest discover -s scripts -p 'test_attachment_release_isolation.py'
python3 -m unittest discover -s scripts -p 'test_attachment_design_validation.py'
```

探针只编译列明的实际源文件，Android/UI/DB 的替身详见证据目录 README。依赖固定版本、URL、大小和 SHA-256 见 `probes/dependencies.json`；脚本下载并校验依赖，不上传源码/医疗资料。报告与测试脚本是新增审计文件；受审生产文件保持冻结字节不变。原始输出和经打包脚本的复现输出分别保留，未通过重复运行挑选绿色结果。

本轮未安装或执行 Semgrep、MobSF、CodeQL、依赖 CVE 全库扫描或完整 Android lint；已运行的是仓库现有静态设计/隔离守卫。没有把规则计数或测试总数当成漏洞覆盖率。

## 后续最小验证清单

1. 修复 F01 后，在 API26/27 实际设置/更新 PIN并在 backup rename 前后杀进程，验证新进程始终要求 PIN或进入锁定恢复状态；也测普通退出、配置重建和显式禁用。
2. 修复 F02 后，以真实 SAF→Room/SQLCipher COMMIT 后丢回执及 fresh PID 重开，证明只能核对旧身份，确认前不重读、不重复写；核对失败不能自动重放。
3. 修复 F03，使用独立恶意 DocumentsProvider 的不消费管道、阻塞打开/写入/关闭、关闭失败和迟到完成，验证撤权、根事务释放和有限资源上限；据此确认或排除 F04。
4. 在隔离模拟器执行 PNMEDBK1/2 错密码/末段篡改/成员缺失/错角色/混库，源keys销毁后的 A→B→C全字段及 typed/history/trash 对比；保留原 schema/预算/算法，不降低断言。
5. API26/29/35 的 PDF/图片/ProxyFD 竞态与畸形语料、512MiB设备/192MiB ART低内存、真实 LMK和死亡点恢复，以及物理设备 AppLock/伪装/截图/recents 验证。硬件断电、人工 TalkBack和 native fuzzing需单独记录。

这些是漏洞修复和补证建议，**不是本轮获授权的生产修改**。维持附件 OFF、外部评审未完成及 `RELEASE_NOT_AUTHORIZED`；本报告不得作为独立第三方认证、正式附件启用或医疗数据安全保证使用。
