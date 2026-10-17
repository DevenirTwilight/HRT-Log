# ANSM-PK 证据索引与科学防火墙

这是维护者证据审计，不是药品说明书离线资源或新增模型。当前状态为 `EVIDENCE_ARCHIVED_PARTIAL_REVIEW / MODEL_CHANGE_BLOCKED`。未获独立科学复核，不以工程CI证明临床准确性。

- [机器索引](evidence-index.json)：现有pk-params的11个BDPM引用、20条既有研究获取记录、生产来源文件指纹与未核实项。
- [本次三份RCP5.2观察](rcp-observations-2026-10-17.json)：原有已知产品URL实际返回HTTP200，响应完整SHA、日期、商品标题定位和限定条件。原文HTML留在仓库外，不在git或APK。当前文档版本与正文变更日期未知，不以获取日代替。
- [原研究台账](../pk-research/p2/p2r-cohort-evidence-ledger.json)与[来源清单](../pk-research/p2/source-manifest.json)：继承原研究的已暴露/训练/队列重叠/方法限制，既有研究不重新标成独立外测。
- [工作状态](WORK-STATE.md)：仍未完成的范围及审批边界。

复现仅索引与源文件指纹：`python3 scripts/build_ansm_pk_evidence_index.py --check`。不访问网络，不拟合参数，不解释健康记录。源文件哈希与响应哈希提供核查指针，不表示仓库重新分发了期刊全文或所有原始科学数据。官方资料不能因项目MIT许可证而改标MIT。

|范围|可核实的限定事实|不能据此推出|
|---|---|---|
|[Oestrodose 63071975](https://base-donnees-publique.medicaments.gouv.fr/medicament/63071975/extrait)|RCP5.2：绝经后女性、2次泵压/日语境中的平均浓度；约10%吸收描述|不是Cmax、个体吸收入血换算、使用建议或通用凝胶参数；0.75mg施用量与75µg近似吸收描述不同|
|[Progynova 60940721](https://base-donnees-publique.medicaments.gouv.fr/medicament/60940721/extrait)|口服酯型；标签峰时4–6h；代谢物排泄约一天|不能替代母体清除半衰期，不能按一个峰时定整条曲线；不外推其他盐/酯/途径|
|[Provames 65421374](https://base-donnees-publique.medicaments.gouv.fr/medicament/65421374/extrait)|微粉化17β-E2口服2mg，标签峰时3–6h|当前E2_ORAL来自另一FDA参考，模型峰时约7.026h；条件不一致须独立复核，不能直接宣布模型无效或自动拟合|
|既有P2研究|原始获取层级、人群/队列与已暴露性质按来源台账保存|PubMed研究不变成ANSM结论；单点/群体均值/图像估读不变成个人完整曲线、真实Tmax或盲法外测|

三份RCP均未给出足以在本次独立确认的完整采样方案/检测方法/个体曲线。未把标签数值合并成一条“法国官方曲线”，不填缺失的Cmax/AUC/母体半衰期。其余8个既有官方引用本次未获取，58商品逐项5.2核实未完成。

后续需独立核实其余来源、研究人群与制剂/剂量/稳态/采样/基线/检测统计、8h后长尾与真正held-out验证。任何正式参数、显示数值、模型默认值、冻结历史或PDF/CSV曲线变化均需原PK_DISPLAY_REVIEW与科学验收；本PR不申请这些变化。

本分支相对基线没有pk-engine/app/core/importer/数据库/备份/Gradle/CI/权限/签名/版本变更。历史PDF文件未读取或改写，未触碰真实健康数据。运行时PK回归等由实际CI另行记录，索引字节复现不冒充运行测试。

现有元数据还需区分历史说明与运行事实：pk-params顶层status仍写“not used by the engine yet”，但其models_note和实际FittedModels/Engine已经表明运行时读取models字段。该旧status不能当作模型未被使用的保护开关。本批仅记录此文档口径不一致，不修改受保护参数文件，也不据此声称发生新的科学模型变化。
