"""Freeze completed P2-AM artifact checksums; never changes research data."""
import hashlib,json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
files={p.relative_to(ROOT).as_posix():hashlib.sha256(p.read_bytes()).hexdigest()
    for p in sorted(ROOT.rglob('*')) if p.is_file()
    and p.name!='SHA256.json'
    and '__pycache__' not in p.parts and not p.name.endswith('.pyc')}
(ROOT/'SHA256.json').write_text(json.dumps({'phase':'P2-AM','files':files},indent=2,sort_keys=True,ensure_ascii=False)+'\n',encoding='utf8')
print('froze',len(files),'files')
