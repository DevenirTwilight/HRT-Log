#!/usr/bin/env python3
"""Force-stop a running synthetic encrypted import; never pm clear or touch real user databases."""
import subprocess
import time
from pathlib import Path

root = Path(__file__).resolve().parent.parent
assert subprocess.check_output(['adb', 'shell', 'getprop', 'ro.kernel.qemu'], text=True).strip() == '1', 'Emulator only'
for relative in ['app/build/outputs/apk/full/debug/app-full-debug.apk', 'app/build/outputs/apk/androidTest/full/debug/app-full-debug-androidTest.apk']:
    subprocess.check_call(['adb', 'install', '-r', '-t', str(root / relative)])
base = ['adb', 'shell', 'am', 'instrument', '-w', '-e', 'class', 'net.plainnotes.app.attachmentpoc.AttachmentRestartAndroidTest#interruptedSyntheticImport', '-e', 'med02a_phase']
component = 'net.plainnotes.app.test/androidx.test.runner.AndroidJUnitRunner'
process = subprocess.Popen(base + ['prepare', component], stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
try:
    deadline = time.monotonic() + 60
    while time.monotonic() < deadline:
        state = subprocess.run(['adb', 'shell', 'run-as', 'net.plainnotes.app', 'cat', 'files/synthetic-med02a-restart/ready'], stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, text=True)
        if state.returncode == 0 and state.stdout.strip().isdigit():
            break
        assert process.poll() is None, 'Prepare terminated before a live import was blocked'
        time.sleep(0.5)
    else:
        raise AssertionError('No live interrupted-import marker')
    assert process.poll() is None, 'Prepare must still be running during force-stop'
    subprocess.check_call(['adb', 'shell', 'am', 'force-stop', 'net.plainnotes.app'])
    output, _ = process.communicate(timeout=20)
    print(output, flush=True)  # Aborted instrumentation here is intentional; prepare must never claim OK.
    verified = subprocess.run(base + ['verify', component], stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    print(verified.stdout, flush=True)
    assert verified.returncode == 0 and 'OK (1 test)' in verified.stdout
    print('PASS: live encrypted import force-stopped; fresh PID rejects/cleans incomplete cipher only, keeps prior object and sentinel')
finally:
    if process.poll() is None:
        subprocess.run(['adb', 'shell', 'am', 'force-stop', 'net.plainnotes.app'], check=True)
        process.communicate(timeout=20)
