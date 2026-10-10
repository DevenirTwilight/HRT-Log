#!/usr/bin/env python3
import hashlib,json
from pathlib import Path
R=Path(__file__).resolve().parents[1]; m=json.loads((R/'SHA256.json').read_text())
for name,expected in m['files'].items():
    p=R/name
    if not p.is_file():raise RuntimeError('missing: '+name)
    actual=hashlib.sha256(p.read_bytes()).hexdigest()
    if actual!=expected:raise RuntimeError('digest mismatch for: '+name)
print(f"PASS: {len(m['files'])}/{len(m['files'])} source and result files SHA-256 verified")
