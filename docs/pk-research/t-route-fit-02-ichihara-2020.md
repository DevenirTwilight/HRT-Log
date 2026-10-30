# T_RCP_ROUTE_FIT_02 — Published human time points for testosterone enanthate

**2026-10-30. Research-only continuation of** [FIT_01](t-route-fit-01-report.md). No production PK, historical results, lab calibration or APK changes.

## New independent published study

Ichihara K, Masumori N, Fujii S, Toda T. *Pharmacokinetics of Testosterone Enanthate After Intramuscular Injection for Transgender Men*. Androgens: Clinical Research and Therapeutics (2020), DOI [10.1089/andro.2020.0002](https://doi.org/10.1089/andro.2020.0002). Full HTML Table 2: https://journals.sagepub.com/doi/full/10.1089/andro.2020.0002

The study has **N=10 Japanese transgender men**, single **250 mg testosterone enanthate IM** after study screening (four first-time recipients; others prior T with washout). This is **a different cohort** from the ANSM young-men label observations and must not be merged into a single fictitious subject.

| Day since injection | 0 | 1 | 2 | 3 | 7 | 14 | 21 | 28 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| Table 2 mean total T, ng/mL | 0.75 | 27.99 | 21.69 | 23.33 | 14.48 | 5.81 | 2.72 | 1.33 |
| Table 2 SD, ng/mL | 0.50 | 17.50 | 9.60 | 15.80 | 4.70 | 2.40 | 1.60 | 0.80 |

Author-reported mean **individual Cmax 29.4 ng/mL**, individual mean **Tmax 1.7 days**, apparent t1/2 mean **6.6 days**. Do not equate individual Cmax means with the highest group-mean Table 2 row or force all columns into a mechanistic one-patient fit. Table 3 source has an unusual textual formatting artifact around AUC SD; **no AUC is reproduced in this study bundle** until independently reconciled.

## Actual constrained fit (first, not external validation)

Executable `tools/pk-research/t_ichihara_2020_fit.py`: standard-library deterministic grid + local refinement. Fit group mean total T; fix mean baseline **0.75 ng/mL**; assume parent T plasma t1/2 **1h** from separate Nebido RCP (an assumption), slow depot component t1/2 **4.5d** from ANDROTARDYL RCP (cross-cohort prior).

- **Training times:** days **1, 3, 7, 14, 28**.
- **Pre-held-out times:** days **2 and 21**, same study/participants, not an independent cohort.
- **Model:** positive two-stage transit input + exponential depot component; nonnegative gain determined analytically for each shape. No day-specific tuning.

Numerical local prototype fit:
- Transit rate about **0.2236/day**; slow-component mass weight about **0.7874**. These are **conditional mathematical fit values, not identified physiological coefficients**.
- Training RMSE ~**0.0614 ng/mL** over five selected time points; held-out RMSE ~**2.8254 ng/mL** over day2/day21.
- Held-out predictions: day2 **25.67** vs observed **21.69**; day21 **2.41** vs observed **2.72** ng/mL. The largest discrepancy is day2, consistent with rapid early kinetics and interindividual variation not captured by the two-input aggregate fit.
- A simpler single exponential input with 4.5d depot t1/2 (same fixed mean baseline, one gain fitted over training points) has training RMSE ~**1.4794** and held-out RMSE ~**2.8812 ng/mL**. The small improvement in held-out fit **does not prove** the extra parameters generalize.
- At these sparse times an excellent training fit can be achieved even though plasma shape between observations remains uncertain. Full 24-hour PK, independent subjects, repeat doses, and baseline feedback **remain unsupported**.

## Cross-study and model qualification

The French RCP for ANDROTARDYL reports ~**20 ng/mL** at **1.5–3d** in young men, whereas this cohort reports much higher group-mean early values and average individual Cmax near 29.4. Comparing **peak time** can help assess shape plausibility, but importing the Japanese cohort's absolute gain to French RCP subjects would conflate population, assay, and baseline differences. E2 observed in the same cohort is **not** evidence of a universal T-to-E2 conversion model.

## Reproduction and release gate

`python3 tools/pk-research/t_ichihara_2020_fit.py /tmp/hrt-te-ichihara-fit.json`

The analogous local implementation was executed successfully with the reported numbers; execution of the exact pushed GitHub program and its CI after commit is **NOT_RUN / PENDING** until verified. This file is **not permission to activate a new absolute T serum curve in the product**. A real route-specific curve needs source provenance, numerical checks, realistic serum baselines, independent validation cohorts, real-world time-history freezes, and appropriate user-facing uncertainty labels.
