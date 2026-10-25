#!/usr/bin/env python3
"""P2-V: continuous multi-study shape model with explicit evidential boundaries.

Rosano, Komesaroff and *manually digitized* Price Figure 1 only. No Doll.
No individual samples; covariance is HYPOTHETICAL sensitivity, not an estimate.
No clinical forecast, dose recommendations, PK population parameter estimates.
"""
from __future__ import annotations
import argparse
import json
import math
from pathlib import Path
from functools import lru_cache
import numpy as np
from scipy.optimize import minimize
from scipy.special import gammainc

ROOT=Path(__file__).resolve().parents[2]
DATA=ROOT/'docs/pk-research/p2/p2u-observed-aggregates.json'
SOURCE_ORDER=['Rosano1997_PK25','Komesaroff1998_n10','Price1997_figure1']
# Real PK observations: Rosano/Kom originals transcribed in upstream P2; Price points manually read.

def bateman(t, k, ke):
    """Stable first-order absorption + central elimination (unit-dose response)."""
    t=np.asarray(t,float)
    if np.any(t<0) or min(k,ke)<=0:raise ValueError('bad rate or time')
    gap=abs(k-ke)
    if gap<1e-9*max(k,ke):return k*t*np.exp(-ke*t)
    return k*np.exp(-min(k,ke)*t)*(-np.expm1(-gap*t))/gap

def erlang(t,n,k,ke):
    """Erlang n-stage INPUT convolved with central first-order clearance.

    Under this research parameterization k>ke, using regularized gamma CDF.
    n counts mathematical input stages, not an anatomical claim.
    """
    t=np.asarray(t,float)
    if n<1 or k<=ke or ke<=0 or np.any(t<0):raise ValueError('bad transit')
    if n==1:return bateman(t,k,ke)
    return np.exp(n*np.log(k/(k-ke))-ke*t)*gammainc(n,(k-ke)*t)

def unpack(x,family,n):
    ke=float(np.exp(x[0])); k=ke+float(np.exp(x[1]))
    slow=float(np.exp(x[2])) if family=='M2' else None
    w=float(x[3]) if family=='M2' else 0.
    return {'family':family,'n_fast':int(n),'k_fast_per_h':k,'k_elim_per_h':ke,
            'k_slow_per_h':slow,'effective_slow_weight':w}

def shape(t,param):
    t=np.asarray(t,float)
    n=param['n_fast']; k=param['k_fast_per_h'];ke=param['k_elim_per_h']
    fast=erlang(t,n,k,ke)
    w=param['effective_slow_weight']
    v=(1-w)*fast+(w*bateman(t,param['k_slow_per_h'],ke) if w>0 else 0)
    f=erlang(np.array([1.]),n,k,ke)[0]
    norm=(1-w)*f+(w*bateman(np.array([1.]),param['k_slow_per_h'],ke)[0] if w>0 else 0)
    if norm<=0 or not np.isfinite(norm):raise ValueError('nonpositive normalization')
    return v/norm

def simulate_study_anchored(t_hours, param, dose_events, baseline, amplitude_per_mg_at_1h):
    """Linear superposition H(t) for a named-study anchor, NOT a validated patient PK curve.

    dose_events is a sequence of (administration_hour, nominal_mg) tuples.
    Amplitude must be sourced/qualified for the *same* study/assay/formulation.
    """
    if not all(math.isfinite(float(x)) for x in (t_hours,baseline,amplitude_per_mg_at_1h)):
        raise ValueError('nonfinite input')
    if baseline<0 or amplitude_per_mg_at_1h<0:raise ValueError('negative scale')
    total=baseline
    for when,mg in dose_events:
        if not math.isfinite(float(when)) or not math.isfinite(float(mg)) or mg<=0:
            raise ValueError('dose event invalid')
        dt=t_hours-when
        if dt>=0:total+=amplitude_per_mg_at_1h*mg*float(shape([dt],param)[0])
    return float(total)

def bounds_for(family):
    out=[(math.log(.08),math.log(1.6)),(math.log(.05),math.log(24.))]
    if family=='M2':out.extend([(math.log(.025),math.log(.8)),(.00001,.80)])
    return out

def make_sources(dataset,delta=.15,rho=0.,exclude=None,price_baseline_cap=24.,ros_baseline_cap=225.,price_points='all'):
    if not (0<=rho<.95):raise ValueError('correlation stress parameter out of bounds')
    out={}
    for name in SOURCE_ORDER:
        if name==exclude:continue
        d=dataset['studies'][name]
        t=np.array(d['hours'],float);y=np.array(d['y'],float)
        se=np.array(d['nominal_se_or_read_width'],float)
        if name=='Price1997_figure1' and price_points=='early':
            keep=t<=4;t,y,se=t[keep],y[keep],se[keep]
        if name=='Price1997_figure1' and price_points=='late':
            keep=t>=4;t,y,se=t[keep],y[keep],se[keep]
        err=np.maximum(se,delta*np.abs(y))
        seq=np.arange(len(t))
        cor=rho**np.abs(seq[:,None]-seq[None,:])
        cov=err[:,None]*cor*err[None,:]
        prec=np.linalg.inv(cov)
        cap={'Rosano1997_PK25':ros_baseline_cap,'Komesaroff1998_n10':160.,
             'Price1997_figure1':price_baseline_cap}[name]
        out[name]=dict(time=t,obs=y,error=err,precision=prec,baseline_cap=cap,unit=d['unit'])
    return out

def profile_study(h,s):
    """Exact convex constrained GLS for baseline 0<=b<=cap and amplitude A>=0."""
    y=s['obs'];P=s['precision'];lo=0.;hi=s['baseline_cap']
    H=np.column_stack((np.ones(len(y)),h))
    Q=H.T@P@H;r=H.T@P@y
    attempts=[]
    try:
        b,a=np.linalg.solve(Q,r)
        if lo<=b<=hi and a>=0:attempts.append((float(b),float(a)))
    except np.linalg.LinAlgError:pass
    for b in (lo,hi):
        a=max(0.,float((r[1]-Q[1,0]*b)/Q[1,1])) if Q[1,1]>0 else 0.
        attempts.append((b,a))
    # amplitude=0 boundary
    b=float(np.clip(r[0]/Q[0,0],lo,hi))
    attempts.append((b,0.))
    scores=[]
    for b,a in attempts:
        pred=b+a*h;res=pred-y;v=float(res@P@res/len(y))
        scores.append((v,b,a,pred))
    loss,b,a,pred=min(scores,key=lambda e:e[0])
    return {'pseudo_loss':loss,'baseline':float(b),'amplitude_at_1h':float(a),
            'predictions':[float(x) for x in pred]}

def score(x,family,n,studies,time_logscales=None,detail=False):
    try:
        p=unpack(x,family,n)
        fits={}
        for name,s in studies.items():
            d=0. if time_logscales is None else float(time_logscales[name])
            h=shape(s['time']*math.exp(d),p)
            fits[name]=profile_study(h,s)
        total=sum(v['pseudo_loss'] for v in fits.values())/len(fits)
        if not np.isfinite(total):return float('inf')
        return (float(total),fits,p) if detail else float(total)
    except (ValueError,OverflowError,FloatingPointError,np.linalg.LinAlgError):
        return float('inf')

def seeds(family,n):
    # Deterministic starts: no hidden optimization stochasticity.
    starts=[(.4,4.0,.10,.15),(.8,9.,.25,.45),(.25,14.,.05,.65)]
    if family=='M0':starts=[(.4,.7,.10,0),(.4,2.0,.10,0),(.85,8.,.10,0)]
    for ke,k,slow,w in starts:
        x=[math.log(ke),math.log(max(.051,k-ke))]
        if family=='M2':x += [math.log(slow),w]
        yield np.array(x)

def optimize(family,studies,scale=False,fast=False,max_order=8):
    choices=[1] if family=='M0' else list(range(2,max_order+1))
    best=None; runners=[]
    for n in choices:
        for initial in list(seeds(family,n))[:(2 if fast else 3)]:
            out=minimize(score,initial,args=(family,n,studies),bounds=bounds_for(family),method='L-BFGS-B',
                         options={'maxiter':110 if fast else 180,'ftol':2e-10})
            value=float(score(out.x,family,n,studies))
            if not np.isfinite(value):continue
            candidate=(value,n,np.array(out.x),bool(out.success))
            runners.append(candidate)
            if best is None or value<best[0]:best=candidate
    if best is None:raise RuntimeError('no finite optimizations')
    loss,n,x,success=best
    _,details,params=score(x,family,n,studies,detail=True)
    out={'loss':loss,'parameters':params,'study_profiles':details,
         'n_optimizations':len(runners),'optimizer_success_for_best':success,
         'local_multistart_not_global_proof':True}
    # Equal or near-equal optima: grid is NOT a confidence region.
    per_n={}
    for value,nn,xx,ok in runners:
        if nn not in per_n or value<per_n[nn]['loss']:
            per_n[nn]={'loss':float(value),'param':unpack(xx,family,nn)}
    out['best_for_each_n']=per_n
    out['tail_q6_index']=repeated_index(params)
    if scale:
        # A penalized time-scale shift captures study-level transit speed heterogeneity.
        # Variance/penalty are analyst assumptions, not empirically measured.
        keys=list(studies)
        reg=.30
        bound=math.log(1.25)
        def objective(z):
            ll=score(z[:len(x)],family,n,studies,{key:z[len(x)+i] for i,key in enumerate(keys)})
            return ll+reg*sum((float(z[len(x)+i])/math.log(1.20))**2 for i in range(len(keys)))/len(keys)
        z=np.r_[x,np.zeros(len(keys))]
        enhanced=minimize(objective,z,method='L-BFGS-B',bounds=bounds_for(family)+[(-bound,bound)]*len(keys),
                          options={'maxiter':240})
        ds={key:float(enhanced.x[len(x)+i]) for i,key in enumerate(keys)}
        newloss,_,param=score(enhanced.x[:len(x)],family,n,studies,ds,detail=True)
        out['bounded_study_time_scale_sensitivity']={
            'max_absolute_scale':1.25,'assumed_reference_scale':1.20,'regularization_weight':reg,
            'penalized_objective':float(objective(enhanced.x)),
            'unpenalized_loss':float(newloss),'time_multipliers':{k:float(math.exp(v)) for k,v in ds.items()},
            'refitted_global_parameters':param,'not_empirical_random_effects':True}
    return out

def repeated_index(params):
    # 120 historical administrations: dimensional ratio to 1mg single-dose 1h increment.
    hours=6*np.arange(1,121,dtype=float)
    return float(.5*np.sum(shape(hours,params)))

def price_checks(params,studies,dataset):
    p=studies.get('Price1997_figure1')
    if p is None:return None
    profile=profile_study(shape(p['time'],params),p)
    # Figure is no quantitative t0; hypothetical 0h model pred=baseline.
    times=np.array([0,1,2,3,4,6,8,12,18,24.],float)
    modeled=profile['amplitude_at_1h']*shape(times,params)
    auc=float(np.trapezoid(modeled,times))
    fig=dataset['studies']['Price1997_figure1'];fig_t=np.r_[0,np.array(fig['hours'])]
    fig_y=np.r_[profile['baseline'],np.array(fig['y'])]
    baseline_removed_auc=float(np.trapezoid(fig_y-profile['baseline'],fig_t))
    return {'price_predicted_increment_AUC0_24':auc,
      'price_manual_figure_increment_AUC_at_fitted_baseline':baseline_removed_auc,
      'price_author_table_AUC0_24':dataset['price_author_table_auc_mean'],
      'price_table_minus_model_AUC':dataset['price_author_table_auc_mean']-auc,
      'same_cohort_fig_and_table_not_independent':True,
      'hypothetical_predose_concentration':profile['baseline']}

def render_case(dataset,scenario,fast=False,offsets=False):
    studies=make_sources(dataset,**scenario)
    fitted={fam:optimize(fam,studies,scale=offsets and fam in ('M1','M2'),fast=fast)
            for fam in ('M0','M1','M2')}
    for r in fitted.values():
        r['price_auc_diagnostic']=price_checks(r['parameters'],studies,dataset)
    return {'scenario':scenario,'n_sources':len(studies),'fit':fitted}

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument('--out',required=True)
    ap.add_argument('--fast',action='store_true',help='shorter but still deterministic multistarts')
    a=ap.parse_args()
    dataset=json.loads(DATA.read_text())
    scenarios={'primary':{'delta':.15,'rho':0.},
               'wide_discrepancy':{'delta':.30,'rho':0.},
               'within_study_positive_correlation':{'delta':.15,'rho':.65},
               'rosano_baseline_limited':{'delta':.15,'rho':0.,'ros_baseline_cap':100.},
               'price_baseline_zero':{'delta':.15,'rho':0.,'price_baseline_cap':0.}}
    outcome={'phase':'P2-V','source_repo_default_head_checked':'7a45741f1a83646195db0a0e20c9806da4878af3',
     'sources':'P2-U published aggregate and manual Price plot points','Doll_used':False,
     'method':'continuous multi-start constrained GLS, same shape with separately profiled amplitudes/baselines',
     'not_population_NLME':True,'reported_covariance_only_hypothetical':True,
     'not_blinded_external_validation':True,'human_coverage_proven':False,
     'production_model_or_database_modified':False,
     'scenarios':{name:render_case(dataset,config,a.fast,offsets=name=='primary') for name,config in scenarios.items()}}
    dst=Path(a.out)
    if dst.exists():raise FileExistsError('Refuse overwrite')
    dst.parent.mkdir(parents=True,exist_ok=True)
    dst.write_text(json.dumps(outcome,ensure_ascii=False,indent=2)+'\n')
    for name,sc in outcome['scenarios'].items():
        print(name,' '.join(f"{fam}={sc['fit'][fam]['loss']:.4g} (n={sc['fit'][fam]['parameters']['n_fast']})" for fam in ('M0','M1','M2')),flush=True)
if __name__=='__main__':main()
