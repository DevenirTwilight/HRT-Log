#!/usr/bin/env python3
"""Run the two real synthetic operation cases first; retain their originals before bulk runs."""
from pathlib import Path
import shutil
import subprocess
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent
CLASS = "net.plainnotes.app.attachments.AttachmentOperationAndroidTest"


def main():
    result = subprocess.run(["./gradlew", ":app:connectedFullDebugAndroidTest",
        f"-Pandroid.testInstrumentationRunnerArguments.class={CLASS}", "--no-parallel", "--max-workers=2"], cwd=ROOT)
    destination = ROOT / "app/build/reports/med02-operation-isolated"
    for source, name in [("app/build/reports/androidTests", "html"),
                         ("app/build/outputs/androidTest-results", "results")]:
        path = ROOT / source
        if path.exists():
            shutil.copytree(path, destination / name, dirs_exist_ok=True)
    result.check_returncode()
    cases = [case for path in (destination / "results").rglob("TEST-*.xml")
             for case in ET.parse(path).getroot().iter("testcase") if case.get("classname") == CLASS]
    assert {case.get("name") for case in cases} == {
        "realSafPdfPngBinaryCommitReopenAndViewWithExistingAssociations",
        "cancelledFailureWrongRecordAndRevokedViewNeverCreateFalseSuccess"}
    assert len(cases) == 2 and all(not list(case.iter("skipped")) and not list(case.iter("failure"))
                                 and not list(case.iter("error")) for case in cases)


if __name__ == "__main__":
    main()
