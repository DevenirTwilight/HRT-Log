#!/usr/bin/env python3
"""Explicit manual synthetic Room probe; no production source, dependencies, or Gradle/CI changes."""
import os
from pathlib import Path
import shutil
import subprocess
import sys

root = Path(__file__).resolve().parents[1]
large = os.environ.get("ANSM_PERF_LARGE") == "1"
probe = "LargeInventoryPerformanceProbe" if large else "AnsmInventoryPerformanceProbe"
source = root / f"docs/ansm-integration/p4/performance/{probe}.kt"
target = root / f"core/data/src/test/java/net/plainnotes/app/data/{probe}.kt"
if target.exists():
    sys.exit("Refusing to overwrite an existing test source")
count = int(os.environ.get("ANSM_PERF_USES", "100"))
if not 10 <= count <= 5000:
    sys.exit("ANSM_PERF_USES must be between 10 and 5000")
try:
    shutil.copyfile(source, target)
    command = [
        str(root / "gradlew"), "--no-daemon", "-Dorg.gradle.jvmargs=-Xmx1536m",
        "-Pkotlin.compiler.execution.strategy=in-process", "--max-workers=1", "--console=plain",
        ":core:data:testDebugUnitTest", "--tests", f"*{probe}",
    ]
    if os.environ.get("ANSM_PERF_COMPILE_NATIVE") == "1":
        command.append(":core:data:compileDebugAndroidTestKotlin")
    result = subprocess.run(command, cwd=root)
    sys.exit(result.returncode)
finally:
    target.unlink(missing_ok=True)
