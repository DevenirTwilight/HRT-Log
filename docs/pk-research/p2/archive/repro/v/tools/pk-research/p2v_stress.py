"""Optional continuous-fit order-boundary and leave-one-study-out stress tests.
No new blinded sources. Refits nuisance baseline/amplitude on held-out-known-study.
"""
import argparse
import json
from pathlib import Path
import p2v_continuous_fit as p

def study_diagnostic(dataset, excluded, family, max_order=8):
    training=p.make_sources(dataset,exclude=excluded)
    fitted=p.optimize(family,training,fast=True,max_order=max_order)
    omitted=p.make_sources(dataset)[excluded]
    hold=p.profile_study(p.shape(omitted['time'],fitted['parameters']),omitted)
    return {'training_shared_shape_score':fitted['loss'],
            'profiled_existing_omitted_cohort_score_NOT_BLINDED':hold['pseudo_loss'],
            'model':fitted['parameters'],
            'heldout_baseline_and_amplitude_REFIT':True}

def main():
    ap=argparse.ArgumentParser();ap.add_argument('--out',required=True);a=ap.parse_args()
    data=json.loads(p.DATA.read_text())
    primary=p.make_sources(data)
    boundary={fam:{'stage_cap_8':p.optimize(fam,primary,fast=True,max_order=8),
                   'stage_cap_12':p.optimize(fam,primary,fast=True,max_order=12)} for fam in ('M1','M2')}
    leave={name:{fam:study_diagnostic(data,name,fam) for fam in ('M0','M1','M2')}
           for name in p.SOURCE_ORDER}
    result={'research':'P2-V secondary','model_fit_exploratory_only':True,
            'not_blind_validation':True,'boundary':boundary,
            'leave_known_study_out_profiled':leave}
    dst=Path(a.out)
    if dst.exists():raise FileExistsError('No overwrite')
    dst.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
    for fam,row in boundary.items():
        for k,r in row.items():
            print('boundary',fam,k,r['loss'],r['parameters']['n_fast'],flush=True)
    for name,rs in leave.items():
        print('omit',name,[(f,round(r['profiled_existing_omitted_cohort_score_NOT_BLINDED'],4)) for f,r in rs.items()],flush=True)
if __name__=='__main__':main()
