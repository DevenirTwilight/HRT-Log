#!/usr/bin/env python3
"""Check packaged fixed-content SHA256 manifest, rejecting missing/mutated files."""
import hashlib,json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
manifest=json.loads((ROOT/'SHA256.json').read_text(encoding='utf-8'))
assert manifest['phase']=='P2-AF'
for rel,digest in manifest['files'].items():
    p=ROOT/rel
    if not p.is_file() or hashlib.sha256(p.read_bytes()).hexdigest()!=digest:
        raise ValueError('Missing or modified packaged file: '+rel)
print('PASS',len(manifest['files']),'P2-AF files SHA256 matched')
