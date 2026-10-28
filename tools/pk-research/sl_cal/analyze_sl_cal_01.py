"""Pivot tables for the SL-CAL-01 report, generated from sl-cal-01-synthetic.json (no new simulation). Cell values are the median across
the contributing cells' own medians (each cell = regimen x truth x scheme x lab count, 100 baseline / 30 sweep users)."""
import argparse
import json
import math
from collections import defaultdict
from pathlib import Path

import numpy as np

LABEL = {'uncalibrated': '未校准', 'A_sl_only': 'A', 'B_two_independent': 'B', 'B2_two_pooled': 'B2', 'C_common': 'C', 'D_p1a_framework': 'D', 'oracle': '真值'}
CORE = ['uncalibrated', 'A_sl_only', 'B_two_independent', 'B2_two_pooled', 'C_common', 'D_p1a_framework']


def med(v):
    v = [x for x in v if x is not None]
    return float(np.median(v)) if v else math.nan


def pivot(rows, rowkey, colkey, metric, flt, rowvals=None, colvals=None, fmt='{:.3f}', rowname=None):
    sel = [r for r in rows if flt(r)]
    cells = defaultdict(list)
    for r in sel:
        cells[(rowkey(r), colkey(r))].append(r.get(metric))
    rv = rowvals or sorted({k[0] for k in cells}, key=str)
    cv = colvals or sorted({k[1] for k in cells}, key=str)
    out = ['| ' + (rowname or '') + ' | ' + ' | '.join(str(LABEL.get(c, c)) for c in cv) + ' |', '|---|' + '---:|' * len(cv)]
    for a in rv:
        out.append(f'| {a} | ' + ' | '.join((fmt.format(med(cells[(a, c)])) if cells.get((a, c)) else '') for c in cv) + ' |')
    return '\n'.join(out)


def build(rows):
    sl = lambda r: r['truth'] == 'sl'
    mixed = lambda r: r['truth'] != 'sl'
    base = lambda r: r['sweep'] == 'baseline'
    out = []
    def add(title, text):
        out.append(f'### {title}\n\n{text}\n')
    for name, who in (('只有舌下', sl), ('混合途径', mixed)):
        for scheme in ('trough', 'peak', 'random'):
            add(f'{name} · {scheme} · 曲线误差（中位 |ln 比|），按化验次数', pivot(rows, lambda r: r['n_labs'], lambda r: r['method'], 'curve_med_abs_log_median',
                lambda r: base(r) and who(r) and r['policy'] == 'all' and r['scheme'] == scheme and r['method'] != 'oracle', rowvals=[1, 2, 3, 5], colvals=CORE, rowname='化验次数'))
    add('舌下系数误差（中位 |ln k̂ − ln g|），混合途径，按采样方式 × 方法', pivot(rows, lambda r: r['scheme'], lambda r: r['method'], 'sl_coef_abs_err_median',
        lambda r: base(r) and mixed(r) and r['policy'] == 'all' and r['method'] in CORE[1:], colvals=CORE[1:], rowname='采样'))
    add('非舌下系数误差，混合途径', pivot(rows, lambda r: r['scheme'], lambda r: r['method'], 'nsl_coef_abs_err_median',
        lambda r: base(r) and mixed(r) and r['policy'] == 'all' and r['method'] in CORE[1:], colvals=CORE[1:], rowname='采样'))
    pol = ['all', 'exclude_1.5h', 'exclude_2.5h', 'exclude_4h', 'delta']
    for scheme in ('peak', 'random'):
        add(f'峰值时点策略 · {scheme} · baseline · 曲线误差（舌下+混合，方法 B2/C/A）', pivot(rows, lambda r: r['policy'], lambda r: r['method'], 'curve_med_abs_log_median',
            lambda r: base(r) and r['scheme'] == scheme and r['method'] in ('A_sl_only', 'B2_two_pooled', 'C_common'), rowvals=pol, colvals=['A_sl_only', 'B2_two_pooled', 'C_common'], rowname='策略'))
        add(f'峰值时点策略 · {scheme} · 拒绝率', pivot(rows, lambda r: r['policy'], lambda r: r['method'], 'refused_rate',
            lambda r: base(r) and r['scheme'] == scheme and r['method'] in ('A_sl_only', 'C_common'), rowvals=pol, colvals=['A_sl_only', 'C_common'], rowname='策略', fmt='{:.2f}'))
    sweeps = ['baseline', 'noise_low', 'noise_high', 'timing_15min', 'timing_30min', 'assay_bias_1.25', 'background_10', 'background_40', 'shape_mismatch_p2x-22', 'prior_sd_0.9', 'truth_prior_consistent']
    for who, nm in ((sl, '只有舌下'), (mixed, '混合')):
        for policy in ('all', 'delta'):
            add(f'敏感性扫描 · {nm} · policy={policy} · 曲线误差', pivot(rows, lambda r: r['sweep'], lambda r: r['method'], 'curve_med_abs_log_median',
                lambda r: who(r) and r['policy'] == policy and r['method'] in CORE + ['oracle'], rowvals=sweeps, colvals=CORE + ['oracle'], rowname='扫描'))
    add('敏感性扫描 · 舌下系数 90% 区间覆盖率（名义 0.90；A 方法，policy=all，只有舌下）', pivot(rows, lambda r: r['sweep'], lambda r: r['method'], 'sl_coverage90',
        lambda r: sl(r) and r['policy'] == 'all' and r['method'] in ('A_sl_only',), rowvals=sweeps, colvals=['A_sl_only'], rowname='扫描', fmt='{:.2f}'))
    add('敏感性扫描 · 舌下系数 90% 区间覆盖率（混合，policy=all）', pivot(rows, lambda r: r['sweep'], lambda r: r['method'], 'sl_coverage90',
        lambda r: mixed(r) and r['policy'] == 'all' and r['method'] in CORE[1:], rowvals=sweeps, colvals=CORE[1:], rowname='扫描', fmt='{:.2f}'))
    for who, nm in ((sl, '只有舌下'), (mixed, '混合')):
        add(f'留一化验预测误差（RMS ln，baseline，N≥2，{nm}）· 策略 × 方法', pivot(rows, lambda r: r['policy'], lambda r: r['method'], 'loo_rms_median',
            lambda r: base(r) and who(r) and r['n_labs'] >= 2 and r['method'] in CORE, rowvals=pol, colvals=CORE, rowname='策略'))
        add(f'留一化验预测误差（仅非峰值目标，{nm}）', pivot(rows, lambda r: r['policy'], lambda r: r['method'], 'loo_rms_nonpeak_median',
            lambda r: base(r) and who(r) and r['n_labs'] >= 2 and r['method'] in CORE, rowvals=pol, colvals=CORE, rowname='策略'))
    add('信息增益 ig_sl（A，policy=all，只有舌下）按采样 × 化验次数', pivot(rows, lambda r: r['scheme'], lambda r: r['n_labs'], 'ig_sl_median',
        lambda r: base(r) and sl(r) and r['policy'] == 'all' and r['method'] == 'A_sl_only', colvals=[1, 2, 3, 5], rowname='采样', fmt='{:.2f}'))
    add('信息增益 ig_sl（C，policy=all，混合）按采样 × 化验次数', pivot(rows, lambda r: r['scheme'], lambda r: r['n_labs'], 'ig_sl_median',
        lambda r: base(r) and mixed(r) and r['policy'] == 'all' and r['method'] == 'C_common', colvals=[1, 2, 3, 5], rowname='采样', fmt='{:.2f}'))
    return '\n'.join(out)


if __name__ == '__main__':
    ap = argparse.ArgumentParser(); ap.add_argument('--in', dest='src', required=True); ap.add_argument('--out', required=True); a = ap.parse_args()
    rows = json.loads(Path(a.src).read_text())['rows']
    Path(a.out).write_text('# SL-CAL-01 合成验证汇总表（由 analyze_sl_cal_01.py 从 sl-cal-01-synthetic.json 生成）\n\n单元格是各 (途径×真值×采样×化验次数) 单元自身中位数的中位数。\n\n' + build(rows) + '\n')
