"""Classify changed paths for the always-present science CI gate.

research-deep runs when a change touches the PK engine, concentration/experimental
app code, research tools, scientific evidence, the science scripts listed below
(including this gate and its test) or any workflow. Other paths (ordinary UI,
translations, product docs) do not trigger it; their regressions are caught by
the routine Android tests. A manual run, a new branch, an unknown event or a
change range that cannot be resolved runs deep validation; a failing git diff
fails the job instead of skipping.
"""
import argparse
import os
import subprocess
from pathlib import Path

SCIENCE_PREFIXES = (
    'pk-engine/', 'tools/pk-research/', 'tools/pk-audit/',
    'docs/pk-research/', 'docs/design/pr19-calibration-evidence/',
    'docs/design/m2-only-evidence/', 'docs/design/unified-model-inputs/',
    'app/src/main/java/net/plainnotes/app/conc/',
    'app/src/main/java/net/plainnotes/app/experimental/',
    'app/src/test/java/net/plainnotes/app/conc/',
)
SCIENCE_FILES = {
    'scripts/generate_unified_model_reference.py',
    'scripts/check_unified_concentration.py',
    'scripts/science_change_gate.py',
    'scripts/test_science_change_gate.py',
    'scripts/run_m2_only_mutations.py',
    'scripts/run_pr19_calibration_mutations.py',
}


def needs_deep(paths):
    return any(path.startswith(SCIENCE_PREFIXES) or path in SCIENCE_FILES
               or path.startswith('.github/workflows/') for path in paths)


def _has_commit(ref):
    return subprocess.run(['git', 'rev-parse', '--verify', '--quiet', ref + '^{commit}'],
                          stdout=subprocess.DEVNULL).returncode == 0


def changed_paths(env=None):
    """Changed paths for this event, or None when the whole tree must be validated."""
    env = os.environ if env is None else env
    event = env.get('GITHUB_EVENT_NAME')
    if event == 'pull_request':
        base = env.get('GITHUB_BASE_REF', '')
        if not base or not _has_commit(f'origin/{base}'):
            return None  # Base not resolvable: validate everything.
        spec = f'origin/{base}...HEAD'  # Changes of the PR since its merge base.
    elif event == 'push':
        before = env.get('GITHUB_EVENT_BEFORE', '')
        if not before or set(before) == {'0'} or not _has_commit(before):
            return None  # New branch, or force push whose old head is gone.
        spec = f'{before}..HEAD'
    else:
        return None  # workflow_dispatch or unknown event: full replay.
    return subprocess.check_output(['git', 'diff', '--name-only', spec], text=True).splitlines()

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('paths', nargs='*')
    args = parser.parse_args()
    paths = args.paths if args.paths else changed_paths()
    deep = paths is None or needs_deep(paths)
    print('changed_paths=' + ('ALL' if paths is None else ','.join(paths)))
    print('run_deep=' + str(deep).lower())
    if 'GITHUB_OUTPUT' in os.environ:
        Path(os.environ['GITHUB_OUTPUT']).open('a').write('deep=' + str(deep).lower() + '\n')
