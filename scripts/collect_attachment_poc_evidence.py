#!/usr/bin/env python3
"""Collect actual emulator observations, never substitute proposed quotas for measured heap/PSS."""
import argparse
import json
import re
import subprocess
from pathlib import Path


def parse(text):
    rows = []
    for line in text.splitlines():
        if 'MED02APOC' not in line:
            continue
        fields = dict(re.findall(r'(\w+)=([^\s]+)', line))
        if not fields:
            continue
        kind = 'large_pdf' if 'large_pdf ' in line else 'image' if 'image ' in line else 'restart' if 'restart ' in line else 'pdf'
        rows.append({'kind': kind, **{k: int(v) if v.isdigit() else v for k, v in fields.items()}})
    return rows


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--output', required=True)
    args = parser.parse_args()
    def adb(*parts):
        return subprocess.check_output(['adb', *parts], text=True)
    assert adb('shell', 'getprop', 'ro.kernel.qemu').strip() == '1', 'Synthetic emulator only'
    rows = parse(adb('logcat', '-d', '-s', 'MED02APOC:I', '*:S'))
    assert any(r['kind'] == 'large_pdf' for r in rows), 'Missing actual large PDF observations'
    assert {r['mime'] for r in rows if r['kind'] == 'image'} >= {'image/png', 'image/jpeg', 'image/webp'}
    output = {
        'scope': 'SYNTHETIC_POC_NOT_PRODUCT_ACCEPTANCE',
        'api': int(adb('shell', 'getprop', 'ro.build.version.sdk').strip()),
        'device': adb('shell', 'getprop', 'ro.product.model').strip(),
        'abi': adb('shell', 'getprop', 'ro.product.cpu.abi').strip(),
        'observations': rows,
        'limits': 'scratch_bound is an algorithm bound; PSS before/after is observed process memory, not peak. No physical ARM benchmark.',
    }
    p = Path(args.output)
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(json.dumps(output, indent=2) + '\n')
    print(f'Actual MED02A POC observations: API{output["api"]}, {len(rows)} rows')


if __name__ == '__main__':
    main()
