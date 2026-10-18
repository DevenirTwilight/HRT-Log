#!/usr/bin/env python3
"""Run/collect inside the clean synthetic emulator, including failures, before shutdown."""
import json
import re
import threading
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]

def run(execute=subprocess.run):
    test = execute([str(ROOT / 'gradlew'), ':core:data:connectedDebugAndroidTest',
                    '-Pandroid.testInstrumentationRunnerArguments.class=net.plainnotes.app.data.LargeInventoryPerformanceProbe',
                    '-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true',
                    '--no-parallel', '--max-workers=2'], cwd=ROOT)
    collection = execute(['python3', str(ROOT / 'scripts/collect_ansm_native_performance.py')], cwd=ROOT)
    folder = ROOT / 'core/data/build/reports/ansm-native-performance'
    folder.mkdir(parents=True, exist_ok=True)
    (folder / 'execution.json').write_text(json.dumps({'test_exit': test.returncode, 'collection_exit': collection.returncode})+'\n')
    return test.returncode or collection.returncode


def verify_emulator(execute=subprocess.check_output):
    serial = execute(['adb','get-serialno'],text=True).strip()
    qemu = execute(['adb','shell','getprop','ro.kernel.qemu'],text=True).strip()
    sdk = execute(['adb','shell','getprop','ro.build.version.sdk'],text=True).strip()
    if not re.fullmatch(r'emulator-[0-9]+',serial) or qemu!='1' or sdk!='26':
        raise ValueError('Synthetic performance runner requires an isolated API26 emulator')


def diagnostics():
    # This wrapper is used only by the fresh synthetic CI emulator, never the application/user device.
    verify_emulator()
    # Discard stale ready markers only in this verified synthetic emulator, before any timed work.
    subprocess.run(['adb','logcat','-c'],check=True)
    folder = ROOT / 'core/data/build/reports/ansm-native-performance'
    folder.mkdir(parents=True, exist_ok=True)
    properties = {}
    for key in ('dalvik.vm.heapsize', 'dalvik.vm.heapgrowthlimit', 'ro.config.low_ram', 'ro.product.cpu.abi'):
        properties[key] = subprocess.check_output(['adb','shell','getprop',key],text=True).strip()
    memory = subprocess.check_output(['adb','shell','cat','/proc/meminfo'],text=True)
    properties['MemTotal'] = next((line for line in memory.splitlines() if line.startswith('MemTotal:')), 'UNKNOWN')
    (folder/'environment.json').write_text(json.dumps(properties,indent=2)+'\n')
    with (folder/'synthetic-crash-log.txt').open('w') as output:
        capture = subprocess.Popen(['adb','logcat','-v','threadtime','-s','AndroidRuntime:E','art:E','System.err:W','libc:F','DEBUG:F','lowmemorykiller:I','ActivityManager:I','ANSM_PERF:I','ANSM_TRACE:I'],stdout=output,stderr=subprocess.STDOUT)
        from collect_ansm_native_performance import watch_trace
        stop=threading.Event()
        watcher=threading.Thread(target=watch_trace,args=(folder,stop),daemon=True)
        watcher.start()
        try:
            return run()
        finally:
            stop.set();watcher.join(timeout=10)
            capture.terminate()
            try: capture.wait(timeout=5)
            except subprocess.TimeoutExpired: capture.kill();capture.wait()

if __name__ == '__main__':
    raise SystemExit(diagnostics())
