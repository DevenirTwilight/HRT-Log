#!/usr/bin/env python3
"""API26/27 actual target PID death + fresh-process verification; synthetic namespaces only."""
import json
import pathlib
import subprocess

REPORT=pathlib.Path('app/build/reports/med02-security')
REPORT.mkdir(parents=True,exist_ok=True)
assert subprocess.check_output(['adb','shell','getprop','ro.kernel.qemu'],text=True).strip()=='1'
api=int(subprocess.check_output(['adb','shell','getprop','ro.build.version.sdk'],text=True).strip())
assert api in (26,27,29,35)
runner='net.plainnotes.app.test/androidx.test.runner.AndroidJUnitRunner'
for path in ['app/build/outputs/apk/full/debug/app-full-debug.apk','app/build/outputs/apk/androidTest/full/debug/app-full-debug-androidTest.apk']:
    subprocess.run(['adb','install','-r','-t',path],check=True)

def run(klass,method,phase,death=False):
    result=subprocess.run(['adb','shell','am','instrument','-w','-e','class',f'{klass}#{method}',
        '-e','securityPhase',phase,runner],text=True,capture_output=True,timeout=90)
    (REPORT/f'{phase}.log').write_text(result.stdout+result.stderr)
    if not death:
        assert result.returncode==0 and 'OK (1 test)' in result.stdout and 'FAILURES!!!' not in result.stdout and 'INSTRUMENTATION_FAILED' not in result.stdout,result.stdout

if api in (26,27):
    f01='net.plainnotes.app.security.AppLockAtomicRestartAndroidTest'
    run(f01,'dieAfterActualAtomicBackupRename','f01-die',death=True)
    sentinel=subprocess.check_output(['adb','shell','run-as','net.plainnotes.app','cat','files/security-f01-death.txt'],text=True).strip()
    assert sentinel.isdigit()
    # pidof returns1 for a dead process; preserve its actual exit/output rather than treating it as test failure.
    pid=subprocess.run(['adb','shell','pidof','net.plainnotes.app'],capture_output=True,text=True)
    assert sentinel not in pid.stdout.split(),'Old target PID still alive'
    run(f01,'freshProcessRecoversAndFailureStaysLockedWithoutReplacingKey','f01-verify')
f02='net.plainnotes.app.attachments.AttachmentReconciliationRestartAndroidTest'
run(f02,'prepareRealCommitWithoutAcknowledgement','f02-prepare')
subprocess.run(['adb','shell','am','force-stop','net.plainnotes.app'],check=True)
run(f02,'freshProcessOnlyReconcilesOriginalCommit','f02-verify')
(REPORT/'restart-result.json').write_text(json.dumps({'api':api,'F01':'PASS' if api in (26,27) else 'NOT_RUN','F02':'PASS','physicalDevice':'NOT_RUN','hardwarePowerLoss':'NOT_RUN'},indent=2))
print('PASS F01 real PID death / recovery and F02 real COMMIT / read-only reconciliation' if api in (26,27) else 'PASS F02 real COMMIT / read-only reconciliation; F01 NOT_RUN on this API')
