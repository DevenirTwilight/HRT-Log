"""P2-AO offline metadata-only human-study data gate. Zero personal medical data.

Public registry claims are NOT equivalent to verified raw individual PK observations.
No model-fitting or clinical outcome computation is performed by this module.
"""
from __future__ import annotations
import csv
import json
from collections import Counter, defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CATALOG = ROOT/'data'/'source-catalog.json'
RESULT = ROOT/'results'/'p2ao-assessment.json'
CSV = ROOT/'results'/'p2ao-source-screen.csv'

_ALLOWED_EXPOSURES = {'known_exposed', 'not_listed_in_frozen_seven', 'unknown'}
_ALLOWED_INDEPENDENCE = {'not_independent', 'unresolved', 'verified_independent'}
_ALLOWED_CLAIMS = {'not_verified', 'not_shared_per_registry', 'declared_available_repository'}


def validate_record(s):
    for field in ('id','family','publication','sample_size','timepoints_h','route','formulation','prior_exposure',
                  'ipd_claim','ipd_bytes_verified','source_verified','dose_clock_verified',
                  'participant_time_series_verified','timing_after_dose_dense','independence','overlap_risk'):
        if field not in s:
            raise ValueError('missing mandatory field: '+field)
    if s['prior_exposure'] not in _ALLOWED_EXPOSURES: raise ValueError('unknown prior exposure')
    if s['independence'] not in _ALLOWED_INDEPENDENCE: raise ValueError('unknown independence')
    if s['ipd_claim'] not in _ALLOWED_CLAIMS: raise ValueError('unknown claim')
    if s['sample_size'] is not None and (not isinstance(s['sample_size'],int) or isinstance(s['sample_size'],bool) or s['sample_size']<1):
        raise ValueError('invalid sample size')
    if not s['publication'].startswith('https://'): raise ValueError('source must be https')
    if any(not isinstance(t,(int,float)) or isinstance(t,bool) or t<0 for t in s['timepoints_h']): raise ValueError('bad timepoint')
    if sorted(set(s['timepoints_h']))!=s['timepoints_h']: raise ValueError('duplicate/unsorted timepoints')
    for field in ('ipd_bytes_verified','source_verified','dose_clock_verified',
                  'participant_time_series_verified','timing_after_dose_dense'):
        if not isinstance(s[field],bool): raise ValueError('not bool: '+field)
    if s['ipd_claim']=='declared_available_repository' and not s.get('repository_url','').startswith('https://'):
        raise ValueError('repository claimed but no URL')
    if s['prior_exposure']=='known_exposed' and s['independence']=='verified_independent':
        raise ValueError('contradictory previously exposed independent status')
    return True


def assess(s):
    validate_record(s)
    reasons=[]
    if not s['ipd_bytes_verified']: reasons.append('no_verified_original_dataset_bytes')
    if not s['participant_time_series_verified']: reasons.append('no_verified_subject_level_serum_E2_series')
    if not s['dose_clock_verified']: reasons.append('no_verified_actual_dose_time_history')
    if not s['timing_after_dose_dense']: reasons.append('not_verified_dense_post_dose_series')
    if s['route']!='SL_E2': reasons.append('route_or_formulation_not_directly_comparable')
    if s['prior_exposure']!='not_listed_in_frozen_seven': reasons.append('not_confirmed_new_to_all_model_development')
    # Even a study not listed in seven still needs an explicit exposure audit and external independent sign-off.
    if s['independence']!='verified_independent': reasons.append('independent_cohort_not_verified')
    if not s['source_verified']: reasons.append('source_registration_not_verified')
    if not s['sample_size'] or s['sample_size']<5: reasons.append('sample_size_below_exploratory_minimum_5')
    return {
        'id':s['id'], 'family':s['family'], 'sample_size':s['sample_size'],
        'route':s['route'], 'ipd_claim':s['ipd_claim'],
        'ipd_bytes_verified':s['ipd_bytes_verified'], 'independence':s['independence'],
        'external_score_eligible':not bool(reasons), 'blockers':reasons,
        'small_sample_caution': s['sample_size'] is None or s['sample_size'] < 5,
        'source_url':s['publication'], 'repository_url':s.get('repository_url')
    }


def verify_catalog(c):
    rows=c['records']; ids=[r['id'] for r in rows]
    if len(ids)!=len(set(ids)): raise ValueError('duplicate record id')
    for r in rows: validate_record(r)
    return True


def run(c):
    verify_catalog(c)
    results=[assess(s) for s in c['records']]
    families=defaultdict(list)
    for s in c['records']: families[s['family']].append(s['id'])
    return {
      'project':c['project'], 'audit_date':c['audit_date'],
      'record_count':len(results), 'distinct_research_families_not_proof_of_independence':len(families),
      'exposure_counts':dict(sorted(Counter(s['prior_exposure'] for s in c['records']).items())),
      'declared_repository_count':sum(s['ipd_claim']=='declared_available_repository' for s in c['records']),
      'verified_original_dataset_count':sum(s['ipd_bytes_verified'] for s in c['records']),
      'external_validation_eligible_count':sum(r['external_score_eligible'] for r in results),
      'model_accuracy_claim':'NOT_EVALUATED',
      'candidate_assessments':results,
      'family_members':dict(sorted(families.items())),
      'access_checks':c.get('checks_attempted',[]),
      'guardrails':['DOI_or_registry_IPD_claim_is_not_downloaded_data',
                    'prior_exposure_is_study_family_not_abstract_or_paper',
                    'missing_sampling_clock_is_not_approximated',
                    'intervention_or_formulation_differences_not_automatically_pooled',
                    'no_real_patient_level_records_in_package']
    }


def main():
    catalog=json.loads(CATALOG.read_text(encoding='utf-8'))
    out=run(catalog)
    RESULT.parent.mkdir(parents=True,exist_ok=True)
    RESULT.write_text(json.dumps(out,ensure_ascii=False,sort_keys=True,indent=2)+'\n',encoding='utf-8')
    with CSV.open('w',newline='',encoding='utf-8-sig') as f:
        fields=['id','sample_size','route','ipd_claim','ipd_bytes_verified','independence',
                'external_score_eligible','blockers','source_url','repository_url']
        w=csv.DictWriter(f,fieldnames=fields);w.writeheader()
        for row in out['candidate_assessments']:
            flat={**row,'blockers':';'.join(row['blockers'])}
            w.writerow({k:flat.get(k) for k in fields})
    print(json.dumps({k:v for k,v in out.items() if k.endswith('count') or k=='record_count'},indent=2))

if __name__=='__main__':main()
