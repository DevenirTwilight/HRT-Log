# PK literature review: oral spironolactone and oral micronized progesterone

Compiled 2026-10-06 for an open-source HRT tracking app. Every number below was read in the fetched abstract, full text or label at the location given. PubMed metadata (PMID/DOI) came from the PubMed tool. Label URLs were downloaded and their Reference IDs matched against the local copies in `src/`. Machine-readable version: `spironolactone_progesterone.json`.

---

## 1. Spironolactone (oral, Aldactone)

### 1.1 Structure and what is known

- **Prodrug-like parent, several active metabolites.** Spironolactone is "rapidly and extensively metabolized". Sulfur-containing metabolites predominate (FDA Aldactone 2014 label, Clinical Pharmacology). The two metabolite classes are sulfur-removed (canrenone) and sulfur-retained (7α-thiomethylspirolactone (TMS), 6β-hydroxy-7α-thiomethylspirolactone (HTMS)) (Overdiek & Merkus 1987 review abstract).
- **TMS, not canrenone, is the main circulating metabolite** after a single dose and at steady state (Overdiek 1985; Gardiner 1989). Older fluorometric "canrenone" assays were non-specific and "considerably overestimated true canrenone levels" (Overdiek 1987). This matters when you read pre-1985 canrenone half-lives such as Karim 1976 and Ho 1984.
- **Published parent→metabolite model (adults):** Tatipalli 2021 popPK (NONMEM, FOCEI). The parent is 2-compartment with first-order absorption plus a lag time. Canrenone is formed from the central parent compartment with Fm fixed at 0.7, and canrenone is itself 2-compartment. All parameters are apparent (/F) because no IV data exist. Data were healthy Indian males (N=92) given single doses of CaroSpir **oral suspension** (25/100 mg), fasting. The suspension has higher relative BA than Aldactone tablets, so CL/F and V/F are not directly transferable to tablets. TMS is not modelled.
- **PBPK (adult → paediatric)** parent + canrenone + TMS model: Soliman 2025 (abstract only read). It assumes complete CES1 conversion to CAN and TMS and captures the food effect. Parameter values were not extracted (see unverified leads).
- **Infants:** Lass 2023 used a one-compartment model for each of SPL, TMS and CAN. Not applicable to adults.
- **Steady state:** canrenone reaches steady state in about 3–4 days (Karim 1976, older assay). Accumulation ratios (AUC day 15/day 1) are 1.25–1.50 by specific HPLC (label/Gardiner).
- **Food:** food roughly doubles parent AUC (+95.4 % ± 66.9 %). Metabolite AUCs rise less: TMS +45 %, canrenone +41 %, HTMS +22 % (Overdiek 1986). The current US label states +95.4 %.
- **Binding:** >90 % plasma protein bound (label).

For an app model, the simplest defensible structure is parent (1–2 cmt, ka ≈ 5/h, lag ≈ 0.16 h, t½ ≈ 1.4 h) → canrenone and TMS (t½ ≈ 14–17 h). This is a parent→metabolite chain with first-order formation, and the Tatipalli parameters can be used if the tablet/suspension caveat is acceptable. Apply food as a multiplier on F (about 1.4–1.95 depending on analyte).

### 1.2 Parameter table (adults)

| Parameter | Value | Unit | Ref | Location | Population (n) | Notes |
|---|---|---|---|---|---|---|
| t½ parent (post-SS) | 1.4 (SD 0.5) | h | Gardiner 1989; FDA label 2014 | abstract; PK table | healthy M, 100 mg/d ×15 d (12) | label: "β half-life" |
| t½ canrenone | 16.5 (6.3) | h | Gardiner 1989; label | abstract; PK table | same (12) | terminal |
| t½ TMS | 13.8 (6.4) | h | Gardiner 1989; label | abstract; PK table | same (12) | terminal |
| t½ HTMS | 15.0 (4.0) | h | Gardiner 1989; label | abstract; PK table | same (12) | terminal |
| Cmax day 1 parent / CAN / TMS / HTMS | 72 (45) / 155 (43) / 359 (106) / 101 (26) | ng/mL | Gardiner 1989 | abstract | same (12) | first 100 mg dose |
| Cmax day 15 parent / CAN / TMS / HTMS | 80 (20) / 181 (39) / 391 (118) / 125 (24) | ng/mL | Gardiner 1989; label | abstract; PK table | same (12) | day-15 dose after low-fat breakfast |
| Tmax SS parent / CAN / TMS / HTMS | 2.6 / 4.3 / 3.2 / 5.1 | h | FDA label 2014 | PK table | same (12) | |
| AUC0-24 SS parent / CAN / TMS / HTMS | 231 (50) / 2173 (312) / 2804 (777) / 1727 (367) | ng·h/mL | Gardiner 1989 | abstract | same (12) | 100 mg/d |
| Accumulation AUC d15/d1 parent / CAN / TMS / HTMS | 1.30 / 1.41 / 1.25 / 1.50 | ratio | FDA label 2014 | PK table | same (12) | |
| Food: AUC0-24 parent fasting → fed | 288 ± 138 → 493 ± 105 | ng·h/mL | Overdiek 1986 | abstract | healthy, 200 mg single, crossover (9) | |
| Food: % ↑AUC parent / TMS / HTMS / CAN | 95.4 ± 66.9 / 45.4 ± 33.7 / 21.8 ± 21.5 / 40.7 ± 26.3 | % | Overdiek 1986 | abstract | same (9) | |
| Food (label wording) | "almost 100 %" (2014); "approximately 95.4 %" (current) | % ↑ BA | FDA 2014; Pfizer 12.3 | PK section | 9 | |
| Tmax parent, 200 mg with breakfast | 1 | h | Overdiek 1985 | abstract | healthy M (4) | detectable to 8 h |
| Protein binding | >90 | % | FDA label 2014 | PK section | – | |
| popPK ka / lag | 5.22 / 0.156 | 1/h, h | Tatipalli 2021 | Table 3 | healthy Indian M, suspension, fasting (92) | |
| popPK CL/F parent / V2/F / Q/F / V3/F | 629 / 517 / 89.9 / 777 | L/h, L | Tatipalli 2021 | Table 3 | (92) | |
| popPK CLM1/F (→CAN), Fm | 217 L/h; 0.7 fixed | | Tatipalli 2021 | Table 3 | (92) | total CL = CL(1−Fm)+CLM1·Fm |
| popPK CAN CLM/F / V4/F / Q1/F / V5/F | 17 / 189 / 60 / 448 | L/h, L | Tatipalli 2021 | Table 3 | (92) | |
| popPK BSV ka / CL / V2 / CLM1 / CLM | 0.9 / 0.166 / 0.118 / 0.18 / 0.08 | as reported (ω) | Tatipalli 2021 | Table 3 | (92) | variance vs CV not clearly stated |
| **TGW, SS (100 mg-normalised), GM (90 % CI)**: parent AUCτ | 329.74 (258.14–421.20) | ng·h/mL | Cattani 2023 | Table 4, PK1 | trans women on EV + SPL 100–200 mg, ~15 d, fasted (19) | LC-MS/MS |
| TGW parent Cmax,ss / Tmax,ss / Cmin | 85.05 / 1.61 / 1.06 | ng/mL, h, ng/mL | Cattani 2023 | Table 4 | (19) | |
| TGW parent CL/F / Vss/F / t½ | 303.27 / 2684.80 / 6.17 | L/h, L, h | Cattani 2023 | Table 4 | (19) | NCA t½ longer than label 1.4 h |
| TGW canrenone AUCτ / Cmax,ss / Tmax,ss / Cmin | 2135.41 / 147.86 / 3.17 / 48.41 | ng·h/mL, ng/mL, h, ng/mL | Cattani 2023 | Table 4 | (19) | |
| Canrenone t½ (older assays) | 19.2 ± 6.57 (200 mg QD); 12.5 ± 3.39 (50 mg QID) | h | Karim 1976 | abstract | healthy M (23) | non-specific assay |
| Canrenone SS max/min (200 mg QD) | ~500 / ~100 | ng/mL | Karim 1976 | abstract | (23) | non-specific assay, likely overestimated |
| Canrenone 2-cmt t½α / t½β (harmonic) | 1.66 / 22.6 | h | Ho 1984 | abstract | young volunteers (8) | base hydrolysis/fluorometry |
| Cirrhosis median terminal t½ SPL / TMS / CAN / HTMS | 9.04 / 23.9 / 57.8 / 126 | h | Sungaila 1992 | abstract | cirrhotic ascites (9) | special population |

### 1.3 Variability and limitations (spironolactone)

- Classic PK data are small (n = 4–12), mostly in healthy men. Gardiner 1989 (the source of the label table) is 12 healthy males. No sex-specific PK study exists in women or trans women other than Cattani 2023 (n=19, NCA only, parent + canrenone only, no TMS). Peeters 2022 found no sex difference in dose/time-adjusted concentrations (n=35, sparse TDM data, abstract).
- Implied CV% from the Gardiner SDs: parent Cmax day 1 ≈ 63 %, day 15 ≈ 25 %; TMS AUC ≈ 28 %; canrenone AUC ≈ 14 % (calculated by me from mean/SD, not reported as CV). The food-effect SD (±67 % on a +95 % mean) shows large inter-individual differences in the food response.
- Absolute bioavailability in humans is not established (no IV formulation). The 2014 label does not give F. The popPK parameters are all /F.
- Parent t½ depends on the sampling window. The label gives 1.4 h, while Cattani's 24 h NCA gives about 6 h, which may reflect a slower terminal phase or reconversion. Use 1.4 h for the distribution/elimination phase that dominates exposure.
- Assay era matters. Pre-HPLC (fluorometric) canrenone values are overestimates. Spironolactone also converts ex vivo to canrenone in stored plasma (Voicu 2011 Bioanalysis, PMID 21679029, abstract).
- Pharmacological activity of the metabolites in humans is "not known" (label). Relative potencies come from rat data.

### 1.4 Unverified leads (spironolactone)

- Soliman 2025 PBPK (PMID 40143132, PMC11944562): the full text was downloaded but parameter values were not extracted. It may contain adult CES1 conversion rates and a TMS model. Worth mining if a TMS chain is needed.
- Sadée W et al. 1970s papers on spironolactone/canrenone kinetics (author search returned PMIDs 4276758, 4853286, 4712657, 4699222, 4696105, 5044816; titles not reviewed).
- CaroSpir (oral suspension) FDA label: likely has fed/fasted and tablet-vs-suspension BA numbers. Not fetched.
- Food-effect study 084-15 (CMP Pharma; high-fat meal ≈ +90 % AUC0-∞) is cited only secondarily in Tatipalli 2021.

---

## 2. Oral micronized progesterone (Utrogestan, Prometrium 100/200 mg)

### 2.1 Structure and what is known

- **Absorption:** first-order (Simon 1993, n=15, "absorption and elimination were first-order processes"). Tmax is about 1–3 h (ANSM RCP), 1–4 h (NL SmPC) or "within 3 hours" (FDA). PK is linear and dose-proportional from 100 to 300 mg (FDA label; Simon 1993) and from 100 to 400 mg in men (FDA label).
- **Bioavailability:** the absolute value is "not known" (FDA Prometrium label). The NL SmPC says about 60 % of the dose is absorbed as progesterone + metabolites, and only **6–10 % remains unmetabolized** after hepatic first pass. Relative F vs IM progesterone is **8.6 %** (Simon 1993).
- **Food:** about 2-fold increase in absorption (AUC and Cmax), Tmax unchanged (Simon 1993). For the Bijuva softgel (different formulation, not micronized-in-peanut-oil), a high-fat meal raised Cmax by 162 % and AUC by 79 %, and median Tmax went from 2 to 3 h (FDA Bijuva label). The ANSM RCP advises taking it away from meals at bedtime. Bijuva is labelled "with food".
- **Elimination half-life:** **no reliable half-life is published for Prometrium or Utrogestan** in the labels. The best label-grade value is from Bijuva (oral progesterone 100 mg softgel, steady state): t½ 8.77 ± 2.78 h (n=13) and 9.98 ± 2.57 h (n=18). Health Canada notes levels stay above baseline 84 h after the last dose. The ANSM RCP recommends splitting the dose about 12 h apart. Short apparent decline after Cmax (ANSM mean profile: 11.75 ng/mL at 2 h → 2 ng/mL at 6 h → 1.64 ng/mL at 8 h) suggests a biphasic profile: fast distribution/first decline plus a slower terminal tail.
- **Steady state:** within 7 days (Bijuva label; Lobo 2019, accumulation ratios 1.36–1.94).
- **Metabolites:** pregnanediols/pregnanolones (FDA). 20α-hydroxy-Δ4-pregnanolone and 5α-dihydroprogesterone are the main plasma metabolites (ANSM). 95 % is eliminated in urine as glucuronides (ANSM).
- **Assay problem (critical for an app comparing to user lab results):** after oral dosing, direct RIA gave values **about 8× higher than LC-MS** (Levine & Watson 2000), and the authors call RIA "inappropriate" after oral progesterone. Prometrium label values (Cmax 17–61 ng/mL) and older studies (Maxson, Hargrove, Norman, ANSM time course) are immunoassay-era. By LC-MS, 100 mg oral gave Cmax 2.20 ng/mL (Levine 2000), and Bijuva 100 mg gave Cavg,ss 0.55–0.76 ng/mL. Treat these as different "truths" depending on the user's lab method.

For an app model, use a one-compartment model with first-order absorption (ka from Tmax ≈ 1.5–3 h). Choose the elimination rate to match a profile with a fast post-peak fall (apparent t½ of a few hours in the 2–8 h window, from the ANSM mean curve) plus a slower terminal phase (t½ ≈ 9–10 h, Bijuva, LC-MS-era). Calibrate the scale to the assay type: LC-MS values are about 1/8 of immunoassay values (Levine 2000). Food multiplies F by about 2 (Simon 1993). Population variability is large; see 2.3.

### 2.2 Parameter table

| Parameter | Value | Unit | Ref | Location | Population (n) | Notes |
|---|---|---|---|---|---|---|
| Cmax 100 / 200 / 300 mg/day (day 5) | 17.3 ± 21.91 / 38.1 ± 37.8 / 60.6 ± 72.5 | ng/mL | FDA Prometrium 2026 | Table 1 | postmenopausal women, 5 daily doses (n not stated) | implied CV 127 / 99 / 120 % |
| Tmax 100 / 200 / 300 mg | 1.5 ± 0.8 / 2.3 ± 1.4 / 1.7 ± 0.6 | h | FDA Prometrium 2026 | Table 1 | same | |
| AUC0-10 100 / 200 / 300 mg | 43.3 ± 30.8 / 101.2 ± 66.0 / 175.7 ± 170.3 | ng·h/mL | FDA Prometrium 2026 | Table 1 | same | implied CV 71 / 65 / 97 %; AUC only to 10 h |
| Absolute F | not known | – | FDA Prometrium 2026 | A. Absorption | – | |
| Fraction absorbed / unmetabolized after first pass | ~60 / 6–10 | % | CBG NL SmPC (RVG 11473) | 5.2 | – | proxy for systemic F |
| Relative F oral vs IM | 8.6 | % | Simon 1993 | abstract | postmenopausal women (15) | |
| Food effect | ~2× (AUC0-24 and Cmax up; Tmax unchanged) | – | Simon 1993 | abstract | (15), 200 mg ×5 d | FDA: ↑BA, magnitude not given |
| Food effect, Bijuva softgel | +162 Cmax, +79 AUC | % | FDA Bijuva 2026 | 12.3 Food Effect | postmenopausal, high-fat | other formulation |
| Mean time course after 200 mg | 0.13 → 4.25 (1 h) → 11.75 (2 h) → 8.37 (4 h) → 2 (6 h) → 1.64 (8 h) | ng/mL | ANSM Utrogestan RCP 2026 | 5.2 Voie orale | volunteers (n not given) | immunoassay era presumably |
| Tmax (labels) | 1–3 (ANSM); 1–4 (NL); "within 3" (FDA) | h | ANSM; CBG; FDA | 5.2 / A. Absorption | – | |
| Mean peak after 200 mg | 77.3 | nmol/L | Health Canada PM 2025 | 10.3 | postmenopausal | ≈24 ng/mL (my conversion); peak 2–4 h; above baseline 84 h after last dose |
| Bijuva SS AUC0-τ (0.5/100; 1/100) | 12.19 ± 11.01; 18.05 ± 15.58 | ng·h/mL | FDA Bijuva 2026 | Table 2 | healthy postmenopausal, fed, day 7 (17; 20) | baseline-adjusted |
| Bijuva SS Cmax | 4.40 ± 5.72; 11.31 ± 23.10 | ng/mL | FDA Bijuva 2026 | Table 2 | (17; 20) | implied CV 130 % / 204 % |
| Bijuva SS Cavg | 0.55 ± 0.45; 0.76 ± 0.65 | ng/mL | FDA Bijuva 2026 | Table 2 | (17; 20) | |
| Bijuva SS Tmax median (range) | 2.00 (0.67–8.00); 2.51 (0.67–6.00) | h | FDA Bijuva 2026 | Table 2 | | |
| Bijuva t½ | 8.77 ± 2.78; 9.98 ± 2.57 | h | FDA Bijuva 2026 | Table 2 + Elimination | (13; 18) | only labelled oral P4 t½ found |
| Steady state | within 7 days | – | FDA Bijuva 2026; Lobo 2019 | 12.3; abstract | | Lobo: accumulation 1.36–1.94 |
| Cavg,ss 100 mg (TX-001HR phase 1) | 0.66 | ng/mL | Lobo 2019 | abstract | postmenopausal (40) | |
| Levine LC-MS: Cmax / Tmax, 100 mg | 2.20 ± 3.06 / 1.00 ± 0.41 | ng/mL / h | Levine 2000 | abstract | postmenopausal (6 oral arm) | ± as reported (SD/SEM not stated in abstract) |
| Levine dose-normalized AUC0-24 | 0.035 ± 0.0052 | ng·h/mL per mg | Levine 2000 | abstract | (6) | i.e. ~3.5 ng·h/mL for 100 mg |
| RIA vs LC-MS bias after oral | ~8× higher by RIA | ratio | Levine 2000 | abstract | (6) | |
| Cmax 200 mg single | 17.0 ± 4.9 (Tmax 2.8 ± 0.35 h) | ng/mL | Maxson 1985 | abstract | 9 postmenopausal F + 1 M (10) | baseline by 24 h |
| Cmax 200 mg micronized in oil | 30.3 ± 7.0 (Tmax 2.0 ± 0.3 h) | ng/mL | Hargrove 1989 | abstract | (7) | formulation matters |
| Cmax range 200 mg | 8.5–70.6 | ng/mL | Norman 1991 | abstract | premenopausal women (10), RIA | ~8-fold inter-individual range |
| Protein binding | 96–99 (albumin 50–54, CBG 43–48) | % | FDA Prometrium 2026 | B. Distribution | – | |
| Urinary elimination as glucuronides | 95 | % | ANSM 2026 | 5.2 | – | |

### 2.3 Variability and limitations (progesterone)

- **Very large inter-individual variability.** Implied CV of Cmax is about 100–130 % (Prometrium Table 1) and up to 204 % (Bijuva Table 2). Norman 1991 found an 8.5–70.6 ng/mL range at 200 mg, and Bolaji 1993 reported "striking differences". Intra-individual PK is reported stable over months (ANSM, NL SmPC). That supports per-user calibration from the user's own lab values rather than a population prior.
- **Assay dependence dominates.** Labels and most older studies used immunoassays that cross-react with oral-route metabolites (5α-DHP, 20α-DHP, pregnanolones). LC-MS values are roughly an order of magnitude lower (Levine 2000; Bijuva vs Prometrium numbers). Prometrium Table 1 does not state the assay or n.
- **No published popPK model of oral micronized progesterone was found.** Simon 1993 confirms first-order kinetics but its abstract gives no ka, CL or t½.
- **Half-life is thin.** The only label-grade terminal t½ (≈9–10 h) is from Bijuva, a different formulation measured at steady state. Prometrium and Utrogestan labels give no t½. Most of the exposure after a dose is in the first 6–8 h.
- Populations are mostly postmenopausal cis women. Men are mentioned qualitatively in the FDA label. No trans-feminine PK data on oral progesterone were found (Dijkman 2023 RCT protocol, PMID 38124194, measures serum P4 but no results yet).
- The ANSM time course is a single mean curve with no n, assay or SD. Treat it as illustrative.

### 2.4 Unverified leads (progesterone)

- Simon 1993 full text (Fertil Steril 60:26–33): should contain the fed/fasted AUC, Cmax, t½ and ka values. Paywalled, not read.
- Stanczyk 1999 (J Reprod Med 44(2 Suppl):141–7, PMID 11392023): review of progesterone measurement methods and oral PK. Abstract only.
- Sitruk-Ware 1987 review (Contraception 36:373–402, PMID 3327648) and de Lignières 1999 (Clin Ther, PMID 10090424). The latter states that inter-/intra-individual AUC variability is similar to synthetic progestins. Neither full text was read.
- Wang 2021 (PMID 34912388): oral Utrogestan AUC geomean 413.68 and Cmax 129.85. Units and dose are not in the abstract, so do not use without the full text.
- Health Canada Prometrium monograph Table 4 is an embedded image and was not extracted.
- Prometrium label wording on PK in men ("generally consistent") has no numbers.

---

## 3. Overall assessment

| Topic | Strength |
|---|---|
| Spironolactone parent + canrenone/TMS/HTMS half-lives, Cmax, AUC, Tmax at steady state | **Solid** (Gardiner 1989 = FDA label table; n=12 healthy men) |
| Spironolactone food effect | **Solid** (Overdiek 1986, n=9; label) |
| Spironolactone parent→canrenone chain model | **Moderate** (Tatipalli 2021 popPK, n=92, but suspension, Indian males, Fm fixed, no TMS) |
| Spironolactone in trans women | **Thin but direct** (Cattani 2023, n=19, NCA, parent + canrenone) |
| Spironolactone absolute F / female-specific PK | **No reliable source found** |
| Progesterone Cmax/Tmax/AUC by dose | **Moderate** (FDA label Table 1; immunoassay-era, n not given) |
| Progesterone food effect | **Moderate** (Simon 1993 abstract "twofold"; Bijuva label numbers for another formulation) |
| Progesterone absolute F | **Thin** (label "not known"; NL SmPC 6–10 % unmetabolized; Simon 8.6 % relative to IM) |
| Progesterone elimination half-life | **Thin** (only Bijuva label, 9–10 h) |
| Progesterone inter-individual variability | **Solid qualitatively** (implied CV 100–200 %; multiple sources) |
| Progesterone assay cross-reactivity | **Solid** (Levine 2000: RIA about 8× LC-MS) |
| Progesterone popPK model | **No reliable source found** |
