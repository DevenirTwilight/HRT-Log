import hashlib
import json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
manifest=json.loads((ROOT/'SHA256.json').read_text(encoding='utf-8'))
for relative,expected in manifest['files'].items():
 actual=hashlib.sha256((ROOT/relative).read_bytes()).hexdigest()
 if actual!=expected:raise ValueError(f'hash mismatch: {relative}')
print('SHA-256 verified:',len(manifest['files']),'files')
