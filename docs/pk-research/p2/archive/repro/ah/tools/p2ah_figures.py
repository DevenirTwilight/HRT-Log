#!/usr/bin/env python3
"""Render explanatory graphs for P2-AH. Existing digitized points are approximation, not study data re-release."""
from pathlib import Path
import json
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
import numpy as np
BASE=Path(__file__).resolve().parents[1]
obj=json.loads((BASE/'data/p2ah-price-auc-audit.json').read_text())
out=BASE/'figures';out.mkdir(exist_ok=True)
s=obj['scenarios'];b=np.array([r['assumed_predose_pg_ml'] for r in s]);auc=np.array([r['figure_baseline_corrected_auc_0_24_pg_h_ml'] for r in s]); upper=np.array([r['read_width_based_upper_auc_0_24'] for r in s]);table=2109
plt.figure(figsize=(9,5.8))
plt.plot(b,auc,'o-',label='Digitized Figure 1, baseline-corrected trapezoid')
plt.plot(b,upper,'s--',label='Upper envelope under manually assumed digitization widths')
plt.axhline(table,ls=':',label='Published Table 1 mean AUC0–24 (2109)')
plt.fill_between(b,auc,upper,alpha=.12)
plt.xlabel('Hypothetical predose E2 (pg/mL) — not measured in archived data')
plt.ylabel('0–24 h AUC (pg·h/mL)')
plt.title('P2-AH: published AUC cannot be reproduced from archived Figure 1 points')
plt.legend(loc='upper right',fontsize=8);plt.grid(alpha=.2);plt.tight_layout();plt.savefig(out/'p2ah-figure-vs-table-auc.png',dpi=170);plt.close()
h=obj['baseline_sensitive_apparent_half_life'];bs=np.array([i['assumed_predose_pg_ml'] for i in h if i['apparent_8_24_h'] is not None]);lh=np.array([i['apparent_8_24_h'] for i in h if i['apparent_8_24_h'] is not None]);
plt.figure(figsize=(9,5.8));plt.plot(bs,lh,'o-');plt.axhline(18.,ls=':',label='Published individual-based t1/2 mean = 18 h (NOT this mean-curve slope)')
plt.xlabel('Assumed predose E2 (pg/mL)');plt.ylabel('Apparent t1/2 from group mean 8h and 24h (h)')
plt.title('P2-AH: late apparent rate from total group means depends on baseline')
plt.grid(alpha=.2);plt.legend(fontsize=8);plt.tight_layout();plt.savefig(out/'p2ah-baseline-vs-apparent-t12.png',dpi=170);plt.close()
print('Saved two figures')
