#!/usr/bin/env python3
import hashlib,json
from pathlib import Path
root=Path(__file__).resolve().parents[1]
manifest=json.loads((root/'SHA256.json').read_text(encoding='utf-8'))
expected=manifest['files']
actual={str(x.relative_to(root)) for x in root.rglob('*') if x.is_file() and '__pycache__' not in x.parts and x.name!='SHA256.json'}
assert set(expected)==actual,(sorted(set(expected)-actual),sorted(actual-set(expected)))
for name,checksum in expected.items():
    assert hashlib.sha256((root/name).read_bytes()).hexdigest()==checksum,name
print(f'PASS: {len(expected)} SHA-256 hashes, no undeclared files')
