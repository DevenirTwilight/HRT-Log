#!/usr/bin/env python3
"""P2-U cross-study, aggregate-data *exploratory* shared-shape constraint scan.

Requires numpy and scipy; uses only already published group statistics and marked
human Figure 1 manual estimates. No Doll observations, no real individual-level
data and no clinical validation. Never changes production model parameters.
"""
import json, math, argparse
from pathlib import Path
from itertools import product
import numpy as np
from scipy.special import gammainc

ROOT = Path(__file__).resolve().parents[2]
OBS_FILE = ROOT/'docs/pk-research/p2/p2u-observed-aggregates.json'
TIMES = np.array(sorted(set([0,10/60,15/60,20/60,30/60,40/60,1,2,3,4,6,8,12,18,24])),float)

def bateman(t,k,ke):
    t=np.asarray(t,float)
    d=k-ke
    if abs(d)<1e-12: return k*t*np.exp(-ke*t)
    return np.exp(-min(k,ke)*t)*k*(-np.expm1(-abs(d)*t))/abs(d)

def g(t,n,k,ke):
    t=np.asarray(t,float)
    if n==1:return bateman(t,k,ke)
    if k<=ke: raise ValueError('k must exceed ke for n>=2 on this grid')
    return np.exp(n*math.log(k/(k-ke))-ke*t)*gammainc(n,(k-ke)*t)

def kernel(t,model):
    n,k,ke,ks,w=model
    return (1-w)*g(t,n,k,ke)+w*bateman(t,ks,ke)

def candidates():
    for k,ke in product([.7,1.2,1.8,3,5,8,12],[.2,.4,.6,.85,1.1]):
        yield 'M0',(1,k,ke,.05,0.)
    for n,k,ke in product(range(2,9),[3,5,7,9,12,16],[.2,.4,.6,.85,1.1]):
        yield 'M1',(n,k,ke,.05,0.)
        for ks,w in product([.05,.1,.2,.4],[.05,.15,.3,.5]):
            yield 'M2',(n,k,ke,ks,w)

def profile(y,t,h,base_bounds,errors):
    y=np.asarray(y,float);h=np.asarray(h,float);err=np.asarray(errors,float)
    if np.any(~np.isfinite(h)) or np.any(err<=0):raise ValueError('Invalid source data')
    lo,hi=base_bounds; w=1/err**2
    s0=np.sum(w);s1=np.sum(w*h);s2=np.sum(w*h*h)
    y0=np.sum(w*y);y1=np.sum(w*y*h)
    tests=[]
    det=s0*s2-s1*s1
    if det>1e-10*s0*s2:
        b=(y0*s2-y1*s1)/det;a=(y1*s0-y0*s1)/det
        if lo<=b<=hi and a>=0:tests.append((float(b),float(a)))
    for b in (lo,hi):tests.append((float(b),float(max(0,(y1-b*s1)/s2)) if s2>0 else 0.))
    tests.append((float(np.clip(y0/s0,lo,hi)),0.))
    scored=[(float(np.mean(((b+a*h-y)/err)**2)),b,a) for b,a in tests]
    loss,b,a=min(scored)
    return {'loss':loss,'baseline':b,'amplitude':a,'pred':[float(z) for z in b+a*h],
            'residual':[float(z) for z in b+a*h-y]}

def sources(dataset,discrepancy,ros_cap,price_cap,include_price=True,excluded=None):
    out={}
    for key,raw in dataset['studies'].items():
        if (key=='Price1997_figure1' and not include_price) or key==excluded:continue
        y=np.array(raw['y']);t=np.array(raw['hours']);se=np.array(raw['nominal_se_or_read_width'])
        err=np.maximum(se,float(discrepancy)*np.abs(y))
        cap={'Rosano1997_PK25':ros_cap,'Komesaroff1998_n10':160.,'Price1997_figure1':price_cap}[key]
        out[key]={'y':y,'t':t,'errors':err,'bounds':(0,float(cap))}
    return out

def calculate(dataset,discrepancy,ros_cap,price_cap,include_price=True,excluded=None):
    study=sources(dataset,discrepancy,ros_cap,price_cap,include_price,excluded)
    winners={name:[] for name in ('M0','M1','M2')}
    totals={k:0 for k in winners}
    for fam,m in candidates():
        resp=kernel(TIMES,m)
        shape=resp / resp[np.where(np.isclose(TIMES,1))[0][0]]
        if np.any(~np.isfinite(shape)):continue
        details={}
        for name,raw in study.items():
            h=np.interp(raw['t'],TIMES,shape)
            details[name]=profile(raw['y'],raw['t'],h,raw['bounds'],raw['errors'])
        objective=sum(v['loss'] for v in details.values()) / len(details)  # EQUAL STUDY WEIGHTS
        totals[fam]+=1
        winners[fam].append((objective,m,details))
    result={}
    for name,items in winners.items():
        items.sort(key=lambda a:a[0]);loss,m,details=items[0]
        f=lambda t:kernel(np.array([t]),m)[0]/kernel(np.array([1.]),m)[0]
        p=details.get('Price1997_figure1')
        omitted_result=None
        if excluded:
            omitted=sources(dataset,discrepancy,ros_cap,price_cap,True)[excluded]
            ex_h=kernel(omitted['t'],m)/kernel(np.array([1.]),m)[0]
            omitted_fit=profile(omitted['y'],omitted['t'],ex_h,omitted['bounds'],omitted['errors'])
            omitted_result={'study':excluded,'profiled_pseudo_loss_on_already_known_study':omitted_fit['loss'],
                            'baseline':omitted_fit['baseline'],'amplitude':omitted_fit['amplitude']}
        price_auc=None
        if p:
            hours=np.array([0,1,2,3,4,6,8,12,18,24])
            concentration=p['amplitude']*kernel(hours,m)/kernel(np.array([1.]),m)[0]
            price_auc=float(np.trapezoid(concentration,hours))
        near=[v for v in items if v[0]<=1.5*loss+1e-12]
        tail=[]
        for _,nm,_ in near:
            norm=float(kernel(np.array([1.]),nm)[0])
            tail.append(float(.5*sum(kernel(np.array([6*j]),nm)[0]/norm for j in range(1,121))))
        result[name]={'excluded_study_diagnostic_NOT_blind_validation':omitted_result, 'n_evaluated':totals[name], 'objective_equal_cohort_pseudo_loss':float(loss),
            'near_1p5x_best_count':len(near), 'near_1p5x_best_q6_range':[min(tail),max(tail)],
            'near_1p5x_best_orders':sorted({v[1][0] for v in near}),
            'parameters':{'n_fast':m[0],'k_fast_h':m[1],'k_elim_h':m[2],'k_slow_h':m[3] if m[4]>0 else None,'w_effective':m[4]},
            'study_profiles':{k:{'pseudo_loss':v['loss'],'baseline':v['baseline'],'amplitude':v['amplitude'],
                 'pred':v['pred']} for k,v in details.items()},
            'normalized_ratio_40to20':float(f(40/60)/f(20/60)),
            'normalized_ratio_30to15':float(f(.5)/f(.25)),
            'normalized_price_2to1':float(f(2)),
            'normalized_price_4to1':float(f(4)),
            'price_baseline_subtracted_auc0_24_h':price_auc,
            'price_table_auc0_24_mean':dataset['price_author_table_auc_mean'],
            'repeat_q6_predose_relative_to_single_1mg_1h':float(.5*sum(f(6*j) for j in range(1,121))),
            'top10_objectives':[float(v[0]) for v in items[:10]]}
    return result

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--out',required=True)
    opt=parser.parse_args()
    dataset=json.loads(OBS_FILE.read_text())
    cases=[('all_floor_0pct',0,225,24,True),('all_floor_15pct',.15,225,24,True),('all_floor_30pct',.30,225,24,True),('rosano_b0',.15,0,24,True),('rosano_b100',.15,100,24,True),('price_b0',.15,225,0,True),('without_price',.15,225,24,False),('omit_Price',.15,225,24,True),('omit_Rosano',.15,225,24,True),('omit_Komesaroff',.15,225,24,True)]
    out={'research':'P2-U','dataset_kind':'EXPOSED_AGGREGATE_AND_PRICE_MANUAL_FIGURE',
      'model_values_not_clinical_parameters':True,'Doll_used':False,
      'uncorrelated_pseudoloss_not_a_likelihood_or_hypothesis_test':True,
      'repeated_dose_values_are_dimensionless_synthetic_only':True,
      'clinical_accuracy_established':False,'production_model_upgrade_authorized':False,
      'scenarios':{name:{'config':{'relative_model_discrepancy_floor':floor,'rosano_baseline_upper_pmol_l':ros,'price_baseline_upper_pg_ml':price,'price_included':pp},'fit':calculate(dataset,floor,ros,price,pp,excluded={'omit_Price':'Price1997_figure1','omit_Rosano':'Rosano1997_PK25','omit_Komesaroff':'Komesaroff1998_n10'}.get(name))} for name,floor,ros,price,pp in cases}}
    dst=Path(opt.out)
    if dst.exists():raise FileExistsError(dst)
    dst.parent.mkdir(parents=True,exist_ok=True)
    dst.write_text(json.dumps(out,ensure_ascii=False,indent=2))
    print(json.dumps({scenario:{m:{'loss':round(v['objective_equal_cohort_pseudo_loss'],3),'n':v['parameters']['n_fast'],'price_auc':None if v['price_baseline_subtracted_auc0_24_h'] is None else round(v['price_baseline_subtracted_auc0_24_h']), 'ros_loss':None if 'Rosano1997_PK25' not in v['study_profiles'] else round(v['study_profiles']['Rosano1997_PK25']['pseudo_loss'],2),'kom_loss':None if 'Komesaroff1998_n10' not in v['study_profiles'] else round(v['study_profiles']['Komesaroff1998_n10']['pseudo_loss'],2)} for m,v in x['fit'].items()} for scenario,x in out['scenarios'].items()},indent=2))

if __name__=='__main__':main()
