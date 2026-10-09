"""P2-E: source-matched PK model AUC and early-shape audit, not production refitting.

Only published aggregate measurements; no personal records, no new clinical fit.
"""
import argparse
import json
import math
from pathlib import Path
import models as m

ROOT=Path(__file__).resolve().parents[2]
PRICE_GRID=(0,1,2,3,4,6,8,12,18,24)

def frozen_models(params, settings, catalog):
    k=settings['candidate_A']['canonical_k']
    unit_a=m.Gamma(1,k)
    a=m.Gamma(144/unit_a(1),k)
    fast,slow,clearance,share=settings['candidate_B']['canonical']
    unit_b=m.Dual(1,fast,slow,clearance,share)
    b=m.Dual(144/unit_b(1),fast,slow,clearance,share)
    return {'HRT_current':lambda t:m.current(t,params),
            'Candidate_A':a,
            'Candidate_B':b,
            'Featherline_80kg':lambda t:m.feather(t,catalog['comparison_model'],80)}

def sampled_auc(kernel,grid=PRICE_GRID):
    return math.fsum((t2-t1)*(kernel(t1)+kernel(t2))/2
                     for t1,t2 in zip(grid,grid[1:]))

def analyze(evidence,params,settings,catalog):
    price=evidence['Price1997']
    ros=evidence['Rosano1997']
    assert evidence['locked_external'] is False
    assert price['n']==6 and price['subtracted_pretreatment_baseline']
    assert price['sampling_hours']==list(PRICE_GRID)
    assert ros['n_PK']==25 and ros['baseline_mean_pmol_l'] is None
    times={x['minutes']:x for x in ros['points']}
    contrast=times[40]['mean_pmol_l']-2*times[20]['mean_pmol_l']
    sd_upper=times[40]['sd_pmol_l']+2*times[20]['sd_pmol_l']
    se_upper=sd_upper/math.sqrt(ros['n_PK'])
    result={'evidence_previously_seen':True,'external_validation_status':'INSUFFICIENT',
            'clinical_accuracy_established':False,'P2C_replacement_authorized':False,
            'price_sampling_hours':list(PRICE_GRID),'price_source_auc_is_baseline_subtracted_trapezoid':True,
            'price_cmax_is_mean_of_individual_sample_maxima':True,
            'rosano_n25_is_separate_from_n9_plus_n7_cardiac':True,
            'rosano_baseline_unknown_not_borrowed':True,
            'rosano_source_sd_and_covariance_sensitive':{
              'observed_raw_C40_over_C20':times[40]['mean_pmol_l']/times[20]['mean_pmol_l'],
              'mean_C40_minus_2_C20_pmol_l':contrast,
              'max_paired_SD_from_triangle_inequality_pmol_l':sd_upper,
              'max_standard_error_of_mean_difference_pmol_l':se_upper,
              'mean_difference_over_max_SE':contrast/se_upper,
              'only_if_same_25_participants_and_timepoint_SD_are_correct':True,
              'formal_confirmatory_inference_claimed':False},
            'models':{}}
    for name,fn in frozen_models(params,settings,catalog).items():
        sampled=sampled_auc(fn)
        continuous=m.simpson(fn,24,4000)
        r40=fn(40/60)/fn(20/60)
        assert 0<r40<2
        result['models'][name]={
            'sampled_auc0_24_per_1mg_pg_h_ml':sampled,
            'continuous_auc0_24_per_1mg_pg_h_ml':continuous,
            'predicted_increment_ratio_40_over_20':r40,
            'dose_specific_conditional_comparisons':[
              {'dose_mg':x['dose_mg'],'reported_AUC_mean_pg_h_ml':x['auc0_24_mean_pg_h_ml'],
               'reported_AUC_sd_pg_h_ml':x['auc0_24_sd_pg_h_ml'],
               'predicted_AUC_on_price_grid_pg_h_ml':x['dose_mg']*sampled,
               'ratio_predicted_over_observed_mean':x['dose_mg']*sampled/x['auc0_24_mean_pg_h_ml']}
              for x in price['sublingual_E2']]}
    return result

def main():
    ap=argparse.ArgumentParser(description=__doc__)
    ap.add_argument('--out',required=True)
    args=ap.parse_args()
    e=json.loads((ROOT/'docs/pk-research/p2/p2e-source-metrics.json').read_text())
    p=json.loads((ROOT/'pk-engine/src/main/resources/pk-params.json').read_text())
    s=json.loads((ROOT/'docs/pk-research/p2/experiment-settings.json').read_text())
    c=json.loads((ROOT/'docs/pk-research/p2/evidence-catalog.json').read_text())
    output=analyze(e,p,s,c)
    path=Path(args.out)
    if path.exists():
        raise ValueError('Refusing to overwrite research output')
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(output,sort_keys=True,ensure_ascii=False,indent=2)+'\n')
    print('Research-only; no human external validation:',path)

if __name__=='__main__':
    main()
