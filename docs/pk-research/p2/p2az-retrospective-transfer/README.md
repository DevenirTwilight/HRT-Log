# P2-AZ — retrospective leave-one-study-out E2 PK shape transfer

This phase actually reselects **32 one-input vs 216 dual-input** *new finite-grid* shape candidates using 2 of 3 **already exposed** published group-mean studies per fold, and holds out the third study **as a group**, not individual timepoints. It covers 18 figure/baseline scenarios × 3 groups = 54 (correlated, not independent) folds. Production Legacy is frozen. Historical 15 P2-X shapes are upstream preselected on all studies and **cannot** be described as blind holdout.

- [Complete Chinese scientific audit and numerical conclusions](P2-AZ-REPORT.md)
- [Frozen study/source/baseline design](p2az-design.json)
- [All 54 model selections and held-out scores](p2az-results.json)
- [Reproduction script](../../../../tools/pk-research/p2az_study_transfer.py)
- [12 synthetic/source isolation tests](../../../../tools/pk-research/test_p2az_study_transfer.py)
- [GitHub Actions exact JSON replay](../../../../.github/workflows/p2az-study-transfer.yml)

Run from repository root:

```bash
python3 -m unittest discover -s tools/pk-research -p 'test_p2az_study_transfer.py' -v
python3 tools/pk-research/p2az_study_transfer.py --output /tmp/p2az-replayed.json
cmp docs/pk-research/p2/p2az-retrospective-transfer/p2az-results.json /tmp/p2az-replayed.json
```

**Findings:** new M2 grid on exposed cohort means scored better than fixed Legacy in 54/54 conditional study-deletion scenarios, and better than newly constructed M1 in 48/54. This is retrospective cross-study *shape* evidence only, not independent unseen external patient PK validation. Formal AICc / likelihood, absolute individual pg/mL accuracy, and clinical model replacement are NOT established. P2-C HOLD. Price P2-I and P2-AJ are the same 6 people, not two external studies. Never republish protected figures or individual OSF files.
