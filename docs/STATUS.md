> B4-D：IMPLEMENTED_LOCAL_VERIFIED_CI_PENDING（2026-10-13）。无流水/ADJUST-only逻辑VOIDED、默认完全隐藏、高级确认恢复已实现；本地全量通过，真实误添加库存4mg→2mg、包装2→1。最终HEAD的Android/Science及API35等待执行，不能套用B4-C状态。C含CONSUME/REVERSE仍安全拒绝，后续补偿设计见报告24。PR23不合并/正式签名/发包。

> B4-D实施中（2026-10-13）：用户批准VOIDED，起点PR23/4afa8b0；无流水与ADJUST-only逻辑作废、原状态安全恢复、默认隐藏与保护触发器已编码。数据106/提醒17及历史164/5/科研261本地通过（后续新增边界测试及完整最终CI待执行）；四语UI/实际API35重启待验收。真实CONSUME/REVERSE仍拒绝直接作废，补偿设计见报告24。不是COMPLETE，不合并/签名/发包。

> 当前（2026-10-13）：PR #22（B3）已于 `bab53d3` 合并，主线 CI 全绿。B4（库存一致性与库存纠错）在 `feature/b4-stock-consistency-20261013`：B4-A 统一逐次扣减预测，B4-B 库存页、日历、提醒接入，B4-C 措辞、核对库存、撤销误添加、包装管理。验收见[审计24](audits/product-quality-2026-10/24-b4-stock-consistency.md)。待产品负责人决定：已有调整流水的误添加包装如何处理。PR 等待审阅，不合并，不签名，不交付。

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
