# 46 — MED-02B3 / B2-SECURITY-GATE2 验证记录

2026-10-14。**开发中，最终HEAD CI尚未执行/通过**。下面是新增测试范围，不把代码存在或编译成功当设备结果。

## 代码范围与可复现命令

core/data仅新增androidTest依赖/隔离测试Manifest及gate2测试协调器；独立SQLCipher synthetic_gate2表不修改Room实体/Schema/guards。真实生产B1 operation保持PREPARED，不使用测试表结果宣称生产READY。app仅androidTest新增TinkPDF adapter/测试；tools仅contractTest/test新增canonical向量；scripts/CI新增隔离红绿/三seam强停和release markers。生产Kotlin/Schema/PNBAK/AppLock/PK/version/sign均应最终diff确认无改动。

```
./gradlew -PjvmOnly :core:domain:test :pk-engine:test :importer:test :attachment-poc:test --no-parallel --max-workers=2
python3 -m unittest discover -s scripts -p test_attachment_design_validation.py
python3 -m unittest discover -s scripts -p test_attachment_release_isolation.py
./gradlew :core:data:testDebugUnitTest :core:reminder:testDebugUnitTest :app:testFullDebugUnitTest :app:lintFullDebug :app:assembleFullDebug :app:assembleFullRelease :core:data:assembleDebugAndroidTest :app:assembleFullDebugAndroidTest --no-parallel --max-workers=2
python3 scripts/check_release_manifest.py
python3 scripts/check_attachment_release_isolation.py
./gradlew :core:data:connectedDebugAndroidTest
python3 scripts/run_attachment_gate2_red.py
python3 scripts/run_attachment_gate2_restart.py WRITE
python3 scripts/run_attachment_gate2_restart.py IN_COMMIT
python3 scripts/run_attachment_gate2_restart.py AFTER_COMMIT
./gradlew :app:connectedFullDebugAndroidTest
```

外部强停脚本只在经ro.kernel.qemu=1核验的emulator中对net.plainnotes.app.data.test操作，没有pm clear/杀用户app。API26/29目标保留原B1与POC并新增Gate2类；API35完整data/app及全部旧重启脚本保持。Science原gate/deep不削减。

## 先失败/修正与运行状态

- 首轮library test APK编译成功5m27；当时尚无设备执行，不称native通过。
- Android联合编译命令曾误带jvmOnly=false：settings只检查property存在，导致缺data项目、20s失败。移除该参数，未改settings/断言/跳过；此是命令错误，不算红用例。
- 确定性红控制是故意unsafe的test-only ack cleanup，要求相同“已提交active仍存在”断言先失败；修正路径读取durable SQL COMMITTED后保留active。不是生产缺陷或修改已验收B2。
- 新epoch恢复时旧harness journal必须按row.dataset/epoch隔离，不只比较连接当前scope；parent路径替换后的cleanup/reconcile必须核对持久device/inode，不能按路径误删foreign。这些检查保留在新增真实测试。
- 最新本地library test APK与app Android测试Kotlin编译PASS，联合7m31、最新增量1m16。纯JVM domain63/PK124/importer12/POC62=261，零fail/error/skip；设计58PASS、Release检查器3方法/33污染subcases PASS。
- 完整Android unit/lint/Debug/unsignedRelease、red-green、三API freshPID/PDF、最终push/PR CI仍PENDING，后续实际结果更新此报告，不借父814绿灯。

## 尚未运行与生产限制

硬件掉电、真实ENOSPC、低端ARM/能耗/真实256MiB库1000版本、Root、敌意/密码PDF/native worker deadline、未来附件四语/320dp/2×/TalkBack、生产Tink/R8增量、跨设备完整备份和外部独立审查均NOT_RUN/NOT_REVIEWED_EXTERNALLY。预提交seam异常是FAULT_INJECTION_ONLY。最终原B2/B3/B4/R15/MED01回归及Release隔离要取最后HEAD。

MED-02B3（B2-SECURITY-GATE2）是安全准入和集成证据阶段，**不是 B3 完整加密备份**，也**不是可向用户交付保存唯一医疗原件的正式资料库**。正式 B2 仍需协议/密钥方案的外部独立审查和负责人批准；B3 完整可信备份与跨设备恢复、B4/B5 图片与多页 PDF、B6 真实设备和无障碍验收以及明确启用批准仍是后续门槛。
