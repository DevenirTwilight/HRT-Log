# MED-02B0 独立一致性与生命周期代码复核

审查对象：PR #27 固定 HEAD `1c8d38553b30734565ac6c44751f0bb1dc19c9a7`。日期：2026-10-14。

本复核由不同 AI 审查代理独立阅读源码后形成，未先接收其他审查者发现。它不是外部专业密码学认证，不是生产验收。未编辑生产或 POC 源码、未运行 Gradle、未修改设备/真实数据；以下运行结果引用既有报告30，新增竞态属于静态可构造复现，尚未动态执行。

## 结论

POC 完整对象认证/逐块重新认证的实现符合其限定合成测试目标，现有报告明确记录范围。不能直接发布生产附件库。发布与撤权线性化、文件持久性与数据库权威指针、预览竞态、旧备份与回收站关联、真实跨设备恢复仍需正式实现和验证。SYNPOC01 不得改名后用于生产。

## 风险及可复现路径

### C-01 高：取消与发布不是原子授权操作

`tools/attachment-poc/src/main/kotlin/net/plainnotes/poc/ChunkObject.kt:88-96` 最后一次 `authorized()` 在 `fault(RENAME)` 和 `Files.move` 前，`active()` 回调没有锁/会话代次协议。确定性复现：初始 active=true，在 Stage.RENAME 故障回调仅令 active=false 而不抛异常，write 仍返回 Owned 和 .object。这不产生残缺密文，但生产若随后 READY/link 提交就可能在已锁定/已切空间后发布不应提交的操作。Stage.BLOCK 取消合同（ChunkContract:65-68）没有覆盖此窗口。

生产规则：每次操作固定 PRIMARY、storeUUID、generation、授权 sessionEpoch、operationUUID；同一发布/撤权协调锁内复核 epoch 与操作状态并提交数据库 READY/currentVersion/link。锁/取消递增 epoch 后等待已入临界区者完成；明确定义已经提交后的取消不能撤回事实，只撤销显示/读访问。不要在持锁阶段阻塞 SAF 读取。

### C-02 高：关闭对象句柄不撤销已打开的 Reader；图片无会话撤权

ChunkObject:26-34 的 Owned.close 只清自己 key；Reader:103-104 拷贝了 key、拥有独立 FD；Reader:144-159 只检查自己的 closed。复现：r=o.open(); o.close(); r.readAt(0,out) 仍可读。现有 closedReaderAndOwnerStayRevoked（ChunkContract:79）先 r.close 再 o.close，未证明 owner 撤销 child reader。ImagePoc.kt:25-37 打开 reader 后无 active/session 检查，返回的 Bitmap 也没有撤权所有者。

生产规则：会话撤权必须注册并关闭每个 reader/代理 FD/解码 worker，所有授权读取与对象句柄关联会话 epoch；显式区分“关闭创建者对象”与“撤销全部访问”。返回位图前进行授权发布，并由 UI 会话统一回收/清屏。不能依赖 Java key.fill(0) 宣称清除 provider/JCA/native 内部副本。

### C-03 高：PDF 撤权与结果交付窄竞态，native 解析无硬预算

EncryptedPdfPoc.kt:31-34 callback 前后检查 active，但最终检查后到 callback 返回仍可能撤权；46-53 render 最后 check 后到 bitmaps+=/返回也存在窗口，因为 close:59 在 monitor 之外设置 active=false。已有 close 同步清理可在 close 返回前释放位图和 FD，但不能宣称“active=false 后没有一字节/一帧交付”；一旦返回 Bitmap，调用者与 close 并发使用可能遇已回收对象。render 持 monitor 执行 native p.render，close 等 monitor；长时间/卡死 native render 使关闭完成无有界时限。实例初始化 reader 全认证（20）没有 active 参数，无法在完整验证中响应 AppLock。

生产规则：明确撤权线性化点及 UI 不展示旧 session 结果；输出及消费均验证同一 sessionEpoch。禁止多个 UI 持有可独立使用的原位 Bitmap；主线程撤销显示后再释放。PDF 使用受控 worker/进程及超时、页数/对象/像素/资源预算；跨进程 worker 不是自动安全沙箱，仍需审查 FD 权限/IPC/UID访问。锁定后的 worker terminate 与 FD 关闭需真实恶意样本测试。B0 不应以 POC benign PDF 成功保证恶意解析安全。

### C-04 高：文件 sync + rename 不等于掉电后持久原子提交

ChunkObject:93 通过重新打开临时文件 fd.sync，95 atomic move，没有父目录 fsync，无 operation journal、无 metadata READY 事务。文件名和 DEK 只通过内存 Owned 返回；崩溃于 move 后返回/包裹密钥/数据库提交前会留下完整但无可恢复密钥/元数据的孤立对象。Atomic move 同文件系统保证名字原子性不保证 rename 持久。catch:97 的 delete 结果不检查，也不 directory sync。

AttachmentRestartAndroidTest:40-47 创建完整 object-state/key-envelope 未同步；49-64 的真实 force-stop 实际发生在第二个 input.read 阻塞时，verify:73-77 证明 partial 不可读/旧对象保留，不证明 sync/rename/metadata/DB commit/fsync-directory 多阶段掉电恢复。

生产规则：已持久化 operation→新文件 CREATE_NEW→写完整 footer→同 FD fsync→独立全认证→rename→父目录 fsync→DB 事务 READY/link/currentVersion/operation提交。权威 DB generation 为唯一发布依据。失败清理只处理 journal 明确 owned 的路径/inode，失败删除留下可重试记录而非假称清完。加入每个边界前后 kill、ENOSPC、目录 sync 错误、WAL commit 成功但 UI 未收到 ack、恢复重入测试；硬件掉电与 flash 行为单列 NOT_RUN。

### C-05 中（私有目录攻击能力前提）：路径检查 TOCTOU 与读文件 symlink

ChunkObject:53 只检查 root 本身目录及非 symlink；祖先路径不验证，检查后路径可换。CREATE_NEW:67 能拒绝已存在终端节点，但不能阻止父目录被换。Reader:103 跟随 symlink 且未 fstat regular file 或绑定数据库预期文件描述符/inode；随机名称不能等同路径授权。catch:97 按字符串路径删除也受 root 替换影响。攻击者须能修改 app-owned root（同UID/错误内部组件/root），不是普通外部 SAF provider 的直接能力，不能夸大为远程漏洞。

生产规则：所有 target/root 在 noBackup 私有 app-owned 专用目录，不采用 URI/displayName/path 做附件引用；可信 openat/O_NOFOLLOW/CREATE_EXCL/fstat regular-file 或经审查 Android 等效 API；操作文件与 inode 固定，写入/reader 不共享可变路径。恢复包不得提供路径；只允许逻辑 UUID 映射内部路径。

### C-06 高：RecoveryModel 不代表原子 SQLCipher+文件恢复，epoch 不充分描述同类记录回收

RecoveryModel.kt:25-30 将 rows、recordEpoch、generation 依次赋值，没有同步且是内存模型；33 oldRecordOnlyRestore 清所有 link 并更 epoch，没有 DB/file/journal/backup 实现。模型 Link:7 仅 recordId+recordEpoch，无 record kind/store，不能生产使用；1000 list page:12-13 每次排序全部行，也不是数据库有界分页证据。

生产规则：链接身份至少 (logicalStoreUUID,recordEpoch,typedKind,stableTargetIdentity)，同 epoch 还须防永久删除后整数 ID 复用；可以为目标身份持久 tombstone/随机 recordUUID 映射（需新增表设计），不能仅 revision=1 与整数ID比较。appointment/lab 在回收站时 live row 不在原表，外键直接 CASCADE 到其原表会错误丢附件；采用独立目标登记/tombstone桥表或经审核 typedLink 事务校验+删除拦截，记录恢复同 ID 恢复附件，永久删除需要明确孤立/回收附件政策，不擅自物理删除共享对象。

### C-07 高：Keystore/便携恢复只覆盖合成封装，不能替代跨设备整体恢复

KeyEnvelope.kt:14-22 context 来自调用者，无固定长度/类型编码协议；24-35 POC PBKDF2 100000、盐16B，仅为合成 envelope。AttachmentPreviewAndroidTest:99-112 同设备删除alias/生成replacement证明包裹链，不是真实第二设备/安装完整恢复。RecoveryModel:26 incomingStore==store 不处理 fresh install 的新 storeUUID、physical generation/alias、用户 PRIMARY 授权。

生产规则：完整备份认证 logicalStoreUUID/recordEpoch/objectVersion/kek-domain/association graph，目标在明确 PRIMARY 授权下保留逻辑身份并新建本地 generation、设备 alias 和 envelope；不能通过重新生成 DEK 冒充旧密文恢复，也不能替换设备包裹就改变对象 AAD。KDF 固定受信 profile、密码长度/质量说明及实测成本；候选 Argon2id 需独立参数审查，绝不把 POC PBKDF2 直接作为生产结论。恢复提交前验证所有必需对象/关联/密钥，可重复执行且保持旧权威 generation。

### C-08 中：图片/native 与无明文缓存证据边界

ImagePoc:19-23 只识别短魔数；28-36 bounds/采样/分配后上限能限制典型尺寸但 bounds decoder 本身已执行 native，不能证明压缩炸弹、metadata/ICC/EXIF/动画、多帧或 parser CPU 内存严格有界。PreviewAndroidTest:50-52 只检查新 app files/cache≤20MiB 且 PDF magic/fullbytes，不覆盖已有文件被覆写、外部系统/provider目录、非PDF bitmap/中间cache或RAM/pagecache。报告30已正确区分受控目录观测。

生产规则：图片第1批仅获审查的静态 JPEG/PNG/WebP，验证 decoder 实际输出格式（magic与MIME本身不够）、原像素/尺寸、metadata长度、时间/内存上限与最大并发1；原件字节与EXIF保留。缩略图默认内存-only，禁止隐式库磁盘cache；生产 AppLock/FLAG_SECURE/截图策略/日志脱敏完整接入。配额及native预算必须低内存 ARM 实测，不把 current emulator PSS 当保证。

## 既有生产兼容行为（迁移/新备份须保持）

- NotesDatabase.kt:3 是 Room9，生产实体列表无附件。DatabaseAccess:50-53 仅注册 allMigrations()+SchemaGuards，无 destructive fallback；Migrations:67-74 8→9 添加 trash_item 和 (kind,ref)唯一索引、backfill，迁移链完整。
- DatabaseAccess:28-29 PRIMARY/DECOY 分离 db/wrap/alias；24-26 DECOY 为 legacy，不是当前伪装笔记 store。38-41 切 UI 不关闭仍在事务中的 DB；背景 PRIMARY。附件授权不能简单复用 select 作为撤权锁。
- DatabaseAccess:73-83 现有 wrap/DB 存在但 alias缺失、解密失败要求 KeyRecoveryRequired，不生成新key掩盖损坏；87 setUserAuthenticationRequired(false)，用户 AppLock不是Keystore的硬件认证绑定。附件应独立 key alias 与 noBackup root，不改变现有DB密钥。
- DataTransfer.kt:23-25 DOMAIN_TABLES 明确固定结构化记录顺序；RawData:32-34 restore/clear 在调用事务内暂移除所有 triggers 然后只调用 SchemaGuards.install。正式新增 attachment triggers 时必须审查该行为，避免旧 restore 意外永久丢新增guards；现阶段不改。
- RawData:59-79 校验tables→反向清理→按父子顺序插入→根据初始量+供应流水重算库存→旧schema regimen/trash升级→validateState。不会保留原 appointment/lab 行，只保留备份中的数值ID。未来 recordEpoch切换/附件quarantine须与这一个 transaction 同提交，不能在恢复成功回调中异步清链接。
- NotesRepository:615-619 旧 PNBAK1导出把 actual db version写schema、RawData.dump所有DOMAIN_TABLES；626-639 恢复支持旧schema1..9并拒绝newer，requiredTables随schema阈值变化。Room10直接进入这些代码会输出schema10并改变兼容判断；必须设计独立 record-only serialization schema固定契约，不能“只不加入附件表”就称旧格式无变化。
- BackupCodec:85-115 PNBAK1 magic/format1，Argon2id32MiB/3次/parallel1，salt16/nonce12/AESGCM256+MAGIC AAD，错tag映射WrongPassword；BackupValidation:10-14 上限file/json32MiB、100k rows、depth32、string1MiB。新完整包需独立magic/version、流式archive与受信KDF上限，保留这些限制/读取回归。
- Trash.kt:19-25 区分 snapshot(LAB/APPOINTMENT等) 与 reference(RECORD/PERIOD/CORRECTION)；39-44 reinsertion使用原keys、CONFLICT_ABORT。NotesRepository:574-579 就诊删除将appointment/questions/visitpack完整打包后删除live rows；794-798 lab及context同理；151-198恢复父子同keys最后删trash item。不能用普通直接FK cascade导致附件丢失，更不能把过去就诊复制成另一套记录。
- NotesRepository:211-225 snapshot purge真正丢payload；服药ledger等保留HIDDEN。医疗附件trash/版本保留/永久删除必须新增明确政策，保留共享引用与已备份副本界限。

## 验证分级及生产准入建议

**已验证（引用报告30，未由本复核重跑）**：标准AES256向量、完整结束清单/块篡改、随机读取、三API benign PDF/图片、同设备Keystore链、真实process force-stop于阻塞导入、受控目录观测。

**仅模型/注入**：RecoveryModel epoch/quarantine/1000 list、ENOSPC、故障BLOCK/SYNC/RENAME；不代表满盘/真实SQLCipher事务或断电。

**本次静态确认/可构造但未运行**：C-01 RENAME seam取消后发布、C-02 Owner关闭但Reader继续、PDF结果交付竞态与无限native等待、RawData新增trigger遗失风险。

**仍需独立专家/实机**：生产AEAD协议及域分离/上限/KDF审核，独立恶意格式语料/隔离解析审核，真实ARM低内存/压力与超时，SQLCipher+文件多阶段崩溃一致性/故障存储/掉电/新设备完整恢复。AI交叉复核不减少该门槛。

建议 B1元数据/key/隔离不开启入口；B2审查通过的协议与journal只合成自动验收；B3完整备份/恢复及旧PNBAK1 epoch原子隔离通过；B4只图片受限 UI 与安全纠错。前三项成功并负责人批准后才考虑默认启用。PDF/export延后至恶意文件、native预算、ARM和隐私提示全部具备。


## 第二轮交叉复核（独立发现完成后）

阅读 `/workspace/scratch/med02b0-crypto-review.md`。另一审查者同样为 AI，仍不是外部安全认证。

### 一致及补充

- 同意其 findings 1/2/4/8，与本报告 C01/C02/C04/C03 相互独立一致：发布撤权窗口、Reader独立key、无dirsync/journal、同步native等待。
- 同意其 finding3 的反回滚边界：元数据CAS/versionUUID只能拒绝与当前权威状态不符的旧对象；攻击者回滚整个离线DB+objects+本地counter时，无独立单调锚无法保证检测。不能把 generation 或 UUID 当单调计数器。
- 同意 finding7：初次全认证是打开时快照。未读块/末尾在打开后被改，不代表每次随机读取会立刻发现“全文件现在不完整”。每块AEAD仍防未认证明文释放。生产不可变对象+固定FD+权威版本是必要假设。
- 同意 finding5/6/9：便携PBKDF2仅POC；协议AAD必须canonical编码；KEK nonce累计调用预算独立于每对象DEK；正/负全格式固定向量与独立provider比对有必要。引用公开向量原始出处需已核验，不把熟悉的数值当出处证明。
- 无实质分歧。严重级别为“生产准入阻断风险”，不应解释为已经上线的可远程利用缺陷。

### 对 root 拟定生产规则的复核

会话generation/capability共用publish+revoke mutex、provider FD关闭取消、isolated worker deadline、Reader/Bitmap scope registry、durable operation reservation/file+dirsync/no-replace/DB CAS、恢复pointer DB-last、重复operation幂等、target UUID registry+tombstone方向能覆盖本次主要风险，需明确以下细节：

1. **session与对象generation不得混用**：sessionEpoch只表授权生命周期；physical generation只表设备上当前文件组；logical store/recordEpoch/versionUUID是数据身份。内容AAD不可直接绑定恢复时要改变的本地physical generation；完整备份保留逻辑身份，恢复目标physical generation另经认证metadata映射。
2. **撤权时限不能靠关FD承诺**：SAF provider可能阻塞到无法及时唤醒，关闭descriptor是必要动作而非各设备可证明的同步中止保证。共享锁不围住长读/全认证/native render；native worker达到deadline可终止进程，撤权先清UI/禁止结果接受，再等释放。验证 callback在撤权后返回/Bitmap回收与消费竞态。
3. **no-replace必须实现验证**：Java ATOMIC_MOVE的目标存在行为不能作为跨Android安全“不替换”合同。random UUID降低偶撞不替代语义；确定性测试预置target并保持旧内容，选受支持的无覆盖提交原语或独占root+操作协调器并记录其同UID攻击边界；禁止REPLACE_EXISTING与共享可写object。
4. **数据库是唯一发布权威**：发生rename+dirsync但DB CAS未成的object只为未提交cipher orphan，不能被目录扫描自动显示；READY行文件missing/corrupt进入明确状态而非重新空造。DB commit完成而ack丢失时同operationUUID查询既有结果，不能重加document/version/link。
5. **GC也要代次并发保护**：backup/read leases pin已选version；替换/回收不能立即删旧key/file；restore提交后不能让旧operation recovery删除新generation对象。GC先检查同一generation+UUID+引用/lease、记durable GC intent再unlink+dirsync，不按扩展名全盘扫删。
6. **registry持久覆盖回收站**：升级时不仅扫描live lab/appointment，还须合成验证trash payload中的typed IDs及其子对象、reference类型隐藏记录。尚未能验证的payload不得强行建立attachment link。永久删除保留不可复用targetUUID墓碑；旧整数ID重生必须新UUID。
7. **旧备份换epoch要覆盖所有替换入口**：不只restoreBackup，还包括RawData.clear和overwrite imports/全清/重建空间等会重用record整数ID的行为。新adapter设计列出入口并明确同事务quarantine；新设备完整恢复保留源epoch但需要明确PRIMARY目标授权。

### RawData保护与PNBAK1 adapter具体验收条款

- `RawData.unguarded`未来不能“删全部trigger后只恢复legacy”。设计一处完整保护安装清单（SchemaGuards+attachment guards+registry guards），在同一SQLCipher事务finally恢复；异常回滚、触发器列表/SQL完整性断言、下一次连接打开、旧restore成功/失败后不可变规则都应测试。或者审查后严格限制被移除的trigger白名单，保留附件guards并先quarantine以避免restore顺序冲突；不是取消保护。
- 冻结独立record-only serializationSchema=9及字段allowlist，Room10不再被直接写进PNBAK1 schema。新导出的record-only包不含attachment_document/version/link/operation/registry/keys。新app继续读旧schema1..9；旧app读取新包兼容性必须用固定旧app/snapshot实际验证，没验证标不保证。不能依靠 `SELECT *` 让未来列自动进入旧格式。
- 旧PNBAK1恢复必须先完成原AESGCM/限制/表校验，records替换+旧attachment link quarantine+recordEpoch更新+必要registry状态在**同一事务**成功或回滚；错误密码、schema newer、无效ledger/trash/预算拒绝不得改旧epoch/附件。
- 保留旧恢复的父子写入、初始库存+ledger重算、RegimenHistory.seed、Trash.backfill、validateState及提醒重建；新元数据列/表不可扩大旧32MiB/100k row等限制，不可改变PNBAK1魔数/Argon2参数。
- 新完整包使用独立magic/版本与最终manifest认证，恢复每个对象/关联/key envelope后才切DB权威generation，未到最终manifest不得report成功。旧generation及device envelope至少保留至新指针可重启打开且恢复完成状态持久化；之后GC须上面的lease/operation规则。
