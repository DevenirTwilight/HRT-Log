#!/usr/bin/env python3
"""MED-02B6 PR-B: basic demo compatibility on the attachment-poc emulators (API 26/29). Emulator only; installs the
debug APKs already built by the job and runs DemoCompatAndroidTest through am instrument, so earlier connected-test
reports in app/build are left as they are. Synthetic data only."""
import subprocess
from pathlib import Path

root = Path(__file__).resolve().parent.parent
assert subprocess.check_output(['adb', 'shell', 'getprop', 'ro.kernel.qemu'], text=True).strip() == '1', 'Emulator only'
for apk in ['app/build/outputs/apk/full/debug/app-full-debug.apk', 'app/build/outputs/apk/androidTest/full/debug/app-full-debug-androidTest.apk']:
    subprocess.check_call(['adb', 'install', '-r', '-t', str(root / apk)])
api = subprocess.check_output(['adb', 'shell', 'getprop', 'ro.build.version.sdk'], text=True).strip()
r = subprocess.run(['adb', 'shell', 'am', 'instrument', '-w', '-e', 'class', 'net.plainnotes.app.demo.DemoCompatAndroidTest',
                    'net.plainnotes.app.test/androidx.test.runner.AndroidJUnitRunner'], stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, timeout=300)
out = root / 'app/build/reports/demo'; out.mkdir(parents=True, exist_ok=True)
(out / f'demo-compat-api{api}.txt').write_text(r.stdout)
print(r.stdout, flush=True)
assert r.returncode == 0 and 'OK (1 test)' in r.stdout, 'DemoCompatAndroidTest failed on API ' + api
print(f'PASS: demo compatibility on API {api}')
