#!/usr/bin/env python3
"""MED-02B6 demo evidence: run the synthetic document-library walkthrough on the emulator while recording the screen,
then pull the real device screenshots (light, then night mode) into app/build/reports/demo/. Emulator only; the demo
holds synthetic fixtures only and never touches the app's records."""
import json
import subprocess
import time
from pathlib import Path

root = Path(__file__).resolve().parent.parent
pkg = 'net.plainnotes.app'
assert subprocess.check_output(['adb', 'shell', 'getprop', 'ro.kernel.qemu'], text=True).strip() == '1', 'Emulator only'
for apk in ['app/build/outputs/apk/full/debug/app-full-debug.apk', 'app/build/outputs/apk/androidTest/full/debug/app-full-debug-androidTest.apk']:
    subprocess.check_call(['adb', 'install', '-r', '-t', str(root / apk)])
out = root / 'app/build/reports/demo'; out.mkdir(parents=True, exist_ok=True)
runner = 'net.plainnotes.app.test/androidx.test.runner.AndroidJUnitRunner'
def instrument(method):
    r = subprocess.run(['adb', 'shell', 'am', 'instrument', '-w', '-e', 'class', 'net.plainnotes.app.demo.DemoArchiveAndroidTest#' + method, runner],
                       stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, timeout=300)
    print(r.stdout, flush=True); assert r.returncode == 0 and 'OK (1 test)' in r.stdout, method
    return r.stdout
subprocess.run(['adb', 'shell', 'run-as', pkg, 'rm', '-rf', 'files/demo-screens'], check=True)
recorder = subprocess.Popen(['adb', 'shell', 'screenrecord', '--time-limit', '90', '/sdcard/demo-walkthrough.mp4'])
time.sleep(1)
try:
    walkthrough = instrument('inboxCategoriesSearchDetailBackAndBin')
finally:
    subprocess.run(['adb', 'shell', 'pkill', '-INT', 'screenrecord']); recorder.wait(timeout=30); time.sleep(2)
video = subprocess.run(['adb', 'pull', '/sdcard/demo-walkthrough.mp4', str(out / 'demo-walkthrough.mp4')])
# PR-B: the full interactive flow, recorded separately.
recorder = subprocess.Popen(['adb', 'shell', 'screenrecord', '--time-limit', '150', '/sdcard/demo-flows.mp4'])
time.sleep(1)
try:
    instrument('fullScriptedFlow')
finally:
    subprocess.run(['adb', 'shell', 'pkill', '-INT', 'screenrecord']); recorder.wait(timeout=30); time.sleep(2)
flows_video = subprocess.run(['adb', 'pull', '/sdcard/demo-flows.mp4', str(out / 'demo-flows.mp4')])
subprocess.run(['adb', 'shell', 'cmd', 'uimode', 'night', 'yes'], check=True)
try:
    time.sleep(2); instrument('overviewScreens')
finally:
    subprocess.run(['adb', 'shell', 'cmd', 'uimode', 'night', 'no'], check=True)
names = subprocess.check_output(['adb', 'shell', 'run-as', pkg, 'ls', 'files/demo-screens'], text=True).split()
for name in names:
    (out / name).write_bytes(subprocess.check_output(['adb', 'exec-out', 'run-as', pkg, 'cat', 'files/demo-screens/' + name]))
pngs = sorted(p.name for p in out.glob('*.png') if p.stat().st_size > 1000)
api = subprocess.check_output(['adb', 'shell', 'getprop', 'ro.build.version.sdk'], text=True).strip()
summary = {'api': api, 'screenshots': pngs, 'video': (out / 'demo-walkthrough.mp4').exists() and video.returncode == 0,
           'flows_video': (out / 'demo-flows.mp4').exists() and flows_video.returncode == 0,
           'note': 'Real emulator captures of synthetic demo data (debug build only). Night-mode shots start with 9.'}
(out / 'demo-evidence.json').write_text(json.dumps(summary, indent=2))
assert len(pngs) >= 18, pngs
print('PASS: demo walkthrough ran on the emulator; screenshots ' + ', '.join(pngs) + ('; video recorded' if summary['video'] else '; video NOT captured'))
