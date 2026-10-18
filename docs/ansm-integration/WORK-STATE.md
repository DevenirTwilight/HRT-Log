## P4 原生排序索引仍FAIL；批量操作读取本地通过 — 2026-10-18

已普通合并主线417e383（#66–#70）为0fd0ca3；四语冲突保留全部主线提醒文案和既有forecast_needs_check，workflow保留17数据类与2新增App类，双方交接保留。主线需求105–107原编号保留，旧P4的105–108映射108–111；新106普遍自动合并授权继承，草稿/外部审阅/发布例外不变。合并后Period5/Privacy3本地PASS，证据merged-main417-app-local-evidence.json；生产参数/PK/权限没有新增变动。

0fd原生run39122744634官方工件SHA/CRC、1470239B trace及可复现parser已归档native-profile-0fd0ca3/：完整audit14149/forecast13529/restored12720ms本轮<15s，但三次写入13726/13307/13938ms>5s仍FAIL。SQL减少不能宣称原生改善。97996采样、152边界未闭合帧、零未知方法/不匹配退出、无溢出，PARTIAL_DIAGNOSTIC；SQLCipher游标仍首要成本。相同SHA重复手动run39122741164取消，仅防重复、无验收结论；保留的PR原生运行不论成败完整记录。所有既有失败和预算保留。

新增零分配操作负向测试：旧实现真实expected0/actual1逐操作回读；现在一次游标读取同一组完整操作字段，NULL/整数/String类型以及全部链/版本/来源/账本/历史校验保留，不增加持久或跨事务缓存、不接写入。data418零失败/跳过，Android测试源码编译PASS；原同规模JVM大库/完整投影相等、SQL14189→12186（三写1379/1388/1495ms）仅诊断，原生未验。对应bulk-operation-*证据与临时诊断patch已保存，生成源码已清理。候选六CI/原生待新HEAD验收。

Beta #64只读同步main417为063861d；区分公开build29、由主线账本声明/其他开发线交付的私密build30（source dfb，不含#70或P4）与本P4候选；没有重新下载/验签/交付build30，真机/升级NOT_RUN。P4_BLOCKED/DRAFT、App入口OFF，P5/P6未实施，Beta NO-GO，本工作未签名发布。

## P4 排序索引回归通过；写入性能仍待原生验收 — 2026-10-18

3ee5110六项必需CI全PASS，已核验官方工件SHA/CRC：API26/29/35的ReferencePersistenceAndroidTest各23用例、含2条新字节边界、零fail/skip。原生run39121269126完整FAIL，仅真实写入11959/11020/11524ms>5000；audit11286/forecast11600/restored audit10395ms本轮<15000，不能当稳定/因果证明。运输双ACK PASS；1155058B trace离线parser完全复现，76108样本、152边界未闭合帧、零未知/不匹配退出、无溢出，仍PARTIAL_DIAGNOSTIC。SQLCipher游标为主要采样成本。原始工件/报告/hash见p4/performance/native-profile-3ee5110/，不删除早期失败。

先写共享SQL/边界负向测试，原实现真实expected0/actual8；第一个分组Map方案虽data417 PASS，却在相同大库查询20198→20811退化（挤掉原始行缓存），已拒绝且未推送，证据保留。替代方案仅按剩余8MiB预算为已完整缓存的不可变行建立排序引用索引，保留重复行、整数类型、原游标独立性；部分/未建索引回SQL，不缓存跨事务信任，不改审计或存量语义。新方案data417零失败/跳过、Android测试源码编译PASS；同规模JVM大库完整投影一致、查询20198→14189（少6009），真实Repository三写入1489/1561/1588ms仅JVM诊断，原生NOT_RUN。新增重复确认测试初次JUnit返回值声明错误已明确Unit修正并保留失败，未放松断言。原始fixture与所有预算不变；诊断patch只加在计时结束后的临时测试副本，生成文件已清理。

主线另有#66–#69到1abfeea，真实device-tests在定时提醒链FAIL；#70由另一开发者处理，避开提醒/UI/字符串共享文件。下一步普通合并主线并保留新旧需求/交接/设备类名单，再做最终CI及原生预算。P4保持DRAFT/PRODUCTION_BLOCKED、正式入口OFF，P5/P6未实施。主线私密build30是独立授权与交付，不含未合并P4；公开build29未改变。本轮未签名、发布或交付APK。

## P4 采样运输已验证；全字符串追加优化本地回归通过 — 2026-10-18

原生0b3c703/run39109780636已取回2310261B ART采样；8字节预检与最终nonce ACK真实PASS，官方工件SHA/CRC、原始trace SHA和确定性gzip已保存，离线重跑parser与原摘要完全相同。采样158040条、152个边界未闭合帧、0未知方法/不匹配退出、无overflow，仅PARTIAL_DIAGNOSTIC，不是调用计数或完整CPU剖析。此前head失败保留，不再称运输待验。证据：p4/performance/native-profile-0b3c703/。

同轮原始预算仍FAIL：audit15569ms、forecast16150ms、三次真实写入15939/15672/15662ms；恢复后audit14587ms。fixture/SQLCipher/512MiB模拟器/192MiB堆/全部预算不变。真实热点以SQLCipher游标读取和Android26 CharSequence区间追加为主，不按猜测改正则/UUID。新候选只在完整Unicode标量扫描后对未转义完整字符串调用String追加；转义区间及全部NUL/代理项/大小/摘要/历史字节约束保留。先新增2条共享边界测试，旧实现加新测试5项PASS，再改生产；全data415、Period5、Privacy3零失败/跳过，Python198全套PASS。环境重启后原Gradle日志不可用，保留实际JUnit时间戳/摘要；原生合同编译产物存在，真实新设备结果仍待CI，不冒充完整日志或实机通过。

主线8ebb719和Beta文档49236a2各六必需CI全PASS；#65已按单独授权合并，旧build29不含该修复。P4候选未合并、DRAFT/PRODUCTION_BLOCKED，App精确写入口OFF，P5/P6 NOT_IMPLEMENTED，Beta NO-GO。无Schema/备份格式/库存行为/PK/签名/版本/发布变化。下一步推送候选并用完全相同API26大库及六CI验收；未达5秒继续基于实际热点优化，不削预算或全量审计。

## P4 原生预检定位 API26 head 不支持字节模式 — 2026-10-18

ceb504d run39109323920在生成大库/加载SQLCipher前按设计停在TRANSPORT_PREFLIGHT_FAILED；PERFORMANCE_NOT_RUN，所有本轮性能数值UNAVAILABLE。官方工件12129374268 SHA/CRC已核验；原始逐路径错误明确：正确测试包的run-as执行head返回exit1/head: not integer: c，另一个候选包unknown。这是本次预检的已确认工具故障；前两轮未保存原始远端错误，不倒填为完全已确认。不会把fail-fast当预算通过，最后完整4dc写入15.6–16.3s仍FAIL/P4_BLOCKED。

新增API26错误响应负向测试在旧reader实际29项/1失败；改为有界dd（bs=字节上限、count=1），统一非PTY shell v2分离stderr，避免exec-out将dd统计文字混入二进制。JSON硬上限65537B、trace硬上限expected_size+1（<=32MiB+1）、8字节含NUL/CR/LF/高位字节探针最多读9B并逐字节验证；App-UID固定路径、nonce/ACK与预算阶段之间不轮询不变。Python198 PASS；生成的Kotlin未变，复用刚完成的原生编译证据，不重复编译。实际dd运输预检待验；无生产代码/预算/Schema/备份/PK变化。

主线8ebb719已六CI PASS，26/29/35各4新增SQLCipher用例工件核验PASS。Beta #64已普通同步主线并推送49236a2，仅文档，准确区分主线修复与旧公开build29；新文档CI待验。P5/P6未实施，正式入口OFF，Beta NO-GO，未签名发布。

## P4 测试运输改为 App 内部路径与大库前预检；主线六 CI 通过 — 2026-10-18

#65 已获单独明确批准后普通合入主线8ebb719，合并后六项必需CI全PASS；官方工件SHA/CRC核验26/29/35各4个新SQLCipher用例真实PASS/0skip，见performance/main-8ebb719-*。本地P4通过普通merge同步主线（223aeff），workflow保留17类并集、双方HANDOFF保留。data413、Period5、PrivacyNoticeFacts3、Python190及原生合同编译PASS，Privacy初次过滤名误写后已实际按正确类名补跑3项，证据已更新。

4dc09b8原生run39108085887完整FAIL：实际写入15604/15957/16339ms仍>5s，审计/预测/恢复后审计本次低于15s。ART文件生成2300664B，但host_ack=false、未取回，且没有逐路径错误信息；仅切换shell不足以完成运输，根因仍UNVERIFIED，不声称已分析方法热点。工件12129850807官方SHA/CRC及原始报告已归档native-4dc09b8-*；7590两次旧失败也保留。

新候选只修改测试工具：使用debug测试App UID的run-as读取自己的内部固定路径，不依赖外部存储别名。完整计量库/SQLCipher加载前，验证8字节含NUL/高位字节探针和App自己读取的nonce ACK；失败15s内停在TRANSPORT_PREFLIGHT_FAILED/PERFORMANCE_NOT_RUN，保留有界逐路径退出码/stderr。成功预检后不轮询adb，等原预算全部冻结后的采样ready才取方法栈并回执，原性能断言/fixture/SHA/预算不变。先新增负向测试实际25项中1失败/3错误，再Python197 PASS；生成原生源码编译PASS并清理测试生成文件。实际新运输NOT_RUN，不能用合成预检冒充原生成功。

P4_BLOCKED/DRAFT、正式入口OFF；P5/P6未实施，Beta NO-GO。无生产代码/Schema/备份/PK新增改动，无新签名、发布或APK交付。完整证据见docs/ansm-integration/p4/performance/与阶段账本。

## #65 获单独批准后普通受保护合并 — 2026-10-18

负责人明确批准#65普通受保护合并；重新核验最终e69490f六CI全PASS、无冲突或未解决讨论，使用--merge --match-head-commit合入8ebb7198fe1dbd1d0c2a5f96e24cdecf7efac54c。不用admin/force/auto，不扩张其他PR授权。主线新CI待验；P4将通过普通基线合并保留daily/measured测试类并集、双方交接，不复制未合并分支。P4仍BLOCKED/OFF、原生同源attempt2运行中、P5/P6未实施，未发布。明确批准记入REQUIREMENTS§108。

## P4 最新同源原生进程退出；#65 最终六 CI 通过 — 2026-10-18

7590cc7 run39106781296 attempt1 在 full_encrypted_restore 阶段测试进程退出。已核验官方工件12129061270 SHA/CRC，保留原始 IN_PROGRESS 报告、环境、exit 和合成日志（performance/native-7590cc7-attempt1-*）。退出前审计9120ms、预测8854ms、备份9835ms仅为已完成阶段；恢复后写入、最终审计、ART采样均未完成/UNAVAILABLE，不构成预算PASS。日志同时出现多个其他系统进程死亡，但没有本测试进程的明确OOM栈/tombstone，不把低内存杀进程写成已确认根因。相同源码/预算的attempt2通过job rerun endpoint启动，只用于复查退出和采样运输；旧失败必须保留，不以一次成功复跑声称根因修复。当前不新增生产优化，不改预算或入口。

独立#65最终e69490f的push/PR全部六项必需CI均PASS（Android39106169277/39106171857，Science39106169223/39106171852）；源88a60f1的26/29/35各4新SQLCipher用例及完整工件核验已在其分支归档。#65已转Ready for review、正文更新最终证据，未自动合并：负责人任务§2.1不允许扩张ANSM授权到一般CI/日常数据修复。P4未复制其代码，共享workflow类名单未来须保留并集。P4_BLOCKED/OFF，P5/P6未实施，Beta NO-GO，未签名发布。

## P4 握手工具准备失败已定位修正；性能未运行 — 2026-10-18

0b7bf67 run39106094875在测试前失败：我新增的强制adb logcat -c在API26返回failed to clear main log。工件12127979394仅source.json，官方SHA/CRC核验PASS；本轮PERFORMANCE_NOT_RUN，数值UNAVAILABLE，不拿上一轮数字充当本轮结果，也不归因生产库存。证据native-0b7bf67-preparation-failure.json。

移除对清日志权限的依赖，改为主机为每次运行生成独立nonce，并同时传给Instrumentation、watcher和collector。原预算开始前仅记录元数据；旧日志ready和旧绿色报告不匹配当前nonce就不能触发adb轮询/被归到当前运行。仅收到本轮预算后ready才采集并回执；保留固定路径、大小/完整性、原退出码和预算后30s上限。新增nonce转发/非法输入/旧ready不触发设备读取/旧绿报告不冒充当前失败的独立测试。Python188和生成原生源码编译PASS，修正后真实运输NOT_RUN。没有生产代码/预算/fixture改动。

#65源88a60f1六项检查及三API各4 SQLCipher用例、data247/reminder20/app1379（205既有skip）工件均已核验；e69490f仅证据文档的最终CI待跑，未合并一般日常数据修复。P4_BLOCKED/OFF，P5/P6未实施，Beta NO-GO。

## P4 采样执行但工件被清理；改为预算后握手 — 2026-10-18

efc7aba原生run39104315237仍最终FAIL：本次三写入16143/16304/16463ms>5s，审计14793ms/预测14853ms/恢复审计14887ms。全部原始指标及官方工件12127614905 SHA/CRC已归档native-efc7aba-*，不借此前较快结果宣称稳定通过。

采样阶段实际创建2302566字节方法栈文件且全投影等值断言通过，但AGP结束后清理APK，原leave-APKs选项没有保住external-files，trace未取回，分析UNAVAILABLE；没有推测方法耗时。后续仅修测试工具运输：Kotlin在原预算冻结后发ready标记，主机平时只读既有本地log，不在原计时期间轮询adb；收到标记后才按固定路径、大小上限读取ready/report/trace，用新nonce绑定并回执。测试只在预算后最多等30s再执行原失败断言；正常APK清理继续，原失败退出码不变。缺失/错nonce/未知格式不伪造成功，未知格式原始字节可保存离线分析。旧trace不得归到新运行。Python184及生成原生源码编译PASS；实际握手采集/ART分析NOT_RUN。不改生产代码/预算/fixture。

独立#65源88a60f1六项必需CI全部PASS，API26/29各4实际SQLCipher用例工件已核验；API35/完整构建报告正在最终核验。一般日常数据修复仍不自行扩大ANSM自动合并权限；P4不复制未合并生产代码。P4_BLOCKED/OFF，P5/P6未实施，Beta NO-GO。

## P4 解析复用原生已验证但5s门仍FAIL；准备ART采样 — 2026-10-18

3cea864最终六项常规CI全部PASS；独立原生run39102897438完整FAIL。审计12576ms、预测12645ms、恢复后审计10083ms本次低于15s；真实写入12666/13373/12296ms仍>5s。逐操作4315ms、逐包装3407ms；正则编译12000次155.75ms vs复用44.34ms，差111ms，不是主要瓶颈，不修改生产正则。工件12127009156官方SHA/CRC及原始报告已归档native-3cea864-*。保持原历史失败，不称稳定准入。

下一提交仅测试工具：所有原计时/峰值/预算冻结后对同一全审计做ART方法栈采样，保留完整投影等值断言。源码不进入src/main，不采集参数/数据库/备份。新runner先拒绝真实设备/非API26非QEMU，AGP仅在新建合成CI模拟器保留测试APK以便结束后取自己目录的trace，随后整台临时模拟器销毁。采集只接受固定合成路径，先验大小且硬限制最多32MiB+1，保留原测试失败退出码；未知格式或缺失trace只记UNAVAILABLE，不能变成预算PASS。解析器对双时钟、递归、截断、时钟倒退、溢出/未闭合及环境隔离有独立合成测试。Python179 PASS、生成原生源码编译PASS；实际ART trace NOT_RUN。不修改生产代码或任一预算。

独立日常回收站修复已提交#65，head88a60f1；先固定时间4红，再最终data247/Python156/原生源码编译绿，新增4共享用例覆盖26/29/35，最终CI进行中。该一般日常数据修复不假定ANSM自动合并授权，保持DRAFT。P4当前不混入未合并#65；未来workflow名单冲突需保留daily及两个measured类的并集。

P4_BLOCKED/OFF，P5/P6未实施，Beta NO-GO。未签名、未发布、未合并。

## P4 单次消费解析复用已编码并通过本地回归 — 2026-10-18

最新确认65cf08e的六项常规CI全部PASS，但独立原生run39069126496四预算FAIL，真实写入18.60–19.03s>5s。原生逐操作审核（含构造）7563ms、逐包装投影6295ms；工件12114726822 SHA/CRC及报告已归档native-65cf08e-*，不使用前次较快运行冒充稳定修复。

在旧实现上先实际通过2个冻结旧解码器对照测试（39有效/33明确拒绝输入），再改为单次审核内复用完整验证的消费 envelope/request/receipt，避免两个热路径各自重复解析、重复 canonical/hash。所有原断言保留，不新增跨行/事务缓存，不改Schema/备份/PK/分配算法/入口。候选本地 data409零fail/error/skip、原生共享合同及生成大库源码编译、Period5/Privacy3、Python169均PASS。同规模JVM大库审计1348ms、写入1686/1573/1661ms，仅DIAGNOSTIC_ONLY，原生新结果NOT_RUN。完整范围与证据见performance/consumption-decoder-reuse.md、receipt-decoder-local-evidence.json。新探针首次转义编译失败及环境缺编译器已记录，修正后重编译通过；未记为原生执行成功。

发现独立Beta分支#64的一个Android作业在TrashDataTest真实失败：trash_item.kind/ref UNIQUE冲突；同SHA另一作业PASS不能视为修复。已在独立fix/daily-trash-ref-collision-20261018工作树准备固定时间复现；当前P4 diff不含该修复。P4_BLOCKED/DRAFT/正式入口OFF；P5/P6未实施，Beta NO-GO。

## P4 同机原生对照已完成；写入仍超预算 — 2026-10-17

测试提交cdc50b7，run39068064156，官方工件12113788250 SHA/CRC核验PASS。完整原生报告见performance/native-cdc50b7-report.json，来源见同名evidence。原验收阶段审计14494ms、预测13971ms、恢复后审计13663ms，均低于15s；三次真实写入15245/15521/16471ms仍超过5s，最终FAIL，P4_BLOCKED。生产代码与上一db34e87相同；上次四预算FAIL完整保留，不能声称已修复稳定性或放行。

后置同机对照：raw17052ms→cached12048ms；cached13311ms→raw16985ms，完整库存投影全部相等。列扫描830ms、引用1680ms、状态含引用2035ms、链791ms、缓存构造257ms，证明缓存有实际收益，但尚未解释剩余主要耗时。JVM同样的后置完整诊断已实际跑通，仅辅助证据。继续仅测试分拆现有私有逐包装投影/逐操作审核的耗时；不省略原全审计，不变更生产算法/预算/入口。最新细分诊断原生源码编译PASS、Python169 PASS、完整JVM大库含两方法反射执行与全部投影断言PASS（仅诊断）；新的原生结果NOT_RUN，不能借用本次结果。

P5/P6仍未实施。Beta #64新增现有build29八个ELF库PT_LOAD p_align=16384静态证明；设备/16KiB运行、覆盖升级仍NOT_RUN。正式入口OFF，未发布。

## P4 原生行缓存复验失败；下一步仅测试分段诊断 — 2026-10-17

当前生产候选 db34e873c692c5a10af1d7ae659933c2af6800b7 的原生 run39066391148 已完整结束，四项时间预算 FAIL，不再标为待跑。审计17477.76ms、预测17541.03ms、恢复后审计18962.86ms，三次真实Repository写入19410.66/19156.16/19755.02ms，仍超过15s/5s。官方工件SHA及完整报告见 performance/native-db34e87-evidence.json 与同名report；未改预算。JVM查询62219→20198（约67.5%减少）不构成原生改善证据；data407、Period5/Privacy3、Python168和原生源码编译通过。

主线d13048b合并后Android run39065037526及Science run39065037594全部成功；#63主线修复完成。P4仍P4_BLOCKED/DRAFT/正式入口OFF，P5仅接线审计，P6未实施。Beta文档#64已只读核验现有build29，仍NO-GO；没有新签名、APK交付或Release。

下一次原生生成测试仅在原有全部计时、峰值与预算结论冻结后，停止预算采样，再运行同一合成数据库 raw→cached / cached→raw全审计对照，并断言全部投影相等；记录列扫描、引用校验、状态校验、操作链及缓存构造分段。仅测试反射调用原全审计核心；不写库、不替代原预算断言、不进入正式APK，不额外预热原验收路径。诊断时长不纳入已冻结的原预算峰值，其自身内存峰值未测，不称完整进程峰值。诊断结果尚未运行。

## 2026-10-17 事务内只读参考行快照

a3e95e4真实API26结果已归档：审计18135ms、预测15773ms、恢复后审计15620ms、三次写入17801/17638/17848ms，仍四门FAIL；完整恢复81099ms通过120s。绝不把改善写成P4准入。

实际合成SQL计数：一次完整审计62219查询，其中按主键重复读取约44000次。新增 ReferenceAuditReads 只读事务局部、有界原始标量缓存与独立游标；未覆盖查询/部分缓存必须回SQL，不伪造不存在；全过程数据修改计数/Schema版本变动硬拒。操作envelope不保留；FrozenConsumptionRequest只缓存不可变DTO并冻结List。全部旧断言保留，当前data407无fail/skip，原生源码编译PASS，新增返回篡改/生命周期/写入/回滚/小预算回退合同。JVM同规模审计1394ms、三次写入1828/1721/1815ms，仅DIAGNOSTIC。当前实现原生及最终六CI待跑，P4_BLOCKED/OFF。

## 2026-10-17 主线 CI 修复合并与本地最终补证

#63 由负责人已启用的自动合并流程合入 d13048b18ad15eb4e7aba70f63c3c5d94a705660；源80c31ce最终六CI均成功。已校验CI工件SHA/CRC：reminder20、data243均0fail/skip，app1379零fail、205既有skip。主线新复验39065037526尚在跑，不能称主线复验完成。P4 已同步这份已合并修复，保留双方 HANDOFF。

a3e95e4 本地最终 native 源码编译、Period5、Privacy3 PASS；最终 JVM 大库审计1974ms、预测2337ms、恢复6303ms、三次写入2625/2352/2415ms，峰值475205928B，仍DIAGNOSTIC_ONLY。低内存原生39064593821待结果。优化前bfcec65重复原生 FAIL，新增恢复151970ms超120s；完整报表及SHA/CRC已归档，不只挑选较好的一次。P4_BLOCKED，正式入口OFF。

## 2026-10-17 P4 审计热点优化提交

保留原始提前行数门后，最终本地 data402 零失败/零跳过；Python168 PASS。单构建器 canonical 以冻结旧编码器作字节/SHA 对照；候选列检查保留所有类型/行数/UTF8预算，批量扫描；候选来源检查仅事务内有界复用完整文本。新3项 shared 负向测试复用原生 SQLCipher runner，未削弱旧断言。先前中间版 Android 源码编译成功，最终重编译/原生预算与六CI待跑。中间 JVM 大库审计1916ms、三次写入2369/2534/2267ms，DIAGNOSTIC_ONLY，不冒充 API26。

主线 CI 五项实际失败出处和公开依赖 URL 已核对，历史数字 HTTP 状态 UNAVAILABLE；#63 为 Claude 独立修复仍待 CI。API35 Back 三失败原因 UNVERIFIED，未改断言。现有 UI 最小接线审计见 p5/existing-ui-wiring-audit-2026-10-17.md；未改共享 UI。Beta 缺口清单独立草稿 #64，真机/升级 NOT_RUN。结论 P4_BLOCKED / 正式入口 OFF，P5/P6 未实施。

## 2026-10-17 后端收尾任务重新核查

默认分支已到 35600a0（#60 仅参考图、#62 发布账本已合并）。#63 是 Claude 独立 CI 修复，正在检查，不争用共享 Gradle/CI。#57 原生 run39047684726 完整运行但四项时限失败，证据见 p4/performance/native-fifth-evidence.json；P4_BLOCKED，P5/P6 未开放。CPU 诊断不是验收。新任务原文归档，需求编号冲突保留两份授权后顺延；发布 build29 不重做。

# 工作状态

2026-10-17。最新默认claude/new-session-1959qb为f1e9b4de1c97e73966b4af8900b78391eada5c43（P3合并后六项CI通过）。下表为当前状态，历史日志保留当时的失败与恢复证据。

|阶段|状态|当前证据及下一动作|
|---|---|---|
|Phase0|DONE|联合实施§102和受保护自动合并§103；官方许可/FT-ST未闭环，打包OFF|
|P0|INTEGRATION_VERIFIED|#53已合并，主线六检查和三API恢复合同通过|
|P1|PRODUCTION_IMPLEMENTED|#54合并c95cea1，主线六检查通过；Room11/业务10，实际包装未启用|
|P2|PRODUCTION_IMPLEMENTED|#56合并b5d9751，主线六检查复验PASS；真实手动来源/关联/历史UI，App1379零失败205条件跳过，data243与192图已核验|
|P3|PRODUCTION_CORE_IMPLEMENTED|#55源e1c40bd六检查成功，合并f1e9b4d；只生产化计量API，不代表库存接线；主线六检查PASS；Gate3红对照顺序修复保留全部原断言|
|P4|IN_PROGRESS / NOT_ACTIVATED|#57草稿；真实Repository创建/用药/更正/删除/恢复/实盘本地data356/共享82/原生编译/Python159 PASS；b95a0d9六CI与三API共享75已核验PASS；新82/新App回归未跑；独立协调、生命周期票据、共享UNKNOWN及UI仍待完成|
|P5|NOT_STARTED|统一新计量预测不确定性和实际包装正式UI未实施|
|官方分发|BLOCKED|许可版本冲突与FT/ST未闭环，不能因#45已合并而打包官方目录|

#45/#48/#50及#51经#52桥接已合并；P0/P1/P2/P3均保护合并，无管理员绕过。当前默认运行仍Room11，Room12仅在#57待验源码，不称已部署。医疗附件/网络/PK/签名/版本/发布不动，实机/断电/TalkBack NOT_RUN。#57不能自动合并未完成的正式库存接线。资料UI医疗中心缺失部分审计见p2/medical-ui-status-2026-10-16.md。

严格JSON检查点：data334项零失败/跳过，Android测试源码编译PASS（共享计量60项原生运行NOT_RUN）。公开恢复已拒绝尾随JSON、重复键、无效UTF-8；精确桥接字段必须为有界整数，不能先经Double舍入。PNBAK1外层不变，完整精确库存导出/恢复仍OFF。3343724远端API26/29 PASS；API35 FAIL为MigrationBaselineTest残留业务10断言（实际11），本次改为明确11并新增旧容器LEGACY验证，未删除原断言。新CI待运行，不能称六检查通过。

## 历史开发记录（当时状态，当前以上表为准）

P0完整CI证据见p0-ci-evidence.json。下载的五份报告已校验官方digest/CRC；API26/29/35新增恢复合同各7/7通过；PeriodStability5/5、PK124/124通过，旧条件SKIPPED如实保留。物理设备/断电测试NOT_RUN，无签名/发布/APK交付。

P1工作树/workspace/HRT-Log-p1，分支feature/ansm02b-03b-p1-persistence-20261016，已普通合入默认cfbe62c。新持久化合同见p1-persistence-contract.md。迁移覆盖1…10→11；保持旧37实体和历史REVERSE账本，迁移失败DDL回滚，旧版本降级拒绝。备份保留PNBAK1外层加密，新业务Schema10与Room11明确区分。旧业务1…9不含引用表，未知身份不回填。

P1只启用商品引用存储和上下文捕获；实际包装确认/精确消费尚未启用。validator明确拒绝PRESENTATION及measured_operation，防止半实现数据被接受。更新引用不走saveMedication，不resize容器，不调用新库存分配器。真实绑定UI尚未实现。

中间失败已保留于开发日志：旧固定版本期望/不正确旧备份夹具、RoomDatabase关闭助手、合成REVERSE构造顺序。修正夹具和明确版本期望，未删减既有业务断言。全部B0影子合同保留，新版本头拒绝与恢复失败目标不变断言保留。

最终P1源95f44d7包含重算摘要后的未来修订/溢出拒绝及JSON字段类型拒绝；233项完整回归PASS，详见p1-local-evidence.json。#54当前草稿，尚未合并或发布。发布二进制隔离本地因未构建unsigned release而NOT_RUN，源码隔离PASS；远端CI保留完整实际构建守卫，不以缺失产物冒充通过。

P1首轮原生CI FAIL：API26原生新引用17/17及迁移3/3实际PASS；既有Gate2测试仍把当前导出断言为业务9，另发现App重启测试固定Room10/业务9期望。已修正：Gate2显式校验当前10后剥离空的新引用表、构造真实旧9备份继续验证旧恢复；App当前版本断言更新为Room11/业务10，全部既有撤销、VOIDED、幂等、隔离及重启断言保留。尚待修正后原生重跑，不把前次失败冒充PASS。

P1源d9fee991bf61b98a3d560a9a224ba29fb9b4fa9b六项必需CI在push/PR均SUCCESS。已校验三API原生及build报告官方SHA256/ZIP CRC，无APK：API26/29数据110、App70各0失败0跳过；API35数据177零失败，App264零失败/8既有条件跳过；新引用17+迁移3各API全PASS。完整App1198零失败/205既有条件跳过，data233与reminder20零失败，PeriodStability5零失败。见p1-ci-evidence.json。尚未合并；需更新默认分支依赖后继续严格检查，不把本提交的后续CI当已完成。
P2 #56已有真实用户资料UI/专用控制器/离线搜索，基于P1的stacked草稿；首轮239数据、应用29及后续30操作/逻辑PASS，32矩阵初轮截图超时已修复测试采集方式，正在重跑。官方资料仍OFF；没有启用医疗附件。P3 #55生产计量核心本地126domain/204data/发布隔离PASS，待最新基线CI。

P1新增阻断：真实控制器已复现空引用数据集恢复后首次关联旧窗口未失效；#54已转回草稿，禁止在修复前自动合并。修复采用数据库打开时初始化的本地预览epoch（不进备份，不创建业务引用），首次确认必须携带打开时epoch。成功恢复/清空和物理删除使旧票据失效；已识别对象的INSERT OR REPLACE拒绝。无Room/业务备份版本再升级，不改药物/实际记录/库存事实。新增共享原生负向测试及完整回归待运行；之前d9六检查通过仍属历史证据，不代表此缺口已获验收。

空预览修复本地完整236项PASS、原生23项合同编译PASS；红测试真实观察1条不应发生的关联已归档p1-empty-preview-red-evidence.json。新增恢复空引用/物理删除后同ID复用/拒绝替换已识别对象测试，Room1…10迁移仍不改37旧实体，仅本地runtime初始化计数明确由0改1。新epoch协议的原生23项×3尚未实际运行；#54继续草稿，P2真实控制器红场景待接新必需票据后重验。

P2源fb797e5接新previewEpoch后完整data242/app63全PASS，真实EmptyPreview恢复红场景转绿；192新截图已生成。P1新增只读referencePreviewEpoch/recordedReferenceHistory接口，历史列表可携带同一事务读取的epoch，防止ID复用；无来源读取不创建业务引用，当前引用更正不重写历史。新增共享测试使原生合同24项，最新API待重验。

P1已普通合入实际默认分支的ANSM-01/#45及02A/#48；不改变其冻结数据，不将参考JSON打包进Android。P1源ef19db8本地237数据PASS/原生24合同编译PASS；P2真实控制器空恢复场景及242数据/63应用PASS已完成。#54可恢复审阅并等待当前基线六检查；之前401为长驻监控凭证过期，当前工具gh访问正常，重启监控继续严格保护，不绕过检查。


2026-10-17 P4续作：#57内部真实计划服药、MISSED补录与format3冻结计划上下文已本地集成验证，完整data373/shared98、原生源码编译、Period5/Privacy3 PASS。共享UNKNOWN/生命周期/data362及四语32组192图已单独留证，混合库存正式入口守卫/data365 PASS。db1ef03六CI及三API共享82实际PASS并校验SHA/CRC。当前新增98原生和最终HEAD CI待验；后实盘独立协调、性能、P5/P6未完成，App精确写入口OFF、#57 DRAFT。完整证据与下一步见p4/work-state.md，V4逐项追踪见../ansm-completion/TRACEABILITY.json。未合并、未发布。

2026-10-17 大库检查点：P4本地data389/shared114/原生及大库原生源码编译/Python163 PASS。14600合成事实+2000包装/操作/来源的完整备份恢复已通过；后续写入预算最初真实FAIL，经保留全部校验的查询优化后4.32…4.64s转绿。新增独立API26 SQLCipher低内存性能任务，未改六必需检查；原生低内存运行与新HEAD CI仍待验，不称P4完成。#57继续DRAFT、App精确写入口OFF，P5/P6未完成。
