## 2026-10-17 主线 CI 修复合并与本地最终补证

#63 由负责人已启用的自动合并流程合入 d13048b18ad15eb4e7aba70f63c3c5d94a705660；源80c31ce最终六CI均成功。已校验CI工件SHA/CRC：reminder20、data243均0fail/skip，app1379零fail、205既有skip。主线新复验39065037526尚在跑，不能称主线复验完成。P4 已同步这份已合并修复，保留双方 HANDOFF。

a3e95e4 本地最终 native 源码编译、Period5、Privacy3 PASS；最终 JVM 大库审计1974ms、预测2337ms、恢复6303ms、三次写入2625/2352/2415ms，峰值475205928B，仍DIAGNOSTIC_ONLY。低内存原生39064593821待结果。优化前bfcec65重复原生 FAIL，新增恢复151970ms超120s；完整报表及SHA/CRC已归档，不只挑选较好的一次。P4_BLOCKED，正式入口OFF。

## 2026-10-17 P4 审计热点优化提交

保留原始提前行数门后，最终本地 data402 零失败/零跳过；Python168 PASS。单构建器 canonical 以冻结旧编码器作字节/SHA 对照；候选列检查保留所有类型/行数/UTF8预算，批量扫描；候选来源检查仅事务内有界复用完整文本。新3项 shared 负向测试复用原生 SQLCipher runner，未削弱旧断言。先前中间版 Android 源码编译成功，最终重编译/原生预算与六CI待跑。中间 JVM 大库审计1916ms、三次写入2369/2534/2267ms，DIAGNOSTIC_ONLY，不冒充 API26。

主线 CI 五项实际失败出处和公开依赖 URL 已核对，历史数字 HTTP 状态 UNAVAILABLE；#63 为 Claude 独立修复仍待 CI。API35 Back 三失败原因 UNVERIFIED，未改断言。现有 UI 最小接线审计见 p5/existing-ui-wiring-audit-2026-10-17.md；未改共享 UI。Beta 缺口清单独立草稿 #64，真机/升级 NOT_RUN。结论 P4_BLOCKED / 正式入口 OFF，P5/P6 未实施。

## 2026-10-17 后端收尾任务重新核查

默认分支已到 35600a0（#60 仅参考图、#62 发布账本已合并）。#63 是 Claude 独立 CI 修复，正在检查，不争用共享 Gradle/CI。#57 原生 run39047684726 完整运行但四项时限失败，证据见 p4/performance/native-fifth-evidence.json；P4_BLOCKED，P5/P6 未开放。CPU 诊断不是验收。新任务原文归档，需求编号冲突保留两份授权后顺延；发布 build29 不重做。

# 工作状态

2026-10-17。最新默认claude/new-session-1959qb为f1e9b4de1c97e73966b4af8900b78391eada5c43（P3合并后六项CI通过）。下表为当前状态，历史日志保留当时的失败与恢复证据。

|阶段|状态|当前证据及下一动作|
|---|---|---|
|Phase0|DONE|联合实施§102和受保护自动合并§103；官方许可/FT-ST未闭环，打包OFF|
|P0|INTEGRATION_VERIFIED|#53已合并，主线六检查和三API恢复合同通过|
|P1|PRODUCTION_IMPLEMENTED|#54合并c95cea1，主线六检查通过；Room11/业务10，实际包装未启用|
|P2|PRODUCTION_IMPLEMENTED|#56合并b5d9751，主线六检查复验PASS；真实手动来源/关联/历史UI，App1379零失败205条件跳过，data243与192图已核验|
|P3|PRODUCTION_CORE_IMPLEMENTED|#55源e1c40bd六检查成功，合并f1e9b4d；只生产化计量API，不代表库存接线；主线六检查PASS；Gate3红对照顺序修复保留全部原断言|
|P4|IN_PROGRESS / NOT_ACTIVATED|#57草稿；真实Repository创建/用药/更正/删除/恢复/实盘本地data356/共享82/原生编译/Python159 PASS；b95a0d9六CI与三API共享75已核验PASS；新82/新App回归未跑；独立协调、生命周期票据、共享UNKNOWN及UI仍待完成|
|P5|NOT_STARTED|统一新计量预测不确定性和实际包装正式UI未实施|
|官方分发|BLOCKED|许可版本冲突与FT/ST未闭环，不能因#45已合并而打包官方目录|

#45/#48/#50及#51经#52桥接已合并；P0/P1/P2/P3均保护合并，无管理员绕过。当前默认运行仍Room11，Room12仅在#57待验源码，不称已部署。医疗附件/网络/PK/签名/版本/发布不动，实机/断电/TalkBack NOT_RUN。#57不能自动合并未完成的正式库存接线。资料UI医疗中心缺失部分审计见p2/medical-ui-status-2026-10-16.md。

严格JSON检查点：data334项零失败/跳过，Android测试源码编译PASS（共享计量60项原生运行NOT_RUN）。公开恢复已拒绝尾随JSON、重复键、无效UTF-8；精确桥接字段必须为有界整数，不能先经Double舍入。PNBAK1外层不变，完整精确库存导出/恢复仍OFF。3343724远端API26/29 PASS；API35 FAIL为MigrationBaselineTest残留业务10断言（实际11），本次改为明确11并新增旧容器LEGACY验证，未删除原断言。新CI待运行，不能称六检查通过。

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


2026-10-17 P4续作：#57内部真实计划服药、MISSED补录与format3冻结计划上下文已本地集成验证，完整data373/shared98、原生源码编译、Period5/Privacy3 PASS。共享UNKNOWN/生命周期/data362及四语32组192图已单独留证，混合库存正式入口守卫/data365 PASS。db1ef03六CI及三API共享82实际PASS并校验SHA/CRC。当前新增98原生和最终HEAD CI待验；后实盘独立协调、性能、P5/P6未完成，App精确写入口OFF、#57 DRAFT。完整证据与下一步见p4/work-state.md，V4逐项追踪见../ansm-completion/TRACEABILITY.json。未合并、未发布。

2026-10-17 大库检查点：P4本地data389/shared114/原生及大库原生源码编译/Python163 PASS。14600合成事实+2000包装/操作/来源的完整备份恢复已通过；后续写入预算最初真实FAIL，经保留全部校验的查询优化后4.32…4.64s转绿。新增独立API26 SQLCipher低内存性能任务，未改六必需检查；原生低内存运行与新HEAD CI仍待验，不称P4完成。#57继续DRAFT、App精确写入口OFF，P5/P6未完成。
