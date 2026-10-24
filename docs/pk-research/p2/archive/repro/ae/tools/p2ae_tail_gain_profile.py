#!/usr/bin/env python3
"""P2-AE independent mathematical identifiability of slow rate vs slow gain.

P2-X previously exposed group-level candidate curves; entirely synthetic measurement
cases. No Doll calibration, GitHub writes, patient data, individual predictions,
clinical sampling advice, or posterior/CI interpretation.
"""
from __future__ import annotations
import argparse, json, math
from pathlib import Path
import numpy as np
from scipy.optimize import nnls
from p2v_continuous_fit import bateman, erlang, shape

ROOT=Path(__file__).resolve().parents[1]
DATA=ROOT/'data'/'p2x-conditional-ensemble-results.json'
PROTOCOLS={
    'early_no_pre':[1,2,4,8],
    'through24_no_pre':[1,2,4,8,12,24],
    'through24_with_pre':[0,1,2,4,8,12,24],
    'through48_with_pre':[0,1,2,4,8,12,24,36,48],
    'through72_with_pre':[0,1,2,4,8,12,24,36,48,72],
}
RATE_LOW=.025;RATE_HIGH=.8
ASSAY_1997_SENSITIVITY=8.0

def select(dataset,delta=.1):
    assert dataset['Doll_used'] is False
    best=float(dataset['summary']['global_best_loss'])
    rows=[]
    for i,r in enumerate(dataset['rows']):
        if r['score']<=best+delta+1e-12:
            fit=r['fit']['Price1997_figure1']
            rows.append(dict(id=f'p2x-{i}',parameter=r['parameter'],b=float(fit['baseline']),a=float(fit['amplitude_at_1h']),loss=r['score']))
    assert len(rows)==15
    return rows

def components(t, p, ks):
    t=np.asarray(t,float)
    assert np.isfinite(t).all() and (t>=0).all() and 0<ks
    f=erlang(t,p['n_fast'],p['k_fast_per_h'],p['k_elim_per_h'])
    s=bateman(t,ks,p['k_elim_per_h'])
    return f,s

def anchor_total(t,r):
    return r['b']+r['a']*shape(np.array(t,float),r['parameter'])

def sigma(y,floor=3,cv=.125):
    assert floor>0 and cv>0
    return np.maximum(floor,cv*np.maximum(y,0))

def profile_one(t,y,sd,p,rate,mode,bknown=None):
    f,s=components(t,p,rate)
    if mode=='fixed_slow_weight':
        w=p['effective_slow_weight']
        fn, sn=components(np.array([1.]),p,rate)
        z=(1-w)*f+w*s
        norm=((1-w)*fn+w*sn)[0]
        x=(z/norm)[:,None]
    elif mode in ('free_fast_slow_gains','known_background_free_gains'):
        x=np.column_stack([f,s])
    else:raise ValueError('unknown mode')
    if bknown is None: x=np.column_stack([np.ones(len(t)),x]);base=0.
    else:base=float(bknown)
    pars, _=nnls(x/sd[:,None],(y-base)/sd)
    prediction=x@pars+base
    err=float(np.linalg.norm((prediction-y)/sd))
    return err, pars

def one_profile(r,times,mode,sd_floor=3.,drop_below_sensitivity=False,grid_points=121):
    t=np.asarray(times,float); y=anchor_total(t,r)
    mask=np.ones(len(t),bool)
    if drop_below_sensitivity:mask &= y>=ASSAY_1997_SENSITIVITY
    t=t[mask]; y=y[mask]
    if len(t)<2:
        return dict(id=r['id'],total_usable=len(t),profile_defined=False)
    p=r['parameter'];sd=sigma(y,sd_floor)
    ks=float(p['k_slow_per_h'])
    rates=np.unique(np.r_[np.geomspace(RATE_LOW,RATE_HIGH,grid_points),ks])
    bknown=r['b'] if mode=='known_background_free_gains' else None
    d=np.array([profile_one(t,y,sd,p,rate,mode,bknown=bknown)[0] for rate in rates])
    assert np.isfinite(d).all()
    zeroidx=np.flatnonzero(np.isclose(rates,ks,rtol=1e-12))[0]
    true_residual=float(d[zeroidx]);assert true_residual<1e-6,(r['id'],mode,true_residual)
    feasible=rates[d<=2.+1e-10]
    assert len(feasible)>0
    if True:
        f,s=components(t,p,ks)
        ww=p['effective_slow_weight'];f1,s1=components(np.array([1.]),p,ks)
        denom=(1-ww)*f1[0]+ww*s1[0]
        assert np.max(np.abs(y-(r['b']+r['a']/denom*((1-ww)*f+ww*s))))<1e-7
    out=dict(id=r['id'],total_usable=int(len(t)),profile_defined=True,
             true_ks_per_h=ks,true_slow_half_life_h=math.log(2)/ks,
             min_feasible_ks=float(feasible.min()),max_feasible_ks=float(feasible.max()),
             feasible_ratio=float(feasible.max()/feasible.min()),
             true_rate_over_min=float(ks/feasible.min()),max_over_true_rate=float(feasible.max()/ks),
             touches_low=bool(np.isclose(feasible.min(),RATE_LOW)),touches_high=bool(np.isclose(feasible.max(),RATE_HIGH)),
             both_rate_boundaries=bool(np.isclose(feasible.min(),RATE_LOW) and np.isclose(feasible.max(),RATE_HIGH)),
             includes_half_truth=bool(feasible.min()<=ks/2) if ks/2>=RATE_LOW else None,
             includes_double_truth=bool(feasible.max()>=2*ks) if 2*ks<=RATE_HIGH else None,
             n_grid_feasible=int(len(feasible)),grid_size=int(len(rates)),min_distance=float(d.min()),true_distance=true_residual,
             profile_curve=[{'ks_per_h':float(k),'residual_distance':float(v)} for k,v in zip(rates,d)])
    return out

def collate(entries):
    valid=[x for x in entries if x['profile_defined']]
    if not valid:return dict(n=len(entries),n_valid=0)
    ratios=np.array([x['feasible_ratio'] for x in valid])
    return dict(n=len(entries),n_valid=len(valid),median_feasible_rate_ratio=float(np.median(ratios)),
        n_span_ge_4x=int(np.sum(ratios>=4)), n_span_ge_10x=int(np.sum(ratios>=10)),
        n_both_rate_boundaries=int(sum(x['both_rate_boundaries'] for x in valid)),
        n_single_boundary=int(sum(x['touches_low'] or x['touches_high'] for x in valid)),
        min_usable_measurements=min(x['total_usable'] for x in valid))

def analyze(d):
    rows=select(d)
    results={}
    # Focus: unknown baseline and amplitude vs unknown mixture of fast and slow inputs.
    modes=('fixed_slow_weight','free_fast_slow_gains','known_background_free_gains')
    for protocol,times in PROTOCOLS.items():
        for mode in modes:
            key=f'{protocol}__{mode}'
            rr=[one_profile(r,times,mode) for r in rows]
            results[key]={'summary':collate(rr),'per_candidate':rr}
    sensitivity={}
    for floor in (3.,5.,8.):
        for censored in (False,True):
            for protocol in ('through24_with_pre','through48_with_pre'):
                mode='free_fast_slow_gains'
                key=f'sdfloor_{int(floor)}__drop_{censored}__{protocol}'
                rr=[one_profile(r,PROTOCOLS[protocol],mode,sd_floor=floor,drop_below_sensitivity=censored) for r in rows]
                sensitivity[key]={'summary':collate(rr),'per_candidate':[dict(x,profile_curve=[]) for x in rr]}
    return dict(phase='P2-AE',strictly_hypothetical=True,production_modified=False,github_modified=False,
        Doll_used=False,patient_specific_validated=False,synthetic_no_real_measured_patient_series=True,
        source='P2-X previously exposed 40 source-conditional study fits, deterministic selection best + 0.10, no new data',
        assumptions=dict(rate_grid_per_h=[RATE_LOW,RATE_HIGH],rate_grid_not_statistical_confidence=True,
            noise_sd_formula='max(sd_floor_pgml, .125 * synthetic group total E2)',
            chosen_distance_threshold=2.,distance_threshold_not_statistical_95pct_CI=True,
            P2_1997_RIA_sensitivity_pgml=ASSAY_1997_SENSITIVITY,
            censoring='Optionally DROPS below 8 instead of proper censored likelihood; not modern assay LLOQ',
            pre_dose_baseline_is_hypothetical_sample=True,
            after24_36_48_72h_are_extrapolations_not_Price_observed=True),
        protocols=PROTOCOLS,parameter_modes={
            'fixed_slow_weight':'Fix original w, nfast,kfast,kelim. Refit nonnegative b and one effective gain for each hypothesized ks.',
            'free_fast_slow_gains':'Fix nfast,kfast,kelim. Refit nonnegative b, UNLINKED fast gain and slow gain for each ks (w unknown).',
            'known_background_free_gains':'IDEAL upper information bound: b magically known without error; refit fast and slow gains.'},
        selected_models=[{'id':r['id'],'b_Price':r['b'],'A_Price':r['a'],'k_slow':r['parameter']['k_slow_per_h'],'w':r['parameter']['effective_slow_weight']} for r in rows],
        main=results,sensitivity=sensitivity)

def create_figures(d):
    import matplotlib
    matplotlib.use('Agg')
    import matplotlib.pyplot as plt
    from matplotlib.ticker import ScalarFormatter
    mode_titles={'fixed_slow_weight':'Fixed slow weight','free_fast_slow_gains':'Slow weight free','known_background_free_gains':'Known true background + free weight'}
    protocols=['early_no_pre','through24_no_pre','through24_with_pre','through48_with_pre','through72_with_pre']
    fig,ax=plt.subplots(figsize=(10,5.6))
    x=np.arange(len(protocols));bw=.26
    for k,(mode,desc) in enumerate(mode_titles.items()):
        yy=[d['main'][f'{p}__{mode}']['summary']['median_feasible_rate_ratio'] for p in protocols]
        ax.bar(x+(k-1)*bw,yy,bw,label=desc)
    ax.set_yscale('log');ax.set_xticks(x, [p.replace('_','\n') for p in protocols]);ax.set_ylabel('Median compatible ks max/min ratio (log scale)');ax.set_title('Synthetic slow-rate identifiability after profiling baseline and input gains');ax.legend(fontsize=8);ax.grid(alpha=.2,axis='y');fig.tight_layout();fig.savefig(ROOT/'figures'/'p2ae-identifiability-designs.png',dpi=170);plt.close(fig)
    ids=('p2x-22','p2x-39','p2x-34')
    fig,axes=plt.subplots(1,len(ids),figsize=(15,4.8),sharey=True)
    for ax,id_ in zip(axes,ids):
        for mode,label in (('fixed_slow_weight','w fixed'),('free_fast_slow_gains','w unknown')):
            rr=next(x for x in d['main'][f'through48_with_pre__{mode}']['per_candidate'] if x['id']==id_)
            ax.plot([z['ks_per_h'] for z in rr['profile_curve']],[z['residual_distance'] for z in rr['profile_curve']],label=label)
        true_k=next(x['k_slow'] for x in d['selected_models'] if x['id']==id_)
        ax.axvline(true_k,color='gray',linestyle=':',label='frozen source ks')
        ax.axhline(2.,color='black',linestyle='--',lw=1,label='illustrative distance=2')
        ax.set_xscale('log');ax.set_title(id_);ax.set_xlabel('Hypothetical slow absorption rate (1/h)');ax.grid(alpha=.22)
    axes[0].set_ylabel('Expected standardized residual distance');axes[-1].legend(fontsize=7);fig.suptitle('Free slow-input gain can absorb changes in the slow absorption rate');fig.tight_layout();fig.savefig(ROOT/'figures'/'p2ae-three-profile-curves.png',dpi=170);plt.close(fig)
    fig,ax=plt.subplots(figsize=(9.6,5.5))
    floorlabels=[];values=[]
    for f in (3,5,8):
      for drop in (False,True):
        d0=d['sensitivity'][f'sdfloor_{f}__drop_{drop}__through48_with_pre']['summary']
        floorlabels.append(f'{f} pg/mL\n'+('drop <8' if drop else 'retain all'))
        values.append(d0['n_span_ge_10x'])
    ax.bar(np.arange(len(values)),values);ax.set_ylim(0,15);ax.set_xticks(np.arange(len(values)),floorlabels);ax.set_ylabel('Candidates allowing ≥10-fold slow-rate span (of 15)');ax.set_title('Assay/noise assumptions change practical rate identification');ax.grid(axis='y',alpha=.2);fig.tight_layout();fig.savefig(ROOT/'figures'/'p2ae-noise-sensitivity.png',dpi=170);plt.close(fig)

def main():
    ap=argparse.ArgumentParser();ap.add_argument('--input',type=Path,default=DATA);ap.add_argument('--output',type=Path,default=ROOT/'data'/'p2ae-identifiability.json');ap.add_argument('--figures',action='store_true');args=ap.parse_args()
    if args.output.exists():raise FileExistsError('refusing overwrite')
    d=analyze(json.loads(args.input.read_text(encoding='utf8')))
    args.output.write_text(json.dumps(d,ensure_ascii=False,indent=2,sort_keys=True)+'\n',encoding='utf8')
    if args.figures:create_figures(d)
    print(json.dumps({'phase':d['phase'],'results':{k:v['summary'] for k,v in d['main'].items()},'sensitivity':{k:v['summary'] for k,v in d['sensitivity'].items()}},indent=2))
if __name__=='__main__':main()
