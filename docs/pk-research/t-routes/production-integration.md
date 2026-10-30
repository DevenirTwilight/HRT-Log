# Sex-steroid formulation integration — 2026-10-30

Baseline `84489514efe638099650353329abfa9a79f2d871`; source research PR #173 `be60ba49728fd54ed32c01edbbc723dbea57488b`. This file describes real production wiring, **not clinical model approval**. Existing E2 estimates remain model-version sensitive and are not independently established as clinically accurate.

## Actual coverage audit

| Analyte/formulation | Actual runtime before this change | Candidate runtime | Evidence/limitations |
|---|---|---|---|
| E2 oral E2 / oral EV | PRODUCTION_ESTIMATE: `Engine.choose`, `ConcentrationCalculator`, ordinary page | unchanged | Existing literature fits/first pass; not new clinical validation |
| E2 sublingual E2 | PRODUCTION_ESTIMATE screen reference: M2 + `TotalE2Calibration` | unchanged | `m2_absolute_pgml=false`; P2 frozen; common coefficient not a T calibration |
| E2 sublingual EV | RECORD_ONLY, explicit unsupported | unchanged | Oral EV is not SL-EV evidence |
| E2 buccal | NOT_IMPLEMENTED as separate formulation | unchanged | No route-specific validated input/parameter set |
| E2 Oestrogel / EstroGel | PRODUCTION_ESTIMATE: product IDs 1/3, existing shared bucket | unchanged | Product/vehicle extrapolation caveat retained |
| E2 Divigel | PRODUCTION_ESTIMATE: ID 4 | unchanged | Existing fitted model |
| E2 Estreva ID 2 / unknown ID 5 | Existing fallback `E2_GEL_OTHER` with NO_PRODUCT_DATA | unchanged here | #169/#171 open separate Estreva work; not merged or validated by this PR |
| E2 patch apply/remove | PRODUCTION_ESTIMATE, recorded wear/replacement | unchanged | µg/day release rate is not total content |
| E2 EV IM | PRODUCTION_ESTIMATE | unchanged | Existing generic formulation/vehicle limitation |
| E2 EC/EB/EN/EU injection | RECORD_ONLY; enum/editor exists, engine rejects | unchanged | Same route does not establish absorption for a new ester/oil/SC route |
| E2 LENZETTO | NOT_IMPLEMENTED in live engine | unchanged | Steady summary envelope from #173 cannot describe first/missed/stopped doses |
| E2 implants / local vaginal products | NOT_IMPLEMENTED in live engine | unchanged | No established systemic product-specific response here; no transplanted E2 kernel |
| T ANDROGEL 50 mg | T record, no saved formulation selector or curve | RESEARCH_SHAPE_ONLY in original concentration destination | Three skin-input sensitivity rates, no fitted concentration amplitude |
| T ANDROGEL 16.2 mg/g | no model | RECORD_ONLY with explicit product | Different absorption (1–8.5% vs 9–14%); no sachet-parameter fallback |
| T ANDROTARDYL IM TE | no model | RESEARCH_SHAPE_ONLY | Explicit two-stage + 4.5d exponential sensitivity, not Japanese cohort's absolute gain |
| T NEBIDO IM TU | no model | RESEARCH_SHAPE_ONLY | Positive fast + 90d slow input, uncertain weights; no fabricated Cmax/Cmin fit |
| T XYOSTED SC TE | research steady envelopes only | RECORD_ONLY with explicit product | No first-dose response derived from steady summaries |
| T cypionate SC | sparse real study + pathological fitting counterexample | RECORD_ONLY with independent code | Counterexample not deployed |
| T cypionate IM | no production algorithm | RECORD_ONLY with independent code | No SC parameter reuse |
| T ANDRODERM / JATENZO / NATESTO / TESTOPEL | no live model | RECORD_ONLY with independent product choices | Wear/removal, food/lymphatic, rapid peaks, implant residuals remain unqualified |
| T propionate/mixed esters/other gels | no product-specific model | RECORD_ONLY unknown identity (do not choose another listed product) | Catalogue identity alone cannot choose a PK kernel |
| CPA/SPI/BICA etc. | own curves or explicit unsupported | unchanged | No fictitious T suppression or E2 aromatization |

## Production call chain / stored identity

`MainActivity → NotesApp/AppShell → existing concentration destination → ConcentrationScreen → E2/T selector → TestosteroneConcentration → same verified transaction records/rule snapshot → TestosteroneHistory → TestosteroneFormulations`.

The existing medication editor adds exact T formulation selection. A namespaced formulation/route code is stored in existing `medication.route` TEXT (e.g. `T_NEBIDO_IM`); catalogue entries explicitly retain actual route/ester. This is a composite formulation identity, **not a claim that a brand is an administration route**. No schema, table, profile field or backup version is added. Existing `MedicationSnapshot.encode` freezes that code in every new rule/actual history. Old null/generic T routes remain unknown; current choices/names/profiles never rewrite them. No JSON clinical record or model coefficient is persisted by the new screen. Old readers do not model T, so cannot reinterpret these codes as E2.

Actual nondeleted ON_TIME/LATE records only, actual timestamp and dose only. No schedule, current dose, fabricated missed dose, unknown volume conversion, future dose, or 180-day history cutoff. Each product remains separate (unknown gains prevent cross-product total). All inputs leave with the existing runtime/dataset UI state. E2 fit, E2 exported snapshots, calibration settings, Room, encrypted backup and medical attachments are unchanged.

## Research kernel and dimensional policy

All new displayed curves are **dimensionless normalized sensitivity scenarios**, not ng/mL/ng/dL or serum T. Endogenous baseline, feedback, conversion to E2 and clearance amplitude are not known. Never place T labs onto this axis. No current absolute T number or T calibration switch.

Causal convolution uses positive skin/exponential or Erlang-2+exponential inputs and an **assumed** one-hour parent removal half-life (not depot elimination). Products are not forced into one Bateman model:

- ANDROGEL50 A/B/C: input rates 1 / 1.5 / 2.5 per day (hypotheses retained from #173).
- ANDROTARDYL A/B/C: Erlang-2 input rate 0.5/day; exponential depot half-life 4.5d; slow weights 0.2 / 0.4 / 0.6 (sensitivity hypotheses, not identified physiology).
- NEBIDO A/B/C: Erlang-2 input rate 0.14/day; slow half-life 90d; slow weights 0.05 / 0.4 / 0.8 (hypotheses, not fit to disparate treatment stages).

Sum actual dose responses, preserve arbitrary intervals/changed/missed/stopped doses and old depot residuals, then normalize **each scenario's displayed-window maximum to 1**. Amplitude/gain is deliberately discarded; curves cannot compare windows or products. A/B/C are neither confidence intervals nor ranked fits. Pure within-formulation dose proportionality is an unvalidated research assumption. T ester mass fractions use molar-mass ratios TE 288.42/400.59, TC 288.42/412.61, TU 288.42/456.70; mL conversion is allowed **only** for the exact verified ANDROTARDYL250mg/1mL and NEBIDO1000mg/4mL codes (250mg compound/mL), preserving the recorded volume/unit unchanged. Unknown strengths, pump, patch contents and E2 equivalents never default to mass. Fresh ANSM response identities/hashes are in `acceptance/official-strength-verification.json`. `UG`/`NG` conversion is dimensional, not a new editor promise.

## Validation and independent evidence

Source scope, research groups, time points, methods/unknown methods and failed constraints remain in the copied #173 reports and source registry, not overwritten with a new success story. No full papers/images redistributed. Ichihara reproduction retained separately: 5 training times, 2 held-out times **same cohort**, train RMSE 0.06136 / held-out 2.82537 ng/mL. No external cohort validation; no absolute gain transplanted to ANDROTARDYL. Current source fulltext retrieval met a publisher access barrier; this round reproduced the already committed group summaries, not a freshly authenticated full table. Assay/formulation gaps remain evidence limitations.

See [local results](acceptance/local-results.json) for actually executed tests and original fixture failure. Native CI must execute the added two-process MainActivity T tests on API26/29/35 before any native PASS claim. Four-language UI uses existing components and original chart; no fake clinical axis or network permission. Physical devices / manual TalkBack are NOT_RUN. New absolute E2/T product models and the full requested route set are **not complete** merely because these research curves render.


### Failure/retry closeout

The first T failed-state retry only changed local retry state. A four-language target reproduced 16 failures (expected callback1, actual0). The repaired button invokes the existing production read; `ConcState` publishes its actual records/rule inputs only after existing runtime checks. T uses that one snapshot rather than `extra.records` from a separately refreshed list. No original E2 compute/fit argument changes or persisted fields. API29 source4d native4 cases passed before a later unchanged medical test failed; API35 lost the emulator in the old E2 reopen before T. Original receipts/partial outcomes are retained, not promoted into final-head native approval.
