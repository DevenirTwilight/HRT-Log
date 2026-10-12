"""Read-only protocol/hash guard, usable before any candidate exists.

Two domains (audit B0, owner decision D-13, 2026-10-12; docs/audits/product-quality-2026-10/12-b0-guard-implementation-plan.md):
  historical: the frozen production baseline and the recorded P2 research outputs are verified from immutable git
              objects (start_sha, research_run_commit). Missing objects fail; the working tree never stands in for history.
  current:    research inputs and archived evidence stay byte-locked; PK display files need an owner approval chain;
              every other product file evolves under the ordinary test suites (guard-classification.json).
"""
import hashlib
import json
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path
ROOT = Path(__file__).resolve().parents[2]
P2 = ROOT / 'docs/pk-research/p2'
OWNER = 'DevenirTwilight'
def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()
class HistoricalObjectMissing(RuntimeError):
    """A historical git object needed for verification is unavailable. Never treated as success."""
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

# --- git objects -------------------------------------------------------------------------------------------------
def _require_commit(commit):
    try:
        ok = subprocess.run(['git', 'cat-file', '-e', commit + '^{commit}'], cwd=ROOT, capture_output=True).returncode == 0
    except FileNotFoundError:
        ok = False
    if not ok:
        raise HistoricalObjectMissing(f'Historical git commit {commit} is unavailable; fetch it (CI: git fetch --no-tags --depth=1 origin {commit})')
def git_blobs(commit, paths):
    """{path: bytes or None} for paths at commit, read from the object database only."""
    _require_commit(commit)
    request = ''.join(f'{commit}:{p}\n' for p in paths).encode()
    out = subprocess.run(['git', 'cat-file', '--batch'], cwd=ROOT, input=request, capture_output=True, check=True).stdout
    result, pos = {}, 0
    for p in paths:
        end = out.index(b'\n', pos); header = out[pos:end].split(b' '); pos = end + 1
        if header[-1] == b'missing':
            result[p] = None
            continue
        size = int(header[2]); result[p] = out[pos:pos + size]; pos += size + 1
    return result

# --- classification ----------------------------------------------------------------------------------------------
def classification(directory=P2, baseline=None):
    text = (directory / 'guard-classification.json').read_bytes()
    lock = json.loads((directory / 'guard-classification-lock.json').read_text())
    if hashlib.sha256(text).hexdigest() != lock['guard_classification_sha256']:
        raise ValueError('Guard classification changed without its lock')
    cls = json.loads(text)
    baseline = baseline or json.loads((directory / 'production-baseline.json').read_text())
    groups = [set(cls['live_lock_baseline']), set(cls['display_review']), set(cls['history_only'])]
    if sum(len(g) for g in groups) != len(set().union(*groups)) or set().union(*groups) != set(baseline['files']):
        raise ValueError('Guard classification does not partition the frozen baseline exactly')
    if cls['historical']['start_sha'] != baseline['start_sha']:
        raise ValueError('Guard classification names a different historical start commit')
    return cls

def check_display(directory, name, expected, actual):
    """A changed PK display file needs an unbroken owner approval chain whose evidence is in git history."""
    path = directory / 'display-approvals.json'
    approvals = json.loads(path.read_text())['approvals'] if path.exists() else []
    chain = [a for a in approvals if a.get('file') == name]
    if not chain:
        raise ValueError('PK display file changed without owner approval: ' + name)
    previous = expected
    for a in chain:
        if any(not a.get(k) for k in ('id', 'date', 'scope', 'previous_sha256', 'new_sha256', 'evidence')) or a.get('approver') != OWNER:
            raise ValueError('Incomplete or non-owner approval record: ' + str(a.get('id')))
        if a['previous_sha256'] != previous:
            raise ValueError('Broken approval chain for ' + name)
        evidence = a['evidence']
        text = git_blobs(evidence.get('commit', ''), [evidence.get('path', 'docs/REQUIREMENTS.md')]).get(evidence.get('path', 'docs/REQUIREMENTS.md'))
        if text is None or a['id'].encode() not in text:
            raise ValueError('Approval evidence not verifiable in git history: ' + a['id'])
        previous = a['new_sha256']
    if previous != actual:
        raise ValueError('PK display file differs from its last approved state: ' + name)
    return chain[-1]

def check_deviations(directory, baseline, cls):
    """The append-only deviation record must keep its required entries in order, each matching git history."""
    path = directory / 'protocol-deviations.json'
    if not path.exists():
        raise ValueError('Append-only protocol deviation record is missing')
    deviations = json.loads(path.read_text())['deviations']
    ids = [d['id'] for d in deviations]
    if len(ids) != len(set(ids)) or ids[:len(cls['required_deviation_ids'])] != cls['required_deviation_ids']:
        raise ValueError('Append-only deviation chain was reordered or truncated')
    for d in deviations:
        if d['file'] in baseline['files'] and d['baseline_sha256'] != baseline['files'][d['file']]:
            raise ValueError('Deviation baseline hash does not match the frozen baseline: ' + d['id'])
        if 'commit' in d:
            blob = git_blobs(d['commit'], [d['file']])[d['file']]
            if blob is None or hashlib.sha256(blob).hexdigest() != d['new_sha256']:
                raise ValueError('Deviation record does not match git history: ' + d['id'])

def dependency_scan(cls, production_root=ROOT):
    """Production files named by research/audit tooling must stay locked or approval-gated, never history-only."""
    protected = set(cls['live_lock_baseline']) | set(cls['live_lock_additional']) | set(cls['display_review'])
    scan = cls['dependency_scan']; prefixes = tuple(scan['production_prefixes'])
    pattern = re.compile(r"""['"]((?:%s)[A-Za-z0-9_./+-]+\.[A-Za-z0-9]+)['"]""" % '|'.join(map(re.escape, prefixes)))
    # Only tracked sources count; generated build outputs (app/build/...) are not production files.
    tracked = set(subprocess.run(['git', 'ls-files', '-z'], cwd=ROOT, capture_output=True, check=True).stdout.decode().split('\0'))
    offenders = set()
    for glob in scan['globs']:
        for tool in sorted(production_root.glob(glob)):
            if any(tool.name.startswith(x) for x in scan['exclude_prefix']):
                continue
            for name in pattern.findall(tool.read_text(errors='ignore')):
                if name in tracked and name not in protected:
                    offenders.add(f'{tool.relative_to(production_root)} -> {name}')
    return sorted(offenders)

def historical_baseline(directory=P2, start_sha=None):
    """Every frozen baseline hash must be reproduced from the git object at the historical start commit."""
    baseline = json.loads((directory / 'production-baseline.json').read_text())
    start = start_sha or baseline['start_sha']
    blobs = git_blobs(start, list(baseline['files']))
    bad = [p for p, h in baseline['files'].items() if blobs[p] is None or hashlib.sha256(blobs[p]).hexdigest() != h]
    if bad:
        raise ValueError(f'Historical baseline not reproduced at {start}: {len(bad)} file(s), e.g. {bad[0]}')
    return len(baseline['files'])

def check(directory=P2, production_root=ROOT, historical=True):
    lock = json.loads((directory / 'protocol-lock.json').read_text())
    for name, expected in lock['files'].items():
        if digest(directory / name) != expected:
            raise ValueError('Frozen input changed: ' + name)
    if lock['protocol_sha256'] != digest(directory / 'validation-protocol.md') or lock['dataset_sha256'] != digest(directory / 'evidence-catalog.json'):
        raise ValueError('Protocol/dataset SHA mismatch')
    baseline = json.loads((directory / 'production-baseline.json').read_text())
    cls = classification(directory, baseline)
    for name in cls['live_lock_baseline']:
        path = production_root / name
        if not path.is_file() or digest(path) != baseline['files'][name]:
            raise ValueError('Production or old evidence changed: ' + name)
    for name, expected in cls['live_lock_additional'].items():
        path = production_root / name
        if not path.is_file() or digest(path) != expected:
            raise ValueError('Research input changed: ' + name)
    for name in cls['display_review']:
        path = production_root / name
        if not path.is_file():
            raise ValueError('PK display file missing: ' + name)
        if digest(path) != baseline['files'][name]:
            check_display(directory, name, baseline['files'][name], digest(path))
    check_deviations(directory, baseline, cls)
    offenders = dependency_scan(cls, production_root)
    if offenders:
        raise ValueError('Research tooling depends on unlocked production files: ' + '; '.join(offenders))
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
    if historical:
        historical_baseline(directory)
    return lock

# --- historical research replay ----------------------------------------------------------------------------------
def replay_research(output, directory=P2, commit=None):
    """Runs research.py in an isolated worktree at the recorded research commit, writing into output. Read-only."""
    cls = classification(directory)
    hist = cls['historical']; commit = commit or hist['research_run_commit']
    recorded = json.loads((ROOT / hist['results_dir'] / 'analysis.json').read_text())
    tools = git_blobs(commit, [f'tools/pk-research/{t}' for t in hist['research_tools']])
    for t in hist['research_tools']:
        blob = tools[f'tools/pk-research/{t}']
        if blob is None or hashlib.sha256(blob).hexdigest() != recorded['code_sha256'][t]:
            raise ValueError(f'Research tools at {commit} are not the ones that produced the recorded results ({t})')
    work = Path(tempfile.mkdtemp(prefix='p2-replay-'))
    tree = work / 'tree'
    try:
        subprocess.run(['git', 'worktree', 'add', '--detach', str(tree), commit], cwd=ROOT, capture_output=True, check=True)
        run = subprocess.run([sys.executable, 'tools/pk-research/research.py', '--output', str(output)], cwd=tree, capture_output=True, text=True)
        if run.returncode != 0:
            raise ValueError('Historical research replay failed: ' + run.stderr[-500:])
    finally:
        subprocess.run(['git', 'worktree', 'remove', '--force', str(tree)], cwd=ROOT, capture_output=True)
        shutil.rmtree(work, ignore_errors=True)
    return output

def compare_results(output, expected_dir, files):
    """Byte-for-byte comparison of replayed outputs with the recorded results."""
    different = [f for f in files if not (Path(output) / f).is_file() or (Path(output) / f).read_bytes() != (Path(expected_dir) / f).read_bytes()]
    if different:
        raise ValueError('Replayed research differs from recorded results: ' + ', '.join(different))
    return len(files)

if __name__ == '__main__':
    print(json.dumps(check(), indent=2))
