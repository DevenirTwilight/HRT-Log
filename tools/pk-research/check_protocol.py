"""Read-only protocol/hash guard, usable before any candidate exists."""
import hashlib
import json
from pathlib import Path
ROOT = Path(__file__).resolve().parents[2]
P2 = ROOT / 'docs/pk-research/p2'
def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()
# Append-only waivers for production files that changed after the freeze for reasons unrelated to PK
# (validation-protocol.md: later changes are recorded as protocol deviations, never by rewriting the protocol).
# Only plain UI files may be waived; PK, concentration/lab display, data, resources and old evidence stay strictly locked.
WAIVABLE_PREFIX = 'app/src/main/java/net/plainnotes/app/ui/'
NEVER_WAIVABLE = ('Conc', 'Lab', 'Chart')
def waiver(directory, name, expected, actual):
    path = directory / 'protocol-deviations.json'
    if not path.exists():
        return None
    for d in json.loads(path.read_text())['deviations']:
        if d['file'] != name:
            continue
        leaf = name.rsplit('/', 1)[-1]
        if not name.startswith(WAIVABLE_PREFIX) or any(word in leaf for word in NEVER_WAIVABLE) or d.get('pk_relevant') is not False:
            raise ValueError('Deviation not allowed for PK-relevant or non-UI file: ' + name)
        if d['baseline_sha256'] == expected and d['new_sha256'] == actual:
            return d
    return None
def check(directory=P2, production_root=ROOT):
    lock = json.loads((directory / 'protocol-lock.json').read_text())
    for name, expected in lock['files'].items():
        if digest(directory / name) != expected:
            raise ValueError('Frozen input changed: ' + name)
    if lock['protocol_sha256'] != digest(directory / 'validation-protocol.md') or lock['dataset_sha256'] != digest(directory / 'evidence-catalog.json'):
        raise ValueError('Protocol/dataset SHA mismatch')
    baseline = json.loads((directory / 'production-baseline.json').read_text())
    for name, expected in baseline['files'].items():
        actual = digest(production_root / name)
        if actual != expected and waiver(directory, name, expected, actual) is None:
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
