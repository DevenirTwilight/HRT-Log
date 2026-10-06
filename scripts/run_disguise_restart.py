#!/usr/bin/env python3
"""Run full-flavor restart phases in different processes; only synthetic ordinary notes are stored."""
import subprocess
from pathlib import Path
runner='net.plainnotes.app.test/androidx.test.runner.AndroidJUnitRunner'
klass='net.plainnotes.app.disguise.ProcessRestartAndroidTest'
# AGP connected tests can uninstall the target and instrumentation APKs when finishing.
root=Path(__file__).resolve().parent.parent
for relative in ['app/build/outputs/apk/full/debug/app-full-debug.apk',
                 'app/build/outputs/apk/androidTest/full/debug/app-full-debug-androidTest.apk']:
    subprocess.check_call(['adb','install','-r','-t',str(root/relative)])
def phase(method, value):
    result=subprocess.run(['adb','shell','am','instrument','-w','-e','class',f'{klass}#{method}',
        '-e','restart_phase',value,runner],text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT)
    print(result.stdout,flush=True)
    assert result.returncode==0 and 'OK (1 test)' in result.stdout, f'Restart phase {value} failed'
phase('prepare','prepare')
subprocess.check_call(['adb','shell','am','force-stop','net.plainnotes.app'])
phase('verifyFreshProcess','verify')
print('PASS: fresh process requires authentication, private ciphertext persists')
