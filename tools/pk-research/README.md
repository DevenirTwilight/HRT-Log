# P2 research CLI (not used by Android)

## P2-D：30min E2 上升比独立审计（研究工具，非生产模型）

作者上传 Komesaroff1998 原论文 p.2314 的 2mg、0/15/30min E2 均值±SEM 研究证据及推导见[科学附录](../../docs/pk-research/p2/p2d-komesaroff-onset-theory.md)。这不是训练集，也不增加独立验证研究数。正权重瞬时一阶核和 gamma2 的 30/15 增量比≤2；作者组均值约4.732，**不能据此推断显著性、配对方差或真延迟**。使用 Python 标准库：

~~~sh
python3 -m unittest discover -s tools/pk-research -p 'test_onset_bound.py'
python3 tools/pk-research/onset_bound.py --out /tmp/p2d-komesaroff-audit-UNUSED.json
~~~

必须使用不存在的输出路径（脚本拒绝覆盖）。读取正式参数及既有冻结研究设置和公开 Featherline 数学参数，生成**未重新拟合**的条件比较；临床有效性标志明确 false。历史 P0/P1 和 P2-A/B 输出保持只读。


Python 3.10+ standard library only. No install, network or imported production/GPL implementation. Existing formal parameters are **read-only**. Never run `tools/pk-fit/fit.py` for this work. Licensed originals remain outside Git and CI. Synthetic data are not human validation.

```sh
python3 tools/pk-research/check_protocol.py
python3 -m unittest discover -s tools/pk-research -p 'test_*.py'
python3 tools/pk-research/research.py --output /tmp/hrt-p2-new
```

`--inputs` selects the complete frozen catalog/protocol directory; every frozen file and old production/evidence hash is checked. `--validate` checks only. `--output` is mandatory and refuses nonempty directories (including the old results). Seed, all model/profile grids, amplitude bounds and study roles come from immutable `experiment-settings.json`/protocol. Altering scientific assumptions requires a new explicit protocol deviation, not changing defaults. No `--seed`/`--model` override that silently bypasses the freeze. Full A/B experiment list and four independently defined mathematical references are always included.

Read outputs: `analysis.json`, `candidate-parameters.json`, `study-comparisons.csv`, `report.md`, output hash manifest. `software_validation_passed=null` in scientific CLI means it does not infer engineering/CI success; verified test counts are separate in `verification.md` and engineering record. No clinical pass thresholds, imaginary SD, dense human sampling, or blind validation claims.

Candidate A gamma2 has two nominal parameters but fixes shape in advance and estimates only effective AUC scale. Candidate B fixes four shape assumptions and estimates one effective exposure. One observed time supports at most one rank; baseline scenarios are assumptions. Identifiability counterexamples/rank use synthetic outputs only. All 339 fit profiles including negative comparisons are retained; no cross-study composite ranking.
