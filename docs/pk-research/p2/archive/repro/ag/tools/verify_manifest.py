#!/usr/bin/env python3
"""Verify every file in manifest, not the manifest itself."""
from pathlib import Path
import hashlib, json
root=Path(__file__).resolve().parents[1]
rows=json.loads((root/'SHA256.json').read_text())
paths={p.relative_to(root).as_posix():p for p in root.rglob('*') if p.is_file() and p.name!='SHA256.json' and '__pycache__' not in str(p)}
assert set(rows)==set(paths),{'missing':set(rows)-set(paths),'extra':set(paths)-set(rows)}
for name,p in paths.items():
    actual=hashlib.sha256(p.read_bytes()).hexdigest()
    assert actual==rows[name],f'Hash mismatch: {name}'
print(f'PASS {len(paths)} file checksums')
