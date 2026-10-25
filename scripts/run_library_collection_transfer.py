#!/usr/bin/env python3
"""Transfer ONLY a synthetic encrypted collection between two freshly created CI guests.

The first guest executes the real A/B roundtrip. It is terminated by emulator-runner;
the second action recreates/wipes its AVD. No database, key envelope, alias or plaintext
original crosses guests. The digest/Android-ID receipt is neutral test evidence only.
"""
import hashlib
import json
import os
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
REPORT = ROOT / "core/data/build/reports/med02-generation-memory/fresh-device"
PACKAGE = "net.plainnotes.app.data.test"
NAME = "synthetic-collection-transfer.cipher"


def digest(file):
    h = hashlib.sha256()
    with file.open("rb") as source:
        for chunk in iter(lambda: source.read(65536), b""):
            h.update(chunk)
    return h.hexdigest()


def main(phase):
    assert phase in ("export", "restore")
    def adb(*args):
        return subprocess.check_output(["adb", *args], text=True).strip()
    assert adb("shell", "getprop", "ro.kernel.qemu") == "1", "Synthetic emulator only"
    assert adb("shell", "getprop", "ro.build.version.sdk") == "26"
    identity = adb("shell", "settings", "get", "secure", "android_id")
    assert re.fullmatch(r"[0-9a-f]{16}", identity), "Fresh guest identity not proven"
    head = os.environ.get("MED02_SOURCE_HEAD") or subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip()
    REPORT.mkdir(parents=True, exist_ok=True)
    file = REPORT / NAME
    if phase == "export":
        with file.open("wb") as out:
            subprocess.run(["adb", "exec-out", "run-as", PACKAGE, "cat", "files/" + NAME], stdout=out, check=True)
        assert 80 < file.stat().st_size <= 1024**3
        (REPORT / "source.json").write_text(json.dumps({"sourceHead": head, "sourceAndroidId": identity, "cipherSha256": digest(file), "bytes": file.stat().st_size}))
        return
    source = json.loads((REPORT / "source.json").read_text())
    assert source["sourceHead"] == head and source["cipherSha256"] == digest(file)
    assert source["bytes"] == file.stat().st_size
    assert source["sourceAndroidId"] != identity, "C must be a newly created/wiped guest, not another namespace in A"
    assert PACKAGE not in adb("shell", "pm", "list", "packages", PACKAGE), "Target package must not exist in fresh C"
    subprocess.run(["adb", "install", "-r", "-t", str(ROOT / "core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk")], check=True)
    subprocess.run(["adb", "shell", "run-as", PACKAGE, "mkdir", "-p", "files"], check=True)
    # Fixed private path, encrypted bytes streamed on stdin; never external plaintext staging.
    with file.open("rb") as incoming:
        subprocess.run(["adb", "shell", f"run-as {PACKAGE} sh -c 'cat > files/{NAME}'"], stdin=incoming, check=True)
    result = subprocess.run(["adb", "shell", "am", "instrument", "-w", "-e", "class",
        "net.plainnotes.app.data.NativeCiHeapProbe,net.plainnotes.app.data.LibraryCollectionAndroidTest#freshDeviceRestoresOnlyTransferredEncryptedCollection",
        "-e", "ansmExpectedHeapMb", "192", "-e", "collection_transfer", "restore",
        PACKAGE + "/androidx.test.runner.AndroidJUnitRunner"], capture_output=True, text=True, timeout=180)
    (REPORT / "target-native.log").write_text(result.stdout + result.stderr)
    assert result.returncode == 0 and "OK (2 tests)" in result.stdout and not any(x in result.stdout for x in ("FAILURES!!!", "INSTRUMENTATION_FAILED", "INSTRUMENTATION_STATUS_CODE: -3")), "Fresh-device C must really restore and independently compare every library"
    (REPORT / "target.json").write_text(json.dumps({"sourceHead": head, "targetAndroidId": identity, "cipherSha256": digest(file), "api": 26, "heapMiB": 192, "freshGuest": True, "native": "PASS_2", "physical": "NOT_RUN"}))
    print("PASS fresh guest C: only authenticated ciphertext transferred; complete business/history/typed links and empty retained root independently compared", flush=True)


if __name__ == "__main__":
    main(sys.argv[1])
