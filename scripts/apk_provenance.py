#!/usr/bin/env python3
"""Reports the three separate identities of an APK (audit B1):

  apk_sha256        the file itself;
  signing_cert      SHA-256 of the signing certificate(s), via apksigner, or "unsigned/not checked";
  source            BuildConfig.SOURCE_* constants parsed from the DEX with dexdump (not a byte grep).

The source fields say which commit the build claims to come from; they are not a cryptographic proof.
Usage: apk_provenance.py BUILD_TOOLS_DIR APK
"""
import hashlib, json, subprocess, sys, tempfile, zipfile
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


def certificates(build_tools: Path, apk: Path) -> list:
    apksigner = build_tools / "apksigner"
    if not apksigner.exists():
        return ["not checked: apksigner missing"]
    out = subprocess.run([str(apksigner), "verify", "--print-certs", str(apk)], capture_output=True, text=True)
    if out.returncode != 0:
        return ["unsigned or invalid signature"]
    return [line.split(":", 1)[1].strip() for line in out.stdout.splitlines() if "certificate SHA-256 digest" in line]


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
    return {
        "apk": path.name,
        "apk_sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
        "signing_cert_sha256": certificates(tools, path),
        "source": source or "absent (built before audit B1 or BuildConfig stripped)",
    }


if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit(__doc__)
    print(json.dumps(main(sys.argv[1], sys.argv[2]), indent=2))
