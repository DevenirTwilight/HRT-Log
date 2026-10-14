## MED-02B3 / B2-SECURITY-GATE2 接手与独立复核修正 — 2026-10-14

负责人指示另一会话接手PR31推到全绿且保留复核。37b813d文档HEAD的push/PR 14项检查已全部成功。独立复核见报告47（作者以外的AI工程复核，不是外部专家审核）：无阻断问题；M1恢复与写入者互斥、M2孤儿隔离保留/配额两项生产设计缺口已写入报告45风险与审批前置。
追加提交只动测试与文档：16项Store负向合同改为断言拒绝原因，双写者补失败者密文已删/胜者保留，新增Gate2上下文向量stdlib重算测试（设计校验58→61），报告46标注DIRECT_CALLBACK/TIMING_BASED证据及MIME拒绝NOT_RUN并修正旧PENDING文字。生产src/main/Schema/guards/备份/PK/version/sign仍零改动。
本地：JVM261零fail/skip、设计61、Release隔离3通过；core/data Android测试APK本地编译成功，设备测试只能由CI运行（本机无KVM）。修正提交的最终HEAD及全部CI以PR31正文为准，未全绿前不称通过；不合并、不签名、不交付APK。生产NO-GO不变。
第二轮（负责人要求“查出的问题帮我解决”）：测试协调器实现M1每operation OS锁租约（reconcile跳过活动写入者、同operation拒绝第二写入者）与M2（记录本operation新建父目录、AFTER_CREATE失败/强停可清理自己的叶子；显式确认释放孤儿、释放配额且不删外来文件），新增3项Store测试与AFTER_CREATE强停（每API 4处）；两处100ms计时证据改为等待BLOCKED；Tink测试依赖改用版本目录。本地JVM261、设计61、Release隔离3、两个Android测试APK编译均通过，Release运行时类路径无Tink。cc6b2d7的API26/29设备结果已证实收紧后的断言成立。孤儿保留期/用户提示、退役epoch active指针（B3）、经内核ProxyFD撤权读取仍为后续事项。

## MED-02B3 / B2-SECURITY-GATE2 代码全绿及最终交接 — 2026-10-14

PR31 https://github.com/DevenirTwilight/HRT-Log/pull/31 OPEN未合并，stacked于PR30(parent814d355，仍OPEN)，main cfe600ee不变。代码00cb4cf89671f06c432fa52323e31022498a6fbf的push/PR Android38625628282/38625636512全五job成功；Science38625628466/38625636623成功，PR deep实际通过、push deep按原gate skip。
JVM261；Androidunit data180/rem17/app865、61原有skip；26/29每API data43/app67零fail/skip；35 data110/app报告249、8原有skip。新16 Store/3 SAF/1 restart+3外部强停/2向量/6 TinkPDF；9次freshPID、三个API红1fail→绿1pass全部实际成功。原B2/B3/B4/R15/MED01/PNBAK9/迁移/重启回归保留。原artifact/SHA/PID/实际final-not-peak PSS见46-code-evidence，首轮9360 API35失败保留。
报告43基线、44协议/外部审查包、45风险/审批、46验收已完成。当前末次文档HEAD还须等待全部最终push/PR CI；精确SHA及最终CI固定PR31正文，不能以00cb结果冒称后续HEAD。生产源码/Schema/guards/备份/PK/version/sign不改、feature0；真实原件/密钥/私有build26未访问，无合并/正式签名/交付APK。ZIP四MD已在PR30归档并SHA核对，PR31继承但尚未进main。
RESEARCH_GO / IMPLEMENTATION_PENDING_OWNER_AND_EXTERNAL_REVIEW，生产NO-GO；ARM/掉电/真ENOSPC/敌意PDF/外部专家/未来完整备份和附件UI仍未验收。此MED-02B3=安全准入证据，不是路线B3完整备份或正式资料库。后续最小B2范围与需批准事项见45，不自动开始。以下保留阶段历史。

## MED-02B3 / B2-SECURITY-GATE2 首轮设备证据及修正 — 2026-10-14

PR31 https://github.com/DevenirTwilight/HRT-Log/pull/31 OPEN，stacked于未合并PR30(parent814d355)，不自合并。首轮9360fbb：PR/push Science gate/deep成功，JVM261及PR完整Android构建成功；API26/29各data42/app66、红1fail→绿1pass、三处force-stop freshPID实际成功。API35 provider误用fingerprint导致startup crash/0tests，首轮非全绿。
修正复用现有ro.kernel.qemu attestation；fixed reservation/primitive移出每8KiB native SQL循环；四页PDF扩为>4段并验证蓝/白页面像素。全部test-only，生产feature0/guards/Room10/PNBAK9/PK/version/sign不改。证据原始JSON及SHA/PID见46-first-device-evidence。
首轮四run现已全部结束；修正library/app测试编译成功5m11+最终1m20，准备推本批，不取消GitHub任务。本地重复旧HEAD全Android退出130，不冒称本地完整通过（同HEAD PR完整Android已成功）；下一步新测试编译、推修正及完整最后HEAD CI，更新报告46与最终PR正文。生产NO-GO/外部专家、ARM、掉电/真ENOSPC/敌意PDF仍未验收。此阶段不是完整备份B3或正式附件上线。

## MED-02B3 / B2-SECURITY-GATE2 开发中 — 2026-10-14

从PR30固定814d355c60396a9246ab6883ea70d3ccf67438cc创建独立feature/med02b3-b2-security-gate-20261014；PR30仍OPEN未合并，base默认cfe600ee，四组最终CI已成功。新PR须stacked指向feature/med02b2-v2-import-gate，不合并父PR。
新增test-only原生SQLCipher+FS+AndroidKeystore隔离协调器/失败矩阵/强停新PID脚本和Tink→全认证固定FD→ProxyFD/PdfRenderer合成四页PDF测试；正式Schema/guards/PNBAK/PK/版本/sign/AppLock不变。生产feature0/VERIFIED禁用，库测试包仅net.plainnotes.app.data.test，禁止pm clear/真实用户数据/私有构建读取。
最新library test APK及app Android测试编译通过（首轮5m27、全app7m31、最新增量1m16）；JVM261零fail/error/skip、Python设计58及Release检查器3方法/33污染subcases通过。首次提交准备推送及创建stacked PR；native设备/最终HEAD CI尚未完成，不称通过。下一步修正编译、push创建stacked PR，运行完整JVM/Android/lint/Debug/unsignedRelease/API26/29/35/Science，保留红绿及真实强停日志；每轮CI结束后才再推。报告43–46记录新证据及未决专家/ARM/掉电/生产批准。此阶段不是完整备份B3或正式附件上线。

## MED-02B2 / B2-GATE — 代码完整验收通过（2026-10-14）

独立PR30 OPEN/未合并，默认base cfe600ee；f8代码push/PR Android/JVM/API26/29/35与Science均成功，JVM259、新20，Android180/17/865、原61skip；API35 88/243、原8skip，26/29每21/61，零失败/新增skip。最后交接文档HEAD及最终checks见PR30正文，须全部结束成功再交付。
产品V2/补充来源/欧盟修订与官方核查、协议比较/失败合同、Release隔离已交付（39–42）。研究/合成验证GO，生产B2 NO-GO：专家/正式protocol/key/FS+Room/AppLock/完整备份和ARM门未通过；附件默认0，Room10/PNBAK9不变。未合并/签名/发布新APK。以下历史索引保留。

## MED-02B2 / B2-GATE 设计与合成合同推进 — 2026-10-14

分支feature/med02b2-v2-import-gate，parent cfe600eeb6efcca60668763a31969af07a2892b9。三前置PR27/28/29均已合并，最终主线Android38598762817全5成功/Science38598762944 gate成功（文档deep按门控skip），MERGED_CI_VERIFIED。未改父分支。
用户新任务及补充资料授权见REQUIREMENTS §92；四Markdown原样归档sources/med02b2，SOURCE_MISSING已补齐。报告39–42/欧盟矩阵已起草，接受/拒绝/延期有来源映射。Tink1.23仅test依赖，不修改production Kotlin/Room10/PNBAK1/PK/版本/签名/feature0。
失败先行1/1损坏tail仍可读首字节→完整认证门；空对象seek EOF失败→同FD顺序stream认证后租约。新增20合同通过，全JVM259零失败/skip；Python设计58/Release隔离3通过；全JVM/Android编译及完整CI逐步执行，最终HEAD尚未全绿时不称完成。真实ARM/硬件掉电/真ENOSPC/新B2联合SQL+FS强停/外部专家仍NOT_RUN。
下一步完整full unit/lint/Debug/unsignedRelease+API26/29/35+Science，独立PR默认base，等待最终HEAD检查。生产B2仍NO-GO/需另批协议/key/guards/持久化接线批准；不自动合并、不真实原件、不B3、不签名交付APK。

## MED-02 前置栈主线合并验收 — 2026-10-14

负责人明确“合并”，见REQUIREMENTS §91。PR27/28/29已按顺序合入claude/new-session-1959qb，保留merge历史：27=d4564fc50201b1d8132d63467028c230e87521fa；28=79f50e81c03a2077ede999c18952dd104df548b1；29=be953672f503940a10880e23dabaaa853d3017bb。每次合并树与该PR已验收HEAD逐字节相同，后续PR retarget后的diff与原stacked diff相同。
主线代码为MERGED_CI_VERIFIED：27 Android38591069907/Science38591070376；28 Android38593942970/Science38593943130；29 [Android38596888161](https://github.com/DevenirTwilight/HRT-Log/actions/runs/38596888161)/[Science38596888022](https://github.com/DevenirTwilight/HRT-Log/actions/runs/38596888022)，全部结束成功，三次Science deep实际成功。最终交接文档HEAD及其主线检查以PR29正文最终集成记录为准，不用代码HEAD冒充后续文档HEAD。
Room runtime10/PNBAK1 serialization9，附件feature默认0。没有真实附件/正式对象协议/新完整备份/生产预览/AppLock原件服务入口；PK、旧29表、正式version/sign/build26不变。真实ARM/硬件掉电/外部独立专家仍NOT_RUN。B2-GATE仅按新任务书开展设计和隔离合成验证，B2生产仍需独立批准；不自合并后续PR、不签名交付APK。以下均为历史阶段记录，旧OPEN/stacked陈述不再代表当前状态。

## MED-02B1 — 基础实现及代码验收完成（2026-10-14）

明确生产Room10授权见REQUIREMENTS §90。默认关闭的八表/DAO、stable typed identity/Trash墓碑、CURRENT/epoch/三UUID域、密钥隔离基础、PNBAK9兼容和可撤销commit/Reader原型完成。
代码f04c3fe的push/PR JVM、Android/lint/Debug/unsignedRelease、API26/29/35及Science全部成功。JVM239；Android单元data180/rem17/app865，原61skip；API35 data88/app报告223，原8skip；26/29各21+41及独立强停验证。Room9迁移/旧链/失败回滚/PNBAK/身份/密钥/真实process force-stop和B2/B3/B4/R15/MED01回归全通过，无新skip。
报告38提供可复现证明与未解决风险。独立PR29 stacked到未合并28/27，不自动合并。最终文档HEAD交付也必须CI全绿；精确最终SHA/CI入口见 https://github.com/DevenirTwilight/HRT-Log/pull/29 正文及checks。
不启用真实附件、不正式cipher/full备份/生产PDF、不PK/version/signing/APK交付。真实ARM/硬件掉电/独立专家认证NOT_RUN，下一阶段须单独批准，不自动开展B2/B3。

## MED-02B0交付设计与最终检查入口 — 2026-10-14

PR28 https://github.com/DevenirTwilight/HRT-Log/pull/28 OPEN，stacked到PR27已验收固定1c8d385，均不合并。
报告32–37和三份AI peer原稿完成：协议公开向量/Room10/完整backup状态机/配额纠错政策/B1–B6/B1提示词。
不外部认证、不启用生产附件；Schema9/PNBAK1/PK/version/正式签名/build26不改。
代码阶段651e510 push Android38543179106、PR Android38543186185、push Science38543179176、PR Science38543186188全部success（PR deep实际成功）。
实际JVM194+POC35；data160/rem17/app865零fail(61原有skip)；API35 data66/app215零fail(8原有skip)；API26/29各33零fail/skip。
新3 filesystem实验实测Os.link EACCES不可用，O_EXCL禁止覆盖/目录sync/leaf nofollow通过，不证明硬件掉电。
本地干净651unsigned Release与两test APK补跑通过；之前dirty源码中止是provenance保护，不绕过。
最后合成草案补强localDataset/cryptoVault/physicalGeneration三域与CURRENT gate、不可变cipher commitment、owned operation wrap生命周期：
Room36+backup模型14+framing3=53，scripts全部88、0fail/newskip；公认新protocol/actual restore仍未生产实现。
最终本次HEAD必须完整push+PR JVM/Android/lint/API35/API26/29/Science再过，验收结果与精确HEAD/四CI链接记录在PR28正文/checks，不能冒用651先前绿灯。
最终交付主审会等待这些checks全部结束；审阅者应看PR28当前HEAD/checks。后续仅经负责人批准执行提示词37，不能自动开始B1或合并27/28，不能签名/交付APK。
仍需独立expert、真实Room/SQLCipher/新device/ENOSPC/powercut、ARM容量/KDF/native恶意格式；具体批准项见36。

## MED-02B0 final synthetic corrections — 2026-10-14

PR28 https://github.com/DevenirTwilight/HRT-Log/pull/28 已OPEN，stacked到未合并PR27，均不得自动合并。
77e168e JVM35/Science PR38541101806深度通过，push Android38541015153 full build/lint成功但device失败。
API26/29/35实际Os.link EACCES，否定hardlink发布方案，报告32/35保存来源与限制。
已将候选改为O_EXCL独占创建cipher对象+完整认证/file+dirsync+DB CAS最后发布，另保留硬链接拒绝严谨测试；
不是降低认证/禁止覆盖标准，不skip这项不可行结果。原已验收POC main source仍不改。
主审发现并补强typed tombstone两步绕过和generation退回STAGING：合成迁移25+backup14+framing3=42，
scripts全部77零失败。报告32–37完整，生产未启用。
上一批PR Android38541101840剩余android job尚运行，必须等结束再推当前纠正版本，未取消CI。
本地完整Android/lint/unsigned build83763仍进行。接下来当前补丁提交/推送、最终HEAD全checks及验收记录。

## MED-02B0 design / review milestone — 2026-10-14

报告32–37完成，包括两位AI独立原报告与第二轮交叉修订；不是外部认证。
公开RFC5869/RFC9106和固定Wycheproof6向量，Python/JCA新草案转录；JVM194+POC35零失败。
SQLite22 +backup model14 +framing3=39合成测试，scripts全部74零失败。
首批9afec67 Android38539482928新增测试compile失败（非公开SDK O_DIRECTORY/Os.unlink），
改公开Os.open/fstat目录类型/Os.remove，断言不变；该CI尚在跑其余job，等结束再推纠正，未取消。
本地完整Android/lint/unsigned build正在83763 session；新协议KAT变更需最终CI重新验证。
默认Schema9/PNBAK1/PK/version/signing均不动；build26 SHA256仍0c6bd9132ab15ca7625f1da19b59c1c9450cf4cb0518b15100e30396c5ed3fa0。
下一步纠正后完整pushCI、stacked PR指向PR27分支，最终HEAD push/PR checks全绿并写结果。不合并、不交付APK。

## MED-02B0 synthetic review milestone — 2026-10-14

Scope commit0a925e8 push Android38536137541/Science38536137432 both green.
Independent AI peer reviews completed; not external certification. 22 synthetic Room9→10
SQLite tests +10 backup durability model tests +3 draft framing/HKDF tests pass locally.
POC JVM existing28 +7 new review/vector tests pass (35 total); accepted POC source unchanged.
New Android public-API link/no-replace, leaf O_NOFOLLOW and directory fsync experiments
are test-only; three API runtime evidence pending CI. Production Schema9/PNBAK1 unchanged.
Protocol/backup/policies/docs33..37 and final full CI/stacked PR still pending.

> MED-02B0启动（2026-10-14）：主线c3bf2cbc Android38527627941/Science38527627946全绿；PR27仍OPEN，1c8d385 push/PR四run全success。本轮独立feature/med02b0-security-design-20261014从已验收PR27创建，后续stacked PR，不改accepted27/生产Schema/PNBAK1/PK/版本/签名/真实附件。用户明确要求不同审查者，已安排两位独立AI代码复核与第三位合成迁移设计；不是外部专业认证。初步生产阻断：publish撤权竞态、Reader生命周期、整体rollback边界、目录fsync/DBjournal/KDF成本。需确定性证据、生产协议/Room10/新备份设计及B1提示词，全部未正式上线。

> MED-02A POC范围完成（2026-10-14）：代码6521173 push/PR Android/JVM/三个API/Science全部成功；PR API29外部protobuf解析失败经HTTP200核验仅失败job定向重跑一次成功（attempt2），未改断言/skip/版本。JVM194+28/data160/rem17/app865、API35 data66/app212（61/8原skip外部流程实际跑）、API26/29各30、Science261全部过；lint0/178既有警告，Debug/unsignedRelease/两仪器包/来源与manifest保护通过，实际DEX证明POC仅testAPK。报告30/机器证据+设计31完整，固定最终SHA/自身CI在PR27正文，最终提交检查成功后交还。PR25/26已MERGED_CI_VERIFIED于c3bf2cbc主线；PR27不自合并，MED02B迁移/协议/完整包/配额/关联政策待批准，物理ARM/独立review/生产AppLock与正式附件库未实施。无生产main/Schema/PNBAK1/PK/版本/签名/build26/真实数据改变，无正式APK交付。

> MED-02A首轮CI实况：dcb8beb Android/JVM/Science及API26/29全部success；API35设备7失败均为基于MODEL/FINGERPRINT的emulator守卫拒绝，PDF/图片未进入。修正为公开getprop实际ro.kernel.qemu==1（不删除guard、不新增skip）；另补真实ContentResolver synthetic test-provider未知SIZE/可靠pipe错误/MIME、内存采样和100对象/1000元数据/多显式链接。主线PR25/26已合并且c3bf2cbc全CI成功，本分支已纳入。新HEAD完整设备/回归仍待，不能报全绿；正式main/Schema/PNBAK1/PK/签名/版本无变。

> 集成基线已验证（2026-10-14）：PR26合并051ee22主线Android38524575273/Science38524575321全绿；PR25安全解决冲突后c97d9c6 push/PR全绿并按用户授权合并c3bf2cbc，最新主线Android38527627941三任务和Science38527627946 gate/deep全部成功。MED02独立分支纳入此最终已验证基线，R15/MED01代码与全部合同/重启脚本完整保留；POC新28 JVM通过/设备实测首轮仍待。下面“未合并/待CI”是历史记录，以本段为最新状态。

> MED-02A补充验证：JVM新增100个独立密文对象及相同内容不同显式链接保留测试；20+8=28项全部成功（36s）。添加大PDF实际采样Java/native/PSS观察器，不把算法bound当内存峰值。首轮dcb8beb API26/29/35/Android还在运行，暂不推送以避免取消；待完成后纳入验证后的R15主线并推送下一批。生产不变，PR25已合并、主线Android仍待，其余JVM/设备/Science成功。

> MED-02A原型阶段（2026-10-14）：JVM加密19/恢复模型7全部通过，无新增skip；Android POC包修正PdfDocument显式关闭后编译成功46s。独立test-only模块、ProxyFD/PdfRenderer/图片/真实synthetic Keystore与live-import force-stop脚本已实现，API26/29/35实测尚待新HEAD CI，不称可行/验收完成。scope32a1681完整CI成功；R15 PR25已合并c3bf2cbc（集成c97d9c6 push/PR全绿），主线38527627941/38527627946仍待完整结果，再纳入本分支最终回归。设计31仅待审批，不实施Room10/完整医疗备份，不交付APK/自合并/改生产数据或build26。

> MED-02A启动（2026-10-14）：MED01 PR26已合并051ee22，主线Android38524575273三任务与Science38524575321 gate/deep全部success。独立feature/med02a-attachment-poc-20261014从此已验证HEAD建立；PR25用户已授权合并，R15分支c97d9c6保留两套功能/测试解决冲突，新CI38525995026/38525995037及38525991389/38525991267正在核验，尚未合并。POC只合成数据、独立模块及测试依赖，不接生产APK；完成后需纳入R15验证主线。尚无加密/预览实测，不称完成，不改Schema/PNBAK1/PK/版本/签名/真实数据/build26。

> 2026-10-14：用户明确授权合并PR25。R15分支合入MED-01主线051ee22，保留两套四语、仓库/刷新/UI合同与外部重启脚本；仅解决共享插入位置冲突，不覆盖功能。合并前本地目标回归及新HEAD完整CI待完成；之后才合并PR25、等待主线成功，再开始MED-02A POC。无Schema/PK/版本/签名/PNBAK1变化。

> R-15代码完整验收通过：ebd02da Android38484766169/JVM/API35全部success；JVM194/data142/rem17/app813零失败（61原skip），API35 data48/app141零失败（8原skip且独立脚本实际通过），新data33/UI四语24/新PID全部过。lint0错误178既有警告，unsignedRelease及来源保护通过。报告27/机器摘要已更新；独立PR最终验收文档HEAD自身push/PR全CI成功后才交还，精确SHA/链接固定PR正文。三类历史安全限制见报告，不合并/签名/交付APK，build26不变。

> R-15回归修正：完整JVM194/data142/reminder17通过；首轮宽泛retained过滤破坏旧契约，已限定R15撤回证据；设备新重启fixture缺必填提醒参数已补齐。保留失败/修正证据，等待新HEAD完整Android/API35/Science，不取消正在运行CI，不称验收完成。见报告27。

> R-15本地目标通过：data32/UI四语24/refresh8全过、两仪器包构建成功；完整回归/API35/Science最终HEAD CI待完成。旧时段已取消/原时区证明缺失/跨库存单位保留安全限制，报告27说明后续产品决定。B4 MERGED_CI_VERIFIED，build26不变。

> B4 MERGED_CI_VERIFIED（最终主线fe879863 Android38470156117 attempt2、Science38470156282均success，deep按文档门控skip）。R-15独立分支开始审查与红测，未实现/未验收；不合并、不签名或交付APK。
> MED-01代码完整验收：b793ded Android38497285919三作业及Science38497285943均success；JVM194/data127/rem17/app839零失败（61原skip），API35 data33/app157零失败（8原条件skip经独立脚本实际通过）；医疗data18/UI四语40/时区索引7/保存3/刷新8/导航7、新PID/PNBAK1/回收及隔离全过。lint0错误，Debug/unsignedRelease/两测试包与来源清单保护通过。审计28/机器证据和B设计29齐全；B新表/加密/流式备份/预览需审批，A正常场景无阻塞。独立PR最终报告HEAD自身push/PR CI须全绿后交还，精确SHA/链接固定PR正文。R15不改；不合并/签名/交付APK或改build26/Schema/PK/格式。

> B4顺序合并完成（2026-10-13，REQ §85）：PR23 merge `d06bcce` 完整主线通过后，PR24改为默认分支、19文件差异不变，以明确新基线验证merge32345cb执行完整Android/Science全部成功，再以merge `9af38f3` 合并。其主线Android38468046764三任务、Science38468046661 gate/deep全部实际success；两个验收HEAD8749207/92f8c42保持，最终业务源码树与验收一致。详见[报告26](audits/product-quality-2026-10/26-b4-merge-and-mainline-validation.md)及机器证据。本次仅归入最新授权/交接/验收文档，交还条件仍为最后文档HEAD自身完整CI实际成功，精确SHA/链接固定PR正文，不循环追加自身SHA。下方待完成/不合并叙述属历史。无新正式签名/APK/版本/发布；build26文件不变。

> 2026-10-13：PR23和PR24已按授权顺序合并（d06bcce / 9af38f3），PR24新基线完整Android/Science成功；当前默认分支9af38f3合并后CI待完成。源码树与验收92f8c42完全相同，build26未改。

> 2026-10-13：PR23合并后主线完整CI通过；PR24已改目标为默认分支，原HEAD92f8c42和差异保持，实际新基线合并预览CI待完成。PR24仍未合并。

> 2026-10-13：用户授权顺序合并PR23→主线CI→PR24改目标及复验→合并。PR23已合入默认分支，merge `d06bcce`，Android38463928846 / Science38463928705待完成；PR24尚未合并、HEAD92f8c42不变。签名APK build26保持原样。

> B4-E代码验收完成（2026-10-13，REQ §84）：独立PR24指向B4分支，代码589d192 push/PR Android三项、Science gate/deep均实际success（38453937357/38453994837/38453937324/38453994527）。JVM194、data109/rem17/app787零失败（app61原跳过），API35 data15/app116零失败（8原条件跳过）；新历史UI24/24四语320dp/2倍字号实际通过，136条设备测量最多3个挂载包装节点，500完整25批、100正常/100VOIDED末页、追加/详情返回定位/关闭重开/失效按钮移除全部通过；B4/B2/B3与新PID加密备份/恢复原回归通过。独立报告ZIP摘要匹配GitHub digest，报告25与机器摘要已补全。最终验收提交仅再清理共享测试命名2条和固定20文案格式1条新lint告警（代码HEAD0错误181警告）；不抑制warning，不新增功能。生效条件仍为PR24最后HEAD自身完整CI完成全绿；最终SHA/链接固定PR正文，不循环追加自SHA提交。PR23保持8749207、build26文件哈希不变；Room/流水/预测/VOIDED业务/PK/加密/版本/签名未改，不合并、不签名或交付。

> 最终测试修复验收（2026-10-13）：1033a370272f4cdb6c9978cbf0b721f285464100 的Android push38439313884 / PR38439320044三作业全success；Science PR38439320016 gate/deep实际success，push38439314196 gate success/deep依普通测试改动门控skip。独立下载设备报告digest e380699f…10e76b一致：data15/15、app92零失败/8原条件跳过，UnifiedConcentrationAndroidTest六项全部实际通过（11.961s，无跳过）；B2重启/清理/恢复与VOIDED跨新PID/加密备份/安全恢复脚本全通过。直接Standard方案挂起由真实窗口主线程Recomposer及空闲同步修复，原断言与模型不变。实际本地编译3m2s，已纠正先前记录笔误。已交付build26源c725310/原证书/文件哈希不变。此次仅补验收记录；交还条件仍是PR23最终记录HEAD自身检查全部完成通过，不循环追加自身SHA。PR23不合并，无公开Release；用户手机覆盖安装未核验。

> 仪器线程修复更正（2026-10-13）：a8fd4de的Android/JVM/Science完成通过，但两组API35在app 14/92后挂起至45分钟，被作业超时自动结束（CANCELLED，不是通过，也不是SDK NOT_RUN）；data设备15项已通过，不能宣称app完成。实际日志16:43启动92项、16:44后停止推进；直接StandardTestDispatcher方案不适合该Activity启动/测试时钟，已撤换，不盲重试。改为Debug-only合成Activity可选真实生命周期主线程Recomposer，并为该组6项Android仪器测试注册其空闲状态；其余测试默认入口/时钟与全部原断言不变。本地assembleFullDebugAndroidTest已成功（3m2s），等待本HEAD最终完整CI；APK源c725310及已交付签名文件不变，不重建/重签，不涉及生产/PK/Schema/PNBAK1。

> 交付后测试框架修复（2026-10-13）：b965273记录/来源工具HEAD的push Android38429993057全success，PR38430002274在既有UnifiedConcentrationAndroidTest出现CalledFromWrongThreadException，未冒称全绿；其Science PR38430002398 gate/deep实际success、push38429993004按原门控deep skip。栈证据显示Compose1.9.3测试默认UnconfinedTestDispatcher将frame/apply恢复到Default worker；仅该Android仪器测试规则改用StandardTestDispatcher，保留全部6项原断言/模型参数/时限，不改生产APK。两仪器依赖编译及assembleFullDebugAndroidTest成功（2m27s）。已等前轮全部结束，现提交本修复并等待本HEAD完整Android/API35/Science检查；结果以PR23实时CI为准，不借用旧绿色，不盲目重试或取消正常CI。已交付APK源c725310与哈希不变，无重建/重签/新交付。

> 当前交付：DELIVERED_PRIVATE / SOURCE_CI_VERIFIED_BUILD26（2026-10-13）。用户§83明确授权的B4-D full Universal正式APK已上传仅owner Drive，独立下载回查一致；0.2.0/build26、原正式证书、Room9/PNBAK1/PK不变。构建源c725310完整Android与PR Science验收通过；来源/签名/16KB/权限/版本检查及账本记录完成，见[机器交付记录](delivery/b4d-build26-2026-10-13.json)与[报告24](audits/product-quality-2026-10/24-b4-stock-consistency.md)。用户安装与物理ARM覆盖升级未验证；PR23保持OPEN，未合并默认分支或公开发布。

> 当前B4-D交付：OFFICIAL_APK_AUTHORIZED_BUILD_PENDING（2026-10-13，REQ §83）。功能461574b完整CI已通过；用户明确授权正式签名APK，必要将versionCode升26，保留0.2.0与原正式证书。正在构建/验收私有交付，不提前记账或冒称已交付；PR23仍OPEN、未合并。

> B4-D：READY_FOR_OWNER_REVIEW（须最终HEAD检查全绿生效）（2026-10-13）。代码HEAD `6061ab3f0ed2c8843117db3066dcd5b60edde450` 的push/PR Android三任务、Science gate/deep全success；JVM194、data109/rem17/app763零失败（app61原跳过），API35 data15/app92零失败（app8条件跳过，新作废重启两方法随后独立通过），lint0错误178警告、Debug/unsigned Release及来源检查通过。无流水/ADJUST-only可逻辑VOIDED，正常UI/库存/共享预测/提醒/扣减完全排除；高级确认恢复重新检查一致性。实例4mg→2mg、普通2盒→1盒，ADJUST完整保留。C含CONSUME/REVERSE仍安全拒绝，方案见[报告24](audits/product-quality-2026-10/24-b4-stock-consistency.md)。代码及首轮验收文档HEAD完整CI通过；当前HEAD的绿色状态以PR23实时检查为准，必须全部完成后交还验收；PR23保持OPEN，不合并/正式签名/交付。A/B无需新增决定，C下一阶段另审。

> B4当前：在既有 `feature/b4-stock-consistency-20261013` / PR23继续，B4-A/B/C不重复实施；产品负责人已正式批准VOIDED，原ADJUST待决定项已解决。以下为历史基线，当前以本段及报告24末节为准。

> 当前（2026-10-13）：B3 PR #22 head 见审计23“最终 CI”；B3-A 返回键回日历、B3-B 日历 TalkBack 语义（四语）、B3-C 旋转/重建保留选中日；设备测试 CalendarIntakeAndroidTest 2/2 在 API35 模拟器实际通过（取消/真实返回键不写记录，保存一次后返回不重复）。状态 READY_FOR_OWNER_REVIEW（以最终提交 CI 全绿为前提），不合并、不签名、不交付；物理 ARM 与真人 TalkBack 为 NOT_RUN；320dp 日期格 34dp 宽按决定不阻断。PR #21 已于 `d5dc9e5` 合并并 MERGED_CI_VERIFIED。下一阶段 B4 库存一致性，未开始。

> 2026-10-13 B2 PR #20 经产品负责人批准，已于 `9c3b5d6` 合入默认分支（未签名、未交付）。科研守卫精简 PR #21：门控自保护修复 `968783c` CI 全绿（Android 38381299493/38381303849，Science 38381299642/38381303857），依 §79 授权以 merge commit 合并；合并后默认分支 CI 通过即为 MERGED_CI_VERIFIED，随后停止扩展科研守卫，下一阶段 B3 导航与无障碍；此前该分支已合入含 B2 的默认分支，状态见[工程报告22](audits/product-quality-2026-10/22-science-validation-simplification.md)；签名/交付须另授权。

# HRT Log 当前状态（单一事实入口）

> 默认分支应用状态：**MERGED_CI_VERIFIED**（2026-10-12）。历史过程见 HANDOFF，需求原文见 REQUIREMENTS；本轮完整证据见[审计20](audits/product-quality-2026-10/20-pr19-merge-and-mainline-validation.md)。应用验收基线如下，随后文档提交的最新 HEAD 以 Git 为准。

## 已合并的应用基准

- 默认分支：`claude/new-session-1959qb`。
- PR #19：https://github.com/DevenirTwilight/HRT-Log/pull/19；merge commit `4c88c1c7ecaa8ede67d7a0e432dcfbb7262d1d8f`，2026-10-12 17:08:10 UTC。
- 父提交：`f53726ff6fa52fc5c78806a1576fdb80fcf29987` + `30b802454923d557d80c5e6bdedf6eec96e583be`。源分支保留；没有 squash/rebase。
- B0/B1、D-1/D-2 已进入默认分支；本轮没有重新实施或额外应用代码修复。
- 版本仍 0.2.0/build25、applicationId `net.plainnotes.app`、Room9；备份格式和正式签名政策不变。

## 主线验收

合并提交的 push CI [38282204769](https://github.com/DevenirTwilight/HRT-Log/actions/runs/38282204769)：jvm/android/device-tests 全 success；独立标定回放 [38282204802](https://github.com/DevenirTwilight/HRT-Log/actions/runs/38282204802) reference success。JVM194、科研/守卫302、data72、reminder14、app510（61条件性跳过）、PeriodStability5/5，零失败；API35 data14/app71（两重启方法主套件跳过后分别独立通过）。lint0错误174警告。unsigned Release 来源4c88c1c/git/clean，无INTERNET。

## 已核实的行为与边界

- B0：历史域164文件及5个结果回放；当前实时锁定43原文件+2研究资源+10新增研究依赖；浓度显示变更受审批链约束，普通产品代码正常开发。
- B1：关于页完整源码SHA/提交时间/来源可复制；Release限制为干净git来源。
- D-1：M2条件性文献情景曲线、情景点读、实测化验单独显示、技术依据折叠；没有个人当前浓度大数字。
- D-2：新PDF/新化验不再静默输出Legacy舌下E2估算；旧calculator1/2、化验、Visit Pack及记录不重写。
- §60审批OWNER_CONFIRMED：2026-10-12事后确认；原2026-10-11代码与晚23分钟书面记录保留（REQUIREMENTS §72）。
- P2-C HOLD；PR #18个人绝对pg/mL能力仍false；条件性情景展示没有消除科学证据张力。

## 发行与后续

本轮没有正式签名、交付APK、公开Release或标签更新。公开v0.2.0和既有私下交付是历史产物；新主线源码不是已安装到手机的版本。交付账本不新增条目；下次实际交付需独立授权、原签名和versionCode至少26。

真实ARM覆盖升级/数据保留/提醒/锁与伪装/M2/PDF/备份验收NOT_RUN。B4/B5、Lab Panel/医疗附件及旧公开包许可处置仍另案。B2 已获独立授权并在专用分支实施，见下文。

其他研究PR未关闭或删除；#18依赖#17，合并#19不等于合并#17/#18。历史详情见HANDOFF；本轮SHA/CI与核验见审计20。

## B2 开发分支（2026-10-12）

`feature/b2-privacy-reliability-20261012` 从最新默认分支 `1e584285cb1e6f7a4b09a3e20585418d35a28735` 创建。B2-A COMPLETE：`ed5682e7a5fd6cc0540e42ac15f4dddca23210f9`，CI38291254654三任务与38291254617 reference全绿。本地完整回归/构建也成功。B COMPLETE（5f839e9修复后CI38301348940/38301348930全绿）；C COMPLETE（8e97e7a，38362778379/38362778161全绿）、D COMPLETE（b6cad04，38366443689/38366443699全绿）；各批前一 CI 全绿后推进。四批代码已全部完成并通过各自CI，最终验收文档和PR供审阅。见[审计21](audits/product-quality-2026-10/21-b2-privacy-reliability.md)。无默认分支合并、签名、交付、版本/Schema/加密格式/PK 变更。
