#!/usr/bin/env python3
"""Make source-faithful figures from frozen P2-AE research result JSON."""
from __future__ import annotations
import json
from pathlib import Path
import numpy as np
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
ROOT=Path(__file__).resolve().parents[1]

def main():
    d=json.loads((ROOT/'data'/'p2ae-cross-candidate.json').read_text())
    schemes=['through24_no_pre','through24_with_pre','through48_with_pre']
    modes=['frozen_entire_shape','free_fast_slow_gains_fixed_ks','free_gains_and_slow_rate']
    titles=['Source rates + weight fixed','Slow weight free, rate fixed','Both slow weight and rate free']
    fig,ax=plt.subplots(figsize=(10.2,5.6))
    x=np.arange(3);width=.255
    for i,(m,title) in enumerate(zip(modes,titles)):
        vals=[d['results'][s]['summary'][m]['pairs_distance_ge_2'] for s in schemes]
        bars=ax.bar(x+(i-1)*width,vals,width,label=title)
        ax.bar_label(bars,fontsize=10,padding=2)
    ax.set_ylim(0,105);ax.set_ylabel('Candidate pairs with conservative distance >= 2 (of 105)')
    ax.set_xticks(x,['to 24h, no pre','to 24h + pre','to 48h + pre'])
    ax.set_title('Profiled nuisance parameters erase previously apparent model separation')
    ax.grid(axis='y',alpha=.2);ax.legend(fontsize=8,loc='upper right')
    fig.tight_layout();fig.savefig(ROOT/'figures'/'p2ae-cross-model-pairs.png',dpi=170);plt.close(fig)

    p=json.loads((ROOT/'data'/'p2ae-precision-sensitivity.json').read_text())
    precision_keys=['sd_floor_3.0_fractional_cv_0.125','sd_floor_1.0_fractional_cv_0.05','sd_floor_1.0_fractional_cv_0.02','sd_floor_0.5_fractional_cv_0.01']
    values=[p['results'][k] for k in precision_keys]
    keys=['CV12.5%, 3pg/mL floor','CV5%, 1pg/mL floor','CV2%, 1pg/mL floor','CV1%, 0.5pg/mL floor']
    fig,ax=plt.subplots(figsize=(9,5.2))
    bars=ax.bar(np.arange(4),[v['threshold2_count'] for v in values]);ax.bar_label(bars)
    ax.set_xticks(np.arange(4),keys,rotation=12,ha='right');ax.set_ylim(0,105)
    ax.set_ylabel('Candidate pairs reaching distance >= 2 (of 105)')
    ax.set_title('Hypothetical lower measurement noise may increase discrimination')
    ax.grid(axis='y',alpha=.2)
    fig.tight_layout();fig.savefig(ROOT/'figures'/'p2ae-precision-cross.png',dpi=170);plt.close(fig)

    d=json.loads((ROOT/'data'/'p2ae-drift-sensitivity.json').read_text());modes=[0.,.05,.1]
    fig,ax=plt.subplots(figsize=(8.5,5))
    for proto in ['through24_with_pre','through48_with_pre']:
        vals=[d['results'][f'{proto}__drift_{x}']['summary']['median_rate_ratio'] for x in modes]
        ax.plot(modes,vals,marker='o',label=proto)
    ax.set_xlabel('Bound on HYPOTHETICAL linear baseline drift (pg/mL per hour)')
    ax.set_ylabel('Median compatible ks max/min across 15 candidate truths')
    ax.set_title('Unmeasured baseline drift further widens slow-rate compatibility')
    ax.grid(alpha=.25);ax.legend(fontsize=8)
    fig.tight_layout();fig.savefig(ROOT/'figures'/'p2ae-drift-sensitivity.png',dpi=170);plt.close(fig)

if __name__=='__main__':main()
