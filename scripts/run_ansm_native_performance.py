#!/usr/bin/env python3
"""Run/collect inside the clean synthetic emulator, including failures, before shutdown."""
import json
import re
import threading
import tempfile
import hashlib
import uuid
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]

def run(execute=subprocess.run, trace_token=None):
    if trace_token is not None and (not isinstance(trace_token,str) or str(uuid.UUID(trace_token))!=trace_token):
        raise ValueError('Invalid synthetic run token')
    command=[str(ROOT / 'gradlew'), ':core:data:connectedDebugAndroidTest',
             '-Pandroid.testInstrumentationRunnerArguments.class=net.plainnotes.app.data.LargeInventoryPerformanceProbe',
             '-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true',
             '--no-parallel','--max-workers=2']
    collector=['python3', str(ROOT / 'scripts/collect_ansm_native_performance.py')]
    if trace_token is not None:
        command.append('-Pandroid.testInstrumentationRunnerArguments.ansmTraceToken='+trace_token)
        collector.extend(['--run-token',trace_token])
    test = execute(command,cwd=ROOT)
    collection = execute(collector,cwd=ROOT)
    folder = ROOT / 'core/data/build/reports/ansm-native-performance'
    folder.mkdir(parents=True, exist_ok=True)
    (folder / 'execution.json').write_text(json.dumps({'test_exit': test.returncode, 'collection_exit': collection.returncode, 'run_token':trace_token})+'\n')
    return test.returncode or collection.returncode


def verify_emulator(execute=subprocess.check_output):
    serial = execute(['adb','get-serialno'],text=True).strip()
    qemu = execute(['adb','shell','getprop','ro.kernel.qemu'],text=True).strip()
    sdk = execute(['adb','shell','getprop','ro.build.version.sdk'],text=True).strip()
    if not re.fullmatch(r'emulator-[0-9]+',serial) or qemu!='1' or sdk!='26':
        raise ValueError('Synthetic performance runner requires an isolated API26 emulator')



def collect_post_run_kernel(folder, exit_code, execute=subprocess.run, environment=subprocess.check_output):
    """Bounded read-only kernel tail, after the test/collector ended; never a pass or cause classifier."""
    if exit_code is None:
        return {'status':'NOT_RUN_NO_COMPLETED_EXIT'}
    if exit_code == 0:
        return {'status':'NOT_RUN_SUCCESS'}
    report={'status':'UNAVAILABLE','scope':'SYNTHETIC_API26_POST_TEST_ONLY','memory_rss':'NOT_MEASURED'}
    try:
        verify_emulator(environment)  # Never read kernel logs from a physical or unrelated device.
        with tempfile.TemporaryFile() as output, tempfile.TemporaryFile() as errors:
            result=execute(['adb','shell','-T','sh','-c','dmesg | tail -n 80'],
                           stdout=output,stderr=errors,timeout=5)
            size=output.tell();output.seek(max(0,size-65536));data=output.read(65536)
            error_size=errors.tell();errors.seek(max(0,error_size-8192));error=errors.read(8192)
        report.update(command_exit=result.returncode,stdout_bytes=size,clipped=size>65536,
                      stderr_tail=error.decode('utf-8',errors='replace'))
        if result.returncode==0 and data:
            (folder/'post-run-kernel-tail.txt').write_bytes(data)
            report.update(status='CAPTURED_DIAGNOSTIC_ONLY',bytes=len(data),sha256=hashlib.sha256(data).hexdigest())
        else:
            report['reason']='NO_KERNEL_PAYLOAD_OR_COMMAND_DENIED'
    except (OSError,ValueError,subprocess.SubprocessError) as error:
        report['reason']=type(error).__name__
    (folder/'post-run-kernel-diagnostics.json').write_text(json.dumps(report,indent=2)+'\n')
    return report


def diagnostics():
    # This wrapper is used only by the fresh synthetic CI emulator, never the application/user device.
    verify_emulator()
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
        trace_token=str(uuid.uuid4())
        watcher=threading.Thread(target=watch_trace,args=(folder,stop),kwargs={'expected_token':trace_token},daemon=True)
        watcher.start()
        exit_code=None
        try:
            exit_code=run(trace_token=trace_token)
            return exit_code
        finally:
            stop.set();watcher.join(timeout=10)
            capture.terminate()
            try: capture.wait(timeout=5)
            except subprocess.TimeoutExpired: capture.kill();capture.wait()
            collect_post_run_kernel(folder,exit_code)

if __name__ == '__main__':
    raise SystemExit(diagnostics())
