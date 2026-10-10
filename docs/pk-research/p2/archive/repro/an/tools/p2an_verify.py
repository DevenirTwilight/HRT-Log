#!/usr/bin/env python3
"""Fail-closed SHA and deterministic result replay for synthetic P2-AN bundle."""
import hashlib
import json
from pathlib import Path
import p2an_audit

ROOT=Path(__file__).resolve().parents[1]
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()

def verify():
    manifest=json.loads((ROOT/'SHA256.json').read_text(encoding='utf-8'))
    assert manifest['phase']=='P2-AN' and manifest['source_origin']=='SYNTHETIC_ONLY'
    for rel,expected in manifest['files'].items():
        p=ROOT/rel
        if not p.is_file() or sha(p)!=expected:raise ValueError('HASH MISMATCH: '+rel)
    prior=(ROOT/'results/p2an-analysis.json').read_bytes()
    result=p2an_audit.main()
    assert result['origin']=='SYNTHETIC_ONLY' and result['is_clinical_validation'] is False
    if (ROOT/'results/p2an-analysis.json').read_bytes()!=prior:
        raise ValueError('Non-deterministic JSON result replay')
    assert sha(ROOT/'tools/p2am_protocol_reference.py')==manifest['p2am_original_sha256']
    print('PASS:',len(manifest['files']),'hashes + exact JSON replay; synthetic only; no clinical claim')

if __name__=='__main__':verify()
