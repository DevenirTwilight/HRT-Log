# ANSM / BDPM：分阶段交接

2026-10-15。此目录是本任务的独立进度入口，避免与开放 PR #44 同时修改
HANDOFF、REQUIREMENTS、STATUS、BACKLOG。合入主线时再由集成阶段添加索引；
本轮用户的独立分支/PR授权覆盖 REQUIREMENTS §2 的历史分支限制。

- 基线：`94ceb81c55425c51e2339d54e4b59cd6017c85b5`。
- 默认分支：`claude/new-session-1959qb`，不是 main。
- 阶段 A：[现状、字段映射、来源核实与实施设计](audit-and-design.md)。
- GitHub 开工快照：[repository-audit.json](repository-audit.json)。
- 阶段 A：已提交设计 `23d32fa` 并推送；基线/映射见上。
- 阶段 B：ANSM-01 独立资源与校验已编码；[来源核实报告](source-verification.md)，
  [离线资源/复现方法](../reference/fr/README.md)。本地39项目录合同测试、全部143项scripts测试通过。
  最终HEAD完整CI及负责人验收仍待完成，不称发布通过。
- 阶段 C–E：未开始；ANSM-01 验收后才能推进依赖它的产品功能。
- ANSM-05/06/PK：只设计，不批准临床计算、自动提醒或模型更改。

无自动合并、签名、APK交付、Release、版本号变更或真实健康数据。

新增阻断：BDPM页脚Etalab2.0与所链接PDF版本1.0冲突；正式App打包前须澄清。
01资源尚未接入Android，02/03/04没有编码；05/06/PK尚未进入阶段F交付。

