#!/usr/bin/env python3
"""Original mathematical audit charts; no reproduction of copyrighted journal image."""
import json, sys
from pathlib import Path
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
ROOT=Path(__file__).resolve().parents[1]
D=json.loads((ROOT/'data'/'p2ai-source-constraints.json').read_text())
F=ROOT/'figures';F.mkdir(exist_ok=True)
plt.rcParams['font.size']=10

def save(name):
 plt.tight_layout();plt.savefig(F/name,dpi=165);plt.close()

b=D['baseline_scenarios'];xs=[x['baseline_pg_ml'] for x in b]; y=[x['trapezoid_baseline_corrected_auc'] for x in b]
plt.figure(figsize=(8.3,4.8))
plt.plot(xs,y,marker='o',label='P2-U Figure 1 approximate trapezoid')
plt.plot(xs,[a+D['trapezoid_audit']['max_all_manual_point_width_auc'] for a in y],linestyle='--',label='All P2-U analyst reading widths shifted upward')
plt.axhline(2109,linestyle=':',label='Price Table 1 reported AUC0-24: 2109')
plt.xlabel('Hypothetical pre-dose E2 baseline (pg/mL)')
plt.ylabel('Baseline-corrected AUC0-24 (pg h/mL)')
plt.title('Price 1997: archived Figure 1 cannot reconstruct reported Table 1 AUC')
plt.grid(alpha=.2);plt.legend(loc='best',fontsize=8)
save('p2ai-auc-mismatch-baseline.png')

points=D['point_contributions'][1:]
plt.figure(figsize=(8.4,4.8))
plt.bar([str(round(x['time_h'],2)) for x in points],[x['maximum_read_width_auc_effect'] for x in points])
plt.xlabel('Post-dose sampling time (h)')
plt.ylabel('AUC change if point increased by full analyst reading width (pg h/mL)')
plt.title('AUC leverage by timepoint (all bars total 185.25)')
plt.grid(alpha=.2,axis='y')
save('p2ai-auc-timepoint-leverage.png')

arms=[a for a in D['table1_reported_study_arms'] if a['route']=='SL']
plt.figure(figsize=(8.1,4.8))
plt.plot([a['dose_mg'] for a in arms],[a['auc_per_mg'] for a in arms],marker='o',label='Mean AUC0-24 per mg')
plt.axhline(2109,linestyle='--',label='Constant-dose-scaling reference from SL 1mg')
plt.xlabel('Single sublingual E2 dose (mg)')
plt.ylabel('Reported study-arm mean AUC per mg (pg h/mL per mg)')
plt.title('Price 1997: dose-normalized group AUCs (descriptive, n=6)')
plt.grid(alpha=.2);plt.legend(fontsize=8)
save('p2ai-sl-dose-normalized-auc.png')
print('saved:',*[p.name for p in sorted(F.glob('*.png'))],sep='\n')
