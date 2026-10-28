"""SL-CAL-01 phase guard: this phase is research/design only. A change range may touch only research files, tests and the three
project documents; production behavior, UI, exports, Room schema, backups, parameters and workflows are off limits."""
import os
import subprocess

ALLOWED_PREFIXES = (
    'docs/pk-research/sl-cal/', 'tools/pk-research/sl_cal/', 'docs/audits/product-quality-2026-10/',
)
ALLOWED_FILES = {
    'tools/pk-research/test_sl_cal_01.py', 'docs/REQUIREMENTS.md', 'docs/HANDOFF.md', 'docs/STATUS.md', 'docs/pk-research/README.md',
}
FORBIDDEN_PREFIXES = (
    'app/', 'core/', 'pk-engine/', 'importer/', 'scripts/', '.github/', 'gradle/',
)


def violations(paths):
    out = []
    for p in paths:
        if p.startswith(FORBIDDEN_PREFIXES):
            out.append(p)
        elif p not in ALLOWED_FILES and not p.startswith(ALLOWED_PREFIXES):
            out.append(p)
    return sorted(out)


def phase_diff_paths(env=None, runner=subprocess.check_output):
    """Paths changed by this phase's branch, or None when the range cannot be defined (not a PR/branch of this phase)."""
    env = os.environ if env is None else env
    head = env.get('GITHUB_HEAD_REF') or runner(['git', 'rev-parse', '--abbrev-ref', 'HEAD'], text=True).strip()
    if not head.startswith('research/sl-cal-01'):
        return None
    base = env.get('GITHUB_BASE_REF') or 'claude/new-session-1959qb'
    return runner(['git', 'diff', '--name-only', f'origin/{base}...HEAD'], text=True).splitlines()
