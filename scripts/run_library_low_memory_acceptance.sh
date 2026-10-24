#!/usr/bin/env bash
# One shell, real installed native runner: Gradle connected tests uninstall their package/data.
set -euo pipefail
report=core/data/build/reports/med02-generation-memory
mkdir -p "$report"
adb shell cat /proc/meminfo > "$report/guest-meminfo.txt"
adb logcat -G 16M
python3 -c 'from pathlib import Path; import re; text=Path("core/data/build/reports/med02-generation-memory/guest-meminfo.txt").read_text(); value=int(re.search(r"MemTotal:\s+(\d+)",text)[1]); assert 400*1024 <= value <= 512*1024, "Unexpected guest memory"'
set +e
./gradlew :core:data:assembleDebugAndroidTest --no-parallel --max-workers=2
result=$?
if [ "$result" -eq 0 ]; then
  adb install -r -t core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk
  result=$?
fi
if [ "$result" -eq 0 ]; then
  adb shell am instrument -w -e class net.plainnotes.app.data.NativeCiHeapProbe,net.plainnotes.app.data.LibraryRestoreAndroidTest,net.plainnotes.app.data.LibraryLifecycleAndroidTest -e ansmExpectedHeapMb 192 net.plainnotes.app.data.test/androidx.test.runner.AndroidJUnitRunner > "$report/native-tests.log" 2>&1
  result=$?
  cat "$report/native-tests.log"
  if [ "$result" -eq 0 ]; then
    python3 -c 'from pathlib import Path; t=Path("core/data/build/reports/med02-generation-memory/native-tests.log").read_text(); assert "OK (9 tests)" in t and not any(x in t for x in ("FAILURES!!!","INSTRUMENTATION_FAILED","INSTRUMENTATION_STATUS_CODE: -3")), "All nine real native tests must run and pass"'
    result=$?
  fi
fi
# Capture before another instrumentation/reinstall; never mask the original failure.
adb exec-out run-as net.plainnotes.app.data.test cat files/synthetic-library-memory.json > "$report/sampled-process-memory.json"
metrics=$?
if [ "$metrics" -eq 0 ]; then
  python3 -c 'import json; from pathlib import Path; m=json.loads(Path("core/data/build/reports/med02-generation-memory/sampled-process-memory.json").read_text()); assert m["api"]==26 and m["sampledNotPeak"] is True and len(m["samples"])>=1'
  metrics=$?
fi
if [ "$result" -eq 0 ] && [ "$metrics" -ne 0 ]; then result=1; fi
if [ "$result" -eq 0 ]; then
  python3 scripts/run_library_restore_restart.py
  result=$?
fi
adb logcat -d > "$report/logcat.txt"
exit "$result"
