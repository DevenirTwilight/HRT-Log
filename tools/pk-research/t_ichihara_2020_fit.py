#!/usr/bin/env python3
"""Research-only fitting to Ichihara et al. 2020 Table 2, testosterone enanthate.
10 transgender men, group means. Days 2 and 21 held out (same cohort).
Python standard library only; NO validated personal concentration predictions.
"""
import json
import math
import sys
from pathlib import Path

DAYS=[0,1,2,3,7,14,21,28]
MEAN=[.75,27.99,21.69,23.33,14.48,5.81,2.72,1.33]
SD=[.5,17.5,9.6,15.8,4.7,2.4,1.6,.8]
TRAIN=[1,3,4,5,7]
HOLD=[2,6]
E=24*math.log(2)  # one-hour IV parent-T half-life, assumed from Nebido RCP
A=math.log(2)/4.5  # terminal depot release half-life, Androtardyl RCP

def first_order(t,a,e=E):
    if t<=0:return 0.
    d=a-e
    if abs(d)<1e-8:return a*t*math.exp(-e*t)
    if d<0:return a*math.exp(-a*t)*math.expm1(d*t)/d
    return a*math.exp(-e*t)*(-math.expm1(-d*t))/d

def two_stage(t,a,e=E):
    if t<=0:return 0.
    d=e-a
    if abs(d)<1e-6:return a*a*t*t*math.exp(-a*t)/2
    return a*a*math.exp(-a*t)*(d*t-1+math.exp(-d*t))/(d*d)

def fit_gain(shape):
    x=[shape(t) for t in DAYS]
    y=[MEAN[i]-.75 for i in TRAIN]
    gain=sum(x[j]*y[z] for z,j in enumerate(TRAIN))/sum(x[j]**2 for j in TRAIN)
    pred=[.75+gain*v for v in x]
    tr=math.sqrt(sum((pred[i]-MEAN[i])**2 for i in TRAIN)/len(TRAIN))
    ho=math.sqrt(sum((pred[i]-MEAN[i])**2 for i in HOLD)/len(HOLD))
    return {'gain':gain,'predictions_ng_ml':pred,'train_rmse_ng_ml':tr,
            'within_cohort_holdout_rmse_ng_ml':ho}

def fit_mixture():
    def model(k,w):
        return lambda t:(1-w)*two_stage(t,k)+w*first_order(t,A)
    best=None
    for k in [.1+i*.02 for i in range(96)]:
        for wi in range(0,100,2):
            w=wi/100
            v=fit_gain(model(k,w))
            if best is None or v['train_rmse_ng_ml']<best['train_rmse_ng_ml']:
                best={'transit_input_rate_day':k,'slow_mass_weight':w,**v}
    for dk,dw in [(.02,.02),(.005,.005),(.001,.001),(.0002,.0002)]:
        for _ in range(4):
            choices=[]
            for xx in [-1,0,1]:
                for yy in [-1,0,1]:
                    k=max(.1,min(2.,best['transit_input_rate_day']+xx*dk))
                    w=max(0.,min(.99,best['slow_mass_weight']+yy*dw))
                    choices.append({'transit_input_rate_day':k,'slow_mass_weight':w,**fit_gain(model(k,w))})
            new=min(choices,key=lambda v:v['train_rmse_ng_ml'])
            if new['train_rmse_ng_ml']>=best['train_rmse_ng_ml']-1e-12:break
            best=new
    return best

def research():
    return {'source':'https://journals.sagepub.com/doi/full/10.1089/andro.2020.0002',
       'source_table':'Ichihara et al 2020 Table 2 group mean and SD (N=10), IM enanthate 250mg',
       'sampling_days':DAYS,'mean_total_testosterone_ng_ml':MEAN,'sd_ng_ml':SD,
       'train_days':[DAYS[i] for i in TRAIN], 'within_cohort_holdout_days':[DAYS[i] for i in HOLD],
       'fixed_group_mean_baseline_ng_ml':MEAN[0],
       'assumed_depot_half_life_days':4.5,'assumed_parent_T_IV_half_life_hours':1,
       'simple_single_input':fit_gain(lambda t:first_order(t,A)),
       'two_input':fit_mixture(),
       'warning':'Research fit to 5 group means, held-out 2 times from SAME cohort. Individual and independent cohort validity NOT established.',
       'research_only':True,'external_validation':False}

def checks():
    v=research()
    assert len(DAYS)==8 and v['train_days']==[1,3,7,14,28]
    assert v['within_cohort_holdout_days']==[2,21]
    assert v['two_input']['train_rmse_ng_ml'] < v['simple_single_input']['train_rmse_ng_ml']
    assert v['two_input']['within_cohort_holdout_rmse_ng_ml'] > 0
    assert abs(v['two_input']['transit_input_rate_day']-.2236)<.02
    assert abs(v['two_input']['slow_mass_weight']-.7874)<.03
    assert not v['external_validation']
    return v

if __name__=='__main__':
    d=checks()
    out=Path(sys.argv[1]) if len(sys.argv)>1 else Path('t_ichihara_2020_fit.json')
    out.parent.mkdir(parents=True,exist_ok=True)
    out.write_text(json.dumps(d,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
    print('PASS 7 numerical/data guards; report:',out)
    print('training RMSE:',d['two_input']['train_rmse_ng_ml'],
          'within-cohort holdout RMSE:',d['two_input']['within_cohort_holdout_rmse_ng_ml'])
