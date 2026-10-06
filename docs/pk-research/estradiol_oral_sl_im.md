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

---

## 3. Sublingual estradiol (micronized E2 tablets held under the tongue); sublingual EV

**DATA ARE THIN.**
- No official label (FDA, EMA or ANSM) describes sublingual use. Provames, Estrofem and Oromone are labelled oral only.
- There are only small single-dose studies (n = 5–10) and a few steady-state trough observations.
- No study reports a full parameter set (ka, t1/2, F) with SD in an abstract.
- **Sublingual EV: no reliable source found.** PubMed searches for "sublingual AND estradiol valerate" returned no PK study.

**Structure (qualitative, all sources agree):**
- Absorption through the oral mucosa is very fast and "burst-like" (Price 1997). Peak E2 comes at 15 min to 1 h (Casper 1981; Fridriksdóttir 1996; Doll 2022).
- E2 then falls rapidly over about 2–6 h (Price 1997; Burnier 1981).
- E2 and E1 are higher than after the same oral dose. Early on the E1/E2 ratio is lower than with oral dosing, but over the day E1 still dominates (Casper 1981; Hoon 1993; Cortez 2024 troughs).
- Mechanistically this looks like two parallel inputs:
  - a fast mucosal fraction that skips first pass;
  - a swallowed fraction that behaves like oral dosing.
- **No published estimates of the split, the mucosal ka or the absolute F were found.**

| Parameter | Value | Unit | SD/range | Ref | Location | Population (n) | Notes |
|---|---|---|---|---|---|---|---|
| Cmax E2, single 1 mg SL | 144 | pg/mL | – | Doll 2022 | abstract | trans women (10) | LC-MS/MS; oral 1 mg 35 pg/mL |
| Tmax E2, 1 mg SL | 1 | h | – | Doll 2022 | abstract | (10) | first sample at 1 h |
| AUC0-8 SL/oral | 1.8 | fold | – | Doll 2022 | abstract | (10) | |
| E2/E1 ratio SL vs oral | 1.1 vs 0.7 | – | SD 1.0 vs 0.4 | Doll 2022 | abstract | (10) | |
| 2 mg SL: rise in 30 min | E2 ×41, E1 ×9 | fold | – | Casper 1981 | abstract | premenopausal follicular (6) + hypogonadal (3) | E1 predominant most of 24 h |
| 0.5 mg SL | E2 ×26 at 1 h; peak in first 2 h; baseline (24 pg/mL) by 24 h | – | – | Burnier 1981 | abstract | PM (5) | E1 max ×13 at 4 h |
| 0.5 mg SL plasma E2 | 133.2–320 | pmol/L | range | Fiet 1982 | abstract | PM (8) | ≈ 36–87 pg/mL |
| 100 µg E2-HPβCD SL tablet, Cmax | 568 | pmol/L | SD 97 | Fridriksdóttir 1996 | abstract | PM (6) | Tmax 15 min; cyclodextrin, not plain tablet |
| SL (1, 0.5, 0.25 mg) vs oral | rapid burst, E2 falls over 6 h, lower E1/E2 | – | – | Price 1997 | abstract | PM (6) | numbers in full text only |
| SL HPβCD 0.675 mg vs oral 1 mg | higher Cmax and AUC SL | – | – | Hoon 1993 | abstract | PM (5) | numbers in full text only |
| **Trough E2**, 6 mo, once-daily SL (mean 6.2 mg/d) | 95.3 | pg/mL | ±10.5 (labelled SD) | Cortez 2024 | Table 3 / Table 1 | trans women + spironolactone (13) | pre-dose; E2 by immunoassay |
| Trough E1, 6 mo, once-daily | 635.7 | pg/mL | ±81.3 | Cortez 2024 | Table 3 | (13) | E1 by LC-MS/MS |
| Trough E2, 6 mo, twice-daily (6.2 mg/d) | 79.4 | pg/mL | ±11.6 | Cortez 2024 | Table 3 | (14) | E1 532.9 ± 124.6 |
| Trough E2, 1 mo (2 mg/d start) | 52.6 (QD) / 55.2 (BID) | pg/mL | ±9.6 / ±5.2 | Cortez 2024 | Table 3 | (13/14) | conflicts with Results text values |
| E1/E2 ratio, clinic samples | 6.88 SL; 9.28 oral; 2.22 TD; 0.84 inj | – | – | Kariyawasam 2025 | abstract | transfeminine (286) | retrospective, timing uncontrolled |

Qualitative findings:
- Cirrincione 2021 (n=93, LC-MS/MS): E1 is higher with SL than with transdermal or injectable routes. E2 is similar.
- Yaish 2023: 0.5 mg four times daily SL causes "alarming excursions" of E2.

**Populations:**
- Postmenopausal cis women: 1980s–1990s studies, RIA era.
- Trans women: Doll 2022, Cortez 2024, Yaish 2023, Kariyawasam 2025, Cirrincione 2021.
- Men: no data found.

**Limitations:**
- Sample sizes are tiny.
- Hold time under the tongue and the swallowed fraction are not standardized.
- The 2022 study sampled only 0–8 h, starting at 1 h.
- The Cortez Table 3 "SD" values look like SEs, and Table 3 disagrees with the Results text.
- No intra-day concentration profile at steady state was found.
- Any app model of SL dosing (for example a fast-absorption fraction plus an oral fraction) would be an **assumption, not literature-derived**.

**Unverified leads:**
- Price 1997 full text: Cmax, Tmax, terminal t1/2, AUC and oral clearance for 0.25/0.5/1 mg SL vs 0.5/1 mg oral in 6 PM women. This is the best candidate for SL parameters but was not accessible here.
- Hoon 1993 full text.
- Doll 2022 full text: SDs, immunoassay results and E1 values.
- Loftsson 2003 (PMID 12779059, Pharmazie): SL cyclodextrin E2 with half-life. Its abstract is not available.

---

## 4. Estradiol valerate, intramuscular injection (Delestrogen / Progynon Depot)

**Structure:**
- The oily depot releases EV slowly. The Delestrogen PI says a single IM injection "is absorbed over several weeks", and gives no numbers.
- Released EV is hydrolysed to E2. Biotransformation is the same as after IV dosing (Düsterberg 1985).
- E2 is not subject to first pass, so E1 stays below E2 (Schug 2012 E1/E2 Cmax ≈ 0.4; clinic E1/E2 0.84 in Kariyawasam 2025).
- The kinetics are absorption rate-limited (flip-flop): the decline after the peak reflects release from the depot, not E2 elimination.
- Tmax is about 2 days and E2 stays elevated for about 7–8 days after 5 mg (Oriowo 1980). After 10 mg, E2 is still elevated at 10 days (Rauramo 1980).
- **No published compartmental model, ka or depot release half-life for IM EV was found in PubMed or label sources.**
- A one-compartment model with first-order (or dual first-order) depot absorption is the natural structure. Its parameters would have to be fitted to the Schug 2012 / Oriowo 1980 curves, which were not accessible as full text.

| Parameter | Value | Unit | SD/range | Ref | Location | Population (n) | Notes |
|---|---|---|---|---|---|---|---|
| Cmax E2, single 10 mg IM (Progynon Depot-10) | 505.7 | pg/mL | geometric mean | Schug 2012 | abstract | healthy PM (24) | GC-MS; test 543.5; measured values |
| AUC0-t E2, 10 mg | 82,660 | pg·h/mL | geometric mean | Schug 2012 | abstract | (24) | test 84,734; ~2-week sampling |
| Cmax / AUC0-t E1, 10 mg | 204.9 / 37,159 | pg/mL; pg·h/mL | geometric mean | Schug 2012 | abstract | (24) | test 219.0 / 38,950 |
| PD persistence | effects outlast plasma E2; 4-wk washout insufficient | | | Schug 2012 | abstract | (24) | |
| Tmax E2/E1, single 5 mg IM in arachis oil | ~2 | days | – | Oriowo 1980 | abstract | women on COC (9) | cypionate ~4 d |
| Duration of elevated E2/E1, 5 mg | 7–8 | days | – | Oriowo 1980 | abstract | (9) | none elevated at 2 wk |
| 10 mg IM (Primogyn Depot) | E2/E1 high at 24 h, still elevated at 10 d | | | Rauramo 1980 | abstract | castrated women | no numbers |
| 4 mg IM clinical duration | 2–4 | weeks | – | Düsterberg 1982 | abstract | climacteric women | therapeutic effect, not PK |
| Trans: share at 100–357 pg/mL mid-cycle, 3 / 4 mg weekly | 78.3 / 76.0 | % | – | Krikorian 2026 | abstract | AMAB adults (459) | EV; 46.0/42.0% at 100–200 pg/mL |
| Trans: median weekly dose reaching 100–200 pg/mL | 4.0 | mg | IQR 3.0–5.0 | Misakian 2025 JCEM | abstract | TGD (131/562) | EV+EC, IM+SC pooled; no route/ester difference |
| Trans: median E2 on weekly injections | 232 | pg/mL | IQR 134–371 | Misakian 2025 Endocr Pract | abstract | TGD (357) | median dose 4 mg; timing varies |
| Trans: mean E2, injectable group | 424.1 | pg/mL | – | Kariyawasam 2025 | abstract | transfeminine | ester/dose/timing not in abstract |

Rothman 2024 (scoping review) concludes that guideline doses of 2–10 mg weekly or 5–30 mg every 2 weeks are likely supraphysiologic, and suggests starting at ≤5 mg weekly.

**Inter-individual variability:** no CV% was found in any accessible abstract. Schug 2012 gives only geometric means, without SD, in the abstract.

**Populations:**
- PK studies: PM / castrated cis women (Schug 2012, Rauramo 1980) and young women on a COC (Oriowo 1980).
- Trans women: only retrospective, sparsely sampled clinic levels (useful for validating simulated steady-state levels, not for fitting).
- Men: no data found.

**Limitations:**
- Formal single-dose PK exists mainly as bioequivalence studies (Schug 2012), and their full texts were not accessible (Tmax, t1/2 and SD unknown here).
- The older studies used RIA and report qualitative or approximate results.
- No steady-state peak/trough PK study with controlled sampling was found.
- SC vs IM: no difference in the trans cohort (Misakian 2025), but that is not a controlled PK comparison.

**Unverified leads:**
- Schug 2012 full text: Tmax, t1/2 and baseline-corrected values for 10 mg IM EV in 24 PM women. This is the best candidate for fitting depot kinetics.
- Oriowo 1980 full text: daily E2 curves after 5 mg EV IM.
- Düsterberg 1985 full text: IV/IM EV parameters.
- Kanin 2025 (PMID 40170698; PMC11957913): weekly injectable estradiol 4.3→3.7 mg, final E2 248 pg/mL. The ester is not specified in the abstract, so it was not entered in the JSON.

---

## Summary of evidence strength

| Drug × route | Strength | Best sources |
|---|---|---|
| EV oral | **Solid** for Cmax/AUC/t1/2/Tmax in PM women (modern LC-MS/MS BE study with SD and CV%, plus labels). No ka or popPK. No trans/male data. | Zhang 2024; Natazia PI; Progynova RCP; Zimmermann 1998 |
| E2 micronized oral | **Solid**: absolute F, SS Cmax/Cmin/Cavg/AUC with SD, t1/2, accumulation. No ka or popPK. One small trans study. | Oromone RCP; Activella PI; Kuhnz 1993 |
| E2 sublingual | **Thin**: n = 5–10 single-dose studies, mostly qualitative in abstracts. One trans LC-MS/MS study (0–8 h). Trough-only trans RCT. No F, ka or t1/2. **SL EV: no reliable source found.** | Doll 2022; Price 1997 (abstract); Cortez 2024 |
| EV IM | **Moderate/thin**: one modern BE study (10 mg; geometric-mean Cmax/AUC only in abstract) and qualitative Tmax ~2 d / duration 7–8 d for 5 mg. No depot ka/t1/2 published in accessible sources. Trans cohort levels exist (retrospective). | Schug 2012; Oriowo 1980; Misakian 2025; Krikorian 2026 |

Conversion: 1 mg EV contains 0.764 mg E2 (PubChem MW 272.4 / 356.5).
Unit conversion: 1 pg/mL E2 = 3.671 pmol/L. This follows from the MW 272.4 (1000/272.4) and is a derived figure. Kariyawasam 2025 also states 1557 pmol/L = 424.1 pg/mL.
