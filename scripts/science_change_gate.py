"""Classify changed paths for the always-present science CI gate.

Unknown source/workflow paths fail closed into deep validation. Documentation
outside scientific evidence and ordinary UI text do not fetch Git history.
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
}


def needs_deep(paths):
    return any(path.startswith(SCIENCE_PREFIXES) or path in SCIENCE_FILES
               or path.startswith('.github/workflows/') for path in paths)


def changed_paths():
    event = os.environ.get('GITHUB_EVENT_NAME')
    if event == 'workflow_dispatch':
        return None  # Manual full replay.
    if event == 'pull_request':
        base = os.environ['GITHUB_BASE_REF']
        before = f'origin/{base}'
        spec = f'{before}...HEAD'
    elif event == 'push':
        before = os.environ.get('GITHUB_EVENT_BEFORE', '')
        if not before or set(before) == {'0'}:
            return None  # New branch: validate the whole tree once.
        spec = f'{before}..HEAD'
    else:
        return None
    subprocess.run(['git', 'rev-parse', '--verify', spec.split('.')[0]],
                   check=True, stdout=subprocess.DEVNULL)
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
