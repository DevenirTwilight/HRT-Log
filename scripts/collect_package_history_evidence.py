#!/usr/bin/env python3
"""Collect synthetic B4-E idle-viewport evidence from the completed real Android test run."""
import argparse
import json
from pathlib import Path
import subprocess

MARKER = "B4E_PACKAGE_HISTORY_JSON="
LANGUAGES = ("en", "zh-CN", "zh-TW", "fr")


def collect(logcat):
    events = {}
    for line in logcat.splitlines():
        if MARKER not in line:
            continue
        event = json.loads(line.split(MARKER, 1)[1])
        if event["sdk"] != 35 or event["width_dp"] != 320 or event["font_scale"] != 2:
            raise ValueError("Expected API35 / effective 320dp viewport / 2x font")
        if not 0 <= event["composed_rows"] < 20:
            raise ValueError("Expected a bounded number of attached package semantics nodes")
        events[(event["locale"], event["kind"], event["total"], event["shown"])] = event
    for locale in LANGUAGES:
        for total in (1, 20, 21, 100, 500):
            if (locale, "first-page", total, min(total, 20)) not in events:
                raise ValueError(f"Missing first-page evidence: {locale}/{total}")
        for shown in range(20, 501, 20):
            if (locale, "all-batches", 500, shown) not in events:
                raise ValueError(f"Missing batch evidence: {locale}/{shown}")
        for kind, total, shown in (("completed-history", 500, 500), ("voided-first-page", 100, 20), ("completed-voided", 100, 100)):
            if (locale, kind, total, shown) not in events:
                raise ValueError(f"Missing completed evidence: {locale}/{kind}")
    return {"schema": "b4e-package-history-evidence-v1", "events": list(events.values()),
            "max_attached_package_rows": max(e["composed_rows"] for e in events.values()),
            "measurement_limit": "Synthetic Debug API35; effective Compose 320dp viewport via LocalDensity, 2x font. Attached semantics nodes, not total recomposition calls, CPU/FPS or physical ARM/Release."}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    logcat = subprocess.run(["adb", "logcat", "-d", "-s", "B4EPackages:I", "*:S"], check=True, capture_output=True, text=True).stdout
    report = collect(logcat)
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")
    print(f"PASS: B4-E {len(report['events'])} viewport measurements; maximum attached rows {report['max_attached_package_rows']}")


if __name__ == "__main__":
    main()
