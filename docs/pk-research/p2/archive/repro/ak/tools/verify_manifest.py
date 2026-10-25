#!/usr/bin/env python3
"""Verify contents against SHA256 manifest; no mutating repository interaction."""
import hashlib,json
from pathlib import Path
root=Path(__file__).resolve().parents[1]
data=json.loads((root/'SHA256.json').read_text())
for path,expected in data['files'].items():
    p=root/path
    assert p.exists(), f'missing file: {path}'
    got=hashlib.sha256(p.read_bytes()).hexdigest()
    assert got==expected, f'hash mismatch: {path}'
assert len(data['files'])>10
print(f"PASS {len(data['files'])} SHA-256 items: all match")
