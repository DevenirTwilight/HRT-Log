#!/usr/bin/env python3
"""Bounded synthetic process-death acceptance for the default-OFF production coordinator."""
import json
import subprocess
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PACKAGE = "net.plainnotes.app.data.test"
REPORT = ROOT / "core/data/build/reports/med02-operation-restart"
COMPONENT = PACKAGE + "/androidx.test.runner.AndroidJUnitRunner"


def main():
    assert subprocess.check_output(["adb", "shell", "getprop", "ro.kernel.qemu"], text=True).strip() == "1", "Emulator only"
    REPORT.mkdir(parents=True, exist_ok=True)
    subprocess.run(["adb", "install", "-r", "-t", str(ROOT / "core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk")], check=True)
    for boundary in ("PREPARED", "STAGED", "IN_COMMIT", "COMMITTED"):
        base = ["adb", "shell", "am", "instrument", "-w", "-e", "class",
                "net.plainnotes.app.data.MedicalAttachmentOperationRestartAndroidTest#safFacadeReconcilesRealCommitAfterFreshProcess",
                "-e", "operation_seam", boundary, "-e", "operation_phase"]
        process = subprocess.Popen(base + ["prepare", COMPONENT], stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
        try:
            deadline = time.monotonic() + 60
            while time.monotonic() < deadline:
                marker = subprocess.run(["adb", "shell", "run-as", PACKAGE, "cat", "files/synthetic-operation-ready"], capture_output=True, text=True)
                if marker.returncode == 0:
                    try:
                        state = json.loads(marker.stdout)
                        if state["seam"] == boundary:
                            break
                    except (ValueError, KeyError):
                        pass
                assert process.poll() is None, "Prepare exited before reaching the live seam"
                time.sleep(0.5)
            else:
                raise AssertionError("Production seam not reached within bounded startup")
            assert process.poll() is None, "Prepare must be alive when killed"
            subprocess.run(["adb", "shell", "am", "force-stop", PACKAGE], check=True)
            output, _ = process.communicate(timeout=20)
            (REPORT / f"{boundary}-killed.log").write_text(output)
            verified = subprocess.run(base + ["verify", COMPONENT], capture_output=True, text=True, timeout=90)
            (REPORT / f"{boundary}-verify.log").write_text(verified.stdout + verified.stderr)
            assert verified.returncode == 0 and "OK (1 test)" in verified.stdout
            assert "FAILURES!!!" not in verified.stdout and "INSTRUMENTATION_STATUS_CODE: -3" not in verified.stdout
            print(f"PASS scoped SAF facade fresh PID: {boundary}; original decrypts, uncommitted never publishes, budget retained, OFF", flush=True)
        finally:
            if process.poll() is None:
                subprocess.run(["adb", "shell", "am", "force-stop", PACKAGE], check=True)
                process.communicate(timeout=20)


if __name__ == "__main__":
    main()
