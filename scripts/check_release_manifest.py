#!/usr/bin/env python3
"""Inspect the manifests produced by AGP, never only the source manifest."""
import pathlib, xml.etree.ElementTree as ET
root=pathlib.Path('app/build/intermediates/merged_manifests')
found={}
for path in root.rglob('AndroidManifest.xml'):
    variant=next((v for v in ['fullRelease'] if v in str(path)),None)
    if variant:
        xml=ET.parse(path).getroot()
        permissions=[n.get('{http://schemas.android.com/apk/res/android}name') for n in xml if n.tag.startswith('uses-permission')]
        assert 'android.permission.INTERNET' not in permissions, f'INTERNET present: {path}'
        assert xml.get('package')=='net.plainnotes.app', f'Wrong applicationId: {path}'
        ns='{http://schemas.android.com/apk/res/android}'
        app=xml.find('application')
        # The application keeps its real identity (REQUIREMENTS 14); only launcher aliases are disguised.
        assert app.get(ns+'label')=='@string/app_name', f'Application label is not HRT Log: {path}'
        assert app.get(ns+'icon')=='@mipmap/ic_launcher', f'Application icon is not the HRT Log icon: {path}'
        activities={a.get(ns+'name'):a for a in app.findall('activity')}
        aliases={a.get(ns+'name'):a for a in app.findall('activity-alias')}
        normal=aliases['net.plainnotes.app.Launcher']
        assert normal.get(ns+'label')=='@string/app_name'
        assert normal.get(ns+'icon')=='@mipmap/ic_launcher'
        assert activities['net.plainnotes.app.MainActivity'].get(ns+'exported')=='false'
        private='net.plainnotes.app.disguise.privatenotes.PrivateNotesActivity'
        assert private in activities and activities[private].get(ns+'exported')=='false'
        assert activities[private].find('intent-filter') is None
        assert activities[private].get(ns+'label')=='@string/private_notes'
        for name in ['Calculator','Notes']:
            alias=aliases['net.plainnotes.app.'+name+'Launcher']
            assert alias.get(ns+'enabled')=='false' and alias.get(ns+'exported')=='true'
            target=activities[alias.get(ns+'targetActivity')]
            assert target.get(ns+'exported')=='false'
            assert target.get(ns+'taskAffinity')!='', f'Shell must share the protected task: {path}'
        assert activities[private].get(ns+'taskAffinity')!=''
        found[variant]=str(path)
assert set(found)=={'fullRelease'}, f'Missing release manifests: {found}'
for variant,path in found.items():print(f'PASS {variant}: HRT Log application identity, correct entry points, no INTERNET ({path})')
