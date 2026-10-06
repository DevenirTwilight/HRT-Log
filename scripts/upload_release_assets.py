#!/usr/bin/env python3
"""Upload explicitly authorized signed hotfix assets, then verify the published release.

Signing is performed locally using the existing production key, never passed to this workflow.
After publication the one-time replacement flag is removed, leaving verification only.
"""
import base64
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
    if manifest.get('replacement_authorized'):
        # Explicit one-time authorization for this emergency replacement, never a generic publisher.
        assert manifest['release_id'] == 404602730 and manifest['tag'] == 'v0.2.0'
        assert manifest['version_code'] == 4 and manifest['version_name'] == '0.2.0'
        with tempfile.TemporaryDirectory(prefix='signed-hotfix-') as temporary:
            files = []
            for expected in manifest['assets']:
                name = expected['name']
                assert re.fullmatch(r'[A-Za-z0-9_.-]+', name)
                chunks = []
                for sha in expected['blobs']:
                    assert re.fullmatch(r'[0-9a-f]{40}', sha)
                    blob = api(f'repos/{repository}/git/blobs/{sha}')
                    assert blob['encoding'] == 'base64'
                    chunks.append(base64.b64decode(blob['content']))
                content = b''.join(chunks)
                assert len(content) == expected['size']
                assert hashlib.sha256(content).hexdigest() == expected['sha256']
                path = Path(temporary) / name
                path.write_bytes(content)
                files.append(str(path))
            subprocess.run(['gh', 'release', 'upload', manifest['tag'], '--repo', repository,
                            '--clobber', *files], check=True)
        # All replacement bytes were verified before changing the public source/notes.
        subprocess.run(['gh', 'api', '--method', 'PATCH', f'repos/{repository}/git/refs/tags/v0.2.0',
                        '-f', 'sha=' + source, '-F', 'force=true'], check=True, stdout=subprocess.DEVNULL)
        subprocess.run(['gh', 'release', 'edit', manifest['tag'], '--repo', repository,
                        '--target', source, '--title', 'HRT Log 0.2.0 — emergency hotfix (build 4)',
                        '--notes-file', 'docs/releases/0.2.0-hotfix.md', '--latest'], check=True)
        release = api(f"repos/{repository}/releases/{manifest['release_id']}")
        for asset in release['assets']:
            if asset['name'] == 'UPSTREAM_LICENSE.txt':
                subprocess.run(['gh', 'api', '--method', 'DELETE',
                                f'repos/{repository}/releases/assets/{asset["id"]}'], check=True)
        subprocess.run(['git', 'fetch', '--force', 'origin', 'refs/tags/v0.2.0:refs/tags/v0.2.0'], check=True)
        release = api(f"repos/{repository}/releases/{manifest['release_id']}")
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
