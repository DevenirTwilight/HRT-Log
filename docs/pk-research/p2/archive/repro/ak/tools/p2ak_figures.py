#!/usr/bin/env python3
"""Research-only illustrations of subjective envelopes (not statistical confidence intervals)."""
import json
from pathlib import Path
import matplotlib.pyplot as plt
ROOT=Path(__file__).resolve().parents[1]
src=json.loads((ROOT/'data/p2ak-scan-visual-intervals.json').read_text())
res=json.loads((ROOT/'data/p2ak-results.json').read_text())
(Rootfig:=ROOT/'figures').mkdir(exist_ok=True)

fig,ax=plt.subplots(figsize=(9,5.5))
for name,arm in src['dose_arms'].items():
 t=[p['hour'] for p in arm['points']]
 c=[p['center'] for p in arm['points']]
 lo=[p['low'] for p in arm['points']];hi=[p['high'] for p in arm['points']]
 l,=ax.plot(t,c,marker='o',label=f'{name} provisional visual center')
 ax.fill_between(t,lo,hi,color=l.get_color(),alpha=0.17,label=f'{name} subjective envelope')
ax.set(xlabel='Hours after dose',ylabel='Serum E2 on scanned Figure 1 (pg/mL)',title='P2-AK | Low-dose scan readings (not validated digitization)')
ax.set_xticks(src['times_postdose_h']);ax.grid(alpha=.2);ax.legend(loc='upper right',fontsize=8)
fig.tight_layout();fig.savefig(Rootfig/'p2ak-visual-point-envelopes.png',dpi=175);plt.close(fig)

fig,ax=plt.subplots(figsize=(9,5))
labels=list(res['arms'])
for i,name in enumerate(labels):
 arm=res['arms'][name];vals=arm['visual_conditional_auc_zero_baseline'];target=arm['table_auc0_24_pg_h_ml']
 ax.plot([i,i],[vals['low'],vals['high']],linewidth=15,alpha=.24)
 ax.plot(i,vals['center'],marker='o',markersize=8,label='provisional Figure center' if i==0 else None)
 ax.plot(i,target,marker='D',markersize=8,label='Table 1 AUC (published)' if i==0 else None)
 ax.annotate(f'{target}',(i,target),xytext=(9,3),textcoords='offset points',fontsize=9)
ax.set_xticks(range(len(labels)),labels);ax.set_ylim(300,1350)
ax.set_ylabel('AUC 0-24h (pg*h/mL)')
ax.set_title('P2-AK | Conditional zero-baseline visual AUC envelopes')
ax.legend();ax.grid(axis='y',alpha=.2)
fig.tight_layout();fig.savefig(Rootfig/'p2ak-auc-interval-vs-table.png',dpi=175);plt.close(fig)

fig,ax=plt.subplots(figsize=(9,5))
for name,arm in res['arms'].items():
 xx=[v['assumed_baseline_pg_ml'] for v in arm['scenario_results']]
 yy=[v['upper_raw_subtracted_auc_pg_h_ml']-arm['table_auc0_24_pg_h_ml'] for v in arm['scenario_results']]
 ax.plot(xx,yy,marker='o',label=name)
ax.axhline(0,linestyle='--',linewidth=1)
ax.set(xlabel='Hypothetical mean predose background (pg/mL)',ylabel='Maximum conditional AUC minus Table 1 AUC (pg*h/mL)',title='P2-AK | Background changes conditional feasibility')
ax.grid(alpha=.2);ax.legend();fig.tight_layout()
fig.savefig(Rootfig/'p2ak-baseline-feasibility.png',dpi=175);plt.close(fig)
print('wrote 3 P2-AK figures')
