#!/usr/bin/env python3
"""Append-only ledger of APKs actually delivered to the owner (owner decision D-4, REQUIREMENTS §67, 2026-10-12).

Every delivered APK gets a strictly larger versionCode than every APK delivered before it, keeps the applicationId
and the original signing certificate, and records three separate identities: the source commit, the APK file
SHA-256 and the signing certificate SHA-256. Development commits that are never delivered do not bump versionCode.

Usage: delivery_ledger.py check [LEDGER] [GRADLE_FILE]
       delivery_ledger.py next  [LEDGER]           prints the smallest versionCode the next delivery may use
"""
import json, re, sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LEDGER = ROOT / "docs/delivery/ledger.json"
GRADLE = ROOT / "app/build.gradle.kts"
HEX40, HEX64 = re.compile(r"[0-9a-f]{40}"), re.compile(r"[0-9a-f]{64}")
FIELDS = ("date", "version_code", "version_name", "application_id", "source_sha", "source_origin", "source_dirty",
          "apk_sha256", "signing_cert_sha256", "file_name", "channel")


def highest(ledger: dict) -> int:
    return max([ledger["pre_policy"]["max_version_code"]] + [d["version_code"] for d in ledger["deliveries"]])


def check(ledger: dict, gradle_text: str) -> int:
    """Raises ValueError on the first violation; returns the highest delivered versionCode."""
    previous = ledger["pre_policy"]["max_version_code"]
    seen = set()
    for d in ledger["deliveries"]:
        name = d.get("file_name", "?")
        missing = [f for f in FIELDS if f not in d]
        if missing:
            raise ValueError(f"{name}: missing {missing}")
        if not isinstance(d["version_code"], int) or d["version_code"] <= previous:
            raise ValueError(f"{name}: versionCode {d['version_code']} is not greater than the previous delivery ({previous})")
        if d["application_id"] != ledger["application_id"]:
            raise ValueError(f"{name}: applicationId {d['application_id']} differs from {ledger['application_id']}")
        if d["signing_cert_sha256"] != ledger["signing_cert_sha256"]:
            raise ValueError(f"{name}: signed with a different certificate; the installed app could not be updated in place")
        if not HEX40.fullmatch(d["source_sha"]) or d["source_origin"] != "git" or d["source_dirty"] is not False:
            raise ValueError(f"{name}: source must be a full commit read from a clean git checkout")
        if not HEX64.fullmatch(d["apk_sha256"]) or d["apk_sha256"] in (d["signing_cert_sha256"],) or d["apk_sha256"] in seen:
            raise ValueError(f"{name}: APK SHA-256 missing, reused, or confused with the certificate")
        seen.add(d["apk_sha256"])
        previous = d["version_code"]
    match = re.search(r"versionCode\s*=\s*(\d+)", gradle_text)
    if not match:
        raise ValueError("versionCode not found in the Gradle file")
    if int(match.group(1)) < previous:
        raise ValueError(f"Source versionCode {match.group(1)} is below the last delivered {previous}; a build from it could not update the phone")
    return previous


if __name__ == "__main__":
    args = sys.argv[1:]
    if not args or args[0] not in ("check", "next"):
        raise SystemExit(__doc__)
    ledger = json.loads(Path(args[1] if len(args) > 1 else LEDGER).read_text())
    if args[0] == "next":
        print(highest(ledger) + 1)
    else:
        top = check(ledger, Path(args[2] if len(args) > 2 else GRADLE).read_text())
        print(f"delivery ledger OK: {len(ledger['deliveries'])} recorded deliveries, highest delivered versionCode {top}, next delivery needs >= {top + 1}")
