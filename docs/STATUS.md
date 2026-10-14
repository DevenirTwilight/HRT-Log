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
