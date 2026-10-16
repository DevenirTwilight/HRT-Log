# ANSM-03B 生产接线提案：待独立审查与明确批准

2026-10-16。所有新持久化、备份、库存接线均 **NOT_IMPLEMENTED**。
本提案不预留Room版本号，也不授权更改Room10/PNBAK1、流水或预测。

## 身份与冻结结构

03A与02B解耦：纯计量只接不可变PresentationRef和显式ActualPackage，既不依赖目录DTO也不查询Room。
未来02B提供namespace/CIS/CIP所属校验、来源版本、规范payload哈希与冻结商品快照UUID。
**药物关联上下文不等于本次实际包装确认。** 未确认实际来源不能按当前关联推导成分量/扣减单位。
02B还未实施，本分支不引入其Mock类型、资源或待审表。正式适配器须独立核实字段和转换依据。

候选新增概念（不是DDL；索引、FK、版本、序列化均待审）：

|结构|建议冻结字段与不变量|
|---|---|
|ContainerPresentationSnapshot|snapshotUUID、业务schema、datasetUUID、containerUUID、可空officialSnapshotUUID、原始成分及盐/酯/水合物/SA-FT-ST组、制剂/实际途径独立、原包装说明、名义质量/体积与次数、每用制剂量/成分量、释放速率、来源/日期/版本/哈希、不确定性、算法版本|
|ContainerMeasureIdentity|datasetUUID/containerUUID/legacyIntegerID，LIVE/PURGED；容器的stockUnit完整语义、量纲与精确数值刻度，confirmedUsableCapacity可空；旧double标量原样保留|
|ContainerPresentationEvent|append-only LINK/CORRECT/UNLINK，operationUUID、expectedRevision、prevEvent、理由/确认时间；冻结引用更正不resize、不重算旧流水|
|MeasuredConsumptionCapture|独立UUID锚定recordID+recordRevision与containerUUID，actualPackageConfirmation、输入量和单位、输出库存量、来源快照/转换算法/关系方向、operationUUID、逆事件引用；旧流水不补捕获|
|MeasureDataset|CURRENT/RETIRED隔离与备份导入本地scope；不得借用attachment_store或PK/needs_review/自由文本存储|

精确值使用规范十进制字符串（数量和单位分列），不以Double转字符串伪造历史精度。
稳定UUID与数据集域防止整数ID复用误绑；旧容器维持legacy unknown，不从名称、当前med.unit、CIS匹配或药代参数补齐。
本轮原型的opaque identity不是正式编码标准；03B需冻结成分与包装语义的规范序列化、预算/哈希算法和兼容解码。
原型只支持有限十进制，任意分数/非终止换算未授权舍入策略前明确拒绝。

## 容器生命周期与账本

1. 新建包装时仅在用户明确确认实际商品/包装/库存单位后冻结快照及确认容量；目录名义64泵不能当保证可用64泵。
   未知容量允许保存参考，停止自动分配/预测相应部分。灌泵、残余、损耗由未来独立实际事实/调整事件表达，不创造新容量。
2. 同药不同规格允许并存，按容器快照独立解释。开封包装换规格应创建新容器，新资料不覆盖旧快照。
   旧包装可继续按原已确认单位消耗；不在编辑药物时自动resize或批量重绑定。
3. 自动分配只能在输入来源已确认且单位、成分、规格、版本兼容的候选集合内进行；先按开封日/id，再密封id的旧顺序仅在该集合内保留。
   多规格混用需要用户实际包装确认/明确分配，不能先猜包装再倒推成分。全局FIFO不能跨不兼容组。
   任一不确定部分返回结构化未分配原因，不能变零、静默跳过错误制造全量可用量，或跨容器凑数。
4. 每次真实消耗冻结原始输入与已确认输出；后续目录/药物/方案变化不重新换算。REVERSE严格使用原消费输出和容器快照，不能当前规格逆算。
   实际量修订仍保留旧CONSUME/REVERSE，独立来源元数据更正不增加Record.revision、不触发账本。
5. VOIDED不抹账、不篡改used；维持当前有真实消耗/冲销不得直接作废的门，ADJUST历史原样保留。
   restore检查原账本、容器及相关上下文；版本变化不自动放宽已有PackageCorrections冲突检查。
   来源更正、真实剩余修正、包装作废/恢复必须分开操作，operationUUID幂等，同UUID不同载荷拒绝。
6. 计算失败先于写事务；确认提交再CAS检查版本、余额、opUUID。取消无写入；进程被杀由单事务回滚，不能保存剂量却漏掉扣减捕获。
   备份/overwrite并发须在相同数据集门内互斥。事务中断、并发双击和恢复重放要做真实SQLCipher测试。

03A的合成协议测试只证明不可变输入与已确认数值的演算合同；不是生产SupplyLedger模拟器的替代验收。
生产VOIDED/REVERSE/ADJUST仍由原有真实Room与设备测试证明；03B新行为未实现不能据此标为通过。
贴片贴用会消耗一贴的事实语义、移除/脱落是否只记事件、补记是否扣库存，需独立审阅；不得从移除次数推算新消耗。
胶囊多途径和注射酯型都不得改变用户实际途径或Profile；原型支持计量不代表扩展正式药品范围。

## Room 与升级链

当前运行Room10、迁移1→...→10、旧29业务/提醒表与附件feature0基础保留。
候选10→N只添加经审批的身份/快照/捕获结构，旧表字段、行和流水哈希必须相同；N集成时重新核对，不能与02B抢Room11。
若02B先落地，复用经过批准的官方不可变快照/数据集接口；独立稳定容器身份不能假设现有附件typed identity支持CONTAINER。
若02B延后，计量核心继续独立，禁止通过偏好侧车抢先落地生产关联。

验收：逐版本1–10真实fixture升级到N；Room schema指纹、FK/索引、所有旧表行、VOIDED/历史修订/冻结结果比对；
10→N任意DDL失败回滚；API26/29/35真实SQLCipher重开及未提交事务force-stop新PID。
不得fallbackToDestructiveMigration。旧APK覆盖降级不受支持，应保留数据库并清楚拒绝打开，不能清库恢复运行。
发布回滚采用兼容性已验证的修复前进版本；没有旧版安全读取新结构的证据时不承诺直接安装旧版回滚。

## PNBAK1 与恢复

PNBAK1 magic、format1、Argon2id32MiB/t3/p1、AES-GCM、既有限额不变；这不意味着业务schema可不升级。
当前serialization schema9与Room10不同，DOMAIN_TABLES/列白名单显式维护。
候选新业务schema需统一包含02B/03B新增域、全部冻结来源（不用当前目录补）、事件/捕获与稳定身份。
旧schema1–9导入新库：保持既有兼容和库存恢复算法原样，新官方/计量映射空，标legacy unknown；不将旧Double假装精确新计量。
新导出旧程序：schema高于支持范围，应在任何替换事务前NewerBackup拒绝，原库不变，不剥掉新表伪装schema9。

新恢复预检：文件/解密/JSON预算→业务版本→规范数值/单位/身份/哈希→CIS-CIP所属→dataset/FK/重复UUID/事件链/逆引用/recordRevision完整性。
来源正文不要求再次联网；备份必须带足够冻结来源。缺来源版本、哈希冲突、未知算法、循环/重复逆事件不能回退当前目录，应整体拒绝并保留原库。
兼容可显示未知的旧域与损坏的新必需域须分别定义，不能把损坏新数据当可忽略旧数据。

事务内先退休旧scope并清除捕获→事件→身份/快照引用，按已批准实际FK确定删除顺序，再恢复旧业务表和稳定映射，
建立fresh本地scope并重映射UUID引用；来源dataset仅出处，不按整数ID重连。最后全局验证、触发器与缓存校验再commit。
此顺序为提案，需和RawData.restore/attachment retirement联合审阅；故障注入每一步必须保留原库可用且无旧关联残留。
新数据roundtrip、旧真实格式合成PNBAK解密恢复、超预算、损坏、ID重用、来源版本缺失、并发取消和新设备恢复均需单独验收。

## 预测、提醒与审批门

StockForecast当前汇总一个med的标量，不能直接接混合量纲容器。
未来统一返回confirmed compatible stock + unknown/unallocated reasons；若无法证明覆盖量，不能显示肯定用尽日期/库存充足。
日历、库存卡和StockAlerts必须消费同一新结果；不改治疗频率/检查规则，不把未知变成缺药医疗警报。
既有7日阈值/366日窗口/DST日历边界需不变性测试；单位未知时的用户显示与提醒暂停政策需负责人明确决定。

|阻断项|风险|解除证据|
|---|---|---|
|01/02A尚未合并、02B未实施|高|各PR独立验收/主线六项CI；不自动合并|
|LO2页脚/PDF1、FT/ST未结案|高|来源适用条款/日期/原字段解释及负责人正式分发确认；不能合并或工程绿替代|
|容器无单位/历史规格|高|新快照与legacy unknown政策批准，旧表全量指纹不变|
|转换与实际来源方向|高|真实包装确认交互、分片证据、完整成分语义及有向关系审阅|
|Room/备份/恢复|高|DDL/业务schema/预算/兼容链/失败及新PID测试独立审查和明确授权|
|库存/预测/提醒变化|高|混合规格、未知处理、分配顺序、反向/幂等/日历DST合同批准|
|实际设备与外部资料审阅|中至高|无实际证据仍NOT_RUN/UNVERIFIED，不以原型代替|

03B开始前负责人必须明确批准：具体实体/Room版本和全升级链、PNBAK业务schema/拒绝策略、
旧库存unknown和多规格分配政策、预测/提醒未知表现、实际包装确认及记录修订/反向语义、失败/回滚与集成文件清单。
随后独立PR；不得修改本阶段默认安全范围，也不得借02B关联自动触发resize。
