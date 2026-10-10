#!/usr/bin/env python3
"""P2-T evidence provenance audit. No data downloads; never authorizes a model or release."""
import argparse
import json
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
LEDGER = ROOT / 'docs/pk-research/p2/p2t-acquisition-ledger.json'


def audit(ledger):
    sources = ledger['sources']
    ids = [s['id'] for s in sources]
    if len(ids) != len(set(ids)):
        raise ValueError('duplicate report id')
    for s in sources:
        if not s.get('cohort_id') or not s.get('sources'):
            raise ValueError('missing cohort provenance')
        times = s.get('timepoints_min', [])
        if times != sorted(set(times)) or any(t < 0 for t in times):
            raise ValueError('non-unique / unsorted / negative times')
        if s.get('actual_pure_sl_subject_series_retrieved') and s.get('source_role', '').startswith(('EXPOSED_', 'UNAVAILABLE_', 'NOT_PK_')):
            # A valid acquired exposed training dataset is allowed, but never a fresh holdout.
            pass
    by_cohort = defaultdict(list)
    for s in sources:
        by_cohort[s['cohort_id']].append(s['id'])
    time_windows = []
    for s in sources:
        ts = s.get('timepoints_min', [])
        has_predose = 0 in ts
        has_early = any(0 < t < 60 for t in ts)
        has_mid = any(60 <= t <= 240 for t in ts)
        has_late = any(t >= 720 for t in ts)
        time_windows.append({
            'id': s['id'], 'cohort_id': s['cohort_id'],
            'published_or_documented_times_min': ts,
            'has_0min_sampling': has_predose,
            'has_postdose_early_under_60min': has_early,
            'has_1_to_4h_samples': has_mid,
            'has_sample_at_or_after_12h': has_late,
            'same_report_early_and_late': has_early and has_late,
            'subject_series_retrieved': s['actual_pure_sl_subject_series_retrieved'],
            'usable_unseen_external_holdout': False
        })
    if any(s['actual_pure_sl_subject_series_retrieved'] for s in sources):
        raise ValueError('P2-T as-of ledger must be revised before declaring obtained IPD')
    return {
        'phase': 'P2-T', 'as_of': ledger['as_of'],
        'reports': len(sources), 'distinct_named_cohort_ids': len(by_cohort),
        'multi_report_cohorts': {k: v for k, v in sorted(by_cohort.items()) if len(v) > 1},
        'registered_ipd_contact_leads': [s['id'] for s in sources if s.get('ipd_sharing_statement')],
        'osf_file_lists_confirmed': [s['id'] for s in sources if s.get('osf_file_list_verified')],
        'early_and_late_same_report_count': sum(x['same_report_early_and_late'] for x in time_windows),
        'retrieved_qualified_pure_sl_subject_series': 0,
        'locked_unseen_external_full_sl_pk_cohorts': 0,
        'human_90pct_interval_coverage_validated': False,
        'model_accuracy_established': False,
        'model_upgrade_authorized': False,
        'report_time_coverage': time_windows
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', required=True)
    args = parser.parse_args()
    d = json.loads(LEDGER.read_text(encoding='utf-8'))
    result = audit(d)
    target = Path(args.out)
    if target.exists():
        raise ValueError('Refusing to overwrite research output')
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(result, indent=2, ensure_ascii=False, sort_keys=True) + '\n', encoding='utf-8')
    print(f'P2-T source metadata audited (not clinical validation): {target}')

if __name__ == '__main__':
    main()
