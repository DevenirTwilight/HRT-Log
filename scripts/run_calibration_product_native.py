#!/usr/bin/env python3
"""Real production calibration in two independently started Android processes, synthetic emulator only."""
import subprocess
from run_med02_product_native import ROOT, REPORT, reset

CLASS='net.plainnotes.app.CalibrationProductAndroidTest'
CASES=('savedSublingualHistoryCalibratesTheProductionTotalAndSurvivesReopen','newProcessReopensSavedSublingualCalibration',
    'savedTestosteroneFormulationUsesActualHistoryInTheProductionDestination','newProcessReopensTestosteroneHistoryWithoutApplyingE2Calibration')

def main():
    assert subprocess.check_output(['adb','shell','getprop','ro.kernel.qemu'],text=True).strip()=='1','Synthetic emulator only'
    REPORT.mkdir(parents=True,exist_ok=True)
    report=REPORT/'calibration-product';report.mkdir(exist_ok=True)
    reset('before-calibration-product')
    # Direct instrumentation preserves the saved database between processes. AGP connectedTest may uninstall after a case.
    subprocess.run(['./gradlew',':app:assembleFullDebug',':app:assembleFullDebugAndroidTest','--no-parallel','--max-workers=2'],cwd=ROOT,check=True)
    for path in ('app/build/outputs/apk/full/debug/app-full-debug.apk','app/build/outputs/apk/androidTest/full/debug/app-full-debug-androidTest.apk'):
        subprocess.run(['adb','install','-r','-t',str(ROOT/path)],check=True)
    for case in CASES:
        subprocess.run(['adb','shell','am','force-stop','net.plainnotes.app'],check=True)
        subprocess.run(['adb','shell','am','force-stop','net.plainnotes.app.test'],check=True)
        result=subprocess.run(['adb','shell','am','instrument','-w','-e','class',f'{CLASS}#{case}',
            'net.plainnotes.app.test/androidx.test.runner.AndroidJUnitRunner'],capture_output=True,text=True,timeout=180)
        output=result.stdout+result.stderr;(report/f'{case}.log').write_text(output)
        result.check_returncode()
        assert 'OK (1 test)' in output and 'FAILURES!!!' not in output and 'Process crashed' not in output,output
    receipt=subprocess.check_output(['adb','shell','run-as','net.plainnotes.app','cat','files/calibration-native-reopen.json'],text=True)
    (report/'reopen.json').write_text(receipt)
    import json
    evidence=json.loads(receipt)
    assert evidence['previous_pid']!=evidence['new_pid'] and evidence['calibration_recomputed'] and evidence['real_main_activity']
    assert evidence['saved_doses']==21 and evidence['saved_e2_labs']==2
    t_receipt=subprocess.check_output(['adb','shell','run-as','net.plainnotes.app','cat','files/testosterone-native-reopen.json'],text=True)
    (report/'testosterone-reopen.json').write_text(t_receipt)
    t_evidence=json.loads(t_receipt)
    assert t_evidence['previous_pid']!=t_evidence['new_pid'] and t_evidence['relative_only'] and t_evidence['real_main_activity']
    reset('after-calibration-product')

if __name__=='__main__':main()
