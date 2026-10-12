# P2-X → Kotlin v0.1: auditable source-to-kernel reproduction (2026-10-10)

## Scope and status

**Exploratory PK research; NOT a human PK accuracy claim or approval for release.** No Doll 144 pg/mL amplitude anchor, no newly blinded human holdout, no change to `Engine.kt`, `pk-params.json`, app UI, existing event database, private medical data or signed packages.

This research version keeps all **40 P2-X conditional M2 candidate optimizations** visible and checks which **15** were embedded in the separate Kotlin `ResearchSublingualV01` kernel. They arise from previously seen aggregate Rosano (n=25), Komesaroff (n=10), and **manually digitized** Price (n=6 crossover) E2 observations. They are **three studies, not 40 independent human cohorts**. Confidence, prediction and posterior intervals have NOT been calculated.

### Repository contents

- [Standalone reproduction source](../../../tools/pk-research/p2x_reproduce_standalone.py): includes three-source group aggregate input, the two-input Erlang–Bateman shape, study-specific amplitude/baseline profiling, the 40-point hypothetical baseline/stage grid, and deterministic 2-start SciPy optimization.
- [Frozen 40-row CSV](p2x-frozen-40-candidates.csv): complete parameter set (n, ke, kfast, kslow, effective slow weight), pseudo-loss, q6/q12/q24 exploratory indices, normalized 0–24h AUC, peak time and conditional Cortez counterfactual.
- [Automated source-to-Kotlin gate](../../../tools/pk-research/test_p2x_source_to_kernel.py): Python-standard-library test included in existing `jvm` CI test discovery. It hashes the complete CSV; tests the 40-combination design; reselects the 15 rows using best pseudoloss +0.10; parses `ResearchSublingualV01.kt` and compares all 9 key fields per chosen candidate. It also asserts research-only production flags.
- [M2 Kotlin implementation](../../../pk-engine/src/main/kotlin/net/plainnotes/app/pk/experimental/ResearchSublingualV01.kt) and [additional physical-model audit](p2ab-v01-kernel-integrity.md).

The original, longer P2-X script (`p2x_ensemble.py`) and `p2v_continuous_fit.py` were used locally to reproduce the archived full research JSON. Their **full original source code and 83KB JSON snapshot are not committed here**; only this independent compact implementation and the **exact original 40-row CSV** are in this PR. Thus the PR provides independently runnable **source-to-parameter** calculation but is not the entire original historical research report/archive.

## Numeric reproduction performed outside GitHub

The previously created local P2-X scripts were re-run with Python 3.13.5, NumPy 2.3.5, SciPy 1.17.0 and `OPENBLAS_NUM_THREADS=1`:

- **40/40** conditional optimization rows regenerated.
- Full original-format 83,665-byte P2-X JSON was **byte-identical** to the archived file; SHA-256 `7bb08b9e5b397e766cd63018567542520105755c9e578a8cdf4204a6df8d5367`.
- Original 9,444-byte CSV was **byte-identical**; SHA-256 `f13aa0a0b27883fe211ee604c7c1a8a413d4e81d7114da2a5c39057db8df0d1b`. Its CRLF line endings are intentionally preserved in the repository.
- **27** tests from original P2-X research bundle passed locally.
- The **new, standalone** source (`p2x_reproduce_standalone.py`) recalculated 40 rows and compared all source points/scores/rates with the original full JSON; the largest absolute parameter/score difference was **9.73e-6**. It is a **numeric equivalence test with tolerances**, not a byte-identical copy of the original implementation.
- The repository's 40-row CSV and new standalone source have been fetched back from GitHub and their Git blob IDs independently matched against local copies (`afed974c...b37` and `d3e0524...9a1`, respectively).
- The new stdlib-only source-to-Kotlin 4-test unit suite passed locally.

### How to reproduce from repository alone

Requires Python 3.13+; install `numpy==2.3.5`, `scipy==1.17.0` in an isolated environment, with BLAS threads constrained for determinism.

```sh
python3 -m venv .venv-p2x
. .venv-p2x/bin/activate
pip install numpy==2.3.5 scipy==1.17.0

# Regenerates 40 conditional candidates in a NEW location and refuses overwrite:
OPENBLAS_NUM_THREADS=1 python tools/pk-research/p2x_reproduce_standalone.py --out /tmp/hrt-p2x-independent.json

# Checks frozen CSV checksum, parameter identities and Kotlin production-disabled flags:
python3 -m unittest discover -s tools/pk-research -p 'test_p2x_source_to_kernel.py' -v
```

The separate `--compare-json` argument needs the **original full-format** P2-X JSON snapshot, which is not currently included in the repository; do not pass the compact reproduction JSON into it. The standard CI guard compares against the frozen complete 40-row CSV **without requiring NumPy or SciPy**.

## Statistical and biological limitations

- Rosano baseline is unknown and capped at 100 or 225 pmol/L hypothetically. Price Figure 1 baseline is manually assumed 0/6/12/18/24 pg/mL and must **not** be treated as a measured baseline distribution.
- Price AUC0–24 in Table 1 is not reproduced from the manual graph under the assessed assumptions; the discrepancy is unresolved.
- Figure digitization widths are not sampling SE; all studies have unknown repeated-measure covariance; the 15% model-discrepancy floor is analyst-assumed. The summed standardized residual **pseudo-loss is not a likelihood**, inferential chi-square, or statistical coverage metric.
- The 15-row subset is a fixed, analyst-selected `pseudo_loss <= best+0.10` slice; it has no probability calibration or posterior frequency interpretation.
- The effective slow-input weight is neither direct bioavailability nor a measured swallowed fraction. Long-terminal slow input can be flip-flop absorption and does not identify clinical elimination half-life.
- Cortez and Yaish were used only as secondary conditional repeat-dose tests in subsequent research; their published group statistics and unknown actual administration times cannot automatically calibrate an individual serum E2 concentration.

This change advances **reproducible engineering**, not clinical model promotion. Do not enable the experimental kernel in production without a separate sign-off and independent validation.
