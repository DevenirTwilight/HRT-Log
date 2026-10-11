# P2-BA — Study-level robustness and model-use evidence gates

P2-BA is stacked on P2-AZ research PR #17; this is a new evidence-use analysis, not refitting models. It takes 54 exposed-study sensitivity folds, correctly collapses the effective published human study units to **three** and computes worst/median conditional shape error ratios. Adds ten documented research-vs-clinical capability requirements with a *fail-closed* Python decision function. **No personal E2 data, new clinical validation, new Kotlin code or production replacement.**

- [Full Chinese scientific report](P2-BA-REPORT.md)
- [Exact CI-computed result with three-study sensitivity and 10 capability states](p2ba-results.json)
- [Research gate implementation](../../../../tools/pk-research/p2ba_evidence_use_gate.py)
- [15 synthetic/source/permission unit tests](../../../../tools/pk-research/test_p2ba_evidence_use_gate.py)
- [Dedicated replay workflow](../../../../.github/workflows/p2ba-evidence-use.yml)

From repository root:
```bash
python -m unittest discover -s tools/pk-research -p 'test_p2ba_evidence_use_gate.py' -v
python tools/pk-research/p2ba_evidence_use_gate.py --out /tmp/p2ba-replay.json
cmp docs/pk-research/p2/p2ba-evidence-use/p2ba-results.json /tmp/p2ba-replay.json
```

Source: `docs/pk-research/p2/p2az-retrospective-transfer/p2az-results.json`, pinned Git object SHA `38fb3ccc4521f8f0d683d2c755c0370539831ed9`. M2 beats Legacy in all three retrospective exposed group-shape scores for all 18 sensitivity settings, and beats M1 in all three only in 12/18 settings. **18 scenarios are correlated perturbations of 3 cohorts, NOT 18 independent external studies.** The frozen historical P2-X 15 were already exposed and should not be advertised as blind LOSO. Ten feature permissions are proposed scientific **contract rules**, not yet implemented in the Android UI. P2-C HOLD.
