#!/usr/bin/env python3
"""T_RCP_ROUTE_FIT_01. Research-only route stress tests; no clinical validity.
Sparse RCP aggregates do not identify personal serum concentration curves.
Standard-library Python; produces JSON and CSV with ILLUSTRATIVE flags.
"""
import csv
import json
import math
import sys
from pathlib import Path

ELIM = 24*math.log(2)  # 1h IV T half-life, RCP for NEBIDO, assumed for others
SLOW_NE = math.log(2)/90  # Nebido terminal depot release, 90 +/- 40 days
SCHEDULE_NE = [0,42,112,182,252,322,392]

def bateman(t, a, e=ELIM):
    if t <= 0: return 0.
    if a<=0 or e<=0: raise ValueError("nonpositive rate")
    d=a-e
    if abs(d)<1e-7:
        return a*t*math.exp(-e*t)*(-math.expm1(-d*t)/(d*t) if d else 1.)
    if d>0:
        return a*math.exp(-e*t)*(-math.expm1(-d*t))/d
    return a*math.exp(-a*t)*math.expm1(d*t)/d

def transit2(t,a,e=ELIM):
    """Positive convolution of two-stage Erlang input with plasma elimination."""
    if t<=0: return 0.
    d=e-a
    if abs(d)<1e-6: return a*a*t*t*math.exp(-e*t)/2
    return a*a*math.exp(-a*t)*(d*t-1+math.exp(-d*t))/(d*d)

def pmax(fn,left,right,step=.5):
    return max(((fn(left+i*step),left+i*step) for i in range(int(round((right-left)/step))+1)),key=lambda x:x[0])

def nonnegative_ols(x,y):
    xm=sum(x)/len(x);ym=sum(y)/len(y)
    den=sum((v-xm)**2 for v in x)
    g=max(0.,sum((v-xm)*(o-ym) for v,o in zip(x,y))/den) if den else 0.
    b=ym-g*xm
    cases=[(max(0.,ym),0.),(0.,max(0.,sum(v*o for v,o in zip(x,y))/sum(v*v for v in x)))]
    if b>=0:cases.append((b,g))
    return min(cases,key=lambda bg:sum((bg[0]+bg[1]*v-o)**2 for v,o in zip(x,y)))

def enanthate_kernel(t):
    # Exploratory: 60% fast 2-stage input + 40% slow 4.5d-release input.
    # Mix fraction and fast rate are NOT estimated from original individual data.
    return .6*transit2(t,.5)+.4*bateman(t,math.log(2)/4.5)

def androtardyl():
    k=enanthate_kernel;p,tp=pmax(k,0,30,.05)
    snapshots=[]
    for b in [0.,.5,1.,1.5,2.]:
        c20=b+(20-b)*k(20)/p;c26=b+(20-b)*k(26)/p
        snapshots.append({'assumed_baseline_ng_ml':b,'day20_ng_ml':round(c20,4),
            'day26_ng_ml':round(c26,4),'passes_both_lower_bounds':c20>=2 and c26>=1})
    r20=k(20)/p;r26=k(26)/p
    return {'source_ester_mg':250,'parent_T_equivalent_mg':180,
        'official_Cmax_total_ng_ml':20,'official_Tmax_range_days':[1.5,3],
        'official_day20_lower_bound_ng_ml':2,'official_day26_lower_bound_ng_ml':1,
        'depot_release_half_life_days':4.5,'assumed_IV_half_life_hours':1,
        'naive_single_exponential_peak_day':round(pmax(lambda t:bateman(t,math.log(2)/4.5),0,8,.01)[1],4),
        'two_input_peak_day':round(tp,4),'minimum_assumed_baseline_to_pass_ng_ml':
        round(max(0,(2-20*r20)/(1-r20),(1-20*r26)/(1-r26)),4),
        'baseline_sensitivity':snapshots,'status':'CONDITIONAL_NOT_VALIDATED'}

def androgel():
    profiles=[]
    for a in [1.,1.5,2.5]:
        k=lambda t:bateman(t,a)
        f=lambda t:sum(k(t-d) for d in range(11) if t>d)
        mean=sum(f(9+i/48) for i in range(48))/48
        g=2.5/mean
        profiles.append({'skin_input_per_day_assumed':a,
            'first_hour_increment_ng_ml':round(g*k(1/24),5),
            'day2_at_12h_increment_ng_ml':round(g*f(2.5),5),
            'post_last_application_day11_to14_ng_ml':
                {str(day):round(g*f(day),5) for day in [11,12,13,14]}})
    return {'official_product':'ANDROGEL 50 mg (5g sachet), not 16.2 mg/g pump',
        'official_absorption_fraction':[.09,.14],
        'official_mean_increase_ng_ml_approx':2.5,
        'official_steady_from_day':2,'official_return_to_baseline_after_stop_h':[72,96],
        'assumption':'RCP mean increment treated conditionally as 24h day9 mean; original AUC not supplied',
        'shape_family':profiles,'status':'NONIDENTIFIED_ILLUSTRATIVE'}

def nebido_kernel(t,a,w):
    return (1-w)*transit2(t,a)+w*bateman(t,SLOW_NE)

def nebido():
    y=[38.,50.,37.,16.] # Population aggregate, FOUR NON-EQUIVALENT STAGES
    rows=[]
    for a in [.10,.12,.14,.16,.18,.20,.22]:
        for wi in range(1,19):
            w=wi/20
            k=lambda t:nebido_kernel(t,a,w)
            c1,t1=pmax(k,0,28,.5)
            c2,_=pmax(lambda t:k(t)+k(t-42),42,70,.5)
            ss=lambda t:sum(k(t-d) for d in SCHEDULE_NE if t>d)
            cmax,_=pmax(ss,392,462,.5)
            cmin=ss(462)
            x=[c1,c2,cmax,cmin];b,g=nonnegative_ols(x,y)
            pred=[b+g*v for v in x]
            resid=[round(z-yy,5) for z,yy in zip(pred,y)]
            sse=sum(z*z for z in resid)
            rows.append({'score':round(sse+.8*(t1-7)**2,6),'fast_input_per_day':a,
                'slow_input_mass_weight':w,'first_peak_day':t1,
                'fitted_baseline_nmol_l':round(b,6),'fitted_gain':round(g,6),
                'predicted_stage_markers_nmol_l':[round(v,5) for v in pred],
                'residual_nmol_l':resid,'sse':round(sse,6)})
    rows.sort(key=lambda r:(r['score'],r['fast_input_per_day'],r['slow_input_mass_weight']))
    return {'source_ester_mg':1000,'parent_T_equivalent_mg':631.5,
        'official_first_peak_day':7,'official_first_Cmax_nmol_l':38,
        'official_second_Cmax_nmol_l':50,
        'official_steady_Cmax_nmol_l':37,'official_steady_Cmin_nmol_l':16,
        'official_depot_release_half_life_days':90,
        'assumed_IV_half_life_hours':1,'injection_days':SCHEDULE_NE,
        'fit_assumption':'one invariant baseline and gain over initiation + maintenance; equal-weight aggregate SSE; time penalty .8/day²',
        'candidate_count':len(rows),'minimum_score_candidate':rows[0],
        'other_shape_candidate':next(x for x in rows if x['slow_input_mass_weight']>=.5),
        'status':'RESIDUALS_NONZERO_NOT_VALIDATED'}

def build():
    return {'study':'T_RCP_ROUTE_FIT_01','research_only':True,
        'source_urls':{
          'ANDROGEL_50':'https://base-donnees-publique.medicaments.gouv.fr/medicament/64205548/extrait',
          'ANDROTARDYL':'https://base-donnees-publique.medicaments.gouv.fr/medicament/68178899/extrait',
          'NEBIDO':'https://base-donnees-publique.medicaments.gouv.fr/medicament/69033757/extrait'},
        'ANDROGEL_50':androgel(),'ANDROTARDYL':androtardyl(),'NEBIDO':nebido()}

def export(data,out):
    out.mkdir(parents=True,exist_ok=True)
    (out/'results.json').write_text(json.dumps(data,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
    with (out/'illustrative_curves.csv').open('w',newline='',encoding='utf-8') as f:
        w=csv.writer(f)
        w.writerow(['product','model_scenario','days_since_first_dose','model_value','unit','scientific_status'])
        k=enanthate_kernel;mx,_=pmax(k,0,30,.05)
        for b in [0.,1.,2.]:
            for i in range(121):
                t=i/4
                w.writerow(['ANDROTARDYL',f'baseline_assumed={b}',t,round(b+(20-b)*k(t)/mx,6),'ng/mL','ILLUSTRATIVE'])
        for a in [1.,1.5,2.5]:
            ff=lambda t:sum(bateman(t-d,a) for d in range(11) if t>d)
            mean=sum(ff(9+i/48) for i in range(48))/48
            for i in range(61):
                t=i/4
                w.writerow(['ANDROGEL_50',f'skin_rate_assumed={a}',t,round(2.5*ff(t)/mean,6),'ng/mL above baseline','ILLUSTRATIVE'])
        n=data['NEBIDO']['minimum_score_candidate']
        a=n['fast_input_per_day'];ww=n['slow_input_mass_weight']
        fn=lambda t:sum(nebido_kernel(t-d,a,ww) for d in SCHEDULE_NE if t>d)
        for i in range(941):
            t=i/2
            w.writerow(['NEBIDO','constant_baseline_stress_test',t,
                round(n['fitted_baseline_nmol_l']+n['fitted_gain']*fn(t),6),
                'nmol/L','ILLUSTRATIVE_AGGREGATE_STRESS_TEST'])

def test():
    assert abs(bateman(2,1,1)-2*math.exp(-2))<1e-8
    assert abs(transit2(2,1,1)-2*math.exp(-2))<1e-8
    assert all(nebido_kernel(t,.15,.6)>=0 for t in [0,.001,.1,1,7,20,70,500])
    a=androtardyl()
    assert 1.5<=a['two_input_peak_day']<=3
    assert a['naive_single_exponential_peak_day']<1
    assert 1<a['minimum_assumed_baseline_to_pass_ng_ml']<2
    assert all(len(x['post_last_application_day11_to14_ng_ml'])==4 for x in androgel()['shape_family'])
    n=nebido()
    assert n['candidate_count']==126
    assert n['minimum_score_candidate']['sse']>0
    assert n['minimum_score_candidate']['fitted_baseline_nmol_l']>=0
    return 10

if __name__=='__main__':
    passed=test()
    data=build()
    out=Path(sys.argv[1]) if len(sys.argv)>1 else Path('t_route_calibration_results')
    export(data,out)
    print('PASS',passed,'research checks; exports',out)
    print('ANDROTARDYL',data['ANDROTARDYL']['two_input_peak_day'])
    print('NEBIDO residuals',data['NEBIDO']['minimum_score_candidate']['residual_nmol_l'])
