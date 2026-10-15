#!/usr/bin/env python3
"""One diagnostic plot per figure; frozen candidates, synthetic time sensitivity."""
import json
from pathlib import Path
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
ROOT=Path(__file__).resolve().parents[1]
res=json.loads((ROOT/'results/p2am-clock-sensitivity.json').read_text())
plt.figure(figsize=(9,5))
for delta in [5,15,30]:
    rows=sorted((x for x in res['rows'] if x['clock_uncertainty_min']==delta),key=lambda x:x['sample_hour'])
    plt.plot([v['sample_hour'] for v in rows], [v['deltaH_sample_clock_max_over15'] for v in rows],marker='o',label=f'Sample-time uncertainty ±{delta} min')
plt.xscale('log');plt.yscale('log');plt.xlabel('Hours after hypothetical dose (log scale)')
plt.ylabel('Max |delta H| across 15 candidates (log scale)')
plt.title('P2-AM: conditional response sensitivity to recorded sample time')
plt.grid(alpha=.2);plt.legend();plt.tight_layout()
ROOT.joinpath('figures').mkdir(exist_ok=True)
plt.savefig(ROOT/'figures/p2am-clock-sensitivity.png',dpi=170);plt.close()
