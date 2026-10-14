#!/usr/bin/env python3
"""Force-stop only the standalone synthetic library test APK at a file/SQL commit seam. This is NOT a power-cut test."""
import subprocess
import json
import time
from pathlib import Path

root = Path(__file__).resolve().parent.parent
package = 'net.plainnotes.app.data.test'
assert subprocess.check_output(['adb', 'shell', 'getprop', 'ro.kernel.qemu'], text=True).strip() == '1', 'Emulator only'
subprocess.check_call(['adb', 'install', '-r', '-t', str(root / 'core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk')])
base = ['adb', 'shell', 'am', 'instrument', '-w', '-e', 'class',
        'net.plainnotes.app.data.Gate2RestartAndroidTest#journalAfterInterruptedRealFileAndSqlCommit',
        '-e', 'gate2_phase']
component = package + '/androidx.test.runner.AndroidJUnitRunner'
import sys
seam = sys.argv[1] if len(sys.argv) > 1 else 'IN_COMMIT'
assert seam in ('WRITE', 'IN_COMMIT', 'AFTER_COMMIT')
base += ['prepare', '-e', 'gate2_seam', seam]
subprocess.run(['adb','shell','run-as',package,'rm','-f','files/synthetic-gate2-restart-ready','files/synthetic-gate2-restart-result'],check=True)
process = subprocess.Popen(base + [component], stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
try:
    deadline = time.monotonic() + 60
    while time.monotonic() < deadline:
        marker = subprocess.run(['adb', 'shell', 'run-as', package, 'cat', 'files/synthetic-gate2-restart-ready'],
                                stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, text=True)
        if marker.returncode == 0 and marker.stdout.strip().endswith('}') and json.loads(marker.stdout)['seam']==seam:
            break
        assert process.poll() is None, 'Prepare exited before transaction was blocked'
        time.sleep(0.5)
    else:
        raise AssertionError('No live metadata-transaction marker')
    assert process.poll() is None, 'Prepare must still be alive at force-stop'
    subprocess.check_call(['adb', 'shell', 'am', 'force-stop', package])
    output, _ = process.communicate(timeout=20)
    print(output, flush=True)  # Aborted prepare is intentional, never accepted as an OK test.
    verified = subprocess.run([('verify' if item == 'prepare' else item) for item in base] + [component], stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, timeout=60)
    print(verified.stdout, flush=True)
    assert verified.returncode == 0 and 'OK (1 test)' in verified.stdout
    result=json.loads(subprocess.check_output(['adb','shell','run-as',package,'cat','files/synthetic-gate2-restart-result'],text=True))
    assert result['previous_pid'] != result['fresh_pid'] and result['fresh_process_asserted'] and result['production_enabled']==0
    reports=root/'core/data/build/reports/gate2';reports.mkdir(parents=True,exist_ok=True)
    (reports/('restart-'+seam+'.json')).write_text(json.dumps(result,indent=2))
    (reports/('restart-'+seam+'.txt')).write_text(verified.stdout)
    print('PASS: fresh PID reconciles real SQLCipher + owned ciphertext + Keystore; force-stop is NOT hardware power loss')
finally:
    if process.poll() is None:
        subprocess.run(['adb', 'shell', 'am', 'force-stop', package], check=True)
        process.communicate(timeout=20)
