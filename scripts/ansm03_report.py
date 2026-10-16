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


APPROVED_CORE = Path('core/domain/src/main/kotlin/net/plainnotes/app/domain/measurement/MeasurementCore.kt')
APPROVED_STOCK_QUANTUM = Path('core/domain/src/main/kotlin/net/plainnotes/app/domain/stock/StockQuantum.kt')
STOCK_IMPORTS = ['import java.math.BigDecimal', 'import net.plainnotes.app.domain.measurement.*']
PURE_IMPORTS = ['import java.math.BigDecimal', 'import java.time.LocalDate']
FORBIDDEN_CORE_APIS = ('android.', 'androidx.', 'kotlinx.coroutines', 'java.io.', 'java.net.', 'java.nio.',
                      'java.sql.', 'javax.sql.', 'System.currentTimeMillis', 'LocalDate.now(', 'System.getenv', 'Class.forName')


def isolation():
    # P3 core and P4 exact-quantum adapter only; database/UI/ledger callers remain forbidden.
    offenders = []
    for module in ('app', 'core', 'pk-engine', 'importer'):
        for file in (ROOT / module).rglob('*.kt'):
            relative = file.relative_to(ROOT)
            if any(part in relative.parts for part in ('build', 'test', 'androidTest', 'historyTest', 'medicalTest', 'attachmentTest')):
                continue
            if any(token in file.read_text() for token in ('net.plainnotes.app.domain.measurement', 'net.plainnotes.app.domain.stock')) and relative not in (APPROVED_CORE, APPROVED_STOCK_QUANTUM):
                offenders.append(str(relative))
    imports = [line for line in (ROOT / MODEL / 'MeasurementCore.kt').read_text().splitlines() if line.startswith('import ')]
    core = ROOT / APPROVED_CORE
    production = core.read_text() if core.is_file() else ''
    production_imports = [line for line in production.splitlines() if line.startswith('import ')]
    forbidden = [token for token in FORBIDDEN_CORE_APIS if token in production]
    stock = ROOT / APPROVED_STOCK_QUANTUM
    stock_text = stock.read_text() if stock.is_file() else ''
    stock_imports = [line for line in stock_text.splitlines() if line.startswith('import ')]
    stock_forbidden = [token for token in FORBIDDEN_CORE_APIS if token in stock_text]
    stock_valid = not stock.exists() or (stock_imports == STOCK_IMPORTS and not stock_forbidden)
    return {'status': 'PASS' if stock_valid and not offenders and imports == PURE_IMPORTS and core.is_file() and production_imports == PURE_IMPORTS and not forbidden else 'FAIL',
            'approved_stock_quantum': str(APPROVED_STOCK_QUANTUM) if stock.exists() else None,
            'stock_imports': stock_imports, 'stock_forbidden_apis': stock_forbidden, 'production_callers': offenders, 'model_imports': imports, 'production_imports': production_imports,
            'forbidden_apis': forbidden, 'approved_core': str(APPROVED_CORE), 'source_set': 'reviewed-main-core-and-frozen-test-oracle',
            'production_integration': 'CORE_ONLY_NO_INVENTORY'}


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
            forbidden = [n for n in z.namelist() if '/prototype/' in n or '/shadow/' in n]
        jar_check = {'status': 'PASS' if 'net/plainnotes/app/domain/measurement/Measurements.class' in found and not forbidden else 'FAIL', 'measurement_entries': found,
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
