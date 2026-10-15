#!/usr/bin/env python3
"""MED-02B5 / B3-BACKUP-GATE: force-stop only the standalone synthetic library test APK while a restore is parked at a
seam, then verify in a fresh process. Emulator only. Force-stop is NOT a power cut."""
import json
import subprocess
import sys
import time
from pathlib import Path

root = Path(__file__).resolve().parent.parent
package = 'net.plainnotes.app.data.test'
assert subprocess.check_output(['adb', 'shell', 'getprop', 'ro.kernel.qemu'], text=True).strip() == '1', 'Emulator only'
seam = sys.argv[1] if len(sys.argv) > 1 else 'BEFORE_COMMIT'
assert seam in ('BEFORE_COMMIT', 'AFTER_COMMIT')
subprocess.check_call(['adb', 'install', '-r', '-t', str(root / 'core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk')])
component = package + '/androidx.test.runner.AndroidJUnitRunner'
def instrument(phase):
    return ['adb', 'shell', 'am', 'instrument', '-w', '-e', 'class', 'net.plainnotes.app.data.B3RestartAndroidTest#interruptedRestoreRecoversInAFreshProcess',
            '-e', 'b3_phase', phase, '-e', 'b3_seam', seam, component]
subprocess.run(['adb', 'shell', 'run-as', package, 'rm', '-f', 'files/synthetic-b3-restart-ready', 'files/synthetic-b3-restart-result'], check=True)
process = subprocess.Popen(instrument('prepare'), stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
try:
    deadline = time.monotonic() + 90
    while time.monotonic() < deadline:
        marker = subprocess.run(['adb', 'shell', 'run-as', package, 'cat', 'files/synthetic-b3-restart-ready'], stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, text=True)
        if marker.returncode == 0 and marker.stdout.strip().endswith('}') and json.loads(marker.stdout)['seam'] == seam:
            break
        assert process.poll() is None, 'Prepare exited before reaching the seam'
        time.sleep(0.5)
    else:
        raise AssertionError('Restore never parked at ' + seam)
    assert process.poll() is None, 'Prepare must still be alive at force-stop'
    subprocess.check_call(['adb', 'shell', 'am', 'force-stop', package])
    output, _ = process.communicate(timeout=30)
    print(output, flush=True)  # the killed prepare run is intentional, never counted as a passing test
    verified = subprocess.run(instrument('verify'), stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, timeout=120)
    print(verified.stdout, flush=True)
    assert verified.returncode == 0 and 'OK (1 test)' in verified.stdout, 'Fresh-process verification failed'
    result = json.loads(subprocess.check_output(['adb', 'shell', 'run-as', package, 'cat', 'files/synthetic-b3-restart-result'], text=True))
    assert result['previous_pid'] != result['fresh_pid'] and result['fresh_process_asserted']
    reports = root / 'core/data/build/reports/b3'; reports.mkdir(parents=True, exist_ok=True)
    (reports / ('restart-' + seam + '.json')).write_text(json.dumps(result, indent=2))
    (reports / ('restart-' + seam + '.txt')).write_text(verified.stdout)
    print('PASS: fresh PID reopens the destination through its own alias and reconciles ' + seam + '; force-stop is NOT power loss')
finally:
    if process.poll() is None:
        subprocess.run(['adb', 'shell', 'am', 'force-stop', package], check=True)
        process.communicate(timeout=30)
