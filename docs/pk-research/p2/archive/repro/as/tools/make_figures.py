#!/usr/bin/env python3
import json,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
import legacy_audit as a
import numpy as np
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
root=a.ROOT/'figures';root.mkdir(exist_ok=True)
fig,ax=plt.subplots(figsize=(9,5.2));t=np.linspace(0,24,500)
ax.plot(t,[a.rel(float(x)) for x in t],lw=2.5,label='Production Legacy, anchored at 1h')
for b in [0,12,24]:
 p=a.stats_price(b)['points'];ax.plot([q['h'] for q in p],[q['observed_relative'] for q in p],marker='o',lw=1.6,label=f'Price digitized (assumed b={b} pg/mL)')
ax.axvspan(8,24,alpha=.10,label='Legacy outside fitted source time (≥8h)')
ax.axhline(0,color='black',lw=.5);ax.set_xlim(0,24);ax.set_ylim(-.025,1.08)
ax.set_xlabel('Hours since single dose');ax.set_ylabel('Relative to 1h increment (dimensionless)')
ax.set_title('Legacy production E2_SL vs Price 1997 Figure 1\nDiagnostic on previously exposed group averages, not external validation')
ax.legend(fontsize=8);fig.tight_layout();fig.savefig(root/'p2as-price-legacy-shape.png',dpi=175);plt.close(fig)
fig,ax=plt.subplots(figsize=(8.2,4.8));t=np.linspace(0,1,300);ax.plot(t,[a.rel(float(x)) for x in t],lw=2.5,label='Production Legacy')
for b in [0,100]:
 p=a.rosano(b)['data'];ax.plot([q['h'] for q in p],[q['observed_relative'] for q in p],marker='o',lw=1.6,label=f'Rosano (assumed b={b} pmol/L)')
ax.set_xlim(0,1);ax.set_ylim(0,1.15);ax.set_xlabel('Hours since single dose');ax.set_ylabel('Relative to 1h increment (dimensionless)')
ax.set_title('Legacy early-rise shape vs Rosano 1997 (published aggregates)');ax.legend();fig.tight_layout();fig.savefig(root/'p2as-rosano-early-rise.png',dpi=175);plt.close(fig)
fig,ax=plt.subplots(figsize=(8.2,4.8));b=[a.default_sl(1,tier=i) for i in range(4)];ax.bar(['2 min','5 min','10 min\n(default)','15 min'],b)
ax.set_ylabel('1mg at 1h model output (pg/mL, no background)');ax.set_title('Legacy hold-time extrapolation is an assumed tier rule\nNOT observed human concentration by hold time')
fig.tight_layout();fig.savefig(root/'p2as-hold-tier-rule.png',dpi=175);plt.close(fig)
print('saved',len(list(root.glob('*.png'))),'figures')
