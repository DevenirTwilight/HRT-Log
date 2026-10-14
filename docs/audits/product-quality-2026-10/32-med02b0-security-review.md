# 32 — MED-02B0 生产附件库安全审查与实施准备

2026-10-14。DESIGN_AND_SYNTHETIC_ONLY / NOT_EXTERNAL_CERTIFICATION。基于PR27已验收固定`1c8d38553b30734565ac6c44751f0bb1dc19c9a7`，默认已验证`c3bf2cbce6663167814aa48a0e8e9e1300adb2e4`，独立stacked分支。不合并PR27或本PR，不启用附件库。

## 结论及交付索引

POC证明限定合成数据上的AEAD/随机读取和公共Android预览可行，**不能直接生产化**。本次不同AI审查者先独立阅读再交叉核查，第三位形成合成迁移草案；主审补充确定性运行证据。没有外部专业安全认证，没有生产迁移/正式新format、用户附件、真实医疗数据、version/signing/build26变更。

- [33 加密对象协议草案与公开向量](33-med02b0-object-protocol-draft.md)：新HKDF域分离组合候选/固定转录；不是SYNPOC01改名。
- [34 Room10迁移草案](34-med02b0-room10-migration-draft.md)：typed identity桥/墓碑、generation、append envelope、22 SQLite合成测试。
- [35 完整加密医疗备份与恢复](35-med02b0-medical-backup-draft.md)：独立格式、流式完整集合认证、SQL权威最后commit、旧包epoch隔离、14失败模型测试。
- [36 配额、纠错政策、B1–B6](36-med02b0-product-policy-and-pr-plan.md)：候选标准/测量计划、默认关闭与审批。
- [37 B1可执行提示词](37-med02b1-executable-prompt.md)：需相应批准后才能修改正式Room等生产子项。

## 基线与审查方法

任务开始实际核查：PR25已合并merge c3bf2cbc、PR26已合并merge051ee22，默认Android38527627941/Science38527627946 success。PR27 OPEN/未合并，head1c8d385 push Android38533090943/Science38533090557与PR Android38533096532/Science38533096549全部success；PR Science实际深度通过，push doc-only深度按既定gate不运行。未更改R15或MED01工作树。

两份原始独立AI报告完整保留：

- [密码学AI审查者](med02b0-peer-reviews/crypto-ai-peer-review.md)：DEK/nonce/AAD/完整结束认证、密钥封装/KDF/rollback及budget。
- [一致性AI审查者](med02b0-peer-reviews/consistency-ai-peer-review.md)：session/preview竞态、文件持久性、Room/trash/旧备份生命周期。

均先独立完成再阅读另一份，addendum有交叉映射；无实质分歧。原报告“动态NOT_RUN”是当时审查者未执行的准确状态，下面主审新增运行证据另列，不能回写成原审查者已经运行。它们是不同AI代理，不是外部安全专家，也不被称为认证。

## 威胁模型

保护app私有加密存储/完整导出包泄露后的内容保密与篡改拒绝；恶意SAF长度/MIME/输入失败、坏文件/native资源攻击、锁定/space switch/并发取消、掉电或操作部分完成、错误备份/ID复用。攻击者能读取/替换对象时依靠可信SQL selected version/key/identity进行验证。拥有整个旧DB+keys+object一致快照的离线rollback不保证可识别；没有可信独立单调锚。root/同UID可改私有root或hook进程需清楚列前提，不能当普通SAF远程能力。解锁期间RAM/native/provider副本、屏幕、用户主动外部导出、既有backup副本不保证法证清除。没有INTERNET，主/伪装域不共享附件；B0不改变现有权限/锁定。

## 交叉发现与验证矩阵

|风险|双审交叉|主审证据|生产必须完成|
|---|---|---|---|
|最终authorized检查后撤权仍publish|crypto1 / C01|新JUnit在RENAME seam撤权，文件仍生成，确定性PASS证明缺口|publish/revoke共用短gate/session epoch；SQL READY/link commit是线性化权威；长IO不持锁|
|owner.close仍可读取既有Reader|crypto2 / C02|新JUnit open→owner.close→read原字节成功|区分close与授权revoke；集中capability registry撤销reader/ProxyFD/native/UI结果，交付及消费验epoch|
|整体合法旧tuple回放|crypto3 / C06补充|新JUnit完整旧tuple可认证；可信new version拒绝旧对象|fresh immutable version+DEK+SQL CAS；明确整个vault旧快照无法一般检测|
|atomic move不保证no-replace/dir durability|crypto4 / C04/C05|新Android实验link existing拒绝、dirfsync、leaf nofollow，结果按最终CI记录|owned dirs/FD/fstat/祖先防替换、journal/file+dirsync/CAS、cleanup错误保留；硬件断电仍NOT_RUN|
|PDF交付竞态/native关闭无硬时限|crypto8 / C03|源码复核，既有良性POC不能排除|受限worker/deadline/恶意样本/锁定UI先撤显示；不要保证瞬间RAM擦除|
|图片原语限尺寸不等于恶意parser安全|C08 / crypto8|既有合成格式结果仅继承报告30|静态format allowlist、metadata/动画/pixel/native资源、并发1、无diskcache、ARM|
|便携PBKDF2与wrap域/budget非生产profile|crypto5/6 / C07|公开HKDF/GCM/Argon2id KAT互操作；不测密码熵/成本|专家定域/GCM限制、允许KDFprofile及ARM成本、wrap轮换/newdevice|
|epoch不足防同epoch整数ID复用；trash FK危险|C06 / crypto交叉|22 SQLite合成测试含同ID新UUID/trash continuity|typed UUID registry+tombstone、实际Room hooks、oldrestore/clear同事务quarantine|
|RawData删全部trigger、Roomversion进PNBAKschema|C06/C07兼容 / crypto交叉|现有生产源码读取；生产未改|完整guardregistry/白名单、固定serialization9 adapter、旧包回归|
|GC/恢复/租约与commit ack失败|C04/C06 / crypto4|14模型测试+多失败seam|真实SQLCipher+文件实现/ENOSPC/kill/lease GC，不能靠模型赋值宣称atomic|

上述新POC审计测试保持原accepted source不变，**通过表示已复现问题，不是已修复安全漏洞**。生产功能目前不存在，风险是准入阻断，不夸大为上线远程漏洞。

## 运行证据与复现

新增JVM7：3确定性audit findings +4primitive/draft test methods，继承28，合计35零失败。RFC5869A1、RFC9106§5.3及固定C2SP/Wycheproof6正负cases；Python cryptography50生成0/1/65536/65537转录，JCA独立核对全域/header/footer/body/AAD篡改。不是新生产serializer、没有随机密钥质量/组合安全证明。源码仅新增testImplementation JSON/BC，不引入生产依赖。Stdlib39项：22合成SQLite +14backup state model +3framing/HKDF/budget；0新skip。初次CI入口loader路径配置失败，改独立TestLoader/top_level，未弱化断言。

复现：

```
python3 -m unittest discover -s scripts -p test_attachment_design_validation.py
./gradlew -PjvmOnly :core:domain:test :pk-engine:test :importer:test :attachment-poc:test --rerun-tasks --no-parallel --max-workers=1
```

API26/29/35 test-only新增2 filesystem方法：合成专用noBackup目录，fsync文件/目录、link EEXIST不覆盖+同inode、leaf O_NOFOLLOW拒绝。不操作真实data；只运行经ro.kernel.qemu明确attestation的模拟器。不能将fsync返回成功当flash掉电认证，祖先目录FD绑定/真实no-replace协调器仍未来实现。

完整Android/JVM/lint/API35与适用Science最终SHA/check链接在本报告末尾最终CI记录填入，保留全部旧B2/B3/B4/R15/MED01测试与既有条件skip。不取消运行中CI。报告30既有PDF/图片/Keystore/force-stop与内存采样仅继承，不误称本轮新增测量；没有真实ARM/容量基准或独立安全专家。本轮未运行真实掉电/满盘/正式新backup，统一NOT_RUN。

## 未解决风险与正式准入门槛

1. 专家审核并冻结协议/KEK域/实际GCM概率与KDF成本，成熟Streaming AEAD对比；B0不是密码认证。
2. 实际session revoke所有输出/worker与并发顺序、native hostile格式/限时/缓存私有目录无明文完整性。
3. 真Room9→10/SQLCipher migration/onOpen/trigger/旧PNBAK adapter/临床回收站/所有overwrite入口，不能直接用tools DDL部署。
4. durable import/restore跨资源journal、file+dirsync/no-replace/CAS/GClease、真实kill/ENOSPC/重启，硬件断电与新device仍需实测。
5. 完整新package全图/结束认证+原数据保留+portable key恢复，关联稳定UUID，旧包必须换epoch/quarantine。
6. quota/像素/deadline/低端ARM与四语无障碍正式产品验收；默认disabled直到安全+完整backup+显式开启批准。

本轮授权范围已经足够完成设计/合成验证，无需请求额外批准；未来实施所需负责人决定具体列报告36，B1提示词明确待批准。没有修改PK模型、Schema9/PNBAK1/KDF、versionCode/正式signing/build26，无APK交付。

### 第二轮草案交叉复核的修订

不同AI审查者read-only复核报告33–37与新tools，发现并修正：backup bootstrap envelope自引用AAD歧义；SQL快照释放后可变rows改用加密SQLCipher spool冻结；历史COMMITTED generation合法GC不再被reconcile误判；pending operation绑定archive+digest；dirsync后重复恢复验证既有immutable generation、不覆盖；报告34删除recordEpoch入contentAAD旧句并区分activation与成功receipt。新增四模型测试，14模型+22SQLite+3framing共39。合成向量UUID按样本区分、footer派生key各异，不把固定KAT当真实attempt唯一性证明。未实施正式backup/spool或对象协议。

首批代码9afec67 Android38539482928失败：新增test误用非公开O_DIRECTORY/Os.unlink导致compile，未执行设备实验；改公开Os.open+fstat S_ISDIR、Os.remove，所有断言保留。该批Science38539482869成功。此失败明确保留，最终纠正后HEAD另跑完整checks。
