#!/usr/bin/env python3
"""P2-AD: fully offline, research-only analysis of 15 P2-X M2 conditional fits.

No GitHub interaction, personal treatment records, optimization of actual doses,
or medical inference. Includes a declared HYPOTHETICAL assay-error scenario.
"""
from __future__ import annotations
import argparse
import json
import math
from pathlib import Path
from itertools import combinations
import numpy as np
from scipy.optimize import nnls
from scipy.special import ndtr
from p2v_continuous_fit import shape

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / 'data' / 'p2x-conditional-ensemble-results.json'
TIME_GRID = [0., 1., 2., 4., 6., 8., 12., 18., 24., 36., 48., 72.]
ASSUMED_FRACTIONAL_INTERASSAY_CV = 0.125
ASSUMED_ABSOLUTE_SD_FLOOR_PGML = 3.0
ASSAY_SENSITIVITY_PGML = 8.0  # Price 1997 RIA sensitivity: NOT a sigma nor a validated modern LLOQ.
PROTOCOLS = {
    'early_no_baseline':[1,2,4,8],
    'to_24_no_baseline':[1,2,4,8,12,24],
    'to_36_no_baseline':[1,2,4,8,12,24,36],
    'to_48_no_baseline':[1,2,4,8,12,24,48],
    'to_36_and_48_no_baseline':[1,2,4,8,12,24,36,48],
    'early_with_baseline':[0,1,2,4,8],
    'to_24_with_baseline':[0,1,2,4,8,12,24],
    'to_48_with_baseline':[0,1,2,4,8,12,24,36,48],
}

def select_rows(dataset:dict, delta:float=0.1) -> list[dict]:
    if dataset.get('Doll_used') is not False: raise ValueError('Doll input must be absent')
    best=float(dataset['summary']['global_best_loss'])
    return [
        {
            'id':f'p2x-{i}', 'parameter':r['parameter'],
            'pseudo_loss':float(r['score']),
            'assumed_price_baseline_pgml':float(r['fit']['Price1997_figure1']['baseline']),
            'price_effective_one_hour_amplitude_pgml':float(r['fit']['Price1997_figure1']['amplitude_at_1h']),
            'assumed_rosano_cap_pmol_l':float(r['rosano_baseline_cap_pmol_l'])
        }
        for i,r in enumerate(dataset['rows']) if float(r['score'])<=best+delta+1e-12
    ]

def curves(rows:list[dict], times:list[float]|np.ndarray):
    ts=np.asarray(times,dtype=float)
    if ts.ndim!=1 or not np.isfinite(ts).all() or np.any(ts<0):raise ValueError('bad times')
    h=np.stack([shape(ts,c['parameter']) for c in rows])
    b=np.array([x['assumed_price_baseline_pgml'] for x in rows])
    a=np.array([x['price_effective_one_hour_amplitude_pgml'] for x in rows])
    incr=a[:,None]*h
    total=b[:,None]+incr
    return total,incr,h

def assumed_sigma(total:np.ndarray, fractional_cv:float=.125, floor:float=3.0):
    if fractional_cv <= 0 or floor <=0:raise ValueError('positive SD assumptions required')
    return np.maximum(floor,fractional_cv*np.maximum(0,total))

def directed_profiled_distance(truth:np.ndarray, alternative_shape:np.ndarray, sd:np.ndarray):
    """Nonnegative b and A *refit* for alternative shape; dimensionless residual distance.

    A small distance means total concentration samples cannot distinguish the models
    even in the synthetic scenario. Not a chi-square statistic or statistical power.
    """
    x=np.column_stack((np.ones(len(alternative_shape)),alternative_shape))
    pars,unused=nnls(x/sd[:,None],truth/sd)
    fitted=x@pars
    return float(np.linalg.norm((truth-fitted)/sd)), pars

def discrimination(rows:list[dict], protocol:list[float], fractional_cv:float=.125,
                   sd_floor:float=3., sensitivity:float|None=None):
    total,inc,h=curves(rows,protocol)
    sig=assumed_sigma(total,fractional_cv,sd_floor)
    dist=[]; matched=0; differing=0
    by_pairs=[]
    for i,j in combinations(range(len(rows)),2):
        vals=[]
        for a,b in ((i,j),(j,i)):
            active=np.ones(len(protocol),bool) if sensitivity is None else total[a]>=sensitivity
            # Conservative: below-sensitivity values are dropped, not fitted or treated as zero.
            if active.sum()<3: vals.append(0.0); continue
            score, pars=directed_profiled_distance(total[a,active],h[b,active],sig[a,active])
            vals.append(score)
        v=min(vals)
        dist.append(v)
        same=rows[i]['assumed_price_baseline_pgml']==rows[j]['assumed_price_baseline_pgml']
        matched+=int(same)
        differing+=int(not same)
        by_pairs.append({'model_a':rows[i]['id'],'model_b':rows[j]['id'],
                         'same_assumed_price_baseline':same,'conservative_distance':round(v,9)})
    d=np.array(dist)
    return {
      'n_candidate_pairs':len(d),'n_same_price_baseline_pairs':matched,
      'n_different_price_baseline_pairs':differing,
      'median_distance':round(float(np.median(d)),8),
      'p10_distance':round(float(np.quantile(d,.1)),8),
      'minimum_distance':round(float(d.min()),8),
      'pairs_distance_ge_2':int(np.sum(d>=2.)),
      'pairs_distance_ge_3':int(np.sum(d>=3.)),
      'median_same_assumed_baseline_distance':round(float(np.median([p['conservative_distance'] for p in by_pairs if p['same_assumed_price_baseline']])),8),
      'median_different_assumed_baseline_distance':round(float(np.median([p['conservative_distance'] for p in by_pairs if not p['same_assumed_price_baseline']])),8),
      'pair_details':by_pairs,
    }

def analyze(data:dict):
    rows=select_rows(data)
    if len(rows)!=15:raise AssertionError(f'expected frozen P2-X +0.1 to yield 15, got {len(rows)}')
    total,inc,h=curves(rows,TIME_GRID)
    times={}
    for i,t in enumerate(TIME_GRID):
        w=total[:,i];x=inc[:,i]
        times[str(int(t))]={
            'min_total_pgml':round(float(w.min()),8),
            'max_total_pgml':round(float(w.max()),8),
            'spread_total_pgml':round(float(w.max()-w.min()),8),
            'min_increment_pgml':round(float(x.min()),8),
            'max_increment_pgml':round(float(x.max()),8),
            'increment_max_min_ratio':round(float(x.max()/x.min()),8) if x.min()>1e-11 else None,
        }
    x18=TIME_GRID.index(18);x24=TIME_GRID.index(24);x36=TIME_GRID.index(36)
    pair_late=[]
    for i,c in enumerate(rows):
        dc=float(total[i,x24]-total[i,x36])
        sigma=float(math.hypot(*assumed_sigma(total[i,[x24,x36]])))
        pair_late.append({
          'id':c['id'],
          'assumed_price_baseline_pgml':c['assumed_price_baseline_pgml'],
          'C24_minus_C36_pgml':round(dc,8),
          'C18_minus_C24_pgml':round(float(total[i,x18]-total[i,x24]),8),
          'hypothetical_sigma_C24_minus_C36_pgml':round(sigma,8),
          'prob_C24_greater_than_C36_in_noise_scenario':round(float(ndtr(dc/sigma)),8),
          'C48_below_1997_assay_sensitivity':bool(total[i,TIME_GRID.index(48)]<ASSAY_SENSITIVITY_PGML),
        })
    designs={name:discrimination(rows,ts) for name,ts in PROTOCOLS.items()}
    sensitivity={}
    for f in (3.,5.,8.):
        for protocol in ('to_24_no_baseline','to_24_with_baseline','to_48_no_baseline','to_48_with_baseline'):
            tag=f'sd_floor_{int(f)}__{protocol}'
            sensitivity[tag]=discrimination(rows,PROTOCOLS[protocol],sd_floor=f,sensitivity=ASSAY_SENSITIVITY_PGML)
    return dict(
      phase='P2-AD',year=2026,purpose='Research-only baseline/tail identifiability and hypothetical measurement design',
      source='P2-X frozen 40 conditional exposed-fit rows and P2-V identical mathematical shape',
      doll_used=False,individual_PK_validated=False,production_engine_modified=False,
      selection_rule='P2-X frozen per-source pseudo-loss <= global minimum + 0.1 (subjective; not confidence)',
      selected_candidate_count=len(rows),candidate_ids=[c['id'] for c in rows],
      price_baseline_counts={str(int(x)):sum(v['assumed_price_baseline_pgml']==x for v in rows) for x in [0.,6.,12.,18.,24.]},
      hypothesis_measurement_error={'fractional_cv':ASSUMED_FRACTIONAL_INTERASSAY_CV,
            'absolute_sd_floor_pgml':ASSUMED_ABSOLUTE_SD_FLOOR_PGML,
            'sigma_formula':'max(3 pg/mL, 12.5 percent of synthetic TOTAL concentration)',
            'interpretation':'illustrative noise, NOT a validated assay variance model, study CI or clinical power',
            'sensitivity_limit_pgml_1997_RIA':ASSAY_SENSITIVITY_PGML,
            'note':'8pg/mL is reported assay sensitivity not a measured SD or definitive lower LOQ'},
      summary={'C24_total_range_pgml':[times['24']['min_total_pgml'],times['24']['max_total_pgml']],
               'C24_increment_range_pgml':[times['24']['min_increment_pgml'],times['24']['max_increment_pgml']],
               'increment_ratio_at_24h':times['24']['increment_max_min_ratio'],
               'C24_to_C36_change_range_pgml':[min(r['C24_minus_C36_pgml'] for r in pair_late),max(r['C24_minus_C36_pgml'] for r in pair_late)],
               'n_C48_below_reported_1997_sensitivity':sum(r['C48_below_1997_assay_sensitivity'] for r in pair_late)},
      profiles=rows,
      time_ranges=times,
      per_candidate_late_differences=pair_late,
      protocols_hours=PROTOCOLS,
      hypothetical_designs=designs,
      sensitivity_with_below_sensitivity_dropped=sensitivity,
      major_caveat='All 15 fitted to previously exposed studies; Price group A and b profiled to hand-digitized graph; no individual PK; no prospective validation; dose events in designs are synthetic; sensitivity fractions are NOT population probabilities.'
    )

def make_figures(result):
    import matplotlib
    matplotlib.use('Agg')
    import matplotlib.pyplot as plt
    rows=result['profiles']; times=np.array(TIME_GRID)
    c,d,h=curves(rows,times)
    fig,ax=plt.subplots(figsize=(8,5));
    for i,row in enumerate(rows):
        ax.plot(times,c[i],alpha=.75,lw=1.0,label=row['id'] if i<3 else None)
    ax.scatter([24.],[24.],marker='x',s=55,label='Price Figure 1 manual reading ~24 pg/mL')
    ax.set_xlim(0,48);ax.set_ylim(0,100);ax.set_xlabel('Hours after hypothetical single 1 mg study dose');ax.set_ylabel('Price-anchored group E2 total (pg/mL)')
    ax.set_title('15 conditional models: total concentration despite tail uncertainty');ax.legend(fontsize=8);ax.grid(alpha=.25)
    fig.tight_layout();fig.savefig(ROOT/'figures'/'p2ad-conditional-total-late.png',dpi=180);plt.close(fig)
    fig,ax=plt.subplots(figsize=(8,5))
    for i,row in enumerate(rows):
        ts=np.linspace(8,48,160)
        aa=row['price_effective_one_hour_amplitude_pgml']*shape(ts,row['parameter'])
        ax.plot(ts,aa,alpha=.7,lw=1.0)
    ax.axhline(3.0,ls='--',alpha=.7,label='Illustrative 3 pg/mL SD floor, NOT LOD')
    ax.set_yscale('log');ax.set_xlabel('Hours after study dose');ax.set_ylabel('Dose-attributed increment (pg/mL, log scale)')
    ax.set_title('Unknown background hides the late E2 increment');ax.legend(fontsize=8);ax.grid(alpha=.2)
    fig.tight_layout();fig.savefig(ROOT/'figures'/'p2ad-increment-tail.png',dpi=180);plt.close(fig)
    fig,ax=plt.subplots(figsize=(10,5));sel=['early_no_baseline','to_24_no_baseline','to_36_no_baseline','to_48_no_baseline','to_24_with_baseline','to_48_with_baseline']
    x=np.arange(len(sel));a=[result['hypothetical_designs'][k]['pairs_distance_ge_2'] for k in sel]
    ax.bar(x,a);ax.set_xticks(x,[k.replace('_','\n') for k in sel]);ax.set_ylabel('Pairwise expected residual distance >= 2 (of 105 pairs)')
    ax.set_title('Synthetic distinguishability after profiling nonnegative background and gain')
    ax.set_ylim(0,105);ax.grid(axis='y',alpha=.2);fig.tight_layout();fig.savefig(ROOT/'figures'/'p2ad-hypothetical-discrimination.png',dpi=180);plt.close(fig)

def main():
    ap=argparse.ArgumentParser();ap.add_argument('--input',type=Path,default=DATA);ap.add_argument('--out',type=Path,default=ROOT/'data'/'p2ad-audit-results.json');ap.add_argument('--figures',action='store_true');args=ap.parse_args()
    if args.out.exists():raise FileExistsError('refusing to replace existing research result')
    d=analyze(json.loads(args.input.read_text(encoding='utf-8')))
    args.out.write_text(json.dumps(d,ensure_ascii=False,indent=2,sort_keys=True)+'\n',encoding='utf-8')
    if args.figures:make_figures(d)
    print(json.dumps({'phase':d['phase'],'summary':d['summary'], 'design_summary':{k:{x:v[x] for x in ('median_distance','pairs_distance_ge_2','pairs_distance_ge_3')} for k,v in d['hypothetical_designs'].items()}},indent=2))
if __name__=='__main__':main()
