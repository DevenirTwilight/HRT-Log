#!/usr/bin/env python3
"""P2-AF synthetic conditional identifiability with free central elimination & slow input rates.

Real P2-X sources were already exposed; ALL targets here synthetic.
No subject data, model posterior, clinical intervals, or dose advice.
"""
from __future__ import annotations
import argparse, json, math
from pathlib import Path
import numpy as np
from scipy.optimize import nnls
from scipy.integrate import simpson
from p2v_continuous_fit import erlang, bateman, shape

ROOT=Path(__file__).resolve().parents[1]
DATA=ROOT/'data'/'p2x-conditional-ensemble-results.json'
PROTOCOLS={
    'to08_pre':[0.,1.,2.,4.,8.],
    'to24_pre':[0.,1.,2.,4.,8.,12.,24.],
    'to48_pre':[0.,1.,2.,4.,8.,12.,24.,36.,48.],
}
CASES=[('08h_nominal','to08_pre',3.,.125),('24h_nominal','to24_pre',3.,.125),('48h_nominal','to48_pre',3.,.125),('48h_high_precision','to48_pre',1.,.05)]
SLOW_BOUND=(.025,.8)
CENTRAL_BOUND=(.5,2.0)
# Explicit coarse grid, with true P2-X rates inserted for exact-null checks.
SLOW_GRID=np.geomspace(*SLOW_BOUND,37)
CENTRAL_GRID=np.geomspace(*CENTRAL_BOUND,23)


def rows_selected(ds):
    assert ds['Doll_used'] is False
    best=ds['summary']['global_best_loss']
    out=[]
    for i,r in enumerate(ds['rows']):
        if r['score']<=best+.1+1e-12:
            fit=r['fit']['Price1997_figure1']
            out.append(dict(id=f'p2x-{i}',params=r['parameter'],baseline=float(fit['baseline']),amplitude=float(fit['amplitude_at_1h']),score=float(r['score'])))
    assert len(out)==15
    return out


def synthetic_total(r,t):
    return r['baseline']+r['amplitude']*shape(t,r['params'])


def functions(t,n,kfast,ke,ks):
    return erlang(t,n,kfast,ke), bateman(t,ks,ke)


def fit_profile(t,y,sd,n,kfast,ke,ks):
    f,s=functions(t,n,kfast,ke,ks)
    mat=np.stack((np.ones_like(t),f,s),axis=1)
    pars,_=nnls(mat/sd[:,None],y/sd)
    resid=(mat@pars-y)/sd
    return float(np.linalg.norm(resid)),pars


def sample_grid(r, protocol, floor,cv, central_free=True):
    t=np.array(PROTOCOLS[protocol]);y=synthetic_total(r,t)
    sd=np.maximum(floor,cv*np.maximum(y,0.0))
    p=r['params'];n=p['n_fast'];kf=p['k_fast_per_h']
    central=np.unique(np.r_[CENTRAL_GRID,float(p['k_elim_per_h'])]) if central_free else np.array([p['k_elim_per_h']])
    slow=np.unique(np.r_[SLOW_GRID,float(p['k_slow_per_h'])])
    accepted=[]; dmin=(float('inf'),None)
    for ke in central:
        f=erlang(t,n,kf,float(ke))
        for ks in slow:
            s=bateman(t,float(ks),float(ke))
            x=np.column_stack((np.ones(t.size),f,s))
            coefficients,_=nnls(x/sd[:,None],y/sd)
            d=float(np.linalg.norm((x@coefficients-y)/sd))
            if d<dmin[0]: dmin=d,(float(ke),float(ks))
            if d<=2.+1e-10:
                accepted.append([float(ke),float(ks),float(d),float(coefficients[0]),float(coefficients[1]),float(coefficients[2])])
    if not accepted:raise AssertionError(('no feasible original',r['id'],protocol, floor,cv,dmin))
    if dmin[0]>1e-6:raise AssertionError(('true source not found',r['id'],protocol,dmin))
    return np.array(accepted),dict(id=r['id'],scenario=protocol,sd_floor=floor,cv=cv,
                                  n_sample=len(t),n_total_grid=len(central)*len(slow),n_feasible=len(accepted),
                                  min_distance=dmin[0],truth_ke=p['k_elim_per_h'],truth_ks=p['k_slow_per_h'])


def endpoints(r, feasible):
    p=r['params'];n=p['n_fast'];kf=p['k_fast_per_h']
    tauc=np.linspace(0,24,961)
    t8=np.linspace(0,8,321)
    tpeak=np.linspace(0,6,241)
    tlate=np.array([2.,6.,12.,24.,36.,48.])
    # q24 pre-dose sum first 60 previous hypothetical 24h intervals, no baseline.
    tq24=np.arange(1,121,dtype=float)*24.
    out=[]
    for ke,ks,d,b,u,v in feasible:
        def raw(t):
            f,s=functions(np.asarray(t,dtype=float),n,kf,ke,ks)
            return u*f+v*s
        norm=float(raw(np.array([1.]))[0]);assert norm>0 and np.isfinite(norm)
        auc8=float(simpson(raw(t8)/norm,x=t8)); auc24=float(simpson(raw(tauc)/norm,x=tauc)); aucinf=float((u+v)/ke/norm)
        if aucinf<auc24-2e-4:raise AssertionError(('mass error',aucinf,auc24))
        sampled=raw(tlate)/norm
        peaks=raw(tpeak)/norm
        q24=float(np.sum(raw(tq24))/norm)
        out.append([auc8,auc24,aucinf,1-auc24/aucinf,q24,float(tpeak[np.argmax(peaks)]),*map(float,sampled)])
    return np.array(out)


NAMES=['auc_0_8_h','auc_0_24_h','auc_0_inf_h','tail_area_fraction_after24','q24_predose_index','peak_time_0_6_h',
       'H2','H6','H12','H24','H36','H48']


def summary_one(r,prof,diagnostic):
    e=endpoints(r,prof)
    vals={name:dict(min=float(e[:,i].min()),median=float(np.median(e[:,i])),max=float(e[:,i].max()),
        spread_ratio=float(e[:,i].max()/e[:,i].min()) if e[:,i].min()>1e-9 else None) for i,name in enumerate(NAMES)}
    return {**diagnostic,'ke_feasible_min':float(prof[:,0].min()),'ke_feasible_max':float(prof[:,0].max()),
            'ks_feasible_min':float(prof[:,1].min()),'ks_feasible_max':float(prof[:,1].max()),
            'gain_nonneg_verified':bool(np.all(prof[:,3:]>=0)),
            'metric_envelopes':vals}


def run(ds):
    rows=rows_selected(ds)
    cases={}
    for name,protocol,floor,cv in CASES:
        case=[]
        for r in rows:
            p,diagnostics=sample_grid(r,protocol,floor,cv,central_free=True)
            case.append(summary_one(r,p,diagnostics))
        cases[name]=case
    # Controlled comparison with ke frozen: same nuisance and one-dimensional ks scan.
    fixed=[]
    for r in rows:
        arr,d=sample_grid(r,'to48_pre',3.,.125,central_free=False)
        fixed.append(summary_one(r,arr,d))
    cases['48h_nominal_fixed_ke']=fixed
    aggregate={}
    for cname,rr in cases.items():
        metrics={}
        for metric in NAMES:
            lo=np.array([v['metric_envelopes'][metric]['min'] for v in rr]);hi=np.array([v['metric_envelopes'][metric]['max'] for v in rr])
            ratios=hi/np.maximum(lo,1e-12)
            metrics[metric]=dict(median_min=float(np.median(lo)),median_max=float(np.median(hi)),
                    min_of_min=float(lo.min()),max_of_max=float(hi.max()),median_internal_spread_ratio=float(np.median(ratios)),
                    n_internal_span_ge_2x=int(np.sum(ratios>=2)),n_internal_span_ge_5x=int(np.sum(ratios>=5)))
        aggregate[cname]=dict(n_candidates=len(rr),median_feasible_grid_points=float(np.median([x['n_feasible'] for x in rr])),
          range_feasible_grid_points=[int(min(x['n_feasible'] for x in rr)),int(max(x['n_feasible'] for x in rr))],
          median_ke_ratio=float(np.median([x['ke_feasible_max']/x['ke_feasible_min'] for x in rr])),
          median_ks_ratio=float(np.median([x['ks_feasible_max']/x['ks_feasible_min'] for x in rr])),metrics=metrics)
    return dict(phase='P2-AF',date='2026-10-10',has_new_human_data=False,no_Doll_calibration=True,
        dataset='P2-X exposed/conditional 15 of 40 candidates',generator='Price Figure 1 pseudo-study fitted baseline+1h amplitude',
        synthetic_only=True,not_human_CI_posterior_or_prediction_bands=True,
        threshold='whitened Euclidean distance <=2; entirely illustrative, not a chi-square confidence set',
        central_rate_grid=[*CENTRAL_BOUND,len(CENTRAL_GRID)],slow_rate_grid=[*SLOW_BOUND,len(SLOW_GRID)],
        ke_lower_bound_is_arbitrary=True,fast_erlang_shape_and_rate_frozen_by_candidate=True,
        nuisance='nonnegative baseline, independent fast/slow gains, central and slow rates free within fixed bounded grids',
        all_15_have_exact_own_truth_on_grid=True,
        q24_mathematical='hypothetical repeated every 24h, 120 prior doses, each shape normalized by 1h increment; not observed or prescribed',
        metric_names=NAMES,results=cases,aggregate=aggregate)


def main():
    parser=argparse.ArgumentParser();parser.add_argument('--output',type=Path,default=ROOT/'data'/'p2af-joint-parameter-functions.json');p=parser.parse_args()
    if p.output.exists():raise FileExistsError('Refuse overwrite')
    d=run(json.loads(DATA.read_text(encoding='utf8')))
    p.output.write_text(json.dumps(d,ensure_ascii=False,indent=2,sort_keys=True)+'\n',encoding='utf8')
    for name,s in d['aggregate'].items():
        print(name, 'k_e span',round(s['median_ke_ratio'],3),'k_s span',round(s['median_ks_ratio'],3),'n',s['median_feasible_grid_points'])
        for metric in ['auc_0_8_h','auc_0_inf_h','tail_area_fraction_after24','q24_predose_index','H24']:
            d0=s['metrics'][metric];print(' ',metric,'median spread',round(d0['median_internal_spread_ratio'],3),'n5x',d0['n_internal_span_ge_5x'])
if __name__=='__main__':main()
