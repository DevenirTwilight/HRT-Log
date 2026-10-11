"""Collect synthetic product preview measurement; distinct from the old comparison benchmark."""
import json,math,subprocess
from pathlib import Path
r=subprocess.run(['adb','logcat','-d','-v','raw','-s','Pr19DisplayPerf:I','*:S'],check=True,capture_output=True,text=True)
prefix='PR19_DISPLAY_PERFORMANCE_JSON='
lines=[s[len(prefix):] for s in r.stdout.splitlines() if s.startswith(prefix)]
if len(lines)!=1:raise ValueError('Expected exactly one product preview measurement')
j=json.loads(lines[0])
if j['protocol']!='price-figure-weighted-history-v1' or j['candidate']!='p2x-39' or j['fixture']!='synthetic-60-doses-over-30-days':raise ValueError('Unexpected protocol/input')
for k in ('compute_ms','chart_points','history_peak_pgml','chart_peak_pgml','process_pss_before_kib','process_pss_after_kib'):
 if isinstance(j[k],bool) or not isinstance(j[k],(int,float)) or not math.isfinite(j[k]) or j[k]<=0:raise ValueError(k)
if not 5041<=j['chart_points']<=12000 or abs(j['chart_peak_pgml']-j['history_peak_pgml'])>1e-5:raise ValueError('Grid accuracy or size violated')
p=Path('app/build/reports/pr19-display-performance.json');p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(j,indent=2)+'\n');print(j)
