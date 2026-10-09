#!/usr/bin/env python3
"""P1-A numerical acceptance only. Immutable P0 inputs; never refits population parameters."""
import argparse
import csv
import json
import math
import shutil
import statistics
from pathlib import Path
from sublingual_compare import ROOT, sha, fitted

PARAM_HASH = 'b2768a6947d29d65f272b1d20e31fd59b9661acf8166b437f2a3b179f235b16b'


def normalized(t, a, ka, lam0, rate):
    """Candidate A only, no production use. Stable difference quotient at and across equal rates."""
    if t < 0:
        return 0.0
    lam = lam0 * rate
    delta = abs(ka-lam)
    b = a * (ka-lam0)
    factor = t if delta == 0 else -math.expm1(-delta*t)/delta
    return b * math.exp(-min(ka,lam)*t) * factor


def run(engine, calculator, stability, output):
    paths = dict(old_engine=ROOT/'docs/pk-research/results/sublingual-p0-engine.json',
                 old_calculator=ROOT/'docs/pk-research/results/sublingual-p0-calculator.json',
                 engine=Path(engine), calculator=Path(calculator), stability=Path(stability),
                 non_sl_baseline=ROOT/'pk-engine/src/test/resources/non-sl-calibration-v1.json',
                 parameters=ROOT/'pk-engine/src/main/resources/pk-params.json')
    data = {k:json.loads(p.read_text()) for k,p in paths.items()}
    assert sha(paths['parameters']) == PARAM_HASH, 'Published population parameters changed'
    old, new = data['old_engine'], data['engine']
    assert new['calibration_algorithm_version'] == 2
    errors=[]; rows=[]
    old_cases={c['id']:c for c in old['population_cases']}
    for c in new['population_cases']:
        before=old_cases[c['id']]
        assert c['events'] == before['events'] and c['time_h'] == before['time_h']
        for t,a,b in zip(c['time_h'],before['concentration_pg_ml'],c['concentration_pg_ml']):
            errors.append(abs(a-b));rows.append([c['id'],t,a,b,abs(a-b)])
        for key in ['cmax_0_24_pg_ml','auc0_8_pg_h_ml','auc0_24_pg_h_ml','pre_current_dose_pg_ml','post_stop_48h_pg_ml']:
            errors.append(abs(c[key]-before[key]))
        assert c['tmax_0_24_h'] == before['tmax_0_24_h']
    assert max(errors)<1e-6
    rate=[dict(rate_factor=r['rate_factor'],before_pg_ml=r['c_46min_pg_ml'],
               after_pg_ml=next(n['c_46min_pg_ml'] for n in new['rate_sensitivity'] if n['rate_factor']==r['rate_factor']))
          for r in old['rate_sensitivity']]
    assert all(abs(r['after_pg_ml']-278.8647796057063)<1e-6 for r in rate)
    assert 0<new['prior_band_46min']['p5']<=new['prior_band_46min']['p95']<math.inf
    assert all(f['rate']==1 and f['covariance'][1:]==[0,0,0] for f in new['synthetic_fits'])
    old_perf=data['non_sl_baseline']['performance']; perf=data['stability']['performance']
    assert all(perf[k]==old_perf[k] for k in ['events','grid_points','samples','warmups'])
    p=data['parameters']['models']['E2_SL'];term=p['terms'][0]
    candidate=[dict(rate=r,fast_only_pg_ml=2*normalized(46/60,term['A_per_mg'],p['ka_per_h'],term['lambda_per_h'],r))
               for r in [.9,1,p['ka_per_h']/term['lambda_per_h'],1.1]]
    report=dict(numerical_acceptance=True,clinical_accuracy_established=False,synthetic_only=True,
                algorithm_version=2,parameter_hash=PARAM_HASH,input_hashes={k:sha(v) for k,v in paths.items()},
                script_sha256=sha(__file__),population_max_absolute_error=max(errors),
                population_cases=len(new['population_cases']),population_time_points=len(rows),rate_sensitivity=rate,
                prior_band_before=old['prior_band_46min'],prior_band_after=new['prior_band_46min'],
                fits_before=old['synthetic_fits'],fits_after=new['synthetic_fits'],
                calculator_before=data['old_calculator'],calculator_after=data['calculator'],candidate_A_fast_only=candidate,
                performance=dict(before_seconds=old_perf['seconds'],after_seconds=perf['seconds'],
                                 before_median=statistics.median(old_perf['seconds']),after_median=statistics.median(perf['seconds']),
                                 caveat='Same host, separate JVMs; illustrative warm microbenchmark, not a general speed guarantee.'),
                unresolved=['180-day history false baseline','missing frozen context false baseline',
                            'causal interpolation/summary future labs','all-outlier IDs versus actual fit'],
                uncertainty='Assumed parameter distribution only; not full predictive, structural, measurement or study uncertainty.')
    out=Path(output);out.mkdir(parents=True,exist_ok=True)
    (out/'sublingual-p1a-report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
    for key in ['engine','calculator','stability']:
        shutil.copyfile(paths[key],out/f'sublingual-p1a-{key}.json')
    with (out/'sublingual-p1a-curves.csv').open('w',newline='') as f:
        w=csv.writer(f,lineterminator="\n");w.writerow(['case','time_h','old_population_pg_ml','new_population_pg_ml','absolute_error']);w.writerows(rows)
    text=['# P1-A 数值验收报告','', '纯合成数据；采用B，仅幅度校准。不是人体外部准确性验收。',
          f"人口曲线 {len(new['population_cases'])} 组×13时刻，指标/时间点最大误差 {max(errors):.12g}。人口参数SHA256 `{PARAM_HASH}`未变。",'',
          '| 速率倍率输入（新策略忽略SL速率） | 旧pg/mL | 新pg/mL |','| --- | --- | --- |']
    text += [f"| {r['rate_factor']} | {r['before_pg_ml']:.8f} | {r['after_pg_ml']:.8f} |" for r in rate]
    text += ['',f"旧先验带：{report['prior_band_before']}；新先验带：{report['prior_band_after']}。仅参数不确定性，不是临床区间。",'',
             '| 化验 | 新幅度 | 新速率 | 幅度方差 |','| --- | --- | --- | --- |']
    text += [f"| {f['id']} | {f['amplitude']:.10f} | {f['rate']} | {f['covariance'][0]:.10f} |" for f in new['synthetic_fits']]
    text += ['', '## 应用实际路径',json.dumps(report['calculator_after'],ensure_ascii=False,indent=2),'',
             '## 性能（120事件、2881网格点、200抽样、2热身、5次；不含断言）',
             f"旧中位 {report['performance']['before_median']:.6f}s，新中位 {report['performance']['after_median']:.6f}s。纯SL复用固定人口核。不同JVM、同主机；不保证一般性能比例。",'',
             '## 尚未修复',*['- '+x for x in report['unresolved']],'',
             'P0文献与外部误差报告仍为原始基线；新人口未变，外部误差也没有改善。数学正确、数值稳定与人体准确性为三个独立判断。',
             '全部输入/脚本哈希、拟合协方差、候选A与旧拟合结果见配套JSON。命令/测试计数见独立P1-A验收记录。']
    (out/'sublingual-p1a-report.md').write_text('\n'.join(text)+'\n')
    return report

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--engine',default=ROOT/'pk-engine/build/reports/pk-p1a/engine-outputs.json')
    p.add_argument('--calculator',default=ROOT/'app/build/reports/pk-p1a/calculator-audit.json')
    p.add_argument('--stability',default=ROOT/'pk-engine/build/reports/pk-p1a/stability.json')
    p.add_argument('--output',default=ROOT/'docs/pk-research/results')
    args=p.parse_args();r=run(args.engine,args.calculator,args.stability,args.output)
    print('Numerical acceptance:',r['numerical_acceptance'],'; clinical accuracy established:',r['clinical_accuracy_established'])
