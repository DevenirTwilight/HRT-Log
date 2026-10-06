# PK review: transdermal estradiol (gel, patch) and oral cyproterone acetate

Machine-readable data: `transdermal_cpa.json` (39 references, 154 entries). Each number below was read from the fetched label text or PubMed abstract cited in that row. "Abstract" means only the PubMed abstract was seen (no full text). Label texts are cached under `src/labels/`, `src/fr/`, `src/estradot/` and `src/cpa/`.

Population caveat for all three drugs: nearly all PK data come from postmenopausal cis women (estradiol) or cis men and young women (CPA). Transgender-specific PK studies were not found. The only transgender data are clinical monitoring levels and pharmacodynamic (PD) testosterone data.

---

## 1. Transdermal 17β-estradiol gel (EstroGel/Oestrogel 0.06%, Oestrodose 0.06%, Estreva 0.1%, Divigel 0.1%)

### Structure (what the sources support)
- **Input:** the stratum corneum acts as a depot. Absorption is passive diffusion, and the stratum corneum is the rate-limiting step (EstroGel and Divigel labels, §12.3). The Oestrodose RCP describes transient storage in the stratum corneum and slow diffusion from the dermal vasculature. A suitable model is a first-order or zero-order-like release from a skin depot into a one-compartment E2 pool. The depot is what drives the long apparent half-life.
- **Absorbed fraction:** about 10% of the applied dose (Oestrodose RCP §5.2: 1 pump = 0.75 mg E2, about 75 µg absorbed).
- **Intrinsic E2 elimination:** half-life about 1 h. Plasma clearance is 650–900 L/day/m² (Dermestril/Thais RCP §5.2).
- **Apparent terminal half-life after the last gel dose:** about 36 h (EstroGel label) vs about 10 h (Divigel label). The two disagree, and the study designs are not described. This reflects depot-limited (flip-flop) kinetics.
- **Time to steady state:** after the 3rd daily application (EstroGel 2.5 g), 4 days (Estreva), 3–5 days (Sirviö 2026, Divigel 1–1.5 mg), and "by day 12" (Divigel label).
- **Dose proportionality:** linear from 0.25 to 1.5 mg/day (Divigel label; Järvinen 2000; Sirviö 2026; Estreva: 3 g doubles the AUC of 1.5 g).

### Key parameters
| Parameter | Value | Unit | Source / location | Population, n |
|---|---|---|---|---|
| EstroGel 1.25 g (0.75 mg) day-14 Cmax | 46.4 | pg/mL | L_ESTROGEL §12.3 | PMW, 24 |
| EstroGel 1.25 g day-14 Cavg | 28.3 | pg/mL | L_ESTROGEL §12.3 | PMW, 24 |
| EstroGel apparent terminal t½ | ~36 | h | L_ESTROGEL §12.3 Excretion | PMW |
| Divigel 0.25 g: AUC0-24 / Cmax / Cavg (CV%) | 236 (94) / 14.7 (84) / 9.8 (92) | pg·h/mL, pg/mL | L_DIVIGEL Table 2 | PMW thigh, n not stated |
| Divigel 0.5 g | 504 (149) / 28.4 (139) / 21 (148) | as above | L_DIVIGEL Table 2 | |
| Divigel 1.0 g | 732 (81) / 51.5 (86) / 30.5 (81) | as above | L_DIVIGEL Table 2 | |
| Divigel tmax median (min, max) for 0.25 / 0.5 / 1.0 g | 16 (0,72) / 10 (0,72) / 8 (0,48) | h | L_DIVIGEL Table 2 | |
| Divigel apparent terminal t½ | ~10 | h | L_DIVIGEL §12.3 | |
| Divigel 0.5 / 1.0 / 1.5 mg day-14 Cavg | 12.4±7.0 / 29.7±7.3 / 55.0±33.1 | pg/mL | Sirviö 2026, abstract | PMW, 10 |
| Oestrogel 1.5 mg/day mean E2 | 68.1±27.4 | pg/mL | Scott 1991, abstract | PMW, 15 |
| Oestrogel 3.0 mg/day mean E2 | 102.9±39.9 | pg/mL | Scott 1991, abstract | PMW, 15 |
| Oestrodose 2 pumps (1.5 mg) mean E2 | 80 | pg/mL | RCP_OESTRODOSE §5.2 | PMW |
| Estreva 1.5 mg single-dose peak / steady-state C24 / day-22 peak | 40 / ~40 / 70 | pg/mL | RCP_ESTREVA §5.2 | PMW (400 cm² abdomen) |
| Absorbed fraction | ~10 | % | RCP_OESTRODOSE §5.2 | |
| Gel tmax (1.5 mg) | 4–5 | h | Järvinen 1999, abstract | PMW, 12 |
| Gel vs oral E2V 2 mg relative bioavailability | 61 | % | Järvinen 1999, abstract | PMW, 12 |
| Gel 1.5 mg vs 50 µg/d patch relative bioavailability | 109 | % | Järvinen 1999, abstract | PMW, 15 |
| Gel 1.0 mg/day ≈ 50 µg/d matrix patch (Cmax, AUC n.s.) | – | | Järvinen 2001, abstract | PMW, 24 |
| Variability: inter- and intra-individual CV of Cmax and AUC | ~30 (intra-individual AUC 21); total AUC CV 35 | % | Järvinen 2001, abstract | PMW, 24 |

### Application area, site and washing effects
- **Smaller area gives higher absorption.** 200 cm² gave 2× the AUC of an area "as large as possible". 200 cm² and 400 cm² did not differ markedly (Järvinen 1997, 1 mg on the thigh, n=16).
- **Washing:**
  - EstroGel: washing 1 h after application lowered Cavg by 22% (n=24).
  - Divigel: washing 1 h after application lowered AUC by 30–38% (n=16).
  - Järvinen 1997: washing at 30 min lowered AUC; no percentage is given in the abstract.
- **Lotions and sunscreen (EstroGel label, n=42):**
  - Moisturizer applied 1 h after the gel raised AUC by 38% and Cmax by 73%.
  - Sunscreen applied 1 h after the gel lowered AUC and Cmax by 16%.
- **Body site:** the Estreva RCP says bioavailability varies with application zone and between patients, but gives no number. Arm vs thigh vs abdomen were not compared quantitatively in any source I fetched.

### Decline after stopping
- The only quantitative descriptors are the apparent terminal half-lives: about 36 h (EstroGel) and about 10 h (Divigel).
- Sirviö 2026 followed patients for 3–4 days after the last dose, but the abstract gives no washout half-life.

### Limitations
- Data are mostly from older RIA assays (pre-2005), postmenopausal women and small n.
- Divigel CVs of 81–149% show very high between-subject variability.
- Oestrogel (FR) was not found in the current BDPM product list. Oestrodose (Besins, 0.06%, 0.75 mg/pump) is the French equivalent and was used instead.

---

## 2. Estradiol patch (Vivelle-Dot / Estradot, Climara, Dermestril/Thais, Femsept)

### Structure
- **Input:** near zero-order input during wear. "Zero-order, dose-proportional" is stated in Boyd 1996b and Boyd 1996a (abstracts).
- **Removal:** intrinsic E2 half-life is about 1 h (Estradot SmPC, Dermestril/Thais RCP). After removal, E2 returns to baseline within 12–24 h:
  - within 24 h: Vivelle-Dot, Estradot
  - within 12 h: Dermestril, Thais, Femsept, Setnikar 1996
  - 8–24 h: Setnikar 1997
- **Apparent half-life after Vivelle-Dot removal:** 5.9–7.7 h (label).
- **Time to steady state:** with the 2nd patch (Setnikar 1996, Setnikar 1998). Climara shows little or no accumulation over 3 weeks.
- **Suggested model:** zero-order input at the nominal rate × relative bioavailability, plus a short skin lag (E2 rises above baseline within 4 h for Vivelle and within about 6 h for the 7-day matrix patch), then one-compartment elimination with an apparent t½ of about 6–8 h after removal.
- **Twice-weekly vs weekly:** Setnikar 1998 found that a 7-day 50 µg patch gave bioavailability similar to two twice-weekly 50 µg patches. Weekly patches have a mid-wear peak (Climara Cmax roughly 2× Cmin, about 18–25 h after application) and a decline toward the end of wear.

### Key parameters
| Parameter | Value | Unit | Source / location | Population, n |
|---|---|---|---|---|
| Vivelle(-Dot) 0.0375 mg/d SS Cmax / Cavg / Cmin(84 h) | 46±16 / 34±10 / 30±10 | pg/mL | L_VIVELLEDOT Table 2 (not baseline-corrected; baseline 11.7) | PMW abdomen |
| 0.05 mg/d | 83±41 / 57±23 / 41±11 | pg/mL | same | |
| 0.075 mg/d | 99±35 / 72±24 / 60±24 | pg/mL | same | |
| 0.1 mg/d | 133±51 / 89±38 / 90±44 | pg/mL | same | |
| 0.1 mg/d, buttocks | 145±71 / 104±52 / 85±47 | pg/mL | same | |
| Vivelle-Dot apparent half-life | 5.9–7.7 | h | L_VIVELLEDOT §12.3 Excretion | |
| Estradot 25 / 37.5 / 50 / 100 average Cmax | ~25 / ~35 / 50–55 / 95–105 | pg/mL | SMPC_ESTRADOT §5.2 | PMW |
| Estradot 50 SS Cmax / Cmin | 57 / 28 | pg/mL | SMPC_ESTRADOT §5.2 | PMW |
| Climara 0.025 (single 7-d wear, abdomen) Cmax / Cmin / Cavg | 32 / 17 / 22 | pg/mL | L_CLIMARA Table 2 | 24 |
| Climara 0.05 | 71 / 29 / 41 | pg/mL | same | 102 |
| Climara 0.1 abdomen | 147 / 60 / 87 | pg/mL | same | 139 |
| Climara 0.1 buttock | 174 / 71 / 106 | pg/mL | same | 38 |
| Climara 0.1 over 3 weeks Cmax / Cmin | ~100 / ~35 | pg/mL | L_CLIMARA §12.3 | 24 |
| Climara variability (relative SD), abdomen Cmax / Cavg | 62 / 48 (buttock 39 / 35) | % | L_CLIMARA §12.3 | |
| Dermestril 25 / 50 / 100 peak (~24 h) / plateau | 37/61/117 and 23/40/79 | pg/mL | RCP_DERMESTRIL §5.2; Setnikar 1997 (GC-MS, n=24) | |
| Dermestril 50 twice-weekly SS Cavg | 35 | pg/mL | Setnikar 1996, abstract | PMW, 16 |
| Femsept (7-d) 50 / 75 / 100 peak (~18 h) / mean | 79/120/148 and 42/54/71 (CV 27–40%) | pg/mL | RCP_FEMSEPT §5.2 | |
| 7-d matrix 50 µg Cmax / tmax / Cav (week 3) | 45 / 25 h / 31 | pg/mL | Setnikar 1998, abstract | 18 |
| Estraderm 0.05 mean E2 | 41.1±13.5 | pg/mL | Scott 1991, abstract | 15 |
| 7-day patch intra-individual / inter-individual CV | 25 / 40–50 | % | Boyd 1996a, abstract | 18 |
| Turner syndrome, twice-weekly 0.0375 / 0.075 mg average E2 | 38±13 / 114±31 (SE) | pg/mL | Taboada 2011, abstract (LC-MS/MS) | 10 girls |
| Trans women on transdermal E2, median E2 (clinic) | 70.8 | pg/mL | Chantrapanichkul 2021, abstract | 134 TW across routes |

### Site effects
- Buttock vs abdomen:
  - Climara: Cmax +25%, Cavg +17% (n=38).
  - Vivelle: increment above baseline 88 vs 79 pg/mL.
  - FemPatch: 19 vs 15 pg/mL above baseline.

### Limitations
- Most data are mean values from manufacturer studies in postmenopausal women, often RIA.
- Climara Table 2 is single-application data.
- Nominal delivery rate ≠ absorbed amount; apparent bioavailability differs by matrix (Reginster 2000: 19–35 pg/mL corrected Cavg across 50 µg patches).
- No population PK model with CV% on CL or input rate was found.

---

## 3. Cyproterone acetate, oral (Androcur 50 mg; 10 mg; 2 mg in Diane/Dianette)

### Structure
- **Absorption:** rapid and essentially complete. Absolute bioavailability is 88% (UK Androcur SmPC; Huber 1988: 88±20%). First pass is negligible (Becker 1980; ANSM RCP "peu important").
- **Tmax:** about 1.6 h (2 mg) to 3–4 h (50–100 mg).
- **Disposition:** at least biphasic. Initial half-life is 0.8–8 h depending on study and dose. Terminal half-life:
  - about 44 h (Androcur SmPC: 43.9±12.8 h)
  - 54±26 h (2 mg single dose)
  - 78.6±16 h after 3 cycles
  - 3.6±1.3 days (100 mg, women)
  - about 2 days (ANSM RCP, attributed to adipose-tissue release)
- **Age effect:** in men the terminal half-life is about 45 h in younger men vs about 95 h in older men (Kuhnz 1997).
- **Volume:** very large and dose-dependent. Vz is about 1000 L after a single dose and about 1300 L at steady state (Kuhnz 1993), consistent with a deep (adipose) compartment.
- **Clearance:** 3.0–3.6 mL/min/kg.
- **Accumulation:** about 2× with daily dosing. Steady state is reached around day 16 (Kuhnz 1993, Dianette SmPC), or day 5–8 in older RIA data (Düsterberg 1979).
- **Suggested model:** 2-compartment, first-order absorption (ka fast, tlag ~0), CL ≈ 3.5 mL/min/kg, Vss ≈ 1000–1300 L. The active metabolite 15β-OH-CPA has AUC 10–15% higher than CPA (Huber 1988) and a CPA:metabolite ratio of about 0.8 (Kuhnz 1997).

### Key parameters
| Parameter | Value | Unit | Source / location | Population, n |
|---|---|---|---|---|
| Absolute bioavailability | 88 (±20) | % | SMPC_ANDROCUR_UK §5.2; Huber 1988 abstract | 7 women |
| Cmax after 100 mg | ~285 | ng/mL | SMPC_ANDROCUR_UK §5.2 | |
| Cmax after 100 mg | 255±110 | ng/mL | Huber 1988 abstract | 7 young women |
| Tmax after 100 mg | ~3 (SmPC); 2–3 (Huber) | h | as above | |
| Tmax (ANSM, 50 mg) | 3–4 | h | RCP_ANDROCUR §5.2 | |
| Terminal t½ | 43.9±12.8 | h | SMPC_ANDROCUR_UK §5.2 | |
| Terminal t½, 100 mg | 3.6±1.3 | days | Huber 1988 | 7 |
| Terminal t½, 2 mg single / multiple dosing | 54.0±26.0 / 78.6±16.0 | h | Kuhnz 1993 abstract | 15 women |
| Terminal t½, younger vs elderly men | ~45 vs ~95 | h | Kuhnz 1997 abstract | 28 men |
| Clearance | 3.5±1.5 (SmPC); 3.6±0.9 → 3.0±0.4 (Kuhnz) | mL/min/kg | | |
| Vz single / steady state | 986 (SD printed as "4371", probably a typo for 437) / 1304±427 | L | Kuhnz 1993 abstract | 15 |
| Unbound fraction | 3.5–4 (SmPC); 3.5±1.9 (Kuhnz) | % | | |
| 2 mg Cmax / Tmax | 15 / 1.6 h (SmPC); 15.2±6.6 (Kuhnz); 11.0±3.4 / 1.6±0.6 h (Düsterberg) | ng/mL | | |
| Accumulation, daily 2 mg | AUC ×2.2 (cycle 1), ×2.4 (cycle 3); SS ~16 d | | SMPC_DIANETTE_UK §5.2 | |
| 14C, 50 mg: Cmax / Tmax | 400±40 ng-eq/mL / 3.8±0.5 h | | Speck 1976 abstract | 4 men |
| Excretion | urine:bile 3:7; 33% urine / 60% feces over 10 d | | SmPC; Speck 1976 | |

### Low-dose and alternate-day dosing
- **Low-dose PK:** no direct PK study of 10 mg or 12.5–25 mg was found. Jentsch 1976 (abstract, n=16) reports near-complete absorption at 2–12 mg. Linear PK from 2 to 100 mg is supported by the label statement "completely absorbed over a wide dose range". Mean 2 mg Cmax (~15) vs 100 mg (~255–285) ng/mL is roughly dose-proportional, but this is my inference, not a reported result.
- **Low-dose PD:**
  - Kuijpers 2021 (ENIGI, n=882): testosterone was about 0.9–1.1 nmol/L with 10, 25, 50 or 100 mg/day plus estrogen, vs 5.5 nmol/L with estrogen alone.
  - Even Zohar 2021: 10–20 mg gave the same suppression as 50–100 mg, with lower prolactin.
- **Alternate-day dosing: no reliable source found.**

### Limitations
- Most CPA PK data are 1976–1997 manufacturer (Schering) studies, many abstract-only or in German. Assays were RIA, 14C or HPLC, with small n (4–28).
- No population PK and no data in trans women.
- Half-life estimates range from 1.6 to 3.6 days depending on sampling duration and dose.

---

## Unverified leads (not entered as data)
- Kuhl H. 2005 Climacteric review "Pharmacology of estrogens and progestogens: influence of different routes of administration". I did not retrieve or verify it this session.
- Hümpel 1977 (Contraception, PMID 880829) and Hümpel 1978 (Arzneimittelforschung, PMID 580402; 50 mg tablets in 5 men): no abstracts available, so no numbers were taken.
- Full text of Sirviö 2026 (Drugs R D, open access?): may contain washout-phase half-life, Cmax/tmax and CV% per dose. Not fetched.
- Full text of Järvinen 1997: may give percentage changes for washing and area. Not fetched.
- Androcur 10 mg (German Fachinformation): not retrieved.
- The Divigel label does not state n for its PK table.
- Balcerek 2021 (PMID 34326812) reports trans-women E2 "328 nmol/L", which is likely a pmol/L unit error. It was excluded.
- No published population-PK (NLME) model for transdermal gel E2 or for CPA in trans women was found. The only pop-PK modelling seen (Stanczyk 2022, PMID 36574387) is for an EE/LNG patch, which is out of scope.
