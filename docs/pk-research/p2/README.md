# P2-A/B 舌下人口模型研究（已完成隔离研究，未投产）

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
