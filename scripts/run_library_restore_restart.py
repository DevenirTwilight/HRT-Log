#!/usr/bin/env python3
"""Four real-process durability seams for internal full-library authority; synthetic emulator only."""
import json
import subprocess
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PACKAGE = "net.plainnotes.app.data.test"
REPORT = ROOT / "core/data/build/reports/med02-generation-restart"
COMPONENT = PACKAGE + "/androidx.test.runner.AndroidJUnitRunner"


def main(test_class="LibraryRestoreRestartAndroidTest", report=REPORT, marker_name="synthetic-generation-restart-ready"):
    assert subprocess.check_output(["adb", "shell", "getprop", "ro.kernel.qemu"], text=True).strip() == "1", "Emulator only"
    report.mkdir(parents=True, exist_ok=True)
    subprocess.run(["adb", "install", "-r", "-t", str(ROOT / "core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk")], check=True)
    for seam in ("PREPARED", "OBJECTS_COMMITTED", "IN_SWITCH", "SWITCHED"):
        base = ["adb", "shell", "am", "instrument", "-w", "-e", "class",
                f"net.plainnotes.app.data.{test_class}#persistentAuthorityChoosesExactlyOldOrVerifiedNewAfterProcessDeath",
                "-e", "generation_seam", seam, "-e", "generation_phase"]
        process = subprocess.Popen(base + ["prepare", COMPONENT], stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
        try:
            deadline = time.monotonic() + 60
            while time.monotonic() < deadline:
                marker = subprocess.run(["adb", "shell", "run-as", PACKAGE, "cat", f"files/{marker_name}"], capture_output=True, text=True)
                if marker.returncode == 0:
                    try:
                        if json.loads(marker.stdout)["seam"] == seam:
                            break
                    except (ValueError, KeyError):
                        pass
                assert process.poll() is None, "Prepare exited before live seam"
                time.sleep(0.5)
            else:
                raise AssertionError("Generation seam not reached within bounded startup")
            assert process.poll() is None
            subprocess.run(["adb", "shell", "am", "force-stop", PACKAGE], check=True)
            output, _ = process.communicate(timeout=20)
            (report / f"{seam}-killed.log").write_text(output)
            verified = subprocess.run(base + ["verify", COMPONENT], capture_output=True, text=True, timeout=90)
            (report / f"{seam}-verify.log").write_text(verified.stdout + verified.stderr)
            assert verified.returncode == 0 and "OK (1 test)" in verified.stdout
            assert "FAILURES!!!" not in verified.stdout and "INSTRUMENTATION_STATUS_CODE: -3" not in verified.stdout
            print(f"PASS durable authority fresh PID: {seam}; old facts retained, exactly old or authenticated new, OFF", flush=True)
        finally:
            if process.poll() is None:
                subprocess.run(["adb", "shell", "am", "force-stop", PACKAGE], check=True)
                process.communicate(timeout=20)


if __name__ == "__main__":
    main()
