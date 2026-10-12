#!/usr/bin/env python3
"""Read-only unified model version/resource gate; does not bypass the P2 guard."""
from pathlib import Path
import hashlib
import json
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]

def check():
    lock = json.loads((ROOT / 'docs/design/unified-model-inputs/model-source-lock.json').read_text())
    for name, expected in lock['files'].items():
        if hashlib.sha256((ROOT / name).read_bytes()).hexdigest() != expected:
            raise ValueError('Locked model/source changed: ' + name)
    keys = None; placeholders = None
    for locale in ('values', 'values-zh', 'values-b+zh+Hant', 'values-fr'):
        strings = ET.parse(ROOT / f'app/src/main/res/{locale}/strings_unified_concentration.xml').getroot()
        current = {n.get('name'): ''.join(n.itertext()) for n in strings}
        arguments = {name: re.findall(r'%\d+\$[sd]', text) for name, text in current.items()}
        if keys is not None and (current.keys() != keys or arguments != placeholders):
            raise ValueError('Locale missing key or changed arguments: ' + locale)
        if not all(current.values()): raise ValueError('Empty translation: ' + locale)
        keys, placeholders = current.keys(), arguments
    for name in ('ExperimentalPkScreen.kt',):
        if (ROOT / 'app/src/main/java/net/plainnotes/app/experimental' / name).exists():
            raise ValueError('Obsolete standalone research page retained')
    if any((ROOT / 'app/src/debug').rglob('Experimental*Activity.kt')):
        raise ValueError('Obsolete separate comparison/preview host retained')
    print('Model source lock and four locales verified; redundant research page absent')

if __name__ == '__main__':
    check()
