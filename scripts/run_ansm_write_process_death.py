#!/usr/bin/env python3
"""Two small synthetic real-writer SIGTERM/force-stop contracts. No workload or budget substitution."""
import argparse
import json
from pathlib import Path
import subprocess
import time

ROOT=Path(__file__).resolve().parents[1]
PACKAGE='net.plainnotes.app.data.test'
RUNNER=PACKAGE+'/androidx.test.runner.AndroidJUnitRunner'
KLASS='net.plainnotes.app.data.MeasuredSaveDeathProbe'

def capture_failure(folder,stage):
    """One failure snapshot before force-stop, never a retry or a longer readiness budget."""
    commands={
        'stages':['adb','shell','run-as',PACKAGE,'cat','files/synthetic-write-death-stages.jsonl'],
        'meminfo':['adb','shell','dumpsys','meminfo',PACKAGE],
        'logcat':['adb','logcat','-d','-v','threadtime','-t','2000'],
    }
    for label,cmd in commands.items():
        try:
            result=subprocess.run(cmd,capture_output=True,text=True,timeout=15)
            (folder/f'write-death-{stage}-{label}.log').write_text(result.stdout+result.stderr)
        except Exception as error:
            (folder/f'write-death-{stage}-{label}.log').write_text(type(error).__name__+'\n')

def death_case(stage):
    subprocess.run(['adb','shell','run-as',PACKAGE,'rm','-f','files/synthetic-write-death-ready.json','files/synthetic-write-death-result.json','files/synthetic-write-death-stages.jsonl'],check=True)
    cmd=['adb','shell','am','instrument','-w','-e','class',KLASS+'#prepareAndWaitForExternalKill',
         '-e','ansm_write_death_action','prepare','-e','ansm_write_death_stage',stage,RUNNER]
    folder=ROOT/'core/data/build/reports/ansm-write-process-death'
    with (folder/('write-death-'+stage+'-prepare.log')).open('w') as log:
        process=subprocess.Popen(cmd,stdout=log,stderr=subprocess.STDOUT)
        try:
            end=time.monotonic()+60
            while time.monotonic()<end:
                ready=subprocess.run(['adb','shell','run-as',PACKAGE,'cat','files/synthetic-write-death-ready.json'],capture_output=True,text=True)
                if ready.returncode==0 and ready.stdout.strip():
                    try:marker=json.loads(ready.stdout)
                    except ValueError:
                        time.sleep(.2);continue  # Same attempt: a host read may race writeText.
                    assert marker['stage']==stage;break
                assert process.poll() is None,'Prepare ended before pending transaction'
                time.sleep(.2)
            else:
                raise AssertionError('No pending writer checkpoint within original 60 seconds')
            alive=subprocess.run(['adb','shell','pidof',PACKAGE],capture_output=True,text=True)
            assert str(marker['pid']) in alive.stdout.split(),'Process died before the deliberate kill'
            subprocess.run(['adb','shell','am','force-stop',PACKAGE],check=True)
            process.wait(timeout=20)
            live=subprocess.run(['adb','shell','pidof',PACKAGE],capture_output=True,text=True)
            assert str(marker['pid']) not in live.stdout.split(),'Prepared process still alive'
        except BaseException:
            capture_failure(folder,stage)
            raise
        finally:
            if process.poll() is None:
                subprocess.run(['adb','shell','am','force-stop',PACKAGE],check=True);process.wait(timeout=20)
    verify=subprocess.run(['adb','shell','am','instrument','-w','-e','class',KLASS+'#verifyFreshProcessHasExactDatasetAndNoAutomaticReplay',
                           '-e','ansm_write_death_action','verify',RUNNER],capture_output=True,text=True,timeout=60)
    (folder/('write-death-'+stage+'-verify.log')).write_text(verify.stdout+verify.stderr)
    assert verify.returncode==0 and 'OK (1 test)' in verify.stdout,'Fresh-process recovery failed'
    result=subprocess.check_output(['adb','shell','run-as',PACKAGE,'cat','files/synthetic-write-death-result.json'],text=True)
    return json.loads(result)


def main(selected_stage=None):
    folder=ROOT/'core/data/build/reports/ansm-write-process-death'
    folder.mkdir(parents=True,exist_ok=True)
    apks=list((ROOT/'core/data/build/outputs/apk/androidTest/debug').glob('*androidTest.apk'))
    assert len(apks)==1,'Expected the synthetic data test APK only'
    subprocess.run(['adb','install','-r','-t',str(apks[0])],check=True)
    cases=[];failed=False
    stages=(selected_stage,) if selected_stage else ('STOCK_CAPTURED','AFTER_COMMIT')
    for stage in stages:
        try:cases.append(death_case(stage))
        except (Exception,AssertionError) as error:
            failed=True;cases.append({'stage':stage,'status':'FAIL','reason':type(error).__name__+': '+str(error)})
    (folder/'write-process-death.json').write_text(json.dumps({'scope':'SYNTHETIC_ACTUAL_WRITER_PROCESS_KILL',
        'performance':'NOT_MEASURED_NOT_A_REPLACEMENT','selected_stages':list(stages),'retries':0,'cases':cases},indent=2)+'\n')
    return int(failed)

if __name__=='__main__':
    parser=argparse.ArgumentParser()
    parser.add_argument('--stage',choices=['STOCK_CAPTURED','AFTER_COMMIT'])
    raise SystemExit(main(parser.parse_args().stage))
