#!/usr/bin/env python3
"""Fixed large native run followed by two small real process-death cases. Synthetic emulator only."""
import json
from pathlib import Path
import subprocess
import time
from run_ansm_native_performance import verify_emulator

ROOT=Path(__file__).resolve().parents[1]
PACKAGE='net.plainnotes.app.data.test'
RUNNER=PACKAGE+'/androidx.test.runner.AndroidJUnitRunner'
KLASS='net.plainnotes.app.data.MeasuredRestoreDeathProbe'

def death_case(stage):
    subprocess.run(['adb','shell','run-as',PACKAGE,'rm','-f','files/synthetic-restore-death-ready.json','files/synthetic-restore-death-result.json'],check=True)
    cmd=['adb','shell','am','instrument','-w','-e','class',KLASS+'#prepareAndWaitForExternalKill',
         '-e','ansm_restore_death_action','prepare','-e','ansm_restore_death_stage',stage,RUNNER]
    folder=ROOT/'core/data/build/reports/ansm-native-performance'
    with (folder/('restore-death-'+stage+'-prepare.log')).open('w') as log:
        process=subprocess.Popen(cmd,stdout=log,stderr=subprocess.STDOUT)
        try:
            end=time.monotonic()+60
            while time.monotonic()<end:
                ready=subprocess.run(['adb','shell','run-as',PACKAGE,'cat','files/synthetic-restore-death-ready.json'],capture_output=True,text=True)
                if ready.returncode==0 and ready.stdout.strip():
                    try:marker=json.loads(ready.stdout)
                    except ValueError:
                        time.sleep(.2);continue  # Same attempt: a host read may race writeText.
                    assert marker['stage']==stage;break
                assert process.poll() is None,'Prepare ended before pending transaction'
                time.sleep(.2)
            else:
                raise AssertionError('No pending restore checkpoint')
            alive=subprocess.run(['adb','shell','pidof',PACKAGE],capture_output=True,text=True)
            assert str(marker['pid']) in alive.stdout.split(),'Process died before the deliberate kill'
            subprocess.run(['adb','shell','am','force-stop',PACKAGE],check=True)
            process.wait(timeout=20)
            live=subprocess.run(['adb','shell','pidof',PACKAGE],capture_output=True,text=True)
            assert str(marker['pid']) not in live.stdout.split(),'Prepared process still alive'
        finally:
            if process.poll() is None:
                subprocess.run(['adb','shell','am','force-stop',PACKAGE],check=True);process.wait(timeout=20)
    verify=subprocess.run(['adb','shell','am','instrument','-w','-e','class',KLASS+'#verifyFreshProcessRollsBackExactDataset',
                           '-e','ansm_restore_death_action','verify',RUNNER],capture_output=True,text=True,timeout=60)
    (folder/('restore-death-'+stage+'-verify.log')).write_text(verify.stdout+verify.stderr)
    assert verify.returncode==0 and 'OK (1 test)' in verify.stdout,'Fresh-process recovery failed'
    result=subprocess.check_output(['adb','shell','run-as',PACKAGE,'cat','files/synthetic-restore-death-result.json'],text=True)
    return json.loads(result)

def main():
    verify_emulator()
    # No retries. Keep original native return code and every original assertion/budget.
    native=subprocess.run(['python3',str(ROOT/'scripts/run_ansm_native_performance.py')],cwd=ROOT)
    cases=[];failed=False
    for stage in ('ROWS_DELETED','ROWS_INSERTED'):
        try:cases.append(death_case(stage))
        except (Exception,AssertionError) as error:
            failed=True;cases.append({'stage':stage,'status':'FAIL','reason':type(error).__name__})
    folder=ROOT/'core/data/build/reports/ansm-native-performance';folder.mkdir(parents=True,exist_ok=True)
    (folder/'restore-process-death.json').write_text(json.dumps({'scope':'SYNTHETIC_API26_ACTUAL_PROCESS_KILL_AFTER_LARGE_BUDGET','cases':cases},indent=2)+'\n')
    return native.returncode or int(failed)

if __name__=='__main__':
    raise SystemExit(main())
