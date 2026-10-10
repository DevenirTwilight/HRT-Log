"""Nonclinical visualization of the P2-X parameter ensemble. No chart represents clinical uncertainty."""
import json
from pathlib import Path
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
root=Path(__file__).resolve().parents[2]
source=root/'docs/pk-research/p2/p2x-conditional-ensemble-results.json'
out=root/'docs/pk-research/p2'
d=json.loads(source.read_text())
fig,ax=plt.subplots(figsize=(8,5))
for n in (4,6,8,10):
 rows=sorted((r for r in d['rows'] if r['n_fast']==n and r['rosano_baseline_cap_pmol_l']==225.),key=lambda a:a['price_fixed_baseline_pg_ml'])
 ax.plot([r['price_fixed_baseline_pg_ml'] for r in rows], [r['trough_index_per_1mg_q6_q12_q24']['24'] for r in rows],marker='o',label=f'input stages n={n}')
ax.set_yscale('log'); ax.set_xlabel('Assumed Price baseline (pg/mL)');ax.set_ylabel('Dimensionless q24 predose index (log scale)')
ax.set_title('P2-X: conditional tail sensitivity (Rosano cap 225 pmol/L)')
ax.legend();ax.grid(True,alpha=.3);fig.tight_layout();fig.savefig(out/'p2x-baseline-vs-q24.png',dpi=160);plt.close(fig)
fig,ax=plt.subplots(figsize=(8,5))
for n in (4,6,8,10):
 rows=sorted((r for r in d['rows'] if r['n_fast']==n and r['rosano_baseline_cap_pmol_l']==225.),key=lambda a:a['price_fixed_baseline_pg_ml'])
 ax.plot([r['price_fixed_baseline_pg_ml'] for r in rows], [r['score'] for r in rows],marker='o',label=f'n={n}')
ax.set_xlabel('Assumed Price baseline (pg/mL)');ax.set_ylabel('Study-balanced pseudo-loss (lower is better)')
ax.set_title('P2-X: loss versus baseline, not a likelihood')
ax.legend();ax.grid(True,alpha=.3);fig.tight_layout();fig.savefig(out/'p2x-baseline-vs-fit.png',dpi=160);plt.close(fig)
print('Generated 2 diagrams')
