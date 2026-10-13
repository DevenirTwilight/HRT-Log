"""Independent, read-only archive verification. No live source or UI approvals.

The manifest is read from the accepted mainline Git object, never trusted from
editable classification JSON. Missing Git objects/dependencies are fatal.
"""
import hashlib
import json
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path
ROOT = Path(__file__).resolve().parents[2]
P2 = ROOT / 'docs/pk-research/p2'
ARCHIVE_COMMIT = '1e584285cb1e6f7a4b09a3e20585418d35a28735'
def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

class HistoricalObjectMissing(RuntimeError):
    """A historical git object needed for verification is unavailable. Never treated as success."""

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

def historical_manifest():
    path = 'docs/pk-research/p2/guard-classification.json'
    return json.loads(git_blobs(ARCHIVE_COMMIT, [path])[path])['historical']


def required_commits():
    # Only three archive identities, no per-change UI approval commits.
    hist = historical_manifest()
    return sorted({ARCHIVE_COMMIT, hist['start_sha'], hist['research_run_commit']})


def verify_records(production_root=ROOT):
    """Old research records are immutable; new paths/versions can be appended."""
    _require_commit(ARCHIVE_COMMIT)
    paths = subprocess.check_output(
        ['git', 'ls-tree', '-r', '--name-only', ARCHIVE_COMMIT, '--',
         'docs/pk-research', 'docs/design/pr19-calibration-evidence',
         'docs/design/m2-only-evidence/independent-reference.json'], cwd=ROOT, text=True).splitlines()
    blobs = git_blobs(ARCHIVE_COMMIT, paths)
    for name, raw in blobs.items():
        target = Path(production_root) / name
        if not target.is_file() or target.read_bytes() != raw:
            raise ValueError('Archived record changed or missing: ' + name)
    return len(paths)


def historical_baseline(directory=P2, start_sha=None):
    """Every frozen baseline hash must be reproduced from the git object at the historical start commit."""
    name = 'docs/pk-research/p2/production-baseline.json'
    baseline = json.loads(git_blobs(ARCHIVE_COMMIT, [name])[name])
    start = start_sha or baseline['start_sha']
    blobs = git_blobs(start, list(baseline['files']))
    bad = [p for p, h in baseline['files'].items() if blobs[p] is None or hashlib.sha256(blobs[p]).hexdigest() != h]
    if bad:
        raise ValueError(f'Historical baseline not reproduced at {start}: {len(bad)} file(s), e.g. {bad[0]}')
    return len(baseline['files'])

def replay_research(output, directory=P2, commit=None):
    """Runs research.py in an isolated worktree at the recorded research commit, writing into output. Read-only."""
    hist = historical_manifest(); commit = commit or hist['research_run_commit']
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
