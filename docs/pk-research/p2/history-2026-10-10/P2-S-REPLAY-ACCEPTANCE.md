# P2-S — full repository source replay acceptance

**Status:** PASS — computational reproducibility only, **not a clinical external validation**.

## Verified inputs
- Original archived P2-S ZIP in `source-packages/P2-S-research-source.zip`.
- Original unmodified SHA-256: `05e550d9e274883f8a3bd549af523ad77b8a4b66da7387d250949783efee855a`.
- Real repository's already-exposed group-aggregate source files: `docs/pk-research/p2/p2e-source-metrics.json` (Rosano) and `docs/pk-research/p2/p2d-komesaroff-table1.json` (Komesaroff). No patient-level data was introduced.
- Original archived P2-S Python script, 11 tests and frozen result checkpoint; no scientific code was rewritten.

## Actual execution evidence — 2026-10-11
- GitHub Actions run: [P2-S archived research replay #38123783828](https://github.com/DevenirTwilight/HRT-Log/actions/runs/38123783828).
- Tested workflow commit: [`7f3089918ca3066245f1a78c8af06d38035f195d`](https://github.com/DevenirTwilight/HRT-Log/commit/7f3089918ca3066245f1a78c8af06d38035f195d).
- Original ZIP hash verification and ZIP CRC: **PASS**.
- Strict five-file allowlist / no unsafe overwrites or links: **PASS**.
- `python -m unittest discover -s tools/pk-research -p 'test_p2s_early_growth_audit.py' -v`: **PASS**, original 11 unit tests.
- `p2s_early_growth_audit.py --out ...` then bytewise `cmp` with original `p2s-early-growth-checkpoints.json`: **PASS**.
- Workflow job `replay-p2s`: **completed/success**, as independently read from the GitHub Actions job steps.

The earlier isolated-ZIP run reported 10/11 with a missing dependency error because the original study ZIP deliberately reuses P2-D/P2-E data in the main repository. It was not a detected numerical disagreement. The verified full-repository run resolves that environment gap.

## Safety / scope

This run establishes reproducibility of the **mathematical group-aggregate bounds under historical, already exposed data**. It does **not** establish new human observations, M2 superiority in untouched cohorts, accurate individual E2 pg/mL curves, clinical tail coverage or approval of P2-C. App release, production parameters, SQLite and HRT dosage logic remain unchanged.
