#!/usr/bin/env python3
"""Verify archived text source bytes only; this does not assess scientific validity."""
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[5]
MANIFEST = Path(__file__).with_name("SELECTION-MANIFEST.json")

def main():
    m = json.loads(MANIFEST.read_text(encoding="utf-8"))
    verified = 0
    for stage in m["stages"]:
        for row in stage["files"]:
            p = ROOT / row["path"]
            if not p.is_file():
                raise SystemExit(f"Missing archived research file: {p}")
            if hashlib.sha256(p.read_bytes()).hexdigest() != row["sha256"]:
                raise SystemExit(f"SHA-256 mismatch: {p}")
            verified += 1
    if verified != m["total_selected"]:
        raise SystemExit(f"File count mismatch: {verified} != {m['total_selected']}")
    print(f"Verified {verified} archived research files. No clinical validation implied.")

if __name__ == "__main__":
    main()
