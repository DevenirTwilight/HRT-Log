#!/usr/bin/env python3
"""Collect synthetic timing metadata/method stacks, never a database, backup, APK or health record."""
import json
import hashlib
from pathlib import Path
import re
import subprocess

def report_paths(lines):
    return [p.strip() for p in lines if re.fullmatch(r'/sdcard/Android/data/net\.plainnotes\.app\.data(?:\.test)?/files/ansm-performance/large-ten-year\.json', p.strip())]


def log_report(text):
    reports = []
    for line in text.splitlines():
        match = re.search(r'\bI\s+ANSM_PERF\s*: (\{.*\})$', line)
        if match:
            try:
                value = json.loads(match.group(1))
            except json.JSONDecodeError:
                continue  # A split/truncated log line is not a valid report.
            if value.get('environment') == 'Native API26 SQLCipher synthetic fixture; emulator, not physical hardware':
                reports.append(value)
    if not reports:
        raise ValueError('No complete synthetic metadata in crash log')
    return reports[-1]


def budget_pass(report):
    return report.get('status') == 'DIAGNOSTIC_BUDGET_PASS' and report.get('api26_low_memory_acceptance') == 'EMULATOR_BUDGET_PASS'


def trace_path(report_path):
    if report_paths([report_path]) != [report_path]:
        raise ValueError('Not a synthetic report path')
    return report_path.rsplit('/', 1)[0] + '/audit-sampling.trace'


def collect_trace(report_path, out, execute=subprocess.check_output):
    # Only a fresh API26 emulator is allowed; never inspect arbitrary device files.
    serial = execute(['adb', 'get-serialno'], text=True).strip()
    qemu = execute(['adb', 'shell', 'getprop', 'ro.kernel.qemu'], text=True).strip()
    sdk = execute(['adb', 'shell', 'getprop', 'ro.build.version.sdk'], text=True).strip()
    if not re.fullmatch(r'emulator-[0-9]+', serial) or qemu != '1' or sdk != '26':
        raise ValueError('Trace collection requires the isolated API26 emulator')
    path = trace_path(report_path)
    words = execute(['adb', 'shell', 'wc', '-c', path], text=True).split()
    if not words:
        raise ValueError('Missing trace size')
    size = words[0]
    from summarize_ansm_method_trace import MAX_TRACE_BYTES, summarize
    if not re.fullmatch(r'[0-9]{1,9}', size) or not 18 <= int(size) <= MAX_TRACE_BYTES:
        raise ValueError('Trace size outside diagnostic bounds')
    data = execute(['adb', 'exec-out', 'head', '-c', str(int(size) + 1), path])
    if len(data) != int(size):
        raise ValueError('Incomplete trace transfer')
    (out / 'audit-sampling.trace').write_bytes(data)
    summary = summarize(data)
    (out / 'audit-sampling-summary.json').write_text(json.dumps(summary, indent=2) + '\n')
    return {'status': summary['status'], 'bytes': len(data), 'sha256': hashlib.sha256(data).hexdigest()}


def collect():
    out = Path('core/data/build/reports/ansm-native-performance')
    out.mkdir(parents=True, exist_ok=True)
    paths = subprocess.check_output(['adb', 'shell', 'find', '/sdcard/Android/data', '-path', '*/files/ansm-performance/large-ten-year.json', '-type', 'f'], text=True).splitlines()
    paths = report_paths(paths)
    if len(paths) > 1:
        raise SystemExit('Ambiguous synthetic reports; refusing to choose one')
    if paths:
        data = subprocess.check_output(['adb', 'exec-out', 'cat', paths[0]])
        report = json.loads(data)
    else:
        try:
            report = log_report((out / 'synthetic-crash-log.txt').read_text())
        except (OSError, ValueError) as error:
            raise SystemExit('Synthetic report unavailable in external files and logcat') from error
        data = (json.dumps(report, indent=2) + '\n').encode()
    (out / 'large-ten-year.json').write_bytes(data)
    # Method stacks contain code names/times only, from the synthetic post-budget phase.
    trace = {'status': 'UNAVAILABLE', 'reason': 'Synthetic external-files report missing after APK cleanup'}
    if paths:
        try:
            trace = collect_trace(paths[0], out)
        except (OSError, ValueError, subprocess.CalledProcessError) as error:
            trace = {'status': 'UNAVAILABLE', 'reason': type(error).__name__}
    (out / 'trace-collection.json').write_text(json.dumps(trace, indent=2) + '\n')
    if not budget_pass(report):
        raise SystemExit('Native synthetic performance did not pass all predefined budgets; report preserved')


if __name__ == "__main__":
    collect()
