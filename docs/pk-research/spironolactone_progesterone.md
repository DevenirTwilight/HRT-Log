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

