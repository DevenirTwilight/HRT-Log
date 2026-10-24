"""Retrospective Price 1-4h training, 6-24h blind-to-optimizer evaluation.
Already-seen Price study in project design: NOT a genuinely external evaluation.
Baseline and amplitude from early Figure points MUST be retained for late eval.
"""
import argparse,json,math
from pathlib import Path
import numpy as np
import p2v_continuous_fit as p

def run(dataset):
    studies=p.make_sources(dataset,price_points='early')
    full=p.make_sources(dataset)
    late=p.make_sources(dataset,price_points='late')['Price1997_figure1']
    # Exclude 4h from the test: train 1,2,3,4 and test 6,8,12,18,24
    outputs={}
    for fam in ['M0','M1','M2']:
        fit=p.optimize(fam,studies,fast=True)
        old=fit['study_profiles']['Price1997_figure1']
        param=fit['parameters']
        t=late['time']; mask=t>4.
        prediction=old['baseline']+old['amplitude_at_1h']*p.shape(t[mask],param)
        observed=late['obs'][mask];errors=late['error'][mask]
        # HOLDOUT scoring uses diagonal assumed uncertainties, no re-fit.
        score=float(np.mean(((prediction-observed)/errors)**2))
        # Explain how much the optimistic error is underestimated by re-fitting late nuisance.
        profiled=p.profile_study(p.shape(t[mask],param),{
            'obs':observed,'precision':np.diag(1/errors**2),'baseline_cap':24.})
        outputs[fam]={'trained_on':['Rosano: all','Komesaroff: all','Price Figure manual: 1,2,3,4h'],
             'evaluated_at_h':[float(x) for x in t[mask]],
             'source_previously_seen_NOT_BLIND':True,
             'training_pseudo_loss_equal_source':fit['loss'],
             'late_no_refit_pseudo_loss':score,
             'late_if_reprofited_invalid_optimistic_pseudo_loss':profiled['pseudo_loss'],
             'early_fixed_Price_nuisance':{'baseline':old['baseline'],'amplitude':old['amplitude_at_1h']},
             'late_predictions_pg_ml':[float(x) for x in prediction],
             'late_manual_observations_pg_ml':[float(x) for x in observed],
             'parameters':param}
    return {'stage':'P2-V','scope':'retrospective time-block stress, not external validation',
            'Price_late_points_held_out_of_fitting':True,'late_recalibration_prohibited':True,
            'results':outputs}

def main():
    ap=argparse.ArgumentParser();ap.add_argument('--out',required=True);a=ap.parse_args()
    data=json.loads(p.DATA.read_text());r=run(data)
    dst=Path(a.out)
    if dst.exists():raise FileExistsError(dst)
    dst.write_text(json.dumps(r,indent=2,ensure_ascii=False)+'\n')
    for k,v in r['results'].items():print(k,round(v['training_pseudo_loss_equal_source'],3),round(v['late_no_refit_pseudo_loss'],3),flush=True)
if __name__=='__main__':main()
