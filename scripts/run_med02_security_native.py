#!/usr/bin/env python3
"""Retain raw real pipe observations even when either native case fails."""
import pathlib
import subprocess
import sys

report=pathlib.Path('core/data/build/reports/med02-security')
report.mkdir(parents=True,exist_ok=True)
apks=list(pathlib.Path('core/data/build/outputs/apk/androidTest/debug').glob('*.apk'))
assert len(apks)==1,apks
subprocess.run(['adb','install','-r','-t',str(apks[0])],check=True)
result=subprocess.run(['adb','shell','am','instrument','-w','-e','class',
    'net.plainnotes.app.data.MedicalCollectionRevocationAndroidTest','-e','securityPipes','1',
    'net.plainnotes.app.data.test/androidx.test.runner.AndroidJUnitRunner'],capture_output=True,text=True,timeout=180)
(report/'pipes-original.log').write_text(result.stdout+result.stderr)
for name in ['security-f03-observation.json','security-f04-observation.json']:
    observation=subprocess.run(['adb','shell','run-as','net.plainnotes.app.data.test','cat','files/'+name],capture_output=True,text=True)
    (report/name).write_text(observation.stdout+observation.stderr)
# Execute restart checks independently so a pipe failure does not erase their evidence.
restart=subprocess.run([sys.executable,'scripts/run_med02_security_restart.py'])
assert result.returncode==0 and 'OK (2 tests)' in result.stdout and 'FAILURES!!!' not in result.stdout and 'INSTRUMENTATION_FAILED' not in result.stdout,result.stdout
assert restart.returncode==0,'Restart acceptance failed; retained original logs'
print(result.stdout)
