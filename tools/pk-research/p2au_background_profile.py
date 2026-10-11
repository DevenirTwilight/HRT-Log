"""P2-AU: conditional nonidentifiability and background-budget sensitivity.

Real clinical rows are read only in a PRIVATE, authorized environment.
Only cohort aggregate statistics are written. This is neither patient prediction
nor independent model validation. Requires prior frozen P2-AR mathematics.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import math
from pathlib import Path
from statistics import median
from typing import Any

from p2ar_real_visit_audit import (
    EXPECTED_VNC54_XLSX_SHA256, SCHEDULES,
    legacy_normalized, m2_normalized, response_pre_post, subject_visits_from_grid,
)

BUDGETS = (0.0, 0.25, 0.50, 0.75, 1.0)
EXPECTED_P2X_SHA256 = '7bb08b9e5b397e766cd63018567542520105755c9e578a8cdf4204a6df8d5367'
EXPECTED_LEGACY_SHA256 = '70d95c7e38222520b09c0d7a740ccf5c92d94775e3aea589cc157daff1ebd2e2'


def required_background_share(pre: float, post: float, model_pre: float, model_post: float) -> float | None:
    """Solve Cpre=b+G*Spre, Cpost=b+G*Spost for alpha=b/Cpre.

    Returns None if a nonnegative background + positive gain cannot fit.
    0<=alpha<=1 denotes observational algebra only, NOT physiological b.
    """
    vals=(pre,post,model_pre,model_post)
    if not all(math.isfinite(v) for v in vals):raise ValueError('Nonfinite data')
    if pre<=0 or post<=pre or model_pre<0 or model_post<=model_pre:
        raise ValueError('Expected positive observed and response increments')
    q=pre/post
    r=model_pre/model_post
    if q+1e-12<r:return None
    alpha=(q-r)/(q*(1-r))
    if alpha < -1e-9 or alpha > 1+1e-9:raise ValueError('Unexpected numerical background share')
    return max(0.,min(1.,alpha))


def compatible(alpha: float | None, budget: float) -> bool:
    if not (0<=budget<=1):raise ValueError('Budget outside [0,1]')
    return alpha is not None and alpha<=budget+1e-12


def scenario_model_counts(people:dict[Any,list[tuple[int,float,float]]],
                          p2x:dict[str,Any], legacy:dict[str,Any]) -> dict[str,Any]:
    candidates=[x for x in p2x['rows'] if x['score']<=p2x['summary']['global_best_loss']+.10+1e-10]
    if len(candidates)!=15:raise ValueError('Expected exactly 15 frozen candidates')
    if len(people)!=11 or sum(len(x) for x in people.values())!=20:raise ValueError('VNC54 roster changed')
    if sum(month==3 for vv in people.values() for month,_,_ in vv)!=9:
        raise ValueError('3-month paired visits changed')
    if sum(month==6 for vv in people.values() for month,_,_ in vv)!=11:
        raise ValueError('6-month paired visits changed')

    # These private ratios exist only in memory: do not return, write or print.
    data=[(sid,month,pre,post) for sid,vv in people.items() for month,pre,post in vv]
    if any(not 0<pre<post for _,_,pre,post in data):raise ValueError('Unexpected measured sign')
    labels=list(SCHEDULES)
    names=['Legacy']+[f'M2_{i+1:02d}' for i in range(15)]  # ephemeral, never published per-candidate
    functions=[lambda t:legacy_normalized(t,legacy)] + [
      (lambda t,p=row['parameter']:m2_normalized(t,p)) for row in candidates]
    per_model={name:{} for name in names}
    for name,fn in zip(names,functions):
        for key,clock in SCHEDULES.items():
            spre,spost=response_pre_post(fn,clock)
            alpha=[required_background_share(pre,post,spre,spost) for _,_,pre,post in data]
            per_model[name][key]=alpha

    output={}
    for label in labels:
        d={'assumed_times_h':list(SCHEDULES[label]),'required_background_upper_bounds':{}}
        for b in BUDGETS:
            legacy_count=sum(compatible(a,b) for a in per_model['Legacy'][label])
            m2_counts=[sum(compatible(a,b) for a in per_model[name][label]) for name in names[1:]]
            budget_label=f'{b:.2f}'
            d['required_background_upper_bounds'][budget_label]={
                'legacy_compatible_visits':legacy_count,
                'm2_compatible_visits_min':min(m2_counts),
                'm2_compatible_visits_median':median(m2_counts),
                'm2_compatible_visits_max':max(m2_counts),
                'm2_candidates_compatible_with_all_20':sum(n==20 for n in m2_counts),
            }
        output[label]=d

    envelope={}
    for b in BUDGETS:
        def stats(name):
            arrays=[[compatible(a,b) for a in per_model[name][lab]] for lab in labels]
            # One global timing scenario for all cohort visits vs a (more permissive)
            # per-visit timing choice are strictly different assumptions.
            counts=[sum(v) for v in arrays]
            visit_any=sum(any(arr[i] for arr in arrays) for i in range(20))
            visit_all=sum(all(arr[i] for arr in arrays) for i in range(20))
            return min(counts),max(counts),visit_any,visit_all
        old=stats('Legacy')
        new=[stats(n) for n in names[1:]]
        envelope[f'{b:.2f}']={
            'legacy_min_max_across_one_global_clock':[old[0],old[1]],
            'legacy_visitwise_any_clock':old[2],
            'legacy_visitwise_every_clock':old[3],
            'm2_min_count_of_global_clock_max':min(x[1] for x in new),
            'm2_max_count_of_global_clock_max':max(x[1] for x in new),
            'm2_candidates_with_at_least_one_global_clock_fitting_20':sum(x[1]==20 for x in new),
            'm2_candidates_with_all_five_clocks_fitting_20':sum(x[0]==20 for x in new),
            'm2_visitwise_any_clock_count_min':min(x[2] for x in new),
            'm2_visitwise_any_clock_count_max':max(x[2] for x in new),
            'm2_visitwise_every_clock_count_min':min(x[3] for x in new),
            'm2_visitwise_every_clock_count_max':max(x[3] for x in new),
        }
    # No subject id, month-specific measurements, 20-element patterns, or
    # per-candidate results. In particular, not even model-level membership.
    return {
      'schema_version':1,'phase':'P2-AU','date':'2026-10-11',
      'status':'RETROSPECTIVE_EXPOSED_DATA_SENSITIVITY_NOT_EXTERNAL_VALIDATION',
      'paired_unique_people':11,'paired_visits':20,
      'frozen_m2_candidate_count':15,
      'background_budget_is_assumption_not_measured_endogenous_fraction':True,
      'dosing_clocks_are_illustrative_not_actual':True,
      'visitwise_clock_envelope_is_unphysical_optimistic_bound':True,
      'model_calibration_or_patient_pg_ml_validation':False,
      'independent_human_external_pk_validation':False,
      'production_replacement_authorized':False,
      'null_carryover_control':{
         'Spre':0,'Spost':1,
         'background_fraction_required_for_every_positive_observation':1,
         'compatible_visits_if_background_budget_1':20,
         'compatible_visits_if_background_budget_below_1':0,
         'interpretation':'Trivial mathematical control; NOT proposed pharmacokinetic model',
      },
      'scenarios':output,'cross_scenario_envelopes':envelope,
    }


def run_private(vnc54:Path,p2x:Path,legacy:Path,out:Path)->None:
    for path,expect in ((vnc54,EXPECTED_VNC54_XLSX_SHA256),(p2x,EXPECTED_P2X_SHA256),
                        (legacy,EXPECTED_LEGACY_SHA256)):
        if hashlib.sha256(path.read_bytes()).hexdigest()!=expect:
            raise ValueError(f'Frozen source hash mismatch: {path.name}')
    if out.exists():raise ValueError('Refusing to overwrite pre-existing result')
    from artifact_tool import Blob, SpreadsheetFile
    workbook=SpreadsheetFile.import_xlsx(Blob.load(str(vnc54)))
    rows=workbook.worksheets.get_item('Data').get_range('A1:DX23').values
    people=subject_visits_from_grid(rows)
    result=scenario_model_counts(people,json.loads(p2x.read_text()),json.loads(legacy.read_text()))
    result['source_vnc54_xlsx_sha256']=EXPECTED_VNC54_XLSX_SHA256
    result['frozen_p2x_json_sha256']=EXPECTED_P2X_SHA256
    result['frozen_legacy_snapshot_sha256']=EXPECTED_LEGACY_SHA256
    out.parent.mkdir(parents=True,exist_ok=True)
    out.write_text(json.dumps(result,ensure_ascii=False,sort_keys=True,indent=2)+'\n',encoding='utf-8')
    print('P2-AU PRIVATE_REPLAY_SUCCEEDED; public cohort-only aggregate written')


def main()->None:
    p=argparse.ArgumentParser()
    p.add_argument('--vnc54-xlsx',required=True,type=Path)
    p.add_argument('--p2x-frozen-json',required=True,type=Path)
    p.add_argument('--legacy-snapshot-json',required=True,type=Path)
    p.add_argument('--out-aggregate',required=True,type=Path)
    args=p.parse_args()
    run_private(args.vnc54_xlsx,args.p2x_frozen_json,args.legacy_snapshot_json,args.out_aggregate)


if __name__=='__main__':main()
