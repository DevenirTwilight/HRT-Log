"""Read-only protocol/hash guard, usable before any candidate exists."""
import hashlib
import json
from pathlib import Path
ROOT = Path(__file__).resolve().parents[2]
P2 = ROOT / 'docs/pk-research/p2'
def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()
def check(directory=P2, production_root=ROOT):
    lock = json.loads((directory / 'protocol-lock.json').read_text())
    for name, expected in lock['files'].items():
        if digest(directory / name) != expected:
            raise ValueError('Frozen input changed: ' + name)
    if lock['protocol_sha256'] != digest(directory / 'validation-protocol.md') or lock['dataset_sha256'] != digest(directory / 'evidence-catalog.json'):
        raise ValueError('Protocol/dataset SHA mismatch')
    baseline = json.loads((directory / 'production-baseline.json').read_text())
    for name, expected in baseline['files'].items():
        if digest(production_root / name) != expected:
            raise ValueError('Production or old evidence changed: ' + name)
    catalog = json.loads((directory / 'evidence-catalog.json').read_text())
    split = json.loads((directory / 'split-manifest.json').read_text())
    assigned = [sid for ids in split['roles'].values() for sid in ids]
    if len(assigned) != len(set(assigned)) or set(assigned) != {s['id'] for s in catalog['studies']}:
        raise ValueError('Study split leakage/missing study')
    for s in catalog['studies']:
        if s['id'] not in split['roles'][s['role']] or (s['previously_seen'] and s['role'] == 'LOCKED_EXTERNAL'):
            raise ValueError('Unblinded/incorrect role')
    train = [r['id'] for r in catalog['records'] if r['used_in_fitting']]
    if train != split['train_record_ids']:
        raise ValueError('Train record mismatch')
    return lock
if __name__ == '__main__':
    print(json.dumps(check(), indent=2))
