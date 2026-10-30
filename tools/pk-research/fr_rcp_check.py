#!/usr/bin/env python3
"""Reproducible official ANSM RCP vs HRT Log model comparison (pure Python).

Values are group summary observations, never individual precision or a held-out validation.
Outputs a factual discrepancy matrix; it intentionally does NOT automatically rewrite parameters.
"""
from __future__ import annotations

import argparse
import json
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MODELS = ROOT / "pk-engine/src/main/resources/pk-params.json"
EVIDENCE = ROOT / "docs/pk-research/france-official-rcp-checkpoints.json"
PMOL_PER_PG = 3.671


def response(m: dict, hour: float, dose: float) -> float:
    if hour < 0:
        return 0.0
    ka = m["ka_per_h"]
    return max(0.0, dose * sum(
        t["A_per_mg"] * (math.exp(-t["lambda_per_h"] * hour) - math.exp(-ka * hour))
        for t in m["terms"]
    ))


def steady(m: dict, hour: float, dose: float, previous_doses: int = 120) -> float:
    return sum(response(m, hour + 24 * i, dose) for i in range(previous_doses))


def profile(m: dict, dose: float) -> dict:
    # Midpoint grid step 0.05h; extrema are bounded at both endpoints.
    n = 480
    vals = [steady(m, 24 * i / n, dose) for i in range(n + 1)]
    auc = 24 / n * (sum(vals) - (vals[0] + vals[-1]) / 2)
    return {"cmax": max(vals), "cmin": min(vals), "cavg": auc / 24, "auc24": auc}


def evaluate(models: dict, evidence: dict) -> list[dict]:
    rows: list[dict] = []
    for e in evidence["entries"]:
        m = models[e["model"]]
        p = profile(m, e["dose_mg"]) if e["regimen"] == "once_daily_steady" else {}
        for observed in e["observations"]:
            name = observed["statistic"]
            if name == "timepoint":
                predicted = response(m, observed["hour"], e["dose_mg"])
                # The model adds exposure on top of unspecified physiological baseline.
                # Subtract only the officially stated baseline from the observation.
                reference = observed["value"] - e.get("baseline_ng_ml", 0.0)
            else:
                predicted = p[name]
                reference = observed["value"]
                if e["unit"] == "pmol/L":
                    predicted *= PMOL_PER_PG
            difference = predicted - reference
            rows.append({
                "id": e["id"], "model": e["model"], "role": e["evidence_role"],
                "product": e["product"], "regimen": e["regimen"], "unit": e["unit"],
                "statistic": name, "hour": observed.get("hour"),
                "source": e["source"], "reported_total": observed["value"],
                "source_baseline_subtracted": e.get("baseline_ng_ml", 0.0) if name == "timepoint" else None,
                "reference_for_comparison": round(reference, 5),
                "predicted": round(predicted, 5),
                "delta": round(difference, 5),
                "ratio": round(predicted / reference, 5) if reference != 0 else None,
                "reported_sd": observed.get("sd"),
                "within_reported_one_sd": abs(difference) <= observed["sd"] if "sd" in observed else None,
            })
    return rows


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--json", action="store_true", help="emit machine JSON instead of compact text")
    args = parser.parse_args()
    params = json.loads(MODELS.read_text(encoding="utf-8"))
    evidence = json.loads(EVIDENCE.read_text(encoding="utf-8"))
    rows = evaluate(params["models"], evidence)
    if args.json:
        print(json.dumps({"schema": 1, "rows": rows}, ensure_ascii=False, indent=2))
    else:
        for r in rows:
            print(
                f'{r["id"]} {r["statistic"]}'
                f'{"" if r["hour"] is None else " @" + str(r["hour"]) + "h"}: '
                f'model={r["predicted"]:.3f}, ANSM={r["reference_for_comparison"]:.3f} '
                f'{r["unit"]}, ratio={r["ratio"]}'
            )


if __name__ == "__main__":
    main()
