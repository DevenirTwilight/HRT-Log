#!/usr/bin/env python3
"""Inspect the manifests produced by AGP, never only the source manifest."""
import pathlib, xml.etree.ElementTree as ET
root=pathlib.Path('app/build/intermediates/merged_manifests')
found={}
for path in root.rglob('AndroidManifest.xml'):
    variant=next((v for v in ['fullRelease','playRelease'] if v in str(path)),None)
    if variant:
        xml=ET.parse(path).getroot()
        permissions=[n.get('{http://schemas.android.com/apk/res/android}name') for n in xml if n.tag.startswith('uses-permission')]
        assert 'android.permission.INTERNET' not in permissions, f'INTERNET present: {path}'
        assert xml.get('package')=='net.plainnotes.app', f'Wrong applicationId: {path}'
        found[variant]=str(path)
assert set(found)=={'fullRelease','playRelease'}, f'Missing release manifests: {found}'
for variant,path in found.items():print(f'PASS {variant}: no INTERNET permission ({path})')
