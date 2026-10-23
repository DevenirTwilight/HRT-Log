"""Collect the isolated, synthetic, unapproved KDF probe before restart tests. No key material."""
import json
import pathlib
import subprocess

result = subprocess.run(
    ["adb", "shell", "run-as", "net.plainnotes.app.data.test", "cat", "files/synthetic-med02-format-readiness.json"],
    capture_output=True, text=True, timeout=30,
)
if result.returncode == 0:
    report = json.loads(result.stdout)
else:
    # AGP may uninstall its self-targeting test APK after connected tests. Preserve the same
    # completed run's logged result; never rerun the KDF to manufacture an artifact.
    logged = subprocess.run(["adb", "logcat", "-d", "-v", "raw", "-s", "MED02_FORMAT_RESOURCE:I", "*:S"],
                            capture_output=True, text=True, timeout=30, check=True)
    lines = [line for line in logged.stdout.splitlines() if line.startswith('{"api":')]
    assert len(lines) == 1, "Missing or ambiguous original KDF resource result"
    report = json.loads(lines[0])
assert report["api"] in (26, 29, 35)
assert (report["memory_kib"], report["iterations"], report["parallelism"]) == (65536, 3, 1)
assert report["independent_kat"] is True
assert report["formal_format_approved"] is False
assert report["joint_restore_budget_verified"] is False
assert report["scope"] == "TEST_ONLY_ISOLATED_KDF"
assert 0 < report["heap_limit_bytes"] <= 192 * 1024 * 1024
assert report["elapsed_ns"] > 0
destination = pathlib.Path("core/data/build/reports/med02-format-readiness/resource.json")
destination.parent.mkdir(parents=True, exist_ok=True)
destination.write_text(json.dumps(report, indent=2) + "\n")
print(f"Collected API{report['api']} isolated KDF resource evidence; format remains unapproved")
