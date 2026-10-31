#!/usr/bin/env python3
"""SC testosterone cypionate, 2017: family of conditional curves from four
published group means. Illustrates missing information, NOT a population PK fit.
A periodic beta envelope has artificial zero-excess endpoints and a selected
baseline. Two exact anchor matches never establish physiological validity.
"""
import csv
import json
import math
import sys
from pathlib import Path

SOURCE='https://pmc.ncbi.nlm.nih.gov/articles/PMC5686655/'
OBS={0.:497.,.25:656.,5.:621.,7.:477.}  # ng/dL, means only
BASELINE_ASSUMED=(OBS[0.]+OBS[7.])/2  # 487; an imposed periodic endpoint, NOT physiological baseline

def lshape(t,peak,period=7.):
    if not 0<t<period or not 0<peak<period:raise ValueError('invalid t or peak')
    qratio=(period-peak)/peak
    return math.log(t/peak)+qratio*math.log((period-t)/(period-peak))

def scenario(peak):
    a=lshape(.25,peak); b=lshape(5.,peak)
    ratio=(OBS[5.]-BASELINE_ASSUMED)/(OBS[.25]-BASELINE_ASSUMED)
    power=math.log(ratio)/(b-a)
    if not math.isfinite(power) or power<=0:raise ValueError('incompatible shape')
    height=(OBS[.25]-BASELINE_ASSUMED)*math.exp(-power*a)
    def curve(t):
        if t<=0 or t>=7:return BASELINE_ASSUMED
        return BASELINE_ASSUMED+height*math.exp(power*lshape(t,peak))
    fit={str(t):round(curve(t),5) for t in OBS}
    return {'assumed_peak_day':peak,'fitted_shape_power':power,'implied_peak_total_ng_dl':BASELINE_ASSUMED+height,
        'selected_trough_ng_dl':BASELINE_ASSUMED,'model_at_reported_times_ng_dl':fit,
        'endpoint_deviation_day0_ng_dl':BASELINE_ASSUMED-OBS[0.],
        'endpoint_deviation_day7_ng_dl':BASELINE_ASSUMED-OBS[7.],
        'curve':[ [round(i/24,7),round(curve(i/24),6)] for i in range(169)]}

def run():
    scenarios=[scenario(p) for p in (.5,1.,1.5,2.)]
    return {'source':SOURCE,'source_identity':'11 transgender men receiving weekly SC testosterone cypionate; pooled cross-person group means',
       'sampling_means_ng_dl':{str(k):v for k,v in OBS.items()},'chosen_endpoint_ng_dl':BASELINE_ASSUMED,
       'scenario_curves':scenarios,'warnings':[
         '0h=497 and 7d=477 ng/dL are distinct measured sample means; deterministic 7-day steady cycle would make both equal. This 20 ng/dL difference is not evidence that steady state fails: variability is large.',
         'Group mean concentration at only 4 published times gives no identified absorption kernel or endogenous T suppression model.',
         'Peak time is assumed and varied, never estimated from independent peak samples.',
         'The day2-peak case is mathematically valid but implies wildly implausible concentration; constrain physiological plausibility before fitting.',
         'Do not carry this cypionate fit over to enanthate, IM injection or absolute HRT Log predictions.'
       ],'research_only':True,'independent_validation':False}

def check():
    d=run()
    assert len(d['scenario_curves'])==4
    for x in d['scenario_curves']:
        assert abs(float(x['model_at_reported_times_ng_dl']['0.25'])-656)<1e-3
        assert abs(float(x['model_at_reported_times_ng_dl']['5.0'])-621)<1e-3
        assert x['endpoint_deviation_day0_ng_dl']==-10
        assert x['endpoint_deviation_day7_ng_dl']==10
    assert d['scenario_curves'][0]['implied_peak_total_ng_dl']<700
    assert d['scenario_curves'][-1]['implied_peak_total_ng_dl']>10000
    return d

if __name__=='__main__':
    out=Path(sys.argv[1] if len(sys.argv)>1 else 'cypionate_research')
    out.mkdir(parents=True,exist_ok=True);d=check()
    (out/'cypionate_partial_results.json').write_text(json.dumps(d,indent=2)+'\n')
    with (out/'cypionate_partial_curves.csv').open('w',newline='') as f:
        w=csv.writer(f);w.writerow(['peak_day_assumed','time_day','total_T_ng_dL','status'])
        for s in d['scenario_curves']:
            for t,c in s['curve']:w.writerow([s['assumed_peak_day'],t,c,'SYNTHETIC_NOT_VALIDATED'])
    print('PASS cypionate 4-point group-summary stress checks; output',out)
    print([(s['assumed_peak_day'],round(s['implied_peak_total_ng_dl'],1)) for s in d['scenario_curves']])
