#!/usr/bin/env python3
"""Fail-closed structural and SHA audit for historical HRT Log P2 research.

This checks archived non-sensitive public files only. It does not evaluate
human PK accuracy, import private OSF workbooks or authorize P2-C.
"""
import hashlib
import json
import re
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
P2 = REPO / "docs/pk-research/p2"
CATALOG = P2 / "HISTORICAL_RESEARCH_CATALOG.json"
MASTER = P2 / "HISTORICAL_RESEARCH_MASTER_INDEX.md"
HISTORY = P2 / "history-2026-10-10"
MANIFEST = HISTORY / "ARCHIVE_MANIFEST.md"
SOURCE = HISTORY / "source-packages"

CATEGORIES = {
    "NATIVE_UMBRELLA": 1, "GATE_HOLD": 1, "NATIVE_RESEARCH": 15,
    "ORIGINAL_ZIP_MERGED": 21, "SANITIZED_ZIP_MERGED": 2,
    "BACKFILL_NOT_ORIGINAL": 2, "ENGINEERING_RECORD_MERGED": 2,
    "RECONSTRUCTED_NOT_ORIGINAL": 1,
    "MERGED_PRIVATE_DATA_AGGREGATES_ONLY": 2,
    "MERGED_PUBLIC_METADATA": 1, "MERGED_MODEL_ONLY": 1,
    "DRAFT_PR_NOT_DEFAULT": 1,
}
SANITIZED_SHA = {
    "P2-AJ": "e2992e331d84ca9d3a96e170d9d6248bfda37371f72a17cbc86f2c6a430ba51d",
    "P2-AK": "9f724af0c031c631fb0d5c70e9c9010a51b503be6b9c57783faff3ef2d86f05a",
}
AT_RECON_SHA = "7dc26d75a1e3b3ade8d0c1942f8b03f9c657dc109295f16552230c7e3b08cf50"
AT_ORIGINAL_SHA = "a636beee77525043a69a8393d88d03b20d9ee14adf2e29c6995960b192b4c14a"

def digest(path):
    sha = hashlib.sha256()
    with path.open("rb") as inp:
        for chunk in iter(lambda: inp.read(1024 * 1024), b""):
            sha.update(chunk)
    return sha.hexdigest()

def load_original_manifest():
    s = MANIFEST.read_text(encoding="utf-8")
    records = {}
    for line in s.splitlines():
        m = re.match(r"^\| ([A-Z]{1,2}) \| .*? \| (\d+) \| `([a-f0-9]{64})` \|$", line)
        if m:
            phase, size, sha = m.groups()
            if phase in records:
                raise AssertionError("duplicate phase in manifest: " + phase)
            records[phase] = (int(size), sha)
    if len(records) != 24:
        raise AssertionError(f"expected 24 genuine historical metadata rows, got {len(records)}")
    if records["AT"] != (390066, AT_ORIGINAL_SHA):
        raise AssertionError("lost original AT checksum changed")
    return records

def audit():
    index = json.loads(CATALOG.read_text(encoding="utf-8"))
    if index["schema_version"] != 1 or index["scientific_release_gate"] != "P2-C HOLD":
        raise AssertionError("invalid catalog release gate")
    recs = index["records"]
    ids = [r["id"] for r in recs]
    if len(ids) != 50 or len(ids) != len(set(ids)):
        raise AssertionError("stage count or uniqueness changed: expected 50")
    count = {c: sum(r["category"] == c for r in recs) for c in CATEGORIES}
    if count != CATEGORIES:
        raise AssertionError("provenance category counts unexpectedly changed: " + repr(count))
    if any(r["category"] not in CATEGORIES for r in recs):
        raise AssertionError("unknown category")
    by_id = {r["id"]: r for r in recs}
    if by_id["P2-C"]["category"] != "GATE_HOLD":
        raise AssertionError("P2-C was wrongly approved")
    if by_id["P2-AX"]["category"] != "DRAFT_PR_NOT_DEFAULT":
        raise AssertionError("P2-AX's historical snapshot was silently changed")
    text = MASTER.read_text(encoding="utf-8")
    original = load_original_manifest()
    actual_archived = 0
    for r in recs:
        ident = r["id"]
        path = r.get("path")
        if path is not None:
            location = (P2 / path).resolve()
            if not location.is_relative_to(P2.resolve()) or not location.is_file():
                raise AssertionError(ident + ": document missing or outside P2: " + str(path))
            if f"({path})" not in text:
                raise AssertionError(ident + ": missing master link")
        elif ident != "P2-AX" or not r.get("external_pr"):
            raise AssertionError(ident + ": unexpected absent local document")
        if r["category"] in {"ORIGINAL_ZIP_MERGED", "SANITIZED_ZIP_MERGED"}:
            name = ident.removeprefix("P2-")
            archive = P2 / r["archive"]
            if archive != SOURCE / f"P2-{name}-research-source.zip" or not archive.is_file():
                raise AssertionError(ident + ": archived source ZIP missing")
            recorded_size, recorded_sha = original[name]
            if r["category"] == "ORIGINAL_ZIP_MERGED":
                if archive.stat().st_size != recorded_size or digest(archive) != recorded_sha:
                    raise AssertionError(ident + ": original archive byte verification failed")
            else:
                if digest(archive) != SANITIZED_SHA[ident]:
                    raise AssertionError(ident + ": redistributable archive hash failed")
                if archive.stat().st_size >= recorded_size:
                    raise AssertionError(ident + ": sanitized archive suspiciously large")
            actual_archived += 1
        elif r["category"] == "RECONSTRUCTED_NOT_ORIGINAL":
            archive = P2 / r["archive"]
            if ident != "P2-AT" or not archive.is_file() or digest(archive) != AT_RECON_SHA:
                raise AssertionError("AT reconstruction identity changed")
            if archive.stat().st_size != 34698:
                raise AssertionError("AT reconstruction size changed")
        elif r.get("archive"):
            raise AssertionError(ident + ": unexpected archive classification")
    if actual_archived != 23:
        raise AssertionError("expected 23 genuine historical source ZIP blobs")
    if "23/24" not in text or "P2-C HOLD" not in text:
        raise AssertionError("master archive/gate status missing")
    return {
        "catalog_records": len(recs),
        "source_zip_sha256_verified": actual_archived,
        "historical_original_byte_identical": 21,
        "copyright_redacted_zip": 2,
        "lost_original_at_present": False,
        "non_original_at_reconstruction_verified": True,
        "scientific_gate": "P2-C HOLD",
    }

if __name__ == "__main__":
    print(json.dumps(audit(), ensure_ascii=False, sort_keys=True, indent=2))
