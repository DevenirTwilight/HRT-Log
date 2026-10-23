# PNMEDBK1：单一整合审批方案（NOT_APPROVED / NOT_READY_TO_FREEZE）

2026-10-23。本文是负责人一次审阅的完整候选，取代同一文件中先前各阶段重复的提案文字；原提案和所有失败/取消原件仍保留在 Git 历史与证据分支。不是生产协议声明、启用许可或外部安全审查结论。

## 本次只请求一个决定

**推荐批准按以下唯一 PNMEDBK1 候选开展默认 OFF 的格式特有实现**：生产 writer、独立 reader、密码输入/KDF/portable DEK 包装、完整集合认证，以及与既有预检/隔离重建的连接。参数、字段和拒绝政策作为本轮实现约束，任何安全相关偏离重新评审。批准实施不等于批准格式永久冻结：下面三项尚缺证据必须闭合后才能冻结；全库激活实现及正式入口启用仍未获授权。不要将本审批解读为允许跳过这些门槛。

本轮已经授权的格式无关代码继续完成；此请求不追溯修改 PNBAK1，也不授权真实医疗原件、永久删除、签名交付或 Release。若负责人要求直接批准“永久格式冻结及全库激活”，当前推荐 **暂不批准**，原因是联合资源和激活证明尚未完成。

## 当前事实与证据适用范围

|能力|实际状态|
|---|---|
|受限输入、Tink1.23.0/Keystore、Room14持久对象事务/恢复、空间及alias预算、完整快照、预检/密文隔离|#103/104/106/110/111/112 MERGED；不重写|
|真实隔离 SQLCipher 业务图、历史/trash/typed links 重建和独立比对|#113 MERGED e6112698；主线Android40009477772/Science40009478000 SUCCESS，三API原件已核；不是正式激活|
|Tink64KiB长度/随机读取/独立上游互通|现有原生合同已通过；下方精确长度公式有执行断言|
|Argon2id65536KiB/3/1|API26/29/35独立 KAT/孤立资源观测通过；不是联合档案接受证明|
|canonical manifest 编解码审查向量|新增 TEST_ONLY Python 编码/逆解码与独立 Kotlin 严格解码；不是生产集合 codec|
|生产 portable writer/reader/KDF/wrapping、持久整库激活|NOT_IMPLEMENTED / BLOCKED_PENDING_EXPLICIT_APPROVAL|
|真机/正式完整换机/外部审查|PHYSICAL_NOT_RUN / NOT_IMPLEMENTED / EXTERNAL_REVIEW_PENDING|

本次代码和原件记录：[最新HANDOFF/原始证据分支](https://github.com/DevenirTwilight/HRT-Log/tree/docs/med02-reconstruction-evidence-20261023)。本审批分支仅TEST_ONLY/文档，本地5项通过、CI_NOT_RUN、未合并，不计生产CI_PASS。

入口始终 feature_enabled=0；NOT_REVIEWED_EXTERNALLY；永久密文/alias删除 OFF。

## 1. 文件头与兼容

文件是严格 `80-byte header || outer Tink ciphertext`。无压缩、路径、链接、可执行代码或 archive SQL。全部整数 network byte order；未知版本/suite/KDF/flags、非零保留位、越界或物理尾部字节整次拒绝，必须先检查固定头和硬限额再派生密码。旧 PNBAK1 完全走原 reader；新 magic 不能回落到 PNBAK1，更不能把旧记录备份称为含附件的完整档案。

|offset|bytes|唯一候选字段|
|---:|---:|---|
|0|8|ASCII PNMEDBK1|
|8|2|version=1|
|10|2|suite=1：Tink AES256-GCM-HKDF Streaming，64KiB ciphertext segments|
|12|2|KDF=1：Argon2id v1.3|
|14|2|flags=0|
|16|16|新随机 password salt|
|32|16|新 archive UUID，RFC4122 network bytes|
|48|4|memory=65536 KiB|
|52|4|iterations=3|
|56|2|parallelism=1|
|58|2|reserved=0|
|60|8|精确 framed plaintext body length|
|68|8|精确 outer ciphertext body length|
|76|4|reserved=0|

## 2. 密码、派生与包装

密码严格 UTF8、不归一化、不修剪、不改大小写；拒绝孤立 surrogate、空串或编码后超过4096B。只允许头中的固定 tuple，不允许攻击者指定更昂贵参数，也没有弱 KDF 自动回退。Argon2id 输出32B root；HKDF-SHA256 salt=archive UUID16B，ASCII info 分别为 `notes.medical.backup.v1/outer`、`notes.medical.backup.v1/portable-dek`，各32B。root/DEK/transient buffers 按既有会话所有权清理。

Outer AAD = ASCII `notes.medical.backup.outer.v1` + NUL + 精确80B头。每次导出/失败重试使用新 archive UUID/salt/Tink randomness。writer只创建全新header，不接受旧header/root/IV作加密输入；不恢复portable wrapping attempt，不能在进程死亡后用旧attempt重新包装。reader只解包，不取得writer权限。portable envelope = AES256-GCM random96bit IV ||32B DEK ciphertext||128bit tag，共60B；AAD = ASCII `notes.medical.backup.dek.v1` + NUL +archive UUID16B+既有108B AttachmentObjectContext.encode()。每个保留 version 恰一 envelope，每 archive最多1000次包装；一次 writer attempt 的预算在调用 wrap前占用，失败不能在同一 attempt 重用预算或IV。

只携带原始内容 DEK 的 portable envelope，不携带源 Keystore alias、设备 envelope、数据库/PIN密钥。新设备按原有 PRIMARY 数据库密钥机制生成自己的本地密钥，按现有 DeviceKeys.Binding 的新 dataset/generation/operation/role 创建新alias/envelope；每次调用前原生持久预留一次性使用。不得把源设备别名复制为新设备密钥，也不得在缺钥时重建同名alias。

## 3. 确定性 manifest 字节（候选，未冻结）

UTF8，无 BOM、空白、尾部换行/垃圾。严格 Unicode，不做 NFC/NFD 替换。字符串只有 `"`、反斜杠分别用反斜杠转义；U+0000–001F 一律小写 `\u00xx`。其余 Unicode 原样 UTF8，拒绝 `\n`、`\/`、printable `\u` 等等价编码。UUID 是小写36字符；hash/envelope小写定长hex。数值为 signed64 十进制，禁止正号、前导零、负零、小数、指数及字符串/boolean强转；原始时间允许完整 signed64，不能取整或改时区。nullable字段必须显式 `null`，不能缺省。

对象字段按下表精确顺序；未知、重复、缺少、错序字段整次拒绝。数组中 UUID 按 unsigned16-byte network order 严格递增（不能使用 Java signed UUID.compareTo）；versions 按 document UUID、ordinal 排序；envelopes按version UUID排序。

|object|固定字段顺序|
|---|---|
|root|version,dataset,epoch,logicalVault,records,documents,versions,links,portableEnvelopes|
|record|uuid,kind,sourceId,state,trashId,evidenceSha256|
|document|uuid,revision,title,category,state,activeVersion,deletedUtc|
|version|document,ordinal,context,plaintextBytes,ciphertextBytes,plaintextSha256,ciphertextSha256,mime|
|context|logicalVault,objectId,version,attempt,dekHandle|
|link|uuid,record,document,revision,state|
|portableEnvelope|version,wrappedDek|

version=1；既有 enum/标题256 UTF16 units/MIME/正数ID与revision/ordinal限制不变。hash64hex，wrappedDek120hex。元数据行合计≤100000、versions≤1000、manifest≤8MiB、nesting≤8、完整集合精确匹配。语法 canonical 不证明 clinical facts/AEAD 合法：必须再复用现有 manifest/preflight、业务版本与全历史审核、Tink/ownership校验。不能另写一套临床语义取代它们。

可重现审查：`python3 tools/med02-format-review/canonical_reference.py --check`；`./gradlew -PjvmOnly :attachment-poc:test --tests '*PortableCanonicalReviewTest'`。Python编码器使用UUID.bytes排序，Kotlin独立递归解码器逐 token 验证，不调用编码器决定 canonicality；独立普通JSON源逐字段比较，固定SHA向量含NFC/NFD/non-BMP/控制字符、signed64两端、unsigned UUID边界、历史/trash/墓碑/link/envelope。错序/重复/未知/非规范数值、坏UTF8/escape、类型和上限对应明确拒绝测试。实现位于 test/tools，不能被当成生产 archive reader。原型DOM解码有深度/节点/字节防护，但没有证明在192MiB中与KDF/SQL同时工作；生产reader须在分配前校验字段与逐项预算。此处仍非独立外部审计。

## 4. 完整集合、历史与 Tink 边界

Body frames = `u8 tag || u64 payloadLength || payload`，严格顺序：一个tag1 manifest、一个tag2现有版本化 business JSON、全部tag3对象（按version UUID network bytes严格递增）、一个tag4 footer，随后必须是经过认证的EOF。对象payload = object UUID16B || version UUID16B ||u64 cipherLength||原始内层Tink密文。不得重复/漏/多/替换成员，不能跳过坏旧版本。

business JSON不被manifest整数语法重新编码：保留现有导出器产生的原始UTF8 frame bytes，并复用严格业务版本解码；structured rows是可信DOMAIN_TABLES所有row数组之和，不含manifest行，联合行上限仍计两者。

Footer payload =32B SHA256 +四个u64（frames,objects,structured rows,total framed bytes）；总frame73B。SHA输入 = ASCII `notes.medical.backup.set.v1`+NUL+精确头+所有此前完整frame bytes，排除footer本身；frames=N+3，total含footer并等于header plaintext length。哈希的真实性来自 outer AEAD，不是独立认证替代品。metadata、business JSON、portable envelopes、所有保留历史/回收站/RETRACTED/QUARANTINED关系均被同一outer认证覆盖。

Pinned Tink1.23.0：plaintext `n` 的 ciphertext长度为 `n+40+16*max(1,ceil((n+40)/65520))`；header40B、tag16B、首段clear65480B、后续65520B、空流56B。原合同实际检查0/1/65479/65480/65481/65520/65536/131073/1048576B，并保留独立上游互解/随机读取/篡改断言。Outer也必须使用同一 suite 精确核物理长度；检查长度不替代完整AEAD/EOF。

保留完整业务快照（现有schema12–13语义）、所有仍需保留版本、文档/记录删除状态、回收站、typed UUID和全部关系revision/evidence。DOSE_RECORD reference trash原行+exact RECORD ref/digest，LAB/APPOINTMENT payload snapshot，PURGED仅有明确来源墓碑证据映射null，不猜事实。

**唯一推荐的v1集合政策**：存在无法完整表示的retired dataset、unknown/orphan文件、未知旧协议或未完成journal时，整次拒绝并保留来源；不删除、不静默省略、不猜测关联。广泛多数据集支持是未来模型扩展，不偷偷混入v1。现有capture已有相应fail-closed边界。

## 5. 跨设备重建及激活：唯一推荐路径与当前阻断

已实现的 #113 私有RAM SQLCipher候选复用原插入依赖、完整历史审计、逐字段/行多重集独立比较、main/TEMP FK和全版本内层认证。新dataset/epoch/generation与源隔离；完整业务图保留整数键，typed UUID显式指向新namespace中对应行，任意重编号拒绝。附件context/logicalVault和事实不改；新设备物理归属/包装是新证据。PURGED/trash的整数分配下界必须在未来可写库证明，不能只看live MAX(id)。

**不能用现有候选直接激活**：DatabaseAccess当前只按Space.PRIMARY固定打开notes.db/key.wrap/notes.wrap，旧数据库句柄、后台写入、maintenance和文件路径都没有整库generation路由。现有AttachmentTransaction只证明单对象COMMIT。仅替换文件、复制单对象receipt、依靠epoch/filename或两次SQL提交无法证明全库原子性。

推荐未来本地方案：保留legacy notes.db作为加密control authority和旧库；新业务库/密文置于独立、受归属保护的generation，复用当前PRIMARY数据库key保护机制（新设备是自己的新PRIMARY key，不导出源key；不另设计KDF/SQLCipher格式）。一个原生authority事务CAS active dataset/epoch/generation/operation/whole-set proof并标完成，作为唯一逻辑可见性提交。候选依赖全部fsync/seal后才能进入该事务；不把跨文件rename/两DB提交说成原子。

还必须覆盖延迟请求：现有NotesRepository的deleteRecord(id)、化验/复诊及提醒等存在整数参数；旧SavedState、PendingIntent或排队请求在切换后重新get()可能取得新库，却携带旧整数。仅退休旧DB句柄、关闭缓存或重启进程不足以证明安全。激活前必须定义并验证dataset/epoch绑定的写请求及后台intent合同，在真正执行点拒绝旧scope；需要与UI/提醒负责人协调接口，本轮不擅自改这些模块。

这是**待审设计而非可执行激活协议**。实施前需明确新的非破坏性Room迁移（不在本文抢占版本）、DatabaseAccess唯一解析入口、old-app downgrade barrier、私有generation根及FD校验。旧generation的业务/附件写保护必须在authority切换前持久生效，避免排队旧整数ID写到新记录；RawData.unguarded/PNBAK1等maintenance不得移除或绕过退休保护。authority未提交时重启恢复旧写权限，提交后只开放已证明新generation；任何不一致先隔离、不得把库交给Repository。每个中间崩溃点、持久性和旧句柄拒绝都须原生反例/新PID验证，不能靠状态机图代替证明。

|durable state|必需事实|中断/失败|
|---|---|---|
|PREPARED/BUILDING|operation UUID、immutable archive commitment、fresh namespace、旧库pin、ownership、实际wrap预留|旧库继续；未知密文/alias保留收费，不自动重用|
|VERIFIED|outer authenticated EOF/footer/set、全部inner AEAD/hash/EOF、业务完整审计/独立比较和typed闭合|仅私有候选，无成功提示|
|READY|新库/文件/包装已durable，空间与旧scope复核，旧writer/maintenance屏障、降级规则已证明|恢复先协调屏障；不猜测激活|
|ACTIVATING→ACTIVE|唯一authority原生COMMIT选择新generation及completion|lost response按UUID核提交；未提交旧库、已提交新库；歧义fail closed|
|ABORTED/UNKNOWN|失败原因/预算消耗与归属证据|不清空旧库，不删未知对象，不自动resume或返还预算|

替代方案是直接清空/覆写current库或移动文件发布；不推荐：很难保留旧库、处理queued旧句柄、跨文件durability和历史身份。推荐方案增加路由/退休屏障与恢复维护成本，但隔离和单一authority边界更清楚。现在没有授权据此实现全库切换。

## 6. 联合预算、失败与批准门

建议wire≤1GiB、保留inner ciphertext总量≤512MiB、versions≤1000、per-object既有16MiB plaintext及16MiB+64KiB cipher上限、manifest8MiB、business JSON32MiB、业务+metadata≤100000行；调用方可以有更低工程限额，不能相信更大的输入声明。全部加/乘/取整必须checked signed64。

孤立KDF原件（source78bd4eaa、ZIP SHA/CRC已核）：API26约0.515s/采样heap72,875,816B/PSS177,831,936B；同源push API29约0.591s/heap73,850,336B/PSS206,227,456B；API35约0.480s/heap76,436,320B/PSS153,426,944B；ART均192MiB。probe先GC、10ms采样，**不是完整过程峰值/真机或联合内存通过**。

|必须同时纳入的资源|预算和拒绝原则|
|---|---|
|旧库和附件/alias|全部pin，不提前清除；已占用不再次误加到free-space需求，但必须计入全设备峰值|
|wire输入/可能的私有copy|已存在输入占用与新增copy分别计，不假设provider会释放原件|
|密文quarantine和unknown|按实际allocated blocks取整，包含已有失败/未知叶，不能只累加logical bytes|
|新SQLCipher/索引/WAL/journal|实际硬上限/最坏事务与checkpoint行为须证明；32MiB JSON不能直接等同32MiB DB|
|authority/rollback余量|保留旧、新与已消费wrap预算；不能把free space检查说成预留或保证后续IO成功|
|RAM|KDF实现开销、旧app/native SQL缓存、JSON索引/副本、manifest/比较工作集、stream buffers；各阶段实测联合PSS/ART，不能将孤立峰值相加当实测|

代码核查的具体容量差异：现有 AttachmentStorageBudget 工程默认是256MiB charged ciphertext、512 aliases；现有 restore preflight按每version两alias/两wrap规划，因此1000 versions需要2000个新alias，恢复时还要保留旧/未知alias。候选512MiB/1000是格式表示上限，**并非当前默认设备可恢复上限**。本次实施请求不批准自动提升本地限额，也不允许少存版本；超过实际可用资源必须整次拒绝。正式冻结前须证明合适的受审配置/联合峰值，不能把上限样本偷偷缩成256版本来报1000版本通过。

磁盘不足/写入中断/取消/应用锁撤权/数据集切换：停止新IO或包装、关闭/清理RAM资源，保留旧库和已创建密文；恢复只采用持久且完整证明，未知对象隔离。密码错误/坏头先拒绝，不自动降级；坏outer/inner/tag/hash/EOF/成员或关联拒绝整个档案；缺本地Keystorekey不得重建同名key掩盖丢失。空间及alias不足拒绝，不能悄悄漏历史版本。业务重建失败从不清空原库。

**冻结前尚缺三项，不能写成PASS：**

1. 生产独立writer/reader/portable wrapping的集合互通与错误密码/损坏/缺失/额外/取消/边界证明。目前只有manifest TEST_ONLY跨语言证据，没有正式outer归档的生产实现或互通证明。
2. 固定API26 512MiB/192MiB下完整上限档案、SQLCipher持久候选、旧库保留/rollback的联合RAM/磁盘/一次性wrap预算，以及不同设备Keystore恢复。孤立KDF和2MiB旧候选不能替代。
3. 本地durable authority、旧句柄/maintenance屏障、旧版本拒绝、allocator下界和真实崩溃恢复契约；需明确迁移和共享文件所有权后批准激活实现。

因此：具备提交**一次性、受限OFF格式实现授权**的明确候选，不具备直接永久冻结/激活/正式启用的条件。保持单一方案，不为获得某次绿灯更换KDF、跳审计或更改限额。下一最小阶段是获得该授权后交付生产writer+独立reader+集合认证并接既有预检/候选；全库激活等第3门单独完成证明后再实施。完整产品演示仍须后续合成导出→异设备本地密钥→重建→原子激活→逐字段核验，当前不能宣称已完成。
