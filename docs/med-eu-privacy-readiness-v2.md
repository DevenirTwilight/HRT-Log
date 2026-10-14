# MED V2 欧盟隐私与质量准入准备

核查2026-10-14。工程条件矩阵，非律师意见/CE/CNIL或安全认证。使用legal-citations来源核验方法；用户评估原文、后续修订分别[原样保留](audits/product-quality-2026-10/sources/med02b2/README.md)，此前SOURCE_MISSING已补齐。原文评分不采用，真实代码/官方条文与分发事实优先。

## 实际数据流和隐私P0

| 数据流 | 当前证据 | 可声明边界 |
| --- | --- | --- |
| 应用主动网络 | app/src/main/AndroidManifest.xml；scripts/check_release_manifest.py检查真实Release manifest无INTERNET；无账号/分析SDK政策 | 应用未申请INTERNET；不能声明系统技术上绝不发送任何数据 |
| 用户SAF选择器 | Backup/Import UI与app/export/Exports.kt、B2生命周期测试；系统provider可本地或云端，见[Android SAF](https://developer.android.com/guide/topics/providers/document-provider) | 系统提供者可能传输副本，不能由本应用清除其历史/缓存 |
| CSV/PDF/VisitPack | 已有用户主动SAF导出；文件可能明文 | 选择目标前说明范围与隐私；外部副本无法远程撤回 |
| 加密记录备份 | DataTransfer/RecordBackupCompatibility：PNBAK1 magic、32MiB/t3/p1、GCM、serialized9；不含附件 | 目标流close/receipt不保证云同步或永久保留；不能把Room10当备份schema10 |
| 存储与伪装 | SQLCipher；独立PrivateStore；allowBackup=false及dataExtraction规则，AppLock/FLAG_SECURE | PRIMARY索引/标题/计数/keys不进入伪装；系统/OEM/解锁RAM/root有边界 |
| 医疗附件 | Room10默认0，正式writer/viewer不存在 | 禁止宣称真实原件已经安全保存/备份 |

P0独立小PR：PRIVACY.md、README/关于页四语入口、导出/卸载/备份及伪装限制；每句映射代码/测试，不把UI改动混入本分支。四语320dp/2×/TalkBack入口、离线可阅读、明文与加密输出提示、无真实数据截图是验收条件。本文不是已上线隐私声明。

原文“备份Argon2 19MiB/2迭代”与当前PNBAK1源码不符，不能照搬；AppLock凭据与备份KDF必须分开描述。

## 条件适用性矩阵

| 事项 | 官方核验和条件 | 本项目状态/触发器 |
| --- | --- | --- |
| GDPR | [2016/679](https://eur-lex.europa.eu/legal-content/EN/TXT/?uri=CELEX:32016R0679) Art2(2)(c)区分自然人纯个人/家庭活动；recital18也不能概括豁免提供处理手段的主体。角色/目的/健康数据处理需场景化 | UNKNOWN法律定性；本地个人记录≠开发者/云provider所有角色都“义务近零”。增加托管/遥测/支持上传即重审；不打9/10分 |
| MDR | [2017/745](https://eur-lex.europa.eu/legal-content/EN/TXT/?uri=CELEX:32017R0745) Art2(1)/(12)、AnnexVIII Rule11按预期用途/功能评估；[MDCG2019-11 rev1官方入口](https://health.ec.europa.eu/latest-updates/update-mdcg-2019-11-rev1-qualification-and-classification-software-regulation-eu-2017745-and-2025-06-17_en)核对为2025-06-17 | 不自行宣告豁免或ClassIIa。记录/提醒/资料整理，不诊断/剂量决策；群体M2不是个人实测。宣传/预期用途改变先独立评审，免责声明不取代分类 |
| CRA | [2024/2847](https://eur-lex.europa.eu/legal-content/EN/TXT/?uri=CELEX:32024R2847) Art2范围及非商业FOSS条件、recitals15–20；Art71分期；[委员会时间线](https://digital-strategy.ec.europa.eu/en/policies/cra-summary)核对Art14从2026-09-11、主要义务2027-12-11，不能混成所有主体当前同一义务 | UNKNOWN具体主体适用；免费/MIT不自动豁免。付费功能/托管/商业赞助/分发性质改变重审；不推定一切捐赠免除或一切捐赠触发 |
| GPSR | [2023/988](https://eur-lex.europa.eu/legal-content/EN/TXT/?uri=CELEX:32023R0988) Art2/3按product和商业活动/市场提供分析，免费也须看事实；软件/独立App不能仅凭“非实体”排除 | UNKNOWN；需要确切产品范围/商业方式及最新委员会软件指引。[委员会FAQ](https://webgate.ec.europa.eu/safety/consumers/consumers_safety_gate/obligationsForBusinesses/documents/Q%26A.pdf)定位成功但实际open重定向临时不可用页，未声称读到软件段落。原稿仅二手律师链接不能当豁免依据；未确认就不写“GPSR不适用” |
| EAA | [2019/882官方正文](https://eur-lex.europa.eu/eli/dir/2019/882/oj) Art2列举产品/服务；LDH条目正文遇反机器人页，另核验EUR-Lex官方页面 | UNKNOWN是否落入列举范围；不把所有健康App自动纳入。四语/320dp/2×/TalkBack为独立质量门，不以法定不适用为删测试理由 |
| EHDS | [2025/327](https://eur-lex.europa.eu/legal-content/EN/TXT/?uri=CELEX:32025R0327) Art2/第III章与wellness互通标签范围、Art105分阶段；保存资料不是接口认证 | UNKNOWN最终分类；只FHIR语义参考，无医院互通宣传。EHR互通/正式数据交换/联网托管先独立评审；不宣布因无共享普遍不适用 |
| 法国HDS/SIS | 本轮未完成法国法令/ANS全部具体场景核验 | UNKNOWN；不托管/不新增Mon espace santé接口。接入场景须专项核对，拒绝“接入立即全认证”的一刀切结论 |
| AI法案 | 本轮没有引入OCR/临床AI/联网模型 | 本批不作完整法律定性；开发工具≠App临床AI。未来能力变化另评审 |

立法六项由Legal Data Hunter resolve_reference命中官方CELEX；五项get_document完整正文核查，EAA索引正文异常如实保留，使用官方EUR-Lex替代。CNIL健康应用页面本次open503，搜索可定位官方页面；不将该页面当已完整阅读或新豁免依据。其[2025移动应用建议修订官方入口](https://cnil.fr/fr/recommandations-applications-mobiles-modifiee)留作Privacy P0进一步审查。

## 预期用途候选与质量门

候选声明：“个人记录、提醒及用户选择的医疗资料整理工具；不提供诊断、剂量决定或疗效判断；研究群体曲线不能替代个人化验。”这不是法律豁免判定。同步README/关于/发行文案需独立P0授权，不直接采用原稿“不是医疗器械”的绝对句式。

EN301549作为自愿工程对照：锁定/恢复/失败/取消/伪装/选择性导出必须走查；全流程焦点、标签、点击区域、非颜色提示、320dp/2×字号四语。现有B3/B4/MED01回归仍跑；未来附件页面尚未存在，端到端NOT_RUN，不能以原型图声称过关。

## 许可和分发专项

当前MIT不追溯授权Git历史旧移植代码；docs/licensing.md保留公开历史事实，不擅自结案/删Release/重写历史。须独立核查可访问标签/旧产物/权利状态，产品负责人决定处置。不得把“当前重写”当旧代码获授权。

[F-Droid独立构建指南](https://f-droid.org/docs/Submitting_to_F-Droid_Quick_Start_Guide/)与[原签名可复现路线](https://f-droid.org/docs/Reproducible_Builds/)应分别看：原签名路线需要重建验证，不笼统宣称所有收录均已字节可复现。本轮未申请/改签名；未来验证既有正式证书覆盖升级、Gradle固定依赖、Release来源和许可。版本基线：仓库Room10、旧正式build26 Room9、公开Release/实验PR不同。

## 从原稿吸收/拒绝/延期

吸收：独立Privacy P0、预期用途/功能变更门、全流程无障碍、地区来源未核实就不进UI、许可/分发专项。
拒绝：评分9/10、“绝不外传”、“义务近零”、“MDR/GPSR/CRA/EHDS必然不适用”、仅捐赠形式决定法律范围、历史风险极低直接结案。
延期：新语言/地区药品全文核验/F-Droid上架/医院互通/法国法专项/正式隐私UI，均独立PR，不改本轮正式Schema/加密/PNBAK或版本策略。

## Sources

EU legislation：[GDPR](https://eur-lex.europa.eu/legal-content/EN/TXT/?uri=CELEX:32016R0679)、[MDR](https://eur-lex.europa.eu/legal-content/EN/TXT/?uri=CELEX:32017R0745)、[CRA](https://eur-lex.europa.eu/legal-content/EN/TXT/?uri=CELEX:32024R2847)、[GPSR](https://eur-lex.europa.eu/legal-content/EN/TXT/?uri=CELEX:32023R0988)、[EAA](https://eur-lex.europa.eu/eli/dir/2019/882/oj)、[EHDS](https://eur-lex.europa.eu/legal-content/EN/TXT/?uri=CELEX:32025R0327)。条款适用仍待具体经营事实/专业复核。
官方指导：[MDCG入口](https://health.ec.europa.eu/latest-updates/update-mdcg-2019-11-rev1-qualification-and-classification-software-regulation-eu-2017745-and-2025-06-17_en)、[CRA委员会摘要](https://digital-strategy.ec.europa.eu/en/policies/cra-summary)；平台技术：[Android SAF](https://developer.android.com/guide/topics/providers/document-provider)、上述F-Droid指南。查询日期2026-10-14。
