"""Original research-only diagnostic charts from the manual pixel table."""
import json
from pathlib import Path
import matplotlib.pyplot as plt
ROOT=Path(__file__).resolve().parents[1]
raw=json.loads((ROOT/'data/p2aj-results.json').read_text(encoding='utf-8'))
pts=raw['digitized_points']
out=ROOT/'figures';out.mkdir(exist_ok=True)
t=[p['hour'] for p in pts];v=[p['value_pg_ml'] for p in pts];err=[p['visual_window_pg_ml'] for p in pts]
fig,ax=plt.subplots(figsize=(9,5));ax.errorbar(t,v,yerr=err,marker='o',capsize=3,label='P2-AJ: facsimile Figure 1 pixel reading (stress windows)')
ax.plot(t,[450,225,116,85,56,45,34,25,24],marker='x',linestyle=':',label='P2-U previous approximate readings')
ax.set_xlabel('Hours after 1 mg SL');ax.set_ylabel('Figure 1 mean serum E2 (pg/mL)')
ax.set_title('Price 1997 Figure 1: independent pixel-to-concentration reread')
ax.legend(fontsize=8);ax.grid(alpha=.25);fig.tight_layout();fig.savefig(out/'p2aj-new-vs-p2u.png',dpi=180);plt.close(fig)
sc=raw['baseline_scenarios'];bs=[x['assumed_b_pg_ml'] for x in sc]
fig,ax=plt.subplots(figsize=(9,5));ax.plot(bs,[x['baseline_subtracted_auc_pg_h_ml'] for x in sc],marker='o',label='Figure 1 trapezoid, nominal')
ax.plot(bs,[x['generous_all_points_up_upper_bound'] for x in sc],marker='s',label='All point readings + broad visual stress windows')
ax.axhline(raw['author_table_auc_pg_h_ml'],linestyle='--',label='Price Table 1, mean AUC = 2109')
ax.set_xlabel('Assumed mean pre-dose E2 (pg/mL; not observed)')
ax.set_ylabel('Baseline-corrected AUC 0-24 h (pg h/mL)');ax.set_title('Primary facsimile Figure 1 vs Table 1: unresolved AUC gap')
ax.grid(alpha=.25);ax.legend(fontsize=8);fig.tight_layout();fig.savefig(out/'p2aj-auc-sensitivity.png',dpi=180);plt.close(fig)
print('wrote two charts')
