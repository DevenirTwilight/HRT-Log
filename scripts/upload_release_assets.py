#!/usr/bin/env python3
"""Verify published release assets; this workflow cannot upload or replace binaries.

The filename is retained for compatibility. Production signing keys never enter this workflow.
"""
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile


def api(endpoint):
    return json.loads(subprocess.check_output(['gh', 'api', endpoint], text=True))


def main():
    repository = os.environ['GITHUB_REPOSITORY']
    manifest = json.loads(Path('.github/release-assets.json').read_text())
    release = api(f"repos/{repository}/releases/{manifest['release_id']}")
    assert not release['draft'] and release['tag_name'] == manifest['tag']
    source = manifest['source_commit']
    assert re.fullmatch(r'[0-9a-f]{40}', source)
    subprocess.run(['git', 'merge-base', '--is-ancestor', source, 'HEAD'], check=True)
    assert subprocess.check_output(['git', 'rev-parse', manifest['tag'] + '^{}'], text=True).strip() == source
    actual = {a['name']: a for a in release['assets']}
    assert set(actual) == {a['name'] for a in manifest['assets']}, 'Unexpected or missing published assets'
    with tempfile.TemporaryDirectory(prefix='release-verify-') as temporary:
        for expected in manifest['assets']:
            name = expected['name']
            assert re.fullmatch(r'[A-Za-z0-9_.-]+', name)
            assert re.fullmatch(r'[0-9a-f]{64}', expected['sha256'])
            assert actual[name]['state'] == 'uploaded' and actual[name]['size'] == expected['size']
            subprocess.run(['gh', 'release', 'download', manifest['tag'], '--repo', repository,
                            '--pattern', name, '--dir', temporary], check=True)
            content = (Path(temporary) / name).read_bytes()
            assert len(content) == expected['size']
            assert hashlib.sha256(content).hexdigest() == expected['sha256']
            if actual[name].get('digest'):
                assert actual[name]['digest'] == 'sha256:' + expected['sha256']
            print(f'Verified published asset: {name} ({expected["size"]} bytes)')


if __name__ == '__main__':
    main()
