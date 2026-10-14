#!/usr/bin/env python3
"""R15 correction and PNBAK1 across new app PIDs, on synthetic emulator fixtures only; never clear app data."""
import subprocess
from pathlib import Path
root = Path(__file__).resolve().parent.parent
qemu = subprocess.check_output(['adb', 'shell', 'getprop', 'ro.kernel.qemu'], text=True).strip()
assert qemu == '1', 'R15 restart validation requires an emulator, not a real device'
runner = 'net.plainnotes.app.test/androidx.test.runner.AndroidJUnitRunner'
klass = 'net.plainnotes.app.stock.UndoSkippedRestartAndroidTest#persistedCorrection'
for relative in ['app/build/outputs/apk/full/debug/app-full-debug.apk',
                 'app/build/outputs/apk/androidTest/full/debug/app-full-debug-androidTest.apk']:
    subprocess.check_call(['adb', 'install', '-r', '-t', str(root / relative)])
def phase(value):
    result = subprocess.run(['adb', 'shell', 'am', 'instrument', '-w', '-e', 'class', klass,
                             '-e', 'r15_restart_phase', value, runner], text=True,
                            stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    print(result.stdout, flush=True)
    assert result.returncode == 0 and 'OK (1 test)' in result.stdout, f'R15 restart phase {value} failed'
phase('prepare')
subprocess.check_call(['adb', 'shell', 'am', 'force-stop', 'net.plainnotes.app'])
phase('verify')
print('PASS: R15 undo persists in a new PID, PNBAK1 retains evidence, real intake deducts once, VOIDED stays excluded')
