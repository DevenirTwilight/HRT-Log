# P1-C2 拟合集合与警告验收

仅合成软件验收；残差不是化验错误诊断，人体准确性未确立。

| 情景 |候选|实际参与|真实排除|警告|
|---|---|---|---|---|
|single_warning|1|1|0|1|
|all_warning|2|2|0|2|
|partial|4|3|1|1|
|normal|3|3|0|0|
|empty|0|0|0|0|

旧单条观测actual=1、claimed excluded=1；新used=1、warning=1、excluded=0。旧/新实际MAP及协方差不变；部分异常与独立保留子集的中心和四分位/协方差一致。
人口/P1-A/历史资格/C1非干涉误差：{'population_cases': 0, 'synthetic_fits': 0.0, 'prior_band_46min': 0.0, 'history_cases': 0, 'causal': 1.447233444196172e-10}。
原始点、人口参数、Build25/schema9/Calculator2/冻结1/2不变；严格结果获知时间/P2另案。完整命令/性能/UI/API35/CI见sublingual-p1c2-verification.md。
