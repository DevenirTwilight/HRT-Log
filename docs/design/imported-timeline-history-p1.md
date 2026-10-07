# 导入历史与用户治疗事件（build13）

## 问题与证据

HrtTrackerWriter/TransMemoWriter仅写实际dose_record（以及导出的化验/预约等），缺少可信历史schedule时不会创建regimen_version。PeriodTimelineProjection仅取regimen_version和LAB/REVIEW/MILESTONE/APPOINTMENT，新Timeline因此完全不显示这些导入用药。ViewModel已读取全部records并在导入后refresh，不是90天截断或漏刷新。

## 独立设计

在Timeline时期下面增加“导入用药历史”摘要，按已有时期归属与导入来源分组，列覆盖日期/记录数；没有方案的实际记录显示于“方案上下文未知”区。详情按冻结药物快照和已知剂量汇总，源ID保留以供查证，完整版History入口读取原记录。执行日期优先taken_utc，确认漏服/跳过仅使用scheduled_utc；删除记录不展示，未来记录独立Upcoming。不会将药物当前配置回套旧记录，不从密集服药推断频率或创建时期/Started HRT，不新增逐条dose主卡。每日状态/日记依旧在各自页面，导入不能绕过此前Timeline范围决定。

已有regimen的分配按精确时刻，不修改原快照，摘要不会因提醒变更切分。UI remember依赖records，导入/删除/编辑后的快照刷新会更新。跨设备无方案时采用UTC显示，并注明；不采用运行设备当前时区重分组。

## 历史暂停/停用/恢复

扩展milestone.kind为PAUSED/STOPPED/RESUMED，日期和可选原因沿用date/note，支持既有可靠保存、编辑/删除、Upcoming及四语。此为用户明确陈述的历史事件，不是指令：不改变今天的药物active/通知/规则，也不重写过往方案。没有medication_id和精确时刻，因此本批不能承诺药物级停用状态和自动时期切分；这些需要另行设计，不把日期级里程碑强行赋予瞬时状态。

## 兼容与校验

保持schema6表/字段与旧Context1、VisitPack1、RegimenDefinition签名及PK不动。SchemaGuards的同一predicate用于写库/restore，onOpen已重新安装trigger，将新类型加入allowlist；旧类型仍有效。新应用可恢复schema1–6旧备份；含新类型的备份不承诺恢复到build12旧应用。PDF里程碑标签扩展，不改变VisitPack1的模板或计数算法。

## 验证

合成HRT Tracker/Trans Memo导入记录，无方案/早于第一方案/已有时期/未来/删除/缺失剂量/缺失route/时区边界/相同药物多来源；同一实际历史不制造方案。四语/简单模式不泄露药名备注，原记录/库存不改。里程碑新类型库写入和加密备份往返，旧trigger升级重新开放和非法类型拒绝。原生UI验证摘要可见且能打开原记录入口。TalkBack由用户报告已通过，不在本批重复修改。

## Prior art

源数据格式互通与历史事实汇总是通用产品思想。本批按HRT Log自身PeriodTimeline/History边界独立实现，未读取或复制外部项目实现。
