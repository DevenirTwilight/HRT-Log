> B4-E开始（2026-10-13，REQ §84）：独立feature/b4e-package-history-20261013从PR23已验收8749207创建，原PR23与build26不动。PR23最新四组CI38445246198/38445246096/38445236468/38445236506均完成success，Science push deep依原门控skip，PR deep实际通过。目标仅PackagesDialog有界LazyColumn、四组20条渐进展示、VOIDED高级分页、稳定排序/滚动与必要测试；不碰库存业务/Room/流水/PK/备份/版本/签名。开发与验收见报告25；后续stacked PR指向B4分支，未创建/未合并，不签名或交付。

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
