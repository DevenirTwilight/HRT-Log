#!/usr/bin/env python3
"""P2-X: nonclinical exploratory multi-study conditional parameter-set audit.

DO NOT use for treatment decisions or claim human calibration/validation.
P2-U pre-exposed Rosano/Komesaroff group summaries and manual Price figure only;
Price study baseline grid is hypothetical and baseline 0 is NOT a measured value.
Every published participant appears at most once per cohort in the loss.
Cortez and Yaish are used only for separate identifiability diagnostics.
No Doll input, no production Android dependencies, no individual patient data.
"""
from __future__ import annotations
import argparse, csv, json, math
from pathlib import Path
import numpy as np
from scipy.optimize import minimize
import p2v_continuous_fit as v

ROOT=Path(__file__).resolve().parents[2]
DATA=ROOT/'docs/pk-research/p2/p2u-observed-aggregates.json'
PRICE_BASELINES=(0.,6.,12.,18.,24.)
ROS_CAPS=(100.,225.)
ORDERS=(4,6,8,10)
CORTEZ={'once':{'dose_per_day_mg':6.2,'baseline_before_therapy_pg_ml':25.8,'six_month_trough_mean_pg_ml':95.3,'reported_plus_minus':10.5},
        'twice':{'dose_per_day_mg':6.2,'baseline_before_therapy_pg_ml':23.4,'six_month_trough_mean_pg_ml':79.4,'reported_plus_minus':11.6}}

def conditional_profile(h,s,fixed_price_baseline=None):
    if fixed_price_baseline is None:return v.profile_study(h,s)
    b=float(fixed_price_baseline)
    if not 0<=b<=24:raise ValueError('price fixed b outside documented sensitivity range')
    y=s['obs'];P=s['precision'];a=max(0.,float((h@P@(y-b))/(h@P@h)))
    pred=b+a*h; r=pred-y
    return {'baseline':b,'amplitude_at_1h':a,'pseudo_loss':float(r@P@r/len(y)),
            'predictions':pred.tolist()}

def conditional_score(x,n,sources,price_baseline,details=False):
    try:
        p=v.unpack(x,'M2',n)
        fit={}
        for key,s in sources.items():
            h=v.shape(s['time'],p)
            fit[key]=conditional_profile(h,s,price_baseline if key=='Price1997_figure1' else None)
        loss=float(np.mean([q['pseudo_loss'] for q in fit.values()]))
        if not math.isfinite(loss):return float('inf')
        return (loss,p,fit) if details else loss
    except (ValueError,ZeroDivisionError,OverflowError,FloatingPointError,np.linalg.LinAlgError):
        return float('inf')

def start_points(n):
    params=[
       (.65,12.,.08,.35), (1.35,15.,.25,.51), (.38,9.,.07,.65),
    ]
    for ke,k,slow,w in params[:2]:
        yield np.array([math.log(ke),math.log(max(.051,k-ke)),math.log(slow),w])

def fit_one(sources,n,price_b):
    winners=[]
    for x0 in start_points(n):
        res=minimize(conditional_score,x0,args=(n,sources,price_b),bounds=v.bounds_for('M2'),
                     method='L-BFGS-B', options={'maxiter':130,'ftol':1e-9})
        val=conditional_score(res.x,n,sources,price_b)
        if math.isfinite(val):winners.append((val,res.x,bool(res.success)))
    if not winners:raise RuntimeError('no finite local optimum')
    loss,x,ok=min(winners,key=lambda r:r[0])
    loss,param,fit=conditional_score(x,n,sources,price_b,True)
    shape=lambda ts: v.shape(np.asarray(ts,float),param)
    t=np.linspace(0,24,1201)
    h=shape(t)
    peak_at=float(t[int(np.argmax(h))]);aucnorm=float(np.trapezoid(h,t))
    tail={str(interval):float(np.sum(shape(interval*np.arange(1,241,dtype=float)))) for interval in (6,12,24)}
    lshape={str(hour):float(shape([hour])[0]) for hour in (0.25,0.5,1,2,4,6,8,12,24)}
    price_amp=fit['Price1997_figure1']['amplitude_at_1h']
    return {
      'rosano_baseline_cap_pmol_l':sources['Rosano1997_PK25']['baseline_cap'],
      'price_fixed_baseline_pg_ml':price_b,'n_fast':n,'score':loss,
      'fit':{k:{j:q[j] for j in ('baseline','amplitude_at_1h','pseudo_loss')} for k,q in fit.items()},
      'parameter':param,'relative_1mg_peak_time_h':peak_at,
      'normalized_increment_auc_0_24_h':aucnorm,
      'trough_index_per_1mg_q6_q12_q24':tail,
      'normalized_curve':lshape,
      'price_model_baseline_subtracted_auc_pg_h_ml':float(price_amp*aucnorm),
      'price_table_auc_reported_pg_h_ml':2109,
      'price_auc_difference_table_minus_model_pg_h_ml':float(2109-price_amp*aucnorm),
      'conditional_gain_ratio_twice_over_once':cortez_required_gain_ratio(tail),
      'optimizer_reported_success':ok,
      'local_multistart_only_not_global':True,
    }

def cortez_required_gain_ratio(trough):
    # Deliberately strong counterfactual assumptions: dose/day = cohort mean;
    # pretreatment baseline persists unchanged; both groups have equal relative bioavailability.
    # Return the effective amplitude ratio REQUIRED by observed differences.
    d1=(CORTEZ['once']['six_month_trough_mean_pg_ml']-CORTEZ['once']['baseline_before_therapy_pg_ml'])
    d2=(CORTEZ['twice']['six_month_trough_mean_pg_ml']-CORTEZ['twice']['baseline_before_therapy_pg_ml'])
    t24=trough['24'];t12=trough['12']
    if t24<=0 or t12<=0:return None
    return float(d2/d1 *2*t24/t12)

def summaries(rows):
    if not rows:raise ValueError('no ensemble')
    overall=min(x['score'] for x in rows)
    kept={}
    for tol in (0.03,0.1,0.3):
        data=[x for x in rows if x['score']<=overall+tol+1e-10]
        def span(key):
            nums=np.array([float(key(x)) for x in data]);
            return {'min':float(np.min(nums)),'median':float(np.median(nums)),'max':float(np.max(nums))}
        kept[f'additive_pseudoloss_{tol:g}']={
            'count':len(data),'min_score':min(x['score'] for x in data),
            'q6':span(lambda x:x['trough_index_per_1mg_q6_q12_q24']['6']),
            'q12':span(lambda x:x['trough_index_per_1mg_q6_q12_q24']['12']),
            'q24':span(lambda x:x['trough_index_per_1mg_q6_q12_q24']['24']),
            'q24_fold_max_min':float(max(x['trough_index_per_1mg_q6_q12_q24']['24'] for x in data)/
                min(x['trough_index_per_1mg_q6_q12_q24']['24'] for x in data)),
            'auc0_24_per_1h':span(lambda x:x['normalized_increment_auc_0_24_h']),
            'peak_time_h':span(lambda x:x['relative_1mg_peak_time_h']),
            'required_cortez_gain_ratio':span(lambda x:x['conditional_gain_ratio_twice_over_once']),
            'source_baseline_grid_values':sorted({x['price_fixed_baseline_pg_ml'] for x in data}),
            'included_orders':sorted({x['n_fast'] for x in data}),
            'interpretation':'Analyst-set pseudo-loss slice of sampled fits, NOT confidence set, CI, posterior, coverage, or exhaustive parameter region.'
        }
    return {'best':min(rows,key=lambda x:x['score']),'slices':kept,'total_fit_count':len(rows),
            'global_best_loss':overall}

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument('--out',required=True); ap.add_argument('--csv',required=True)
    opt=ap.parse_args()
    for p in (opt.out,opt.csv):
        if Path(p).exists():raise FileExistsError('Outputs are immutable; refusing existing path '+p)
    dataset=json.loads(DATA.read_text())
    fits=[]
    for cap in ROS_CAPS:
        sources=v.make_sources(dataset,delta=.15,rho=0,ros_baseline_cap=cap,price_baseline_cap=24)
        for price_b in PRICE_BASELINES:
            for n in ORDERS:
                r=fit_one(sources,n,price_b)
                fits.append(r)
                print(f"cap={cap:g} Price_b={price_b:g} n={n:>2} score={r['score']:.5f} q24={r['trough_index_per_1mg_q6_q12_q24']['24']:.6g}",flush=True)
    summary=summaries(fits)
    result={'phase':'P2-X','date':'2026-10-10','status':'NONCLINICAL_CONDITIONAL_ENSEMBLE',
       'input_studies':['Rosano1997_PK25','Komesaroff1998_n10','Price1997_figure1'],
       'independent_unseen_complete_SL_PK':0,'Doll_used':False,'production_model_untouched':True,
       'cross_study_biological_amplitude_not_shared':True,
       'pseudo_loss_definition':'mean across 3 study-specific generalized squared residuals / number of times, each with manual min(SEorReadWidth, 15% model discrepancy floor) and within-source rho=0 ASSUMED',
       'note_error_floor':'Code uses maximum of nominal uncertainty and 15% of y, not minimum; this prose supersedes typo',
       'baseline_grid_not_empirical_prior':True,
       'no_valid_confidence_intervals_or_clinical_prediction_intervals':True,
       'no_human_clinical_parameters_identified':True,
       'cortez_group_E2_means':CORTEZ,
       'cortez_SD_SE_ambiguity':'Table3 caption mean(SD) versus Fig3 caption mean(SE), confirm with authors before inferential use',
       'yaish_group_mean_vs_median_not_paired':True,
       'fig_and_table_AUC_same_six_participants_not_independent':True,
       'source_urls':{'price':'https://pubmed.ncbi.nlm.nih.gov/9052581/',
              'rosano':'https://doi.org/10.1161/01.cir.96.9.2837',
              'komesaroff':'https://doi.org/10.1210/jcem.83.7.4945',
              'cortez':'https://pmc.ncbi.nlm.nih.gov/articles/PMC11220669/',
              'yaish':'https://pmc.ncbi.nlm.nih.gov/articles/PMC10732161/'},
       'design':{'rosano_baseline_capped_pmol_l':ROS_CAPS,'price_baseline_fixed_pg_ml':PRICE_BASELINES,'n_input_stages':ORDERS,
             'assumed_rho':0,'assumed_model_error_fraction':0.15,
             'local_optimizers_each':2,'normalized_to_h1h':True},
       'summary':summary,'rows':fits}
    Path(opt.out).parent.mkdir(parents=True,exist_ok=True)
    Path(opt.out).write_text(json.dumps(result,indent=2,ensure_ascii=False)+'\n')
    with open(opt.csv,'w',newline='') as o:
        w=csv.writer(o);w.writerow(['Rosano_baseline_cap','Price_baseline','n','pseudo_loss','ke_per_h','k_fast_per_h','k_slow_per_h','slow_effective_weight','q6','q12','q24','AUC_normalized_0_24','t_peak_h','Price_model_auc','required_Cortez_gain_ratio'])
        for a in fits:
            p=a['parameter'];t=a['trough_index_per_1mg_q6_q12_q24']
            w.writerow([a['rosano_baseline_cap_pmol_l'],a['price_fixed_baseline_pg_ml'],a['n_fast'],a['score'],p['k_elim_per_h'],p['k_fast_per_h'],p['k_slow_per_h'],p['effective_slow_weight'],t['6'],t['12'],t['24'],a['normalized_increment_auc_0_24_h'],a['relative_1mg_peak_time_h'],a['price_model_baseline_subtracted_auc_pg_h_ml'],a['conditional_gain_ratio_twice_over_once']])
    print('SUMMARY',json.dumps({k:v for k,v in summary.items() if k!='best'},ensure_ascii=False))
if __name__=='__main__':main()
