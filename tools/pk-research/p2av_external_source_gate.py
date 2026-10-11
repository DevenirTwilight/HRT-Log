"""P2-AV non-personal, source-metadata-only PK validation gate.

Never ingest individual health rows. Study follow-up months are not post-dose hours.
A published sampling schedule does not mean serial concentrations were retrieved.
"""
from __future__ import annotations

import argparse
import json
from collections import defaultdict
from pathlib import Path

EXPECTED_SCHEMA = 1
ROUTE_SL = 'SL_SEPARATE'


def audit(records: list[dict]) -> dict:
    """Audit publication records; return only public metadata classifications.

    This is an evidence-classification audit, not a pharmacokinetic fit.
    No candidate models and no personal observations are loaded.
    """
    if not isinstance(records, list) or not records:
        raise ValueError('nonempty records required')
    seen_ids = set()
    cohorts = defaultdict(list)
    rows = []
    for row in records:
        needed = ('id', 'cohort_id', 'publication_url', 'route_resolution', 'analyte',
                  'evidence_kind', 'usable_postdose_times_hours', 'sampling_plan_hours',
                  'sample_clock_verified', 'assay_verified', 'exposed_to_development',
                  'independence_verified', 'predose_baseline_available', 'dose_history_verified',
                  'patient_time_series_available', 'data_reuse_authorized', 'study_followup_months',
                  'holdout_protocol_frozen_before_data_access', 'absolute_scale_precommitted')
        if not isinstance(row, dict) or any(k not in row for k in needed):
            raise ValueError('missing record metadata')
        rid = row['id']
        if not isinstance(rid, str) or not rid or rid in seen_ids:
            raise ValueError('empty or duplicate publication ID')
        seen_ids.add(rid)
        cohort = row['cohort_id']
        if not isinstance(cohort, str) or not cohort:
            raise ValueError('missing canonical cohort')
        if not isinstance(row['publication_url'], str) or not row['publication_url'].startswith('https://'):
            raise ValueError('source citation missing HTTPS')
        for key in ('sample_clock_verified', 'assay_verified', 'exposed_to_development',
                    'independence_verified', 'predose_baseline_available',
                    'dose_history_verified', 'patient_time_series_available',
                    'data_reuse_authorized', 'holdout_protocol_frozen_before_data_access',
                    'absolute_scale_precommitted'):
            if type(row[key]) is not bool:
                raise ValueError('boolean evidence gate not established: ' + key)
        planned = row['sampling_plan_hours']
        usable = row['usable_postdose_times_hours']
        if not isinstance(planned, list) or not isinstance(usable, list):
            raise ValueError('sample clocks must be lists')
        for seq in (planned, usable):
            if any(type(t) not in (float, int) or t < 0 or t > 168 for t in seq):
                raise ValueError('sample clock invalid')
            if seq != sorted(set(seq)):
                raise ValueError('sample clocks must be sorted and unique')
        if any(t not in planned for t in usable):
            raise ValueError('reported usable measurements must have declared clock provenance')
        if any(type(m) is not int or m <= 0 for m in row['study_followup_months']):
            raise ValueError('visit follow-up months invalid')
        # Actual accessible concentration values, not a protocol timetable.
        postdose = [t for t in usable if t > 0]
        separate_sl = row['route_resolution'] == ROUTE_SL and row['analyte'] == 'E2'
        baseline = row['predose_baseline_available']
        trace_eligible = bool(separate_sl and len(postdose) >= 3 and baseline
                              and row['sample_clock_verified'] and row['assay_verified']
                              and row['dose_history_verified'] and row['data_reuse_authorized'])
        late_eligible = bool(trace_eligible and any(t >= 8 for t in postdose))
        # Locked external validation requires the holdout to have been sealed before
        # any model selection/use; never infer 'unseen' from an independent NCT ID.
        external_eligible = bool(trace_eligible and row['independence_verified']
                                 and not row['exposed_to_development']
                                 and row['holdout_protocol_frozen_before_data_access'])
        absolute_eligible = bool(external_eligible and row['patient_time_series_available']
                                 and row['absolute_scale_precommitted'])
        blockers = []
        if not separate_sl: blockers.append('ROUTE_OR_ANALYTE_NOT_SEPARATE_SL_E2')
        if len(postdose) < 3: blockers.append('LESS_THAN_3_ACCESSIBLE_POSTDOSE_TIMES')
        if not baseline: blockers.append('PRE_DOSE_BASELINE_NOT_AVAILABLE')
        if not row['sample_clock_verified']: blockers.append('ACTUAL_SAMPLE_CLOCK_UNVERIFIED')
        if not row['assay_verified']: blockers.append('E2_ASSAY_UNVERIFIED')
        if not row['dose_history_verified']: blockers.append('DOSE_HISTORY_UNVERIFIED')
        if not row['data_reuse_authorized']: blockers.append('REUSE_AUTHORIZATION_UNVERIFIED')
        if row['exposed_to_development']: blockers.append('PREVIOUSLY_EXPOSED_TO_MODEL_DEVELOPMENT')
        if not row['independence_verified']: blockers.append('COHORT_DISJOINTNESS_UNVERIFIED')
        if not row['patient_time_series_available']: blockers.append('NO_ACCESSIBLE_INDIVIDUAL_TIME_SERIES')
        if not row['holdout_protocol_frozen_before_data_access']:
            blockers.append('BLIND_PROTOCOL_NOT_PRECOMMITTED')
        if not row['absolute_scale_precommitted']:
            blockers.append('ABSOLUTE_E2_SCALE_NOT_PRECOMMITTED')
        result = {
            'publication_id': rid, 'canonical_cohort_id': cohort,
            'reported_followup_months_not_dose_hours': row['study_followup_months'],
            'planned_postdose_times_h': [t for t in planned if t > 0],
            'actually_available_postdose_times_h': postdose,
            'serial_shape_evaluation_eligible': trace_eligible,
            'long_tail_evaluation_eligible': late_eligible,
            'locked_external_shape_evaluation_eligible': external_eligible,
            'individual_absolute_concentration_evaluation_eligible': absolute_eligible,
            'blockers': blockers,
        }
        rows.append(result)
        cohorts[cohort].append(rid)
    return {
        'schema_version': EXPECTED_SCHEMA,
        'phase': 'P2-AV',
        'scope': 'PUBLIC_METADATA_ONLY_NO_PARTICIPANT_VALUES',
        'publication_records': len(rows),
        'unique_canonical_cohorts': len(cohorts),
        'canonical_cohorts': [{'cohort_id': k, 'publication_ids': sorted(v)}
                              for k, v in sorted(cohorts.items())],
        'source_rows': sorted(rows, key=lambda x: x['publication_id']),
        'serial_shape_eligible_count': sum(x['serial_shape_evaluation_eligible'] for x in rows),
        'long_tail_eligible_count': sum(x['long_tail_evaluation_eligible'] for x in rows),
        'locked_external_eligible_count': sum(x['locked_external_shape_evaluation_eligible'] for x in rows),
        'individual_absolute_eligible_count': sum(x['individual_absolute_concentration_evaluation_eligible'] for x in rows),
        'production_model_replacement_authorized': False,
        'new_independent_human_PK_profiles_obtained': 0,
        'claim': 'metadata eligibility only; absence of accessible curves is not evidence of biological model failure',
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument('--registry', type=Path, required=True)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    if args.out.exists():
        parser.error('refuses to overwrite archived output')
    registry = json.loads(args.registry.read_text(encoding='utf-8'))
    if registry.get('schema_version') != EXPECTED_SCHEMA:
        parser.error('unsupported registry schema')
    summary = audit(registry['publications'])
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(summary, ensure_ascii=False, sort_keys=True, indent=2)+'\n', encoding='utf-8')
    print(f"{summary['publication_records']} publications -> {summary['unique_canonical_cohorts']} cohorts; locked external eligible={summary['locked_external_eligible_count']}")


if __name__ == '__main__':
    main()
