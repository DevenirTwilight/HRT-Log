#!/usr/bin/env python3
"""Each real skip scenario owns a fresh synthetic installation and treatment history."""
import shutil
import subprocess
import xml.etree.ElementTree as ET
from run_med02_product_native import ROOT, REPORT, reset

CLASS = 'net.plainnotes.app.ExplicitSkippedProductAndroidTest'
CASES = ('fiveDaysTwiceDailyHaveOneSummaryAndReopenWithoutTenRows',
         'skipInCalendarBelongsToTreatmentAndCanBeUndoneAfterReopen')

def main():
    assert subprocess.check_output(['adb', 'shell', 'getprop', 'ro.kernel.qemu'], text=True).strip() == '1', 'Synthetic emulator only'
    REPORT.mkdir(parents=True, exist_ok=True)
    for name in CASES:
        reset(f'before-{name}')
        result = subprocess.run(['./gradlew', ':app:connectedFullDebugAndroidTest',
            f'-Pandroid.testInstrumentationRunnerArguments.class={CLASS}#{name}',
            '--no-parallel', '--max-workers=2'], cwd=ROOT)
        target = REPORT / 'explicit-skip' / name
        for source, label in [('outputs/androidTest-results', 'results'), ('reports/androidTests', 'html')]:
            path = ROOT / 'app/build' / source
            if path.exists():
                shutil.copytree(path, target / label, dirs_exist_ok=True)
        result.check_returncode()
        cases = [case for path in (target / 'results').rglob('TEST-*.xml')
                 for case in ET.parse(path).getroot().iter('testcase') if case.get('classname') == CLASS]
        assert len(cases) == 1 and cases[0].get('name') == name
        assert all(not list(cases[0].iter(tag)) for tag in ('failure', 'error', 'skipped'))
        reset(f'after-{name}')

if __name__ == '__main__':
    main()
