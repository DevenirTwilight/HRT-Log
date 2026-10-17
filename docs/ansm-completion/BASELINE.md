# ANSM V4 当前基线

当前检查点（2026-10-17）：实际默认仍f1e9b4de1c97e73966b4af8900b78391eada5c43 / Room11 / 业务10。#57最新dd7e61c64768ce562ea4ee5a9a94607293a949ce，仍OPEN/DRAFT；候选Room12/业务11、全局审计、精确恢复、真实内部Repository、更正/独立协调、共享UNKNOWN保护已经实现，本地data390/shared114与原生源码编译PASS。原生大库前三轮FAIL（第三轮实际heap384m），修正待原生重跑；最终六CI尚未闭环，App精确写入口OFF、P5/P6 NOT_IMPLEMENTED。不能把以下历史里程碑当当前缺失项或验收结果。

独立ANSM-04A草稿#58当前fda7782：四语只读RCP/Notice分栏、证据DTO和显式公共产品页安全导航；选定91项/384张合成截图及离线Python160 PASS，逐商品58/64/96覆盖台账已提交。正式临床资源OFF、04B/05/06/PK索引未完成，04A最终CI待验、实机/TalkBack NOT_RUN。没有发布或APK交付。

## 历史检查点（保留其当时的源码与范围）

2026-10-17。实取证据：[JSON](baseline-evidence-2026-10-17.json)。

默认分支 `claude/new-session-1959qb`：`f1e9b4de1c97e73966b4af8900b78391eada5c43`；运行Room11 / PNBAK1业务10。P0–P3及#45/#48/#50已合并，不重复开发。

#57 OPEN/DRAFT，已推送HEAD `b95a0d92e390e60fd73f1ae0a28e13186a198e92`，push/PR六项检查均SUCCESS。该SHA包含内部精确备份与真实服药入口，源码未启用用户界面。当前工作树进一步完成修订/删除/恢复/实盘合同，本地data356零失败/跳过及原生源码编译PASS，尚未推送，不能借用b95原生75的CI作验收。

|范围|默认分支真实能力|#57候选与剩余|
|---|---|---|
|药品引用|用户自填来源搜索/详情/绑定/解除/历史|保持；官方目录资源OFF|
|计量|纯生产domain API，不管库存|Room12编码 / 业务11；内部精确恢复、记录事务、票据；正式UI仍OFF|
|库存|旧标量路径、日历/库存/提醒共用旧预测|修订恢复本地已验；显式协调、生命周期票据、共享UNKNOWN准入待完成|
|官方资料04/05/06|未实施正式只读完整流程|按独立PR实现允许的DTO/入口/未知状态；临床内容和资源需来源/权利审查|
|PK|原模型与独立研究证据|模型/PDF数值不改，仅补证据索引|
|附件/网络/发布|正式附件OFF，无INTERNET|不启用，不签名/改版本/交付APK|

必需检查：jvm、android、device-tests(API35)、attachment-poc-compatibility(26)/(29)、science-change-gate。research-deep遵守原条件，合法SKIPPED不算PASS。真正设备、断电、人工TalkBack仍NOT_RUN。

当前没有其他开放库存/数据层实现PR；旧科研及#34不夹带合并。主线SHA、任务旧锚点、候选SHA分别保留，不能把本表当发布批准。


2026-10-17 subsequent P4 checkpoint: local data373/shared98, Android test-source compilation, App PeriodStability5 and PrivacyNoticeFacts3 PASS. Eight scheduled contracts verify planned-context freezing and exact backup round-trip; new native98 runtime and final CI pending. Earlier UNKNOWN matrix32/192 and data362/reminder21/app40 evidence remains separately scoped. Default f1e9b4d still Room11/business10; candidate remains Room12/business11, user-entered only and App measured write entry OFF. Later-count reconciliation, performance, P5/P6 and official distribution are not complete.
