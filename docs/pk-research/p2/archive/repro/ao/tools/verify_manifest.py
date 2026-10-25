"""Verify frozen P2-AO bundle file SHA-256 list (excluding manifest itself)."""
import hashlib
import json
from pathlib import Path
root=Path(__file__).resolve().parents[1]
manifest=json.loads((root/'SHA256.json').read_text(encoding='utf-8'))
listed=manifest['files']
actual={p.relative_to(root).as_posix() for p in root.rglob('*') if p.is_file() and p.name!='SHA256.json' and '__pycache__' not in p.parts}
if set(listed)!=actual:
    raise SystemExit(f'File set mismatch missing={set(listed)-actual}; unexpected={actual-set(listed)}')
for rel,sha in listed.items():
    b=(root/rel).read_bytes()
    digest=hashlib.sha256(b).hexdigest()
    if digest!=sha:raise SystemExit(f'SHA mismatch {rel}')
print(f'OK: {len(listed)} file hashes verified.')
