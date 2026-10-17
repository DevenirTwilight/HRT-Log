# V4 来源与需求初始映射

按原任务书0–40节全部登记，粒度目前为节；不能据此声称医学/产品逐条验收。机器索引见TRACEABILITY.json；逐药品与声明级证据继续补入同一表，不重开旧阶段。

|ID|阶段|要求|当前|
|---|---|---|---|
|V4.00|GOVERNANCE|工作身份、核心目标、终止条件|EVIDENCE_ONLY / IN_PROGRESS|
|V4.01|GOVERNANCE|实时扫描仓库；继承已完成成果，不重做|EVIDENCE_ONLY / IN_PROGRESS|
|V4.02|GOVERNANCE|自主执行、分工、权限与硬边界|EVIDENCE_ONLY / IN_PROGRESS|
|V4.03|P4|P4｜完成 #57：全数据集校验、精确备份、实际库存与历史更正|NOT_IMPLEMENTED / IN_PROGRESS|
|V4.04|P5|P5｜统一库存预测、四语界面与端到端完成|NOT_IMPLEMENTED / NOT_STARTED|
|V4.05|ANSM04|ANSM-04｜官方文档入口、可追溯安全资料（实际用户功能）|NOT_IMPLEMENTED / NOT_STARTED|
|V4.06|ANSM05|ANSM-05｜药品特定安全教育信息与事实累计（高风险、受门控）|NOT_IMPLEMENTED / NOT_STARTED|
|V4.07|ANSM06|ANSM-06｜供应/召回/安全公告：离线且不冒充实时|NOT_IMPLEMENTED / NOT_STARTED|
|V4.08|ANSM_PK|ANSM-PK｜药品说明书药代资料与现有模型的科学防火墙|NOT_IMPLEMENTED / NOT_STARTED|
|V4.09|SOURCE|官方数据许可与实装闸门（横跨 04/05/06）|NOT_IMPLEMENTED / NOT_STARTED|
|V4.10|GOVERNANCE|横向质量门：数据、隐私、UI、科学和设备|EVIDENCE_ONLY / IN_PROGRESS|
|V4.11|GOVERNANCE|依赖图与 PR 交付计划（不要单个巨型 PR）|EVIDENCE_ONLY / IN_PROGRESS|
|V4.12|GOVERNANCE|正式收尾和交接结果|EVIDENCE_ONLY / IN_PROGRESS|
|V4.13|GOVERNANCE|立即执行顺序（从这里开工）|EVIDENCE_ONLY / IN_PROGRESS|
|V4.14|GOVERNANCE|补全审计结论：旧版 0–13 节继续有效，本节以下为强制补充（V2）|EVIDENCE_ONLY / IN_PROGRESS|
|V4.15|SOURCE|药品覆盖与数据质量：从泛称“支持法国药品”改成逐产品验收|NOT_IMPLEMENTED / NOT_STARTED|
|V4.16|SOURCE|BDPM 数据集清单、文件级日期及额外字段接入判定|NOT_IMPLEMENTED / NOT_STARTED|
|V4.17|ANSM04|ANSM-04 必须有完整 RCP / Notice 导航和漏服说明信息归档|NOT_IMPLEMENTED / NOT_STARTED|
|V4.18|ANSM05|ANSM-05 药品安全资料：逐项覆盖并避免未核实风险结论|NOT_IMPLEMENTED / NOT_STARTED|
|V4.19|SOURCE|地区、来源与商品映射：用户手动药物永远不能被法国目录覆盖|NOT_IMPLEMENTED / NOT_STARTED|
|V4.20|SOURCE|离线官方来源更新、兼容历史、版本回滚与外链隐私|NOT_IMPLEMENTED / NOT_STARTED|
|V4.21|P4|P4/P5 全局完整性的补充验收（不重复前文实现）|NOT_IMPLEMENTED / IN_PROGRESS|
|V4.22|ANSM06|ANSM-06 更新生命周期、药物警戒和批号核验|NOT_IMPLEMENTED / NOT_STARTED|
|V4.23|GOVERNANCE|项目级“全覆盖完成”判定：按源条目关闭任务而不是按阶段标题关闭|EVIDENCE_ONLY / IN_PROGRESS|
|V4.24|GOVERNANCE|执行顺序修订：补全工作应与原 13 节并行穿插，不等 P4 完成再开始资料审计|EVIDENCE_ONLY / IN_PROGRESS|
|V4.25|GOVERNANCE|V3 复核结论与最新执行锚点（替代第1节中的历史快照）|EVIDENCE_ONLY / IN_PROGRESS|
|V4.26|P4|P4 新硬门：每个包装必须自描述库存编码，不能靠引用表猜测|NOT_IMPLEMENTED / IN_PROGRESS|
|V4.27|CROSS_CUTTING|导入器、CSV/PDF/Visit Pack 与数据可移植性（V2 缺少的下游功能闭环）|NOT_IMPLEMENTED / NOT_STARTED|
|V4.28|CROSS_CUTTING|生命周期与隐私：删除全部数据、回收站、清空、缓存和恢复闭环|NOT_IMPLEMENTED / NOT_STARTED|
|V4.29|CROSS_CUTTING|正式资料输入、更新包和外部链接的威胁模型|NOT_IMPLEMENTED / NOT_STARTED|
|V4.30|CROSS_CUTTING|时间、时区与资料陈旧性的可验证处理|NOT_IMPLEMENTED / NOT_STARTED|
|V4.31|CROSS_CUTTING|上线方式、部署后回归与安全退出策略|NOT_IMPLEMENTED / NOT_STARTED|
|V4.32|CROSS_CUTTING|跨模块映射与防止静默漏项：从验收需求到真实产物|NOT_IMPLEMENTED / NOT_STARTED|
|V4.33|CROSS_CUTTING|严格的最终完成判定（V3 对“全做完”的附加封口）|NOT_IMPLEMENTED / NOT_STARTED|
|V4.34|GOVERNANCE|V4 本轮查漏结论与当前代码锚点（优先于过期快照）|EVIDENCE_ONLY / IN_PROGRESS|
|V4.35|ANSM06|BDPM 加强监测（黑色倒三角）及监管状态不得混同|NOT_IMPLEMENTED / NOT_STARTED|
|V4.36|ANSM06|不良反应报告：为用户提供官方渠道，而非自动上报|NOT_IMPLEMENTED / NOT_STARTED|
|V4.37|CROSS_CUTTING|实际计量边界补测：注射/多剂量、分片、储存和开封期限|NOT_IMPLEMENTED / NOT_STARTED|
|V4.38|CROSS_CUTTING|外部浏览器、身份与网络边界的技术性更正|NOT_IMPLEMENTED / NOT_STARTED|
|V4.39|ANSM05|最终临床内容、来源与发布开关的“三向一致”闸门|NOT_IMPLEMENTED / NOT_STARTED|
|V4.40|GOVERNANCE|V4 范围封口：解决已确认缺陷，不允许无限扩需求|EVIDENCE_ONLY / IN_PROGRESS|

## 当前可追溯增量（2026-10-17）

- P4 #57/dd7e61c：内部真实流程及兼容已编码并本地390项通过；原生性能前三轮FAIL、实际ART配置与阶段日志修正待重跑，最终六CI待验，PRODUCTION_BLOCKED。
- 04A #58/fda7782：四语只读资料入口/安全导航，91项与384图及Python160通过；58/64/96逐商品台账完整，正式临床内容和资源OFF，最终CI待验。
- 06公众报告入口本次HTTP500，来源可用性UNVERIFIED、App功能NOT_IMPLEMENTED。

上述是局部实际进展，不关闭节级全部要求。原表为初始记录；机器表对应条目已补最新证据与未完成项。
