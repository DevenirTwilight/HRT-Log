#!/usr/bin/env python3
"""ANSM-03B0 test-source and built release isolation guard; no APK is distributed."""
from pathlib import Path
import hashlib
import json
import zipfile

ROOT = Path(__file__).resolve().parents[1]
MARKERS = (b'net/plainnotes/app/domain/measurement/', b'net/plainnotes/app/domain/shadow/',
           b'net.plainnotes.app.domain.measurement.', b'net.plainnotes.app.domain.shadow.',
           b'ShadowMeasurementAllocator', b'Ansm03b0', b'ansm_lab_', b'ansm03-exact-v1')


def check_archive(path, apk=False):
    with zipfile.ZipFile(path) as z:
        names = [n for n in z.namelist() if n.endswith('.dex' if apk else '.class')]
        assert names, f'No executable payload: {path}'
        for name in names + (['AndroidManifest.xml'] if apk else []):
            data = z.read(name)
            for marker in MARKERS:
                assert marker not in name.encode() and marker not in data and marker.decode().encode('utf-16-le') not in data, f'Test-only marker {marker!r}: {path}:{name}'
    return len(names)


def check_sources(root=ROOT):
    audit = json.loads((root / 'docs/ansm03b0/repository-audit.json').read_text())
    # The frozen parent model and all three original test files are never rewritten by B0.
    for path, expected in audit['original_source_hashes'].items():
        assert hashlib.sha256((root / path).read_bytes()).hexdigest() == expected, f'Parent changed: {path}'
    for base in ('app/src', 'core/data/src', 'core/domain/src', 'core/reminder/src'):
        for p in (root / base).rglob('*.kt'):
            rel = p.relative_to(root)
            if any(part in ('test', 'androidTest', 'historyTest', 'medicalTest', 'attachmentTest') for part in rel.parts):
                continue
            value = p.read_bytes()
            assert not any(m in value for m in MARKERS), f'Runtime source references prototype: {rel}'
    generated = root / 'core/data/build/generated/ansm03b0TestSources'
    if generated.exists():
        expected = {'net/plainnotes/app/domain/measurement/MeasurementCore.kt', 'net/plainnotes/app/domain/shadow/ShadowMeasurementAllocator.kt'}
        assert {str(p.relative_to(generated)) for p in generated.rglob('*.kt')} == expected
        for p in generated.rglob('*.kt'):
            assert p.read_bytes() == (root / 'core/domain/src/test/kotlin' / p.relative_to(generated)).read_bytes()


def main():
    check_sources()
    check_archive(ROOT / 'core/domain/build/libs/domain.jar')
    jars = list((ROOT / 'core/data/build/intermediates/runtime_library_classes_jar/release').rglob('*.jar'))
    assert jars, 'Missing unshrunk release runtime JAR; assembleFullRelease first'
    for jar in jars:
        check_archive(jar)
    apks = list((ROOT / 'app/build/outputs/apk/full/release').glob('*.apk'))
    assert apks, 'Missing release APK'
    for apk in apks:
        check_archive(apk, apk=True)
    print('PASS ANSM-03B0: parent unchanged; test generation exact; runtime JARs and release APK exclude prototype')


if __name__ == '__main__':
    main()
