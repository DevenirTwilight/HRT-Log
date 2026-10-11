"""P2-AU: deterministic clock-grid robustness; real medical rows stay private.

Uses frozen Legacy/M2 single-dose shape kernels imported from P2-AR-REAL.
Data may be read only from private, exact-hash VNC54 file; public output is aggregate.
This is a *counterfactual schedule grid*, neither a scheduling prior nor a PK validation.
"""
from __future__ import annotations
import argparse
import hashlib
import itertools
import json
from pathlib import Path
from statistics import median
from typing import Any

from p2ar_real_visit_audit import (EXPECTED_VNC54_XLSX_SHA256,
    legacy_normalized, m2_normalized, response_pre_post,
    subject_visits_from_grid)

GAP_CHOICES = (3, 4, 5, 6)  # strictly predeclared, no patient clock inference
POST_HOURS = 1.5


def clock_grid() -> tuple[tuple[int,int,int,int], ...]:
    # First daily dose at t=0; strictly increasing gaps, 6--15 h night interval.
    return tuple((0, a, a+b, a+b+c) for a,b,c in itertools.product(GAP_CHOICES, repeat=3))


def score_thresholds(people: dict, frozen: dict, legacy: dict) -> dict:
    visits=[(sid,month,pre/post) for sid,rows in people.items() for month,pre,post in rows]
    if len(people)!=11 or len(visits)!=20 or any(not (0 < r < 1) for _,_,r in visits):
        raise ValueError('Source cohort shape or sign unexpected')
    p2x=[r['parameter'] for r in frozen['rows']
         if r['score']<=frozen['summary']['global_best_loss']+0.10+1e-10]
    if len(p2x)!=15:
        raise ValueError('P2-X frozen candidate gate altered')
    shapes=[('Legacy',None)]+[(f'M2_{i+1:02d}',p) for i,p in enumerate(p2x)]
    allmodels=[]
    clocks=clock_grid()
    for label,p in shapes:
        shape=(lambda t: legacy_normalized(t,legacy)) if p is None else (lambda t,p=p:m2_normalized(t,p))
        thresholds=[]
        visitcounts=[]
        subjectcounts=[]
        for clock in clocks:
            a,b=response_pre_post(shape,clock,days=60,post_hours=POST_HOURS)
            ratio=a/b
            thresholds.append(ratio)
            visitcounts.append(sum(r+1e-12>=ratio for _,_,r in visits))
            subjectcounts.append(sum(all(pre/post+1e-12>=ratio for mo,pre,post in rows)
                                     for rows in people.values()))
        # A common, unknown whole-cohort schedule is varied; no best-clock tuning.
        # 'Per-visit existential' is explicitly a mathematically weaker thought experiment.
        allmodels.append({'model':label,
            'compatible_visits_min':min(visitcounts),
            'compatible_visits_median':median(visitcounts),
            'compatible_visits_max':max(visitcounts),
            'all20_common_clock_count':sum(v==20 for v in visitcounts),
            'compatible_subjects_min':min(subjectcounts),
            'compatible_subjects_max':max(subjectcounts),
            'strict_all64_clock_compatible_visits':sum(r+1e-12>=max(thresholds) for _,_,r in visits),
            'per_visit_any_clock_compatible_visits':sum(r+1e-12>=min(thresholds) for _,_,r in visits),
            'threshold_min':min(thresholds),
            'threshold_max':max(thresholds),
            })
    legacy_line=allmodels[0];m2=allmodels[1:]
    return {
        'schema_version':1,'phase':'P2-AU','study_type':'real_data_retrospective_clock_design_sensitivity',
        'n_distinct_people':len(people),'n_pair_visits':len(visits),
        'clock_grid':'all triples of 3,4,5,6 hour within-day gaps, first dose at t=0',
        'clock_grid_size':len(clocks),'night_gap_range_hours':[6,15],
        'post_nominal_h':POST_HOURS,'repeated_dosing_days_assumed':60,
        'schedule_weighting_or_patient_prior':False,
        'all_schedules_are_counterfactual_not_observed':True,
        'source_dataset_previously_seen_in_model_development':True,
        'model_names':['Legacy','15 frozen P2-X M2'],
        'legacy_clock_summary':legacy_line,
        'm2_per_candidate_clock_summaries':m2,
        'm2_class_envelope':{
            'per_candidate_all20_common_clock_counts':sorted(x['all20_common_clock_count'] for x in m2),
            'any_candidate_any_common_clock_all20':any(x['all20_common_clock_count']>0 for x in m2),
            'models_at_least_one_all20_common_clock':sum(x['all20_common_clock_count']>0 for x in m2),
            'min_compatible_visits_over_all_candidates_all_clocks':min(x['compatible_visits_min'] for x in m2),
            'max_compatible_visits_over_all_candidates_all_clocks':max(x['compatible_visits_max'] for x in m2),
            'strict_all64_clock_compatible_visits_range':[
                min(x['strict_all64_clock_compatible_visits'] for x in m2),
                max(x['strict_all64_clock_compatible_visits'] for x in m2)],
            'per_visit_any_clock_compatible_visits_range':[
                min(x['per_visit_any_clock_compatible_visits'] for x in m2),
                max(x['per_visit_any_clock_compatible_visits'] for x in m2)],
            'median_compatible_visits_across_clock_grid_range':[
                min(x['compatible_visits_median'] for x in m2),
                max(x['compatible_visits_median'] for x in m2)]
        },
        'legacy_vs_m2_accuracy_inferred':False,
        'personal_pg_ml_eligible':False,'external_human_validation':False,
        'model_replacement_authorized':False,
        'privacy':'only group counts and model thresholds exported; no person or visit values',
    }


def run_privately(vnc54: Path,p2x_file:Path,legacy_file:Path,out:Path)->dict:
    if out.exists():raise ValueError('refuse output overwrite')
    source_hash=hashlib.sha256(vnc54.read_bytes()).hexdigest()
    if source_hash!=EXPECTED_VNC54_XLSX_SHA256:raise ValueError('private data hash changed')
    from artifact_tool import Blob,SpreadsheetFile
    wb=SpreadsheetFile.import_xlsx(Blob.load(str(vnc54)))
    data=wb.worksheets.get_item('Data').get_range('A1:DX23').values
    people=subject_visits_from_grid(data)
    result=score_thresholds(people,json.loads(p2x_file.read_text()),json.loads(legacy_file.read_text()))
    result['private_source_sha256']=source_hash
    out.parent.mkdir(parents=True,exist_ok=True)
    out.write_text(json.dumps(result,indent=2,sort_keys=True,ensure_ascii=False)+'\n')
    return result

if __name__=='__main__':
    p=argparse.ArgumentParser()
    p.add_argument('--private-vnc54',type=Path,required=True)
    p.add_argument('--p2x-json',type=Path,required=True)
    p.add_argument('--legacy-json',type=Path,required=True)
    p.add_argument('--out-aggregate',type=Path,required=True)
    a=p.parse_args()
    run_privately(a.private_vnc54,a.p2x_json,a.legacy_json,a.out_aggregate)
