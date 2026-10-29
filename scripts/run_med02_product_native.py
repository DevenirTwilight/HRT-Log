#!/usr/bin/env python3
"""Isolated emulator installation: REAL MainActivity/SAF/scoped production paths, synthetic data only."""
from pathlib import Path
import shutil
import subprocess
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent
REPORT = ROOT / 'app/build/reports/med02-product-native'
CLASS = 'net.plainnotes.app.attachments.MedicalAttachmentProductAndroidTest'
SHOT_NAMES = ('pdf-page1','pdf-page2','pdf-page3','pdf-zoom-pan','pdf-controls-hidden','pdf-android8-refused','image-fit','image-zoom-pan')


def reset(label):
    # Only generated synthetic evidence, never a library object or user file.
    subprocess.run(['adb','shell','rm','-f',*[f'/data/local/tmp/hrt-synthetic-reader-{name}.{suffix}'
                    for name in SHOT_NAMES for suffix in ('png','json')]],check=True)
    for package in ('net.plainnotes.app.test', 'net.plainnotes.app'):
        result = subprocess.run(['adb', 'uninstall', package], capture_output=True, text=True)
        (REPORT / f'{label}-{package}.log').write_text(result.stdout + result.stderr)
        listed = subprocess.check_output(['adb', 'shell', 'pm', 'list', 'packages', package], text=True).splitlines()
        assert f'package:{package}' not in listed, 'Isolated emulator package reset failed'


def main():
    assert subprocess.check_output(['adb', 'shell', 'getprop', 'ro.kernel.qemu'], text=True).strip() == '1', 'Synthetic emulator only'
    REPORT.mkdir(parents=True, exist_ok=True)
    cases=((CLASS, 'mainActivityVisitAndIndividualLabSafCommitReopenPreviewUnlinkAndTransfer'),
           (CLASS, 'standalonePdfExplicitWholeDrawLinkFiveMembersAndReopenUseOneOriginal'),
           (CLASS, 'fullscreenReaderRenamesThreePagePdfAndLargeImageThenReturnsToTheSameFile'))
    for test_class, name in cases:
        reset(f'before-{name}')
        result = subprocess.run(['./gradlew', ':app:connectedFullDebugAndroidTest',
            f'-Pandroid.testInstrumentationRunnerArguments.class={test_class}#{name}', '--no-parallel', '--max-workers=2'], cwd=ROOT)
        for source, label in [('outputs/androidTest-results', 'results'), ('reports/androidTests', 'html')]:
            path = ROOT / 'app/build' / source
            if path.exists():
                shutil.copytree(path, REPORT / name / label, dirs_exist_ok=True)
        result.check_returncode()
        cases = [case for path in (REPORT / name / 'results').rglob('TEST-*.xml')
                 for case in ET.parse(path).getroot().iter('testcase') if case.get('classname') == test_class]
        assert len(cases) == 1 and cases[0].get('name') == name
        assert all(not list(case.iter(tag)) for case in cases for tag in ('failure', 'error', 'skipped'))
        if name == 'fullscreenReaderRenamesThreePagePdfAndLargeImageThenReturnsToTheSameFile':
            api = int(subprocess.check_output(['adb', 'shell', 'getprop', 'ro.build.version.sdk'], text=True).strip())
            pictures = ['pdf-page1', 'pdf-page2', 'pdf-page3', 'pdf-zoom-pan', 'pdf-controls-hidden'] if api >= 28 else ['pdf-android8-refused']
            pictures += ['image-fit', 'image-zoom-pan']
            target = REPORT / name / 'native-screenshots'
            target.mkdir(parents=True, exist_ok=True)
            for picture in pictures:
                for suffix in ('png', 'json'):
                    value = subprocess.check_output(['adb', 'exec-out', 'cat', f'/data/local/tmp/hrt-synthetic-reader-{picture}.{suffix}'])
                    assert value and (suffix != 'png' or value.startswith(bytes((137,80,78,71,13,10,26,10)))), f'Invalid synthetic screenshot {picture}.{suffix}: {value[:32]!r}'
                    (target / f'{picture}.{suffix}').write_bytes(value)
        reset(f'after-{name}')

    core_classes=('net.plainnotes.app.data.MedicalFilesOperationAndroidTest',
                  'net.plainnotes.app.data.MedicalFilesSqlCipherMigrationTest')
    result=subprocess.run(['./gradlew', ':core:data:connectedDebugAndroidTest',
        '-Pandroid.testInstrumentationRunnerArguments.class='+','.join(core_classes), '--no-parallel','--max-workers=2'],cwd=ROOT)
    for source,label in [('outputs/androidTest-results','results'),('reports/androidTests','html')]:
        path=ROOT/'core/data/build'/source
        if path.exists():shutil.copytree(path,REPORT/'core-med03'/label,dirs_exist_ok=True)
    result.check_returncode()
    cases=[case for path in (REPORT/'core-med03/results').rglob('TEST-*.xml') for case in ET.parse(path).getroot().iter('testcase') if case.get('classname') in core_classes]
    assert len(cases)==3 and {case.get('name') for case in cases}=={
        'oneOriginalFivePanelMembersStandaloneMultiLinkRecycleAndAuthenticatedRestore',
        'published21RetainsRowsIdentitiesAndValidatesRoom22AfterReopen',
        'ddlFailureRollsBackOriginalRowsTablesIndicesAndTriggers'}
    assert all(not list(case.iter(tag)) for case in cases for tag in ('failure','error','skipped'))



if __name__ == '__main__':
    main()
