#!/usr/bin/env python3
"""B2-GATE APK payload guard. Not a malicious-dependency/security certification.

Scan every DEX and binary manifest for known test-only namespaces, protocol marks and provider.
R8 must not be relied on to remove an accidentally registered experimental provider.
"""
from pathlib import Path
import zipfile

FORBIDDEN=(b'net/plainnotes/poc/', b'net.plainnotes.poc.', b'com/google/crypto/tink/',
           b'com.google.crypto.tink.', b'SYNPOC01', b'DRAFT-ONLY-OBJ0!', b'B2-TEST-ONLY',
           b'SyntheticStreamProvider', b'attachmentpoc')

def check_apk(path):
    with zipfile.ZipFile(path) as apk:
        dex=[name for name in apk.namelist() if name.endswith('.dex')]
        assert dex, 'No DEX payload to verify'
        for name in dex+['AndroidManifest.xml']:
            value=apk.read(name)
            for marker in FORBIDDEN:
                assert marker not in value and marker.decode().encode('utf-16-le') not in value, f'Test-only marker {marker!r} in {name}'
    return len(dex)

if __name__=='__main__':
    paths=list(Path('app/build/outputs/apk/full/release').glob('*.apk'))
    assert paths, 'Missing fullRelease APK; run assembleFullRelease first'
    for path in paths:print(f'PASS attachment isolation: {path}, {check_apk(path)} DEX files')
