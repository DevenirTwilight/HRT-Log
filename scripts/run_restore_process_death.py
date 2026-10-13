#!/usr/bin/env python3
"""Kill a prepared password dialog, then verify a genuinely new process; synthetic encrypted data only."""
import subprocess
import time
from pathlib import Path
root=Path(__file__).resolve().parent.parent
runner='net.plainnotes.app.test/androidx.test.runner.AndroidJUnitRunner'
klass='net.plainnotes.app.privacy.RestoreProcessDeathAndroidTest'
for relative in ['app/build/outputs/apk/full/debug/app-full-debug.apk','app/build/outputs/apk/androidTest/full/debug/app-full-debug-androidTest.apk']:
    subprocess.check_call(['adb','install','-r','-t',str(root/relative)])
subprocess.check_call(['adb','shell','run-as','net.plainnotes.app','rm','-f','cache/b2-restore-death-ready'])
command=['adb','shell','am','instrument','-w','-e','class',klass+'#prepareOpenPasswordAndWaitForExternalKill','-e','restore_death_phase','prepare',runner]
with (root/'app/build/reports/restore-death-prepare.log').open('w') as log:
    process=subprocess.Popen(command,stdout=log,stderr=subprocess.STDOUT)
    try:
        deadline=time.monotonic()+40
        while time.monotonic()<deadline:
            marker=subprocess.run(['adb','shell','run-as','net.plainnotes.app','cat','cache/b2-restore-death-ready'],capture_output=True,text=True)
            if marker.returncode==0 and marker.stdout.strip().isdigit():
                prepared=int(marker.stdout.strip());break
            assert process.poll() is None,'Prepare ended before a password-dialog checkpoint'
            time.sleep(.2)
        else:
            raise AssertionError('No prepared password-dialog checkpoint')
        subprocess.check_call(['adb','shell','am','force-stop','net.plainnotes.app'])
        process.wait(timeout=20)
        live=subprocess.run(['adb','shell','pidof','net.plainnotes.app'],capture_output=True,text=True)
        assert str(prepared) not in live.stdout.split(),'Prepared process still alive'
        print('PASS: deliberately killed the process after the real password-dialog checkpoint',flush=True)
    finally:
        if process.poll() is None:
            subprocess.run(['adb','shell','am','force-stop','net.plainnotes.app'],check=True)
            process.wait(timeout=20)
result=subprocess.run(['adb','shell','am','instrument','-w','-e','class',klass+'#freshProcessHasNoPendingRestoreOrPassword','-e','restore_death_phase','verify',runner],text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT)
print(result.stdout,flush=True)
assert result.returncode==0 and 'OK (1 test)' in result.stdout,'Fresh-process restore safety verification failed'
print('PASS: fresh process has no pending source/password/restore and keeps encrypted source intact')
