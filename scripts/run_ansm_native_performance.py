#!/usr/bin/env python3
"""Run/collect inside the emulator action, including failed tests, before emulator shutdown."""
import json
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]

def run(execute=subprocess.run):
    test = execute([str(ROOT / 'gradlew'), ':core:data:connectedDebugAndroidTest',
                    '-Pandroid.testInstrumentationRunnerArguments.class=net.plainnotes.app.data.LargeInventoryPerformanceProbe',
                    '--no-parallel', '--max-workers=2'], cwd=ROOT)
    collection = execute(['python3', str(ROOT / 'scripts/collect_ansm_native_performance.py')], cwd=ROOT)
    folder = ROOT / 'core/data/build/reports/ansm-native-performance'
    folder.mkdir(parents=True, exist_ok=True)
    (folder / 'execution.json').write_text(json.dumps({'test_exit': test.returncode, 'collection_exit': collection.returncode})+'\n')
    return test.returncode or collection.returncode

if __name__ == '__main__':
    raise SystemExit(run())
