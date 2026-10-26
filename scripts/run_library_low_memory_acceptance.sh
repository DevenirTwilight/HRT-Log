#!/usr/bin/env bash
# One shell, real installed native runner: Gradle connected tests uninstall their package/data.
set -euo pipefail
report=core/data/build/reports/med02-generation-memory
mkdir -p "$report"
adb shell cat /proc/meminfo > "$report/guest-meminfo.txt"
adb logcat -G 16M
python3 -c 'from pathlib import Path; import re; text=Path("core/data/build/reports/med02-generation-memory/guest-meminfo.txt").read_text(); value=int(re.search(r"MemTotal:\s+(\d+)",text)[1]); assert 400*1024 <= value <= 512*1024, "Unexpected guest memory"'
set +e
collection_installed=0
./gradlew :core:data:assembleDebugAndroidTest --no-parallel --max-workers=2
result=$?
if [ "$result" -eq 0 ]; then
  adb install -r -t core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk
  result=$?
  if [ "$result" -eq 0 ]; then collection_installed=1; fi
fi
if [ "$result" -eq 0 ]; then
  adb shell am instrument -w -e class net.plainnotes.app.data.NativeCiHeapProbe,net.plainnotes.app.data.LibraryRestoreAndroidTest,net.plainnotes.app.data.LibraryLifecycleAndroidTest,net.plainnotes.app.data.MedicalAttachmentOperationAndroidTest -e ansmExpectedHeapMb 192 net.plainnotes.app.data.test/androidx.test.runner.AndroidJUnitRunner > "$report/native-tests.log" 2>&1
  result=$?
  cat "$report/native-tests.log"
  if [ "$result" -eq 0 ]; then
    python3 -c 'from pathlib import Path; t=Path("core/data/build/reports/med02-generation-memory/native-tests.log").read_text(); assert "OK (13 tests)" in t and not any(x in t for x in ("FAILURES!!!","INSTRUMENTATION_FAILED","INSTRUMENTATION_STATUS_CODE: -3")), "All original twelve native tests plus the application transfer facade contract must run and pass"'
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
# Minimal postmortem before any new process. Diagnostics never replace the real test result.
adb shell dmesg > "$report/kernel-after-base.txt" 2>&1
adb shell cat /proc/meminfo > "$report/guest-meminfo-after-base.txt" 2>&1
base_result=$result
# Independent fresh instrumentation: a baseline crash must not hide new-path evidence.
# Preserve BOTH results; successful new cases cannot turn the original gate green.
collection_result=1
if [ "$collection_installed" -eq 1 ]; then
  collection_classes=net.plainnotes.app.data.NativeCiHeapProbe,net.plainnotes.app.data.MedicalArchiveAndroidTest#collectionWriterAndIndependentReaderPreserveTwoTypedLibrariesAfterSourceKeyLoss,net.plainnotes.app.data.MedicalArchiveAndroidTest#independentCollectionPreservesScopedCopiesAndAuthenticatesAllLibraries,net.plainnotes.app.data.MedicalArchiveAndroidTest#authenticatedCollectionMissingExtraCrossLibraryWrapAndInnerCorruptionAllRefuse
  adb shell am instrument -w -e class "$collection_classes" -e ansmExpectedHeapMb 192 net.plainnotes.app.data.test/androidx.test.runner.AndroidJUnitRunner > "$report/native-collection.log" 2>&1
  result=$?
  cat "$report/native-collection.log"
  if [ "$result" -eq 0 ]; then
    python3 -c 'from pathlib import Path; t=Path("core/data/build/reports/med02-generation-memory/native-collection.log").read_text(); assert "OK (4 tests)" in t and not any(x in t for x in ("FAILURES!!!","INSTRUMENTATION_FAILED","INSTRUMENTATION_STATUS_CODE: -3")), "Heap probe and all three real collection tests must pass"'
    result=$?
  fi
  adb exec-out run-as net.plainnotes.app.data.test cat files/synthetic-collection-memory.json > "$report/sampled-collection-memory.json"
  collection_metrics=$?
  if [ "$result" -eq 0 ] && [ "$collection_metrics" -eq 0 ]; then
    python3 -c 'import json; from pathlib import Path; m=json.loads(Path("core/data/build/reports/med02-generation-memory/sampled-collection-memory.json").read_text()); assert m["api"]==26 and m["sampledNotPeak"] is True and len(m["samples"])==5 and all(180*1024*1024 <= s["javaMaxBytes"] <= 192*1024*1024 for s in m["samples"])'
    collection_metrics=$?
  fi
  if [ "$result" -eq 0 ] && [ "$collection_metrics" -ne 0 ]; then result=1; fi
  collection_result=$result
fi
# New business integration runs independently; neither old baseline nor codec failures are masked.
integration_result=1
if [ "$collection_installed" -eq 1 ]; then
  adb shell am instrument -w -e class net.plainnotes.app.data.NativeCiHeapProbe,net.plainnotes.app.data.LibraryCollectionAndroidTest#restoredLibraryAddsOriginalAndExportsAllLibrariesToIndependentNewDevice -e collection_transfer export -e ansmExpectedHeapMb 192 net.plainnotes.app.data.test/androidx.test.runner.AndroidJUnitRunner > "$report/native-integration.log" 2>&1
  integration_result=$?
  cat "$report/native-integration.log"
  if [ "$integration_result" -eq 0 ]; then
    python3 -c 'from pathlib import Path; t=Path("core/data/build/reports/med02-generation-memory/native-integration.log").read_text(); assert "OK (2 tests)" in t and not any(x in t for x in ("FAILURES!!!","INSTRUMENTATION_FAILED","INSTRUMENTATION_STATUS_CODE: -3")), "Heap probe and complete independent-device collection roundtrip must pass"'
    integration_result=$?
  fi
  if [ "$integration_result" -eq 0 ]; then
    python3 scripts/run_library_collection_transfer.py export
    integration_result=$?
  fi
fi
adb shell dmesg > "$report/kernel-after-collection.txt" 2>&1
adb shell cat /proc/meminfo > "$report/guest-meminfo-after-collection.txt" 2>&1
printf '{"baseExit":%s,"collectionExit":%s,"integrationExit":%s}\n' "$base_result" "$collection_result" "$integration_result" > "$report/native-stage-results.json"
if [ "$base_result" -ne 0 ]; then result=$base_result; elif [ "$collection_result" -ne 0 ]; then result=$collection_result; else result=$integration_result; fi
if [ "$result" -eq 0 ]; then
  python3 scripts/run_library_restore_restart.py
  result=$?
  if [ "$result" -eq 0 ]; then python3 scripts/run_attachment_operation_restart.py; result=$?; fi
  if [ "$result" -eq 0 ]; then python3 scripts/run_library_collection_restart.py; result=$?; fi
fi
adb logcat -d > "$report/logcat.txt"
exit "$result"
