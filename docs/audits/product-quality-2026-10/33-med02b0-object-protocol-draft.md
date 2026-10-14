# 33 — MED-02B0 加密对象协议草案与公开向量

2026-10-14。UNAPPROVED_DRAFT / SYNTHETIC_ONLY。没有生产 magic、生产 writer/reader、正式存储或格式迁移。SYNPOC01 保持原样，不得改名上线。这里的组合设计须独立安全专家复核；标准原语及互操作通过不等于组合协议安全认证。

## 选型与替代方案

候选：每次尝试独立256bit随机DEK，标准HKDF-SHA256域分离，AES-256-GCM固定64KiB分块，完整结束认证。兼顾ProxyFD指定块读取与受限内存。先全流认证再开放预览，成本O(N)，每次随机读重新认证该块；禁止将“打开时完整认证”称为持续全文件完整性监测。对象只能不可变，FD固定，权威SQL版本与会话令牌固定。

生产冻结前必须对比成熟Streaming AEAD（例如Tink）：Android支持、审查历史、seek语义、截断/EOF行为、依赖体积和升级政策。B0没有引入或实测该依赖，也不声称它自动满足完整结束清单/随机访问。若成熟方案能满足门槛优先使用；若不能，记录可重复评估后再批准本组合。

## 密钥与身份

每次导入/替换/失败重试：新operation/attempt UUID、新version UUID、新随机DEK32、新salt32；object UUID也重新生成。取消或崩溃后不能恢复旧加密计数器，不能续写旧part。已完整认证的密文可在journal协调下继续发布，不再进行同密钥同nonce加密。

身份为logicalVaultUUID、objectUUID、versionUUID、attemptUUID、stableDEKHandleUUID。主空间独立noBackup目录、Keystore alias与索引；不共用伪装空间密钥。physicalGeneration、recordEpoch、sessionEpoch不进入内容AAD：它们分别是文件组、结构化数据集合、授权生命周期，恢复/重新关联不应强制改变原件密文。完整备份认证它们的元数据映射。

设备KEK仅包裹DEK，envelope独立域，绑定crypto logicalVault/version/DEKhandle+目标localDataset/wrapEpoch/role。暂存operation wrap还绑定owned operationUUID与resource physicalGeneration，独立role；active device wrap以新nonce重新包裹并计入KEK总调用预算。portable backup wrap绑定source crypto身份+archive，不能绑定未知目标localDataset。append新envelope，验证解包等于既有DEK后再切换；旧alias直到新包裹验证且持久化后才考虑回收。Keystore不可用时禁止生成替代key冒充恢复。跨设备用独立便携包裹，不导出设备Keystore key。随机96bit wrap nonce候选最多65536次调用（失败也计）后轮换：仅nonce碰撞生日估计约2^-65，不是完整GCM安全界。确定性计数器备选必须先证明崩溃时原子预留不重复。

## 固定研究转录（不是正式wire规范）

所有整数无符号大端、UUID16字节网络顺序。固定140B unsignedHeader：

|偏移|长度|字段|
|---|---:|---|
|0|16|研究标记ASCII `DRAFT-ONLY-OBJ0!`，禁止生产采用|
|16|2|draft协议0|
|18|2|候选套件1|
|20|4|块大小65536|
|24|80|上述五个UUID依序|
|104|32|HKDF随机salt|
|136|2|实际格式枚举，fixture1仅合成|
|138|2|flags=0，未知值拒绝|

H0=SHA256(unsignedHeader)，**不含tag**，避免密钥派生循环。L为ASCII `HRT-MED-DRAFT0`后接00，共15字节。PRK=HKDF-Extract(salt,DEK)，三个K=HKDF-Expand(PRK,L||roleByte||H0,32)，role分别01 header、02 body、03 footer。与备份、设备包裹、便携包裹完全不同域。

- header：nonce12个00；AAD=L||01||unsignedHeader；空明文；tag16。该派生key只加密一次。
- body i：nonce=8个00||u32(i)，AAD=L||02||H0||headerTag||u32(i)||u32(actualPlainLen)，sealed=plain+16B tag。序号从0连续、不能重排/重复/缺块；除末块外plainLen必须65536。
- footer：独立key、nonce12个00，AAD=L||03||H0||headerTag；明文u64(total)||u32(count)||SHA256(按序原件)，44B，sealed60B。该key只加密一次；改footer必须新尝试。
- 候选对象布局：header140+tag16，然后每块index4/len4/sealed，最后footer60；EOF严格，已认证total决定精确物理长度216+total+24×count。不解析自由路径或可变无界字段。

header先验证tag及可信元数据的crypto vault/object/version/handle，固定大小解析attempt/salt；H0是unsignedHeader的自描述摘要，不当信任锚，限制分配；随后footer、每块tag、总长/块数、原件hash、可信metadata的整对象ciphertextSHA256、精确EOF全部通过才发布读权限。失败不返回部分明文，清响应缓冲并关闭能力；随机读也核对可信版本和会话授权。内容hash保留SQLCipher内，不作为公开文件名或自动跨就诊去重/关联依据。

## 密码学与资源预算（候选，未批准）

第一profile：原件≤16777216B，chunk65536B，最多256 body encryptions，header/footer各1次；nonce12B/tag16B无截短。unsignedHeader≤1024B、完整AAD≤1024B、footer≤256B。保守body GHASH input blocks≤256×(4096+64+1)=1065216 <2^21，包含AAD及长度块；这不是伪造概率证明，失败验证/总对象key数量/provider约束仍需专家确定。单次验证错误关闭能力，不能无限自动试错。

便携备份KDF候选Argon2id v19、64MiB/t3/p4（RFC9106第二推荐配置），**没有ARM成本测量，不是最终手机配置**；固定allowlist profile、预认证成本上限、一次任务、取消边界。salt≥16B。密码保持用户字节的明确UTF8规则，不trim或隐式Unicode归一化；≥12codepoint UX仅建议待批准，长度不等于熵。PNBAK1既有32MiB/t3/p1不变。RFC的32KiB KAT只是原语向量，不能当生产成本。

## 公开出处与转录证据

2026-10-14 HTTP200实际获取：

- [RFC5869](https://www.rfc-editor.org/rfc/rfc5869.txt)，SHA256 `7a40eb3835b35fc947eb12a2ed614db079d43b26e50dbc537c31fba16397089c`，Appendix A.1 HKDF Extract/Expand正向KAT。
- [RFC9106](https://www.rfc-editor.org/rfc/rfc9106.txt)，SHA256 `855c06f060379e34285e83a217e9069b5c72e161a1e54df9af5cd88dbb231f31`，§5.3 Argon2id KAT（secret/additional也纳入）。JVM BouncyCastle1.86验证。
- [C2SP/Wycheproof固定commit向量](https://raw.githubusercontent.com/C2SP/wycheproof/7a325932231bd7a724c791c8b0d65ea4d6d2f435/testvectors_v1/aes_gcm_test.json)，213177B，SHA256 `985e5ecc172e181eaf49e89508b9470dcf478002eb7e8559c707eb42dc97dfe7`。选tc91–93（含非空AAD、空明文）、130–132（tag翻位拒绝）。Apache2许可与引用见仓库NOTICE和工具目录LICENSE。
- NIST CAVP两条zip请求HTTPError，**未核实官方NIST在线来源**，没有把原POC熟悉的零AES向量新增标成来源验证。

`draft_vectors.json`新转录覆盖0/1/65536/65537字节，固定合成DEK/salt及按样本大小区分的UUID绝不能复用生产；不同样本header/派生key各异，但这不验证真实随机DEK/attempt allocator唯一性。Python cryptography50 AESGCM生成，JCA独立验证HKDF、header/body/footer、AAD篡改及公共负向量。两套实现一致仅说明此转录互操作，不证明协议无设计漏洞。stdlib三测试核对编码、序号/nonce/长度/hash及保守预算。generator仅测试工具，需要本地可选cryptography，CI不安装该库以运行生产代码。

复现：`python3 tools/attachment-design-validation/generate_draft_vectors.py --wycheproof <已核实原始JSON> --check`；`./gradlew -PjvmOnly :attachment-poc:test`。真实熵、所有API provider的新协议KAT、形式化攻击分析、外部专家审查、生产密钥与锁定集成尚未完成。

SQL `store_uuid`最终定义为新随机本地metadata dataset，**不是**header首个logicalVaultUUID；它与crypto logical_vault_uuid字段、physical generation及session/epoch明确分离（报告34）。同crypto version跨dataset必须相同原件byte commitments，不能借新scope复用versionID写不同内容。旧完整包恢复保持header/cipher/DEK，metadata映射fresh local IDs。新公开转录仍crypto身份，与此SQL命名修订无关。
