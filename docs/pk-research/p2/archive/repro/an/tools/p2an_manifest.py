#!/usr/bin/env python3
"""Create SHA256 manifest after all outputs are final; file itself excluded."""
import hashlib,json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
files=sorted(p for p in ROOT.rglob('*') if p.is_file() and
             p.relative_to(ROOT).parts[0] in ('tools','inputs','figures','results') and
             '__pycache__' not in p.parts and p.suffix!='.pyc')
files.extend(sorted(p for p in ROOT.iterdir() if p.is_file() and p.suffix=='.md'))
manifest={'phase':'P2-AN','source_origin':'SYNTHETIC_ONLY',
          'p2am_original_sha256':hashlib.sha256((ROOT/'tools/p2am_protocol_reference.py').read_bytes()).hexdigest(),
          'clinical_accuracy_established':False,'production_replacement_authorized':False,
          'files':{str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in files}}
(ROOT/'SHA256.json').write_text(json.dumps(manifest,sort_keys=True,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
print('manifest files',len(files))
