"""P2-AR: retrospective, privacy-preserving conditional audit of VNC54 E2.

Inputs are authorized locally held VNC54 original xlsx and frozen, previously
exposed P2-X / P2-AS *nonpersonal* JSON. No individual visit lines are written.
Analytical conditional feasibility is not a patient PK accuracy score.
"""
from __future__ import annotations

import argparse
from collections import defaultdict
import hashlib
import json
import math
from pathlib import Path
from statistics import median
from typing import Any

EXPECTED_VNC54_XLSX_SHA256 = '038a935f699ddadc9f0d843e125e5ee8047132773d1d6485085d6302066867dd'
SCHEDULES = {
    'every_6h': (0, 6, 12, 18),
    'night_gap_9h': (0, 5, 10, 15),
    'night_gap_10h': (0, 4, 9, 14),
    'night_gap_12h': (0, 4, 8, 12),
    'night_gap_14h': (0, 3, 6, 10),
}


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def lower_gamma_integer(n: int, z: float) -> float:
    """Regularized lower incomplete gamma P(n,z), integer n >= 1, stdlib.

    The series for z<25 avoids subtraction of close numbers at early times.
    The complementary Poisson sum is stable for large z.
    """
    if n < 1 or z < 0 or not math.isfinite(z):
        raise ValueError('n >= 1 and finite z >= 0 required')
    if z == 0: return 0.0
    if z >= 25:
        term = 1.0
        total = term
        for k in range(1, n):
            term *= z/k
            total += term
        return max(0.0, min(1.0, 1.0-math.exp(-z)*total))
    term = 1.0
    total = term
    for j in range(1, 400):
        term *= z/(n+j)
        total += term
        if term < 1e-15*total: break
    return max(0.0, min(1.0, math.exp(n*math.log(z)-z-math.lgamma(n+1))*total))


def m2_normalized(t: float, parameter: dict[str, Any]) -> float:
    if t < 0: return 0.0
    n = int(parameter['n_fast'])
    k = float(parameter['k_fast_per_h'])
    ke = float(parameter['k_elim_per_h'])
    ks = float(parameter['k_slow_per_h'])
    w = float(parameter['effective_slow_weight'])
    if not (0 < ke < k and ks > 0 and 0 <= w <= 1):
        raise ValueError('invalid frozen candidate')

    def unscaled(x: float) -> float:
        fast = math.exp(n*math.log(k/(k-ke))-ke*x)*lower_gamma_integer(n, (k-ke)*x)
        gap = abs(ks-ke)
        slow = ks*x*math.exp(-ke*x) if gap < 1e-9*max(ks,ke) else (ks*math.exp(-min(ks,ke)*x)*(-math.expm1(-gap*x))/gap)
        return (1-w)*fast + w*slow
    return unscaled(t)/unscaled(1.0)


def legacy_normalized(t: float, parameters: dict[str, Any]) -> float:
    if t < 0:return 0.0
    sl, oral = parameters['E2_SL'],parameters['E2_ORAL']
    share = float(sl['swallowed_share'])
    def unscaled(x: float) -> float:
        def component(m: dict[str, Any]) -> float:
            return sum(float(term['A_per_mg'])*(math.exp(-float(term['lambda_per_h'])*x)-math.exp(-float(m['ka_per_h'])*x)) for term in m['terms'])
        return component(sl)+share*component(oral)
    return unscaled(t)/unscaled(1.0)


def response_pre_post(shape, doses: tuple[int, int, int, int], days: int = 60, post_hours: float = 1.5) -> tuple[float, float]:
    if len(doses) != 4 or doses[0] != 0 or sorted(doses) != list(doses) or doses[-1] >= 24:
        raise ValueError('four ordered within-day doses expected')
    if days < 1 or days > 365: raise ValueError('days out of research bound')
    if not 0 < post_hours <= 3: raise ValueError('postdose sampling time outside scenario domain')
    history = [24*day-hour for day in range(1,days+1) for hour in doses]
    before = sum(shape(t) for t in history)
    after = shape(post_hours)+sum(shape(t+post_hours) for t in history)
    if not (0 <= before < after):raise ValueError('nonpositive 90-minute model increment')
    return before,after


def visit_profile(pre: float, post: float, S_pre: float, S_post: float) -> dict[str, float | bool]:
    """Two measurements / two nuisance parameters b,G, with no measurement noise."""
    if not all(math.isfinite(v) for v in (pre,post,S_pre,S_post)) or pre <= 0 or post <= pre or S_pre < 0 or S_post <= S_pre:
        raise ValueError('requires positive observed/model increment')
    gain=(post-pre)/(S_post-S_pre)
    b=pre-gain*S_pre
    # A tiny negative float due to rounding is not a material failed condition.
    feasible = pre/post + 1e-12 >= S_pre/S_post
    return {'gain':gain,'background':b,'nonnegative_background_feasible':feasible}


def subject_visits_from_grid(grid: list[list[Any]]) -> dict[Any, list[tuple[int,float,float]]]:
    """Keep participant keys transient, and never return them from public audit()."""
    if len(grid) != 23 or len(grid[0]) < 122:
        raise ValueError('unexpected VNC54 worksheet shape')
    header=grid[0]
    expected={0:'Pt #',2:'Tx',36:'Tx',75:'3-E2 ',76:'3-E2 post',78:'Tx',120:'6-E2 ',121:'6-post E2'}
    if any(header[i]!=name for i,name in expected.items()):
        raise ValueError('unrecognized VNC54 columns')
    people=defaultdict(list)
    row_keys=set()
    for row in grid[1:]:
        if len(row)<122:raise ValueError('short VNC54 row')
        sid=row[0]
        if sid in row_keys or sid is None:raise ValueError('duplicate or missing row key')
        row_keys.add(sid)
        groups=[str(row[c]) for c in (2,36,78)]
        if len(set(groups))!=1:raise ValueError('inconsistent duplicated treatment group')
        if groups[0] not in ('1','1.0'):continue
        for month,precol,postcol in ((3,75,76),(6,120,121)):
            pre,post=row[precol],row[postcol]
            if (isinstance(pre,(int,float)) and not isinstance(pre,bool) and math.isfinite(pre)
                and isinstance(post,(int,float)) and not isinstance(post,bool) and math.isfinite(post) and pre>0 and post>0):
                people[sid].append((month,float(pre),float(post)))
    return people


def analyze(people: dict[Any,list[tuple[int,float,float]]], p2x: dict[str,Any], legacy: dict[str,Any], source_sha: str) -> dict[str,Any]:
    models=[r for r in p2x['rows'] if r['score']<=p2x['summary']['global_best_loss']+.10+1e-10]
    if len(models)!=15:raise ValueError('Frozen P2-X 15-candidate selection changed')
    if len(people)!=11 or sum(len(v) for v in people.values())!=20:
        raise ValueError('Expected VNC54 paired-visit roster changed; examine privately')
    if sum(sum(month==3 for month,_,_ in visits) for visits in people.values())!=9:
        raise ValueError('3-month pairing changed')
    if sum(sum(month==6 for month,_,_ in visits) for visits in people.values())!=11:
        raise ValueError('6-month pairing changed')
    if any(not(post>pre) for visits in people.values() for month,pre,post in visits):
        raise ValueError('Positive-increment analysis cannot include nonincreasing visit')

    # Cohort-level-only counts: never persist sid, pre, post, ratio or patient-level predictions.
    visits=[(sid,mo,pre,post) for sid,vv in people.items() for mo,pre,post in vv]
    output={}
    for label,doses in SCHEDULES.items():
        records=[]
        for row in models:
            Spre,Spost=response_pre_post(lambda t,r=row:m2_normalized(t,r['parameter']),doses)
            threshold=Spre/Spost
            compat={(sid,month) for sid,month,pre,post in visits if pre/post+1e-12>=threshold}
            num_both=sum(len(vv)==2 and all((sid,mo) in compat for mo,_,_ in vv) for sid,vv in people.items())
            full_subject_count=sum(all((sid,mo) in compat for mo,_,_ in vv) for sid,vv in people.items())
            records.append((len(compat),full_subject_count,num_both,threshold))
        S0,S1=response_pre_post(lambda t:legacy_normalized(t,legacy),doses)
        legacy_threshold=S0/S1
        legacy_n=sum(pre/post+1e-12>=legacy_threshold for _,_,pre,post in visits)
        # Explicitly nonstatistical allowable measurement perturbation scenarios.
        sensitivity={}
        for eps in (0.1,0.2):
            allowed=[]
            for _,_,_,threshold in records:
                allowed.append(sum(pre*(1+eps)/(post*(1-eps))+1e-12>=threshold for _,_,pre,post in visits))
            sensitivity[str(eps)]={'models_all_20_compatible_if_each_pre_post_can_shift_independently':sum(n==20 for n in allowed), 'max_compatible_visits':max(allowed)}
        # Sensitivity uses alternative 75/105-minute clocks; not measured timing error.
        sampling_sensitivity={}
        for post_hours in (1.25,1.75):
            across=[]
            for row in models:
                S0,S1=response_pre_post(lambda t,r=row:m2_normalized(t,r['parameter']),doses,post_hours=post_hours)
                ratio=S0/S1
                across.append(sum(pre/post+1e-12>=ratio for _,_,pre,post in visits))
            L0,L1=response_pre_post(lambda t:legacy_normalized(t,legacy),doses,post_hours=post_hours)
            sampling_sensitivity[str(post_hours)]={
                'm2_candidates_all_20_visits_compatible':sum(n==20 for n in across),
                'm2_compatible_visits_range':[min(across),max(across)],
                'legacy_compatible_visits':sum(pre/post+1e-12>=L0/L1 for _,_,pre,post in visits),
            }
        output[label]={
            'dose_times_hours_hypothetical':list(doses),
            'hypothetical_postdose_sampling_time_sensitivity':sampling_sensitivity,
            'm2_frozen_candidate_count':len(records),
            'm2_compatible_visits_min':min(x[0] for x in records),
            'm2_compatible_visits_median':median(x[0] for x in records),
            'm2_compatible_visits_max':max(x[0] for x in records),
            'm2_candidates_compatible_with_all_20_visits':sum(x[0]==20 for x in records),
            'm2_candidates_compatible_with_all_11_people_observed_visits':sum(x[1]==11 for x in records),
            'm2_model_pre_to_post_response_ratio_min':min(x[3] for x in records),
            'm2_model_pre_to_post_response_ratio_median':median(x[3] for x in records),
            'm2_model_pre_to_post_response_ratio_max':max(x[3] for x in records),
            'legacy_compatible_visits':int(legacy_n),
            'legacy_response_ratio':legacy_threshold,
            'purely_hypothetical_error_perturbations':sensitivity,
        }
    return {
        'schema_version':1,'phase':'P2-AR-REAL','date':'2026-10-11',
        'status':'PRIVATE_REAL_DATA_AUDITED__ONLY_AGGREGATES_PUBLIC',
        'source_vnc54_xlsx_sha256':source_sha,
        'paired_unique_people':len(people),
        'paired_visits':sum(len(x) for x in people.values()),
        'paired_visits_month_3':9,'paired_visits_month_6':11,
        'people_with_both_paired_visits':sum(len(v)==2 for v in people.values()),
        'positive_post_minus_predose_visits':20,
        'time_post_nominal_h':1.5,
        'model_candidates_frozen_before_this_audit':len(models),
        'studies_previously_exposed_to_m2_development':True,
        'schedules_are_not_patient_dose_history':True,
        'no_experimental_assay_uncertainty_quantified':True,
        'error_perturbations_are_analyst_assumptions_not_confidence_intervals':True,
        'absolute_pg_ml_prediction_eligible':False,
        'independent_external_clinical_validation':False,
        'model_production_replacement_authorized':False,
        'no_patient_rows_or_identifiers_exported':True,
        'hypothetical_schedule_results':output,
    }


def main() -> None:
    p=argparse.ArgumentParser(description='P2-AR private VNC54 paired E2 conditional audit')
    p.add_argument('--vnc54-xlsx',type=Path,required=True,help='Private authorized original workbook, never committed')
    p.add_argument('--p2x-frozen-json',type=Path,required=True)
    p.add_argument('--legacy-snapshot-json',type=Path,required=True)
    p.add_argument('--out-aggregate',type=Path,required=True)
    args=p.parse_args()
    if args.out_aggregate.exists():p.error('Refusing to overwrite an existing aggregate output')
    src_hash=sha256(args.vnc54_xlsx)
    if src_hash!=EXPECTED_VNC54_XLSX_SHA256:p.error('VNC54 source hash mismatch; reject an unknown version')
    # Only load participant data in memory in a private, authorized local environment.
    from artifact_tool import Blob, SpreadsheetFile
    workbook=SpreadsheetFile.import_xlsx(Blob.load(str(args.vnc54_xlsx)))
    sheet=workbook.worksheets.get_item('Data')
    rows=sheet.get_range('A1:DX23').values
    people=subject_visits_from_grid(rows)
    p2x=json.loads(args.p2x_frozen_json.read_text())
    legacy=json.loads(args.legacy_snapshot_json.read_text())
    result=analyze(people,p2x,legacy,src_hash)
    args.out_aggregate.parent.mkdir(parents=True,exist_ok=True)
    args.out_aggregate.write_text(json.dumps(result,indent=2,sort_keys=True,ensure_ascii=False)+'\n',encoding='utf-8')
    print('P2-AR audit PASS; private rows not written; aggregate:',args.out_aggregate)


if __name__=='__main__':main()
