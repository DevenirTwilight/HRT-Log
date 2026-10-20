package net.plainnotes.app.domain

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/** Display/entry categories only; these do not imply diagnoses or PK eligibility. */
enum class LabCategory { HORMONES, CBC, LIVER, KIDNEY, LIPIDS, GLUCOSE_METABOLISM, OTHER }

enum class LabConversionFailure { UNKNOWN_ANALYTE, UNKNOWN_UNIT, INVALID_NUMBER, OUT_OF_RANGE }
sealed interface LabConversionResult {
    data class Success(val value: BigDecimal) : LabConversionResult
    data class Failure(val reason: LabConversionFailure) : LabConversionResult
}

/** unit = canonical * factor + offset. Affine HbA1c conversion must retain its offset. */
class LabUnitRule internal constructor(val unit: String, val factor: BigDecimal, val offset: BigDecimal = BigDecimal.ZERO)

class LabAnalyteDefinition internal constructor(
    val code: String,
    val category: LabCategory,
    private val rules: List<LabUnitRule>,
) {
    val canonicalUnit: String = rules.first().unit
    val units: List<String> = rules.map { it.unit }
    fun toCanonical(value: BigDecimal, unit: String): LabConversionResult = convert(value, unit, canonicalUnit)
    fun fromCanonical(value: BigDecimal, unit: String): LabConversionResult = convert(value, canonicalUnit, unit)

    /** No locale parsing, inferred units, reference ranges or clinical interpretation. */
    fun convert(value: String, from: String, to: String): LabConversionResult {
        if (value.length > 256) return LabConversionResult.Failure(LabConversionFailure.OUT_OF_RANGE)
        val decimal = value.toBigDecimalOrNull() ?: return LabConversionResult.Failure(LabConversionFailure.INVALID_NUMBER)
        return convert(decimal, from, to)
    }

    fun convert(value: BigDecimal, from: String, to: String): LabConversionResult {
        val source = rules.firstOrNull { it.unit == from }
            ?: return LabConversionResult.Failure(LabConversionFailure.UNKNOWN_UNIT)
        val target = rules.firstOrNull { it.unit == to }
            ?: return LabConversionResult.Failure(LabConversionFailure.UNKNOWN_UNIT)
        if (value.signum() < 0) return LabConversionResult.Failure(LabConversionFailure.INVALID_NUMBER)
        if (!bounded(value)) return LabConversionResult.Failure(LabConversionFailure.OUT_OF_RANGE)
        // Identical affine units must not round or acquire an offset.
        if (source == target) return LabConversionResult.Success(value)
        val canonical = value.subtract(source.offset).divide(source.factor, CONTEXT)
        val result = canonical.multiply(target.factor, CONTEXT).add(target.offset, CONTEXT)
        // E.g. an NGSP result below the affine intercept cannot produce a negative IFCC result.
        if (result.signum() < 0) return LabConversionResult.Failure(LabConversionFailure.OUT_OF_RANGE)
        return if (bounded(result)) LabConversionResult.Success(result.stripTrailingZeros())
        else LabConversionResult.Failure(LabConversionFailure.OUT_OF_RANGE)
    }

    private fun bounded(value: BigDecimal): Boolean = value.precision() <= 64 && value.scale() in -64..64 &&
        value.abs() <= MAX_VALUE
    companion object {
        /** DECIMAL128, half-even. Repeating divisions are rounded once at 34 significant digits. */
        val CONTEXT: MathContext = MathContext(34, RoundingMode.HALF_EVEN)
        private val MAX_VALUE = BigDecimal("1E30")
    }
}

/** Closed, versioned entry vocabulary. Existing saved strings remain untouched. */
object LabCatalog {
    const val VERSION = "LAB-03-2026-10-19-v1"
    private fun rule(unit: String, factor: String = "1", offset: String = "0") =
        LabUnitRule(unit, BigDecimal(factor), BigDecimal(offset))
    private fun definition(code: String, category: LabCategory, vararg units: LabUnitRule) =
        LabAnalyteDefinition(code, category, units.toList())
    private fun count(code: String, red: Boolean = false) = definition(code, LabCategory.CBC,
        rule(if (red) "10¹²/L" else "10⁹/L"),
        rule(if (red) "millions/mm³" else "10³/mm³"),
        rule(if (red) "10^12/L" else "10^9/L"),
        rule(if (red) "10⁶/µL" else "10³/µL"),
        *if (red) arrayOf(rule("10^6/mm3"), rule("millions/mm3")) else arrayOf(rule("10^3/mm3"), rule("× 10³/mm³")))

    val all: List<LabAnalyteDefinition> = buildList {
        // Frozen legacy chart factors retained verbatim (no PK factor replacement).
        add(definition("E2", LabCategory.HORMONES, rule("pg/mL"), rule("pmol/L", "3.671")))
        add(definition("T", LabCategory.HORMONES, rule("ng/dL"), rule("nmol/L", "0.03467")))
        listOf("P4", "P4_IA", "P4_MS").forEach { add(definition(it, LabCategory.HORMONES, rule("ng/mL"), rule("nmol/L", "3.18"))) }
        add(definition("PRL", LabCategory.HORMONES, rule("ng/mL"), rule("mIU/L", "21.2")))
        listOf("LH", "FSH").forEach { add(definition(it, LabCategory.HORMONES, rule("IU/L"), rule("mIU/mL"))) }
        add(definition("SHBG", LabCategory.HORMONES, rule("nmol/L")))
        listOf("ALT", "AST", "GGT", "ALP").forEach { add(definition(it, LabCategory.LIVER, rule("U/L"), rule("IU/L"))) }
        // CREA keeps the legacy canonical unit; exact reciprocal avoids an extra binary approximation.
        add(definition("CREA", LabCategory.KIDNEY, rule("µmol/L"),
            LabUnitRule("mg/dL", BigDecimal.ONE.divide(BigDecimal("88.42"), LabAnalyteDefinition.CONTEXT)), rule("μmol/L")))
        listOf("K", "NA").forEach { add(definition(it, LabCategory.KIDNEY, rule("mmol/L"))) }
        listOf("TC", "HDL_C", "LDL_C").forEach { add(definition(it, LabCategory.LIPIDS,
            rule("mg/dL"), rule("g/L", "0.01"), rule("mmol/L", "0.0259"))) }
        add(definition("TG", LabCategory.LIPIDS, rule("mg/dL"), rule("g/L", "0.01"), rule("mmol/L", "0.0113")))
        listOf("GLU_FASTING", "GLU_RANDOM").forEach { add(definition(it, LabCategory.GLUCOSE_METABOLISM,
            rule("mg/dL"), rule("g/L", "0.01"), rule("mmol/L", "0.05551"))) }
        // IFCC canonical: NGSP% = 0.09148 * IFCC mmol/mol + 2.152 (NGSP master equation).
        add(definition("HBA1C", LabCategory.GLUCOSE_METABOLISM, rule("mmol/mol"), rule("%", "0.09148", "2.152")))
        add(count("RBC", true))
        listOf("HB", "MCHC").forEach { add(definition(it, LabCategory.CBC, rule("g/dL"), rule("g/L", "10"))) }
        add(definition("HCT", LabCategory.CBC, rule("%"), rule("L/L", "0.01")))
        add(definition("MCV", LabCategory.CBC, rule("fL")))
        add(definition("MCH", LabCategory.CBC, rule("pg")))
        listOf("WBC", "PLT", "NEUT_ABS", "LYMPH_ABS", "MONO_ABS", "EOS_ABS", "BASO_ABS").forEach { add(count(it)) }
        listOf("NEUT_PCT", "LYMPH_PCT", "MONO_PCT", "EOS_PCT", "BASO_PCT").forEach { add(definition(it, LabCategory.CBC, rule("%"))) }
        // Report-only units until separately reviewed mass↔molar factors/formula metadata exist.
        add(definition("TBIL", LabCategory.LIVER, rule("µmol/L"), rule("μmol/L")))
        add(definition("ALB", LabCategory.LIVER, rule("g/L"), rule("g/dL", "0.1")))
        add(definition("UREA", LabCategory.KIDNEY, rule("mmol/L")))
        add(definition("EGFR", LabCategory.KIDNEY, rule("mL/min/1.73m²"), rule("mL/min/1.73 m²")))
    }
    private val byCode = all.associateBy { it.code }
    fun find(code: String): LabAnalyteDefinition? = byCode[code]
    fun categoryFor(code: String): LabCategory = find(code)?.category ?: LabCategory.OTHER
    fun convert(code: String, value: String, from: String, to: String): LabConversionResult =
        find(code)?.convert(value, from, to) ?: LabConversionResult.Failure(LabConversionFailure.UNKNOWN_ANALYTE)
}
