#!/usr/bin/env python3
"""Bound the disposable API26 CI emulator's real ART heap; refuses physical devices."""
import argparse
import subprocess
import time


def configure(execute=subprocess.check_output, pause=time.sleep, clock=time.monotonic, expected_api="26"):
    if expected_api not in ("26", "27", "29", "35"):
        raise ValueError("Only the explicit synthetic CI API matrix is allowed")
    def adb(*args):
        return execute(['adb', *args], text=True).strip()
    serial = adb('get-serialno')
    if not serial.startswith('emulator-') or adb('shell', 'getprop', 'ro.kernel.qemu') != '1' or adb('shell', 'getprop', 'ro.build.version.sdk') != expected_api:
        raise RuntimeError('Refusing runtime configuration outside the explicitly selected disposable CI emulator')
    root_error = None
    try:
        adb('root')
    except subprocess.CalledProcessError as error:
        # adbd can close its transport while restarting as root. Command acknowledgement
        # is not proof of daemon identity: observe the reconnected device below.
        root_error = error
        print('adb root did not acknowledge completion; verifying the reconnected emulator', flush=True)
    execute(['adb', 'wait-for-device'], text=True, timeout=30)
    if adb('get-serialno') != serial:
        raise RuntimeError('Disposable CI emulator identity changed during adbd reconnect') from root_error
    if adb('shell', 'id', '-u') != '0':
        raise RuntimeError('Disposable CI emulator must support adb root; no production-device fallback') from root_error
    # The emulator can override vm.heapSize in the AVD profile. Change the actual
    # runtime properties and restart the synthetic framework before any test installation.
    adb('shell', 'setprop', 'dalvik.vm.heapsize', '192m')
    adb('shell', 'setprop', 'dalvik.vm.heapgrowthlimit', '192m')
    adb('shell', 'setprop', 'sys.boot_completed', '0')
    adb('shell', 'stop')
    adb('shell', 'start')
    until = clock() + 120
    while adb('shell', 'getprop', 'sys.boot_completed') != '1':
        if clock() >= until:
            raise RuntimeError('Synthetic framework did not restart within 120 seconds')
        pause(2)
    for key in ('dalvik.vm.heapsize', 'dalvik.vm.heapgrowthlimit'):
        if adb('shell', 'getprop', key) != '192m':
            raise RuntimeError('Actual ART heap configuration did not take effect: ' + key)
    print('Synthetic API'+expected_api+' emulator ART heap properties verified192m; test Runtime.maxMemory and measured budgets still required')


if __name__ == '__main__':
    parser=argparse.ArgumentParser()
    parser.add_argument("--api",choices=("26","27","29","35"),default="26")
    configure(expected_api=parser.parse_args().api)
