#!/usr/bin/env python3
"""Diagnostic graphics for the retrospective scenario grid (never probabilities)."""
import json
from pathlib import Path
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt

root=Path(__file__).resolve().parents[1]
a=json.loads((root/'results/p2al-audit.json').read_text())
rows=a['scenario_scores']; names=[f"cap{r['rosano_baseline_cap_pmol_l']} / b{r['assumed_price_baseline_pg_ml']}" for r in rows]
lo=[r['interval_delta_mse_min'] for r in rows];hi=[r['interval_delta_mse_max'] for r in rows];center=[r['center_delta_mse_m2_minus_m1'] for r in rows]
fig,ax=plt.subplots(figsize=(10,5.5))
for k,(l,h,c) in enumerate(zip(lo,hi,center)):
    ax.vlines(k,l,h,linewidth=2)
    ax.plot(k,c,marker='o',markersize=4)
ax.axhline(0,linestyle='--',linewidth=1)
ax.set_xticks(range(len(names)),names,rotation=45,ha='right')
ax.set_ylabel('MSE(M2) − MSE(M1), (pg/mL)²')
ax.set_title('Retrospective Price redigitization: conditional model ranking')
ax.text(.01,.98,'Positive: M1 smaller squared error   |   Negative: M2 smaller squared error\nBars: extreme subjective visual-read rectangles; not CIs',transform=ax.transAxes,va='top',fontsize=9)
fig.tight_layout();fig.savefig(root/'figures/p2al-paired-ranking-intervals.png',dpi=180);plt.close(fig)

v=a['identifiability']
fig,ax=plt.subplots(figsize=(7.5,4.5))
ax.bar(['0–8h relative AUC','24h predose index'],[v['P2_AF_refined_auc08_median_span_ratio'],v['P2_AF_refined_q24_median_span_ratio']])
ax.set_yscale('log');ax.set_ylim(1,100)
ax.set_ylabel('Median feasible max/min ratio (log scale)')
ax.set_title('P2-AF: two distinct stability regimes (synthetic conditional)')
for i,n in enumerate([v['P2_AF_refined_auc08_median_span_ratio'],v['P2_AF_refined_q24_median_span_ratio']]):ax.text(i,n*1.15,f'{n:.2f}×',ha='center')
fig.tight_layout();fig.savefig(root/'figures/p2al-function-robustness.png',dpi=180);plt.close(fig)
print('2 figures saved')
