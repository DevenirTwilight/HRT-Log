#!/usr/bin/env python3
"""Collect the synthetic-only instrumentation measurement after AGP target cleanup."""
import argparse
import json
import math
from pathlib import Path
import subprocess

PREFIX = 'UNIFIED_DEVICE_PERFORMANCE_JSON='


def parse_report(log: str) -> dict:
    lines = [line[len(PREFIX):] for line in log.splitlines() if line.startswith(PREFIX)]
    if len(lines) != 1:
        raise ValueError('Expected exactly one synthetic device measurement, got ' + str(len(lines)))
    report = json.loads(lines[0])
    if report.get('fixture') != 'synthetic-60-doses-over-30-days':
        raise ValueError('Unexpected fixture')
    for key in ('compute_ms', 'process_pss_before_kib', 'process_pss_after_kib',
                'native_pss_after_kib', 'dalvik_pss_after_kib', 'chart_points', 'sdk'):
        value = report.get(key)
        if isinstance(value, bool) or not isinstance(value, (int, float)) or not math.isfinite(value) or value < 0:
            raise ValueError('Invalid measurement: ' + key)
    if not (0 < report['chart_points'] <= 2001 and report['sdk'] >= 26
            and report['process_pss_before_kib'] > 0 and report['process_pss_after_kib'] > 0):
        raise ValueError('Invalid point count, API or process memory')
    if not isinstance(report.get('abis'), list) or not report['abis'] or not all(isinstance(v, str) and v for v in report['abis']):
        raise ValueError('Missing device ABI array')
    if not isinstance(report.get('measurement_limit'), str) or not report['measurement_limit']:
        raise ValueError('Missing measurement limitations')
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    # Only our dedicated tag, containing a synthetic benchmark: never dump app health logs.
    result = subprocess.run(['adb', 'logcat', '-d', '-v', 'raw', '-s', 'UnifiedDevicePerf:I', '*:S'],
                            check=True, capture_output=True, text=True)
    report = parse_report(result.stdout)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2, allow_nan=False) + '\n')
    print('Validated Android synthetic measurement:', report['chart_points'], 'points,',
          report['compute_ms'], 'ms,', report['process_pss_after_kib'], 'KiB process PSS')


if __name__ == '__main__':
    main()
