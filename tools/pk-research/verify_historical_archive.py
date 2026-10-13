"""Run the frozen 164-file and five-output replay; failure never becomes skip."""
import argparse
import importlib.metadata
import json
import platform
import subprocess
import tempfile
import time
from pathlib import Path
import research_archive as archive


def verify(output):
    started = time.monotonic()
    records = archive.verify_records()
    baseline = archive.historical_baseline()
    replay_started = time.monotonic()
    archive.replay_research(output)
    hist = archive.historical_manifest()
    results = archive.compare_results(output, archive.ROOT / hist['results_dir'], hist['result_files'])
    return dict(baseline_files=baseline, replay_results=results, archived_records=records,
                archive_commits=archive.required_commits(),
                verifier_commit=subprocess.check_output(['git','rev-parse','HEAD'],cwd=archive.ROOT,text=True).strip(),
                python=platform.python_version(), platform=platform.platform(),
                packages={d.metadata['Name']:d.version for d in importlib.metadata.distributions()},
                replay_seconds=time.monotonic()-replay_started, total_seconds=time.monotonic()-started)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--required-commits', action='store_true')
    parser.add_argument('--report', type=Path)
    args = parser.parse_args()
    if args.required_commits:
        print(' '.join(archive.required_commits()))
    else:
        with tempfile.TemporaryDirectory(prefix='science-archive-') as directory:
            result = verify(Path(directory) / 'replay')
        encoded = json.dumps(result, indent=2, sort_keys=True) + '\n'
        if args.report:
            args.report.parent.mkdir(parents=True, exist_ok=True)
            args.report.write_text(encoded)
        print(encoded)
