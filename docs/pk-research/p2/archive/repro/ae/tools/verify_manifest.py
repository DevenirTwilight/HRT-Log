#!/usr/bin/env python3
"""Verify SHA-256 digest of every included file except SHA256.json itself."""
import hashlib,json
from pathlib import Path
r=Path(__file__).resolve().parents[1]
m=json.loads((r/'SHA256.json').read_text())
actual={str(p.relative_to(r)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(r.rglob('*')) if p.is_file() and p.name!='SHA256.json' and '__pycache__' not in p.parts}
assert actual==m['files'],f'manifest discrepancy: expected {len(m["files"])} actual {len(actual)}'
print(f'SHA-256 verified: {len(actual)} files')
