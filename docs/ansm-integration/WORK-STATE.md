# 工作状态

2026-10-16；起始默认分支claude/new-session-1959qb，2f4b958425e1cd67dad1d97deb8ab724eb8ecac1。
当前分支feature/ansm02b-03b-p0-restore-20261016，最新HEAD以git/对应PR为准，工作树/workspace/HRT-Log-integration。

|阶段|状态|下一动作|
|---|---|---|
|Phase0|IN_PROGRESS|来源许可再核实/恢复依赖图|
|P0|IN_PROGRESS|真实覆盖恢复红测试→安全删除/预检/守卫保留|
|P1|NOT_STARTED|统一引用/包装/消费Schema与备份合同|
|P2|NOT_STARTED|生产入口，官方资源OFF，手动来源可用|
|P3|NOT_STARTED|复用原35测试及计量核心|
|P4|NOT_STARTED|Legacy/Verified互斥，事务幂等|
|P5|NOT_STARTED|统一预测及四语集成|
|官方分发|BLOCKED|许可版本冲突、FT/ST保留；不挡通用实现|

本轮已明确授权生产实现，旧03B0“只能test-only”的限制不再适用于命名范围；不把旧PR当已合并。
P0 红测试已运行：真实生产恢复/清库路径 4 项均按预期 FAIL（3 项 RESTRICT 自引用阻断，1 项额外保护触发器丢失），未修改生产代码制造失败。设备验证 NOT_RUN。
用户已授权本任务自动合并。#50 已合入默认分支 b1b2d93；#51 合入原功能 base，桥接 #52 待当前 CI。#45/#48/#52 已请求 auto-merge，严格保护和六检查保留。
