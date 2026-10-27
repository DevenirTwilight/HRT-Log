#!/usr/bin/env python3
"""B2-GATE APK payload guard. Not a malicious-dependency/security certification.

Scan every DEX and binary manifest for known test-only namespaces, protocol marks and provider.
R8 must not be relied on to remove an accidentally registered experimental provider.
"""
from pathlib import Path
import zipfile

FORBIDDEN=(b'net/plainnotes/poc/', b'net.plainnotes.poc.', b'com/google/crypto/tink/',
           b'com.google.crypto.tink.', b'SYNPOC01', b'DRAFT-ONLY-OBJ0!', b'B2-TEST-ONLY',
           b'SyntheticStreamProvider', b'attachmentpoc', b'Gate2StreamProvider', b'B2-SECURITY-GATE2-TEST-ONLY', b'Gate2PeerProvider', b'KernelProxyFdGate3', b'B3Vault', b'HRTLOG-B3-TEST-ONLY',
           b'net/plainnotes/app/debug/demo/', b'MedicalArchiveDemoActivity', b'DemoRepository',
           b'net/plainnotes/app/debug/catalog/', b'CatalogPrototypeActivity', b'SYNTH-CIS-', b'DraftMedicalArchive', b'MED02-DRAFT-ONLY-V0', b'AttachmentOperationTestActivity', b'SyntheticAttachmentDatabase',
           b'SyntheticDocumentsProvider', b'synthetic-medflow-', b'net.plainnotes.app.test.synthetic-medflow',
           b'MedicalAttachmentUiTestActivity', b'SyntheticMedicalArchiveProvider', b'SyntheticSecurityDocumentsProvider')
# MED-02B6: the synthetic document-library demo exists only in the debug build; its resources must not reach release.
RESOURCE_FORBIDDEN=(b'demo_banner', b'demo_launcher_label', b'SYNTHETIC DEMO', b'cat_synthetic', b'SYNTHETIC EXAMPLES')

def check_apk(path):
    with zipfile.ZipFile(path) as apk:
        assert not any('ansm02-synthetic' in name for name in apk.namelist()), 'Synthetic catalogue asset in release'
        dex=[name for name in apk.namelist() if name.endswith('.dex')]
        assert dex, 'No DEX payload to verify'
        for name in dex+['AndroidManifest.xml']:
            value=apk.read(name)
            for marker in FORBIDDEN:
                assert marker not in value and marker.decode().encode('utf-16-le') not in value, f'Test-only marker {marker!r} in {name}'
        if 'resources.arsc' in apk.namelist():
            table=apk.read('resources.arsc')
            for marker in RESOURCE_FORBIDDEN:
                assert marker not in table and marker.decode().encode('utf-16-le') not in table, f'Demo resource {marker!r} in resources.arsc'
    return len(dex)

if __name__=='__main__':
    paths=list(Path('app/build/outputs/apk/full/release').glob('*.apk'))
    assert paths, 'Missing fullRelease APK; run assembleFullRelease first'
    for path in paths:print(f'PASS attachment isolation: {path}, {check_apk(path)} DEX files')
