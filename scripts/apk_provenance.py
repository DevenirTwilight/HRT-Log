#!/usr/bin/env python3
"""Reports the three separate identities of an APK (audit B1):

  apk_sha256        the file itself;
  signing_cert      SHA-256 of the signing certificate(s), via apksigner, or "unsigned/not checked";
  source            BuildConfig.SOURCE_* constants parsed from the DEX with dexdump (not a byte grep);
  package           applicationId, versionCode and versionName from the binary manifest (aapt2 dump badging).

The source fields say which commit the build claims to come from; they are not a cryptographic proof.
Usage: apk_provenance.py BUILD_TOOLS_DIR APK [--expect-source-sha SHA]
  --expect-source-sha  fail unless the APK names exactly SHA, read from a clean git checkout (CI self-check).
"""
import hashlib, json, re, subprocess, sys, tempfile, zipfile
import xml.etree.ElementTree as ET
from pathlib import Path

FIELDS = ("SOURCE_SHA", "SOURCE_COMMIT_TIME_UTC", "SOURCE_DIRTY", "SOURCE_ORIGIN")


def source_fields(dexdump_xml: str) -> dict:
    """BuildConfig constants of package net.plainnotes.app in one dexdump -l xml document."""
    root = ET.fromstring(dexdump_xml)
    found = {}
    for package in root.iter("package"):
        if package.get("name") != "net.plainnotes.app":
            continue
        for cls in package.iter("class"):
            if cls.get("name") != "BuildConfig":
                continue
            for field in cls.iter("field"):
                if field.get("name") in FIELDS and field.get("static") == "true" and field.get("final") == "true":
                    found[field.get("name")] = field.get("value")
    return found


def package_fields(badging: str) -> dict:
    """applicationId, versionCode and versionName from the first line of aapt2 dump badging."""
    line = next((l for l in badging.splitlines() if l.startswith("package:")), "")
    # Whole attribute names only: compileSdkVersionCodename='17' must not be read as name='17'.
    found = dict(re.findall(r"(?<![A-Za-z])(name|versionCode|versionName)='([^']*)'", line))
    return {"application_id": found.get("name"), "version_code": int(found["versionCode"]) if found.get("versionCode", "").isdigit() else None,
            "version_name": found.get("versionName")}


def certificates(build_tools: Path, apk: Path) -> list:
    apksigner = build_tools / "apksigner"
    if not apksigner.exists():
        return ["not checked: apksigner missing"]
    out = subprocess.run([str(apksigner), "verify", "--print-certs", str(apk)], capture_output=True, text=True)
    if out.returncode != 0:
        return ["unsigned or invalid signature"]
    return [line.split("certificate SHA-256 digest:", 1)[1].strip().replace(":", "").lower()
            for line in out.stdout.splitlines() if "certificate SHA-256 digest:" in line]


def main(build_tools: str, apk: str) -> dict:
    tools, path = Path(build_tools), Path(apk)
    source = {}
    with tempfile.TemporaryDirectory() as tmp, zipfile.ZipFile(path) as z:
        for name in sorted(n for n in z.namelist() if n.startswith("classes") and n.endswith(".dex")):
            dex = Path(tmp) / name
            dex.write_bytes(z.read(name))
            xml = subprocess.run([str(tools / "dexdump"), "-l", "xml", str(dex)], capture_output=True, text=True, check=True).stdout
            for key, value in source_fields(xml).items():
                if key in source and source[key] != value:
                    raise SystemExit(f"conflicting {key} in {name}")
                source[key] = value
    badging = subprocess.run([str(tools / "aapt2"), "dump", "badging", str(path)], capture_output=True, text=True)
    return {
        "apk": path.name,
        "package": package_fields(badging.stdout) if badging.returncode == 0 else "not read: aapt2 failed",
        "apk_sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
        "signing_cert_sha256": certificates(tools, path),
        "source": source or "absent (built before audit B1 or BuildConfig stripped)",
    }


def expect_source(report: dict, sha: str) -> None:
    """Raises unless the APK claims exactly sha, read from the project's own clean checkout."""
    source = report["source"] if isinstance(report["source"], dict) else {}
    wanted = {"SOURCE_SHA": sha, "SOURCE_ORIGIN": "git", "SOURCE_DIRTY": "false"}
    wrong = {k: source.get(k) for k, v in wanted.items() if source.get(k) != v}
    if wrong:
        raise SystemExit(f"APK source identity mismatch, expected {wanted}, got {wrong}")


if __name__ == "__main__":
    args = sys.argv[1:]
    expected = None
    if "--expect-source-sha" in args:
        i = args.index("--expect-source-sha"); expected = args[i + 1]; del args[i:i + 2]
    if len(args) != 2:
        raise SystemExit(__doc__)
    report = main(*args)
    print(json.dumps(report, indent=2))
    if expected is not None:
        expect_source(report, expected)
