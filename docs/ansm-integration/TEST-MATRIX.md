# 测试矩阵

当前所有新测试NOT_RUN。每批记录命令、源码SHA、API/ABI、计数、失败和artifact摘要；不继承旧HEAD绿灯。

P0：非空同库/异库/重复恢复、CONSUME/REVERSE/CONSUME、FK孤儿/循环/深链/超预算、导入失败整库回滚、guard完整、重开、SQLCipher API26/29/35；旧PNBAK与全既有回归。
P1：旧1..10→新Room完整链，旧schema1..9→新业务、current catalogue缺失roundtrip、CAS/op冲突、域复用整数ID、快照与捕获不可变、trigger清单、逆量/ADJUST/VOIDED。
P2/P5：四语、320/411dp、1x/2x、明暗/对比度、取消/键盘/重启/备份恢复，AppLock/伪装边界；人工TalkBack/真机NOT_RUN。
P3/P4：原35保留、生产/原型同向量差分、来源有向转换、部分片/凝胶/贴/胶囊/注射合成、同规格FIFO、混合UNKNOWN、Legacy/Verified互斥、事务故障与强停。
六检查：jvm/android/device-tests/compat26/compat29/science-change-gate，保留全部既有任务、断言、科学/权限守卫。
环境JDK21=/tmp/ansm02-tools/jdk，SDK37=/tmp/ansm02-tools/sdk；本地无KVM，原生由CI。Gradle限额1536MiB、1worker/in-process；既有CI不弱化。

## P0 本地（2026-10-16）

- EXPECTED_RED：原生产代码下 BackupReplacementTest 4/4 FAIL（自FK×3、触发器丢失×1），p0-red-evidence.json。
- 中间编译FAIL：误用了AndroidX internal类，改用公开 FrameworkSQLiteOpenHelperFactory 的 name=null 内存API，无新增依赖。
- 中间新增故障注入用例FAIL：协程栈恢复复制异常对象，身份相等不是业务合同；改查注入消息逃逸，继续严格比较所有业务行/身份/守卫/重开内容。
- PASS：:core:data:testDebugUnitTest :core:data:assembleDebugAndroidTest，191测试，0失败/跳过；原生测试仅编译PASS。
- NOT_RUN：原生SQLCipher API26/29/35、物理设备、断电。待GitHub CI实际结果。

## P1实现中

- PASS：ReferencePersistenceTest 10项 + ReferencePayloadTest 7项，本地Robolectric。
- FAIL已定位：初始完整旧回归6项，固定Room10/Schema9期望和旧fixture使用新表清单/旧迁移缺步骤，已调整但全套复跑尚未完成。
- 初始新测试12项FAIL：10项合成药物夹具缺少现有active计划要求的soon/late参数；2项负向测试未捕获日期/JSON的专用拒绝异常。修正夹具与专用异常覆盖后17项全通过，没有修改旧业务断言以掩盖错误。
- NOT_RUN：完整P1回归、原生SQLCipher、迁移1…10专项、UI/物理设备。

P1完整本地回归最终PASS：215/215、0失败/跳过；新增24（引用14、payload7、迁移3，其中迁移逐一覆盖1…10）。原生编译PASS，原生执行NOT_RUN。中间失败保留说明：旧固定备份表数28→35及Room/业务版本严格期望更新；新夹具缺indices需按冻结schema可选字段读取；旧账本按真实revision1消费→revision2更正→REVERSE→再消费顺序构造，未修改生产触发器。
P0 CI实际证据见p0-ci-evidence.json：六检查PASS；API26/29数据90、App70；API35数据157、App264（旧8 SKIPPED）；JVM domain121/PK124/importer12/POC63；数据Robo204、提醒20、App1198（旧205 SKIPPED），PeriodStability5/5。
