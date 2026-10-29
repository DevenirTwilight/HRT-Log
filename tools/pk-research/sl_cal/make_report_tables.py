"""Turns sl-cal-01-synthetic.json into the markdown tables used by the report (aggregation only, no new numbers are invented).
Rows are weighted by their user counts; 'median of cell medians' is used as the summary and says so in the table header."""
import argparse
import json
from collections import defaultdict
from pathlib import Path

import numpy as np

METHOD_LABEL = {'uncalibrated': '未校准(参考)', 'A_sl_only': 'A 只校舌下', 'B_two_independent': 'B 两系数(独立先验)', 'B2_two_pooled': 'B2 两系数(部分合并)',
                'C_common': 'C 共同系数', 'D_p1a_framework': 'D 复用P1-A框架', 'oracle': '真值系数(下限)'}


def load(path):
    return json.loads(Path(path).read_text())['rows']


def med(values):
    v = [x for x in values if x is not None]
    return float(np.median(v)) if v else float('nan')


def table(rows, sweep, policy, where, metrics, group_by='method', order=None):
    sel = [r for r in rows if r['sweep'] == sweep and r['policy'] == policy and where(r)]
    groups = defaultdict(list)
    for r in sel:
        groups[r[group_by]].append(r)
    keys = order or sorted(groups)
    out = ['| ' + group_by + ' | ' + ' | '.join(m[0] for m in metrics) + ' |', '|---|' + '---:|' * len(metrics)]
    for k in keys:
        if k not in groups:
            continue
        out.append(f'| {METHOD_LABEL.get(k, k)} | ' + ' | '.join(f'{med([r.get(m[1]) for r in groups[k]]):.3f}' for m in metrics) + ' |')
    return '\n'.join(out)


if __name__ == '__main__':
    ap = argparse.ArgumentParser(); ap.add_argument('--in', dest='src', required=True); a = ap.parse_args()
    rows = load(a.src)
    order = ['uncalibrated', 'A_sl_only', 'B_two_independent', 'B2_two_pooled', 'C_common', 'D_p1a_framework', 'oracle']
    M = [('曲线误差(中位|ln比|)', 'curve_med_abs_log_median'), ('曲线误差p90', 'curve_med_abs_log_p90'), ('1.5倍内占比', 'within_1p5x_median'),
         ('峰值|误差|p90', 'peak_abs_err_p90'), ('谷值|误差|p90', 'trough_abs_err_p90'), ('舌下系数|误差|中位', 'sl_coef_abs_err_median'), ('拒绝率', 'refused_rate')]
    print('## baseline, policy=all, SL-only'); print(table(rows, 'baseline', 'all', lambda r: r['truth'] == 'sl', M, order=order))
    print('\n## baseline, policy=all, mixed'); print(table(rows, 'baseline', 'all', lambda r: r['truth'] != 'sl', M, order=order))
