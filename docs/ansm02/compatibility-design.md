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
Mock选择只保存内存中的引用预览，不预填剂量、计划、容量、PK，不产生用户药物。
手动创建继续可用；本演示的手动操作也只返回预览，不写数据。进程结束即丢弃。

未来正式加载：在01合并、许可/归因和分发范围签核后，单独实现受版本和校验和保护的
asset适配器，将01字段映射到只读DTO；构建不下载。损坏/重复/无效所属关系整体拒绝，
显示错误并保留手动路径；不以Mock假装正式加载成功。官方网页只能由用户显式操作，
使用已核实产品入口，防不可信scheme/host，失败提示且不带用户ID/健康记录。
不推断不存在的深链接或临床摘要。锁定/PRIVATE/伪装外壳不得显示入口，另审集成。

## ANSM-02B 建议实体（全部 NOT_IMPLEMENTED）

建议审查后增加 Room11 的三个独立结构；最终版本号必须集成时重核，不能抢占其他迁移：

1. OfficialReferenceSnapshot：不可变UUID、schemaVersion、namespace、CIS、可空CIP13、
   referenceVersion、catalogSha256、productPayloadSha256、capturedAtUtc；必要原文快照含
   名称/成分角色与分母/剂型/所有途径/选定包装/独立状态/字段来源与核实日/未知冲突列表。
   无用户剂量/计划/PK参数；失去当前目录也能读。规范JSON确定性哈希并设大小/字段预算。
2. MedicationReferenceEvent：事件UUID、medication_id、revision、关联快照UUID（解除为空）、
   observedAtUtc、previousEventUUID、动作LINK/UNLINK/CORRECT、可选用户更正说明。
   append-only；每药物revision唯一，重复opUUID幂等；不得用可回溯生效时间改写旧事实。
3. RecordReferenceCapture：record_id+record_revision及可空eventUUID，表示本次记录明确捕获的
   来源；未来方案需单独的 rule_version_id 捕获映射。旧行**不回填**；未知就是未知。
   如编辑旧记录，仅用户明确更正引用才产生新revision；保留旧修订证据，不能按当前药物绑定补齐。

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
回滚，保留原库。恢复顺序快照→med→event→record映射需与实际FK和旧恢复顺序联合设计。
ID重分配必须重映射所有引用，不能将整数ID复用关联另一药物；优先使用既有逻辑数据集/稳定UUID
机制，但不把功能关闭的附件表当本功能存储。

软删除药物/记录保留引用与事件，恢复恢复原上下文；永久清除在同事务按所有权删除映射，
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
