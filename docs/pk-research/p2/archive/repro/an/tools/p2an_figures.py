#!/usr/bin/env python3
from pathlib import Path
import json
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
ROOT=Path(__file__).resolve().parents[1]
r=json.loads((ROOT/'results/p2an-analysis.json').read_text())
out=ROOT/'figures';out.mkdir(exist_ok=True)
scenarios=['complete','mcar','drop_late_evaluations','signal_dependent_mnar']
labels=['Complete 2-8h','MCAR: 35% missing','Time-dependent 6/8h loss','Signal-dependent loss']
values=[r['missingness'][k]['subjects_equal_weight_delta_mockB_minus_mockA'] for k in scenarios]
fig,ax=plt.subplots(figsize=(9,4.7))
ax.bar(labels,values)
ax.axhline(0,color='black',lw=1)
ax.set_ylabel('Mean paired MAE delta: MOCK B − MOCK A (dimensionless)')
ax.set_title('Synthetic missingness changes the observed model ranking')
ax.tick_params(axis='x',rotation=18)
fig.tight_layout();fig.savefig(out/'p2an-missingness-ranking.png',dpi=170);plt.close(fig)
fig,ax=plt.subplots(figsize=(8.2,4.2))
for k,label in zip(scenarios,labels):
    vals=[r['missingness'][k]['retained_by_time'][str(float(t))] for t in (2,3,4,6,8)]
    ax.plot((2,3,4,6,8),vals,marker='o',label=label)
ax.set(xlabel='Post-dose evaluation time (h)',ylabel='Participants with a retained evaluation',title='Synthetic sample availability by time')
ax.legend(fontsize=8);fig.tight_layout();fig.savefig(out/'p2an-retention-by-time.png',dpi=170);plt.close(fig)
c=r['covariance']['covariance_delta_approx'];fig,ax=plt.subplots(figsize=(5,4.5));im=ax.imshow(c)
ax.set_xticks(range(3),labels=['2h','4h','8h']);ax.set_yticks(range(3),labels=['2h','4h','8h']);ax.set_title('Delta-method covariance of ratios')
for i in range(3):
    for j in range(3):ax.text(j,i,f'{c[i][j]:.5f}',ha='center',va='center',color='black',fontsize=9)
fig.colorbar(im,ax=ax,label='Dimensionless squared')
fig.tight_layout();fig.savefig(out/'p2an-ratio-covariance.png',dpi=170);plt.close(fig)
print('Figures saved', len(list(out.glob('*.png'))))
