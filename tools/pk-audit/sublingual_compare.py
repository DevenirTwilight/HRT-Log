#!/usr/bin/env python3
"""P0 test/research only. Independent ODE arithmetic; no Featherline source or production fitting writes.

Run Kotlin tests first. Literature observations are read only from the cited evidence file.
Generated time points are model samples, never additional human observations.
"""
import argparse
import csv
import hashlib
import json
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
TIMES = [.25, .5, .75, 46 / 60, 1, 1.5, 2, 3, 4, 6, 8, 12, 24]


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def bateman(t, absorption, elimination):
    """Unit oral-depot input: solve dA/dt=-ka A, dX/dt=ka A-ke X.
    This is a general textbook equation, independently implemented, not project source reuse.
    """
    if t < 0:
        return 0.0
    delta = abs(absorption - elimination)
    if delta < 1e-12:
        return absorption * t * math.exp(-elimination * t)
    return absorption / delta * math.exp(-min(absorption, elimination) * t) * -math.expm1(-delta * t)


def fitted(t, model):
    if t < 0:
        return 0.0
    return math.fsum(term['A_per_mg'] * math.exp(-term['lambda_per_h'] * t) *
                     -math.expm1(-(model['ka_per_h'] - term['lambda_per_h']) * t) for term in model['terms'])


def current(t, parameters, route='sublingual'):
    if route == 'oral':
        return fitted(t, parameters['models']['E2_ORAL'])
    sl = parameters['models']['E2_SL']
    return fitted(t, sl) + sl['swallowed_share'] * fitted(t, parameters['models']['E2_ORAL'])


def feather(t, meta, weight=80, route='sublingual'):
    """Necessary publicly stated numeric inputs + independently solved parallel-depot ODE."""
    slow = meta['oral_bioavailability'] * bateman(t, meta['k_oral_per_h'], meta['k_elimination_per_h'])
    amount = slow if route == 'oral' else (meta['mucosal_fraction'] * meta['fast_bioavailability'] *
        bateman(t, meta['k_fast_per_h'], meta['k_elimination_per_h']) + (1 - meta['mucosal_fraction']) * slow)
    # 1 mg = 10^9 pg; V(L/kg)*kg*1000 = mL. E2 mg = active E2 mg here, no ester conversion.
    return 1e9 / (meta['volume_l_per_kg'] * weight * 1000) * amount


def population(events, model, parameters, meta, t):
    f = (lambda dt, route: current(dt, parameters, route)) if model == 'HRT_current' else (lambda dt, route: feather(dt, meta, route=route))
    return math.fsum(e['dose_mg'] * f(t - e['time_h'], e['route']) for e in events)


def trapezoid(values, dt=.005):
    return math.fsum((a + b) * dt / 2 for a, b in zip(values, values[1:]))


def simpson(f, upper=8, n=4000):
    step = upper / n
    return step / 3 * (f(0) + f(upper) + 4 * math.fsum(f(i * step) for i in range(1, n, 2)) +
                      2 * math.fsum(f(i * step) for i in range(2, n, 2)))


def surrogate_profiles(parameters):
    """Demonstration of structural non-identifiability, NOT proposed human-validated parameters.
    Gamma input shape n is varied. Solve C(1)=144, C'(1)=0 and the SAME model-derived proxy AUC.
    Hold E2_ORAL fixed only for this demonstration; original Doll absolute AUC is not available.
    """
    oral = parameters['models']['E2_ORAL']
    sl = parameters['models']['E2_SL']
    target = sl['checks']['auc0_8']
    oral1 = fitted(1, oral)
    oral_prime1 = math.fsum(x['A_per_mg'] * (-x['lambda_per_h'] * math.exp(-x['lambda_per_h']) +
                          oral['ka_per_h'] * math.exp(-oral['ka_per_h'])) for x in oral['terms'])
    profiles = []
    for shape in [1, 1.2, 1.5, 2, 3]:
        def response(share):
            peak_fast = 144 - share * oral1
            rate = shape + share * oral_prime1 / peak_fast
            amplitude = peak_fast * math.exp(rate)
            f = lambda t: (amplitude * t**shape * math.exp(-rate*t) if t > 0 else 0) + share * fitted(t, oral)
            return f, rate, amplitude
        lo, hi = 0.0, 1.0
        if not simpson(response(lo)[0]) <= target <= simpson(response(hi)[0]):
            profiles.append({'shape': shape, 'status': 'no solution within share 0..1'}); continue
        for _ in range(55):
            mid = (lo + hi)/2
            if simpson(response(mid)[0]) < target: lo = mid
            else: hi = mid
        share = (lo + hi)/2
        f, rate, amplitude = response(share)
        profiles.append({'shape': shape, 'rate': rate, 'amplitude': amplitude, 'swallowed_coefficient': share,
                         'c_at_1h': f(1), 'derivative_at_1h': (f(1+1e-5)-f(1-1e-5))/2e-5,
                         'auc0_8_proxy': simpson(f), 'c_at_24h': f(24),
                         'status': 'mathematical surrogate; not physiology or independent validation'})
    return profiles


def run(engine_path, calculator_path, output):
    params_path = ROOT / 'pk-engine/src/main/resources/pk-params.json'
    evidence_path = ROOT / 'pk-engine/src/test/resources/sublingual-literature-validation.json'
    p = json.loads(params_path.read_text()); evidence = json.loads(evidence_path.read_text())
    assert sha(params_path) == evidence['production_params_sha256'], 'Published parameters changed; audit baseline must be reviewed'
    meta = evidence['comparison_model']
    engine = json.loads(Path(engine_path).read_text()); calculator = json.loads(Path(calculator_path).read_text())
    assert engine['synthetic_only']
    out = Path(output); out.mkdir(parents=True, exist_ok=True)
    rows, cases = [], {}
    max_difference = 0.0
    for case in engine['population_cases']:
        case_results = {}
        for model in ['HRT_current', 'Featherline_population']:
            values = [population(case['events'], model, p, meta, t) for t in TIMES]
            if model == 'HRT_current':
                max_difference = max(max_difference, max(abs(a-b) for a,b in zip(values, case['concentration_pg_ml'])))
                assert max_difference < 1e-6, 'Independent arithmetic disagrees with actual Kotlin'
            for t, value in zip(TIMES, values): rows.append([case['id'], model, t, value, 'synthetic_model_sample_not_human_data'])
            fine = [population(case['events'], model, p, meta, i*.005) for i in range(4801)]
            peak_i = max(range(len(fine)), key=fine.__getitem__)
            metrics = {'cmax_0_24_pg_ml': fine[peak_i], 'tmax_0_24_h': peak_i*.005,
                       'auc0_8_pg_h_ml': trapezoid(fine[:1601]), 'auc0_24_pg_h_ml': trapezoid(fine),
                       'pre_current_dose_pg_ml': fine[0], 'post_stop_48h_pg_ml': population(case['events'], model, p, meta, 48),
                       'c_46min_pg_ml': values[3]}
            if model == 'HRT_current':
                for key in metrics.keys() - {'c_46min_pg_ml'}:
                    assert abs(metrics[key]-case[key]) < 2e-6, f'Kotlin metrics disagreement: {case["id"]}/{key}'
            case_results[model] = metrics
        cases[case['id']] = case_results
    with (out/'sublingual-p0-curves.csv').open('w', newline='') as f:
        w=csv.writer(f); w.writerow(['synthetic_case','model','time_h','concentration_pg_ml','data_type']); w.writerows(rows)
    records = {r['id']:r for r in evidence['records']}
    external = []
    for model in ['HRT_current', 'Featherline_population']:
        fn = lambda t: current(t,p) if model=='HRT_current' else feather(t,meta)
        obs = records['pines_60min']
        pred = 4*fn(1)
        external.append({'study':'Pines1998','metric':'4 mg, 60 min mean; not true Cmax','model':model,'observed_pg_ml':obs['value'],
                         'predicted_pg_ml':pred,'error_pg_ml':pred-obs['value'],'relative_error':pred/obs['value']-1,
                         'spread':obs['reported_plusminus'],'spread_type':obs['spread_type'],
                         'status':'external disagreement/heterogeneity assessment; assay/baseline/weight uncertain; no clinical pass claim'})
        obs = records['yaish_90min']; trough = records['yaish_trough']['value']/3.671
        observed = obs['value']/3.671; sd = obs['sd']/3.671
        case = next(c for c in engine['population_cases'] if c['id']=='yaish_regular_q6h_0.5mg')
        raw = population(case['events'],model,p,meta,1.5)
        pre = population(case['events'],model,p,meta,0)
        conditioned = raw-pre+trough
        external.append({'study':'Yaish2023','metric':'0.5 mg q~6h, fixed 90-min mean; not true Cmax','model':model,
                         'observed_pg_ml':observed,'sd_pg_ml':sd,'raw_regular_history_prediction_pg_ml':raw,
                         'trough_conditioned_prediction_pg_ml':conditioned,'conditioned_error_pg_ml':conditioned-observed,
                         'conditional_z_difference_in_reported_sd':(conditioned-observed)/sd,
                         'status':'conditional on constant observed group trough; not person-level validation or assay harmonization',
                         'uncertainty_note':'No SD for paired increment without covariance; do not treat observed treatment trough as endogenous baseline'})
        obs = records['burnier_1h_fold']; fold = (0.5*fn(1)+24)/24
        external.append({'study':'Burnier1981','metric':'0.5 mg, 1 h fold, assuming constant reported 24 pg/mL baseline','model':model,
                         'observed_fold':obs['value'],'conditional_predicted_fold':fold,'error_fold':fold-obs['value'],
                         'status':'abstract-only conditional fold comparison; no fabricated original absolute 1h concentration'})
    profiles = surrogate_profiles(p)
    full = {'schema_version':1,'input_sha256':{'production_params':sha(params_path),'evidence':sha(evidence_path),
             'actual_kotlin_engine_outputs':sha(engine_path),'actual_calculator_audit':sha(calculator_path),'tool':sha(__file__)},
            'synthetic_only':True,'not_clinical_validation':True,'published_parameters_modified':False,
            'comparison_commit':meta['commit'],'max_independent_kotlin_sample_difference_pg_ml':max_difference,
            'cases':cases,'external_summary_comparisons':external,'surrogate_identifiability_profiles':profiles,
            'actual_kotlin_rate_and_fit_audit':{k:engine[k] for k in ['rate_sensitivity','prior_band_46min','synthetic_fits']},
            'actual_calculator_audit':calculator,'third_human_validated_candidate_status':'not identified; no invented parameter set',
            'scientific_acceptance':'NOT established: current population model does not reproduce independent fixed-time summaries across studies; heterogeneous assays/populations prevent universal ranking'}
    (out/'sublingual-p0-report.json').write_text(json.dumps(full,ensure_ascii=False,indent=2)+'\n')
    text=['# 舌下 P0 自动对照报告','',
          '输入为实际 Kotlin 测试导出的合成给药及已核对原文摘要。数值对照成功不等于科学验证通过；独立人体外部验证尚不充分，当前模型与多项摘要存在明显差异。生产参数未修改。',
          '',f'参数 SHA256 `{sha(params_path)}`；Featherline固定公开参数提交 `{meta["commit"]}`，体重80kg。',
          f'独立稳定算式与实际Kotlin逐点最大差异：{max_difference:.9g} pg/mL。每个指标数值积分步长0.005h，峰值时间为此网格，不冒充人体实测。','',
          '| 时间 | HRT单次2mg pg/mL | Featherline单次2mg pg/mL |','|---|---:|---:|']
    for t in TIMES: text.append(f'| {t*60:g}分钟 | {2*current(t,p):.6f} | {2*feather(t,meta):.6f} |')
    text+=['','| 合成情形 | 模型 | C46min | Cmax0–24 | Tmax h | AUC0–8 | AUC0–24 | 当前剂量前谷 | 停药后48h |','|---|---|---:|---:|---:|---:|---:|---:|---:|']
    for name, result in cases.items():
        for model,v in result.items():text.append(f'| {name} | {model} | '+ ' | '.join(f'{v[k]:.6f}' for k in ['c_46min_pg_ml','cmax_0_24_pg_ml','tmax_0_24_h','auc0_8_pg_h_ml','auc0_24_pg_h_ml','pre_current_dose_pg_ml','post_stop_48h_pg_ml'])+' |')
    text+=['','连续情形给药仅到t=0为止；0–24h是最后一次之后的下降，**不是停止后仍包含未来剂量的稳态日AUC**。daily_2mg为2mg/day，twice_daily_2mg为4mg/day；比较频率时使用daily_4mg对twice_daily_2mg，避免把总日量变化误当频率效应。yaish_regular只是合成等间隔历史，不是研究每个人的实际历史。','',
           '| 独立研究 | 模型 | 确实报告的观察 | 对应计算 | 比较限制 |','|---|---|---|---|---|']
    for r in external:
        if r['study']=='Pines1998': obs=f"{r['observed_pg_ml']:.1f}±704 pg/mL（±类型未知）"; pred=f"{r['predicted_pg_ml']:.2f}，误差{r['error_pg_ml']:+.2f}"
        elif r['study']=='Yaish2023':obs=f"{r['observed_pg_ml']:.2f}±{r['sd_pg_ml']:.2f} pg/mL（SD）";pred=f"原始历史{r['raw_regular_history_prediction_pg_ml']:.2f}；条件谷校正{r['trough_conditioned_prediction_pg_ml']:.2f}"
        else:obs='1h相对基线26倍';pred=f"条件预测{r['conditional_predicted_fold']:.2f}倍"
        text.append(f"| {r['study']} | {r['model']} | {obs} | {pred} | {r['status']} |")
    text+=['','Doll144/1h属于拟合目标，不能算独立命中。Pines只有60min、Yaish只有90min及晨谷，不能构造整段“人体曲线”；不能说某模型在15/30/45min吻合这些研究，因为没有取得对应实测。Featherline在Pines60min和Yaish90min摘要数值上较接近，不构成总体/个体更准确的证明。','',
           '## 吞咽项结构不可辨识演示','',
           '以下模型只拟合同一**模型生成代理**AUC390.6484、C(1)=144和C′(1)=0。变化快速输入形状后吞咽系数可很不同；不是人体数据，不是已验证候选，也不用于界面。','',
           '| Gamma形状 n | 吞咽系数 | C1h | C′1h | 代理AUC0–8 | C24h |','|---:|---:|---:|---:|---:|---:|']
    for v in profiles:
        if 'swallowed_coefficient' in v:text.append('| '+' | '.join(f'{v[k]:.8g}' for k in ['shape','swallowed_coefficient','c_at_1h','derivative_at_1h','auc0_8_proxy','c_at_24h'])+' |')
    text+=['','## 实际Kotlin校准敏感性','', '| 速率倍率 | 单次2mg C46min pg/mL |','|---:|---:|']
    for v in engine['rate_sensitivity']:text.append(f"| {v['rate_factor']} | {v['c_46min_pg_ml']:.6f} |")
    b=engine['prior_band_46min'];text += ['',f"现有默认无化验5–95%先验带：{b['p5']:.6f}–{b['p95']:.6f} pg/mL，中心{b['center']:.6f}。这是原软件输出的已证实参数化病态，不能称可靠人体区间。",'',
           '## 应用历史与因果路径实测','', '```json',json.dumps(calculator,ensure_ascii=False,indent=2),'```','',
           '完整机器结果还记录单点/同相位/不同相位化验的参数及协方差；所有化验为合成。确认缺陷和修复规格见[校准技术审计](../sublingual-calibration-audit.md)。']
    (out/'sublingual-p0-report.md').write_text('\n'.join(text)+'\n')
    return full


if __name__ == '__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--engine',default=str(ROOT/'pk-engine/build/reports/pk-p0/engine-outputs.json'))
    parser.add_argument('--calculator',default=str(ROOT/'app/build/reports/pk-p0/calculator-audit.json'))
    parser.add_argument('--output',default=str(ROOT/'docs/pk-research/results'))
    args=parser.parse_args()
    result=run(args.engine,args.calculator,args.output)
    print(json.dumps({'arithmetic_match_max_pg_ml':result['max_independent_kotlin_sample_difference_pg_ml'],
                      'scientific_acceptance':result['scientific_acceptance'],'output':args.output},ensure_ascii=False))
