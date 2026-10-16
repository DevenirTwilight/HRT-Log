#!/usr/bin/env python3
"""Reproduce ANSM-03A source isolation, fingerprints and actual local JUnit evidence. No network."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
MODEL = Path('core/domain/src/test/kotlin/net/plainnotes/app/domain/measurement')
PROTECTED = ('app/src/main', 'app/src/debug', 'app/src/full', 'core/data', 'core/reminder', 'core/ui',
             'core/domain/src/main', 'pk-engine', 'importer', '.github', 'gradle', 'app/build.gradle.kts',
             'core/domain/build.gradle.kts', 'settings.gradle.kts', 'build.gradle.kts', 'gradle.properties',
             'docs/HANDOFF.md', 'docs/REQUIREMENTS.md', 'docs/STATUS.md', 'docs/BACKLOG.md')


def git(*args):
    return subprocess.check_output(['git', *args], cwd=ROOT, text=True).strip()


def isolation():
    offenders = []
    for module in ('app', 'core', 'pk-engine', 'importer'):
        for file in (ROOT / module).rglob('*.kt'):
            relative = file.relative_to(ROOT)
            if 'build' in relative.parts or 'test' in relative.parts or 'androidTest' in relative.parts:
                continue
            if 'net.plainnotes.app.domain.measurement' in file.read_text():
                offenders.append(str(relative))
    imports = [line for line in (ROOT / MODEL / 'MeasurementCore.kt').read_text().splitlines() if line.startswith('import ')]
    allowed = ['import java.math.BigDecimal', 'import java.time.LocalDate']
    return {'status': 'PASS' if not offenders and imports == allowed else 'FAIL',
            'production_callers': offenders, 'model_imports': imports, 'source_set': 'test',
            'production_integration': 'NOT_IMPLEMENTED'}


def report(baseline):
    files = sorted((ROOT / MODEL).glob('*.kt'))
    changed = git('diff', '--name-only', baseline, '--', *PROTECTED).splitlines()
    fingerprints = {path: git('rev-parse', f'{baseline}:{path}') for path in PROTECTED}
    current = {path: git('rev-parse', f'HEAD:{path}') for path in PROTECTED}
    suites = []
    for module, task in [('core/domain', 'test'), ('pk-engine', 'test'), ('importer', 'test'), ('tools/attachment-poc', 'test'),
                         ('core/data', 'testDebugUnitTest'), ('app', 'testFullDebugUnitTest')]:
        folder = ROOT / module / 'build/test-results' / task
        rows = []
        for xml in sorted(folder.glob('TEST-*.xml')):
            s = ET.parse(xml).getroot()
            rows.append({'suite': s.get('name'), 'timestamp': s.get('timestamp'),
                         **{k: int(s.get(k, 0)) for k in ['tests', 'failures', 'errors', 'skipped']}})
        suites.append({'module': module, 'task': task, 'status': ('NOT_RUN' if not rows else
            'FAIL' if any(r['failures'] or r['errors'] for r in rows) else 'PASS'), 'suites': rows})
    jar = ROOT / 'core/domain/build/libs/domain.jar'
    jar_check = {'status': 'NOT_RUN'}
    if jar.exists():
        with zipfile.ZipFile(jar) as z:
            found = [n for n in z.namelist() if '/measurement/' in n]
        jar_check = {'status': 'FAIL' if found else 'PASS', 'measurement_entries': found,
                     'sha256': hashlib.sha256(jar.read_bytes()).hexdigest()}
    return {'baseline': baseline, 'observed_head': git('rev-parse', 'HEAD'), 'isolation': isolation(),
            'protected_changed_files': changed, 'protected_baseline_objects': fingerprints,
            'protected_head_objects': current, 'protected_status': 'PASS' if not changed and current == fingerprints else 'FAIL',
            'source_sha256': {str(f.relative_to(ROOT)): hashlib.sha256(f.read_bytes()).hexdigest() for f in files},
            'main_jar': jar_check, 'actual_local_reports': suites,
            'report_semantics': 'JUnit XML present on disk; use command completion and source hashes to identify tested revision. No absent test is inferred PASS.',
            'new_room_backup_binding': 'NOT_IMPLEMENTED', 'physical_device': 'NOT_RUN'}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--baseline', required=True)
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    result = report(args.baseline)
    text = json.dumps(result, ensure_ascii=False, indent=2) + '\n'
    if args.output:
        args.output.write_text(text)
    else:
        print(text, end='')
    raise SystemExit(1 if result['isolation']['status'] == 'FAIL' or result['protected_status'] == 'FAIL' else 0)
