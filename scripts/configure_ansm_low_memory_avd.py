#!/usr/bin/env python3
"""Configure only the ephemeral CI AVD; never an app setting or user device."""
import os
from pathlib import Path

def configure(text):
    values = {'hw.ramSize': '512', 'vm.heapSize': '192'}
    lines = [line for line in text.splitlines() if line.split('=', 1)[0].strip() not in values]
    return '\n'.join(lines + [key+'='+value for key,value in values.items()])+'\n'

if __name__ == '__main__':
    root = Path(os.environ.get('ANDROID_AVD_HOME', str(Path.home()/'.android/avd')))
    file = root / 'test.avd/config.ini'
    if not file.is_file():
        raise SystemExit('Expected freshly created CI AVD config; refusing to guess a different profile')
    file.write_text(configure(file.read_text()))
    print('Configured ephemeral test AVD: hw.ramSize512MiB / vm.heapSize192MiB; runtime values still require verification')
