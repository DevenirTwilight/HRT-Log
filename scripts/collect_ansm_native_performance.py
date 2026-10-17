#!/usr/bin/env python3
"""Collect only the synthetic benchmark JSON, never a database, backup, APK or health record."""
import json
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
    if not budget_pass(report):
        raise SystemExit('Native synthetic performance did not pass all predefined budgets; report preserved')


if __name__ == "__main__":
    collect()
