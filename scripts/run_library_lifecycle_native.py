#!/usr/bin/env python3
"""Run the whole-UID synthetic inventory fixture before unrelated native fixtures.

Unrelated fixture roots intentionally retain aliases. They must not be silently adopted
by a production inventory, or deleted by a test pretending they belong to its root.
Only the emulator's synthetic instrumentation package is reset, never the real app.
Keep these four tests' reports independently of the subsequent full native suite.
"""
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
REPORT = ROOT / "core/data/build/reports/med02-lifecycle-isolated"


def reset_fixture(label):
    removed = subprocess.run(["adb", "uninstall", "net.plainnotes.app.data.test"], capture_output=True, text=True)
    (REPORT / f"synthetic-package-reset-{label}.log").write_text(removed.stdout + removed.stderr)
    packages = subprocess.check_output(["adb", "shell", "pm", "list", "packages", "net.plainnotes.app.data.test"], text=True).splitlines()
    assert "package:net.plainnotes.app.data.test" not in packages, "Synthetic package must be absent before whole-UID inventory"


def main():
    assert subprocess.check_output(["adb", "shell", "getprop", "ro.kernel.qemu"], text=True).strip() == "1", "Synthetic emulator only"
    REPORT.mkdir(parents=True, exist_ok=True)
    reset_fixture("before-lifecycle")
    try:
        result = subprocess.run(["./gradlew", ":core:data:connectedDebugAndroidTest",
            "-Pandroid.testInstrumentationRunnerArguments.class=net.plainnotes.app.data.LibraryLifecycleAndroidTest,net.plainnotes.app.data.MedicalAttachmentOperationAndroidTest",
            "--no-parallel", "--max-workers=2"], cwd=ROOT)
        assert result.returncode == 0, "Isolated lifecycle native tests failed"
    finally:
        for name, source in (("html", "reports/androidTests"), ("results", "outputs/androidTest-results")):
            path = ROOT / "core/data/build" / source
            if path.exists():
                shutil.copytree(path, REPORT / name, dirs_exist_ok=True)
    reset_fixture("before-collection-integration")
    result = subprocess.run(["./gradlew", ":core:data:connectedDebugAndroidTest",
        "-Pandroid.testInstrumentationRunnerArguments.class=net.plainnotes.app.data.LibraryCollectionAndroidTest#restoredLibraryAddsOriginalAndExportsAllLibrariesToIndependentNewDevice",
        "--no-parallel", "--max-workers=2"], cwd=ROOT)
    try:
        assert result.returncode == 0, "Complete collection business roundtrip failed"
    finally:
        for name, source in (("html", "reports/androidTests"), ("results", "outputs/androidTest-results")):
            path = ROOT / "core/data/build" / source
            if path.exists():
                shutil.copytree(path, REPORT / "collection" / name, dirs_exist_ok=True)
    reset_fixture("before-collection-restart")
    subprocess.run([sys.executable, "scripts/run_library_collection_restart.py"], cwd=ROOT, check=True)
    # PREPARED release also inventories the entire UID, so its real restart seams
    # require the same isolation, not the unrelated bulk fixtures' retained keys.
    reset_fixture("before-restart")
    subprocess.run([sys.executable, "scripts/run_library_restore_restart.py"], cwd=ROOT, check=True)
    reset_fixture("before-operation-restart")
    subprocess.run([sys.executable,"scripts/run_attachment_operation_restart.py"],cwd=ROOT,check=True)
    reset_fixture("before-bulk")


if __name__ == "__main__":
    main()
