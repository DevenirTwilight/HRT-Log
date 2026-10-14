# 34 — Room9→10正式迁移设计草案与合成验证

2026-10-14，UNAPPROVED_DESIGN / SYNTHETIC_SQLITE_ONLY。生产NotesDatabase仍Room9，schema JSON、实体、迁移链、PNBAK1未修改。tools中的SQL不是已注册Room迁移；未来须负责人批准、完整Entity/export/实际Room与SQLCipher验证后才能实现。

## 三个独立命名空间

|字段|用途|完整恢复|
|---|---|---|
|`store_uuid`|本地metadata dataset UUID，所有附件表PK/FK scope|目标新随机值，每次整集合替换都新建|
|`logical_vault_uuid`|不可变原件AEAD密码学域|保留archive原值，不写新local ID入原件|
|`generation_uuid`|本地物理加密目录/文件集合|目标新随机值，非内容AAD|

recordEpoch是结构化集合身份，sessionEpoch是授权生命周期，也不属于原件AAD。一个logicalVault可以对应多个本地dataset，不能对logicalVault全局unique；同documentUUID在不同local scope中可以有不同文字revision或历史PURGED墓碑。原件crypto version身份却不可因此被改写：同logicalVault/versionUUID在任一保留scope中必须绑定相同document/object/DEKhandle/protocol、明密文hash和长度，否则拒绝导入。恢复不会绕开旧不可变规则UPSERT墓碑，也不复制就诊为第二套业务记录。

CURRENT唯一partial index只保证最多一个；仓库最终提交须检查恰好一个，不能退休旧scope后直接提交零CURRENT。RETIRED终态不可重新启用；需要恢复旧备份时新建dataset。普通查询、reader、lease、key服务、CAS、Trash hooks必须固定CURRENT localDataset+session，不能仅按logicalVault/整数ID查询。

## 既有业务及表结构

appointment/lab删除当前使用Trash snapshot，然后物理删除live parent/questions/context；恢复按原整数ID父子重插CONFLICT_ABORT。dose保留soft delete/ledger引用。因此不能把新附件直接FK CASCADE/RESTRICT至live appointment/lab：分别会丢恢复链接或阻断现有删除。

|草案表|主键/约束/索引/规则|
|---|---|
|attachment_store|local UUID PK；immutable crypto UUID/PRIMARY；STAGING/CURRENT/RETIRED；recordEpoch、numeric generation、deferred scoped active physical generation；feature默认0；CURRENT partial unique|
|attachment_record_identity|PK(localStore,epoch,recordUUID)；typed APPOINTMENT/LAB/DOSE_RECORD+legacyID；ACTIVE/TRASHED/PURGED+trashID/evidence32；live typed-ID partial unique；ACTIVE创建/恢复须CURRENT/当前epoch/实际typed row；永久墓碑不revive/delete|
|attachment_document|PK(localStore,docUUID)；title/classification/CAS revision；ACTIVE/TRASHED/PURGE_PENDING/PURGED+deleted time；deferred activeVersion FK必须同store+doc；初始null，仅VERIFIED可激活|
|attachment_version|PK(localStore,docUUID,versionUUID)；objectUUID unique/store、ordinal unique/doc；immutable DEKhandle/protocol/MIME/plain+cipher SHA256/长度；STAGED/VERIFIED/MISSING/CORRUPT/PURGED；跨相同crypto domain/version禁止原件身份冲突|
|attachment_link|PK(localStore,linkUUID)；scoped identity/doc FK；revision、ACTIVE/RETRACTED/QUARANTINED；active关系partial unique；新ACTIVE须CURRENT scope/current epoch/ACTIVE target/doc+VERIFIED version；历史不可delete|
|attachment_operation|PK(localStore,opUUID)，idempotency unique/store；kind/phase、expected generation/doc revision、容量预约、pending index；预留resource physicalGeneration FK+object/version/DEKhandle UUID及≤1024B staged wrappedDEK/hash32|
|attachment_generation|PK(localStore,physicalUUID)；STAGING/VALIDATED/RETIRED/PURGE_PENDING/PURGED；manifest digest32/time；已验证不可退回STAGING改清单，PURGED终态；active pointer须VALIDATED且numeric generation+1|
|attachment_key_envelope|PK(localStore,envelopeUUID)，version FK+DEKhandle必须相同，wrapEpoch unique/version；独立device alias/wrapped bytes/time；AVAILABLE/TOMBSTONED；append-only不改旧bytes/复活/删除|

新父表FK采用RESTRICT；循环active-version/active-generation pointer采用deferred NO ACTION，在commit验证，不改变任何legacy FK/触发器/索引。UUID长度CHECK不是canonical UUID解析/授权；B1 DTO严格解析网络字节序并服务验证当前PRIMARY句柄。SHA形状CHECK不是hash匹配/AEAD验证：合成包裹bytes仅用于约束测试，真实服务必须核对hash、解包同DEK、合法key/epoch/nonce预算。

操作PREPARED可先预留空resource，但IMPORT/REPLACE到WRITING/AUTHENTICATED/COMMITTED必须有完整owned UUID与staged wrap/hash；CLEANED保留资源身份、可清wrap bytes。离开PREPARED所有权/审计hash冻结，不得返回PREPARED。CLEANED/ABORTED终态允许一次不可逆NULL清key blob，不准替换/填回、修改UUID/hash。完整kind-specific phase图/CAS/cleanup成功证据仍需B2仓库验证，DDL不独立证明所有operation语义。

## 仓库事务及纠错策略（未来实现）

1. 批准后additive升级空表；只在实际PRIMARY连接分配fresh localDataset+cryptoVault+recordEpoch、默认off。若共享Room类型要求legacy DECOY验证空附件表，不创建store/key/root/入口，需review兼容；当前伪装笔记不共享附件。普通SQL的PRIMARY字符串不能证明实际DB空间。
2. 身份按用户明确选择typed可信目标懒创建。历史Trash payload不能可靠定位则拒绝关联，不猜日期/Slot，不新增另一套就诊。clinical delete在原Trash事务内ACTIVE→TRASHED+snapshot证据；原parent-first恢复同epoch/CURRENT/recordUUID，失败保留trash和链接；purge保留永久墓碑。同epoch复用整数ID必须新recordUUID。
3. 文字/分类修改revision+operation；替换新object/version/DEK，保留旧版本，完整对象认证/file+dirsync后CAS切selectedVersion。metadata hashes只有完整已知后才写immutable version；未知长度导入先operation预留/staged wrappedDEK，不能先写假hash再修改不可变version。key暂存wrap与active wrap不同角色、新nonce，最后创建active envelope；cleanup清暂存包裹，仅保留IDs/hash。
4. document删除TRASHED默认普通不可见，版本/链接保留；恢复重新核对CURRENT/epoch/目标与key/object auth。缺失target quarantine不猜同ID。永久purge先intent、无引用/lease且owned范围unlink+dirsync，再tombstone；旧备份副本不被清除。不承诺闪存法证擦除。
5. crypto/device key包装可轮换append，不修改原件/旧envelope。设备wrap AAD绑定logicalVault/version/handle+目标localDataset/wrapEpoch/role；portable wrap绑定源crypto身份/archive，不能绑定未知目标dataset。源device envelopes只历史证据，目标不可AVAILABLE；新device unwrap portable后新alias rewrap，证明同DEK并持久化。Keystore丢失不生成新key冒充恢复。
6. 容量事务计入全部retained/trash/retired objects及pending reservations、cipher overhead、SQL staging和keys；candidate16/256/1000不是device验收。GC固定dataset/physicalGeneration/objectID+lease，durable intent后只清owned未引用路径，失败保持可重试证据。Android hardlink实测EACCES，选O_EXCL独占cipher creation+全认证/file+directory fsync+DB最后发布；不依赖目录扫描或Java rename覆盖语义。

## 完整恢复与旧包

完整包先在独立SQLCipher staging库中验证业务rows/完整metadata图/原件/key，不能用现有主库同整数ID的rows“验证”导入记录。每个source local metadata scope一对一映射fresh target scope，源crypto UUID及原cipher/DEK不变。目标同一主Room事务：expected CURRENT dataset UUID+generation CAS→旧links quarantine/旧dataset RETIRED→按既有父子规则替换28个业务表→导入fresh metadata图及新device envelopes→选择新CURRENT。无需删除/改写旧墓碑、不可变版本或链接。中途失败整事务rollback原rows/current/links，commit仅激活线性化点；reconcile/成功receipt条件详35。

独立staging DB不是跨库atomic：不能声称ATTACH/WAL同时commit另一DB/目录；先staging完整验证、文件持久化，再最后主库单事务。旧结构化数据需在提交前持久化加密rollback snapshot/保留旧generation，计入容量，直到重启验证/receipt完成。连接与旧capabilities在CURRENT切换时撤权。大图加载可在事务内准备，但最终真正DB commit必须与授权发布协调；不能仅在withTransaction callback最后check后释放gate再声称commit与revoke原子，B3须证明其实际Room API线性化且长SAF/native IO不持gate。

旧PNBAK1、overwrite/RawData.clear/全清：先原密码/schema/ledger/Trash校验；同一主事务替换业务rows、旧scope退休/quarantine、fresh localDataset+recordEpoch+**新crypto namespace的空附件集合**。相同整数ID绝不重新关联，旧对象/key留高级恢复隔离。reassociation须显式用户选目标，跨crypto域重新加密新对象不改旧AAD。全部保护registry/白名单同事务重装，不能沿用drop全trigger仅SchemaGuards的当前方法。导出record-only serialization固定9，不直接写runtime Room10。新完整包独立格式详35。

## 合成验证与真正生产验收

命令：`python3 tools/attachment-design-validation/test_room10_draft.py`。最新**36/36、0skip**；原25保留，新增6 local/crypto scope，4 crypto commitment/operation resources，1 terminal key cleanup。fixture构造实际Schema9 JSON的29表+有导出的indices、room identityHash、partial active-dose index、literal SchemaGuards（不含插值milestone）、exact supply append-only triggers。before/after逐表rows fingerprint及所有已安装legacy sqlite_master SQL不变，断DDL rollback user_version9。只合成数据。

覆盖原25升级/墓碑/trash/FK/ledger/envelope/physicalCAS，加上同crypto同docUUID不同revision/旧PURGED共存、CURRENT唯一/crypto mappingimmutable、CAS切换/正常join仅当前/中断rows+links+CURRENT rollback、退休/staging身份link拒绝复活、旧包新crypto+epoch；同crypto version恢复成功及逐字段byte identity冲突拒绝；缺失/crossstore physical resource/超限包裹/写后UUID与wrap修改拒绝；完整合法key终态wipe及重填/phase/hash复活拒绝。

不代表实际Room/SQLCipher、完整新包或AEAD：export JSON部分runtime index遗漏；generated Kotlin guards/onOpen未执行；真实KDF/Keystore/hash匹配、SQLCipher WAL/ENOSPC/powercut、多线程quota/GC/授权、ARM和新device NOT_RUN。未来B1须review Entity/Index/FK/CHECK/trigger全定义、真实Room migration/validate/export10、所有29旧表数据hash/guard SQL、重启/onOpen、老migration chain/VOIDED/Trash/R15/MED01/PNBAK回归、双实际DB隔离；不destructive fallback。协议和备份独立专家审查仍未完成。

[初始迁移AI复核原稿](med02b0-peer-reviews/migration-ai-peer-review.md)保留当时22测试/设计状态。主审先修两步墓碑/重stage漏洞到25，再发现local/crypto namespace混用的恢复冲突，交叉crypto reviewer确认三个域分离与wrap不同角色，最终36。各阶段没有生产修改或把AI叫外部认证。
