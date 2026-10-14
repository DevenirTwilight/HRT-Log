#!/usr/bin/env python3
"""B2-GATE3 deterministic red controls, each followed by the same assertion on the fixed path.

1. Same-process maintenance probe that opens and closes a second channel (the pre-GATE3 lease code) must let a
   second real process acquire a live writer's fcntl lease -> expected 1 failure; registry path -> 1 pass.
2. Kernel ProxyFD lease bound to an unrelated capability must deliver plaintext after revocation -> expected
   1 failure; owner-bound lease -> 1 pass.
Emulator-only standalone test packages; never pm clear, never the user's app data. Not a production-defect claim
for (2); (1) records a defect found and fixed in the test harness itself.
"""
import json
import subprocess
from pathlib import Path

root = Path(__file__).resolve().parent.parent
assert subprocess.check_output(['adb', 'shell', 'getprop', 'ro.kernel.qemu'], text=True).strip() == '1', 'Emulator only'
for apk in ['core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk',
            'app/build/outputs/apk/full/debug/app-full-debug.apk',
            'app/build/outputs/apk/androidTest/full/debug/app-full-debug-androidTest.apk']:
    subprocess.check_call(['adb', 'install', '-r', '-t', str(root / apk)])
CONTROLS = [
    ('net.plainnotes.app.data.test/androidx.test.runner.AndroidJUnitRunner',
     'net.plainnotes.app.data.Gate3ProcessLockAndroidTest#sameProcessMaintenanceProbeNeverDropsALiveWritersOsLock',
     'gate3_red', "Peer process could acquire a live writer's lease after a same-process probe"),
    ('net.plainnotes.app.test/androidx.test.runner.AndroidJUnitRunner',
     'net.plainnotes.app.attachmentpoc.KernelProxyFdGate3AndroidTest#kernelReadsReachCallbackAndRevocationStopsNewPlaintext',
     'gate3_kernel_red', 'Revoked kernel read delivered new plaintext'),
]
out = root / 'core/data/build/reports/gate3'
out.mkdir(parents=True, exist_ok=True)
summary = []
for runner, test, flag, message in CONTROLS:
    base = ['adb', 'shell', 'am', 'instrument', '-w', '-e', 'class', test]
    red = subprocess.run(base + ['-e', flag, 'true', runner], stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, timeout=180)
    assert message in red.stdout and 'Failures: 1' in red.stdout, red.stdout
    green = subprocess.run(base + [runner], stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, timeout=180)
    assert green.returncode == 0 and 'OK (1 test)' in green.stdout, green.stdout
    name = test.split('#')[1]
    (out / f'{name}-red.txt').write_text(red.stdout)
    (out / f'{name}-green.txt').write_text(green.stdout)
    summary.append({'test': test, 'red': 'expected 1 failure: ' + message, 'green': 'OK (1 test)'})
(out / 'red-green.json').write_text(json.dumps(summary, indent=2))
evidence = subprocess.check_output(['adb', 'logcat', '-d', '-s', 'GATE3_PROCESS:I', 'GATE3_KERNEL:I'], text=True)
(out / 'gate3-evidence.txt').write_text(evidence)
print(evidence, flush=True)
print('PASS: both GATE3 red controls failed as expected; fixed paths pass the same assertions')
