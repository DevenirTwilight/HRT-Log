#!/usr/bin/env python3
"""Summarize actually emitted synthetic integration reports and JUnit XML; never infer missing PASS."""
import argparse
import hashlib
import json
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]


def report():
    result = {'scope': 'TEST_ONLY synthetic inputs; production integration PRODUCTION_BLOCKED',
              'physical_device': 'NOT_RUN', 'production_implemented': False, 'released': False,
              'test_runs': [], 'synthetic_events': [], 'synthetic_reports': []}
    for module, task in [('core/domain', 'test'), ('core/data', 'testDebugUnitTest'), ('core/reminder', 'testDebugUnitTest')]:
        suites = []
        for path in sorted((ROOT / module / 'build/test-results' / task).glob('TEST-*.xml')):
            s = ET.parse(path).getroot()
            suites.append({'name': s.get('name'), 'timestamp': s.get('timestamp'),
                           **{k: int(s.get(k, 0)) for k in ('tests', 'failures', 'errors', 'skipped')}})
        result['test_runs'].append({'module': module, 'status': 'NOT_RUN' if not suites else 'FAIL' if any(s['failures'] or s['errors'] for s in suites) else 'PASS', 'suites': suites})
    for path in sorted((ROOT / 'core/data/build/reports/ansm03b0').glob('*.json')):
        value = json.loads(path.read_text())
        result['synthetic_reports'].append({'name': path.name, 'sha256': hashlib.sha256(path.read_bytes()).hexdigest(),
                                            'size': path.stat().st_size})
        for event in value.get('events', []):
            result['synthetic_events'].append({'report': path.name, 'event': event['label'],
                'records': len(event['tables']['dose_record']), 'ledger_rows': len(event['tables']['supply_transaction']),
                'containers': event['tables']['supply_container'], 'forecast': event['forecast']})
    result['limits'] = ['XML presence identifies a reported run, not future-source or CI success; pair with command completion and source hashes.',
                        'Legacy REAL values remain binary64 observations, original decimal precision UNKNOWN.',
                        'Candidate SQL is not a Room schema, production serializer, full new-backup roundtrip or forecast date algorithm.']
    return result


if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--output', type=Path, required=True)
    args = p.parse_args()
    args.output.write_text(json.dumps(report(), ensure_ascii=False, indent=2) + '\n')
