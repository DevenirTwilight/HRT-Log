#!/usr/bin/env python3
"""Isolated emulator installation: REAL MainActivity/SAF/scoped production paths, synthetic data only."""
from pathlib import Path
import shutil
import subprocess
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent
REPORT = ROOT / 'app/build/reports/med02-product-native'
CLASS = 'net.plainnotes.app.attachments.MedicalAttachmentProductAndroidTest'


def reset(label):
    for package in ('net.plainnotes.app.test', 'net.plainnotes.app'):
        result = subprocess.run(['adb', 'uninstall', package], capture_output=True, text=True)
        (REPORT / f'{label}-{package}.log').write_text(result.stdout + result.stderr)
        listed = subprocess.check_output(['adb', 'shell', 'pm', 'list', 'packages', package], text=True).splitlines()
        assert f'package:{package}' not in listed, 'Isolated emulator package reset failed'


def main():
    assert subprocess.check_output(['adb', 'shell', 'getprop', 'ro.kernel.qemu'], text=True).strip() == '1', 'Synthetic emulator only'
    REPORT.mkdir(parents=True, exist_ok=True)
    reset('before')
    result = subprocess.run(['./gradlew', ':app:connectedFullDebugAndroidTest',
        f'-Pandroid.testInstrumentationRunnerArguments.class={CLASS}', '--no-parallel', '--max-workers=2'], cwd=ROOT)
    for source, label in [('outputs/androidTest-results', 'results'), ('reports/androidTests', 'html')]:
        path = ROOT / 'app/build' / source
        if path.exists():
            shutil.copytree(path, REPORT / label, dirs_exist_ok=True)
    result.check_returncode()
    cases = [case for path in (REPORT / 'results').rglob('TEST-*.xml')
             for case in ET.parse(path).getroot().iter('testcase') if case.get('classname') == CLASS]
    assert len(cases) == 1 and cases[0].get('name') == 'mainActivityVisitAndIndividualLabSafCommitReopenPreviewUnlinkAndTransfer'
    assert all(not list(case.iter(tag)) for case in cases for tag in ('failure', 'error', 'skipped'))
    reset('after')


if __name__ == '__main__':
    main()
