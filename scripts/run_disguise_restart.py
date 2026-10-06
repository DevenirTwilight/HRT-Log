#!/usr/bin/env python3
"""Run full-flavor restart phases in different processes; only synthetic ordinary notes are stored."""
import subprocess
runner='net.plainnotes.app.test/androidx.test.runner.AndroidJUnitRunner'
klass='net.plainnotes.app.disguise.ProcessRestartAndroidTest'
def phase(method, value):
    out=subprocess.check_output(['adb','shell','am','instrument','-w','-e','class',f'{klass}#{method}',
        '-e','restart_phase',value,runner],text=True,stderr=subprocess.STDOUT)
    print(out)
    assert 'OK (1 test)' in out, f'Restart phase {value} failed'
phase('prepare','prepare')
subprocess.check_call(['adb','shell','am','force-stop','net.plainnotes.app'])
phase('verifyFreshProcess','verify')
print('PASS: fresh process requires authentication, private ciphertext persists')
