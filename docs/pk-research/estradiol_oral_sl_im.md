# Estradiol PK literature review: oral EV, oral micronized E2, sublingual E2, IM EV

Scope: data for an HRT tracking app's PK model. Every number below was read in the fetched abstract, full text or label. The location column says where. Machine-readable copy: `estradiol_oral_sl_im.json` in the same folder.
Abbreviations: E2 = 17β-estradiol, E1 = estrone, E1S = estrone sulfate, EV = estradiol valerate, PM = postmenopausal, SS = steady state, DNG = dienogest, MPA = medroxyprogesterone acetate.

General caveats:
- Many studies do **not** say whether endogenous E2 was subtracted. Baseline-corrected and uncorrected values cannot be compared directly.
- Older studies (before 2000) used immunoassays, which can over-read E2 at low concentrations.

---

## 0. EV to E2 molecular-weight conversion

| Parameter | Value | Unit | Ref | Location |
|---|---|---|---|---|
| MW estradiol valerate (C23H32O3) | 356.5 | g/mol | PubChem CID 13791 | PUG REST MolecularWeight |
| MW estradiol (C18H24O2) | 272.4 | g/mol | PubChem CID 5757 | PUG REST MolecularWeight |
| E2 mass fraction of EV | 0.764 | – | derived 272.4/356.5 | computed (not a published number) |

So 1 mg EV contains 0.764 mg E2, and 2 mg EV contains 1.53 mg E2. Hydrolysis to E2 is complete: the Progynova RCP says the ester is cleaved during absorption and first pass. Düsterberg & Nishino 1982 (abstract) say EV is "completely converted" to E2 + valeric acid and that EV and E2 are "virtually dose-equivalent".

---

## 1. Estradiol valerate, oral (Progynova)

**Structure (from labels/literature):**
1. Absorption is rapid and complete. EV is hydrolysed to E2 in gut mucosa and liver.
2. First pass is heavy: about 3% of the dose reaches the circulation as E2 (Progynova RCP; Natazia PI). E1 and E1S are produced in large amounts and E1S acts as a circulating reservoir.
3. The apparent terminal t1/2 after oral dosing is about 14–17 h. This is much longer than the intrinsic E2 t1/2 after IV dosing (not covered by these sources) and probably reflects E1S/enterohepatic recycling. It is therefore an apparent "flip-flop/reservoir" half-life.
4. A one-compartment model with first-order absorption and an apparent t1/2 of about 14–15 h is consistent with the reported Tmax and t1/2, but no published popPK model for oral EV was found.
5. Only one published ka was found (Saavedra 2004, 1.06, units not given in the abstract), and its other parameters look unreliable.

| Parameter | Value | Unit | SD/range | Ref | Location | Population (n) | Notes |
|---|---|---|---|---|---|---|---|
| Bioavailability as E2 | ~3 | % | – | Progynova RCP; Natazia PI | RCP 5.2; PI 12.3 | n/a | |
| Tmax | 4–6 | h | – | Progynova RCP | 5.2 | – | |
| Multiple- vs single-dose levels | ~2× | – | – | Progynova RCP | 5.2 | – | |
| Return to baseline after stopping 2 mg | 2–3 | days | – | Progynova RCP | 5.2 | – | |
| Metabolic clearance of E2 | ~30 | mL/min/kg | – | Progynova RCP | 5.2 | – | |
| Unbound E2 / SHBG-bound | 1–1.5 / 30–40 | % | – | Progynova RCP | 5.2 | – | |
| Vd (E2, after IV) | ~1.2 | L/kg | – | Natazia PI | 12.3 Distribution | – | |
| Terminal t1/2 (E2) | ~14 | h | – | Natazia PI | 12.3 Excretion | – | |
| Single 3 mg EV: Cmax / Tmax / AUC0-24 | 73.3 / 6 (median) / 1301 | pg/mL / h / pg·h/mL | Tmax range 1.5–12 | Natazia PI | 12.3 Absorption | fertile women | correction for endogenous E2 not stated |
| SS 2 mg EV (+3 mg DNG): Cmax | 70.5 | pg/mL | SD 25.9 | Natazia PI | Table 1 | fertile women (15) | Day 24 |
| SS Tmax | 3 (median) | h | 1.5–12 | Natazia PI | Table 1 | (15) | |
| SS AUC0-24 E2 | 1323 | pg·h/mL | SD 480 | Natazia PI | Table 1 | (15) | CV ≈ 36% |
| SS E1 Cmax / AUC0-24 | 483 / 7562 | pg/mL / pg·h/mL | SD 198 / 3403 | Natazia PI | Table 1 | (15) | |
| Food | E2 Cmax +23%, AUC unchanged | | | Natazia PI | 12.3 Food | | |
| Single 1 mg EV fasted (reference): Cmax | 20.47 | pg/mL | SD 13.42 (CV 65.6%) | Zhang 2024 | Table 2 | Chinese PM (24) | **baseline-corrected**, LC-MS/MS; test 18.90 ± 7.24 |
| AUC0-t / AUC0-inf | 543.52 / 575.99 | pg·h/mL | SD 191.37 / 215.47 | Zhang 2024 | Table 2 | (24) | sampled to 72 h |
| t1/2 | 14.48 | h | SD 4.17 | Zhang 2024 | Table 2 | (24) | |
| Tmax | 10 (median) | h | 0.67–14 | Zhang 2024 | Table 2 | (24) | fasting |
| Fed: Cmax / AUC0-t / t1/2 / Tmax | 33.98 / 692.17 / 14.41 / 2.0 | pg/mL, pg·h/mL, h, h | SD 11.97 / 203.79 / 8.84; Tmax 0.75–12 | Zhang 2024 | Table 2 | (30) | |
| Unconj. E1 fasted: Cmax / AUC0-t / t1/2 | 169.02 / 3660.96 / 12.92 | pg/mL, pg·h/mL, h | SD 53.45 / 1336.78 / 3.26 | Zhang 2024 | Table 2 | (24) | E1:E2 AUC ≈ 6.7 |
| Total E1 fasted: Cmax / AUC0-t | 11.79 / 148.52 | ng/mL, ng·h/mL | SD 2.94 / 52.68 | Zhang 2024 | Table 2 | (24) | |
| Intra-individual CV, E2 Cmax | 29.9 | % | – | Zhang 2024 | Table 3 | (24) | AUC0-inf 14.8% |
| Single 4 mg EV: Cmax | 42.9 | pg/mL | SD 21.0 | Zimmermann 1998 | abstract | PM (32) | test 39.8 ± 17.7 |
| Tmax | 10.0 | h | SD 5.9 | Zimmermann 1998 | abstract | (32) | test 8.2 ± 4.5 |
| AUC0-48 | 1015.2 | pg·h/mL | SD 555.2 | Zimmermann 1998 | abstract | (32) | |
| Terminal t1/2 | 15.0 | h | SD 4.8 | Zimmermann 1998 | abstract | (32) | test 16.9 ± 6.0 |
| Free E1 Cmax / t1/2 / AUC0-48 | 174.3 / 13.5 / 3485.1 | pg/mL, h, pg·h/mL | – | Zimmermann 1998 | abstract | (32) | |
| Conj. E1 Cmax / Tmax / t1/2 | 16.2 / 2.0 / 10.6 | ng/mL, h, h | – | Zimmermann 1998 | abstract | (32) | |
| Single 2 mg EV: Cmax | 104.89 | units not given (pg/mL presumed) | SD 26.96 | Saavedra 2004 | abstract | PM (15) | immunoassay (ECLIA) |
| AUC0-24 | 1900.30 | (pg·h/mL presumed) | SD 392.23 | Saavedra 2004 | abstract | (15) | |
| ka | 1.06 | (1/h presumed) | SD 0.31 | Saavedra 2004 | abstract | (15) | only ka found |
| t1/2 | 35.65 | (h) | SD 20.62 | Saavedra 2004 | abstract | (15) | outlier vs other studies |
| V/F | 16.29 | not given | SD 8.76 | Saavedra 2004 | abstract | (15) | identical to MRT → probable error, do not use |
| Accumulation ratio (E2 AUC) | 3.3 (predicted 1.7) | – | – | Zimmermann 2000 | abstract | PM (16) | EV 2 mg + DNG 2 mg; free E1 2.4, total E1 1.5 |
| Mean E2, 2 mg EV (Sisare) | ~30 (day 1) / ~60 (day 21) | pg/mL | – | Wiegratz 2001 | abstract | PM (50) | from baseline ~10 pg/mL |

Qualitative findings:
- Düsterberg 1985 (ovariectomized women) and Aedo 1990 (PM, n=8): daily oral EV did not accumulate.
- Järvinen 2004 (PM, n=46): age did not significantly change E2 PK. Its abstract gives no numbers.

**Inter-individual variability:**
- Between-subject CV for E2 Cmax and AUC is about 35–65% (Zhang 2024 Table 2; Natazia Table 1).
- Within-subject CV is about 15–30% (Zhang 2024 Table 3).

**Populations:** all PK data come from cis women (postmenopausal or fertile). **No oral EV PK study in trans women or men was found.**

**Limitations:**
- Exposure is clearly lower than with an equal mass of micronized E2 (Wiegratz 2001). Some of the difference is the 0.764 mass ratio.
- Accumulation estimates disagree: no accumulation (Düsterberg 1985, Aedo 1990), about 2× (RCP), and 3.3× (Zimmermann 2000).
- Fertile-women data (Natazia) include endogenous E2.

**Unverified leads (not used for numbers):**
- Düsterberg & Nishino 1982 and Düsterberg 1985: full texts may contain IV/IM/oral EV PK parameters. Only the abstracts were seen.
- Järvinen 2004: full text has steady-state E2 Cmax/AUC for 1 and 2 mg EV.
- Zimmermann 2000: full text has single-dose and 12-week E2 parameters.

---

## 2. 17β-estradiol, oral micronized (Provames / Estrofem / Oromone)

**Structure:**
- Absorption is rapid: Tmax 3–6 h in the RCPs, and 4–6 h for Oromone/Estrofem.
- Absolute bioavailability is low: about 5–6% (Kuhnz 1993; Oromone RCP).
- Most of the dose becomes E1/E1S on first pass. E1S then acts as a reservoir and goes through enterohepatic recycling. Provames RCP: levels fall after 6 h and can rise again.
- The apparent terminal t1/2 is 10–16 h (Oromone RCP) or about 14 h (Activella PI Table 3).
- Steady state is reached in about 1 week (Oromone RCP) or within 2 weeks (Activella PI). Accumulation is 33–47% (Activella PI).
- AUC is dose-proportional from 1 to 2 mg (Oromone RCP) and from 2 to 4 mg (Kuhnz 1993). At 8 mg it is less than proportional (Kuhnz 1993).
- No published popPK or ka value for oral micronized E2 was found. A one-compartment model with first-order absorption, F of about 5% and an apparent t1/2 of about 14 h fits these label data.

| Parameter | Value | Unit | SD/range | Ref | Location | Population (n) | Notes |
|---|---|---|---|---|---|---|---|
| Absolute F (4 mg oral vs 0.3 mg IV) | 4.9 | % | SD 5.0 | Kuhnz 1993 | abstract | young women (14) | high intra/inter variability |
| Absolute F | ~6 or less | % | – | Oromone RCP | 5.2 | – | |
| Free E1/E2 ratio | ~1.0 IV vs 8.8–19.8 oral | – | – | Kuhnz 1993 | abstract | (14) | |
| Tmax | 4–6 (Oromone, Estrofem); 3–6 (Provames, 2 mg) | h | – | ANSM RCPs | 5.2 | – | |
| t1/2 | 10–16 (Oromone); ~14–16 (Estrofem) | h | – | ANSM RCPs | 5.2 | – | |
| **SS 2 mg/day E2: Cmax / Cmin (trough) / Cavg** | 89 / 35.0 / 62.9 | pg/mL | SD 16 / 13.4 / 15.6 | Oromone RCP | 5.2 table | healthy PM (n not stated) | wording mixes "SS" and "single dose" |
| SS AUC0-24 E2 | 1486 | pg·h/mL | SD 374 | Oromone RCP | 5.2 table | PM | |
| SS E1 Cmax / Cmin / Cavg / AUC0-24 | 591 / 208 / 392 / 9275 | pg/mL; pg·h/mL | SD 178 / 102 / 142 / 3389 | Oromone RCP | 5.2 table | PM | E1:E2 ≈ 6 |
| SS E1S Cmax / Cmin / Cavg / AUC0-24 | 25.9 / 5.7 / 13.1 / 307.3 | ng/mL; ng·h/mL | SD 16.4 / 5.9 / 9.4 / 224.1 | Oromone RCP | 5.2 table | PM | from E2 2 mg + dydrogesterone product |
| Protein binding | 98–99% (alb 30–52%, SHBG 46–69%) | | | Oromone RCP | 5.2 | | Activella: SHBG 37%, alb 61%, 1–2% free |
| Single 1 mg (+NETA): Cmax | 26.8 | pg/mL | geo CV 36% | Activella PI | Table 3 | healthy PM (24) | geometric mean, **baseline-unadjusted** |
| AUC0-t | 766.5 | pg·h/mL | geo CV 48% | Activella PI | Table 3 | (24) | |
| Tmax | 6.0 (median) | h | 0.5–16 | Activella PI | Table 3 | (24) | |
| t1/2 | 14.0 | h | geo CV 29% | Activella PI | Table 3 | (18) | |
| E1 Cmax / AUC0-t / t1/2 | 195.5 / 4469.1 / 10.7 | pg/mL; pg·h/mL; h | CV 37 / 48 / 44% | Activella PI | Table 3 | (24; t1/2 n=13) | |
| Accumulation at SS | 33–47 | % | – | Activella PI | 12.3 | PM | SS within 2 weeks |
| Food effect | none on E2 bioavailability | | | Activella PI | 12.3 | | |
| Single 2 mg: E2 peak | 110 | pg/mL | – | Yen 1975 | abstract | PM (9) | at 5 h; E1 peak 467 pg/mL at 6 h, 140 at 24 h; E1:E2 ≈ 3–6; RIA |
| SS mean E2 (Estrace, dose not in abstract) | 114.0 | pg/mL | SD 65.2 | Scott 1991 | abstract | PM (15) | marked peaks/nadirs; E1/E2 5.05 |
| Single 1 mg: E2 peak | 35 | pg/mL | – | Doll 2022 | abstract | **trans women (10)** | LC-MS/MS; peak at 8 h = last sample |
| 2 mg (Trisequens): mean E2 | ~40 (day 1) / ~80 (day 21) | pg/mL | – | Wiegratz 2001 | abstract | PM (50) | higher than 2 mg EV in same women |

**Inter-individual variability:**
- E2 AUC CV: about 25% (Oromone SS) to 48% (Activella single dose). Kuhnz 1993 reports high intra- and inter-individual variability (F 4.9 ± 5.0%, i.e. CV of about 100%).

**Populations:**
- Mostly postmenopausal cis women.
- Trans women: one small study (Doll 2022, n=10). It only sampled to 8 h, so oral Cmax/Tmax are probably truncated.
- Men: no oral micronized E2 PK data found.

**Limitations:**
- The Oromone table does not give n and is worded ambiguously (single dose vs steady state).
- The Activella data come with 0.5 mg NETA and are baseline-unadjusted.
- Many older studies used RIA.

**Unverified leads:**
- Kuhnz 1993 full text: IV E2 clearance, Vd and t1/2 in young women. The abstract is truncated, so no IV parameters were extracted.
- Cassidenti 1990 (PMID 2256508): 1–2 mg micronized E2 profiles in smokers vs non-smokers. No numbers in the abstract.
- Price 1997 (oral arms of 0.5/1 mg): oral clearance, t1/2 and AUC. Numbers are in the full text only.

