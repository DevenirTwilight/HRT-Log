#!/usr/bin/env python3
"""Collect only the synthetic benchmark JSON, never a database, backup, APK or health record."""
import json
from pathlib import Path
import re
import subprocess

def report_paths(lines):
    return [p.strip() for p in lines if re.fullmatch(r'/sdcard/Android/data/net\.plainnotes\.app\.data(?:\.test)?/files/ansm-performance/large-ten-year\.json', p.strip())]


def collect():
    out = Path('core/data/build/reports/ansm-native-performance')
    out.mkdir(parents=True, exist_ok=True)
    paths = subprocess.check_output(['adb', 'shell', 'find', '/sdcard/Android/data', '-path', '*/files/ansm-performance/large-ten-year.json', '-type', 'f'], text=True).splitlines()
    paths = report_paths(paths)
    if len(paths) != 1:
        raise SystemExit('Expected exactly one synthetic performance report; test may have failed before initialization')
    data = subprocess.check_output(['adb', 'exec-out', 'cat', paths[0]])
    report = json.loads(data)
    (out / 'large-ten-year.json').write_bytes(data)
    if report.get('status') != 'DIAGNOSTIC_BUDGET_PASS' or report.get('api26_low_memory_acceptance') != 'EMULATOR_BUDGET_PASS':
        raise SystemExit('Native synthetic performance did not pass all predefined budgets; report preserved')


if __name__ == "__main__":
    collect()
