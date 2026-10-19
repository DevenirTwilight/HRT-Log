# LAB-03 analyte and unit dictionary

Scope: pure domain entry/display conversions; no diagnosis, default reference intervals,
derived clinical scores, medication decisions or PK/calibration eligibility changes.
All tests are synthetic. No report or personal details are copied into this repository.
Source retrieval: 2026-10-19. Source pages are read for numerical relationships, not
redistributed as clinical text. This catalogue is not endorsed by the source organisations.

## Verified source relationships

| Source | Verified content | Application |
|---|---|---|
| [NGSP: IFCC standardization](https://ngsp.org/ifccngsp.asp) | NGSP % = 0.09148 × IFCC mmol/mol + 2.152 | HBA1C uses IFCC as canonical; affine conversion, never a simple proportion |
| [Mayo Clinic Laboratories: SI Unit Conversion](https://www.mayocliniclabs.com/order-tests/si-unit-conversion.html) | Triglycerides mg/dL × 0.0113 → mmol/L; glucose mg/dL × 0.05551 → mmol/L; albumin g/dL × 10 → g/L | TG, GLU_FASTING/GLU_RANDOM, ALB |
| [Labcorp: SI Unit Conversion Table](https://www.labcorp.com/resource/si-unit-conversion-table) | Cholesterol, HDL, LDL mg/dL × 0.0259 → mmol/L | TC/HDL_C/LDL_C use the published rounded forward factor |

HTTP 200 body SHA-256 (retrieved HTML bytes; dynamic pages may change independently):

- NGSP: `f6047bf850230797ce353d48689cde3dfae929be9c87ed19696e37768eccbcfa`.
- Mayo: `96f559f04c21cf03915851c2f8898abc0624e3dbb61672adfa4e371fb64e8222`.
- Labcorp: `75b36c57a3bfedacd7f2eff82abd9a1026576d159c15636b47f3819dc126d10f`.

Labcorp initially returned HTTP 403; retry with ordinary browser User-Agent returned
its actual table, including separate cholesterol/HDL/LDL rows. The table's separately
rounded reverse factor 38.61 is **not** mixed with its forward factor. This catalogue
uses the inverse of the same 0.0259 coefficient for reversibility. The extra digits in
a computed result do not increase the source coefficient's scientific precision.
An earlier candidate 0.02586 was not verified from these retrieved sources and is not
used. NCBI Bookshelf requests returned a captcha despite HTTP 200; they provide no
verification evidence. No claim is made that a successful HTTP request verifies content.

## Dimensional identities and legacy boundaries

Metric identities are exact: g/L × 100 = mg/dL; g/dL × 10 = g/L;
mIU/mL = IU/L; 10³/mm³ = 10⁹/L; millions/mm³ = 10¹²/L;
Hct % ÷ 100 = L/L. `µ` and `μ` aliases are explicit and narrowly scoped.
Cell percentages and absolute counts have separate codes and no cross-conversion.
RBC and WBC/PLT counts have different units even when their numerical values match.
No density, biochemical composition or formula is guessed from unit spelling.

The 14 legacy identities E2, T, P4, P4_IA, P4_MS, PRL, LH, FSH, SHBG,
ALT, AST, GGT, CREA and K preserve the current application's canonical units and
factors (E2 3.671, T 0.03467, P4 3.18, PRL 21.2, CREA inverse 88.42).
These are compatibility constraints, not new clinical-source validation claims.
The existing app's Double converter remains unchanged for these saved identities;
the new decimal catalogue must not replace PK's unit implementation.

ALP uses U/L. TBIL accepts µmol/L only; UREA accepts mmol/L only until additional
mass/molar relationships are separately verified. In particular BUN is not urea:
Labcorp's BUN coefficient must not be applied to UREA. EGFR is a report value in
mL/min/1.73m², not a new calculated eGFR. Formula/version/assay/instrument and
population qualifiers can be retained in existing notes; no formula is assumed.

## Numeric and integration contract

`LabConversionResult.Success(BigDecimal)` and structured failures distinguish
unknown analytes, unknown units, invalid values and bounded range failure.
Conversions accept nonnegative decimal values, at most 64 input significant digits,
scale -64..64, magnitude ≤ 1E30. String inputs are capped at 256 characters before
parsing. NaN/Infinity/locale text, negative values and overflow-sized inputs fail.
Zero is a mathematical value, not missingness. This does not alter the existing
persistence constraint requiring positive numeric lab results.

Arithmetic uses DECIMAL128 (34 significant digits, half-even) for nonterminating
ratios. Unit identity conversions preserve the original BigDecimal exactly. Existing
source coefficients are decimal strings, not binary Double literals. Exact decimal
power-of-ten round trips do not drift. Rounded molar round trips are bounded by the
34-digit arithmetic, not advertised as exact inverses in all finite representations.
No unknown unit is implicitly accepted. Conversion failure cannot write a value.

One-sided report reference bounds can be converted independently with the same
monotonic affine mapping; missing bounds stay missing. `<31` is not a plain numeric
result and is not parsed as31 or0. Reference interval population, administrative sex,
rounded alternate representations and assay identity do not select clinical ranges.
A report's9ng/dL and separately rounded0nmol/L are not reconciled, overwritten or
saved as two measurements by this conversion library.

No generic custom-analyte identity or free-unit storage is introduced here. Future
custom analytes need dedicated reviewed persistence semantics; opaque codes must
not encode metadata. No non-HDL, ratios, FIB-4 or eGFR formula is calculated.

## Local verification

`:core:domain:test` PASS: 135 tests, 0 failures/errors/skips, including 9 new
`LabCatalogTest` methods. Checks cover the45-code vocabulary, all unit pairs with
25 deterministic synthetic values per analyte,1000 exact decimal round trips,
French report examples, affine bound conversion, unknown/absolute-versus-percentage
unit rejection, zero/negative/nonfinite/huge/high-precision inputs and assay identity.
The first targeted run reported two test-data mistakes (expected catalogue count48
instead of45 and manually transcribed HbA1c56 mmol/mol expected7.27588 instead of
7.27488); both expectations were corrected against the explicit vocabulary/equation.
No production assertion was removed. CI, integrated App tests and physical-device
checks are outside this domain-only commit and must be recorded by integration.
