#!/usr/bin/env python3
"""P1-C1 sampling-time noninterference, not clinical external validation. All old evidence is read-only."""
import argparse
import json
import shutil
from pathlib import Path
from sublingual_compare import ROOT, sha
from sublingual_p1a_compare import PARAM_HASH

def validate(before,after):
    assert before['synthetic_only'] and after['synthetic_only']
    for key in ['now','query','dose','sample']:assert before[key]==after[key]
    old={r['id']:r for r in before['cases']};new={r['id']:r for r in after['cases']}
    assert set(old)==set(new)=={'none','future400','future800','same_time_two'}
    base=new['none']['values_center_p5_p25_p75_p95'];assert len(base)==5
    for key in new:
        a=old[key];b=new[key]
        assert a['qualified_count']==b['qualified_count'] and a['raw_count']==b['raw_count']
        assert len(b['values_center_p5_p25_p75_p95'])==5
        assert max(abs(x-y) for x,y in zip(base,b['values_center_p5_p25_p75_p95']))<1e-7
        assert b['summary_count']==b['post_count']==0 and b['diagnostic_observed'] is None
        if key!='none':
            assert a['qualified_count']>0 and a['summary_count']>0
            assert abs(a['values_center_p5_p25_p75_p95'][0]-old['none']['values_center_p5_p25_p75_p95'][0])>1
            assert b['after_sample_count']==b['qualified_count']
            assert abs(b['after_sample_values'][0]-new['none']['after_sample_values'][0])>1
    return max(abs(x-y) for x,y in zip(old['none']['values_center_p5_p25_p75_p95'],base))

def close(a,b,path=''):
    if isinstance(a,dict):assert set(a)==set(b);return max([close(a[k],b[k],path+'.'+k) for k in a]+[0.])
    if isinstance(a,list):assert len(a)==len(b);return max([close(x,y,path) for x,y in zip(a,b)]+[0.])
    if isinstance(a,(int,float)) and not isinstance(a,bool):assert abs(a-b)<1e-7,(path,a,b);return abs(a-b)
    assert a==b,(path,a,b);return 0.

def run(args):
    paths={key:Path(getattr(args,key)) for key in ['before','after','engine','calculator','eligibility']}
    data={k:json.loads(p.read_text()) for k,p in paths.items()}
    assert sha(ROOT/'pk-engine/src/main/resources/pk-params.json')==PARAM_HASH
    unchanged=validate(data['before'],data['after'])
    old_engine=json.loads((ROOT/'docs/pk-research/results/sublingual-p1b-engine.json').read_text())
    population_error=close(old_engine['population_cases'],data['engine']['population_cases'])
    fit_error=close(old_engine['synthetic_fits'],data['engine']['synthetic_fits'])
    prior_error=close(old_engine['prior_band_46min'],data['engine']['prior_band_46min'])
    old_gate=json.loads((ROOT/'docs/pk-research/results/sublingual-p1b-eligibility.json').read_text())
    gate_error=close(old_gate['cases'],data['eligibility']['cases'])
    assert data['calculator']['causal_interpolation_leak']['still_unfixed'] is False
    r=dict(synthetic_only=True,sampling_time_noninterference_acceptance=True,clinical_accuracy_established=False,
           calculator_version=2,labfit_algorithm_version=2,parameter_hash=PARAM_HASH,
           source_hashes={k:sha(p) for k,p in paths.items()},script_sha256=sha(__file__),
           production_source_hashes={f:sha(ROOT/f) for f in ['pk-engine/src/main/kotlin/net/plainnotes/app/pk/LabFit.kt','app/src/main/java/net/plainnotes/app/conc/ConcentrationCalculator.kt','app/src/main/java/net/plainnotes/app/conc/CalibrationEligibility.kt','app/src/main/java/net/plainnotes/app/ui/ConcChart.kt','app/src/main/java/net/plainnotes/app/ui/ChartViewport.kt','app/src/main/java/net/plainnotes/app/export/Exports.kt']},
           population_error=population_error,fit_error=fit_error,prior_error=prior_error,eligibility_case_error=gate_error,
           unchanged_no_lab_display_error=unchanged,before=data['before'],after=data['after'],
           unresolved=['P1-C2 all-outlier bookkeeping','No result-known timestamp: reconstruction is by sampling time','P2 population/external clinical accuracy not established'])
    out=Path(args.output);out.mkdir(parents=True,exist_ok=True)
    for key in ['after','engine','calculator','eligibility']:shutil.copyfile(paths[key],out/f'sublingual-p1c1-{key}.json')
    (out/'sublingual-p1c1-report.json').write_text(json.dumps(r,ensure_ascii=False,indent=2)+'\n')
    text=['# P1-C1 因果时间边界验收','', '合成软件验证通过；不是临床准确性验证。采样时间截止，不是结果获知时间回放。','',
          '| 情景 | 旧t0中心 | 新t0中心 | 新p5 / p25 / p75 / p95 | 历史摘要计数 | 采样后计数 |',
          '| --- | --- | --- | --- | --- | --- |']
    before={x['id']:x for x in data['before']['cases']}
    for a in data['after']['cases']:
        v=a['values_center_p5_p25_p75_p95'];b=before[a['id']]
        text.append(f"| {a['id']} | {b['values_center_p5_p25_p75_p95'][0]:.9f} | {v[0]:.9f} | {' / '.join(f'{n:.9f}' for n in v[1:])} | {a['summary_count']} | {a['after_sample_count']} |")
    text+=['',f'未校准原显示及分位最大误差{unchanged:.12g}。人口/P1-A拟合/先验/P1-B六资格案例最大误差：{population_error:.12g}/{fit_error:.12g}/{prior_error:.12g}/{gate_error:.12g}。',
           '未来400/800/同时间新增/移除均P1-B合格，查询前不参与；原始点保留。采样后实际影响曲线，不是禁用校准。',
           '局部同前缀插值：两个端点来自同一个查询前缀模型及MC，图层只连接同拟合段。源码、参数、取消/预算、性能和CI计数见sublingual-p1c1-verification.md。',
           '旧P0/P1-A/P1-B不覆写，只读重放；P1-C2和P2未实施。']
    (out/'sublingual-p1c1-report.md').write_text('\n'.join(text)+'\n');return r

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--before',default=ROOT/'docs/pk-research/results/sublingual-p1c1-before.json')
    p.add_argument('--after',default=ROOT/'app/build/reports/pk-p1c1/causal-after.json')
    p.add_argument('--engine',default=ROOT/'pk-engine/build/reports/pk-p1c1/engine-outputs.json')
    p.add_argument('--calculator',default=ROOT/'app/build/reports/pk-p1c1/calculator-audit.json')
    p.add_argument('--eligibility',default=ROOT/'app/build/reports/pk-p1c1/eligibility-audit.json')
    p.add_argument('--output',default=ROOT/'docs/pk-research/results')
    r=run(p.parse_args());print('P1-C1 sampling-time noninterference:',r['sampling_time_noninterference_acceptance'],'; clinical accuracy:',r['clinical_accuracy_established'])
