# 身心状态重新设计：调研与核实记录

核实日期：2026-10-07。范围与规则见 `REQUIREMENTS.md` 第 15、15a 节。

- **已核实**：本次实际打开原文并逐字摘录，写明原文位置。
- **部分核实**：只打开了部分原文，或来源有疑点（写明哪里没核实）。
- **未核实**：没能打开权威原文，或不满足来源规则。**未核实的内容不得用于界面。**

引文保留原文语言。原始下载文件只在会话临时目录中，未入库；需要逐字符复制时应重新从原文复制。

---

## 1. 结论摘要

1. 三份指南（法国 HAS 2025、WPATH SOC8、美国内分泌学会 2017）的监测内容都是**写给医生的**，都**没有面向患者的自测清单**（已核实，HAS 另出的 fiches 未查）。应用里的记录项只能说"依据指南中医生评估的方面整理"，不能说"指南提供的清单"。
2. 阶段记录可以按 HAS R40 的四个方面组织：耐受、效果、风险因素、满意度（已核实，专家共识级别）。
3. 女性化效果时间表有**两个版本**：内分泌学会 2017 Table 13 与 SOC8 附录 C 表 1（改编自前者，数值不同）。界面必须注明引用的是哪一版。
4. 药物警示症状：法国说明书、ANSM、台湾卫福部、大陆说明书（第三方转载）中，**最强措辞是"立即"（immédiatement / 立即），没有任何来源提到急救电话或急诊**。
5. 螺内酯：法国说明书和大陆说明书（第三方转载）都**没有"出现某症状请就医"的面向患者的清单**。
6. 中国大陆没有官方或学会的跨性别激素治疗指南；高绿芬等 2022 综述原文写国内专家共识及指南"仍然空白"（只作中文参考文献）。
7. GENDER-Q 需要许可，GCLS 对嵌入应用、翻译、修改没有明确授权，**两者都不复制任何题目**；PHQ-9、GAD-7 是计分筛查量表，不采用。
8. 大陆个人上报药品不良反应**没有面向个人的在线入口**；法规规定可向经治医师、药品生产或经营企业、当地监测机构报告。

---

## 2. 指南

### 2.1 法国 HAS 2025《Transidentité : prise en charge de l'adulte》—— 已核实

- 来源：https://www.has-sante.fr/upload/docs/application/pdf/2025-07/transidentite_prise_en_charge_de_ladulte_-_recommandations.pdf （2025 年 7 月 17 日学院通过，41 页；页码与印刷页脚一致）
- 人群：成年跨性别者。以下条目证据等级均为 **AE（accord d'experts，专家共识）**，不是研究证据分级——主要局限。

| 条目 | 位置 | 原文（节选） |
|---|---|---|
| R40 | 第 21 页，§4.2.4 | « le bilan clinique de surveillance … sera fait à 3 mois puis à une fréquence adaptée au cas par cas jusqu'à ce que le dosage soit dans les valeurs de référence puis une fois par an. Il consistera en un entretien clinique pour vérifier la tolérance (signes de sur et sous-dosages hormonaux, effets indésirables), l'efficacité du traitement, les facteurs de risque et la satisfaction de la personne (AE). » |
| R41 | 第 21 页 | 化验：雌二醇、睾酮；« en cas d'utilisation de la spironolactone : dosage de créatininémie et ionogramme sanguin »；达标后 « puis si besoin »。**R41 不含血压。** |
| R45 | 第 21 页 | « L'évaluation de la tolérance clinique est à considérer au même titre que les dosages sanguins. » |
| R32 | 第 19 页，§4.2.3.2 | 螺内酯：« sous réserve d'une surveillance de la pression artérielle, ionogramme sanguin et de la créatininémie »。Tableau 4（第 20 页）螺内酯行写 « Fonction rénale, potassium »。 |
| R37 / R38 | 第 20 页 | CPA 50–100 mg/j 因脑膜瘤风险不作首选；« En dernière intention » 可短期低剂量使用。 |
| Tableau 4 | 第 21 页 | CPA 行：« Prolactinémie, IRM cérébrale avant l'initiation et à 5 ans puis tous les 2 ans »（MRI 时间表在表格中，**不是编号推荐**；表格写 « En 2e intention »，与 R38 « dernière intention » 措辞不一致，照录）。 |
| R49 | 第 22 页 | 出现头痛、溢乳或视觉障碍时查泌乳素。 |
| 患者自评清单 | 全文检索 | 推荐 PDF 中没有；HAS 另出的 fiches / argumentaire **未查**。 |

注：R40 中"稳定后每年一次"的"稳定"原文指"化验值进入参考范围"。

### 2.2 WPATH SOC8（Coleman et al. 2022，Int J Transgend Health 23(Suppl 1)，DOI 10.1080/26895269.2022.2100644）第 12 章 —— 已核实（页码未核实）

- 来源：Europe PMC 全文 XML（PMC9553112）。出版社 PDF 返回 403，**无 S 页码**，只给推荐编号和表号。
- 人群：跨性别与性别多元的青少年和成人；推荐对象是 health care professionals。
- **12.9**（监测）：第一年及调整剂量期间每 3 个月化验性激素，达到稳定维持剂量后每年 1–2 次临床与化验；正文自承 « there is no strong evidence … supporting specific testing intervals »。正文还要求评估 « the impact of treatment on gender dysphoria (if present) and psychological well-being »。
- **12.12**：应告知效果出现的时间进程。
- **12.16**：CPA 与脑膜瘤、高泌乳素血症相关；« a decision was made not to make a recommendation for or against monitoring prolactin levels »；**没有 MRI 时间表**。
- **附录 C 表 1**「Expected time course of physical changes…」（« Adapted from Hembree et al., 2017 »），雌激素方案（效果 | 开始 | 最大）：

| 效果 | 开始 | 最大 |
|---|---|---|
| Redistribution of body fat | 3–6 months | 2–5 years |
| Decrease in muscle mass and strength | 3–6 months | 1–2 years |
| Softening of skin/decreased oiliness | 3–6 months | Unknown |
| Decreased sexual desire | 1–3 months | Unknown |
| Decreased spontaneous erections | 1–3 months | 3–6 months |
| Decreased sperm production | Unknown | 2 years |
| Breast growth | 3–6 months | 2–5 years |
| Decreased testicular volume | 3–6 months | Variable |
| Decreased terminal hair growth | 6–12 months | > 3 years |
| Increased scalp hair | Variable | Variable |
| Voice changes | None | — |

- **附录 C 表 5**（监测，改编自内分泌学会）：第一年约每 3 个月、之后每年 1–2 次；用螺内酯监测电解质（尤其钾）和肾功能（尤其肌酐）；**不含泌乳素**。
- 附录 C 表 2（风险）：雌激素方案 "Likely increased risk" 含静脉血栓栓塞等（XML 丢失了加粗标记，无法确认哪些为"临床显著"）。

### 2.3 美国内分泌学会 2017（Hembree et al., J Clin Endocrinol Metab 102(11):3869–3903, doi 10.1210/jc.2017-01658）—— 已核实（第三方托管的出版版 PDF 副本）

- 来源：Wesleyan 大学托管的出版版 PDF 副本（每页带 OUP 下载水印与期刊页码）。OUP 官网 403，**未与官网逐字比对**。
- **Table 13**（第 3889 页）Feminizing Effects in Transgender Females（开始 | 最大）：体脂重新分布 3–6 mo | 2–3 y；肌肉量和力量下降 3–6 mo | 1–2 y；皮肤变软/出油减少 3–6 mo | Unknown；性欲下降 1–3 mo | 3–6 mo；自发勃起减少 1–3 mo | 3–6 mo；男性性功能障碍 Variable | Variable；乳房发育 3–6 mo | 2–3 y；睾丸体积减小 3–6 mo | 2–3 y；精子生成减少 Unknown | >3 y；终毛生长减少 6–12 mo | >3 y；头发 Variable | —；声音 None | —。表注：数值为 « clinical observations »（三个队列）。
- **与 SOC8 表 1 的差异**：SOC8 把体脂、乳房的最大效果改为 2–5 years，性欲下降最大改为 Unknown，睾丸体积改为 Variable，精子生成改为 2 years，删去"男性性功能障碍"行，头发改为 "Increased scalp hair | Variable | Variable"。
- **Table 15**（第 3890 页，**不是 Table 14**；Table 14 是跨性别男性）：第一年每 3 个月、之后每年 1–2 次；每 3 个月测睾酮、雌二醇；用螺内酯第一年每 3 个月、之后每年查电解质（尤其钾）。**不含肌酐、不含泌乳素。**
- **推荐 4.2**（第 3890 页）：建议定期监测泌乳素；正文 « at baseline and then at least annually during the transition period and every 2 years thereafter »。
- 推荐 4.1 正文：医生应监测体重、血压，评估吸烟、抑郁症状及深静脉血栓/肺栓塞等不良事件风险。
- 证据等级符号在抽取文本中为乱码，**具体等级未核实**。

### 2.4 患者自评清单 —— 已核实：三份指南都没有

SOC8 第 12 章的推荐主语都是 health care professionals；内分泌学会 Table 15 与 4.1 写给 clinician；HAS R40 是医生做的 « entretien clinique »。

### 2.5 中文参考文献：高绿芬等 2022 —— 已核实（期刊原文 PDF）

- 高绿芬、高琳芝、谢杏美、谢丽姗、宋羽葳、赖顺凯、贾艳滨、王晓玉.《跨性别女性性别确认激素治疗策略》（综述）. 妇产与遗传（电子版）, 2022, 12(4): 37–45. DOI 10.3868/j.issn.2095-1558.2022.04.007（ISTIC 注册，doi.org 无法解析；请用期刊官网链接 https://cjournal.hep.com.cn/2095-1558/CN/10.3868/j.issn.2095-1558.2022.04.007 ，该页挂错了摘要，以 PDF 为准）。单位：暨南大学附属第一医院等。
- 摘要（第 37 页）：« 跨性别女性性别确认激素治疗相关的专家共识及指南性文献在我国仍然空白。»（英文摘要写 "still rare"，比中文弱。）
- 监测（第 41 页"五、临床监测"）：« 建议在开始使用激素治疗的前两年，应每 3 ~ 6 个月监测患者一次，2 年后每 6 ~ 12 个月监测一次……主要监测雌二醇和睾酮浓度，其他项目包括肾功能、电解质、癌症筛查及骨密度测量等 »；« 对于服用螺内酯的跨性别女性，建议在前 2 年每 3～4 个月测量一次血清钾浓度，然后每年测量一次 »。
- 局限：综述，不是指南或共识，监测频率转引自其他文献；文中含剂量与雌二醇数值阈值，本应用**不引用**剂量和阈值。**只能写作"中文参考文献"，不得称"中国指南"。**

---

## 3. 药物警示症状（逐字来源）

**共同发现：所有来源的最强措辞是"立即"；没有一个来源提到 15、SAMU、urgences、急诊或任何急救电话。**

### 3.1 雌二醇（法国说明书，BDPM 患者 notice rubrique 2 « Avertissements et précautions »）—— 已核实

| 产品 | CIS | notice 更新 | 链接 |
|---|---|---|---|
| PROVAMES 2 mg（片） | 65421374 | 05/12/2023 | https://base-donnees-publique.medicaments.gouv.fr/medicament/65421374/extrait#tab-notice |
| OESTRODOSE 0,06 %（凝胶） | 63071975 | 14/08/2026 | …/medicament/63071975/extrait#tab-notice |
| ESTREVA 0,1 %（凝胶） | 63216075 | 06/07/2026 | …/medicament/63216075/extrait#tab-notice |
| DERMESTRIL 50（贴片） | 65662980 | 11/07/2025 | …/medicament/65662980/extrait#tab-notice |
| PROGYNOVA 2 mg（戊酸雌二醇；**已归档、法国不再销售**） | 60940721 | 30/05/2024 | …/medicament/60940721/extrait#tab-notice |

五份 notice 的共同段落（以 OESTRODOSE 为例）：« Arrêtez de prendre … et **prévenez immédiatement votre médecin** si vous notez l'apparition des signes suivants … : une des pathologies signalées en rubrique « N'utilisez jamais … » ; un jaunissement de votre peau ou du blanc de vos yeux … ; un gonflement du visage, de la langue et/ou de la gorge, et/ou des difficultés à déglutir ou une urticaire accompagnée de difficultés à respirer … ; une augmentation importante de votre pression artérielle (les symptômes peuvent être mal de tête, fatigue, sensations vertigineuses) ; des maux de tête tels qu'une migraine, qui apparaissent pour la première fois ; si vous devenez enceinte ; si vous remarquez des signes possibles d'un caillot sanguin, tels que : Gonflement douloureux dans vos jambes, Douleur brutale à la poitrine, Difficulté à respirer. »

- 各产品措辞有细微差别（例如 PROVAMES、PROGYNOVA 写 « gonflement douloureux et rougeur au niveau des jambes »；PROGYNOVA 另列过敏反应）。界面按产品分别逐字引用。
- RCP 4.4（OESTRODOSE、ESTREVA、DERMESTRIL、PROGYNOVA）：血栓症状须 « contacter immédiatement leur médecin »。
- 局限：notice 末尾修订日期均为 « [à compléter ultérieurement par le titulaire] »。OESTROGEL 在数据库中无独立条目（OESTRODOSE notice 第 5 节标题残留 OESTROGEL 名称，推断为改名，未核实）。PROVAMES 的 « Saignements inattendus » 列表原文后面没有就医结论句，**不得引用为"需就医"**。

### 3.2 戊酸雌二醇 / 雌二醇（大陆说明书，第三方转载）

按 15a 第 4 条规则（第三方转载须至少两站内容一致，并有核准日期）：

- **补佳乐 戊酸雌二醇片（Bayer）—— 已核实（第三方转载，仅限下列段落）**。核准日期 2006-10-13（三站一致）；最新修改日期各站不同：药智网 2023-05-23（https://db.yaozh.com/instruct/2679625942310912.html ）、某医院用药知识库 2025-05-30（swin.zy91.com）、tfsci.mtf.wiki 2021-12-28（社区站点）。即使不计 tfsci，药智网与医院知识库两站也满足规则。三个修订版中逐字一致的段落：
  - 【注意事项】« 如果患者有禁忌症或出现以下状况，应立即停止治疗：-黄疸或肝功能恶化 -严重的血压升高 -新发的偏头痛型头痛 -急性视觉障碍或其他损伤 -妊娠。»
  - 【注意事项】« 如果患者出现血栓栓塞的可能症状(尤其是腿部痛性肿胀、突发性胸痛、呼吸急促)，必须立即联系医生。»
  - 【注意事项】« 必须告知女性患者，出现哪些乳房改变时必须报告医师或护士 »
  - 另有一个**无核准日期**的旧版（国药准字 J20030089，内容不同），**不得使用**。
- **爱斯妥 雌二醇凝胶（Besins）—— 已核实（第三方转载，仅限下列段落）**。核准日期 2007-10-17（药智网说明书实物照片与 tfsci 两处独立显示）；四站正文一致（药智网 https://db.yaozh.com/instruct/35999.html 、39药品通、药源网、tfsci）。【不良反应】« 为慎重起见，若出现下列任何一种情况，最好停止用药：心血管或血栓栓塞性意外；胆汁郁积性黄疸；良性乳房疾病，子宫肿瘤（例如：纤维瘤体积增加）；肝腺瘤：可增加腹腔出血的危险性；乳头溢液：若出现此症状，应检查是否有垂体腺瘤。» **措辞是"最好停止用药"，没有"立即"。** 另有"请向医生咨询/报告"的段落（不良反应、大量或不规则出血、药物过量症状）。
- 国产雌二醇凝胶（叶开泰 H20051153）—— 未核实（找不到说明书全文）。

### 3.3 醋酸环丙孕酮（CPA）

- **ANSM 问答（2022-12-01）—— 已核实**：https://ansm.sante.fr/dossiers-thematiques/androcur-et-risque-de-meningiome/questions-reponses
  - 症状（« Quels sont les symptômes d'un méningiome ? »，原文注明 « liste non exhaustive »）：« maux de tête fréquents, troubles de l'audition, vertiges, troubles de la mémoire, troubles du langage, faiblesse, paralysie, troubles de la vision, perte d'odorat, convulsions, nausées... »；有症状时 « consultez votre médecin qui vous prescrira une IRM cérébrale de contrôle »，IRM « dès que possible »。**没有"立即"。**
  - **法国的规定**：IRM « en début de traitement, à renouveler dans les 5 ans, puis tous les 2 ans tant que l'IRM est normale et que le traitement est poursuivi »；治疗超过一年后每年签署 « attestation d'information »，配药时出示。
- **ANDROCUR 50 mg 法国说明书（CIS 61255738，notice 05/11/2024）—— 已核实**：
  - 脑膜瘤：« Si vous remarquez des symptômes tels que des troubles de la vision (par exemple une vision double ou floue), une perte d'audition ou un sifflement dans les oreilles, une perte d'odorat, des maux de tête qui s'aggravent au fil du temps, des pertes de mémoire, des crises convulsives, une faiblesse dans les bras ou les jambes, vous devez en informer **immédiatement** votre médecin. »
  - 肝毒性：« Arrêtez votre traitement et consultez **immédiatement** votre médecin si vous présentez des symptômes de toxicité hépatique (ex : démangeaisons sur le corps entier, jaunissement de la peau, urines foncées, douleurs abdominales, troubles digestifs). »
  - « Arrêtez le traitement et prévenez **immédiatement** votre médecin en cas de : Jaunisse …, démangeaisons sur le corps entier. Douleur inhabituelle dans les jambes, faiblesse dans les membres. Douleur de la poitrine, pouls irrégulier, essoufflement soudain. Perte de connaissance, confusion, maux de tête sévères inhabituels, vertiges, troubles visuels, élocution ralentie ou perte de la parole. »
  - 仿制药 ARROW 50 mg（CIS 67552319）notice **未打开**。
- **台湾卫福部 2022-09-15 —— 已核实**：https://mohw.gov.tw/cp-16-71612-1.html « 提醒病人若出現視力變化、聽力喪失或耳鳴、嗅覺喪失、隨時間惡化之頭痛、記憶力喪失、癲癇發作或四肢無力等不適症狀應儘速回診。» 措辞"儘速"，不是"立即"。
- **台湾卫福部 2013-01-31 —— 已核实，仅作参考**：https://mohw.gov.tw/cp-16-23729-1.html 针对 **cyproterone 与 ethinyloestradiol 复方**（原文未提任何商品名）：« 血栓相關前兆症狀，如偏頭痛、腿部疼痛或腫脹、胸部突然劇痛、突然呼吸困難、突然咳嗽、任何不尋常或持續頭痛，如發現前述症狀時，應儘速回診原處方醫師。» 不适用于单方 CPA，界面不用于单方 CPA。
- 大陆单方 CPA（华典，湖北葛店人福 H20056637）—— **未核实**：两站核准日期矛盾（2005-07-26 / 2006-11-02），版本旧（2009），且所有大陆版本都没有脑膜瘤内容。其他国产单方 CPA、Androcur 大陆注册 —— 未核实。达英-35（复方）—— 未核实。

### 3.4 螺内酯

- **ALDACTONE 50 mg 法国说明书（CIS 67368301，notice 12/01/2026）—— 已核实**：notice 只写 « Les symptômes d'une hyperkaliémie sévère peuvent inclure : crampes musculaires, rythme cardiaque irrégulier, diarrhée, nausées, sensations vertigineuses ou maux de tête. »，**没有附带就医或停药指示**；rubrique 4 也没有。→ 界面显示"官方来源未列出"（15a 第 3 条）。25 mg / 75 mg notice 未打开。
- 大陆：上海信谊 H31021273 —— 部分核实（39药品通与健客两站内容一致，但核准日期只有一站显示，版本 2010 年）；说明书只有面向医生的 « 用药期间如出现高钾血症，应立即停药 »，**没有面向患者的症状清单**。杭州民生、浙江亚太 —— 未核实。对界面无影响：仍显示"官方来源未列出"。
- 官方监测建议：HAS R32（血压、电解质、肌酐），只对法国地区显示（15a 第 3 条）。

### 3.5 孕酮、比卡鲁胺、其他与自定义药物

本次未调研官方症状来源 → 界面显示"无官方资料"（不加评判）。

---

## 4. 地区背景

- **大陆《药品网络销售禁止清单（第一版）》（2022 年第 111 号公告，2022-12-01 施行）—— 已核实**：附件原件 https://www.nmpa.gov.cn/directory/web/nmpa/images/1669813317575030937.doc 。"二、其他禁止通过网络零售的药品（四）"列有 « 环丙孕酮 » 和 « 雌二醇 »；备注 « 所列品种为通用名，限于单方制剂 »。**"戊酸雌二醇"未出现**，原文没有说明酯类是否包括在内，不作推断。限制的是网络零售。公告正文网页 412，正文核对的是中国食品药品国际交流中心转载页。
- **medRxiv 10.1101/2022.10.05.22280725 —— 部分核实**：Zeng et al.（厦门大学）"Hormone overdose and misuse in Chinese transgender and gender non-conforming population: A mixed-methods study protocol"，预印本，**是研究方案，没有实际样本**。"most Chinese TGNCs have to use hormones themselves without professional supervision" 是引言中的论断，**没有数字或引文**。→ 不得写成研究发现；**不用于界面**。其中提到的 2017 年北京同志中心调查（2060 人，约三分之一有激素治疗经历）未核实。高绿芬 2022 引言另有 « 许多跨性别者经非正规途径获取药物 »（综述论断）。
- **大陆个人上报药品不良反应 —— 已核实**：《药品不良反应报告和监测管理办法》（卫生部令第 81 号）第二十三条：« 个人发现新的或者严重的药品不良反应，可以向经治医师报告，也可以向药品生产、经营企业或者当地的药品不良反应监测机构报告 »（https://www.gov.cn/gongbao/content/2011/content_2004739.htm ）。国家中心网站只有企业和医疗机构入口；NMPA 2018 年第 131 号通告附件：« 药品说明书、标签、持有人门户网站公布的联系电话是患者报告不良反应……的重要途径 »。12315 是投诉举报热线，不是不良反应上报渠道。
- **台湾 —— 已核实**：食藥署 https://www.fda.gov.tw/TC/sitecontent.aspx?sid=4240 « 民眾亦可主動通報 »，電話 02-23960100，入口 adr.fda.gov.tw。
- **法国 —— 已核实**：ANSM 患者页 https://ansm.sante.fr/documents/reference/declarer-un-effet-indesirable/comment-declarer-si-vous-etes-patient-ou-usager ：« Utilisez le portail de signalement des effets indésirables : signalement.social-sante.gouv.fr »。
- 急救电话（120、999、15、112 已核实，119 部分核实）——**按 15a 第 2 条不进界面**，此处只留记录。

---

## 5. 量表授权（详见 `docs/licensing.md`）

- **GENDER-Q —— 已核实**：© 2024 McMaster University and Brigham and Women's Hospital（https://qportfolio.org/copyright-information/ ）。非营利研究和临床免费但须申请；« providers of electronic platforms for PROMs administration » 列为商业许可对象。开发论文：Kaur et al., JAMA Netw Open 2025;8(4):e254708. → **候选，未获授权，不复制题目。**
- **GCLS —— 已核实**：Jones et al., Int J Transgend 2019;20(1):63–80, DOI 10.1080/15532739.2018.1453425；附录 « The GCLS is freely available for use »，翻译须联系作者；文章为 CC BY-NC-ND 4.0；嵌入应用、修改未说明。→ **候选，未获授权，不复制题目。**
- **PHQ-9 / GAD-7 —— 已核实**：均为计分筛查量表（Kroenke 2001；Spitzer 2006），PHQ-9 第 9 题涉及自伤念头。→ **不采用。**

---

## 6. 未核实与待补

- HAS 另出的 fiches / argumentaire（是否有患者材料）。
- SOC8 页码；内分泌学会 2017 与 OUP 官网逐字比对、证据等级符号。
- 法国：ACETATE DE CYPROTERONE ARROW 50 mg、ALDACTONE 25/75 mg notice。
- 大陆：单方 CPA、达英-35、国产雌二醇凝胶、螺内酯（杭州民生、浙江亚太）说明书；NMPA 是否要求 CPA 说明书增加脑膜瘤内容。可由用户拍摄药盒说明书补充。
- 台湾 119 的全国主管机关页面（只核实到地方消防局；不进界面，影响小）。
- 2017 年北京同志中心跨性别调查原文。
