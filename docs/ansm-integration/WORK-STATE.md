# 工作状态

2026-10-16；起始默认分支claude/new-session-1959qb，2f4b958425e1cd67dad1d97deb8ab724eb8ecac1。
P0分支feature/ansm02b-03b-p0-restore-20261016，最新HEAD以git/对应PR为准，工作树/workspace/HRT-Log-integration。

|阶段|状态|下一动作|
|---|---|---|
|Phase0|IN_PROGRESS|来源许可再核实/恢复依赖图|
|P0|DONE|#53已合并cfbe62c，源HEAD六检查通过；主线复验进行中|
|P1|IN_PROGRESS|完整本地215项PASS/原生编译PASS；接入最新主线并提交独立PR，原生运行待CI|
|P2|NOT_STARTED|生产入口，官方资源OFF，手动来源可用|
|P3|NOT_STARTED|复用原35测试及计量核心|
|P4|NOT_STARTED|Legacy/Verified互斥，事务幂等|
|P5|NOT_STARTED|统一预测及四语集成|
|官方分发|BLOCKED|许可版本冲突、FT/ST保留；不挡通用实现|

本轮已明确授权生产实现，旧03B0“只能test-only”的限制不再适用于命名范围；不把旧PR当已合并。
P0 红测试已运行：真实生产恢复/清库路径 4 项均按预期 FAIL（3 项 RESTRICT 自引用阻断，1 项额外保护触发器丢失），未修改生产代码制造失败。设备验证 NOT_RUN。
用户已授权本任务自动合并。#50 已合入默认分支 b1b2d93；#51 合入原功能 base，桥接 #52 待当前 CI。#45/#48/#52 已请求 auto-merge，严格保护和六检查保留。

P0 本地全数据回归191/191 PASS，包含新增共享恢复7项、依赖图4项；原生测试编译PASS，原生运行NOT_RUN。新增一次故障注入断言曾因协程复制异常对象而FAIL，改为确认原注入错误消息逃逸并完整比较数据/身份/守卫，未改变已有断言；随后全套通过。

P1工作树/workspace/HRT-Log-p1，feature/ansm02b-03b-p1-persistence-20261016，基于P0 eedf587；P0尚未合并。字段/身份/epoch/精确捕获/新业务备份合同见p1-persistence-contract.md，状态DESIGN，不标为生产可用。

P1新引用存储、专用CAS/幂等API、默认UNKNOWN历史捕获、恢复epoch和统一新表Schema已有代码；17项新测试PASS。本地首轮191回归6项FAIL均已定位旧版本固定断言/旧fixture导出与迁移路径，已明确更新，完整重跑待执行。生产包装/精确分配尚未启用，P1 validator明确拒绝未启用的PRESENTATION/Measured操作，防止半实现数据被接受。
#52已合并至默认分支92f27e99e5567a9eb42bbb3f19a577238ca97c5c，主线六检查复验进行中。

P0 #53 source 1d368ef9a1868f57291cd43a193a74c02dd9e8b6，六必需检查均SUCCESS，merge cfbe62c4a466f55234d53fbeff4c7f2c03224d50；5份CI报告ZIP已校验官方digest/CRC并确认无APK。API26/29/35新增恢复合同各7/7通过，PeriodStability5/5、PK124/124通过；旧条件SKIPPED保留见p0-ci-evidence.json。未签名/发布/交付，物理设备/断电NOT_RUN。
P1完整215项PASS（24新增），0失败/跳过；原生编译PASS，执行NOT_RUN。新迁移合同覆盖1…10→11、Room10旧37实体与REVERSE账本位级指纹、迁移故障DDL回滚与旧版本降级拒绝。中间失败属于测试夹具构造/旧版本固定期望，未关闭现有守卫；旧28业务表新增7=35，附件与本地runtime仍不备份。
