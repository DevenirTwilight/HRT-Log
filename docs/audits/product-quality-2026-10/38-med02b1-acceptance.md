# 38 — MED-02B1 实施与验证记录（进行中）

范围和授权见 REQUIREMENTS §90；stacked于PR28固定977e077，PR27/28不合并。

## 先失败证据

新增 CapabilityObjectTest 的撤权发布与 Owner-close Reader 合同，在仅委托旧POC的初始适配器上实际执行：2 tests completed, 2 failed（AssertionError）。这证明新合同捕捉两项已确认缺陷。原 SecurityReviewEvidenceTest 保留，继续复现已验收旧POC的行为；旧SYNPOC01类不作为安全入口，也不作为正式存储协议。

新适配器耗时认证及 IO 在门外进行，仅能力发布/对调用者复制结果在共同 session monitor 内线性化。撤权先完成，则提交不执行；提交先取得门，则撤权等提交结束。关闭 owner 撤销其 reader 的能力；撤权后的读取结果擦除且拒绝。原型仍仅合成数据。

实际 Room10/SQLCipher、PNBAK兼容、完整最终CI 尚待完成，不能据此宣称验收通过。真实 ARM、生产密码学专家审核 NOT_RUN。

## 旧回归的迁移适配

MED01 历史就诊合同原有运行时版本9断言，在明确授权的 Room10 后必然失败。仅将该运行时期望改为10，同时增加结构化备份版本仍为9的断言，原数据/回收站往返逐项对照全部保留；没有删除测试或削减内容断言。

## 身份外键加强的先失败证据

新增生产导出合同先执行：4 tests，1 failure，明确指出即便 TRASHED 的 typed identity 也不能持有另一个 dataset 的 recordEpoch。随后将正式实体及迁移 DDL 的身份→store 外键加强为 (store_uuid,record_epoch) 组合外键。其余29个旧实体完整逐项对比不变。此为B1审核中的约束加强，不机械复制合成草案。

## 预览原型的撤权边界

继承的 SYNPOC01 低层类继续保留审计复现，仍明确不是生产安全入口。新的 CapabilityObject 承载安全入口；原 Android ProxyFD/PdfRenderer 与图片原型已采用该适配器。Reader 子能力关闭不影响兄弟 Reader；父对象关闭或 session 撤权使全部子能力不可发布。完整认证/解密及 native render 在授权门外，结果交付在共同门内，Owned原始关闭也参与其自己的短发布门。耗时 Reader 构造不持有 owner 锁。尚未接入真实应用医疗附件UI或生产 AppLock/文件服务；这必须在B2/B4开启前独立验收。已交付给调用者的旧明文不能被撤回，不承诺 JVM/闪存法证擦除。

## 首轮设备失败与修正

代码2af68bb push Android38575437245三个API均失败：合成native fixture错误地要求无索引表也必须含JSON indices字段，四个native迁移用例在建Room9前即JSONException。改为遵循Room导出规范：字段不存在代表空索引列表，已声明索引仍逐条执行；29表/索引/data hash、native迁移验证等断言全部保留。

API35原B4的直接Room9测试builder未添加9→10路径，出现required migration not found。补与生产一致的migration9To10，并将明确授权后的runtime断言改为10，同时增加PNBAK序列化版本仍为9的断言；VOIDED、ADJUST数量、普通查询空状态及重开全部原断言保留。未添加destructive fallback或skip。首轮不能称原生迁移通过；修正后需最终三API重跑。

2af68bb push/PR Science两个gate/deep实际成功，JVM均成功；尚在运行的Android/PR jobs不取消，全部结束后才推修正批。

## 第二轮设备失败及反馈缩短

c74f527 的 push Android38578061148 与 PR38578070063 已全部结束，JVM/Android构建成功，三个API设备任务仍失败；两个Science成功。新迁移夹具用day_note.note而实际字段为text，导致三迁移用例初始化失败。双加密数据库相同整数ID/PRIMARY隔离用例已在API26、29、35通过；其他native迁移尚不可宣称通过。

修正字段并抽出共同 AttachmentMigrationContract，四个相同数据/DDL/索引/回滚/恢复合同既运行于本地真实Room，也运行于设备真实SQLCipher。设备具体驱动保留SQLCipher及密文文件头断言，本地普通SQLite结果不替代加密迁移证明。没有修改业务Schema来迁就错误夹具，没有skip、减少断言或放宽认证。

## 已实现的基础设施与范围

Room运行时版本10新增八表：attachment_store、attachment_record_identity、attachment_document、attachment_version、attachment_link、attachment_operation、attachment_generation、attachment_key_envelope。迁移只增加结构；29个既有实体的字段、外键和索引通过导出合同逐项比较。CURRENT唯一；RETIRED不能回到CURRENT。localDataset、logicalVault、physicalGeneration与recordEpoch分域，不能互相替代。

功能开关固定关闭；新版本不能被标记VERIFIED，generation不能标记VALIDATED。操作日志只允许准备、写入占位及取消，不允许将未认证文件提交为有效业务对象。没有原件导入、生产预览或真实文件写入服务。

typed record identity绑定真实Appointment、Lab、已服药DoseRecord。软删除保留原UUID及回收站所有权/完整payload哈希；正确恢复沿用UUID，永久删除保留墓碑，同整数ID新建产生新UUID。旧TRASHED身份仍占用同ID且归属不能确认时，保守拒绝新关联；附件链接保持隔离，不猜测归属。

PNBAK1记录序列化仍为schema9，使用固定28个既有业务表/字段，不输出附件元数据或密钥。magic、KDF、AES-GCM参数及体积限制均不变。恢复旧包与业务替换同一事务退休旧dataset，隔离链接并生成新身份epoch；不能据相同整数ID重绑原文件。Room10版本号不写入旧序列化格式。

密钥接口区分数据库密钥、空间KEK、版本DEK、设备包裹及未来便携备份角色。PRIMARY与伪装空间不共享附件命名空间；缺失设备密钥明确失败，不重新生成伪装恢复成功。便携封装只保留接口，尚无正式格式或真实密钥迁移。

## 撤权与事务的线性化

AttachmentSession的撤权、epoch和父子读取能力由同一短同步门管理。AttachmentTransaction在门内执行完整同步Room事务，包括真实提交结束；不能仅检查授权再在门外提交。耗时加密IO不占用此门。撤权先取得门则拒绝提交，事务先取得门则完成后撤权。并发合同验证两种顺序、陈旧scope和关闭后子Reader拒绝发布。

这是基础接口与合成原型证明。正式应用锁、空间切换、ProxyFD及原件服务尚未上线，后续必须将全部生产调用接入同一生命周期；不能把接口合同当作已启用产品安全验收。

## 实际本地验证与设备门槛

完整纯JVM：domain63、PK124、importer12、合成POC40，共239，零失败/零跳过。既有core:data及基础合同176通过；新增共用迁移合同结果另行记录。schema/scripts92通过。未删除既有测试，未增加跳过。

最终须以本PR最后HEAD上的Android、JVM、lint、API26/29/35及Science结果为准。任何进行中或失败任务不计通过；本地普通SQLite结果不替代SQLCipher设备验证。

## 未解决风险及B2准入条件

- SYNPOC01不是生产协议。正式对象协议、域分离/GCM使用限额及便携密钥封装仍需独立审核；本轮没有外部专业密码学认证。
- 真实ARM、硬件掉电、实际文件系统+数据库联合提交及空间耗尽的生产导入证明未运行。日志数据库重开只证明元数据持久性，不等于文件系统崩溃一致性。
- 正式医疗完整备份属于B3，未实施；PNBAK1仅结构化记录，不备份未来附件。
- 旧TRASHED身份无法可靠归属时拒绝关联；不存在按日期、文件名或整数ID猜测恢复。
- B2只能在批准正式加密协议/密钥策略后实施认证导入、操作日志与崩溃恢复；B3完整恢复验收前不能默认开启正式附件功能。B4生产锁定/预览需独立验证。候选配额16MiB/256MiB/1000仍非真机验收标准。

未合并PR27/28/29，未修改PK、versionCode、正式签名或build26；不签名交付APK，不操作真实医疗数据。

本地重复完整Android命令在Release R8阶段主动停止（退出130），以优先验证迁移夹具修正；不计为完整构建成功。未取消任何GitHub任务，最终完整Android构建以最后HEAD的CI为准。

修正后本地四个真实Room迁移合同实际通过：4 tests，0 failure/error/skip，测试14.358s；core:data设备测试包assemble同时通过，完整命令2m21s。本地仍是普通SQLite，不替代后续native SQLCipher/密文头检查。

## 原生迁移首批实际通过与强停补测

20181a7 PR/push API26、29兼容任务均成功，每API data21与POC41全部通过，原live cipher import强停也通过；原生四迁移合同已运行。PR Science深度验证实际成功。API35/完整Android仍进行中，非最终全绿。

额外代码检查发现R15 app重启合同还断言runtime9，改为已批准的10并新增实际解密旧PNBAK序列化schema9检查。其他真实扣减、VOIDED、计划状态和重复服药断言保留。当前CI未包含此修正，不能据此宣称全量通过。

为B1专门新增library test APK的SQLCipher强停合同：已提交CURRENT/identity/PREPARED后开启第二事务并在其中阻塞，force-stop后新PID检验已提交状态持久、未提交元数据及就诊文字变更回滚，开关仍关闭且无任何版本/原件。只使用独占合成数据库；既有真实app数据不触碰。这属于进程强停证明，不代表硬件掉电或文件系统与数据库联合原子性。新合同尚待实际运行。

## 20181a7全量结果及补测批

该HEAD push38581720343与PR38581734170全部任务已结束：Android/JVM/API26/29成功；pushScience38581720069成功（deep按原工作流跳过），PRScience38581733920的deep实际成功。API35 data87全部通过；app只有UndoSkippedRestartAndroidTest旧runtime9断言失败（实际10），8个原有条件跳过不变，其他用例通过。仍不能标记整批通过。

修正R15运行时期望为10，额外解密合成PNBAK检查序列化版本9，其他断言原样保留。四个共用迁移合同进一步在真实Room9夹具升级前生成PNBAK，再在Room10恢复；本地4/0failure/0skip，13.812s，连native测试包assemble命令1m42s通过。强停测试先保留一笔已提交PREPARED，再阻塞第二个未提交PREPARED事务，不伪造正式导入或认证对象。

本地完整单元回归data180、reminder17、app865均零fail/error，app61个既有skip不变；完整命令12m10s。新强停合同及R15 app测试编译2m53s通过；force-stop实际运行仍待最终CI。
