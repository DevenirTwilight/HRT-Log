#!/usr/bin/env python3
"""P2-AG diagnostics; consumes a frozen, early-source-only retraining result."""
import argparse,csv,json,math,sys
from pathlib import Path
import numpy as np
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
sys.path.insert(0,str(Path(__file__).resolve().parent))
import p2v_continuous_fit as v
ROOT=Path(__file__).resolve().parents[1]

def summarize(blob):
    summary={'phase':'P2-AG','qualifier':'Previously exposed and analyst-digitized sources; hypothesis and anchor conditioned, not blind clinical validation.',
      'training_n_timepoints':7,'n_training_study_specific_nuisance':4,
      'M1_n_shared_shape_parameters':2,'M2_n_shared_shape_parameters':4,
      'train_vs_validation_time_overlap':'Early studies 0..1h; Price has 1..24h and is used at 1h only for amplitude calibration.',
      'comparison':{},'anchor_interval_checks':{},'leaky_in_sample_reference':{}}
    for key,obj in blob['families'].items():
        a=obj['selected'];s=obj['price_1h_calibrated_heldout_scores_by_baseline'];
        summary['comparison'][key]={
            'train_pseudoloss':a['train_pseudoloss'],'n_fast':a['n'],'train_opt_optimizations':len(obj['training_ranked_fits']),
            'heldout_by_price_baseline':{b:{'standardized_rmse':q['heldout_all']['standardized_rmse'],
                  'raw_rmse_pg_ml':q['heldout_all']['rmse_pg_ml'],
                  'early_rmse_pg_ml':q['heldout_2_to_8']['rmse_pg_ml'],
                  'late_rmse_pg_ml':q['heldout_12_to_24']['rmse_pg_ml'],
                  't2_prediction_pg_ml':q['predicted_pg_ml'][1],
                  't24_prediction_pg_ml':q['predicted_pg_ml'][-1]} for b,q in s.items()},
            'at_bound_ke':abs(a['parameters']['k_elim_per_h']-1.6)<1e-4,
            'min_score_is_not_external_accuracy':True
        }
    price=blob['families']['M2_rosano_cap_100']['price_1h_calibrated_heldout_scores_by_baseline']['24']
    t2=float(price['predicted_pg_ml'][1]);y=float(price['observed_pg_ml'][1]);
    h2=(t2-24)/(450-24)
    assert abs(h2-v.shape(np.array([2]),blob['families']['M2_rosano_cap_100']['selected']['parameters'])[0])<1e-10
    # ±15 at 1h and b∈[0,24]. This is *manual-read* sensitivity, not a confidence interval.
    lows=[b+(435-b)*h2 for b in (0,24)]
    highs=[b+(465-b)*h2 for b in (0,24)]
    summary['anchor_interval_checks']={'hypothetical_1h_figure_read_range':[435.,465.],
       'hypothetical_price_baseline_range':[0.,24.],
       'm2_predicted_price_t2_range_pg_ml':[float(min(lows)),float(max(highs))],
       'manual_price_t2_read_range':[225.-32.5,225.+32.5],
       'manual_range_disjoint':float(max(highs))<225.-32.5,
       'manual_figure_ranges_are_not_statistical_confidence_bands':True}
    old=blob['reference_price_in_sample_leaky']['price_conditional_reference']
    summary['leaky_in_sample_reference']={'standardized_rmse':old['heldout_all']['standardized_rmse'],
       'raw_rmse_pg_ml':old['heldout_all']['rmse_pg_ml'],
       'not_external_validation':True}
    return summary

def plots(result):
    figdir=ROOT/'figures';figdir.mkdir(exist_ok=True)
    data=result['families']
    sample=data['M2_rosano_cap_100']['price_1h_calibrated_heldout_scores_by_baseline']['24']
    fig,ax=plt.subplots(figsize=(9,5.5))
    t=np.array(sample['times_h'])
    y=np.array(sample['observed_pg_ml'])
    er=np.array(sample['manual_read_width_pg_ml'])
    ax.errorbar(t,y,yerr=er,fmt='o',capsize=3,label='Price figure manual read (NOT assay SE)')
    for key,nice in [('M1_rosano_cap_100','Early-only M1, Rosano cap=100'),('M2_rosano_cap_100','Early-only M2, Rosano cap=100'),('M1_rosano_cap_225','Early-only M1, Rosano cap=225'),('M2_rosano_cap_225','Early-only M2, Rosano cap=225')]:
        if key=='M2_rosano_cap_225':continue  # overlapping predicted line; identical to cap100 here
        pred=data[key]['price_1h_calibrated_heldout_scores_by_baseline']['24']['predicted_pg_ml']
        ax.plot(t,pred,marker='.',label=nice)
    leaky=result['reference_price_in_sample_leaky']['price_conditional_reference']
    ax.plot(t,leaky['predicted_pg_ml'],ls=':',label='P2-X leaky in-sample fit (comparison only)')
    ax.axvline(1,linestyle='--',alpha=.5)
    ax.set_xlabel('Hours after nominal 1 mg dose')
    ax.set_ylabel('Conditional study scenario E2 (pg/mL)')
    ax.set_title('P2-AG: Price 2–24h is not used in early-source fitting\nPrice 1h is a study-specific amplitude anchor; b=24 pg/mL hypothetical')
    ax.legend(fontsize=8);ax.grid(alpha=.2);fig.tight_layout();fig.savefig(figdir/'p2ag-price-transport-b24.png',dpi=160);plt.close(fig)

    fig,ax=plt.subplots(figsize=(8.5,5.2))
    for key in ['M1_rosano_cap_100','M2_rosano_cap_100','M1_rosano_cap_225','M2_rosano_cap_225']:
        score=data[key]['price_1h_calibrated_heldout_scores_by_baseline']
        ax.plot([0,6,12,18,24],[score[str(b)]['heldout_all']['standardized_rmse'] for b in [0,6,12,18,24]],'o-',label=key.replace('_rosano_cap_', ' / Rosano cap '))
    ax.set(xlabel='ASSUMED Price background (pg/mL)',ylabel='Price held-out standardized RMSE (dimensionless)',title='P2-AG: study background assumption alters the hold-out error')
    ax.grid(alpha=.2);ax.legend(fontsize=8);fig.tight_layout();fig.savefig(figdir/'p2ag-price-baseline-sensitivity.png',dpi=160);plt.close(fig)

    fig,ax=plt.subplots(figsize=(8.3,4.0))
    spans={'Komesaroff1998_n10':[0,.25,.5], 'Rosano1997_PK25':[1/6,1/3,2/3,1], 'Price1997_figure1':[1,2,3,4,6,8,12,18,24]}
    for idx,(key,ts) in enumerate(spans.items()):
        ax.scatter(ts,[idx]*len(ts),s=65,label=key)
    ax.axvspan(1,24,alpha=.09)
    ax.axvline(1,ls='--',alpha=.6)
    ax.set_yticks(list(range(3)),list(spans));ax.set(xlabel='Actual sampled times (h; Price times from digitized figure)',title='P2-AG: only Price informs post-1h observed source domain',xlim=(-.25,25))
    ax.grid(axis='x',alpha=.18);fig.tight_layout();fig.savefig(figdir/'p2ag-time-support-matrix.png',dpi=160);plt.close(fig)

def table(blob):
    out=ROOT/'data/p2ag-conditional-holdout-metrics.csv'
    with out.open('w',newline='') as f:
        w=csv.writer(f);w.writerow(['family_rosano_cap','price_assumed_b_pg_ml','early_train_pseudoloss','n_fast','Price_anchor_t1_pg_ml','heldout_n_timepoints','heldout_RMSE_pg_ml','heldout_standardized_RMSE','heldout_early2to8_RMSE_pg_ml','heldout_late12to24_RMSE_pg_ml','pred_t2_pg_ml','pred_t24_pg_ml'])
        for key,obj in blob['families'].items():
            for b,s in obj['price_1h_calibrated_heldout_scores_by_baseline'].items():
                w.writerow([key,b,obj['selected']['train_pseudoloss'],obj['selected']['n'],450,s['heldout_all']['n'],s['heldout_all']['rmse_pg_ml'],s['heldout_all']['standardized_rmse'],s['heldout_2_to_8']['rmse_pg_ml'],s['heldout_12_to_24']['rmse_pg_ml'],s['predicted_pg_ml'][1],s['predicted_pg_ml'][-1]])
    return out

if __name__=='__main__':
    opt=argparse.ArgumentParser();opt.add_argument('--input',default=str(ROOT/'data/p2ag-cross-source.json'));args=opt.parse_args()
    x=json.load(open(args.input));summary=summarize(x);sp=ROOT/'data/p2ag-compact-summary.json';sp.write_text(json.dumps(summary,ensure_ascii=False,indent=2,allow_nan=False)+'\n');table(x);plots(x)
    print('Wrote summary, 20 comparison cases, 3 figures. Sharp gap?',summary['anchor_interval_checks']['manual_range_disjoint'])
