#!/usr/bin/env python3
"""Run/collect inside the clean synthetic emulator, including failures, before shutdown."""
import json
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]

def run(execute=subprocess.run):
    test = execute([str(ROOT / 'gradlew'), ':core:data:connectedDebugAndroidTest',
                    '-Pandroid.testInstrumentationRunnerArguments.class=net.plainnotes.app.data.LargeInventoryPerformanceProbe',
                    '--no-parallel', '--max-workers=2'], cwd=ROOT)
    collection = execute(['python3', str(ROOT / 'scripts/collect_ansm_native_performance.py')], cwd=ROOT)
    folder = ROOT / 'core/data/build/reports/ansm-native-performance'
    folder.mkdir(parents=True, exist_ok=True)
    (folder / 'execution.json').write_text(json.dumps({'test_exit': test.returncode, 'collection_exit': collection.returncode})+'\n')
    return test.returncode or collection.returncode


def diagnostics():
    # This wrapper is used only by the fresh synthetic CI emulator, never the application/user device.
    folder = ROOT / 'core/data/build/reports/ansm-native-performance'
    folder.mkdir(parents=True, exist_ok=True)
    properties = {}
    for key in ('dalvik.vm.heapsize', 'dalvik.vm.heapgrowthlimit', 'ro.config.low_ram', 'ro.product.cpu.abi'):
        properties[key] = subprocess.check_output(['adb','shell','getprop',key],text=True).strip()
    memory = subprocess.check_output(['adb','shell','cat','/proc/meminfo'],text=True)
    properties['MemTotal'] = next((line for line in memory.splitlines() if line.startswith('MemTotal:')), 'UNKNOWN')
    (folder/'environment.json').write_text(json.dumps(properties,indent=2)+'\n')
    with (folder/'synthetic-crash-log.txt').open('w') as output:
        capture = subprocess.Popen(['adb','logcat','-v','threadtime','-s','AndroidRuntime:E','libc:F','DEBUG:F','lowmemorykiller:I','ActivityManager:W'],stdout=output,stderr=subprocess.STDOUT)
        try:
            return run()
        finally:
            capture.terminate()
            try: capture.wait(timeout=5)
            except subprocess.TimeoutExpired: capture.kill();capture.wait()

if __name__ == '__main__':
    raise SystemExit(diagnostics())
