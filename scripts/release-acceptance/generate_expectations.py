#!/usr/bin/env python3
"""Build-time expectations for the P1 release acceptance tests (synthetic, source-derived only).

Writes into <out>/assets/ (packaged into the instrumentation APK, never into the app APK):
  acceptance-expected.json  string values per locale from the source XML, resource names, named JSON SHA-256
  acceptance-condition.json scenario/mode/source identity for the evidence trail
and, for mode=functional, <out>/ported/: copies of selected JVM tests with only the Robolectric runner
annotations removed (assertions and golden data are byte-for-byte unchanged), so they run in the release
process on a device. The originals keep running unchanged in :app:testFullDebugUnitTest.
"""
import argparse
import hashlib
import json
import re
import subprocess
import xml.etree.ElementTree as ET
from pathlib import Path

LOCALES = {'': 'en', 'zh': 'zh-CN', 'b+zh+Hant': 'zh-TW', 'fr': 'fr'}
NAMED_JSON = {'pk-params.json': 'pk-engine/src/main/resources/pk-params.json',
              'symptom-sources.json': 'app/src/main/resources/symptom-sources.json',
              'wellbeing-translations.json': 'app/src/main/resources/wellbeing-translations.json'}
# Plain JUnit/Room/ApplicationProvider tests without file-system, shadow or Compose-host dependencies.
PORTED = ['PeriodStabilityTest', 'LabEstimateTest', 'ConcentrationCalculatorTest', 'ChartViewportTest', 'CalendarModelTest',
          'HistoricalContextTest', 'HistoryAttributionTest', 'ImportedHistoryIntegrationTest', 'ImportedHistoryTimelineTest',
          'LongitudinalProjectionTest', 'MilestoneSavingTest', 'ObservedTreatmentHistoryTest', 'PausedPlanTest',
          'PeriodMergeAcrossImportTest', 'PeriodTimelineTest', 'SameCardSplitTest', 'SourceNeutralContinuityTest',
          'SymptomCatalogTest', 'TimelineEditFlowTest', 'TimelineEditProjectionTest', 'TimelineV2FlowTest', 'TimelineV2Test',
          'TimesPerDayTest', 'UserDiagnosticRegressionTest', 'WellbeingSummaryTest', 'ExportTest', 'LegacyPeriodFixtures',
          'LegacyTimelineFixtures', 'timeline/MergeDiagnostics', 'security/AppLockTest', 'security/SessionTest']
RUNNER_ONLY = [re.compile(r'@RunWith\(RobolectricTestRunner::class\)\s*'), re.compile(r'@Config\([^()]*\)\s*'), re.compile(r'@GraphicsMode\([^()]*\)\s*'),
               re.compile(r'^import org\.robolectric\.(RobolectricTestRunner|annotation\.Config|annotation\.GraphicsMode)\s*$', re.M),
               re.compile(r'^import org\.junit\.runner\.RunWith\s*$', re.M)]


def android_unescape(text):
    """aapt2 rules for a plain <string>: quotes keep whitespace, otherwise whitespace runs collapse, backslash escapes."""
    out, quoted, pending_space, i = [], False, False, 0
    while i < len(text):
        c = text[i]
        if c == '\\' and i + 1 < len(text):
            n = text[i + 1]
            if pending_space and out: out.append(' ')
            pending_space = False
            if n == 'u' and re.fullmatch(r'[0-9a-fA-F]{4}', text[i + 2:i + 6] or ''):
                out.append(chr(int(text[i + 2:i + 6], 16))); i += 6; continue
            out.append({'n': '\n', 't': '\t'}.get(n, n)); i += 2; continue
        if c == '"':
            if pending_space and out: out.append(' ')
            pending_space = False; quoted = not quoted; i += 1; continue
        if c.isspace() and not quoted:
            pending_space = True; i += 1; continue
        if pending_space and out: out.append(' ')
        pending_space = False; out.append(c); i += 1
    return ''.join(out)


def read_values(res_dirs):
    strings, other = {}, {}
    for qualifier in LOCALES:
        folder = 'values' + ('-' + qualifier if qualifier else '')
        for res in res_dirs:
            for xml in sorted((res / folder).glob('*.xml')) if (res / folder).is_dir() else []:
                for el in ET.parse(xml).getroot():
                    name = el.get('name')
                    if el.tag == 'string':
                        value = None if len(el) else android_unescape(el.text or '')
                        strings.setdefault(LOCALES[qualifier], {})[name] = value
                    elif el.tag in ('plurals', 'string-array', 'array', 'integer-array'):
                        other.setdefault(el.tag, set()).add(name)
    return strings, {k: sorted(v) for k, v in other.items()}


def file_resources(res_dirs):
    names = {}
    for res in res_dirs:
        for folder in res.iterdir():
            kind = folder.name.split('-')[0]
            if kind in ('drawable', 'mipmap', 'xml', 'font', 'raw'):
                for f in folder.iterdir(): names.setdefault(kind, set()).add(f.name.split('.')[0])
    return {k: sorted(v) for k, v in names.items()}


def referenced(root):
    """Resources the shipped code or XML names statically: these must survive resource shrinking."""
    found = {}
    sources = [root / 'app/src/main', root / 'app/src/full'] + sorted((root / 'core').glob('*/src/main'))
    for base in sources:
        for f in base.rglob('*'):
            if f.suffix not in ('.kt', '.java', '.xml') or not f.is_file(): continue
            text = f.read_text(encoding='utf-8', errors='replace')
            for kind, name in re.findall(r'\bR\.(string|plurals|drawable|mipmap|xml|array)\.([A-Za-z0-9_]+)', text) + \
                    re.findall(r'@(string|plurals|drawable|mipmap|xml|array)/([A-Za-z0-9_]+)', text):
                found.setdefault(kind, set()).add(name)
    return {k: sorted(v) for k, v in found.items()}


def main():
    a = argparse.ArgumentParser()
    a.add_argument('--root', required=True); a.add_argument('--out', required=True)
    a.add_argument('--scenario', required=True); a.add_argument('--mode', required=True)
    args = a.parse_args()
    root, out = Path(args.root), Path(args.out)
    (out / 'assets').mkdir(parents=True, exist_ok=True)
    res_dirs = [root / 'app/src/main/res', root / 'app/src/full/res', root / 'core/reminder/src/main/res']
    strings, other = read_values(res_dirs)
    expected = {'locales': LOCALES, 'strings': strings, 'other_values': other, 'files': file_resources(res_dirs),
                'referenced_in_source': referenced(root),
                'named_json_sha256': {k: hashlib.sha256((root / v).read_bytes()).hexdigest() for k, v in NAMED_JSON.items()},
                'launcher_components': {'net.plainnotes.app.Launcher': {'enabled_by_default': True, 'icon': 'ic_launcher', 'label': 'app_name'},
                                        'net.plainnotes.app.CalculatorLauncher': {'enabled_by_default': False, 'icon': 'ic_shell_calc', 'label': 'shell_calc'},
                                        'net.plainnotes.app.NotesLauncher': {'enabled_by_default': False, 'icon': 'ic_shell_notes', 'label': 'shell_notes'}}}
    (out / 'assets/acceptance-expected.json').write_text(json.dumps(expected, ensure_ascii=False, sort_keys=True, indent=1), encoding='utf-8')
    try: sha = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip()
    except Exception: sha = 'unknown'
    try: dirty = bool(subprocess.check_output(['git', 'status', '--porcelain'], cwd=root, text=True).strip())
    except Exception: dirty = None
    (out / 'assets/acceptance-condition.json').write_text(json.dumps({'scenario': args.scenario, 'mode': args.mode, 'source_sha': sha,
        'worktree_dirty': dirty, 'resource_shrink': args.scenario in 'BDF', 'abi_filter': ['arm64-v8a'] if args.scenario in 'CD' else [], 'compressed_native': args.scenario in 'EF'}, indent=1))
    ported = out / 'ported'
    if ported.exists():
        for f in sorted(ported.rglob('*.kt')): f.unlink()
    if args.mode == 'functional':
        manifest = {}
        for rel in PORTED:
            src = root / 'app/src/test/java/net/plainnotes/app' / f'{rel}.kt'
            text = src.read_text(encoding='utf-8'); changed = text
            for pattern in RUNNER_ONLY: changed = pattern.sub('', changed)
            if re.search(r'org\.robolectric|\bshadowOf\b|RuntimeEnvironment', changed): raise SystemExit(f'{rel}: Robolectric dependency beyond runner annotations')
            dest = ported / 'net/plainnotes/app' / f'{rel}.kt'; dest.parent.mkdir(parents=True, exist_ok=True)
            dest.write_text(changed, encoding='utf-8')
            manifest[rel] = {'source_sha256': hashlib.sha256(text.encode()).hexdigest(), 'tests': len(re.findall(r'@Test\b', text)),
                             'removed_lines': [l for l in text.splitlines() if l not in changed.splitlines()]}
        (out / 'assets/acceptance-ported.json').write_text(json.dumps(manifest, indent=1))


if __name__ == '__main__':
    main()
