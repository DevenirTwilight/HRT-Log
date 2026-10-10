# P2-A/B 舌下人口模型研究（已完成隔离研究，未投产）

## P2-R：跨研究人体证据整合及M0/M1/M2审查门槛（2026-10-10）

[完整理论与门槛](p2r-cross-study-evidence-upgrade-gates.md)、[9篇报告对应8队列的来源台账](p2r-cohort-evidence-ledger.json)、[不冒充监管要求的内部准入政策](p2r-upgrade-policy.json)、[fail-closed审查程序](../../../tools/pk-research/p2r_evidence_gate.py)及[14项正反测试](../../../tools/pk-research/test_p2r_evidence_gate.py)。以ICH M15 2026用途/风险/独立验证、M10 2023生物分析方法学、FDA人口PK2022评估原则为参考，但内部两独立holdout/改善10%/退化不超5%均属未来新数据开封前必须审阅冻结的**提议**，非官方数值标准。真实完整独立锁定SL人体PK时序仍为0，Price与Doll等均已见或数据不合格，Yager与硕士论文同队列。脚本永不自动认证模型、人体90%覆盖或批准APK。P1、生产E2_SL、P2冻结历史及签名APK不改。


## P2-Q源头勘误：零慢输入权重下参数去重（2026-10-09）

[新增严谨核查附录](p2q-baseline-canonicalization-audit.md)，既有P2-Q脚本/测试补充严格规范参数键、人工Price基线敏感性及P2-P相同阈值下的双时点重排。0/25/50pg/mL三种人工基线时，P2-O原始75/97/62参数行在数学上为75/88/56条不重复曲线；b25/50的P2-P配对4656/1891改为3828/1540，重新计算最少未区分的≥6h两时点为6+12/6+10/6+10，剩余1006/1951/1248对唯一曲线。三基线场景无共同规范参数，结论只是人工源与网格敏感性。主P2-Q b0的75条及5550有向合成比较不受去重影响；没有新人体数据、Doll单点未被用于定标、正式产线与APK不动。


## P2-Q：未知基线、有效幅度与检测误差的跨研究可辨识性审计（2026-10-09）

[完整科学/数学报告](p2q-unknown-baseline-gain-noise-identifiability.md)、[合成实验配置](p2q-identifiability-design.json)、[结构化核查指标](p2q-summary-checkpoints.json)、[独立可复现程序](../../../tools/pk-research/p2q_identifiability.py)与[13项测试](../../../tools/pk-research/test_p2q_identifiability.py)。严格证明F/V同变比不可辨识、w=0时ks消失、一阶吸收/清除可互换；按P2-O已见75候选做模拟，假定合成b=30pg/mL、1h增量A=150pg/mL、不同假设测定误差。极理想σabs=1/比例2%、仅早期点，已知b/A仍有527/5550个有向模型比较未分辨，未知b/A则1249；加入晚期和给药前点降至294。误差增大至假设绝对15pg/mL/比例25%时5550/5550皆无法区分；**不是临床真实精度或显著性检验**。最简、单transit、双输入合成真值各40次/误差情景：简单惩罚降低某些过拟合，也能错误淘汰真实双输入。无Doll144定标、无人类独立锁定验证、正式PK/隐私/签名APK不变。


## P2-P：新采样时点对长尾辨识的条件性研究（2026-10-09）

[源数据、方法、全面敏感性与验收报告](p2p-conditional-sampling-info.md)、[固定场景配置](p2p-conditional-sampling-design.json)、[双模型条件辨识扫描](../../../tools/pk-research/p2p_conditional_sampling.py)、[10项研究回归](../../../tools/pk-research/test_p2p_conditional_sampling.py)。**不以Doll144作任何幅度定标**，完全复用P2-O的75/97/62已见数学候选；b=0、delta=0.02假设下，24h一时点2775对中2467对不能区分，8h一时点1318对，6+12h两点1006对（其中36对q6谷差仍≥2x），若人为b=25/50，最佳两晚期时点变6+10h。delta不是人类测量噪声/CI，未知实际基线、1h增量、LLOQ会进一步降低可辨识性，不能发个人验血时点建议或正式人体模型预测精度声称。仅研究文件；正式生产模型、P1、历史、P2-A/B、APK都未动。


## P2-O：真正跨研究的长尾辨识性／重复给药歧义（2026-10-09）

[完整来源与数学推导](p2o-tail-structural-nonidentifiability.md)、[11,088组参数网格](p2o-slow-tail-parameter-scan.json)、[标准库扫描/质量守恒AUC](../../../tools/pk-research/p2o_slow_tail_scan.py)及10项测试。按Rosano早期增长与Price人工2/1、4/1条件性指标、明示人工窗口筛选，b=0时75组满足，24h AUC/H1=2.20–3.29h、q6给药前累积增量/H1跨6.89倍；Price假设基线25/50（非实测）分别97/62组。甚至两组早期比率最大相差仅0.83%的参数，q6谷浓度可以相差**4.60倍**，其kslow=.05或.40/h，真实中央消除相同。严格质量守恒AUC∞=1/(ke*h1)排除了图像数值积分伪象。全部属于已见研究后探索性扫描，Doll144不用于定标，P2-C不准投产，历史/签名APK/个人参数完全不变。


## P2-N：快慢双输入与中央室卷积的跨研究候选曲线（2026-10-09）

[P2-N机制推导及跨时间来源比较](p2n-transit-convolution-cross-study.md)、[五条非临床假设曲线和源身份](p2n-transit-convolution-design.json)、[稳定的多级输入-消除卷积程序](../../../tools/pk-research/p2n_transit_convolution.py)、[10项数值与科学状态测试](../../../tools/pk-research/test_p2n_transit_convolution.py)。新关键区别：n级吸收**输入**经中央室消除后C(t)早期∝t^n，而旧直接经验gamma**浓度**早期∝t^(n−1)；不能混淆两种上界。归一化不使用Doll144。六级输入+30%有效慢输入案例的Rosano20/40比≈4.31、Price2h/1h≈0.56、4h/1h≈0.175，提示多输入曲线数学上可以跨时段接近；但离散24h AUC/1h≈2.535h，低于Price图≈3.46h和原Table1≈4.68h，Kom早期比也偏高。模型参数是已见数据后的探索例子，不是生理分室实证、临床预测或投产许可。全部生产模型/签名APK/历史不变。


## P2-M 跨研究曲线（2026-10-09）

新增[完整数学方法与原文来源限制](p2m-cross-study-mechanistic-curves.md)、[固定网格/队列身份](p2m-study-shape-design.json)、[双研究共享形状压力测试](../../../tools/pk-research/p2m_cross_study_shape.py)与10项测试。不再以Doll144作为本轮形状校准点。Rosano与Kom早期群体浓度上升显著陡于HRT/Featherline默认正核；探索性共用gamma结构在未知Rosano基线0→225pmol/L时从n3/k1.25→n8/k8.5跳变，前者1–2h继续增加、后者急速衰减，都难跨到Price人工1–2h曲线。这是不可辨识性和不完全可迁移性的证据，不是新模型赢家或统计上证明已有模型错误。保持旧冻结及全部生产/历史/APK不变。


## P2-L：正规分次给药场景、公平模型压力测试与2025/2026新研究（2026-10-09）

[完整科学推导、文献与局限](p2l-repeated-dose-new-cohorts.md)、[最小来源元数据](p2l-repeated-dose-source-audit.json)、[固定HRT/Featherline q6h数学计算](../../../tools/pk-research/p2l_repeated_dose_audit.py)及9项回归。Yaish2023已见同一人群0.5mg SL四次/日：90min median1721pmol/L[IQR1000–2432]、6月晨间谷mean204.5±63.3pmol/L，不能拿不同统计量计算个体峰谷比。假设30pg/mL基线、严格q6h持续120剂、Featherline80kg（**非真实人群输入**）：HRT低谷/90min总值33.07/96.48pg/mL，Featherline94.83/302.96；人体观察mean55.71/median468.81。Kariyawasam2025全研究286，基线表完整263中38SL，免疫法且排除极早/谷异常观察；E1/E2平均比值6.88≠2340/613。Bar-On2026凝血研究30人15SL，free protein S下降为生物标志物，不等于VTE风险、不是PK人体外测；2024会议为该队列中期，去重。临床独立PK准确性仍不足，P2-C未授权，生产和签名APK不变。


## P2-K 公平比较 HRT Log 和 Featherline（2026-10-09）

用户指出 P2 项目过度依赖最初 Doll 训练点，现以[正反证据对称评估与方法依据](p2k-featherline-hrt-balanced-evidence.md)、[独立可重算公平比较脚本](../../../tools/pk-research/p2k_model_balance.py)和6项测试纠偏。Price1997 同六人三SL剂量已公布的基线扣除AUC上，Featherline 80kg在三个剂量均比HRT当前核更接近；1h浓度也更贴近Price原图。然而 Featherline对Doll 1h≈144预测≈481、Price2–4h可能高估，HRT对Doll是训练点吻合；双方都无法解释Rosano20→40min的4.23倍群体浓度增加。Price Figure1与Table1仍存在来源内未解释差异；图上9点MAE排序会随未经核实的基线假设翻转。**支持性证据须明确承认，但不能称模型临床准确、95%覆盖验证或正式替换已批准。**


## P2-J：Price原图积分及非负基线的代数一致性审查（2026-10-09）

[完整理论和来源审计](p2j-price-figure-baseline-consistency.md)、[标准库复算程序](../../../tools/pk-research/p2j_price_baseline_audit.py)和8项数学测试。沿用P2-I已暴露的人工群体估读，若Figure为同源未扣基线均值，扣除未知非负基线b后AUC0–24=1557.5−23.5b，无法达到Table1的2109；人工较高读数情景即使b=0也只有1760.5，差348.5。**这一结论依赖来源口径和图像估读前提，不能认定论文错误。**旧P2冻结、正式模型及APK保持不变。


## P2-I：Price 原版Figure1 vs Table1 AUC内部一致性（2026-10-09）

新增[完整数值重建与理论分析](p2i-price-figure-reconciliation.md)、[原PDF坐标读数及宽泛手动范围](p2i-price-figure-points.json)、[研究程序](../../../tools/pk-research/p2i_price_figure_audit.py)与8项纯数学回归测试。1mg SL作者Table1基线扣除人均AUC=2109±1031，原图人工约1567.5，Wikimedia同图独立数字化约1557.5 pg·h/mL；两组图像读数是同一个队列且非作者原始记录。手工上下范围1390–1785.5（非置信区间），提示尚未解释的图表一致性问题，不能据此宣布原文错误。不同来源估计量不能混用；正式参数、P1、APK和原P2冻结协议均不变。


## P2-H：已审核上传的原版Price论文和68页TransPrEP建模论文（2026-10-09）

[详细原文核查与双室数学推导](p2h-original-pdfs-half-life-audit.md)、[PDF SHA-256/精确页面/来源状态](p2h-primary-pdf-audit.json)、[独立数值审计](../../../tools/pk-research/p2h_eigenvalue_audit.py)和6项测试。Price1997 Table1及Figure1在原版6页PDF直接确认；Abdelmawla论文已取得68页全文，表明n14初始189观测→n12/168最终观测，两名受试者缺失24h由0h假设补入，研究数据没法区分PO/SL；Table3参数的28.4h恰好等于ln2×(Vc+Vp)/CL，而若额外假设标准两室中央清除系统，其慢根对应50.5h（只是条件数学值，不是临床实测）。Doll这次6页为ScienceDirect网页打印**并非完整研究PDF**。科学来源已升级，但独立外部模型验证仍不足；不改生产、APK或旧冻结研究。


**P2-G公开图纠正：**Price1997和Burnier1981已经存在第三方公开数字化图；图像不是人体原始个体曲线，详情见[p2g访问勘误](p2g-data-acquisition-theory.md)。先审查图/附件，再考虑联系作者。


## P2-G：优先获取合法、独立的人体分时数据（2026-10-09）

[完整来源审计、数学/统计理由、取证优先级和作者询问草稿](p2g-data-acquisition-theory.md)；[可核对的最小研究元数据](p2g-data-acquisition-registry.json)；[许可、共享队列和外测资格核查程序](../../../tools/pk-research/p2g_readiness.py)。重点为Doll LC-MS/MS七时点绝对数据、TransPrEP口服/舌下分组和重复用药史、Price正版时序与基线；未知数据不补造，研究作者联系草稿未发送。Yager与Abdelmawla同NCT03652623只算1个队列，P2-G新增可锁定的人体独立数据仍为0；不启动P2-C，也不改已交付APK。


## P2-F：LC-MS/MS口服/舌下混合队列与单gamma输入可辨识性（2026-10-09）

新增[理论说明、数据限制与数学反例](p2f-transprep-and-transit-theory.md)、[原始来源及共享队列审计](p2f-cohort-and-transit-source.json)、[纯数学复算脚本](../../../tools/pk-research/p2f_transit_audit.py)及5项测试。Yager2022 TransPrEP n13口服/舌下合并组LC-MS/MS，2023 Abdelmawla人口PK论文是同一试验再分析，28.4h是合并途径的**模型依赖表观值**，不是纯舌下真实消除常数。Rosano既有组均值10/20/40/60min给更高阶gamma的严格结构约束，但基线未知令参数不可辨识。既有P2/A/B与正式人口/签名APK不变；外部独立验证仍不足。


## 新增 P2-E：Price 1997 与 Rosano 1997 正文药代数字（2026-10-09）

[完整科学理论、原始值及否决备选方案的解释](p2e-primary-source-upgrade.md)；[最小机器证据](p2e-source-metrics.json)；[可运行源匹配 AUC 和早期上界审计](../../../tools/pk-research/p2e_price_rosano.py)。

Price 1997 n=6 同人交叉，原作者 Table1 E2（均值±SD）1mg SL Cmax451±162 pg/mL、AUC0–24 2109±1031 pg·h/mL。原文 AUC 按0/1/2/3/4/6/8/12/18/24h梯形法且给药前基线已扣除；当前HRT按同网格1mg模型值约367.54，Featherline仅条件80kg约2026.50。Rosano 1997 另外一组 n=25（**并非该论文主要心血管试验 n9+n7**）有1mg SL 的10/20/40/60min E2均值234/468/1980/2124 pmol/L，研究通用统计为 SD；其前剂基线未知，不能借用另一组的数据。40min−2×20min=1044 pmol/L 超过零延迟正一阶核条件性增长限制，协方差未知只能作条件统计敏感性计算。详见理论附录、单位/分析方法与限制。旧 P2-A/B 冻结证据保持只读；新数据已见，不能冒充 LOCKED_EXTERNAL；临床外部验证不足、未投产。


## 2026-10-09：全文新核对的 30min E2 与早期上界

新增 [Komesaroff1998 研究证据、数学定理及四模型条件对照](p2d-komesaroff-onset-theory.md)、[最小原始表格数值](p2d-komesaroff-table1.json)。作者上传全文 p.2314 Table 1：2mg Estrace SL、n=10、RIA、0/15/30min，均值±SEM；30min 1969±302pmol/L。报告群体中心增量30/15≈4.732，而所有正权重、零延迟一阶核或gamma2满足≤2的早期比率上界；统计排除/机制辨识均未成立，不能加入生产延迟参数。附有[可执行独立数学压力检查](../../../tools/pk-research/onset_bound.py)及四项测试。冻结P2-A/B、模型参数和P1各工程修复保持不变，外部独立验证仍不足。


## P2-D 后续原始人体证据与理论解释（2026-10-09；研究附录）

新增 [P2-D 理论与证据详解](p2d-evidence-theory.md)：Fiet 1982、Hoon 1993、Fridriksdóttir 1996、Komesaroff 1998、Fisman 1999，以及 NCT05428215 试验登记的原始摘要/注册事实；逐项说明固定采样点、基线、制剂差异、剂量线性外推、有效双途径参数不可辨识性和潜在队列重叠。Doll 2020 会议初报不能与 2022 正式论文重复计数。提供原始链接、全文待核对问题、进一步获取人体时序的顺序与原因。

**这是一份新增证据与方法学附录，不是新增的完整外部验证数据集。** 不修改既有 P2-A/B 冻结协议或数据拆分；新增数字已被研究者见到，后续不可称未见 LOCKED_EXTERNAL。正式人口模型及全部 P1 行为不变；external_validation_insufficient、clinical_accuracy_established=false，P2-C 不获授权。


[原始证据审计](evidence-audit.md)、[目录](evidence-catalog.json)、[获取记录](source-manifest.json)、[整研究分割](split-manifest.json)。Cortez fresh全文、Yaish P0全文重核，其他五研究仅摘要。TRAIN1/DESIGN_EXPOSED3/LOCKED_EXTERNAL0/QUALITATIVE3/NOT_COMPARABLE0。

[拟合前冻结协议](validation-protocol.md)和机器设置/lock在9051875独立提交推送；研究实现138b981、报告回归af3767e在后。没有科学协议偏离。P0/P1已完成、原文件和生产hash锁定只读。研究没有Android依赖。

[候选数学](candidate-models.md)、[可辨识性](identifiability.md)、[按研究外部条件比较](external-validation.md)、[敏感性](sensitivity.md)、[结论与P2-C门槛](model-selection.md)、[实际验收](verification.md)。[机器报告](results/analysis.json)、[全部参数](results/candidate-parameters.json)、[逐研究误差](results/study-comparisons.csv)、[可读表](results/report.md)。339固定形状/基线情景各只拟合一个幅度；不是339人体采样。全部负结果保留，没有选赢家。

**external_validation_insufficient / clinical_accuracy_established=false**。数学正确、数值稳定不等于人体有效。保留现有人口模型；P2-C未实施且需独立授权。Build25/Schema9/P1校准/历史冻结/备份/PDF/签名不变，不发APK/Release/PR。

复现（Python3.10+标准库，无AndroidSDK、无网络依赖）：

```sh
python3 tools/pk-research/check_protocol.py
python3 -m unittest discover -s tools/pk-research -p 'test_*.py'
python3 tools/pk-research/research.py --output /tmp/hrt-p2-new-empty
```

输出必须为空目录，不能覆盖旧结果；SHA和完整命令/计数见验收。新输入/假设只能记录新的显式协议偏离，不静默修改冻结文件。研究CLI不自认证软件/临床成功，软件证据单独记录。
