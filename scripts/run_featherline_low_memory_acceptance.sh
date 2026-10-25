#!/usr/bin/env bash
# Real MainActivity/SAF/Argon2id/clinical transaction under the existing frozen API26 guest.
set -euo pipefail
report=app/build/reports/featherline-low-memory
mkdir -p "$report"
adb shell cat /proc/meminfo > "$report/meminfo.txt"
python3 - "$report/meminfo.txt" <<'PY'
from pathlib import Path
import re,sys
v=int(re.search(r'MemTotal:\s+(\d+)',Path(sys.argv[1]).read_text())[1])
assert 400*1024<=v<=512*1024
PY
./gradlew :app:assembleFullDebug :app:assembleFullDebugAndroidTest --no-parallel --max-workers=2
adb install -r -t app/build/outputs/apk/full/debug/app-full-debug.apk
adb install -r -t app/build/outputs/apk/androidTest/full/debug/app-full-debug-androidTest.apk
adb logcat -c
set +e
adb shell am instrument -w -e class net.plainnotes.app.imports.AutomaticImportAndroidTest -e featherlineExpectedHeapMb 192 net.plainnotes.app.test/androidx.test.runner.AndroidJUnitRunner > "$report/native.txt" 2>&1
result=$?
adb logcat -d > "$report/logcat.txt"
adb shell dmesg > "$report/kernel.txt" 2>&1
adb exec-out run-as net.plainnotes.app cat files/synthetic-featherline-memory.json > "$report/memory.json"
set -e
cat "$report/native.txt"
if [ "$result" -ne 0 ]; then exit "$result"; fi
python3 - "$report" <<'PY'
from pathlib import Path
import sys,json
p=Path(sys.argv[1]);s=(p/'native.txt').read_text()
assert 'OK (1 test)' in s and not any(x in s for x in ('FAILURES!!!','INSTRUMENTATION_FAILED','INSTRUMENTATION_STATUS_CODE: -3'))
m=json.loads((p/'memory.json').read_text());assert m['api']==26 and m['maxHeapBytes']==192*1024*1024 and m['sampledNotPeak'] and m['samples']>0
PY
