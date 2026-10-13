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
