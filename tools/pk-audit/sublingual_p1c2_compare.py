#!/usr/bin/env python3
"""Actual Kotlin disposition evidence; model residuals are not clinical verdicts. Old evidence read-only."""
import argparse,json,shutil
from pathlib import Path
from sublingual_compare import ROOT,sha
from sublingual_p1a_compare import PARAM_HASH
from sublingual_p1c1_compare import close,validate as validate_c1

def validate(before,engine,app):
 assert before['synthetic_only'] and engine['synthetic_only'] and app['synthetic_only']
 assert before['engine']['actual_post_count']==1 and before['engine']['claimed_excluded']==['tail']
 assert before['application']['eligible'] and before['application']['actual_post_count']==1 and before['application']['claimed_excluded']==['l1']
 rows={r['id']:r for r in engine['cases']}
 assert set(rows)=={'single_warning','all_warning','partial','normal','empty'}
 for r in rows.values():
  c,u,x,w=map(set,[r['candidates'],r['used'],r['excluded'],r['warnings']])
  assert u.isdisjoint(x) and u|x==c and w<=c and r['count']==len(u)
  assert r['rate_log']==0 and r['covariance'][1:]==[0,0,0]
  assert r['count']==len(c)-len(x)
 for key in ['single_warning','all_warning']:
  r=rows[key];assert set(r['warnings'])==set(r['used'])==set(r['candidates']) and not r['excluded']
 assert rows['single_warning']['count']==1 and rows['all_warning']['count']==2
 assert rows['partial']['excluded']==['tail'] and rows['partial']['used']==['a','b','c']
 for key in ['amplitude_log','covariance','center','p5','p25','p75','p95']:close(rows['partial'][key],rows['normal'][key])
 assert not rows['normal']['warnings'] and not rows['empty']['used']
 assert abs(rows['single_warning']['amplitude_log']-before['engine']['log_amplitude'])<1e-10
 close(rows['single_warning']['covariance'],before['engine']['covariance'])
 apps={r['id']:r for r in app['cases']};a=apps['single_warning']
 assert a['raw_count']==a['eligible_count']==a['available_count']==a['actual_count']==1
 assert a['warnings']==a['used']==['l4'] and not a['excluded']
 close(a['covariance'],before['application']['covariance'])
 for key in ['amplitude_log','covariance','values']:close(apps['partial'][key],apps['normal'][key])
 assert apps['partial']['raw_count']==apps['partial']['eligible_count']==apps['partial']['available_count']==4
 assert apps['partial']['actual_count']==3 and apps['partial']['excluded']==['l4']
 assert apps['empty']['actual_count']==0
 return True

def run(a):
 paths={k:Path(getattr(a,k)) for k in ['before','engine','app','population','calculator','eligibility','causal']}
 data={k:json.loads(p.read_text()) for k,p in paths.items()}
 assert sha(ROOT/'pk-engine/src/main/resources/pk-params.json')==PARAM_HASH
 validate(data['before'],data['engine'],data['app'])
 old=ROOT/'docs/pk-research/results'
 old_pop=json.loads((old/'sublingual-p1c1-engine.json').read_text());old_gate=json.loads((old/'sublingual-p1c1-eligibility.json').read_text())
 errors={k:close(old_pop[k],data['population'][k]) for k in ['population_cases','synthetic_fits','prior_band_46min']}
 errors['history_cases']=close(old_gate['cases'],data['eligibility']['cases'])
 errors['causal']=validate_c1(json.loads((old/'sublingual-p1c1-before.json').read_text()),data['causal'])
 assert data['calculator']['causal_interpolation_leak']['still_unfixed'] is False
 after={'synthetic_only':True,'engine':data['engine'],'application':data['app']}
 out=Path(a.output);out.mkdir(parents=True,exist_ok=True)
 (out/'sublingual-p1c2-after.json').write_text(json.dumps(after,ensure_ascii=False,indent=2)+'\n')
 report=dict(synthetic_only=True,software_acceptance=True,clinical_accuracy_established=False,parameter_hash=PARAM_HASH,algorithm_version=2,calculator_version=2,source_hashes={k:sha(v) for k,v in paths.items()},script_hash=sha(__file__),unchanged_errors=errors,policy='all-warning: retain all, excluded empty; partial: existing one-pass removal/refit',unresolved=['No result-known timestamp','P2 population/clinical external accuracy unestablished'],production_source_hashes={f:sha(ROOT/f) for f in ['pk-engine/src/main/kotlin/net/plainnotes/app/pk/LabFit.kt','app/src/main/java/net/plainnotes/app/conc/ConcentrationCalculator.kt','app/src/main/java/net/plainnotes/app/ui/ConcentrationScreen.kt']})
 (out/'sublingual-p1c2-report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
 text=['# P1-C2 拟合集合与警告验收','','仅合成软件验收；残差不是化验错误诊断，人体准确性未确立。','','| 情景 |候选|实际参与|真实排除|警告|','|---|---|---|---|---|']
 for r in data['engine']['cases']:text.append('|'+ '|'.join([r['id'],str(len(r['candidates'])),str(r['count']),str(len(r['excluded'])),str(len(r['warnings']))])+'|')
 text+=['','旧单条观测actual=1、claimed excluded=1；新used=1、warning=1、excluded=0。旧/新实际MAP及协方差不变；部分异常与独立保留子集的中心和四分位/协方差一致。',f'人口/P1-A/历史资格/C1非干涉误差：{errors}。','原始点、人口参数、Build25/schema9/Calculator2/冻结1/2不变；严格结果获知时间/P2另案。完整命令/性能/UI/API35/CI见sublingual-p1c2-verification.md。']
 (out/'sublingual-p1c2-report.md').write_text('\n'.join(text)+'\n');return report
if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__)
 defaults={'before':'docs/pk-research/results/sublingual-p1c2-before.json','engine':'pk-engine/build/reports/pk-p1c2/disposition-engine.json','app':'app/build/reports/pk-p1c2/disposition-app.json','population':'pk-engine/build/reports/pk-p1c2/engine-outputs.json','calculator':'app/build/reports/pk-p1c2/calculator-audit.json','eligibility':'app/build/reports/pk-p1c2/eligibility-audit.json','causal':'app/build/reports/pk-p1c2/causal-after.json','output':'docs/pk-research/results'}
 for k,v in defaults.items():p.add_argument('--'+k,default=ROOT/v)
 r=run(p.parse_args());print('P1-C2 software acceptance:',r['software_acceptance'],'; clinical accuracy:',r['clinical_accuracy_established'])
