# 工作状态

2026-10-16。起始默认分支 claude/new-session-1959qb，基线 2f4b958425e1cd67dad1d97deb8ab724eb8ecac1；当前已验证主线 cfbe62c4a466f55234d53fbeff4c7f2c03224d50。

|阶段|状态|证据和下一动作|
|---|---|---|
|Phase0|DONE|负责人联合实施授权已完整归档；官方许可/FT-ST仍未闭环，分发OFF|
|P0|INTEGRATION_VERIFIED|#53已合并；主线六项必需检查PASS；API26/29/35新增恢复测试各7项PASS|
|P1|IN_PROGRESS|Room11/业务备份Schema10、稳定引用/CAS/历史捕获已实现；上一轮231数据+8应用测试PASS，新增修订边界测试正重跑；尚无P1 PR，原生运行NOT_RUN|
|P2|NOT_STARTED|真实生产入口，官方资源OFF，保留手动来源|
|P3|NOT_STARTED|复用原35项测试，审查并生产化计量核心|
|P4|NOT_STARTED|Legacy/Verified互斥、精确账本、事务幂等|
|P5|NOT_STARTED|统一预测不确定性及四语集成|
|官方分发|BLOCKED|许可版本冲突、FT/ST原码保留；不阻断通用实现|

#50已合入默认分支b1b2d93；#51合入其原功能base，桥接#52已合入92f27e9；P0 #53源提交1d368ef已合入cfbe62c。用户授权本任务受保护自动合并（REQUIREMENTS §103），不得绕过六检查或合并无关PR。#45/#48仍OPEN、auto-merge已启用，不冒称已进入正式Android。

P0完整CI证据见p0-ci-evidence.json。下载的五份报告已校验官方digest/CRC；API26/29/35新增恢复合同各7/7通过；PeriodStability5/5、PK124/124通过，旧条件SKIPPED如实保留。物理设备/断电测试NOT_RUN，无签名/发布/APK交付。

P1工作树/workspace/HRT-Log-p1，分支feature/ansm02b-03b-p1-persistence-20261016，已普通合入默认cfbe62c。新持久化合同见p1-persistence-contract.md。迁移覆盖1…10→11；保持旧37实体和历史REVERSE账本，迁移失败DDL回滚，旧版本降级拒绝。备份保留PNBAK1外层加密，新业务Schema10与Room11明确区分。旧业务1…9不含引用表，未知身份不回填。

P1只启用商品引用存储和上下文捕获；实际包装确认/精确消费尚未启用。validator明确拒绝PRESENTATION及measured_operation，防止半实现数据被接受。更新引用不走saveMedication，不resize容器，不调用新库存分配器。真实绑定UI尚未实现。

中间失败已保留于开发日志：旧固定版本期望/不正确旧备份夹具、RoomDatabase关闭助手、合成REVERSE构造顺序。修正夹具和明确版本期望，未删减既有业务断言。全部B0影子合同保留，新版本头拒绝与恢复失败目标不变断言保留。
