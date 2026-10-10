#!/usr/bin/env python3
"""P1 release acceptance orchestrator for one scenario on one disposable emulator.

Only ever targets an emulator (ro.kernel.qemu / ro.boot.qemu == 1); it uninstalls and installs
net.plainnotes.app there, which is only acceptable on a throwaway device holding synthetic data.
Every phase records its command, exit code, parsed per-test results and logcat crash markers in
<out>/result.json; nothing is inferred - a phase that cannot run is recorded as BLOCKED or NOT_RUN.
"""
import argparse
import hashlib
import json
import re
import subprocess
import time
from pathlib import Path

PKG = 'net.plainnotes.app'
TEST_PKG = 'net.plainnotes.app.test'
RUNNER = f'{TEST_PKG}/androidx.test.runner.AndroidJUnitRunner'
MEDIA = f'/sdcard/Android/media/{PKG}/p1-acceptance'
CRASH = re.compile(r'FATAL EXCEPTION|Resources\$NotFoundException|UnsatisfiedLinkError|ANR in ' + re.escape(PKG) + r'|Process: ' + re.escape(PKG))


def adb(*args, timeout=900, check=False):
    p = subprocess.run(['adb', *args], text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=timeout)
    if check and p.returncode: raise RuntimeError(f'adb {" ".join(args)} -> {p.returncode}\n{p.stdout}')
    return p


def prop(name): return adb('shell', 'getprop', name).stdout.strip()


def sha(path): return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def device_identity():
    keys = ['ro.product.cpu.abi', 'ro.product.cpu.abilist', 'ro.build.version.sdk', 'ro.build.version.release', 'ro.build.fingerprint',
            'ro.kernel.qemu', 'ro.boot.qemu', 'ro.dalvik.vm.native.bridge', 'ro.product.model', 'ro.hardware']
    info = {k: prop(k) for k in keys}
    info['uname_m'] = adb('shell', 'uname', '-m').stdout.strip()
    return info


def parse_instrument(text):
    tests, current, results = [], {}, {'0': 'PASS', '-2': 'FAIL', '-1': 'ERROR', '-3': 'IGNORED', '-4': 'ASSUMPTION_SKIPPED'}
    for line in text.splitlines():
        m = re.match(r'INSTRUMENTATION_STATUS: (\w+)=(.*)', line)
        if m: current[m.group(1)] = m.group(2); continue
        m = re.match(r'INSTRUMENTATION_STATUS_CODE: (-?\d+)', line)
        if m:
            code = m.group(1)
            if code != '1' and 'test' in current:
                tests.append({'class': current.get('class'), 'test': current.get('test'), 'result': results.get(code, f'CODE_{code}'),
                              'stack': (current.get('stack') or '')[:4000] or None})
            current = {} if code != '1' else current
    counts = {}
    for t in tests: counts[t['result']] = counts.get(t['result'], 0) + 1
    ok = bool(re.search(r'^OK \(\d+ tests?\)', text, re.M))
    return tests, counts, ok


class Run:
    def __init__(self, out, scenario):
        self.out = Path(out); self.out.mkdir(parents=True, exist_ok=True)
        self.result = {'scenario': scenario, 'phases': [], 'started': time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime())}

    def save(self): (self.out / 'result.json').write_text(json.dumps(self.result, indent=1, ensure_ascii=False))

    def phase(self, name, status, **extra):
        entry = {'name': name, 'status': status, **extra}
        self.result['phases'].append(entry); self.save(); print(f'[{status}] {name}', flush=True)
        return entry

    def logcat_crashes(self, label):
        text = adb('logcat', '-d', '-v', 'threadtime').stdout
        (self.out / f'logcat-{label}.txt').write_text(text)
        hits = [l for l in text.splitlines() if CRASH.search(l) and (PKG in l or 'FATAL' in l or 'NotFound' in l)]
        adb('logcat', '-c')
        return hits[:50]

    def instrument(self, name, args, expect_ok=True):
        cmd = ['shell', 'am', 'instrument', '-w', '-r', '-e', 'hrtDisposableDevice', 'yes', *args, RUNNER]
        p = adb(*cmd, timeout=3600)
        (self.out / f'instrument-{name}.txt').write_text(p.stdout)
        tests, counts, ok = parse_instrument(p.stdout)
        crashes = self.logcat_crashes(name)
        bad = counts.get('FAIL', 0) + counts.get('ERROR', 0)
        status = 'PASS' if (ok and bad == 0 and p.returncode == 0 and tests) else 'FAIL'
        if not expect_ok: status = 'INFO'
        return self.phase(name, status, command='adb ' + ' '.join(cmd), exit_code=p.returncode, runner_ok=ok, counts=counts,
                          tests=tests, logcat_crash_lines=crashes)

    def pull_evidence(self, label):
        dest = self.out / f'evidence-{label}'; dest.mkdir(exist_ok=True)
        p = adb('pull', MEDIA, str(dest))
        adb('shell', 'rm', '-rf', MEDIA)
        return p.returncode


def wait_boot():
    adb('wait-for-device', timeout=600)
    deadline = time.time() + 600
    while time.time() < deadline:
        if prop('sys.boot_completed') == '1': break
        time.sleep(5)
    else: raise RuntimeError('emulator did not finish booting')
    adb('shell', 'input', 'keyevent', '82'); time.sleep(10)


def install(run, label, apk, expect_success=True):
    p = adb('install', '-r', '-t', '-g', str(apk), timeout=600)
    ok = p.returncode == 0 and 'Success' in p.stdout
    if expect_success: run.phase(f'install-{label}', 'PASS' if ok else 'FAIL', command=f'adb install -r -t -g {apk}', exit_code=p.returncode, output=p.stdout[-2000:], apk_sha256=sha(apk))
    return ok, p


def resumed():
    text = adb('shell', 'dumpsys', 'activity', 'activities').stdout
    return [l.strip() for l in text.splitlines() if 'ResumedActivity' in l or 'topResumedActivity' in l]


def cold_restarts(run, label, entry, count=3):
    attempts = []
    for i in range(count):
        adb('shell', 'am', 'force-stop', PKG); time.sleep(2)
        p = adb('shell', 'am', 'start', '-W', '-n', entry)
        time.sleep(6)
        pid = adb('shell', 'pidof', PKG).stdout.strip()
        top = resumed()
        attempts.append({'exit_code': p.returncode, 'am_start': p.stdout.strip()[-600:], 'pid': pid, 'resumed': top})
    crashes = run.logcat_crashes(f'restarts-{label}')
    ok = all(a['exit_code'] == 0 and a['pid'] and 'Status: ok' in a['am_start'] and any(PKG in r for r in a['resumed']) for a in attempts) and not crashes
    adb('shell', 'input', 'keyevent', '3'); adb('shell', 'am', 'force-stop', PKG)
    return run.phase(f'cold-restarts-{label}', 'PASS' if ok else 'FAIL', entry=entry, attempts=attempts, logcat_crash_lines=crashes)


def main():
    a = argparse.ArgumentParser()
    a.add_argument('--scenario', required=True, choices='ABCD')
    a.add_argument('--exact-app', required=True); a.add_argument('--exact-test', required=True)
    a.add_argument('--functional-app'); a.add_argument('--functional-test')
    a.add_argument('--out', required=True)
    a.add_argument('--skip-reboot', action='store_true')
    args = a.parse_args()
    run = Run(args.out, args.scenario)
    adb('wait-for-device', timeout=600)
    dev = device_identity(); run.result['device'] = dev
    run.result['apks'] = {k: {'path': v, 'sha256': sha(v), 'bytes': Path(v).stat().st_size} for k, v in
                          {'exact_app': args.exact_app, 'exact_test': args.exact_test, 'functional_app': args.functional_app,
                           'functional_test': args.functional_test}.items() if v}
    if '1' not in (dev['ro.kernel.qemu'], dev['ro.boot.qemu']):
        run.phase('device-guard', 'BLOCKED', reason='Not an emulator; refusing to install or uninstall anything.'); return 2
    abis = dev['ro.product.cpu.abilist'].split(',')
    arm64_only = args.scenario in 'CD'
    run.result['abi_note'] = ('ARM64-only package on an ARM64 device' if arm64_only and 'arm64-v8a' in abis and dev['uname_m'] == 'aarch64'
                              else 'ARM64-only package on a non-ARM64 device' if arm64_only else 'universal package')
    for pkg in (TEST_PKG, PKG): adb('uninstall', pkg)
    adb('logcat', '-c')

    if arm64_only and 'arm64-v8a' not in abis:
        ok, p = install(run, 'exact-app', args.exact_app, expect_success=False)
        refused = not ok and 'INSTALL_FAILED_NO_MATCHING_ABIS' in p.stdout
        run.phase('arm64-package-refused-on-non-arm64-device', 'PASS' if refused else 'FAIL', command=f'adb install -r -t -g {args.exact_app}',
                  exit_code=p.returncode, output=p.stdout[-2000:], expectation='Package Manager refuses an arm64-only APK on a device without arm64-v8a')
        run.phase('runtime-on-arm64', 'BLOCKED', reason=f'Device ABIs {abis}; no ARM64 runtime available in this job.')
        run.result['finished'] = time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime()); run.save(); return 0 if refused else 1
    if arm64_only and dev['uname_m'] != 'aarch64':
        run.phase('runtime-on-arm64', 'BLOCKED', reason=f'arm64-v8a only offered through native bridge ({dev["ro.dalvik.vm.native.bridge"]}) on {dev["uname_m"]}; not real ARM64.')
        run.save(); return 0

    # ---- exact: production R8 output of this scenario ----
    if not install(run, 'exact-app', args.exact_app)[0] or not install(run, 'exact-test', args.exact_test)[0]: run.save(); return 1
    run.instrument('exact-runtime', ['-e', 'class', 'net.plainnotes.app.releaseacceptance.ReleaseRuntimeAcceptanceTest'])
    run.pull_evidence('exact')
    cold_restarts(run, 'exact-main-launcher', f'{PKG}/.Launcher')
    if args.functional_app:
        for pkg in (TEST_PKG, PKG): adb('uninstall', pkg)
        if not install(run, 'functional-app', args.functional_app)[0] or not install(run, 'functional-test', args.functional_test)[0]: run.save(); return 1
        # Whole instrumentation set: existing androidTest + androidTestFull suites, ported JVM suites (PeriodStability etc.),
        # the release acceptance classes. Restart phases skip themselves without their argument; they run below.
        run.instrument('functional-all', ['-e', 'hrtNotificationPhase', 'granted'])
        run.pull_evidence('functional')
        # Disguise/private notes across a real process death (same orchestration as scripts/run_disguise_restart.py).
        klass = 'net.plainnotes.app.disguise.ProcessRestartAndroidTest'
        run.instrument('process-restart-prepare', ['-e', 'class', f'{klass}#prepare', '-e', 'restart_phase', 'prepare'])
        adb('shell', 'am', 'force-stop', PKG)
        run.instrument('process-restart-verify', ['-e', 'class', f'{klass}#verifyFreshProcess', '-e', 'restart_phase', 'verify'])
        # Notification permission denied: revoking kills the app process, so this is a separate instrumentation run.
        adb('shell', 'pm', 'revoke', PKG, 'android.permission.POST_NOTIFICATIONS')
        run.instrument('reminder-permission-denied', ['-e', 'class', 'net.plainnotes.app.releaseacceptance.ReleaseFunctionalAcceptanceTest#reminderIsScheduledAndPostedWhenNotificationsAreAllowed',
                                                      '-e', 'hrtNotificationPhase', 'denied'])
        adb('shell', 'pm', 'grant', PKG, 'android.permission.POST_NOTIFICATIONS')
        run.pull_evidence('reminder-denied')
        cold_restarts(run, 'functional-main-launcher', f'{PKG}/.Launcher')
        if args.skip_reboot: run.phase('reboot-reminder-recovery', 'NOT_RUN', reason='--skip-reboot')
        else:
            run.instrument('reboot-prepare', ['-e', 'class', 'net.plainnotes.app.releaseacceptance.ReminderRebootAcceptanceTest#scheduleBeforeReboot', '-e', 'hrtRebootPhase', 'prepare'])
            adb('shell', 'am', 'force-stop', PKG)
            adb('reboot', timeout=120); time.sleep(20); wait_boot()
            deadline, seen = time.time() + 240, False
            while time.time() < deadline and not seen:
                seen = bool(re.search(rf'pkg={re.escape(PKG)}\b.*id=100\b|NotificationRecord\(.*pkg={re.escape(PKG)}.*id=100', adb('shell', 'dumpsys', 'notification', '--noredact').stdout))
                if not seen: time.sleep(10)
            alarms = [l.strip() for l in adb('shell', 'dumpsys', 'alarm').stdout.splitlines() if PKG in l][:20]
            crashes = run.logcat_crashes('after-reboot')
            run.phase('reboot-reminder-recovery', 'PASS' if seen and not crashes else 'FAIL', notification_after_reboot=seen, alarm_lines=alarms,
                      logcat_crash_lines=crashes, note='App not opened after reboot; SystemReceiver must restore the cached alarm and AlarmReceiver post it.')
    run.result['finished'] = time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime())
    run.result['status'] = 'PASS' if all(p['status'] in ('PASS', 'INFO') for p in run.result['phases']) else \
        'FAIL' if any(p['status'] == 'FAIL' for p in run.result['phases']) else 'PARTIAL'
    run.save()
    return 0 if run.result['status'] == 'PASS' else 1


if __name__ == '__main__':
    raise SystemExit(main())
