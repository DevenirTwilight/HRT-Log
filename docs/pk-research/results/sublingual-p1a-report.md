# P1-A 数值验收报告

纯合成数据；采用B，仅幅度校准。不是人体外部准确性验收。
人口曲线 9 组×13时刻，指标/时间点最大误差 7.16795511835e-11。人口参数SHA256 `b2768a6947d29d65f272b1d20e31fd59b9661acf8166b437f2a3b179f235b16b`未变。

| 速率倍率输入（新策略忽略SL速率） | 旧pg/mL | 新pg/mL |
| --- | --- | --- |
| 0.5 | 170144.30401042 | 278.86477961 |
| 0.9 | 29254.27555715 | 278.86477961 |
| 0.999 | 557.76581554 | 278.86477961 |
| 1 | 278.86477961 | 278.86477961 |
| 1.001 | 0.17737902 | 278.86477961 |
| 1.01 | 0.00000000 | 278.86477961 |
| 1.1 | 0.00000000 | 278.86477961 |

旧先验带：{'p5': 0, 'center': 278.8647796057063, 'p95': 152714.8058637828}；新先验带：{'p5': 98.82246992729898, 'center': 278.8647796057063, 'p95': 692.8536038998615}。仅参数不确定性，不是临床区间。

| 化验 | 新幅度 | 新速率 | 幅度方差 |
| --- | --- | --- | --- |
| one_lab | 1.3732037279 | 1 | 0.0669764697 |
| same_phase | 1.4492182809 | 1 | 0.0261182110 |
| different_times | 1.4492182809 | 1 | 0.0261182110 |

## 应用实际路径
{
  "causal_interpolation_leak": {
    "population_current_pg_ml": 277.76798707874434,
    "sample_after_now_min": 5,
    "earlier_grid_uses_future_lab": false,
    "current_with_future_lab_pg_ml": 286.36702173659074,
    "causal_summary_future_observations": 1
  },
  "single_dose": {
    "actual_calculator_off_pg_ml": 277.76798707874434,
    "synthetic_400_pg_ml_lab_on": 368.3183698246684,
    "direct_engine_46min_pg_ml": 278.86477960569,
    "calibrated_rate": 1,
    "calibrated_amplitude": 1.3259928679983342
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

## 性能（120事件、2881网格点、200抽样、2热身、5次；不含断言）
旧中位 0.047623s，新中位 0.009953s。纯SL复用固定人口核。不同JVM、同主机；不保证一般性能比例。

## 尚未修复
- 180-day history false baseline
- missing frozen context false baseline
- causal interpolation/summary future labs
- all-outlier IDs versus actual fit

P0文献与外部误差报告仍为原始基线；新人口未变，外部误差也没有改善。数学正确、数值稳定与人体准确性为三个独立判断。
全部输入/脚本哈希、拟合协方差、候选A与旧拟合结果见配套JSON。命令/测试计数见独立P1-A验收记录。
