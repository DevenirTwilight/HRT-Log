from pathlib import Path
import json,hashlib,sys
BASE=Path(__file__).resolve().parents[1]
manifest=json.loads((BASE/'SHA256.json').read_text())
for rel,sha in manifest.items():
    p=BASE/rel
    if not p.is_file() or hashlib.sha256(p.read_bytes()).hexdigest()!=sha:
        raise SystemExit('Hash mismatch: '+rel)
print('verified',len(manifest),'files')
