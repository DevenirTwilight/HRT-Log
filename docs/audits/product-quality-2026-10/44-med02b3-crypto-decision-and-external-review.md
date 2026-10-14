# 44 — B2-SECURITY-GATE2 密码候选决定与外部审查包

2026-10-14。RESEARCH_GO / IMPLEMENTATION_PENDING_OWNER_AND_EXTERNAL_REVIEW；生产NO-GO。
**NOT_REVIEWED_EXTERNALLY**：本轮是作者代码复核与自动测试，没有外部安全专家签字。32中历史AI交叉复核不升级为专业认证。

## 候选选择与实际代码

推荐继续A：Tink1.23 AES256-GCM-HKDF Streaming + 全认证同FD租约 + 可信SQL版本/字节承诺 + 独立审核容器与key角色。不是批准wire/key格式。B：报告33 JCA/HKDF header/body/footer为可验证备选，额外序列化/终结集合/组合协议的审计及升级负担较大；SYNPOC01及DRAFT-ONLY-OBJ0!不改名生产。

固定库来源/上游规范/版本digest见报告40；实际Android使用tink-android:1.23.0，JVM使用tink:1.23.0，全为test依赖。实验subtle API不是承诺长期生产API；正式接入须用受支持primitive/key API或说明维护风险，并记录依赖锁定/升级、安全公告与R8测试。当前没有正式Tink runtime依赖，因此生产APK增量/R8接入成本NOT_RUN，JAR大小不得代替。最终现有Release实际全DEX/manifest检查见46。

| 事项 | 可执行证据 | 仍缺 |
| --- | --- | --- |
| A段/final/EOF | 父B2StreamingMatrix 0/1/65480/65481/65536/65537/1MiB/16MiB；错key/domain、尾随/重排/缺块/tag；原直接seek坏tail反例保留 | 不是上游原语认证；正式容器/解析及错误策略待专家 |
| 全认证与二次读取 | B2StreamingCandidate sameFD顺序到严格EOF，再seek；Gate2Store强制SQL evidence | 块缓存已认证后重写相同大小未读块不等于全对象持续监测；锁定所有生产消费者未接 |
| 新PDF链 | EncryptedTinkPdfGate2 / TinkPdfGate2AndroidTest，真正Tink四页、非顺序ProxyFD、缩放/重开/owner revoke、坏tail、路径替换 | 正常PDF不证明恶意parser/密码PDF/无界native任务安全；无明文fallback |
| key角色与丢alias | Gate2Store实际AndroidKeystore AES256-GCM stage/active独立alias，role AAD，缺失alias重开不创建 | 合成alias属于测试包；非正式KEK服务/跨设备/硬件安全认证 |
| SQL+FS+授权 | 真实SQLCipher CAS/rollback、file+dir sync、三seam强停、新PID、完整auth后commit | 硬件掉电/闪存持久性/真实ENOSPC/跨库恢复待测；不是生产writer |

## canonical context 精确候选（未冻结）

[独立Python UUID转录](../../../tools/attachment-design-validation/gate2-context-vector.json)与Gate2ContextContract固定向量：ASCII `B2-SECURITY-GATE2-TEST-ONLY` + 00；u16BE protocol=0；u16BE suite=1；五个UUID16B网络顺序依次logicalVault/object/version/attempt/DEKhandle；u16BE format（合成opaque=0）。固定114B（marker28B、protocol/suite4B、五UUID80B、format2B），由独立向量验证；真实正式protocolID、格式allowlist、metadata版本/flags尚未分配。负向向量拒绝u16截短及UUID顺序替代，不证明完整密码组合安全。

SQLCipher元数据是selected版本的可信来源：明文长度、plainSHA256、cipherSHA256及完整crypto上下文。public hash不是认证本身；只有可信SQL选定+真实AEAD全对象验证一起满足承诺。localDataset只绑定本地查询、device envelope、epoch和CAS；physicalGeneration绑定owned FS；recordEpoch绑定医疗记录集合；session/process epoch绑定内存能力；它们不偷塞内容AAD以致跨设备恢复必须改旧原件。

测试device envelope=AES256-GCM，96bit随机IV+32B DEK密文+128bit tag，60B。staging AAD=context||ASCII `/staging/operation/dataset/physical`；active AAD=context||ASCII `/active/dataset/1`；固定UUID/角色仅用于合成协调器，正式编码需专家审查长度/字段域。两角色不同alias、不同随机IV。新operation/重试全新attempt/version/object/DEKhandle/32B随机DEK；从未续写partial，也不持久明文DEK。丢alias不能生成替代key冒充恢复。portable包裹/KDF归未来完整备份B3，本轮未实施。

## 预算与不能保证的威胁

16MiB候选、64KiB ciphertext segment、40B头、16B tag：最多257个body GCM invocation，新DEK派生域；每段plaintext至多65520，首段65480。nonce=7B prefix+u32 index+final byte，salt32；AAD是HKDF info、段GCM AAD为空。总GHASH块保守<2^21仅容量推导，不是总伪造概率证明。报告33备选header/body/footer分key预算继续有效，不混用A/B。

device KEK调用必须单独计数：每attempt至少staging1、成功再active1；失败包裹、轮换、恢复也计。报告33候选每KEK随机96bit IV≤65536次需轮换，仅生日碰撞估算约2^-65，不是完整GCM界，也不是本harness正式限额实现。攻击失败尝试、对象总量、盐/DEK熵、HKDF域分离与多用户聚合由专家定量审查。可携密码猜测成本没有ARM实测，不能借PNBAK既有KDF宣称新格式安全。

攻击者持有一致的旧SQLCipher数据库+密文+合法key句柄快照：没有外部可信单调锚，普遍完整rollback检测不可保证。Root/同UID hook/已解锁native/JVM副本、用户主动外传、系统云provider不在普通坏SAF输入同一边界。文件路径替换时固定FD读原inode符合租约契约；新open必须核对selectedSQL commitment。unlink不会撤销已有FD，必须靠session/owner能力与读lease管理；不承诺瞬间清除已交付字节或native内存。

## 交给外部专家的固定输入与问题

1. 父ref814d355及43-baseline文件SHA；报告32/33/40/41，全部公开RFC5869/RFC9106/Wycheproof固定来源；本PR最终SHA由46/PR正文固定，不使用活动分支代替。
2. Gate2Context固定合成向量、B2StreamingCandidate、Gate2Store、两stage/active域与Keystore角色；JSON scope明确TEST_ONLY。禁止真实医疗文件/用户密码/签名/私有构建进入审查包。
3. 可复现命令见46：红控制→绿路径、native SQL+FS竞争、三freshPID、TinkPDF及release隔离；CI报告/日志在公开Actions，结论标注实际级别。
4. 请审核A完整EOF+可信SQL commitment+同FD随机读是否需要额外manifest、canonical字段及套件升级策略、KEK/DEK/HKDF/GCM预算、错误oracle、空对象、目录FD/GC与commit/revoke线性化。
5. 请明确独立KDF/portable封装、整个合法旧快照威胁及接受边界；AppLock/worker/ProxyFD/bitmap结果撤权与native parser隔离，发表风险及整改而非仅“测试通过”。

评审交付必须含审查者身份/独立性、固定SHA、方法、风险评级、整改复测与签审意见。缺席时继续NOT_REVIEWED_EXTERNALLY；负责人批准不能替代专业审核，审核也不自动授权功能启用。
