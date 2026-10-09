# P1-B 历史资格门控验收

纯合成数据。工程资格验收通过，不是人体外部科学验证。
人口曲线9组×13点与P1-A最大差异0；人口参数hash `b2768a6947d29d65f272b1d20e31fd59b9661acf8166b437f2a3b179f235b16b`未变。P1-A拟合/先验带数值逐字段相同。

| 情景 | 人口pg/mL | 门控后pg/mL | 合格/实际拟合/原观测 | 基线 |
| --- | --- | --- | --- | --- |
| old_treatment_lab | 277.76798708 | 277.76798708 | 0/0/1 | None |
| unknown_context_lab | 277.76798708 | 277.76798708 | 0/0/1 | None |
| eligible_single_sl | 277.76798708 | 368.31836982 | 1/1/1 | None |
| eligible_mixed | 298.84739092 | 375.41796593 | 1/1/1 | None |
| partial_eligibility | 279.03729364 | 496.31097454 | 1/1/2 | None |
| bounded_old_sl | 277.76798708 | 368.31836982 | 1/1/1 | None |

案例A旧500假基线/current777.767987 → 当前人口277.767987且不拟合；案例B旧220假基线 → 基线未知且不拟合，观测均保留。原始精确旧数字见只读P0 JSON与本报告before字段。

资格逐条状态、原因、证据记录及残余log上界见eligibility JSON。COMPLETE仅指已保存模型输入，不是现实全部治疗史。时间窗不是治疗开始；生产不生成确认治疗前基线。
非SL窗口前暴露/未知给药无法由固定SL上界证明，保守不纳入；混合缺一不可把其余剂量冒充全部观测。
10000条证据×1000化验资格索引计时0.007330s（当前runner，非跨设备性能承诺）；无逐化验数据库查询。残余预算/取消及3000实际记录回归由Kotlin测试验证。

仍未修复：
- P1-C causal historical interpolation/unrestricted summary
- P1-C all-outlier bookkeeping
- Explicit pre-treatment user confirmation/persistence absent
- P2 population model/external clinical accuracy not established
- Omitted non-SL and unknown exposure conservatively excluded; no universal 180-day zero-residual claim

完整测试命令/计数、四语UI、冻结备份/PDF与API35产物回查见独立P1-B验收记录。
