# 27 — R-15 撤销误跳过（独立验收）

> 代码完整回归已通过；最终结果见“完整代码验收结果”。前面的红测、首轮失败及待测描述保留为实施过程证据；精确最终文档HEAD和其自身push/PR CI在交付PR正文及独立最终验收文件固定。

2026-10-13，REQ §86；基线fe8798634ccd0d92d2913f384abfcfff94d32274。B4最后Android38470156117 attempt2三作业success，Science38470156282 gate success/deep按纯文档门控skip，B4 MERGED_CI_VERIFIED。首轮既有浓度UI框架跨线程异常与有界单项重跑保留，未宣称永久修复。

## 已审查的数据链路

HistoryScreen.RecordRow明确禁用SKIPPED点击。主动跳过同时保存在slot_override.skipped和dose_record(SKIPPED)。override现有事务能清除标记、软删除SKIPPED并reconcile；不能只删除任意记录，也不能直接变成已服药。Timeline以有效记录快照为先，其后使用retained/原rule版本和override；AUTO_MISSED投影为UNCONFIRMED，APP MISSED是用户确认的漏服，只有ON_TIME/LATE具有真实服药与SupplyLedger消耗。编辑/删除真实服药使用REVERSE/CONSUME，不应用于跳过纠错。计划改变闭合旧版本，对已经发生/覆盖的旧Slot保留retained；未来已被新计划取消的旧Slot不得凭快照拼造。

## 事务方案

以历史页选中的完整记录作为预期版本。事务内重新读取，拒绝删除/改修/状态变化、实际服药/漏服以及没有真实主动skip override的记录；定位原rule/key/zone及可靠retained，校验有效时刻/剂量/快照相符。只撤回skipped，保留改期和剂量覆盖；复用现有override写入与软删除机制。原始跳过行及revision保留，不物理删除。必要时以已有Timeline投影为目标Slot生成AUTO_MISSED，不写其他日期，不合成服药事实。整个事务不调用库存消费/回补逻辑。新跳过保存实际原Slot信息，避免改期/时区后丢失定位证据。

无法定位、旧future Slot被计划改动取消、旧改期缺少可靠原Slot/时区证据等情况保守拒绝；不使用当前计划代替历史。对应子项不硬做，本轮正常可定位记录继续完成。

历史页使用原有行菜单，SKIPPED只有独立撤销入口；明确二次确认，四语、小屏大字体可滚动且按钮适配，TalkBack可发现。正常服药编辑/删除/补录不变。ViewModel通过原ReminderCoordinator.mutate及共享refresh即时更新历史/日历/库存预测/提醒，不增加算法。

## 当前红测证据

仓库新共享17项在真实Room/SchemaGuards下执行（SDK28 JVM）：17失败、0跳过，耗时17.755s，Gradle4m13s。临时未实现seam使16项UnsupportedOperationException，回滚故障注入项AssertionError；明确是新入口实现前的红测，不把编译失败当红测。临时seam不会提交为生产实现。历史页四语320dp/2x真实不可点击红测随后完成，结果如下。

完整验收、失败/修复、文件清单、最终HEAD/CI及PR待实施完成后记录；当前不是验收完成报告。不合并、不签名/交付APK，不改Room/PK/备份/版本或build26。

## 首版实现与本地目标验证

原UI定位器先失败在懒节点未滚动到（4/4，不算业务红测）；改为scrollToIndex(3)后保留同一启用断言，四语4/4真实失败在[Disabled]（15.354s，组合构建5m22s）。首版仓库25/25通过后，新增“撤销→再改计划”的幽灵未来Slot红测1/1失败（59s）；Timeline仅让经过R15撤销且有来源证据的无实际记录retained遵守原rule有效期，其他历史retained与真实记录仍优先，避免丢失提前服药事实。再补旧改期retained无来源证明、跨单位库存两项红测，2/2实际失败（2m11s），随后安全限制通过。

新APP跳过仅在已有config_snapshot JSON内附加skip_original_slot_v1（key、原UTC、原zone三项），临床字段不改、快照版本仍2。旧retained可能来自后来按当前时区重建，不能无条件当原始证据；新记录优先采用首次实际Slot证据，无改期的旧记录可由自己的时刻/zone和原版本解析，旧elapsed key可验证绝对anchor。旧wall改期没有来源证明时，即使有derived retained也保守拒绝。计划编辑不覆盖已有原Slot保留信息。没有新增表/列、Room仍9，PNBAK1封装/表集合/列名/加密参数均未改；原JSON字段允许可选证据，旧备份无需迁移。

当前本地：共享仓库32/32（21.210s）、原生四语320dp/2x UI24/24（39.240s）、DataRefresh8/8（8.996s），全部零失败/零跳过。UI测试真实宽度320dp、Button role及本地化onClickLabel、滚动确认说明、确认/取消按钮可点击可见；未宣称真人TalkBack或物理ARM验收。DataRefresh新增两项证明日历/历史/统计/共享预测同步、可用库存不变但计划需求恢复一项、DUE提醒只恢复一条、重复陈旧操作不重建缓存。

第一次Android测试包编译缺android.test.core的ApplicationProvider；只补对应androidTest依赖，未减少测试或修改断言。随后core/data及full app两份仪器包实际构建成功（2m18s）。独立新进程脚本仅接受emulator，只使用专用synthetic数据库/文件，不pm clear任何应用数据；同一个仪器方法在主套件运行完整两阶段，无新增Assume/skip，另外由外部force-stop执行prepare/verify并验证不同PID、PNBAK1与VOIDED及后续真实用量只扣一次。真实API35结果仍待CI。

为匹配日历已使用的原规则快照，补录确认页同样显示保存版本的药物标签/剂量单位，并保留当前旋转侧位等UI设置；正常同版本编辑/删除/补录不变。仓库从不调用SupplyLedger消费/回补；只有随后用户显式正常补录才产生真实消耗。ViewModel用原mutate/refresh，增加当前选择及in-flight保护，防止重复触发提醒缓存副作用。

## 本轮保守限制与待决定项

- 原Slot不再属于已闭合旧版本（如未来跳过对应时段已被新计划取消）：不通过扩大版本有效期或拼造Slot恢复。若需要只撤回标记而不恢复时段，或恢复旧计划，需要产品负责人另行明确行为；本轮该子项保留限制。
- 旧wall改期缺原UTC/zone来源证明：不根据当前时区猜测；需要可信历史证据才能安全恢复，不是数据库不可变约束阻止正常纠错。
- 历史剂量单位与当前库存单位不同：明确提示暂不能安全撤销，不向共享预测注入无法比较的用量。跨单位历史计划与库存如何关联/展示需进一步决定；本轮不改库存/PK或做隐式换算。

这些子项不影响可靠、同单位的当天/过去/改计划/覆盖剂量/改期的撤销；真实已服药、确认漏服、自动未确认漏服及陈旧版本一律拒绝。尚未完成全量回归、API35和最终HEAD CI，不称最终验收完成。

## 完整回归首轮失败与修正

代码8b2baa1的Android38483241840首轮确实失败，未重跑已知失败源码。JVM保留原ScheduleTest断言发现宽泛限制retained改变了旧历史契约（预期2、实际1）。修正限定为有R15撤回证据的Slot，其他既有retained规则完全保持；已可靠验证的旧记录在纠错软删除revision中附加同一可选来源证据，避免随后改计划再产生幽灵需求。新增第33项专门验证这一旧记录路径。完整JVM194项已通过；完整数据142项、提醒17项已通过，完整app/lint/构建继续执行。

API35首轮新重启合成fixture缺少必填soon/late参数，在saveMedication前置规则校验失败，并未执行撤销。补齐30/120分钟参数，不放松业务规则、不删断言。该设备报告artifact11858503725，SHA256 37b07873a6d46365f591cac8d7492914a6e6c30c7233ea498c9fcbfc73043a0e。已等待该轮全部结束再推送修正，未取消运行作业。Science38483241759 gate/deep实际通过；修正后最终HEAD全部CI仍待完成。

## 完整代码验收结果

代码提交 `ebd02da91d96cfbdebe1e7de2fd7b651cfdaaec7`：

| 检查 | 实际结果 |
| --- | --- |
| 完整 JVM | domain58、PK124、importer12，共194，零失败/零跳过 |
| 完整 Android 原生单元回归 | data142、reminder17、app813，零失败；app61项原条件跳过，未新增skip |
| R15仓库合同 | SDK28和API35同一33项，全部执行通过，包括两处事务故障回滚 |
| 四语历史UI | 简中/繁中/英/法 ×6，共24；SDK35原生和API35均零失败/零跳过，实际320dp/2倍字号 |
| API35总套件 | data48/0失败/0跳过；app141/0失败/8原条件跳过 |
| 新进程纠错 | 主套件方法1/1通过；外部prepare和force-stop后verify各1/1，通过不同PID断言 |
| lint | 零错误、178项既有警告，不新增抑制 |
| 构建 | fullDebug、unsigned fullRelease、data/app仪器包通过；CI来源/干净检出/清单无INTERNET/账本保护通过 |
| Science | 38484766307 gate success，按本推送路径deep skip；首版38483241759 gate/deep实际success；最终PR deep另以自身CI核验 |

Android：https://github.com/DevenirTwilight/HRT-Log/actions/runs/38484766169 。Science：https://github.com/DevenirTwilight/HRT-Log/actions/runs/38484766307 。首版深度验证：https://github.com/DevenirTwilight/HRT-Log/actions/runs/38483241759 。三个记录均保留实际head，不把旧绿色冒充最终HEAD。验收文档提交的精确HEAD及push/PR CI在交付PR正文和独立最终验收文件中固定，避免追加自身SHA循环；必须全部完成才交还。

独立下载设备artifact11859831404，SHA256 `69ccd4cbf4ba5f7107472ecae315136b829143a8369d2681c7c34f519c5178c9`；构建报告artifact11860147057，SHA256 `21433a38342736f5ed8900f0ddd22b7a34d76f23af3ba0eec402b71aa50e0ba5`，均与GitHub摘要一致。直接读取HTML确认data48/app141、R15 data33/UI24/restart1及零失败。Gradle控制台Finished149会重复计入8个skip结束事件，报告真实用例总数为141，未将其计作额外执行。

本地长构建启动于8b2baa1修正中的工作树，在Release来源保护处正确拒绝dirty tree（已通过的测试/lint不受影响）；未绕过保护。提交修正后的干净ebd02da单独重新构建四份包通过。CI本身始终在对应干净检出构建并验证来源。

## 需求逐项验收

| 用户验收场景 | 证据/结果 |
| --- | --- |
| 当天误跳过 | 恢复PENDING；到时间按SOON/OVERDUE投影，不自动已服药 |
| 过去日期 | 原SKIPPED软删除并递增revision；仅目标时段生成AUTO_MISSED，投影UNCONFIRMED |
| 计划版本改变 | 保存版本剂量/时段不被当前版本覆盖；原时区证据保留；未来已取消旧时段保守拒绝 |
| 覆盖剂量、改期 | 两项覆盖保留；可靠原始证据无需derived retained即可恢复 |
| 与普通漏服区分 | APP MISSED及AUTO_MISSED均没有撤销跳过入口且仓库拒绝 |
| 真实已服药 | 仓库拒绝，无CONSUME/REVERSE修改，正常编辑/删除保持 |
| 重复及陈旧操作 | 完整选中快照+当前override复核；一次revision，重复拒绝，修订/后来真实服药拒绝 |
| 取消 | 四语UI取消0提交，数据库事务未被调用 |
| 失败回滚 | 软删除写入故障及AUTO_MISSED插入故障均完整回滚记录/override/retained/库存 |
| 历史与日历刷新 | DataRefresh新增2项验证共同投影、统计及共享预测即时更新 |
| 库存与提醒 | 可用20mg→20mg，真实消耗0；恢复一项未来计划需求和一条当前缓存代DUE提醒，重复不重建缓存 |
| 重新启动/备份 | 文件数据库重开、PNBAK1 round-trip、实际新PID后纠错状态保持 |
| VOIDED | ADJUST-only已作废包装及其流水保持原样，撤销不复活；随后正常记录3mg只扣一次，真实库存20→17mg |
| 四语/320dp/2x/无障碍 | 每语实际6项，确认内容滚动、两按钮可见可点击、Button role和本地化OnClickLabel；真人TalkBack/物理ARM未运行 |

B2真实密码检查点外部强杀、明文残留清理/无待恢复来源/加密源保留、私密空间认证；B3真实返回与日历；B4统一预测、VOIDED新PID与加密备份/安全恢复、B4-E136个视口测量最多3个挂载包装节点均在本设备作业实际通过。没有新增Assume、skip、放松断言、取消正常CI、清除真实应用数据或修改PK模型。

## 修复前后与交付边界

之前：历史SKIPPED不可点击，无法撤销误跳过。之后：点击该行→独立撤销菜单→明确说明→确认，可靠记录按原计划和真实当前时间恢复；如果实际已经服药，随后通过正常明确记录流程补充，才产生真实一次消耗。

不改Room9、表/列、SupplyLedger保护、正式PNBAK1封装/加密参数、PK内核、versionCode26或签名配置；新增可选纠错证据只位于既有config_snapshot JSON。已交付build26 SHA256 `0c6bd9132ab15ca7625f1da19b59c1c9450cf4cb0518b15100e30396c5ed3fa0`未改。本轮不合并、不签名、上传或交付APK。正常可可靠定位且单位一致的场景不需进一步决定；前述三个历史边界保留安全限制并列出后续决定，未将它们冒称全部支持。

## 下一批候选（仅提议，不实施）

1. R-08（路线图P1）：按失效通知/按需刷新替代30s全量计算；先取得真实设备基准，当前NotesViewModel仍有delay(30000)刷新。
2. R-16（P2）：历史投影失败显示可理解提示；当前AppShell仍有getOrDefault(emptyMap())，可能静默丢失标签。
3. R-26（P3）：复诊问题删除进回收站；当前deleteVisitQuestion仍直接DAO删除，符合长期可发现纠错原则。

本轮未开始上述任何第二功能项目。

## 新增和修改文件

- `.github/workflows/android.yml`
- `app/build.gradle.kts`
- `app/src/androidTest/java/net/plainnotes/app/stock/UndoSkippedHistoryAndroidTest.kt`
- `app/src/androidTest/java/net/plainnotes/app/stock/UndoSkippedRestartAndroidTest.kt`
- `app/src/historyTest/java/net/plainnotes/app/history/UndoSkippedUiContract.kt`
- `app/src/main/java/net/plainnotes/app/NotesViewModel.kt`
- `app/src/main/java/net/plainnotes/app/ui/AppShell.kt`
- `app/src/main/java/net/plainnotes/app/ui/HistoryScreen.kt`
- `app/src/main/res/values-b+zh+Hant/strings.xml`
- `app/src/main/res/values-fr/strings.xml`
- `app/src/main/res/values-zh/strings.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/test/java/net/plainnotes/app/DataRefreshTest.kt`
- `app/src/test/java/net/plainnotes/app/UndoSkippedHistoryUiTest.kt`
- `core/data/build.gradle.kts`
- `core/data/src/androidTest/java/net/plainnotes/app/data/UndoSkippedAndroidTest.kt`
- `core/data/src/historyTest/java/net/plainnotes/app/data/UndoSkippedContract.kt`
- `core/data/src/main/java/net/plainnotes/app/data/NotesRepository.kt`
- `core/data/src/main/java/net/plainnotes/app/data/SkipSlotEvidence.kt`
- `core/data/src/main/java/net/plainnotes/app/data/SkipUndoConflict.kt`
- `core/data/src/test/java/net/plainnotes/app/data/UndoSkippedTest.kt`
- `core/domain/src/main/kotlin/net/plainnotes/app/domain/ScheduleEngine.kt`
- `docs/HANDOFF.md`
- `docs/REQUIREMENTS.md`
- `docs/STATUS.md`
- `docs/audits/product-quality-2026-10/06-roadmap-prioritized.md`
- `docs/audits/product-quality-2026-10/27-r15-undo-skipped.md`
- `docs/audits/product-quality-2026-10/27-r15-validation-evidence.json`
- `docs/audits/product-quality-2026-10/README.md`
- `scripts/run_undo_skipped_restart.py`
