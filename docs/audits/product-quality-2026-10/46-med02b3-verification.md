# 46 — MED-02B3 / B2-SECURITY-GATE2 验证记录

2026-10-14。**代码阶段00cb4cf完整CI验收通过；附件生产仍NO-GO。** 最终验收文档HEAD及其全部push/PR检查以[PR31最终记录](https://github.com/DevenirTwilight/HRT-Log/pull/31)固定，交付前必须实际等最后HEAD成功，不借用旧代码绿灯。以下先给结果，再保留真实阶段性失败与修正历史。


## 已核验完整代码结果

固定代码 `00cb4cf89671f06c432fa52323e31022498a6fbf`；[PR31](https://github.com/DevenirTwilight/HRT-Log/pull/31) OPEN、未合并，stacked base=PR30分支feature/med02b2-v2-import-gate；parent814d355，默认main仍cfe600ee。父PR30 OPEN、原最终四CI成功；不改变或合并父分支。

| 检查 | 实际结果 |
| --- | --- |
| 全JVM | domain63/PK124/importer12/POC62=261，零fail/error/skip；本轮新增2个确定性context向量，父B2原20合同不重复计功 |
| Android unit | data180/reminder17/app865，零失败；app61原有skip不变 |
| lint / 构建 | full lint、Debug、unsigned Release、两test APK、provenance/manifest/Release全DEX隔离均PASS |
| API26/29 | 每API native data43/app67，零fail/skip；真实SQLCipher/Keystore/FS与6个TinkPDF方法实际运行 |
| API35完整设备 | data110/app报告249，零fail；8原有app skip（runner Finished257），所有旧重启脚本成功 |
| 三API红绿 | 故意unsafe test-only ack cleanup控制各预期1failure，同断言修正路径各1pass；不是生产漏洞声明 |
| 三API强停 | 每API WRITE/IN_COMMIT/AFTER_COMMIT三次，共9次不同fresh PID；旧CURRENT/清理、完整orphan隔离、已COMMIT新CURRENT保留分别验证 |
| 旧回归 | B2隐私/导入清理、B3导航/锁定/伪装、B4统一库存/VOIDED/恢复/分页、R15误跳过、MED01就诊/化验/Trash/PNBAK9全部保留通过；B4-E最大挂载3行不是ARM基准 |
| Science | PR gate/deep实际SUCCESS；push gateSUCCESS、deep按原分类SKIPPED，无新增skip/PK改动 |

[PR Android38625636512](https://github.com/DevenirTwilight/HRT-Log/actions/runs/38625636512)、[push Android38625628282](https://github.com/DevenirTwilight/HRT-Log/actions/runs/38625628282)各五job全部结束SUCCESS；[PR Science38625636623](https://github.com/DevenirTwilight/HRT-Log/actions/runs/38625636623)、[push Science38625628466](https://github.com/DevenirTwilight/HRT-Log/actions/runs/38625628466)全部结束SUCCESS（push既有deep门控）。不是最终后来文档HEAD的替代。

[原始artifact身份、ZIP digest、根HTML/XML计数、PID和实际内存样本](46-med02b3-code-evidence.json)已下载核对5份报告，未下载/交付APK。仅本轮新增：16项原生Store方法、3项本地SAF provider、1项普通restart合同另加3处外部强停、2项同源canonical向量、6项TinkPDF；与父B2 20方法严格区分。生产B1日志仍PREPARED，模拟有效状态在独立synthetic_gate2表，不是正式生产READY。

## 实际资源数据与未完成项目

16MiB+1/stall/cancel测试方法包含fixture建立/实际加密IO/拒绝清理；结束时仅采一次PSS，**不是峰值/吞吐/ARM性能**。代码00cb：API26 elapsed52497ms/finalPSS39142KiB；29 30710ms/55936KiB；35 56806ms/62351KiB。不能据此声称缓存reservation必然加速或冻结16/256/1000额度；独立设备基准仍NOT_RUN。其余原始方法样本在JSON，不填猜测数值。

四页合成PDF>4×64KiB跨段，正确蓝/白页中央像素、第一页/末页跳页/缩放/重开；同FD完整认证→ProxyFD随机offset→公共PdfRenderer，不落完整明文PDF/cache。坏tail在FD发行前拒绝；路径替换原inode租约继续、newopen commitment拒绝；unlink不会撤销既有FD，owner能力关闭才撤权。真实render后Page仍打开/位图未交付时，撤权拒绝并回收结果，renderer关闭与page生命周期串行。

真实ARM、硬件掉电、真实磁盘ENOSPC、Root无限竞争/整个合法旧快照回滚、敌意/密码PDF/隔离worker deadline、满配256MiB/1000真实对象、生产Tink/R8 APK增量、未来资料库四语320dp/2×/TalkBack、完整附件备份/跨设备恢复及外部独立审核NOT_RUN/NOT_REVIEWED_EXTERNALLY。seam异常为FAULT_INJECTION_ONLY；fsync返回及force-stop不是掉电认证。

## 不变量、来源与下一阶段

相对parent814，production Kotlin/src/main、Room schema10/八表/旧29表/guards、PNBAK1 magic/KDF/AES-GCM/serialization9、AppLock、PK、versionCode、正式签名配置无差异；实际CI Release全DEX/manifest无POC/Tink/provider/实验marker和INTERNET。没有取消GitHub CI；未访问真实用户数据/密钥/私有构建/内部交付链接，未接触/重建正式build26，不冒称重新校验其私有APK checksum。
ZIP四份Markdown原字节及SHA在sources/med02b2，全部通过重新hash核对；用户原欧盟稿与后续修订分别保留。它们已在PR30仓库分支，PR31继承，尚未进入默认main；SOURCE_MISSING已解决。PHR V2显式临床关联、日期/来源语义、纠错/版本/Trash、图片+多页PDF、开放前完整B3备份、Privacy P0和欧盟条件矩阵保持；本PR无提前UI。

协议决定及专家固定审查包见44；风险/实机计划与具体最小生产B2审批范围见45；代码/来源基线见43。结论RESEARCH_GO / IMPLEMENTATION_PENDING_OWNER_AND_EXTERNAL_REVIEW，生产NO-GO。仍需负责人确定独立专家、审核后wire/context/DEK/KEK/角色预算、默认关闭writer/recovery/AppLock接线及必要最小guard/ADR；**本PR没有替代批准或实施生产协议**。

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

## 首轮9360fbb真实CI及修正批

代码9360fbb19165007963d68aebc01960ff894e3ac2：PR Android38621821257的JVM/完整Android构建/26/29成功，但API35失败；push38621755505同API35失败，JVM/完整Android/26/29成功；两Android run已全部结束。两个Science38621820854/38621755706的gate/deep全部实际SUCCESS。首轮**不是全绿**。

API26/29各native data42、app66零fail/skip，红控制预期1fail→修正同断言1pass；三处force-stop后freshPID分别验证WRITING owned清理、IN_COMMIT rollback/完整orphan隔离、AFTER_COMMIT新current/revision2保留。原B1 SQLCipher/迁移/旧包强停及旧POC继续成功。[原始artifact身份/digest与PID](46-med02b3-first-device-evidence.json)已下载核对；不是APK交付。

API35 startup crash根因新增provider误用fingerprint判断emulator，0 tests，不能说native通过。改用现有已验收`/system/bin/getprop ro.kernel.qemu=1` attestation；保持emulator-only、standalone包与所有安全断言，不新增skip。

实际样本发现16MiB+1拒绝方法（包含fixture建立/IO/失败清理）耗时API26 47044ms、API29 34502ms；各fixture结束时PSS仅final_not_peak，不是峰值/ARM基准。协调器此前每8KiB重新查询native SQL reservation并构造primitive；修正为本attempt固定的durable reservation在IO前读取一次、同primitive计数，仍逐读检查actual limit/cipher overhead/capability。新样本必须实测，不承诺预测加速。

PDF增加四页有界comment padding，原件>4×64KiB，避免只验证一个加密segment；页面白/蓝交替并检查中央像素，保留第一页/末页/跳页/缩放/重开/撤权/坏tail/路径替换。不是恶意PDF或16MiB parser容量验收。

本地旧HEAD完整Android重复运行退出130：同HEAD PR完整unit/lint/Debug/unsignedRelease已成功，为及时应用已定位修正停止仅本地Gradle客户端，未取消任何GitHub CI，未把该本地命令算完整成功。修正批需本地测试编译及最后HEAD全部CI；所有首轮CI结束后才推，不重跑无原因失败。

新增第6项TinkPDF native page生命周期竞争：真实render生成位图、Page仍打开时暂停发布；owner撤权后结果拒绝并回收，Renderer close与Page生命周期串行。此为合成instrumentation seam，不是敌意parser deadline或正式AppLock接线认证。编译/最终CI结果以本批实际记录为准。

补充提交前owned leaf检查与真实路径替换负向合同：全认证后将目标改为另一个合法旧cipher，SQL gate必须拒绝，保持旧CURRENT及foreign文件、重启清理只隔离。此inode保护不声称抵抗同UID/root无限竞争或持续整库回滚。新16项Store/6项PDF要在修正最终HEAD实际运行。

修正批本地library test APK/app Android测试编译全部成功：5m11及最终增量1m20；Release检查器3方法再次通过。首轮四run已全部结束，允许推本批；新HEAD全量CI尚未完成，不能冒称最终验收。
