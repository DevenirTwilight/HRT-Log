# build13：导入时间线与历史治疗事件验收

2026-10-08；源码b44588f，full/build13/schema6。独立设计见[设计](design/imported-timeline-history-p1.md)。

## 修复与实现

`HrtTrackerWriter.kt`/`DataTransfer.kt`原本将导入用药写入dose_record，不猜历史schedule；`NotesViewModel.refresh`已读取全部records。但`PeriodTimeline.kt`只展示regimen_version及重要事件，因此导入实际用药不出现。本批新增`ImportedHistory.kt`只读聚合，`LongitudinalScreen.kt`按已有时期或方案未知区显示覆盖日期/数量；未来原记录独立Upcoming。taken_utc优先、删除记录排除、无可用时刻不编造日期。缺失route/amount不套当前配置。导入药物的冻结上下文、剂量范围和各药物记录数可查看，原状态保留在History，不把HT无计划的ON_TIME存储类别写成准时结论。

按源ID打开History的all范围，并排除不属于该摘要的记录。该筛选是本次导航的临时状态，Activity/进程重建后可重新从摘要打开，不宣称永久保存查询。日记/每日状态仍在原页，不违背此前Timeline不逐条列正常dose的范围。APP实际记录未新增逐条Timeline卡；HRT Tracker化验及Trans Memo预约继续读取原事件。

新增PAUSED/STOPPED/RESUMED里程碑类型，日期/可选原因由用户填写，不从实际记录推断。可保存/修改/删除、未来显示和四语PDF标签；保存控制器沿用既有可靠回执。SchemaGuards和repository共享类型allowlist、onOpen重新安装旧trigger、restore复用同一语义检查。未增加表/字段或schema7；旧schema1–6备份可恢复。含新类型的备份不承诺恢复到build12旧应用。

## 通过的验证

- 合成HT JSON→真实数据库→Timeline→原记录/化验；再导入幂等、加密恢复后相同投影。
- 两类导入、早于首个方案、无方案、提醒合并后同一摘要、未来、删除、无时刻、缺实际量/route，不制造Started/频率/停药。
- 生命周期记录加密备份往返、旧trigger更新、非法kind拒绝，药物active/规则/原regimen均不改变。
- UI import/删除触发records依赖更新；详情只用冻结名称；历史source真实滚动并验证准确ID；无计划时刻不出现准时文案。中文native graphics合成Timeline截图已查看，摘要可见且无裁切。
- 本机完整命令3m14s成功；普通回归261登记（260通过/1既有PDF跳过），额外12 opt-in按钮审计跳过；0失败。full lint0错误98警告；debug/release及两个原生测试APK通过。没有schema/PLAN或V1 RegimenHistory/Context1/VisitPack1/PK改动。
- [CI37705351828](https://github.com/DevenirTwilight/HRT-Log/actions/runs/37705351828)三任务success。API35 data11通过；app26通过，另2重启项在独立进程各通过，含导入详情、简单模式隐藏冻结药名和旧大字体长Timeline。
- 正式原证书v2/v3，net.plainnotes.app/build13/nondebug/no INTERNET，ZIP与8个ELF库16KB对齐；实际下载23,362,779 bytes、SHA256 `547a29cca9dca8e4c243a48e2569088c04f2d84d2097f4e130cf7e2f45698f29`与本地相同。临时凭据清理、原私有备份未改。

## 明确边界

用户填的日期级暂停/停用/恢复是历史事实，不是药物级状态机；不自动修改今天的提醒或回写过去regimen。完整停用状态、精确边界/药物关联和稳定slot身份尚未实现。导入没有可信计划时，摘要不等于历史治疗方案；仍明确未知。用户对build12的TalkBack反馈已记，不冒充本批新增界面的人工TalkBack或用户真实升级已验收。没有真实健康数据、医疗判断、公开Release/标签变更。

过程失败已修正并记录：测试Application不应启动正式receiver，源记录行需真正滚动后断言，原生测试需显式app.R；最后本地构建会话中断后串行重跑完整成功。不能把初轮或中断构建当作最终交付依据。
