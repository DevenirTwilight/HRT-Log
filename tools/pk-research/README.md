# P2 research CLI (not used by Android)

Python 3.10+ standard library only. No install, network or imported production/GPL implementation. Existing formal parameters are **read-only**. Never run `tools/pk-fit/fit.py` for this work. Licensed originals remain outside Git and CI. Synthetic data are not human validation.

```sh
python3 tools/pk-research/check_protocol.py
python3 -m unittest discover -s tools/pk-research -p 'test_*.py'
python3 tools/pk-research/research.py --output /tmp/hrt-p2-new
```

`--inputs` selects the complete frozen catalog/protocol directory; every frozen file and old production/evidence hash is checked. `--validate` checks only. `--output` is mandatory and refuses nonempty directories (including the old results). Seed, all model/profile grids, amplitude bounds and study roles come from immutable `experiment-settings.json`/protocol. Altering scientific assumptions requires a new explicit protocol deviation, not changing defaults. No `--seed`/`--model` override that silently bypasses the freeze. Full A/B experiment list and four independently defined mathematical references are always included.

Read outputs: `analysis.json`, `candidate-parameters.json`, `study-comparisons.csv`, `report.md`, output hash manifest. `software_validation_passed=null` in scientific CLI means it does not infer engineering/CI success; verified test counts are separate in `verification.md` and engineering record. No clinical pass thresholds, imaginary SD, dense human sampling, or blind validation claims.

Candidate A gamma2 has two nominal parameters but fixes shape in advance and estimates only effective AUC scale. Candidate B fixes four shape assumptions and estimates one effective exposure. One observed time supports at most one rank; baseline scenarios are assumptions. Identifiability counterexamples/rank use synthetic outputs only. All 339 fit profiles including negative comparisons are retained; no cross-study composite ranking.
