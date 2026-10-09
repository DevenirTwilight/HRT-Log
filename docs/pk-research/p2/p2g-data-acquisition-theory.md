# P2-G：数据获取路线、来源审计与进入药代建模前的科学门槛

日期 2026-10-09。**阶段目标不是增加药代参数或发布新模型，而是取得能在样本、时间和单位上准确对应的真实舌下 E2 数据。** 本附录依据可查原论文及现有P2-A至P2-F，保留所有限制。新增 registry 和审计工具只使用公开来源元数据，不导入任何人的用药/化验记录。

## 1. 为何这是下一个最小有价值的研究单元

旧 P2-A/B 有339组受限情景，Doll 1mg/1h单个目标只能确定幅度，不能唯一识别峰时、尾部、实际黏膜份额或清除。P2-D/E 借 Komesaroff / Rosano 的群体早期点暴露零延迟核形状局限，并用Price单剂扣基线的AUC0–24指出模型尾部差异。然而**已被研究者看到的数据**不能事后重新当作盲化外部集；分析法、给药前基线、剂型、队列重用使其不能直接合并成完整人体时间序列。因此优先寻求可合法使用的原始**按时间的汇总浓度和实验设计**，然后才考虑是否增加受限lag/transit结构。

## 2. 按来源核查：已公开什么，还没有什么

### A. Doll et al. 2022 — 首要优先

来源：[PubMed](https://pubmed.ncbi.nlm.nih.gov/34781041/)；[出版社原文页面](https://www.sciencedirect.com/science/article/pii/S1530891X21013744)。DOI 10.1016/j.eprac.2021.11.081；研究 NCT04036500。10名跨性别女性在1mg口服后1周接受1mg舌下；采样0/1/2/3/4/6/8小时，并进行LC-MS/MS及免疫分析。摘要报告LC-MS/MS舌下E2的采样峰值144pg/mL@1h、舌下与口服AUC0–8相对比1.8。**相对比1.8不是作者公布的舌下绝对AUC**；不得用旧口服模型生成的绝对 AUC 冒充人体测量。

此次核对PubMed、出版社页面、2020会议摘要（N5属于最终N10队列前期，不独立）、其他摘要索引；未取得可直接导入研究引擎的合法完整7时点真实数值表。全文状态：摘要和部分出版社页面可核对；**未证实**有公开可授权的逐人原始数据，也不能仅因目前找不到就断言数据不存在。PubMed明确列通讯作者 **Jenna L. Sarvaideo，Jsarvaideo@mcw.edu**，这是公开科学通讯联系方式，而非对私密记录的访问权。

**先请求最少且更易获准的非个人表格**：
- 舌下1mg与口服1mg分别0/1/2/3/4/6/8h E2 LC-MS/MS浓度 mean/SD/SE/n（包括前剂基线）、可公开的按时点 E1/E2 汇总及检出限/低于LLOQ如何处理。
- AUC0–8的绝对数值、是否扣基线和算法、Cmax是平均曲线最大值还是各人峰值统计、实际采样时刻偏差和溶解/吞咽规则。
- 数据共享、版权/学术引用及能否仅在开源研究报告发布合法派生统计的许可。若仅允许私下科研查看，不将原始表/个人数据提交公开GitHub。
- 只有伦理/同意允许时才讨论去标识的逐人浓度—时间和同人交叉协方差，不请求身份信息或可回识别数据。

### B. TransPrEP — 首要优先，但先拆途径

原始全文：[Yager et al. 2022](https://pmc.ncbi.nlm.nih.gov/articles/PMC9910105/), PMID35815468, DOI10.1089/AID.2022.0044, NCT03652623；[同一数据的机构群体PK再分析](https://digitalcollections.cuanschutz.edu/works/publication-dissertation/3ygcx-1ac37)，Abdelmawla 2023, DOI10.25677/3ygcx-1ac37。入组TW n26、完成用于激素PK分析n25，口服/舌下**合并**为13人，肌注12人；0/1/2/4/6/8/24h计划采样且记录实际时刻；已有慢性稳定GAHT，检测LC-MS/MS。

公开合并组AUC_last 1531 pg·h/mL、Cmax191 pg/mL（PrEP前几何均值）是**持续治疗、PO/SL混合、总浓度**，**不能等同于Price单剂扣基线 AUC0–24**。2023论文所报28.4h是口服/舌下合并的模型依赖**表观半衰期**，不能当纯SL真实清除常数，且原2022+2023是同一个实验队列不能计为两项外测。

此次从PMC索引确认方法、时点和LC-MS/MS，但页面打开遇到浏览器验证，**未核实原论文有无可下载的合法参与者级附件或明确的数据共享条款**；临床试验登记API本轮也未完整读取，所以不能说“研究方拒绝共享”，只能说“尚未核实存在可直接获取的纯SL逐人数据”。

建议联系论文通讯研究团队，先询问：
1. 13人中纯舌下/纯口服各多少人？使用的17β-E2或酯、给药剂量、分剂时间、末次和更早给药历史、是否能判断基线与真实谷浓度。
2. 能否提供**以纯舌下分组**的去标识时点汇总（每时点人数、均值/几何均值、离散度、采样实际偏差、给药频次），并明确区分有无PrEP、每次给药剂量和分析物。
3. 能否授权研究用途的去标识原始表或可公开的汇总统计？如果涉及未成年人/敏感研究，尊重IRB及最小披露要求；只接受合法许可且不可回识别的材料。
4. 如论文有模型诊断图与数据字典，可先分享合法的非个人级资料，优先区分结构、观测和背景。

### C. Price et al. 1997 — 继续核对正式 Table1 和采样时间均值

来源：[PubMed](https://pubmed.ncbi.nlm.nih.gov/9052581/)；[出版社原始页](https://www.sciencedirect.com/science/article/pii/S0029784496005133)。按本项目P2-E从非官方可读全文转录核对：同六名绝经后女性交叉接受0.25/0.5/1mg舌下；E2采用提取后RIA；0/1/2/3/4/6/8/12/18/24h采样。1mg舌下 baseline-subtracted trapezoid AUC0–24 **2109±1031 pg·h/mL (SD)**。若得到官方正版PDF应重新对照 E1/E2 两列和统计口径。

优先索取分剂量的**真实采样均值及SD/SE/n、每位给药前基线、AUC估计的个体梯形规则**；未获权不得重发布全文、版权表格或个体数据。要明确三个剂量源于同六人，不能按独立n18验收。不同于Doll的LC-MS/MS，不允许没有证据就把二者合成同一误差正态分布。

### D. Rosano/Komesaroff — 获取早期配对样本与检测依据

Rosano n25的10/20/40/60min均值（234/468/1980/2124pmol/L）虽可用于单gamma核的结构数学反证，但该25人治疗前基线及实际检测法尚有缺口，不能借同篇冠心病干预n9组的基线。Komesaroff n10 的0/15/30min RIA mean±SEM 已核对，15min SEM很大；二者均缺个体协方差。未来请求**非个人化早期配对协方差或每时点均值/方差与真实基线**优先于拟合多级transit生理份额。

## 3. 收到数据之前：必须进行来源和许可验收

机器研究目录：[p2g-data-acquisition-registry.json](p2g-data-acquisition-registry.json)。同NCT03652623的Yager与Abdelmawla只算**一个队列**。这份目录存最少可核实文献指标、缺失状态、数据权利状态，特意没有虚构个人血药时间序列。

候选未来外部数据进入科学评估之前，必须检查：
- 数据来源是否有正确DOI/试验登记/真实表格页码/版本，以及实际授权用途；真实受试者级数据不得存公开仓库、CI或聊天。
- 准确的舌下途径、剂型/药物成分、准确给药时刻与剂量历史、精确采样时刻、E2分析物和检测法、基线、误差类型和每点n。统计均值不能当一名虚拟受试者。
- 队列ID与既有论文是否重用、这份数据是否已经被本项目看过或用于调参。只有**收到独立未知新数据前**明确冻结的协议与独立性，才可能进入新的锁定外部验证。
- 外部数据的必要信息不足时，应该保留缺失并限制到定性/条件情景，不能自动插值、补0、填服药时刻或声称临床预测区间有真实90%覆盖率。
- 即使未来数据满足行政准入，也只是可以接受**另案科学外测审查**，绝不自动准许P2-C正式模型替换。

为避免把“已有论文表格”和“真正可检验曲线”混为一谈，新增一个零真实个体数据的检查程序：

    python3 -m unittest discover -s tools/pk-research -p 'test_p2g_readiness.py'
    python3 tools/pk-research/p2g_readiness.py --out /tmp/hrt-p2g-new-report.json

程序读取公开来源元数据，输出不同论文相同队列去重、可用外测研究数为0和假设数据进入外测前所缺条件。测试使用**合成输入**，包括错误把口服／舌下混合组当纯SL、未取得许可、重复队列、未锁定协议、未知基线或检测、摘要伪造时序等拒绝情景。

## 4. 可直接发送给研究者的英文询问稿（仅草稿，不发送）

Doll / Sarvaideo 研究团队（公开通讯邮箱来自PubMed）：

Subject: Research request — time-resolved estradiol summary data from sublingual/oral PK study (Doll et al., 2022)

Dear Dr. Sarvaideo,

I am reviewing published sublingual estradiol pharmacokinetic evidence for a nonclinical, open-source modeling research project, including your 2022 Endocrine Practice study (DOI: 10.1016/j.eprac.2021.11.081). I would like to ask whether a de-identified aggregate table can be shared for the 0, 1, 2, 3, 4, 6, and 8-hour E2 LC-MS/MS measurements in the oral and sublingual arms, with the corresponding n, dispersion type, predose baseline, and any reported absolute AUC0–8 values. Information on dose timing, tablet dissolution/hold instructions, assay limits, and how AUC and Cmax were calculated would also help us avoid inappropriate model assumptions.

Aggregate information would be more than sufficient for an initial methodological assessment. Please let me know whether there are sharing, citation, or redistribution restrictions; no identifiable participant information is requested. Thank you for considering this request.

Kind regards,
[researcher's name]

TransPrEP / Yager research team：

Subject: Data availability inquiry — estradiol oral-versus-sublingual PK subgroup (TransPrEP, NCT03652623)

Dear TransPrEP research team,

I am conducting a nonclinical methodological review of published sublingual estradiol concentration models. Your 2022 article (DOI: 10.1089/AID.2022.0044) and the related 2023 population PK thesis use valuable LC-MS/MS data. Since the published oral/sublingual group combines routes, may I ask whether separate, non-identifying group-level E2 concentration summaries are available for the sublingual participants, aligned to their exact or nominal 0, 1, 2, 4, 6, 8, and 24-hour sampling times, with n and dispersion at each time point?

Details on the sublingual subgroup's dose schedules, timing of earlier doses, formulation, predose concentration interpretation, and applicable study/data-sharing permissions would be especially useful. I am not requesting identifying or re-identifiable participant records, and would respect all consent and IRB restrictions. Please let me know whether an aggregate table, data dictionary, or approved summary can be made available and what reuse restrictions apply.

Kind regards,
[researcher's name]

**这些是仓库研究草稿，不是已发送邮件，不是作者已同意共享的证明。**

## 5. 下轮真正可实施的决策门槛

- **拿到Doll合法的七时点汇总**：优先对**该研究内**的时间形状、不同测定法、实际绝对AUC进行受限拟合，承认Doll属于已经用过的TRAIN，不能将新增Doll时序用作完全独立的外部验证；再寻找独立holdout。
- **拿到TransPrEP分途径、分时间点**：分清口服与SL和慢性给药，做重复输入/间隔一致性结构检查；不能把相同NCT队列硕士论文当额外n。
- **两者都没有**：不提高模型自由度，不用Price/Rosano已见均值强行选赢家，只记录缺口和具体取证努力。
- **即使获得额外时序**：需要另订科学验证协议；参数可辨识性、异质检测法、样本独立性和临床不确定性区间覆盖仍单独评估。

正式HRT模型、LabFit个人校准、冻结历史、Schema、APK交付、GitHub Release及旧P2协议/文件都保持不变，下一轮仅提交 docs/tools 科学研究资料。**研究工程完成 ≠ 真实人体精度验证**。
