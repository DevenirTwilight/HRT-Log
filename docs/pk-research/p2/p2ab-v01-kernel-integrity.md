# P2-AB — Experimental sublingual kernel integrity and functional-exposure audit

**Date:** 2026-10-10. **Scope:** research kernel v0.1, `ResearchSublingualV01`; no changes to the production PK engine or model parameters.

## Research inputs and provenance

- Frozen input: P2-X 40 conditional candidate fits from the same already-exposed Rosano 1997, Komesaroff 1998 and **manually digitized** Price 1997 group curves. No Doll 144 pg/mL anchor.
- Locally archived P2-X parameter source (`p2x-conditional-ensemble-results.json`) SHA-256:
  `7bb08b9e5b397e766cd63018567542520105755c9e578a8cdf4204a6df8d5367`.
- Original Python P2-X fit script `p2x_ensemble.py` SHA-256:
  `2ab50eabf2070fd46a66afe1ffa7da44b2e1599a09be6cc01ac359f0c3e4cb88`.
- Original common Python shape fit `p2v_continuous_fit.py` SHA-256:
  `4ab4cb87ca6671ab509be4dc67ad7c5a97e3dfb0fa300c52d034037727da096a`.
- Re-selected every P2-X row satisfying pseudoloss `<= min(loss)+0.1`, in original row order. A comparison of all **15 rows, 9 fields per row** (ID/order/rates/slow fraction/pseudoloss/two baseline assumptions) with embedded Kotlin candidates yielded maximum absolute mismatch **3.56e−15** (floating-point roundoff). No mismatched row.
- The archived P2-X source and complete upstream fit code are **not committed in this PR**. The SHA-256 strings are **provenance pointers**, not proofs that GitHub alone can rerun the source fitting or that human parameters are valid. Full source reproduction will require importing and independently reviewing those files.

## Key analytic invariant: normalized AUC to infinity

For both unit-mass absorption inputs `f_fast` (Erlang n-stage) and `f_slow` (one-step),
convolution with a one-compartment response yields

```text
h(t) = (1-w) * integral[0,t] f_fast(u) * exp(-ke*(t-u)) du
             + w * integral[0,t] f_slow(u) * exp(-ke*(t-u)) du
H(t) = h(t)/h(1)
```

With `ke > 0`, normalized unit-mass absorption `integral f = 1` and `0<=w<=1`, Tonelli/Fubini give

```text
integral[0,infinity] h(t) dt = ((1-w)+w)/ke = 1/ke
integral[0,infinity] H(t) dt = 1 / (ke * h(1))
```

Implemented as `ResearchSublingualV01.aucInfinityRelativeHours(candidate)`.
The result is **hours of dimensionless shape area**, not measured systemic E2 AUC, absolute F, volume, or a validated patient's exposure. Mathematical conservation does **not** establish physiological one-compartment elimination, mass balance in humans, or correctness of the fitted rates.

Independent SciPy numerical integration over 0–240 hours in 0.05-hour steps agreed with the analytic areas within **0.00010 h** for each candidate. A repository JUnit test performs independent trapezoid integration with tolerance 0.002 h.

## Candidate-conditional quantitative findings (15 near-optimal fits)

| Normalized quantity | Smallest | Largest |
|---|---:|---:|
| Model AUC0–24 h (numeric; hours) | 2.1914 | 3.4538 |
| Model AUC0–∞ (analytic; hours) | 2.1948 | 4.3202 |
| Fraction of model AUC beyond 24 h | 0.11% | 20.05% |
| Strictly hypothetical q24 predose index (240 previous doses, relative to 1mg-at-1h) | 0.000664 | 0.062994 |
| Fitted central-response half-time ln2/ke (h) | 0.476 | 0.733 |
| Fitted slow-input half-time ln2/ks (h) | 2.612 | 13.416 |

The central-response half-time and slow-input half-time are **distinct mathematical parameters**. With `ks < ke` for all these candidates, the long terminal tail is dominated by slow input (*flip-flop*); it would be misleading to report `ln2/ks` as measured systemic E2 elimination half-life. In addition, the mixture weight is an **effective fitted absorption-input weight**, not measured swallowed share or a proven fraction of drug reaching circulation.

The ~95-fold q24 ratio occurs at very small relative values and does not imply an observed 95-fold difference in human serum E2. The total normalized AUC∞ varies by ~1.97-fold; the extreme disparity is dominated by the *tail functional*, not a universal change to all exposure endpoints.

## Hypothetical Price-baseline stratification

The 15 candidate rows are **not** equally likely samples from a posterior or a clinical population. Their allocation is:

| Assumed Price Figure 1 baseline (pg/mL) | Candidate count | Conditional q24 index (min–max) | Conditional AUC∞ (h) |
|---:|---:|---:|---:|
| 0 | 1 | 0.062994 | 4.320 |
| 6 | 1 | 0.039235 | 3.627 |
| 12 | 2 | 0.019606–0.021278 | 3.022–3.088 |
| 18 | 5 | 0.002378–0.007497 | 2.415–2.595 |
| 24 | 6 | 0.000664–0.001841 | 2.195–2.274 |

A separate Rosano baseline cap produces a further imbalance: 5 candidates with cap 100 and 10 with cap 225 pmol/L. These counts arise from an arbitrary conditional grid plus an analyst-selected pseudo-loss slice, **not** patient prior probabilities. The global 15-candidate median is therefore *descriptive of this arbitrary selection* and must not be presented as the best estimate or the most likely individual curve.

The Kotlin function `spreadByAssumedPriceBaseline` returns explicit scenario-specific counts, minima, and maxima instead of concealing this imbalance inside the global median. Neither grouped ranges nor the old spread constitute a validated 90% prediction interval.

## Gates and next experiments

1. Preserve the isolated API until an independently approved product decision. Production `E2_SL`, migration, history and UI remain unchanged.
2. Do **not** treat the P2-X `0–24 pg/mL` manual baseline grid as observations or plausible probabilities; Price Figure 1/Table 1 AUC discrepancy remains unresolved.
3. The normalized infinite AUC endpoint allows structural integrity checks, not clinical dosage conversion.
4. Reconcile source-input checksums with full P2-X upstream datasets and scripts before claiming full reproducible source-to-APK traceability.
5. Consider reweighting by explicit, separately justified scenario hypotheses **only after** source uncertainty and baseline plausibility are established; never count one study's many conditional fits as independent humans.
6. An independently timed human SL dataset with baseline, assay, dosing history and early/late measurements remains necessary for prospective validation.

**Status:** exploratory only; no clinical efficacy, individual concentration accuracy, measured coverage or production model upgrade is established.

## Avoid stitched `median` trajectories

Every candidate is normalized to `H(1h)=1` by **construction**, so the pointwise spread at exactly 1 hour is always zero, despite uncertain early and late absorption. This is a *mathematical anchor*, not agreement across 15 independent human predictions.

The rank of candidates changes with time. For example, under a single hypothetical 1 mg dose, the identity of the candidate producing the pointwise median switches from `p2x-18` at 0.5 h to `p2x-34` at 6 h and `p2x-14` at 24 h. Joining pointwise medians would create a curve that is **not a member of the 15 fitted mechanistic candidate trajectories**. Analogous switching happens at the minima and maxima.

Therefore `coherentCandidateSeries(timeHours, doses)` exports each candidate ID and its complete aligned curve without switching parameters between timepoints. The existing pointwise `spread()` remains a descriptive summary **only** and must not be labelled a coherent PK model or an individual expected concentration. The time axis is required to be sorted and finite; series and model spread remain relative and not clinically calibrated.
