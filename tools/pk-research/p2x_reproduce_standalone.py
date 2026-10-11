#!/usr/bin/env python3
"""Recompute P2-X's 40 exposed-study M2 fits from aggregate observations.

Research-only, no human-level fitting, no independent external validation, no Doll
calibration, no patient/production use. Script requires numpy>=2 and scipy.
This is a compact source-to-parameter implementation, NOT a clinical estimator.
"""
import argparse
import hashlib
import json
import math
from pathlib import Path
import numpy as np
from scipy.optimize import minimize
from scipy.special import gammainc

# All raw inputs are previously-exposed study group aggregates. Price is manually
# digitized Figure 1 and is NOT author-supplied numerical time-series data.
SOURCES = {
  'Rosano1997_PK25': {'t':[1/6,1/3,2/3,1], 'y':[234,468,1980,2124], 'se':[11.2,23,91.2,113.6], 'cap':225},
  'Komesaroff1998_n10': {'t':[0,.25,.5], 'y':[89.4,486.6,1969], 'se':[9,218,302], 'cap':160},
  'Price1997_figure1': {'t':[1,2,3,4,6,8,12,18,24], 'y':[450,225,116,85,56,45,34,25,24],
      'se':[15,32.5,12,8.5,7,6.5,6,5.5,5.5], 'cap':24},
}
BASIS=('Rosano1997_PK25','Komesaroff1998_n10','Price1997_figure1')
SOURCE_URLS={'Rosano1997_PK25':'https://doi.org/10.1161/01.cir.96.9.2837',
    'Komesaroff1998_n10':'https://doi.org/10.1210/jcem.83.7.4945',
    'Price1997_figure1':'https://pubmed.ncbi.nlm.nih.gov/9052581/'}
PRICE_BASELINES=(0.,6.,12.,18.,24.)
ROSANO_CAPS=(100.,225.)
ORDERS=(4,6,8,10)


def parameters(x,n):
    e=float(np.exp(x[0]));return {'family':'M2','n_fast':n,
        'k_fast_per_h':e+float(np.exp(x[1])), 'k_elim_per_h':e,
        'k_slow_per_h':float(np.exp(x[2])),'effective_slow_weight':float(x[3])}


def shape(times,p):
    t=np.asarray(times,dtype=float)
    k=p['k_fast_per_h'];ke=p['k_elim_per_h'];s=p['k_slow_per_h'];w=p['effective_slow_weight'];n=p['n_fast']
    if np.any(t<0) or not (k>ke>0 and s>0 and 0<=w<=1):raise ValueError('invalid shape')
    fast=np.exp(n*np.log(k/(k-ke))-ke*t)*gammainc(n,(k-ke)*t)
    slow=s*np.exp(-min(s,ke)*t)*(-np.expm1(-abs(s-ke)*t))/abs(s-ke) if abs(s-ke)>=1e-9*max(s,ke) else s*t*np.exp(-ke*t)
    v=(1-w)*fast+w*slow
    f=np.exp(n*np.log(k/(k-ke))-ke)*gammainc(n,k-ke)
    q=s*math.exp(-min(s,ke))*(-math.expm1(-abs(s-ke)))/abs(s-ke) if abs(s-ke)>=1e-9*max(s,ke) else s*math.exp(-ke)
    norm=(1-w)*f+w*q
    if norm<=0 or not math.isfinite(norm):raise ValueError('invalid reference')
    return v/norm


def source_profiles(cap, discrepancy=.15, rho=0.):
    profiles={}
    for name in BASIS:
        d=SOURCES[name];t=np.asarray(d['t'],float);y=np.asarray(d['y'],float)
        e=np.maximum(np.asarray(d['se'],float), discrepancy*np.abs(y))
        idx=np.arange(len(t));cor=rho**np.abs(idx[:,None]-idx[None,:])
        precision=np.linalg.inv(e[:,None]*cor*e[None,:])
        profiles[name]={'t':t,'y':y,'P':precision,'cap':cap if name=='Rosano1997_PK25' else d['cap']}
    return profiles


def best_local_amplitude(curve,study,price_baseline=None):
    y,P=study['y'],study['P']
    if price_baseline is not None:
        baseline=float(price_baseline)
        a=max(0.,float(curve@P@(y-baseline)/(curve@P@curve)))
        residual=baseline+a*curve-y
        return {'baseline':baseline,'amplitude_at_1h':a,'pseudo_loss':float(residual@P@residual/len(y))}
    h=np.column_stack((np.ones(len(y)),curve));Q=h.T@P@h;r=h.T@P@y
    options=[]
    try:
        b,a=np.linalg.solve(Q,r)
        if 0<=b<=study['cap'] and a>=0: options.append((float(b),float(a)))
    except np.linalg.LinAlgError:pass
    for b in (0.,study['cap']):
        a=max(0.,float((r[1]-Q[1,0]*b)/Q[1,1])) if Q[1,1]>0 else 0.
        options.append((b,a))
    options.append((float(np.clip(r[0]/Q[0,0],0,study['cap'])),0.))
    scored=[]
    for b,a in options:
        res=b+a*curve-y;scored.append((float(res@P@res/len(y)),b,a))
    loss,b,a=min(scored,key=lambda item:item[0]);return {'baseline':b,'amplitude_at_1h':a,'pseudo_loss':loss}


def objective(x,n,studies,price_baseline,details=False):
    try:
        p=parameters(x,n)
        prof={name:best_local_amplitude(shape(s['t'],p),s,price_baseline if name=='Price1997_figure1' else None)
              for name,s in studies.items()}
        loss=float(np.mean([d['pseudo_loss'] for d in prof.values()]))
        if not math.isfinite(loss):return float('inf')
        return (loss,p,prof) if details else loss
    except (ValueError, ZeroDivisionError, OverflowError, FloatingPointError, np.linalg.LinAlgError):
        return float('inf')


def fit_one(studies,n,b):
    choices=[]
    for ke,k,ks,w in ((.65,12.,.08,.35),(1.35,15.,.25,.51)):
        initial=np.asarray([math.log(ke),math.log(max(.051,k-ke)),math.log(ks),w])
        r=minimize(objective,initial,args=(n,studies,b),bounds=[(math.log(.08),math.log(1.6)),(math.log(.05),math.log(24.)),
          (math.log(.025),math.log(.8)),(.00001,.8)],method='L-BFGS-B',options={'maxiter':130,'ftol':1e-9})
        val=objective(r.x,n,studies,b)
        if math.isfinite(val):choices.append((val,r.x,bool(r.success)))
    if not choices:raise RuntimeError('no finite fits')
    _,x,success=min(choices,key=lambda z:z[0])
    loss,p,prof=objective(x,n,studies,b,True)
    times=np.linspace(0,24,1201);values=shape(times,p)
    tails={str(h):float(np.sum(shape(h*np.arange(1,241,dtype=float),p))) for h in (6,12,24)}
    return {'price_fixed_baseline_pg_ml':b,'rosano_baseline_cap_pmol_l':studies['Rosano1997_PK25']['cap'],
        'n_fast':n,'score':loss,'parameter':p,'fit':prof,
        'relative_1mg_peak_time_h':float(times[np.argmax(values)]),
        'normalized_increment_auc_0_24_h':float(np.trapezoid(values,times)),
        'trough_index_per_1mg_q6_q12_q24':tails,'optimizer_reported_success':success}


def reproduce():
    rows=[]
    for cap in ROSANO_CAPS:
        studies=source_profiles(cap)
        for b in PRICE_BASELINES:
            for n in ORDERS:rows.append(fit_one(studies,n,b))
    return rows


def compact_compare(original,reproduced):
    if len(original)!=len(reproduced):raise AssertionError('row counts changed')
    max_diff=0.0
    for i,(a,b) in enumerate(zip(original,reproduced)):
        for field in ('price_fixed_baseline_pg_ml','rosano_baseline_cap_pmol_l','n_fast','score',
                      'relative_1mg_peak_time_h','normalized_increment_auc_0_24_h'):
            delta=abs(a[field]-b[field]);max_diff=max(max_diff,delta)
            if delta>1e-5:raise AssertionError(f'row {i} field {field}: {a[field]} vs {b[field]}')
        for field in ('k_fast_per_h','k_elim_per_h','k_slow_per_h','effective_slow_weight'):
            delta=abs(a['parameter'][field]-b['parameter'][field]);max_diff=max(max_diff,delta)
            if delta>1e-4:raise AssertionError(f'row {i} param {field}: {a["parameter"][field]} vs {b["parameter"][field]}')
    return max_diff


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out',required=True,help='new path (overwrite refused)')
    parser.add_argument('--compare-json',help='previous P2-X full JSON snapshot')
    args=parser.parse_args()
    path=Path(args.out)
    if path.exists():raise FileExistsError(path)
    rows=reproduce()
    if args.compare_json:
        data=json.loads(Path(args.compare_json).read_text())
        md=compact_compare(data['rows'],rows)
        print(f'Compared {len(rows)} rows; largest absolute param/score difference: {md:.3g}')
    path.write_text(json.dumps({'phase':'P2-X-source-reproduction','scope':'EXPOSED_CONDITIONAL_NOT_VALIDATION',
       'Doll_used':False,'source_urls':SOURCE_URLS,'rows':rows},indent=2)+'\n')
    print('Written',path,'sha256',hashlib.sha256(path.read_bytes()).hexdigest())
if __name__=='__main__':main()
