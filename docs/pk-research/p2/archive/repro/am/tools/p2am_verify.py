#!/usr/bin/env python3
import json,hashlib
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
m=json.loads((ROOT/'SHA256.json').read_text(encoding='utf-8'))
assert m['phase']=='P2-AM'
for name,digest in m['files'].items():
    p=ROOT/name
    assert p.is_file(),f'missing {name}'
    assert hashlib.sha256(p.read_bytes()).hexdigest()==digest, f'hash mismatch: {name}'
assert len(m['files'])>10
print('P2-AM SHA-256 PASS:',len(m['files']),'files')
