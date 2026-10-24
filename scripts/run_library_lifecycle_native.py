#!/usr/bin/env python3
"""Run the whole-UID synthetic inventory fixture before unrelated native fixtures.

Unrelated fixture roots intentionally retain aliases. They must not be silently adopted
by a production inventory, or deleted by a test pretending they belong to its root.
Only the emulator's synthetic instrumentation package is reset, never the real app.
Keep these four tests' reports independently of the subsequent full native suite.
"""
import shutil
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
REPORT = ROOT / "core/data/build/reports/med02-lifecycle-isolated"


def main():
    assert subprocess.check_output(["adb", "shell", "getprop", "ro.kernel.qemu"], text=True).strip() == "1", "Synthetic emulator only"
    REPORT.mkdir(parents=True, exist_ok=True)
    removed = subprocess.run(["adb", "uninstall", "net.plainnotes.app.data.test"], capture_output=True, text=True)
    (REPORT / "synthetic-package-reset.log").write_text(removed.stdout + removed.stderr)
    packages = subprocess.check_output(["adb", "shell", "pm", "list", "packages", "net.plainnotes.app.data.test"], text=True).splitlines()
    assert "package:net.plainnotes.app.data.test" not in packages, "Synthetic package must be absent before whole-UID inventory"
    try:
        result = subprocess.run(["./gradlew", ":core:data:connectedDebugAndroidTest",
            "-Pandroid.testInstrumentationRunnerArguments.class=net.plainnotes.app.data.LibraryLifecycleAndroidTest",
            "--no-parallel", "--max-workers=2"], cwd=ROOT)
        assert result.returncode == 0, "Isolated lifecycle native tests failed"
    finally:
        for name, source in (("html", "reports/androidTests"), ("results", "outputs/androidTest-results")):
            path = ROOT / "core/data/build" / source
            if path.exists():
                shutil.copytree(path, REPORT / name, dirs_exist_ok=True)


if __name__ == "__main__":
    main()
