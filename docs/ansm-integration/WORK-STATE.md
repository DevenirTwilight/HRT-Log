# 工作状态

2026-10-17。最新默认claude/new-session-1959qb为f1e9b4de1c97e73966b4af8900b78391eada5c43（P3合并后六项CI通过）。下表为当前状态，历史日志保留当时的失败与恢复证据。

|阶段|状态|当前证据及下一动作|
|---|---|---|
|Phase0|DONE|联合实施§102和受保护自动合并§103；官方许可/FT-ST未闭环，打包OFF|
|P0|INTEGRATION_VERIFIED|#53已合并，主线六检查和三API恢复合同通过|
|P1|PRODUCTION_IMPLEMENTED|#54合并c95cea1，主线六检查通过；Room11/业务10，实际包装未启用|
|P2|PRODUCTION_IMPLEMENTED|#56合并b5d9751，主线六检查复验PASS；真实手动来源/关联/历史UI，App1379零失败205条件跳过，data243与192图已核验|
|P3|PRODUCTION_CORE_IMPLEMENTED|#55源e1c40bd六检查成功，合并f1e9b4d；只生产化计量API，不代表库存接线；主线六检查PASS；Gate3红对照顺序修复保留全部原断言|
|P4|IN_PROGRESS / NOT_ACTIVATED|#57草稿；全局候选及操作因果链、零效果/退役隔离，data324/共享50/原生编译/Period5/Privacy3/Python158 PASS；新50原生NOT_RUN，旧10480c6六CI/三API35 PASS；候选Room12/业务10；独立编码、Schema11恢复、真实事务及预测UI待实施|
|P5|NOT_STARTED|统一新计量预测不确定性和实际包装正式UI未实施|
|官方分发|BLOCKED|许可版本冲突与FT/ST未闭环，不能因#45已合并而打包官方目录|

#45/#48/#50及#51经#52桥接已合并；P0/P1/P2/P3均保护合并，无管理员绕过。当前默认运行仍Room11，Room12仅在#57待验源码，不称已部署。医疗附件/网络/PK/签名/版本/发布不动，实机/断电/TalkBack NOT_RUN。#57不能自动合并未完成的正式库存接线。资料UI医疗中心缺失部分审计见p2/medical-ui-status-2026-10-16.md。

## 历史开发记录（当时状态，当前以上表为准）

P0完整CI证据见p0-ci-evidence.json。下载的五份报告已校验官方digest/CRC；API26/29/35新增恢复合同各7/7通过；PeriodStability5/5、PK124/124通过，旧条件SKIPPED如实保留。物理设备/断电测试NOT_RUN，无签名/发布/APK交付。

P1工作树/workspace/HRT-Log-p1，分支feature/ansm02b-03b-p1-persistence-20261016，已普通合入默认cfbe62c。新持久化合同见p1-persistence-contract.md。迁移覆盖1…10→11；保持旧37实体和历史REVERSE账本，迁移失败DDL回滚，旧版本降级拒绝。备份保留PNBAK1外层加密，新业务Schema10与Room11明确区分。旧业务1…9不含引用表，未知身份不回填。

P1只启用商品引用存储和上下文捕获；实际包装确认/精确消费尚未启用。validator明确拒绝PRESENTATION及measured_operation，防止半实现数据被接受。更新引用不走saveMedication，不resize容器，不调用新库存分配器。真实绑定UI尚未实现。

中间失败已保留于开发日志：旧固定版本期望/不正确旧备份夹具、RoomDatabase关闭助手、合成REVERSE构造顺序。修正夹具和明确版本期望，未删减既有业务断言。全部B0影子合同保留，新版本头拒绝与恢复失败目标不变断言保留。

最终P1源95f44d7包含重算摘要后的未来修订/溢出拒绝及JSON字段类型拒绝；233项完整回归PASS，详见p1-local-evidence.json。#54当前草稿，尚未合并或发布。发布二进制隔离本地因未构建unsigned release而NOT_RUN，源码隔离PASS；远端CI保留完整实际构建守卫，不以缺失产物冒充通过。

P1首轮原生CI FAIL：API26原生新引用17/17及迁移3/3实际PASS；既有Gate2测试仍把当前导出断言为业务9，另发现App重启测试固定Room10/业务9期望。已修正：Gate2显式校验当前10后剥离空的新引用表、构造真实旧9备份继续验证旧恢复；App当前版本断言更新为Room11/业务10，全部既有撤销、VOIDED、幂等、隔离及重启断言保留。尚待修正后原生重跑，不把前次失败冒充PASS。

P1源d9fee991bf61b98a3d560a9a224ba29fb9b4fa9b六项必需CI在push/PR均SUCCESS。已校验三API原生及build报告官方SHA256/ZIP CRC，无APK：API26/29数据110、App70各0失败0跳过；API35数据177零失败，App264零失败/8既有条件跳过；新引用17+迁移3各API全PASS。完整App1198零失败/205既有条件跳过，data233与reminder20零失败，PeriodStability5零失败。见p1-ci-evidence.json。尚未合并；需更新默认分支依赖后继续严格检查，不把本提交的后续CI当已完成。
P2 #56已有真实用户资料UI/专用控制器/离线搜索，基于P1的stacked草稿；首轮239数据、应用29及后续30操作/逻辑PASS，32矩阵初轮截图超时已修复测试采集方式，正在重跑。官方资料仍OFF；没有启用医疗附件。P3 #55生产计量核心本地126domain/204data/发布隔离PASS，待最新基线CI。

P1新增阻断：真实控制器已复现空引用数据集恢复后首次关联旧窗口未失效；#54已转回草稿，禁止在修复前自动合并。修复采用数据库打开时初始化的本地预览epoch（不进备份，不创建业务引用），首次确认必须携带打开时epoch。成功恢复/清空和物理删除使旧票据失效；已识别对象的INSERT OR REPLACE拒绝。无Room/业务备份版本再升级，不改药物/实际记录/库存事实。新增共享原生负向测试及完整回归待运行；之前d9六检查通过仍属历史证据，不代表此缺口已获验收。

空预览修复本地完整236项PASS、原生23项合同编译PASS；红测试真实观察1条不应发生的关联已归档p1-empty-preview-red-evidence.json。新增恢复空引用/物理删除后同ID复用/拒绝替换已识别对象测试，Room1…10迁移仍不改37旧实体，仅本地runtime初始化计数明确由0改1。新epoch协议的原生23项×3尚未实际运行；#54继续草稿，P2真实控制器红场景待接新必需票据后重验。

P2源fb797e5接新previewEpoch后完整data242/app63全PASS，真实EmptyPreview恢复红场景转绿；192新截图已生成。P1新增只读referencePreviewEpoch/recordedReferenceHistory接口，历史列表可携带同一事务读取的epoch，防止ID复用；无来源读取不创建业务引用，当前引用更正不重写历史。新增共享测试使原生合同24项，最新API待重验。

P1已普通合入实际默认分支的ANSM-01/#45及02A/#48；不改变其冻结数据，不将参考JSON打包进Android。P1源ef19db8本地237数据PASS/原生24合同编译PASS；P2真实控制器空恢复场景及242数据/63应用PASS已完成。#54可恢复审阅并等待当前基线六检查；之前401为长驻监控凭证过期，当前工具gh访问正常，重启监控继续严格保护，不绕过检查。
