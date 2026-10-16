# ANSM-02 兼容设计与审批包

2026-10-16；提案，不是迁移授权。用户本轮明确允许先实现独立合成 UI；以下生产方案等待审阅。

## 当前映射与不能复用的字段

|现有文件/结构|含义|官方目录边界|
|---|---|---|
|core/data/.../Entities.kt MedicationEntity|用户姓名、分子、途径、单位、实际剂量、计划及默认容量|无CIS/CIP/规格版本；不能把商品强度当每次剂量|
|同文件 ProfileEntity|仅PK酯/途径/凝胶号/贴片释放|不保存官方商品或改参数|
|MedicationSnapshot.kt v2|实际记录/规则的冻结药物上下文|不改v2解码，不从当前目录补历史|
|ContainerEntity / SupplyEntryEntity|既有容器及append-only账本，VOIDED/冲销/恢复|容器没有独立单位/商品快照；本轮不接线|
|NotesRepository.saveMedication|保存可能创建方案版本，显式resize开关可改当前容器|官方关联应独立事务，只改引用，不调用该方法制造方案或库存修改|
|MedicationEditor / MedicationsScreen|原手动创建编辑与药物卡|本轮零改动；Mock仅演示未来入口，退出不修改生产草稿|
|NotesDatabase / Migrations|Room10，1→10非破坏迁移|不降级、不清库、不先改版本|
|DataTransfer / RecordBackupCompatibility|PNBAK1加密容器format1，业务schema9，显式列白名单|新表不会自动进备份；必须独立版本设计，不承诺旧程序可保留新引用|

官方层：source namespace + CIS 是商品键，CIP13 是包装键并必须属于该 CIS；
产品有多成分、多途径，包装有独立声明状态。成分SA与FT/ST原始代码及关联组保留，
不相加、不静默标准化冲突。浓度/规格/释放速率/总容量/使用量各自原文保存。
用户药物、计划、历史快照、容器及PK是独立对象，名称相似/同成分不代表等效。

## ANSM-02A（当前可实施）

所有新界面/数据在 app/src/debug 下，正式源码及导航不改。
非导出测试Activity无launcher、无intent接收健康数据、无正式repository或存储依赖。
合成ID使用 SYNTH-CIS-* / SYNTH-CIP-*，不能误认为有效CIS/CIP；固定 synthetic-v1 版本，
合成来源和日期明确标记。加载器只接本地合成输入，可注入加载中/失败/空状态。
字母重音兼容搜索不改显示原文；产品键不同绝不合并。按剂型、原始规格、途径独立筛选。
常驻合成横幅、状态与来源分开，未知供应始终未知；有效授权不意味能买到。
独立RCP/Notice显示入口/正文核实的区别，合成无真实URL，不构造链接或联网。
Mock选择仅修改独立UI预览状态，不预填剂量、计划、容量、PK，不产生用户药物。
手动创建继续可用；本演示的手动操作也只返回预览，不写业务库/备份。UI使用rememberSaveable，
Android可能恢复合成草稿；这不是正式引用持久化，恢复/配置变更专项未验收。不得输入真实健康资料。

未来正式加载：在01合并、许可/归因和分发范围签核后，单独实现受版本和校验和保护的
asset适配器，将01字段映射到只读DTO；构建不下载。损坏/重复/无效所属关系整体拒绝，
显示错误并保留手动路径；不以Mock假装正式加载成功。官方网页只能由用户显式操作，
使用已核实产品入口，防不可信scheme/host，失败提示且不带用户ID/健康记录。
不推断不存在的深链接或临床摘要。锁定/PRIVATE/伪装外壳不得显示入口，另审集成。

## ANSM-02B 建议实体（全部 NOT_IMPLEMENTED）

建议审查后增加候选 Room11 的三个领域结构与两个身份结构；最终版本号必须集成时重核，不能抢占其他迁移：

1. OfficialReferenceSnapshot：不可变UUID、schemaVersion、namespace、CIS、可空CIP13、
   referenceVersion、catalogSha256、productPayloadSha256、capturedAtUtc；必要原文快照含
   名称/成分角色与分母/剂型/所有途径/选定包装/独立状态/字段来源与核实日/未知冲突列表。
   无用户剂量/计划/PK参数；失去当前目录也能读。规范JSON确定性哈希并设大小/字段预算。
2. MedicationReferenceEvent：事件UUID、datasetUUID、medicationUUID、revision、关联快照UUID（解除为空）、
   observedAtUtc、previousEventUUID、动作LINK/UNLINK/CORRECT、可选用户更正说明。
   append-only；每药物revision唯一，重复opUUID幂等；不得用可回溯生效时间改写旧事实。
3. RecordReferenceCapture：captureUUID、datasetUUID、record_id+record_revision、独立referenceRevision、
   previousCaptureUUID、operationUUID、nullable contextEventUUID、
   nullable actualProductSnapshotUUID、actualConfirmationSource。context仅表示创建记录时冻结的
   用户药物关联上下文，不证明实际使用了该CIS/CIP。actual默认null，只有用户明确确认本次
   实际商品/包装才冻结另一快照；不能从当前绑定、方案或库存旧容器推断。未来方案需单独的
   rule_version_id 捕获映射。旧行**不回填**；未知就是未知。
   来源更正仅追加referenceRevision并保留旧capture，绝不改变RecordEntity.revision或触发账本冲销。
   用户实际时间/剂量更正仍走原记录修订；新capture显式锚定对应record_revision，不能按当前药物绑定补齐。

4. ReferenceDataset：datasetUUID与CURRENT/RETIRED状态，只属于官方引用子系统。
   首次显式关联时延迟初始化；旧药物/旧备份不创建虚构官方身份。整库替换先使旧scope失效，事务内清除旧绑定及旧scope，
   新备份导入在同一事务建立新local scope并重映射全部引用，保留来源dataset标识作出处，
   不按旧整数ID自动接续。不得借用功能关闭的attachment_store或偏好文件。
5. ReferenceMedicationIdentity：datasetUUID、medicationUUID、nullable medication_id、
   LIVE/PURGED。这里LIVE指身份仍存在，不能与Medication.active混同；复合FK将事件绑定到它，
   CURRENT中每个存在的med_id唯一。整型ID只是库句柄；未来若批准物理删除，先按所有权移除
   关联数据并使旧身份PURGED/med_id=null，同整数ID新药必须新UUID。记录捕获必须验证event
   的UUID确属该记录药物；本提案不新增药物回收站或永久删除功能。

这五个概念都尚未建表；具体DDL、索引/约束、恢复顺序和故障注入须在02B批准包评审后冻结。
已有AttachmentIdentity.bind只支持APPOINTMENT/LAB/DOSE_RECORD，没有MEDICATION身份；
不能假设可直接复用或新增一种附件target来绕过本批迁移审批。

当前引用由最新event导出，引用事件和快照同事务提交；引用已归档/已撤销产品仍可读。
选择包装必须验证parentCIS，换CIS清除不属于它的CIP；取消不提交，重复确认同op不新增。
更换/解除仅影响未来显式捕获；不重写记录v2、计划、流水、已用包装、预测、PK或PDF。
当前药物默认未知身份，禁止用商品名自动匹配。跨地区别名须独立来源审核。
本提案尚不能证明旧容器对应哪种包装：**不建立容器绑定或自动换算**，留ANSM-03审查。

## 迁移、备份、恢复与删除

迁移10→候选11仅建新表/索引/FK，旧表全部schema/data指纹一致，无补填、无重算。
异常DDL事务回滚、完整链1→新版本、真实SQLCipher API26/29/35需验证。
不得fallbackToDestructiveMigration；旧APK覆盖安装不支持降级Room，拒绝打开新库且不能清空。
正式升级前备份说明不得声称旧版会理解新引用。

PNBAK1 magic/加密/KDF保持，建议业务schema10显式增加引用表及列；先审旧schema1–9→新库
全部引用为空（不由当前目录补），旧format1仍可解密。新版导出必须包含全部不可变快照、
事件、记录修订映射，不依赖APK目录存在。旧版读取新业务schema应明确拒绝，不静默丢引用。
限制payload大小/JSON嵌套/哈希，重复UUID/CIS-CIP冲突/孤儿/循环revision/无效枚举整体拒绝并
回滚，保留原库。建议同事务先按capture→event→identity→snapshot/scope清除旧引用，
再按原顺序恢复业务表，随后新scope→snapshot→identity→event→capture；最终顺序仍须
与实际FK/旧恢复入口联合审阅和故障注入。不得遗留已被覆盖的旧药物-CIS绑定。
ID重分配必须重映射所有引用，不能将整数ID复用关联另一药物；优先使用既有逻辑数据集/稳定UUID
机制，但不把功能关闭的附件表当本功能存储。

当前removeMedication仅停用药物、关通知并结束现行规则，不是删除或回收站；停用/重新启用
不解绑、不清快照或更换medicationUUID。实际记录进入既有回收站则保留capture，恢复原上下文；
记录永久清除在同事务按所有权删除映射，
只清无任何活跃/回收站/历史修订引用的快照。整库覆盖恢复/删除全部数据遵守既有dataset/epoch
与锁机制，不单独保存偏好侧车。更正产生新事件，不覆盖既有快照；目录更新仅影响新选择。

## 审查矩阵与正式接线验收（待批准，NOT_IMPLEMENTED）

|风险|影响|必须证据|
|---|---|---|
|盐/酯/角色或mg/µg/g/mL混淆|高|原文和分母/角色保留，FT/ST冲突显式，禁止跨量纲运算|
|绑定触发resize/计划/PK|高|前后全表指纹，PeriodStability、账本/VOIDED、预测和PK结果逐项相同|
|引用版本可变导致历史漂移|高|目录替换/移除/产品停产后历史快照字节不变；不回填旧记录|
|新备份缺表或恢复失败|高|旧PNBAK真实恢复、新roundtrip、故障注入回滚、加密预算及旧版明确拒绝|
|删除/恢复/ID复用错配|高|trash→restore保持、purge只删所属对象、双dataset同ID不串联|
|UI误导许可/供应/临床核实|中|常驻合成标签，状态三分，来源日期、未知/冲突及RCP/Notice独立文案|
|锁/伪装或链接泄露|高|生产入口仅PRIMARY解锁，后台取消、无intent载荷健康数据、无INTERNET|

门：01已合并且主线六检查成功 → 许可与原字段解释/可显示范围及日期由负责人确认 →
DTO/快照/迁移/备份方案独立审阅批准 → 无共享文件并行冲突 → 02B接线与全量兼容验证。
ANSM-03还需独立量纲及容器历史方案；不能从本Mock选择或CIP标签推导库存容量/消耗。

## 01真实字段 → 未来DTO适配（当前不实现）

以PR45固定HEAD阅读结构，不复制数据到本分支：

|01字段|未来只读投影|禁止的推断|
|---|---|---|
|reference_version / sources / license / verified_on / data_updated_on / data_update_semantics|版本、来源、许可门、核实日、网站显示日期及语义分别显示|不把获取日期或网站日期当每个导出更新时间|
|cis / name_fr / form_fr / routes_fr|namespace+CIS、原名、剂型、多途径|不合并同名不同CIS，不从PK途径反推官方途径|
|ingredients[].name_fr / substance_code / component_type_raw / group / strength_fr / denominator_fr / source|保留角色分组的成分行及原单位/分母、字段来源|没有统一product.strength字段，不能用名称解析替代；不能把SA和FT相加|
|authorization.value/raw / marketing.value/raw|独立授权与销售声明，保留原文|不合成“可以买到”状态|
|supply_notices / supply_verification / unverified_fields / conflicts|独立公告、未知与冲突|空公告不表示无短缺，缺结果不表示停产|
|presentations[].cis / cip13 / cip7 / label_fr / authorization_raw / marketing / package_quantity / source|CIS-CIP所属、原包装说明、独立状态和名义数量|不能改用户库存单位或容量；不推断可分片或可用泵数|
|documents.rcp 与 documents.notice 的 url/panel_id/verification/reason|两个文档入口与面板观察证据|VERIFIED在这里仅入口存在；正文由field_verification.rcp_notice_content单独决定|
|page_source / field_verification|身份核查证据、字段级状态|HTTP200/面板存在不验证全文，不生成医学摘要|

Mock DTO仅验证交互，不是上述正式适配器的完整实现；它没有通用源哈希、逐字段证据、
实时供应或真实文档链接，不得通过移除合成标签直接用于生产。未来适配器需另有字段级测试。

并发与跨实体约束：LINK/UNLINK/CORRECT提交携带expectedHeadRevision，以事务CAS拒绝
过时编辑，不覆盖另一窗口的更正。每个RecordReferenceCapture必须同时校验记录的
medication身份、数据集稳定身份和event所属药物；仅整数ID相同不足以通过。
无历史映射的旧记录始终显示“未保存官方来源”，不以event时间推算来源。
引用源版本更正与用户实际剂量更正是两种操作，后者仍走原记录修订与账本路径；
前者不触发任何库存冲销。所有CAS/错误归属/重复提交测试属于02B审批后的验收。

现有MedicationEditor仅E2提供实际route，其余保存null。官方产品多途径列表不能替用户确认
实际途径，也不能由PK profile补齐历史。若正式选择后要扩展实际途径录入，必须单独确定
旧unknown/null、记录修订与备份语义并审批；本Mock不填充route。

## 选择确认的字段边界

|信息|当前Mock确认行为|未来正式接线的约束|
|---|---|---|
|CIS、显式可选CIP、来源版本、商品原名|仅UI引用预览；可取消/解除|在独立引用结构中保存；不取第一个包装作默认实际包装|
|用户名称与剂量文本|原样保留|关联不是重命名；新药如提供名称候选也须可修改且不改已有药物|
|成分/剂型/规格/途径|只读来源|不是用户实际成分/途径确认，更不是品牌/盐/酯/释放形式等效关系|
|剂量、频率、时间、实际途径、库存单位/容量、PK|不预填、不计算|由原手动流程明确输入；不从商品规格或包装数转换|
|未知字段、授权/销售/供应、RCP/Notice正文核实|分别显示或标未知|不把许可/面板/授权/无公告推成临床内容、适用个人或可买到|

确认本身不能修正用户历史或自动消除参考资料与手动信息的差异。所有持久化与新药物创建
必须在02B审批后设计真正事务和取消语义；当前不存在这种正式入口。

另一个高风险语义是“关联商品”与“这次实际用的商品/包装”不同。用户更换关联后可能仍在
使用旧包装；记录发生时间也可能早于补录时间。上下文捕获必须标明创建/更正时刻，不能
把它标为实际给药商品。实际商品快照只接受明确事实录入；未知保持null，不能按当前链接
回填过去或用它校正库存。02B需对这两种来源分别做补录、迟到记录、关联切换、目录更新、
更正/撤销和备份回放测试；该事实确认UI不在当前Mock实现或任何已批准的生产范围内。

引用元数据更正与真实服药更正必须用不同修订域：前者CAS追加referenceRevision，保持
RecordEntity整行（包括revision）、SupplyLedger及方案/PK不变；不能调用现有实际记录编辑
路径来“顺便保存来源”。后者才使用既有RecordEntity.revision及冲销流程。审批后需对同一
记录的引用更正/重复操作/并发更正验证旧capture字节不变及库存流水计数/哈希完全不变。
