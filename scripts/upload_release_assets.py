#!/usr/bin/env python3
"""Upload verified, already signed binaries to a draft release; no signing keys."""
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile


def api(endpoint):
    return json.loads(subprocess.check_output(["gh", "api", endpoint], text=True))


def main():
    repository = os.environ["GITHUB_REPOSITORY"]
    manifest = json.loads(Path(".github/release-assets.json").read_text())
    release_id = manifest["release_id"]
    assert isinstance(release_id, int) and release_id > 0
    release = api(f"repos/{repository}/releases/{release_id}")
    assert release["draft"], "Only draft releases may receive these uploads"
    assert release["tag_name"] == manifest["tag"]
    assert re.fullmatch(r"[0-9a-f]{40}", manifest["source_commit"])
    subprocess.run(["git", "merge-base", "--is-ancestor", manifest["source_commit"], "HEAD"], check=True)
    with tempfile.TemporaryDirectory(prefix="release-assets-") as temporary:
        files = []
        for asset in manifest["assets"]:
            assert re.fullmatch(r"[A-Za-z0-9_.-]+", asset["name"])
            assert re.fullmatch(r"[0-9a-f]{64}", asset["sha256"])
            chunks = []
            for sha in asset["blobs"]:
                assert re.fullmatch(r"[0-9a-f]{40}", sha)
                blob = api(f"repos/{repository}/git/blobs/{sha}")
                assert blob["encoding"] == "base64"
                chunks.append(base64.b64decode(blob["content"]))
            content = b"".join(chunks)
            assert len(content) == asset["size"]
            assert hashlib.sha256(content).hexdigest() == asset["sha256"]
            path = Path(temporary) / asset["name"]
            path.write_bytes(content)
            files.append(str(path))
        # Retries can replace partial draft uploads, never a published release.
        assert api(f"repos/{repository}/releases/{release_id}")["draft"]
        subprocess.run(["gh", "release", "upload", manifest["tag"], "--repo", repository,
                        "--clobber", *files], check=True)
    uploaded = {a["name"]: a for a in api(f"repos/{repository}/releases/{release_id}")["assets"]}
    for expected in manifest["assets"]:
        actual = uploaded[expected["name"]]
        assert actual["state"] == "uploaded" and actual["size"] == expected["size"]
        if actual.get("digest"):
            assert actual["digest"] == "sha256:" + expected["sha256"]
        print(f"Verified uploaded asset: {expected['name']} ({expected['size']} bytes)")


if __name__ == "__main__":
    main()
