#!/usr/bin/env python3
"""Two bounded real-process seams for production ciphertext quarantine; never claims activation."""
import json
import subprocess
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PACKAGE = "net.plainnotes.app.data.test"
REPORT = ROOT / "core/data/build/reports/med02-restore-restart"
COMPONENT = PACKAGE + "/androidx.test.runner.AndroidJUnitRunner"


def main():
    assert subprocess.check_output(["adb", "shell", "getprop", "ro.kernel.qemu"], text=True).strip() == "1", "Emulator only"
    REPORT.mkdir(parents=True, exist_ok=True)
    subprocess.run(["adb", "install", "-r", "-t", str(ROOT / "core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk")], check=True)
    for seam in ("COPY", "VERIFIED"):
        base = ["adb", "shell", "am", "instrument", "-w", "-e", "class",
                "net.plainnotes.app.data.AttachmentRestoreRestartAndroidTest#quarantineNeverActivatesAfterActualProcessDeath",
                "-e", "restore_seam", seam, "-e", "restore_phase"]
        process = subprocess.Popen(base + ["prepare", COMPONENT], stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
        try:
            deadline = time.monotonic() + 60
            while time.monotonic() < deadline:
                marker = subprocess.run(["adb", "shell", "run-as", PACKAGE, "cat", "files/synthetic-restore-restart-ready"], capture_output=True, text=True)
                if marker.returncode == 0:
                    try:
                        if json.loads(marker.stdout)["seam"] == seam:
                            break
                    except (ValueError, KeyError):
                        pass
                assert process.poll() is None, "Prepare exited before live seam"
                time.sleep(0.5)
            else:
                raise AssertionError("Restore seam not reached within bounded startup")
            assert process.poll() is None
            subprocess.run(["adb", "shell", "am", "force-stop", PACKAGE], check=True)
            output, _ = process.communicate(timeout=20)
            (REPORT / f"{seam}-killed.log").write_text(output)
            verified = subprocess.run(base + ["verify", COMPONENT], capture_output=True, text=True, timeout=90)
            (REPORT / f"{seam}-verify.log").write_text(verified.stdout + verified.stderr)
            assert verified.returncode == 0 and "OK (1 test)" in verified.stdout
            assert "FAILURES!!!" not in verified.stdout and "INSTRUMENTATION_STATUS_CODE: -3" not in verified.stdout
            print(f"PASS restore quarantine fresh PID: {seam}; old DB unchanged, unknown retained, no auto-activation, OFF", flush=True)
        finally:
            if process.poll() is None:
                subprocess.run(["adb", "shell", "am", "force-stop", PACKAGE], check=True)
                process.communicate(timeout=20)


if __name__ == "__main__":
    main()
