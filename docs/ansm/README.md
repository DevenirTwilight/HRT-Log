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
  实现43a0acf的push/PR完整CI均成功，[实际CI与报告证据](ci-code-evidence.json)。
  最终集成/交接HEAD须以[PR45当前HEAD的检查](https://github.com/DevenirTwilight/HRT-Log/pull/45)为准，
  不能冒用43a结果；负责人验收仍待完成，不称发布通过。
- 阶段 C–E：未开始；ANSM-01 验收后才能推进依赖它的产品功能。
- ANSM-05/06/PK：只设计，不批准临床计算、自动提醒或模型更改。

无自动合并、签名、APK交付、Release、版本号变更或真实健康数据。

新增阻断：BDPM页脚Etalab2.0与所链接PDF版本1.0冲突；正式App打包前须澄清。
01资源尚未接入Android，02/03/04没有编码；05/06/PK尚未进入阶段F交付。

## 本批交付与集成边界

- PR：[ANSM-01 #45](https://github.com/DevenirTwilight/HRT-Log/pull/45)，分支
  `feature/ansm01-reference-data-20261015`；未合并。开工设计分支
  `feature/ansm01-offline-reference-20261015`保留23d32fa及其完整成功CI，无任务被取消。
- 开工默认分支基线94ceb81；工作期间PR44合并为2ab7a0c。本地以保留历史的merge纳入，
  不覆盖其共享文件。当前versionCode27来自PR44；本PR相对最新默认分支没有版本/Gradle差异。
  PR44的其他交付授权不适用于本任务，仍不访问签名材料或交付APK。
- 改动文件：本目录6份文档/JSON；`docs/reference/fr/`六份资源/说明；
  `scripts/bdpm_reference.py`及`test_bdpm_reference.py`。全部新增；没有修改既有测试、
  app/core/pk-engine/importer/CI/Gradle或正式来源SHA机制。
- 数据结构：只新增独立JSON schema1；Room10、PNBAK1序列化9、历史记录/快照/方案/PDF、
  库存流水/VOIDED/撤销恢复、锁定与伪装、PK参数不变。没有Android资源打包/运行时加载。
- 实现提交的CI下载报告：app1158登记/173skip/0fail；data180、reminder19零失败；
  PeriodStability5/5零skip；143项scripts通过；lint、Debug/unsignedRelease及No INTERNET检查成功。
  173项skip按报告原样保留，本PR未添加skip或修改任何既有断言。设备CI是模拟器，不是物理手机。
- UI无改动，无新增截图。四语320/411dp×1/2字号×浅深色的新UI验收、人工TalkBack、
  物理设备、真实历史升级验收均NOT_RUN。本地无SDK/javac/KVM，Android检查由CI实际执行。
- 2ab7a0c主线运行38809226303的API26红对照发生较早断言失败，
  `SKIP_LIVE_WRITER`→`CLEANED_OWNED_INCOMPLETE`；故障注入控制尚未到预期消息检查点。
  已读实际日志及脚本，记录在CI证据JSON；本任务未改测试或重跑主线，不宣称是生产缺陷，
  不把该次主线运行写成全绿。43a的push和PR对应API26检查实际均成功。

## 下一阶段与待审事项

先由负责人审阅01字段/来源/冲突和当前HEAD CI，不自动合并。许可版本澄清、FT/ST语义核对、
授权终止日期/生产状态/实时供应未知、独立人工药品数据审阅均有记录，不以CI代替资料验收。

01验收后：02先确定稳定CIS/CIP引用及冻结快照如何安全持久化；现有模型没有专用字段，
不得塞入PK字段、needs_review或未纳入备份的偏好侧车规避兼容性设计。
需要Room变更时先提交独立迁移/旧备份/失败回滚方案等批准。
03先做单位/包装转换规格与输入验证；任何库存接线/流水或预测含义改变必须独立审查批准。
04在可靠产品身份基础上做只读资料和RCP/Notice入口，独立核实临床文字再利用、
四语、安全文案、隐私边界和无障碍。05/06/PK按阶段F只交设计/风险/验收条件，不编码高风险功能。

