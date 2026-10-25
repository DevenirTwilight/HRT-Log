"""Aggregate-only chart; no participant values or identifiers exported."""
import json
from pathlib import Path
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
ROOT=Path(__file__).resolve().parent
r=json.loads((ROOT/'audit'/'p2aq_aggregate_audit.json').read_text())
stats=r['VNC54']['data']['visit_stats']
medians=[x['postdose_pmol_l']['median'] for x in stats]
q1=[x['postdose_tukey_halves_iqr_pmol_l'][0] for x in stats]
q3=[x['postdose_tukey_halves_iqr_pmol_l'][1] for x in stats]
counts=[x['n_postdose'] for x in stats]
fig,ax=plt.subplots(figsize=(8.2,4.9))
x=[0,1]
ax.errorbar(x,medians,yerr=[[m-a for m,a in zip(medians,q1)],[b-m for m,b in zip(medians,q3)]],marker='o',linestyle='none',capsize=8,linewidth=2)
ax.set_xticks(x,labels=[f'Month 3 (n={counts[0]})',f'Month 6 (n={counts[1]})'])
ax.set_xlim(-.5,1.5)
ax.set_ylim(0,max(q3)*1.32)
ax.set_ylabel('Estradiol, 90 min after sublingual 0.5 mg (pmol/L)')
ax.set_title('Yaish VNC54: Published 90-minute subgroup, individual records re-audited')
ax.text(.5,-.20,'Median and half-sample IQR; same cohort, not independent external validation',transform=ax.transAxes,ha='center',fontsize=9)
ax.grid(axis='y',alpha=.2)
fig.tight_layout(rect=[0,.08,1,1])
path=ROOT/'figures'/'p2aq_vnc54_90min_aggregate.png'
fig.savefig(path,dpi=160)
plt.close(fig)
print(path)
