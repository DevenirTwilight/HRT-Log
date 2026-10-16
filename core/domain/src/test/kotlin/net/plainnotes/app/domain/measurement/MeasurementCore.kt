package net.plainnotes.app.domain.measurement

import java.math.BigDecimal
import java.time.LocalDate

/** ANSM-03A laboratory core. Test source only; no production caller or serialization contract. */
internal enum class Dimension { MASS, VOLUME, TABLET, CAPSULE, PATCH, ACTUATION, RELEASE_RATE }
internal enum class MeasureUnit(val dimension: Dimension, val base: BigDecimal) {
    UG(Dimension.MASS, BigDecimal("0.001")), MG(Dimension.MASS, BigDecimal.ONE), G(Dimension.MASS, BigDecimal("1000")),
    ML(Dimension.VOLUME, BigDecimal.ONE), TABLET(Dimension.TABLET, BigDecimal.ONE),
    CAPSULE(Dimension.CAPSULE, BigDecimal.ONE), PATCH(Dimension.PATCH, BigDecimal.ONE),
    PUMP(Dimension.ACTUATION, BigDecimal.ONE), UG_PER_24H(Dimension.RELEASE_RATE, BigDecimal.ONE)
}
internal enum class QuantityKind { ACTIVE_MASS, FORMULATION, USE_COUNT, RELEASE_RATE, ABSORBED_MASS }
/** identity is exact and opaque: compound/salt/ester/hydrate/role/group, or frozen presentation identity. */
internal data class Meaning(val kind: QuantityKind, val identity: String)
internal data class Quantity(val value: BigDecimal, val unit: MeasureUnit, val meaning: Meaning)
internal enum class MeasureError {
    INVALID_NUMBER, PRECISION_OR_RANGE, INVALID_QUANTITY, MISSING_HISTORICAL_SNAPSHOT, INSUFFICIENT_SOURCE,
    PRESENTATION_MISMATCH, VERSION_MISMATCH, AMBIGUOUS_STRENGTH, INGREDIENT_MISMATCH, INCOMPATIBLE_UNIT,
    FRACTION_NOT_VERIFIED, DECLARED_QUANTITY_CONFLICT, UNKNOWN_CAPACITY, INSUFFICIENT_STOCK, OPERATION_CONFLICT, VOIDED
}
internal sealed interface MeasureResult<out T> {
    data class Success<T>(val value: T, val algorithm: String = Measurements.ALGORITHM, val evidence: String? = null) : MeasureResult<T>
    data class Failure(val error: MeasureError) : MeasureResult<Nothing>
}
/** Frozen identity; digest identifies the complete immutable source/presentation payload, not a mutable row. */
internal data class PresentationRef(val namespace: String, val product: String, val presentation: String,
                                    val version: String, val sha256: String)
internal data class ConversionEvidence(val ref: PresentationRef, val source: String, val locator: String,
                                       val verifiedOn: String, val conditions: String, val verified: Boolean,
                                       val fractionalContentVerified: Boolean = false)
/** Deliberately directed. Reverse conversion needs a separately declared relation and evidence. */
internal data class ContentRelation(val from: Quantity, val to: Quantity, val evidence: ConversionEvidence)
internal data class ActualPackage(val ref: PresentationRef, val userConfirmed: Boolean)
internal data class Conversion(val quantity: Quantity, val ref: PresentationRef)

internal object Measurements {
    const val ALGORITHM = "ansm03-exact-v1"
    private val max = BigDecimal("1000000000000000000")
    private val digest = Regex("[0-9a-f]{64}")
    private fun fail(e: MeasureError) = MeasureResult.Failure(e)
    fun parse(text: String): MeasureResult<BigDecimal> {
        if (text.length > 80) return fail(MeasureError.PRECISION_OR_RANGE)
        if (!Regex("[0-9]+(?:\\.[0-9]+)?").matches(text)) return fail(MeasureError.INVALID_NUMBER)
        return bounded(BigDecimal(text))
    }
    private fun bounded(v: BigDecimal): MeasureResult<BigDecimal> = when {
        v.signum() < 0 -> fail(MeasureError.INVALID_NUMBER)
        v.scale() > 18 || v.scale() < -18 || v > max -> fail(MeasureError.PRECISION_OR_RANGE)
        else -> MeasureResult.Success(if (v.signum() == 0) BigDecimal.ZERO else v.stripTrailingZeros())
    }
    fun error(q: Quantity): MeasureError? {
        val number = bounded(q.value)
        if (number is MeasureResult.Failure) return number.error
        if (q.meaning.identity.isBlank()) return MeasureError.INVALID_QUANTITY
        val d = q.unit.dimension
        val valid = when (q.meaning.kind) {
            QuantityKind.ACTIVE_MASS, QuantityKind.ABSORBED_MASS -> d == Dimension.MASS
            QuantityKind.FORMULATION -> d == Dimension.MASS || d == Dimension.VOLUME
            QuantityKind.USE_COUNT -> d in setOf(Dimension.TABLET, Dimension.CAPSULE, Dimension.PATCH, Dimension.ACTUATION)
            QuantityKind.RELEASE_RATE -> d == Dimension.RELEASE_RATE
        }
        return if (valid) null else MeasureError.INVALID_QUANTITY
    }
    private fun identityError(a: Meaning, b: Meaning): MeasureError? = when {
        a.kind != b.kind -> MeasureError.INCOMPATIBLE_UNIT
        a.identity != b.identity -> MeasureError.INGREDIENT_MISMATCH
        else -> null
    }
    fun inUnit(q: Quantity, unit: MeasureUnit): MeasureResult<Quantity> {
        error(q)?.let { return fail(it) }
        if (q.unit.dimension != unit.dimension) return fail(MeasureError.INCOMPATIBLE_UNIT)
        return quantity(q, unit, q.value.multiply(q.unit.base).divide(unit.base))
    }
    private fun quantity(q: Quantity, unit: MeasureUnit, number: BigDecimal): MeasureResult<Quantity> = when (val n = bounded(number)) {
        is MeasureResult.Failure -> n
        is MeasureResult.Success -> MeasureResult.Success(Quantity(n.value, unit, q.meaning))
    }
    fun add(a: Quantity, b: Quantity): MeasureResult<Quantity> = arithmetic(a, b, false)
    fun subtract(a: Quantity, b: Quantity): MeasureResult<Quantity> = arithmetic(a, b, true)
    private fun arithmetic(a: Quantity, b: Quantity, subtract: Boolean): MeasureResult<Quantity> {
        error(a)?.let { return fail(it) }; error(b)?.let { return fail(it) }
        if (a.unit.dimension != b.unit.dimension) return fail(MeasureError.INCOMPATIBLE_UNIT)
        identityError(a.meaning, b.meaning)?.let { return fail(it) }
        return when (val same = inUnit(b, a.unit)) {
            is MeasureResult.Failure -> same
            is MeasureResult.Success -> quantity(a, a.unit, if (subtract) a.value - same.value.value else a.value + same.value.value)
        }
    }
    fun refValid(r: PresentationRef): Boolean = listOf(r.namespace, r.product, r.presentation, r.version).all { it.isNotBlank() } && digest.matches(r.sha256)
    private fun sourceValid(e: ConversionEvidence): Boolean = e.verified && refValid(e.ref) &&
        listOf(e.source, e.locator, e.conditions).all { it.isNotBlank() } && runCatching { LocalDate.parse(e.verifiedOn) }.isSuccess
    fun compareRefs(expected: PresentationRef, actual: PresentationRef): MeasureError? = when {
        expected.namespace != actual.namespace || expected.product != actual.product || expected.presentation != actual.presentation -> MeasureError.PRESENTATION_MISMATCH
        expected.version != actual.version || expected.sha256 != actual.sha256 -> MeasureError.VERSION_MISMATCH
        else -> null
    }
    fun convert(input: Quantity, target: Meaning, targetUnit: MeasureUnit, relation: ContentRelation?, actual: ActualPackage?): MeasureResult<Conversion> {
        error(input)?.let { return fail(it) }
        if (actual == null) return fail(MeasureError.MISSING_HISTORICAL_SNAPSHOT)
        if (relation == null || !actual.userConfirmed || !sourceValid(relation.evidence)) return fail(MeasureError.INSUFFICIENT_SOURCE)
        val e = relation.evidence
        compareRefs(e.ref, actual.ref)?.let { return fail(it) }
        if (!refValid(actual.ref)) return fail(MeasureError.INSUFFICIENT_SOURCE)
        if (error(relation.from) != null || error(relation.to) != null || relation.from.value.signum() <= 0 || relation.to.value.signum() <= 0)
            return fail(MeasureError.AMBIGUOUS_STRENGTH)
        // Absorption is outside this measurement contract, even if a caller supplies a relation.
        if (relation.from.meaning.kind in setOf(QuantityKind.ABSORBED_MASS, QuantityKind.RELEASE_RATE) ||
            relation.to.meaning.kind in setOf(QuantityKind.ABSORBED_MASS, QuantityKind.RELEASE_RATE))
            return fail(MeasureError.INCOMPATIBLE_UNIT)
        if (relation.from.meaning.kind == relation.to.meaning.kind) {
            return fail(if (relation.from.meaning.kind == QuantityKind.ACTIVE_MASS && relation.from.meaning != relation.to.meaning)
                MeasureError.INGREDIENT_MISMATCH else MeasureError.INCOMPATIBLE_UNIT)
        }
        identityError(input.meaning, relation.from.meaning)?.let { return fail(it) }
        identityError(target, relation.to.meaning)?.let { return fail(it) }
        val n = inUnit(input, relation.from.unit)
        if (n is MeasureResult.Failure) return n
        val inputAmount = (n as MeasureResult.Success).value.value
        val counts = setOf(Dimension.TABLET, Dimension.CAPSULE, Dimension.PATCH, Dimension.ACTUATION)
        if (relation.from.unit.dimension in counts && !e.fractionalContentVerified && inputAmount.remainder(BigDecimal.ONE).signum() != 0)
            return fail(MeasureError.FRACTION_NOT_VERIFIED)
        val raw = try { relation.to.value.multiply(inputAmount).divide(relation.from.value) }
                  catch (_: ArithmeticException) { return fail(MeasureError.PRECISION_OR_RANGE) }
        // Partial devices/capsules/patches are not assumed proportional either.
        if (relation.to.unit.dimension in counts && !e.fractionalContentVerified && raw.remainder(BigDecimal.ONE).signum() != 0)
            return fail(MeasureError.FRACTION_NOT_VERIFIED)
        val out = inUnit(relation.to.copy(value = raw), targetUnit)
        return when (out) {
            is MeasureResult.Failure -> out
            is MeasureResult.Success -> MeasureResult.Success(Conversion(out.value, e.ref), evidence = e.source + "#" + e.locator)
        }
    }

    /** Positive actual use is separate from zero-valued mathematical quantities (e.g. exhausted stock). */
    fun actualUse(input: Quantity, target: Meaning, unit: MeasureUnit, relation: ContentRelation?, actual: ActualPackage?): MeasureResult<Conversion> {
        if (input.value.signum() <= 0) return fail(MeasureError.INVALID_NUMBER)
        return convert(input, target, unit, relation, actual)
    }

    /** Compare two independently recorded facts; discrepancy never changes either fact or chooses a dose. */
    fun verifyDeclaredContent(input: Quantity, declared: Quantity, relation: ContentRelation?, actual: ActualPackage?): MeasureResult<Conversion> {
        error(declared)?.let { return fail(it) }
        val converted = actualUse(input, declared.meaning, declared.unit, relation, actual)
        if (converted is MeasureResult.Failure) return converted
        val value = (converted as MeasureResult.Success).value
        return if (value.quantity.value.compareTo(declared.value) == 0) converted else fail(MeasureError.DECLARED_QUANTITY_CONFLICT)
    }
}

/** Nominal data is never promoted to usable stock. No multiplication of nominal packs into actual capacity. */
internal class PresentationSnapshot(
    val ref: PresentationRef,
    val nominalTotal: Quantity?,
    val nominalUses: Quantity?,
    relations: List<ContentRelation>,
    val confirmedUsable: Quantity? = null,
    val releaseRate: Quantity? = null,
) {
    val relations: List<ContentRelation> = relations.toList()
    /** Requires exactly one explicit relation; duplicate/conflicting strengths never choose the first. */
    fun convert(input: Quantity, target: Meaning, unit: MeasureUnit, actual: ActualPackage?): MeasureResult<Conversion> {
        val matching = relations.filter { it.from.meaning == input.meaning && it.to.meaning == target }
        if (matching.size > 1) return MeasureResult.Failure(MeasureError.AMBIGUOUS_STRENGTH)
        val relation = matching.singleOrNull()
        if (relation != null) Measurements.compareRefs(ref, relation.evidence.ref)?.let { return MeasureResult.Failure(it) }
        return Measurements.convert(input, target, unit, relation, actual)
    }
    fun remaining(used: Quantity, actual: ActualPackage?): MeasureResult<Quantity> {
        if (actual == null) return MeasureResult.Failure(MeasureError.MISSING_HISTORICAL_SNAPSHOT)
        if (!actual.userConfirmed || !Measurements.refValid(ref) || !Measurements.refValid(actual.ref))
            return MeasureResult.Failure(MeasureError.INSUFFICIENT_SOURCE)
        Measurements.compareRefs(ref, actual.ref)?.let { return MeasureResult.Failure(it) }
        val capacity = confirmedUsable ?: return MeasureResult.Failure(MeasureError.UNKNOWN_CAPACITY)
        return Measurements.subtract(capacity, used)
    }
}
