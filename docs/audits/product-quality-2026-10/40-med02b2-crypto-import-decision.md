# 40 — MED-02B2 协议候选与生产准入决定

2026-10-14。候选研究GO；**生产NO-GO / BLOCKED**：没有独立专家审核、正式容器/设备及portable包裹批准、FS+SQL完整恢复、ARM验收。不是把SYNPOC01或DRAFT-ONLY-OBJ0!改名生产。

## 固定来源及版本

实际Maven metadata和GitHub latest核对Tink 1.23.0（2026-07-09发布），不是搜索缓存的1.20.0。[官方release](https://github.com/tink-crypto/tink-java/releases/tag/v1.23.0)、[设置文档](https://developers.google.com/tink/setup/java)、[Streaming规范](https://developers.google.com/tink/streaming-aead/aes_gcm_hkdf_streaming)于2026-10-14核验。Java11+，Android官方完全支持API24+；本项目min26，兼容性仍取本PR三API实跑，不用说明书冒充设备PASS。

Maven实际文件SHA256：

| artifact | 字节 | SHA256 |
| --- | ---: | --- |
| tink-1.23.0.jar | 2775464 | 5bcdc8798ce106f5145acb4a0e993c5e5861337b1e75ca88ceb6ab9da10fa512 |
| tink-android-1.23.0.jar | 3320451 | c656918451b01c45ce5b20c7b6d4c388f956f61b3a3528e769048c8944c42f9e |
| JVM sources.jar | 6153355 | 266a0c7f8cfcf007394a1573114cf8b54297391d85d74747935f359e9a051808 |
| Android sources.jar | 5010264 | 32ac8eaa5788ee1d724c97e454c233622ce9c01bbd7b996102a594ccbf8e89ca |

Apache2许可见THIRD_PARTY_NOTICES。只testImplementation/androidTestImplementation，正式依赖/R8不改；JAR字节不是Release APK增量。生产接入后的R8/体积/可复现构建成本NOT_RUN。上游sources仅仓库外审查，没有复制库实现。实验用subtle构造器固定参数，未来是否改为公开key/primitive API需审核，不能把它当已冻生产API。

## 方案对照

A：Tink AES256-GCM-HKDF，64KiB**密文**segment，新版本/重试独立随机32B IKM/DEK，随机32B salt与7B nonce前缀由库产生。
B：报告33 JCA/HKDF-SHA256，64KiB**明文**块，独立header/body/footer派生key及认证结束清单。

| 准入项 | A实际依据 | B实际依据 |
| --- | --- | --- |
| 维护/审查/许可 | 当前版本及上游原语规范已核对；不是本组合认证 | 自有组合草案；公开RFC/Wycheproof/JCA-Python向量，仅原语/转录证据 |
| 有界流式未知长度 | 新实验8KiB输入/64KiB分段；真正provider仍必须外层limit与EOF成功 | 原SYNPOC有界；新草案仅转录，不具备通用生产writer |
| 先全认证再读 | 库直接seek FAIL此产品合同：坏tail仍可读首字节；测试门全顺序认证至EOF后再给租约 | 草案规定全footer/块/hash/EOF；生产parser未实现 |
| EOF/截断/重排/缺块/复制/trailing | 最终segment标志参与nonce，完整扫描实验拒绝；不是显式仓库footer | 结束清单有total/count/hash及严格物理长度；组合待审 |
| 随机读 | 同FD顺序认证后seek按块重认证；capability门外解密、门内交付 | 原POC三API ProxyFD经验；新草案random-reader未实现 |
| 空文件 | 1.23 seek读空对象返回0，不能靠seek循环证明EOF；改用同FD顺序stream认证，返回租约后空read仍0 | 0字节草案向量已有，通过不等于parser |
| 可信身份/commitment | AAD派生绑定；新测试旧合法tuple拒绝可信hash；正式canonical transcript未冻结 | 140B头及三个域已转录；同crypto身份byte commitment模型继承 |
| 生命周期/重试 | 每操作新attempt/version/key，库nonce域；禁复用/续写 | 同要求，header/footer各key只一次；不重用计数 |
| Keystore/portable | 仅B1角色接口和合成key；未实现正式wrap/恢复 | 同；不能宣称JCA草案自动解决设备故障 |
| CAS/revoke/close | 复用AttachmentSession；实验memoryCAS，真实Room同步门继承B1 | 原型安全wrapper已B1修复；新正式格式未接线 |
| FS原子/崩溃 | 本批真实exclusive文件写+file force；directory seam/DB为模型，不能替代Room/硬件断电 | 原26/29/35 Os实验继承；生产协调器仍未实现 |
| PDF ProxyFD | 已证明旧POC可行；新候选只验证seek，Tink ProxyFD/PdfRenderer端到端NOT_RUN | 原POC不是新草案生产viewer |
| 满配/ARM | 16MiB边界合成实验；256MiB/1000真实库/ARM NOT_RUN | 同 |

推荐**优先继续A成熟Streaming AEAD + 经独立审核的上下文/完整认证门/可信元数据容器**，避免承担新的分块密码组合维护；不立即正式选定wire格式。B作为可验证备选，需专家评估额外容器/结束清单的成本与收益。成熟库不是免审理由；不因A可seek就省略全部对象认证。

## 密码预算、域及恢复

候选16MiB：A segment65536/tag16/header40，首块plaintext65480、后块65520，最多257次body GCM，每次新随机DEK派生域；nonce=7B prefix+u32 index+final byte。AAD通过HKDF info进入derivedKey，GCM块AAD为空；不是B的header/footer独立key结构。保守总GHASH输入块小于2^21（含长度块/末块取整）；这是容量推导，不是认证伪造概率、失败尝试或总体密钥使用安全证明。B最多256body+header/footer各1、预算见33。KEK包裹另域另限额，不能用body预算替代。

正式上下文必须冻结suite/protocol、crypto logicalVault/object/version/attempt/DEKhandle、format和固定编码；localDataset/physicalGeneration/recordEpoch/sessionEpoch不改旧cipher AAD。trusted SQL selected版本绑定plain/cipher hash及长度；只靠公开hash不是认证，完整tuple离线回滚仍无独立可信单调锚，root/同UID不是普通SAF威胁。

重试全新DEK/attempt/version，不恢复part写计数；已完整密文孤儿不自动激活。未来B3保留源crypto身份/cipher/DEK，fresh本地dataset/generation，新device wrap，portable绑定源crypto/archive，不绑定未知目标dataset。新完整包认证28业务表+typed graph+所有保留版本/Trash+key材料及final集合/EOF；PNBAK1仍schema9且不含附件，旧恢复fresh epoch/quarantine。

提升16MiB必须重算segment/GHASH/包frame/满库旧+新双份及SQL staging/WAL余量，不能仅改常量。候选256MiB/1000版本不是验收标准；无需推定64/128MiB。可携KDF/密码成本未获ARM测量与独立审核。

## 需独立专家回答

1. A的key-per-version、canonical context、可信长度/hash和full-scan组合是否足够绑定完整对象；有没有必要再加独立manifest？
2. 验证尝试/GHASH/随机salt及nonce预算、总体对象数量/重试熵、KEK包裹限额与轮换。
3. pinned FD和native cache后的撤权、损坏/错域/旧tuple重放、整个合法旧库回滚边界。
4. AppLock注册/切空间/备份及ProxyFD结果交付线性化；库内部key和native RAM不能承诺物理清零。
5. FS父目录FD绑定/NOFOLLOW、operation-owned清理、SQL提交与fsync/掉电、新deviceportable恢复。

本PR作者自查和自动测试不称外部专业认证；没有第三方安全审核签字。性能仅记录实际输出，算法缓冲上限不等于PSS峰值。
