#!/usr/bin/env python3
"""MED-02B5 / B3-BACKUP-GATE red controls, each followed by the same assertion on the fixed path.

1. b3_red=manifest: the final-manifest transcript check is disabled, so a bundle whose object frames were reordered
   after the manifest is accepted -> expected 1 failure; with the check -> MANIFEST_MISMATCH, 1 pass.
2. b3_red=cas: the commit's compare-and-swap is disabled, so a restore overwrites a CURRENT that changed under it
   -> expected 1 failure; with the CAS -> CAS_STALE, 1 pass.
Emulator-only standalone library test package; synthetic data only.
"""
import json
import subprocess
from pathlib import Path

root = Path(__file__).resolve().parent.parent
assert subprocess.check_output(['adb', 'shell', 'getprop', 'ro.kernel.qemu'], text=True).strip() == '1', 'Emulator only'
subprocess.check_call(['adb', 'install', '-r', '-t', str(root / 'core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk')])
runner = 'net.plainnotes.app.data.test/androidx.test.runner.AndroidJUnitRunner'
CONTROLS = [
    ('net.plainnotes.app.data.B3GateAndroidTest#reorderedFramesAreRejectedByTheFinalManifest', 'manifest',
     'Reordered bundle was accepted without manifest verification'),
    ('net.plainnotes.app.data.B3GateAndroidTest#staleCasAbortsAndKeepsTheOldCurrent', 'cas', 'Stale CAS committed'),
]
out = root / 'core/data/build/reports/b3'
out.mkdir(parents=True, exist_ok=True)
summary = []
for test, flag, message in CONTROLS:
    base = ['adb', 'shell', 'am', 'instrument', '-w', '-e', 'class', test]
    red = subprocess.run(base + ['-e', 'b3_red', flag, runner], stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, timeout=300)
    assert message in red.stdout and 'Failures: 1' in red.stdout, red.stdout
    green = subprocess.run(base + [runner], stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, timeout=300)
    assert green.returncode == 0 and 'OK (1 test)' in green.stdout, green.stdout
    name = test.split('#')[1]
    (out / f'{name}-red.txt').write_text(red.stdout)
    (out / f'{name}-green.txt').write_text(green.stdout)
    summary.append({'test': test, 'red': 'expected 1 failure: ' + message, 'green': 'OK (1 test)'})
(out / 'red-green.json').write_text(json.dumps(summary, indent=2))
evidence = subprocess.check_output(['adb', 'logcat', '-d', '-s', 'B3_GATE:I'], text=True)
(out / 'b3-red-evidence.txt').write_text(evidence)
print(evidence, flush=True)
print('PASS: both B3 red controls failed as expected; fixed paths pass the same assertions')
