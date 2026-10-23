#!/usr/bin/env bash
# Single shell: emulator-runner executes each YAML script line separately.
set -euo pipefail
mkdir -p core/data/build/reports/med02-generation-memory
adb shell cat /proc/meminfo > core/data/build/reports/med02-generation-memory/guest-meminfo.txt
adb logcat -G 16M
python3 -c 'from pathlib import Path; import re; text=Path("core/data/build/reports/med02-generation-memory/guest-meminfo.txt").read_text(); value=int(re.search(r"MemTotal:\s+(\d+)",text)[1]); assert 400*1024 <= value <= 512*1024, "Unexpected guest memory"'
set +e
./gradlew :core:data:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=net.plainnotes.app.data.NativeCiHeapProbe,net.plainnotes.app.data.LibraryRestoreAndroidTest -Pandroid.testInstrumentationRunnerArguments.ansmExpectedHeapMb=192 --no-parallel --max-workers=2
result=$?
if [ "$result" -eq 0 ]; then
  python3 scripts/run_library_restore_restart.py
  result=$?
fi
adb exec-out run-as net.plainnotes.app.data.test cat files/synthetic-library-memory.json > core/data/build/reports/med02-generation-memory/sampled-process-memory.json
metrics=$?
adb logcat -d > core/data/build/reports/med02-generation-memory/logcat.txt
if [ "$result" -eq 0 ] && [ "$metrics" -ne 0 ]; then result=1; fi
exit "$result"
