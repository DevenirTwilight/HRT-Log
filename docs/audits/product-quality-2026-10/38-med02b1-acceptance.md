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
