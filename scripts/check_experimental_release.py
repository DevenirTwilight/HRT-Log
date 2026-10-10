#!/usr/bin/env python3
"""Release boundary for the opt-in experimental PK page (REQUIREMENTS §55).

Checks the merged fullRelease manifest and, when present, the fullRelease APK:
debug-only research activities stay out of release, no network permission or service appears,
every ABI keeps its SQLCipher library stored deflated (compact packaging), and the debug hosts are not in the dex.
"""
import pathlib, sys, zipfile, xml.etree.ElementTree as ET
NS = '{http://schemas.android.com/apk/res/android}'
ABIS = {'arm64-v8a', 'armeabi-v7a', 'x86', 'x86_64'}
DEBUG_ONLY = ('ExperimentalSlComparisonActivity', 'ExperimentalPkPreviewActivity')

manifests = [p for p in pathlib.Path('app/build/intermediates/merged_manifests').rglob('AndroidManifest.xml') if 'fullRelease' in str(p)]
assert manifests, 'fullRelease merged manifest missing; run :app:assembleFullRelease first'
for path in manifests:
    root = ET.parse(path).getroot()
    permissions = {n.get(NS + 'name') for n in root if n.tag == 'uses-permission'}
    assert 'android.permission.INTERNET' not in permissions, f'INTERNET present: {path}'
    app = root.find('application')
    components = [c.get(NS + 'name') for c in app if c.tag in ('activity', 'activity-alias', 'service', 'receiver', 'provider')]
    leaked = [c for c in components if any(d in c for d in DEBUG_ONLY) or '.debug.' in c]
    assert not leaked, f'debug-only components in release manifest: {leaked}'
    print(f'PASS manifest {path}: no debug research activity, no INTERNET')

apks = sorted(pathlib.Path('app/build/outputs/apk/full/release').glob('*.apk'))
if not apks:
    print('SKIP APK checks: no fullRelease APK built'); sys.exit(0)
for apk in apks:
    with zipfile.ZipFile(apk) as z:
        libs = [i for i in z.infolist() if i.filename.startswith('lib/') and i.filename.endswith('.so')]
        abis = {i.filename.split('/')[1] for i in libs}
        assert abis == ABIS, f'{apk}: ABIs {sorted(abis)} != {sorted(ABIS)}'
        for abi in ABIS:
            assert any(i.filename == f'lib/{abi}/libsqlcipher.so' for i in libs), f'{apk}: libsqlcipher.so missing for {abi}'
        stored = [i.filename for i in libs if i.compress_type != zipfile.ZIP_DEFLATED]
        assert not stored, f'{apk}: native libraries no longer deflated: {stored}'
        dex = b''.join(z.read(n) for n in z.namelist() if n.endswith('.dex'))
        for name in DEBUG_ONLY:
            assert name.encode() not in dex, f'{apk}: {name} found in release dex'
    print(f'PASS {apk.name}: {apk.stat().st_size} bytes, ABIs {sorted(abis)}, {len(libs)} native libs deflated, SQLCipher in every ABI, no debug research host')
