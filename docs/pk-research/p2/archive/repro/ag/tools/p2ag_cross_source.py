#!/usr/bin/env python3
"""P2-AG: retrospective temporal transport/holdout audit; NO clinical validation.

Train only on early Rosano & Komesaroff aggregate timepoints. Do not use
Price figure in optimization. After locking shape, calibrate a single Price
1h anchor and evaluate untouched Price figure 2-24h points. All research
sources have been PREVIOUSLY EXPOSED, so not a blinded external validation.
"""
from __future__ import annotations
import argparse, json, math, sys, time
from pathlib import Path
import numpy as np
from scipy.optimize import minimize
sys.path.insert(0,str(Path(__file__).resolve().parent))
import p2v_continuous_fit as v

ROOT=Path(__file__).resolve().parents[1]
GRID_N=(4,6,8,10)
BASES=(0.,6.,12.,18.,24.)
CAPS=(100.,225.)
SEEDS_M1=((.40,4.,0.,0.),(.80,9.,0.,0.),(.25,14.,0.,0.))
SEEDS_M2=((.65,12.,.08,.35),(1.35,15.,.25,.51),(.38,9.,.07,.65))
P2X=ROOT/'data/p2x-conditional-ensemble-results.json'
SOURCE=ROOT/'data/p2u-observed-aggregates.json'


def starts(fam):
    for ke,k,ks,w in (SEEDS_M2 if fam=='M2' else SEEDS_M1):
        a=[math.log(ke),math.log(max(.051,k-ke))]
        if fam=='M2':a += [math.log(ks),w]
        yield np.array(a,float)


def early_sources(data,roscap,modelerror=0.15):
    allsrc=v.make_sources(data,delta=modelerror,rho=0,exclude='Price1997_figure1',ros_baseline_cap=roscap)
    assert set(allsrc)=={'Rosano1997_PK25','Komesaroff1998_n10'}
    assert all(np.max(s['time'])<=1 for s in allsrc.values())
    return allsrc


def fit_all(data,roscap,family,orders=GRID_N):
    studies=early_sources(data,roscap)
    fits=[]
    for n in orders:
        for si,x0 in enumerate(starts(family)):
            result=minimize(v.score,x0,args=(family,n,studies),bounds=v.bounds_for(family),method='L-BFGS-B',
                options={'maxiter':95,'ftol':1e-9})
            loss,profiles,p=v.score(result.x,family,n,studies,detail=True)
            fits.append({'family':family,'n':n,'seed_index':si,'train_pseudoloss':loss,
                'optimizer_success':bool(result.success),'optimizer_message':str(result.message),
                'parameters':p,'training_profiles':{k:{kk:prof[kk] for kk in ('pseudo_loss','baseline','amplitude_at_1h')} for k,prof in profiles.items()}})
    fits.sort(key=lambda a:(a['train_pseudoloss'],a['n'],a['seed_index']))
    return fits


def price_evaluate(parameter,price,baseline):
    times=np.array(price['hours'],float)
    observed=np.array(price['y'],float)
    err=np.maximum(np.array(price['nominal_se_or_read_width'],float),.15*np.abs(observed))
    assert np.all(np.isfinite(times)) and times[0]==1 and np.all(np.diff(times)>0)
    amplitude=(observed[0]-baseline) # H(1)=1
    assert amplitude>0
    predicted=baseline+amplitude*v.shape(times,parameter)
    residual=predicted-observed
    # 1h point is CALIBRATION, not scored.
    def summary(mask):
        e=residual[mask]; sig=err[mask]
        return {'n':int(np.sum(mask)),'rmse_pg_ml':float(np.sqrt(np.mean(e*e))),
            'mean_signed_error_pg_ml':float(np.mean(e)),
            'standardized_rmse':float(np.sqrt(np.mean((e/sig)**2))),
            'maximum_abs_error_pg_ml':float(np.max(np.abs(e))),
            'mean_abs_error_pg_ml':float(np.mean(np.abs(e)))}
    return {'baseline_hypothesis_pg_ml':baseline,'anchor_time_h':1,'anchor_value_pg_ml':float(observed[0]),
        'anchor_derived_amplitude_pg_ml_at_1h':float(amplitude),'heldout_all':summary(times>1),
        'heldout_2_to_8':summary((times>=2)&(times<=8)),
        'heldout_12_to_24':summary(times>=12),
        'times_h':times.tolist(),'observed_pg_ml':observed.tolist(),
        'predicted_pg_ml':predicted.tolist(), 'manual_read_width_pg_ml':np.array(price['nominal_se_or_read_width'],float).tolist(),
        'residual_pg_ml':residual.tolist()}


def curve_scores(data,cap,fitset):
    price=data['studies']['Price1997_figure1']
    best=fitset[0]
    out={str(int(b)):price_evaluate(best['parameters'],price,b) for b in BASES}
    # Train-loss-only candidate selection. Do not select using Price held-out data.
    return out


def fitted_original_evidence_price(data,p2x):
    # This explicitly LEAKS Price into fitting: reference / warning, not heldout test.
    best=min(p2x['rows'],key=lambda r:r['score'])
    return {'p2x_id':best.get('id'),'source':'P2-X trained on the held-out Price figure; NOT a holdout',
        'fit_hypothesis_price_baseline_pg_ml':best['price_fixed_baseline_pg_ml'],
        'train_pseudoloss':best['score'],
        'price_conditional_reference':price_evaluate(best['parameter'],data['studies']['Price1997_figure1'],best['price_fixed_baseline_pg_ml'])}


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--output',required=True)
    x=parser.parse_args()
    dst=Path(x.output)
    if dst.exists():raise FileExistsError(dst)
    data=json.loads(SOURCE.read_text()); old=json.loads(P2X.read_text())
    result={'phase':'P2-AG','claim':'RETROSPECTIVE_CROSS_SOURCE_TEMPORAL_TRANSPORT_AUDIT_NOT_EXTERNAL_VALIDATION',
        'previously_seen_sources':True,'price_in_training':False,
        'price_1h_used_only_for_heldout_study_amplitude':True,'price_2to24h_held_out':True,
        'rosano_kom_early_fit_max_hour':1,
        'price_figure_points_are_manual_digitalizations_not_raw_subject_measurements':True,
        'score_error_model_assumed':'sigma=max(figure_read_width,0.15*observed); within-group covariance unknown',
        'source':{'early':['Rosano1997_PK25','Komesaroff1998_n10'],
            'evaluation':'Price1997_figure1','baseline_hypotheses':list(BASES),'rosano_baseline_caps_pmol_l':list(CAPS)},
        'families':{},'reference_price_in_sample_leaky':fitted_original_evidence_price(data,old)}
    for cap in CAPS:
        for family in ('M1','M2'):
            key=f'{family}_rosano_cap_{int(cap)}'
            t0=time.monotonic(); fits=fit_all(data,cap,family)
            scores=curve_scores(data,cap,fits)
            result['families'][key]={
                'model':family,'rosano_background_cap_pmol_l':cap,
                'candidate_choice':'Minimum training pseudoloss using only Rosano and Komesaroff; Price never used for parameter selection.',
                'seed_count':len(fits),'training_ranked_fits':fits,
                'selected':fits[0], 'price_1h_calibrated_heldout_scores_by_baseline':scores}
            print(key,'fit_n',len(fits),'best_train',round(fits[0]['train_pseudoloss'],6),
                  'n',fits[0]['n'],'q24?',
                  'Price heldout stdRMSE b24',round(scores['24']['heldout_all']['standardized_rmse'],4),
                  'wall',round(time.monotonic()-t0,2),flush=True)
    dst.write_text(json.dumps(result,indent=2,ensure_ascii=False,allow_nan=False)+'\n')
    print('wrote',dst,flush=True)

if __name__=='__main__':main()
