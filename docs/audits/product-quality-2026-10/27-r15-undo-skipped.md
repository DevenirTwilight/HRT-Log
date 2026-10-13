# 27 — R-15 撤销误跳过（实施中）

2026-10-13，REQ §86；基线fe8798634ccd0d92d2913f384abfcfff94d32274。B4最后Android38470156117 attempt2三作业success，Science38470156282 gate success/deep按纯文档门控skip，B4 MERGED_CI_VERIFIED。首轮既有浓度UI框架跨线程异常与有界单项重跑保留，未宣称永久修复。

## 已审查的数据链路

HistoryScreen.RecordRow明确禁用SKIPPED点击。主动跳过同时保存在slot_override.skipped和dose_record(SKIPPED)。override现有事务能清除标记、软删除SKIPPED并reconcile；不能只删除任意记录，也不能直接变成已服药。Timeline以有效记录快照为先，其后使用retained/原rule版本和override；AUTO_MISSED投影为UNCONFIRMED，APP MISSED是用户确认的漏服，只有ON_TIME/LATE具有真实服药与SupplyLedger消耗。编辑/删除真实服药使用REVERSE/CONSUME，不应用于跳过纠错。计划改变闭合旧版本，对已经发生/覆盖的旧Slot保留retained；未来已被新计划取消的旧Slot不得凭快照拼造。

## 事务方案

以历史页选中的完整记录作为预期版本。事务内重新读取，拒绝删除/改修/状态变化、实际服药/漏服以及没有真实主动skip override的记录；定位原rule/key/zone及可靠retained，校验有效时刻/剂量/快照相符。只撤回skipped，保留改期和剂量覆盖；复用现有override写入与软删除机制。原始跳过行及revision保留，不物理删除。必要时以已有Timeline投影为目标Slot生成AUTO_MISSED，不写其他日期，不合成服药事实。整个事务不调用库存消费/回补逻辑。新跳过保存实际原Slot信息，避免改期/时区后丢失定位证据。

无法定位、旧future Slot被计划改动取消、旧改期缺少可靠原Slot/时区证据等情况保守拒绝；不使用当前计划代替历史。对应子项不硬做，本轮正常可定位记录继续完成。

历史页使用原有行菜单，SKIPPED只有独立撤销入口；明确二次确认，四语、小屏大字体可滚动且按钮适配，TalkBack可发现。正常服药编辑/删除/补录不变。ViewModel通过原ReminderCoordinator.mutate及共享refresh即时更新历史/日历/库存预测/提醒，不增加算法。

## 当前红测证据

仓库新共享17项在真实Room/SchemaGuards下执行（SDK28 JVM）：17失败、0跳过，耗时17.755s，Gradle4m13s。临时未实现seam使16项UnsupportedOperationException，回滚故障注入项AssertionError；明确是新入口实现前的红测，不把编译失败当红测。临时seam不会提交为生产实现。历史页四语320dp/2x的旧界面不可点击红测仍在运行；后续结果必须实际完成才填入。

完整验收、失败/修复、文件清单、最终HEAD/CI及PR待实施完成后记录；当前不是验收完成报告。不合并、不签名/交付APK，不改Room/PK/备份/版本或build26。

## 首版实现与本地目标验证

原UI定位器先失败在懒节点未滚动到（4/4，不算业务红测）；改为scrollToIndex(3)后保留同一启用断言，四语4/4真实失败在[Disabled]（15.354s，组合构建5m22s）。首版仓库25/25通过后，新增“撤销→再改计划”的幽灵未来Slot红测1/1失败（59s）；Timeline仅让无实际记录的retained遵守原rule有效期，真实记录仍优先，避免丢失提前服药事实。再补旧改期retained无来源证明、跨单位库存两项红测，2/2实际失败（2m11s），随后安全限制通过。

新APP跳过仅在已有config_snapshot JSON内附加skip_original_slot_v1（key、原UTC、原zone三项），临床字段不改、快照版本仍2。旧retained可能来自后来按当前时区重建，不能无条件当原始证据；新记录优先采用首次实际Slot证据，无改期的旧记录可由自己的时刻/zone和原版本解析，旧elapsed key可验证绝对anchor。旧wall改期没有来源证明时，即使有derived retained也保守拒绝。计划编辑不覆盖已有原Slot保留信息。没有新增表/列、Room仍9，PNBAK1封装/表集合/列名/加密参数均未改；原JSON字段允许可选证据，旧备份无需迁移。

当前本地：共享仓库32/32（21.210s）、原生四语320dp/2x UI24/24（39.240s）、DataRefresh8/8（8.996s），全部零失败/零跳过。UI测试真实宽度320dp、Button role及本地化onClickLabel、滚动确认说明、确认/取消按钮可点击可见；未宣称真人TalkBack或物理ARM验收。DataRefresh新增两项证明日历/历史/统计/共享预测同步、可用库存不变但计划需求恢复一项、DUE提醒只恢复一条、重复陈旧操作不重建缓存。

第一次Android测试包编译缺android.test.core的ApplicationProvider；只补对应androidTest依赖，未减少测试或修改断言。随后core/data及full app两份仪器包实际构建成功（2m18s）。独立新进程脚本仅接受emulator，只使用专用synthetic数据库/文件，不pm clear任何应用数据；同一个仪器方法在主套件运行完整两阶段，无新增Assume/skip，另外由外部force-stop执行prepare/verify并验证不同PID、PNBAK1与VOIDED及后续真实用量只扣一次。真实API35结果仍待CI。

为匹配日历已使用的原规则快照，补录确认页同样显示保存版本的药物标签/剂量单位，并保留当前旋转侧位等UI设置；正常同版本编辑/删除/补录不变。仓库从不调用SupplyLedger消费/回补；只有随后用户显式正常补录才产生真实消耗。ViewModel用原mutate/refresh，增加当前选择及in-flight保护，防止重复触发提醒缓存副作用。

## 本轮保守限制与待决定项

- 原Slot不再属于已闭合旧版本（如未来跳过对应时段已被新计划取消）：不通过扩大版本有效期或拼造Slot恢复。若需要只撤回标记而不恢复时段，或恢复旧计划，需要产品负责人另行明确行为；本轮该子项保留限制。
- 旧wall改期缺原UTC/zone来源证明：不根据当前时区猜测；需要可信历史证据才能安全恢复，不是数据库不可变约束阻止正常纠错。
- 历史剂量单位与当前库存单位不同：明确提示暂不能安全撤销，不向共享预测注入无法比较的用量。跨单位历史计划与库存如何关联/展示需进一步决定；本轮不改库存/PK或做隐式换算。

这些子项不影响可靠、同单位的当天/过去/改计划/覆盖剂量/改期的撤销；真实已服药、确认漏服、自动未确认漏服及陈旧版本一律拒绝。尚未完成全量回归、API35和最终HEAD CI，不称最终验收完成。
