#!/usr/bin/env python3
"""P1-B software/data eligibility acceptance. Old P0/P1-A evidence is read-only."""
import argparse
import json
import shutil
from pathlib import Path
from sublingual_compare import ROOT, sha
from sublingual_p1a_compare import PARAM_HASH


def validate(engine,calculator,eligibility):
    original=json.loads((ROOT/'docs/pk-research/results/sublingual-p1a-engine.json').read_text())
    errors=[]
    for old,new in zip(original['population_cases'],engine['population_cases']):
        assert old['id']==new['id'] and old['events']==new['events'] and old['time_h']==new['time_h']
        errors += [abs(a-b) for a,b in zip(old['concentration_pg_ml'],new['concentration_pg_ml'])]
    assert len(engine['population_cases'])==9 and max(errors)<1e-6
    assert original['synthetic_fits']==engine['synthetic_fits']
    assert original['prior_band_46min']==engine['prior_band_46min']
    rows={r['id']:r for r in eligibility['cases']}
    for key in ['old_treatment_lab','unknown_context_lab']:
        r=rows[key];assert r['baseline_pg_ml'] is None and r['fit_count']==0 and r['eligible_count']==0
        assert r['original_observations']==1 and abs(r['gated_pg_ml']-r['population_pg_ml'])<1e-8
    assert rows['old_treatment_lab']['decisions'][0]['baseline_eligibility']=='NOT_PRE_TREATMENT'
    for key in ['eligible_single_sl','eligible_mixed','bounded_old_sl']:
        r=rows[key];assert r['eligible_count']==1 and r['fit_count']==1 and r['baseline_pg_ml'] is None
        assert abs(r['gated_pg_ml']-r['population_pg_ml'])>1
    p=rows['partial_eligibility'];assert p['original_observations']==2 and p['eligible_count']==1 and p['fit_count']==1
    assert rows['eligible_single_sl']['rate']==1
    assert eligibility['resource_test']['eligible']==1000
    assert calculator['causal_interpolation_leak']['still_unfixed']
    return max(errors)


def run(engine_path,calculator_path,eligibility_path,output):
    paths={'engine':Path(engine_path),'calculator':Path(calculator_path),'eligibility':Path(eligibility_path)}
    inputs={k:json.loads(p.read_text()) for k,p in paths.items()}
    assert sha(ROOT/'pk-engine/src/main/resources/pk-params.json')==PARAM_HASH
    error=validate(inputs['engine'],inputs['calculator'],inputs['eligibility'])
    p0=json.loads((ROOT/'docs/pk-research/results/sublingual-p0-calculator.json').read_text())
    rows=inputs['eligibility']['cases'];by_id={r['id']:r for r in rows}
    report=dict(synthetic_only=True,software_eligibility_acceptance=True,clinical_accuracy_established=False,
        parameter_hash=PARAM_HASH,calculator_version=2,labfit_algorithm_version=2,population_max_absolute_error=error,
        source_hashes={k:sha(p) for k,p in paths.items()},script_sha256=sha(__file__),
        before=dict(case_A=p0['history_window_false_baseline'],case_B=p0['missing_context_false_baseline']),
        after=inputs['eligibility'],unresolved=['P1-C causal historical interpolation/unrestricted summary','P1-C all-outlier bookkeeping',
            'Explicit pre-treatment user confirmation/persistence absent','P2 population model/external clinical accuracy not established',
            'Omitted non-SL and unknown exposure conservatively excluded; no universal 180-day zero-residual claim'])
    out=Path(output);out.mkdir(parents=True,exist_ok=True)
    (out/'sublingual-p1b-report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
    for key,p in paths.items():shutil.copyfile(p,out/f'sublingual-p1b-{key}.json')
    text=['# P1-B 历史资格门控验收','', '纯合成数据。工程资格验收通过，不是人体外部科学验证。',
          f'人口曲线9组×13点与P1-A最大差异{error:.12g}；人口参数hash `{PARAM_HASH}`未变。P1-A拟合/先验带数值逐字段相同。','',
          '| 情景 | 人口pg/mL | 门控后pg/mL | 合格/实际拟合/原观测 | 基线 |','| --- | --- | --- | --- | --- |']
    text += [f"| {r['id']} | {r['population_pg_ml']:.8f} | {r['gated_pg_ml']:.8f} | {r['eligible_count']}/{r['fit_count']}/{r['original_observations']} | {r['baseline_pg_ml']} |" for r in rows]
    text += ['', '案例A旧500假基线/current777.767987 → 当前人口277.767987且不拟合；案例B旧220假基线 → 基线未知且不拟合，观测均保留。原始精确旧数字见只读P0 JSON与本报告before字段。','',
             '资格逐条状态、原因、证据记录及残余log上界见eligibility JSON。COMPLETE仅指已保存模型输入，不是现实全部治疗史。时间窗不是治疗开始；生产不生成确认治疗前基线。',
             '非SL窗口前暴露/未知给药无法由固定SL上界证明，保守不纳入；混合缺一不可把其余剂量冒充全部观测。',
             f"10000条证据×1000化验资格索引计时{inputs['eligibility']['resource_test']['seconds']:.6f}s（当前runner，非跨设备性能承诺）；无逐化验数据库查询。残余预算/取消及3000实际记录回归由Kotlin测试验证。",'',
             '仍未修复：',*['- '+s for s in report['unresolved']],'',
             '完整测试命令/计数、四语UI、冻结备份/PDF与API35产物回查见独立P1-B验收记录。']
    (out/'sublingual-p1b-report.md').write_text('\n'.join(text)+'\n')
    return report

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--engine',default=ROOT/'pk-engine/build/reports/pk-p1b/engine-outputs.json')
    p.add_argument('--calculator',default=ROOT/'app/build/reports/pk-p1b/calculator-audit.json')
    p.add_argument('--eligibility',default=ROOT/'app/build/reports/pk-p1b/eligibility-audit.json')
    p.add_argument('--output',default=ROOT/'docs/pk-research/results')
    a=p.parse_args();r=run(a.engine,a.calculator,a.eligibility,a.output)
    print('P1-B software eligibility acceptance:',r['software_eligibility_acceptance'],'; clinical accuracy established:',r['clinical_accuracy_established'])
