"""Export research-only model manifests and study-anchored plots. No clinical calibration."""
import json,csv,argparse
from pathlib import Path
import numpy as np
import p2v_continuous_fit as p

def export(outdir):
    outdir=Path(outdir);outdir.mkdir(exist_ok=True,parents=True)
    raw=json.loads((p.ROOT/'docs/pk-research/p2/p2v-continuous-fit-results.json').read_text())
    samples=json.loads(p.DATA.read_text())
    source=p.make_sources(samples)
    expanded={fam:p.optimize(fam,source,fast=True,max_order=16) for fam in ['M0','M1','M2']}
    manifest={'schema_version':1,'phase':'P2-V','scope':'research-only, exploratory retrospective cross-study fit',
      'Doll_data_used':False,'model_parameters_are_not_estimated_individual_population_PK':True,
      'input_data':'docs/pk-research/p2/p2u-observed-aggregates.json',
      'model_form':'C_study(t) = baseline_study + A_study * h(t)/h(1h)',
      'concentration_from_dose_history_requires_explicit_study_amp_per_mg_and_verified_scope':True,
      'independent_clinical_accuracy_proven':False,
      'production_replacement_authorized':False,
      'primary_default_search':{fam:raw['scenarios']['primary']['fit'][fam]['parameters'] for fam in ['M0','M1','M2']},
      'expanded_order_search':{fam:{'score':expanded[fam]['loss'],'parameters':expanded[fam]['parameters'],
                                 'observed_study_anchors':{n:{'baseline':d['baseline'],'amplitude_at_observed_dose_1h':d['amplitude_at_1h'],
                                 'dose_mg':samples['studies'][n]['dose_mg'],'unit':samples['studies'][n]['unit']} for n,d in expanded[fam]['study_profiles'].items()},
                                 'dimensionless_q6_hypothetical_0p5mg_index':expanded[fam]['tail_q6_index']} for fam in ['M0','M1','M2']},
       'price_figure_and_table1_auc_disagreement_unresolved':True,
       'uncertainty_not_derived_from_subject_level_data':True}
    (outdir/'p2v-candidate-parameters.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n')
    steps=sorted(set([0,.05,.1,1/6,.25,1/3,.5,2/3,1,1.5,2,3,4,6,8,10,12,16,18,20,24]+list(np.linspace(0,24,97))))
    with (outdir/'p2v-normalized-curves.csv').open('w',newline='') as f:
        cw=csv.writer(f);cw.writerow(['hours','M0_H_t_over_H_1h','M1_H_t_over_H_1h','M2_H_t_over_H_1h'])
        for t in steps:
            cw.writerow([format(t,'.12g')]+[format(float(p.shape([t],expanded[fam]['parameters'])[0]),'.12g') for fam in ['M0','M1','M2']])
    # Group-level predictions are anchored to EACH study rather than mixed absolute blood levels.
    with (outdir/'p2v-study-reconstruction.csv').open('w',newline='') as f:
        cw=csv.writer(f);cw.writerow(['study','time_h','observed','model','predicted','unit','evidence_type'])
        for fam,v in expanded.items():
            for n,s in source.items():
                fit=v['study_profiles'][n]
                for t,obs,pred in zip(s['time'],s['obs'],fit['predictions']):
                    cw.writerow([n,format(t,'.10g'),format(obs,'.10g'),fam,format(pred,'.12g'),s['unit'],
                                 'group mean' if n!='Price1997_figure1' else 'manually read figure'])
    print('written',outdir)
    for fam,v in expanded.items():print(fam,'loss',round(v['loss'],6),'params',v['parameters'])
    return manifest

def main():
    ap=argparse.ArgumentParser();ap.add_argument('--outdir',required=True);args=ap.parse_args()
    export(args.outdir)
if __name__=='__main__':main()
