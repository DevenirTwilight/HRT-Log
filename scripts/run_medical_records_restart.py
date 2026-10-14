#!/usr/bin/env python3
"""MED-01 new PID and PNBAK1 proof with named synthetic stores; never clear real app data."""
import subprocess
from pathlib import Path
root = Path(__file__).resolve().parent.parent
assert subprocess.check_output(['adb', 'shell', 'getprop', 'ro.kernel.qemu'], text=True).strip() == '1', 'Requires an emulator'
for relative in ['app/build/outputs/apk/full/debug/app-full-debug.apk', 'app/build/outputs/apk/androidTest/full/debug/app-full-debug-androidTest.apk']:
    subprocess.check_call(['adb', 'install', '-r', '-t', str(root / relative)])
for phase in ['prepare', 'verify']:
    if phase == 'verify':
        subprocess.check_call(['adb', 'shell', 'am', 'force-stop', 'net.plainnotes.app'])
    result = subprocess.run(['adb', 'shell', 'am', 'instrument', '-w', '-e', 'class', 'net.plainnotes.app.medical.MedicalRecordsRestartAndroidTest#persistedMedicalRecords', '-e', 'med01_phase', phase, 'net.plainnotes.app.test/androidx.test.runner.AndroidJUnitRunner'], text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    print(result.stdout, flush=True)
    assert result.returncode == 0 and 'OK (1 test)' in result.stdout, f'MED-01 {phase} failed'
print('PASS: MED-01 new PID retains original visit ID/timezone/questions/labs, PNBAK1 and recycle recovery, isolated legacy store')
