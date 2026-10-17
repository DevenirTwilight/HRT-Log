#!/usr/bin/env python3
"""Bound the disposable API26 CI emulator's real ART heap; refuses physical devices."""
import subprocess
import time


def configure(execute=subprocess.check_output, pause=time.sleep, clock=time.monotonic):
    def adb(*args):
        return execute(['adb', *args], text=True).strip()
    serial = adb('get-serialno')
    if not serial.startswith('emulator-') or adb('shell', 'getprop', 'ro.kernel.qemu') != '1' or adb('shell', 'getprop', 'ro.build.version.sdk') != '26':
        raise RuntimeError('Refusing runtime configuration outside the disposable API26 emulator')
    adb('root')
    adb('wait-for-device')
    if adb('shell', 'id', '-u') != '0':
        raise RuntimeError('Disposable CI emulator must support adb root; no production-device fallback')
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
    print('Synthetic API26 emulator ART heap properties verified192m; test Runtime.maxMemory and measured budgets still required')


if __name__ == '__main__':
    configure()
