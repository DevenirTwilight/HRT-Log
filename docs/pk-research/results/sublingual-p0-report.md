# 舌下 P0 自动对照报告

输入为实际 Kotlin 测试导出的合成给药及已核对原文摘要。数值对照成功不等于科学验证通过；独立人体外部验证尚不充分，当前模型与多项摘要存在明显差异。生产参数未修改。

参数 SHA256 `b2768a6947d29d65f272b1d20e31fd59b9661acf8166b437f2a3b179f235b16b`；Featherline固定公开参数提交 `374ecbd7c74e3edea1ba2070a243c402141e41b2`，体重80kg。
独立稳定算式与实际Kotlin逐点最大差异：4.76745754e-10 pg/mL。每个指标数值积分步长0.005h，峰值时间为此网格，不冒充人体实测。

| 时间 | HRT单次2mg pg/mL | Featherline单次2mg pg/mL |
|---|---:|---:|
| 15分钟 | 152.437586 | 496.135479 |
| 30分钟 | 237.441852 | 771.108908 |
| 45分钟 | 277.386795 | 908.559214 |
| 46分钟 | 278.864780 | 914.262352 |
| 60分钟 | 288.047613 | 961.517472 |
| 90分钟 | 262.086754 | 935.716558 |
| 120分钟 | 211.978781 | 838.644659 |
| 180分钟 | 117.031603 | 619.919267 |
| 240分钟 | 57.470421 | 443.814712 |
| 360分钟 | 11.771071 | 224.677848 |
| 480分钟 | 2.229209 | 114.081722 |
| 720分钟 | 0.172514 | 29.840758 |
| 1440分钟 | 0.066117 | 0.579852 |

| 合成情形 | 模型 | C46min | Cmax0–24 | Tmax h | AUC0–8 | AUC0–24 | 当前剂量前谷 | 停药后48h |
|---|---|---:|---:|---:|---:|---:|---:|---:|
| single_2mg | HRT_current | 278.864780 | 288.047613 | 1.000000 | 781.295199 | 785.217641 | 0.000000 | 0.020187 |
| single_2mg | Featherline_population | 914.262352 | 967.356226 | 1.135000 | 3826.499552 | 4165.887215 | 0.000000 | 0.000255 |
| single_1mg | HRT_current | 139.432390 | 144.023806 | 1.000000 | 390.647600 | 392.608821 | 0.000000 | 0.010093 |
| single_1mg | Featherline_population | 457.131176 | 483.678113 | 1.135000 | 1913.249776 | 2082.943608 | 0.000000 | 0.000127 |
| single_4mg | HRT_current | 557.729559 | 576.095226 | 1.000000 | 1562.590398 | 1570.435283 | 0.000000 | 0.040374 |
| single_4mg | Featherline_population | 1828.524704 | 1934.712452 | 1.135000 | 7652.999105 | 8331.774431 | 0.000000 | 0.000510 |
| daily_2mg | HRT_current | 278.956409 | 288.138196 | 1.000000 | 781.924196 | 786.555149 | 0.095153 | 0.029036 |
| daily_2mg | Featherline_population | 914.714603 | 967.758065 | 1.130000 | 3828.155280 | 4167.677548 | 0.580107 | 0.000255 |
| twice_daily_2mg | HRT_current | 279.146999 | 288.321640 | 1.000000 | 783.111420 | 789.024519 | 0.320260 | 0.045065 |
| twice_daily_2mg | Featherline_population | 937.846558 | 988.283893 | 1.120000 | 3911.909027 | 4257.956223 | 30.432885 | 0.000261 |
| daily_4mg | HRT_current | 557.912819 | 576.276392 | 1.000000 | 1563.848392 | 1573.110298 | 0.190305 | 0.058071 |
| daily_4mg | Featherline_population | 1829.429205 | 1935.516130 | 1.130000 | 7656.310561 | 8335.355096 | 1.160214 | 0.000510 |
| prior_2mg_6h | HRT_current | 285.093874 | 293.205189 | 0.985000 | 795.836892 | 800.961195 | 11.771071 | 0.035186 |
| prior_2mg_6h | Featherline_population | 1087.432356 | 1122.485421 | 1.050000 | 4447.219655 | 4833.034427 | 224.677848 | 0.000292 |
| prior_oral_2mg_24h | HRT_current | 305.308093 | 314.192194 | 0.995000 | 962.855587 | 1171.338537 | 27.456961 | 2.575038 |
| prior_oral_2mg_24h | Featherline_population | 914.692435 | 967.740478 | 1.130000 | 3828.108905 | 4167.637972 | 0.544930 | 0.000255 |
| yaish_regular_q6h_0.5mg | HRT_current | 71.391789 | 73.417184 | 0.985000 | 199.741396 | 201.890101 | 3.072436 | 0.019637 |
| yaish_regular_q6h_0.5mg | Featherline_population | 278.569773 | 286.739826 | 1.040000 | 1136.130932 | 1234.487471 | 64.826318 | 0.000075 |

连续情形给药仅到t=0为止；0–24h是最后一次之后的下降，**不是停止后仍包含未来剂量的稳态日AUC**。daily_2mg为2mg/day，twice_daily_2mg为4mg/day；比较频率时使用daily_4mg对twice_daily_2mg，避免把总日量变化误当频率效应。yaish_regular只是合成等间隔历史，不是研究每个人的实际历史。

| 独立研究 | 模型 | 确实报告的观察 | 对应计算 | 比较限制 |
|---|---|---|---|---|
| Pines1998 | HRT_current | 1759.0±704 pg/mL（±类型未知） | 576.10，误差-1182.90 | external disagreement/heterogeneity assessment; assay/baseline/weight uncertain; no clinical pass claim |
| Yaish2023 | HRT_current | 543.18±396.89 pg/mL（SD） | 原始历史66.48；条件谷校正119.11 | conditional on constant observed group trough; not person-level validation or assay harmonization |
| Burnier1981 | HRT_current | 1h相对基线26倍 | 条件预测4.00倍 | abstract-only conditional fold comparison; no fabricated original absolute 1h concentration |
| Pines1998 | Featherline_population | 1759.0±704 pg/mL（±类型未知） | 1923.03，误差+164.03 | external disagreement/heterogeneity assessment; assay/baseline/weight uncertain; no clinical pass claim |
| Yaish2023 | Featherline_population | 543.18±396.89 pg/mL（SD） | 原始历史272.96；条件谷校正263.84 | conditional on constant observed group trough; not person-level validation or assay harmonization |
| Burnier1981 | Featherline_population | 1h相对基线26倍 | 条件预测11.02倍 | abstract-only conditional fold comparison; no fabricated original absolute 1h concentration |

Doll144/1h属于拟合目标，不能算独立命中。Pines只有60min、Yaish只有90min及晨谷，不能构造整段“人体曲线”；不能说某模型在15/30/45min吻合这些研究，因为没有取得对应实测。Featherline在Pines60min和Yaish90min摘要数值上较接近，不构成总体/个体更准确的证明。

## 吞咽项结构不可辨识演示

以下模型只拟合同一**模型生成代理**AUC390.6484、C(1)=144和C′(1)=0。变化快速输入形状后吞咽系数可很不同；不是人体数据，不是已验证候选，也不用于界面。

| Gamma形状 n | 吞咽系数 | C1h | C′1h | 代理AUC0–8 | C24h |
|---:|---:|---:|---:|---:|---:|
| 1 | 0.0034080983 | 144 | 5.6843419e-09 | 390.64842 | 0.046788365 |
| 1.2 | 0.30694508 | 144 | 7.1054274e-09 | 390.64842 | 4.2138896 |
| 1.5 | 0.59934477 | 144 | 8.5265128e-09 | 390.64842 | 8.228093 |
| 2 | 0.89229541 | 144 | 9.9475983e-09 | 390.64842 | 12.24986 |

## 实际Kotlin校准敏感性

| 速率倍率 | 单次2mg C46min pg/mL |
|---:|---:|
| 0.5 | 170144.304010 |
| 0.9 | 29254.275557 |
| 0.999 | 557.765816 |
| 1 | 278.864780 |
| 1.001 | 0.177379 |
| 1.01 | 0.000000 |
| 1.1 | 0.000000 |

现有默认无化验5–95%先验带：0.000000–152714.805864 pg/mL，中心278.864780。这是原软件输出的已证实参数化病态，不能称可靠人体区间。

## 应用历史与因果路径实测

```json
{
  "causal_interpolation_leak": {
    "population_current_pg_ml": 277.76798707874434,
    "sample_after_now_min": 5,
    "earlier_grid_uses_future_lab": false,
    "current_with_future_lab_pg_ml": 289.1998247378571,
    "causal_summary_future_observations": 1
  },
  "single_dose": {
    "actual_calculator_off_pg_ml": 277.76798707874434,
    "synthetic_400_pg_ml_lab_on": 398.42655303749905,
    "direct_engine_46min_pg_ml": 278.86477960569,
    "calibrated_rate": 0.9995655795850356,
    "calibrated_amplitude": 1.000002290377117
  },
  "time_and_frozen_route": {
    "sample_zone_label_does_not_shift_epoch": true,
    "uses_taken_not_scheduled": true,
    "actual_dose_revision_applied": true,
    "dst_fold_actual_elapsed_min": 46,
    "current_oral_EV_does_not_overwrite_historical_E2_SL": true
  },
  "missing_context_false_baseline": {
    "false_baseline_pg_ml": 220,
    "warning_present": true,
    "skipped": 1
  },
  "history_window_false_baseline": {
    "used_doses": 1,
    "population_current_pg_ml": 277.76798707874434,
    "false_baseline_pg_ml": 500,
    "history_days": 180,
    "untruncated_baseline": null,
    "lab_age_days": 200,
    "provided_record_age_days": 201,
    "wrong_calibrated_current_pg_ml": 777.7679870787443
  },
  "filters_and_units": {
    "skipped_deleted_missed_contribute": false,
    "pg_ml_pmol_l_equivalent": true
  }
}
```

完整机器结果还记录单点/同相位/不同相位化验的参数及协方差；所有化验为合成。确认缺陷和修复规格见[校准技术审计](../sublingual-calibration-audit.md)。
