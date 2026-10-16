package net.plainnotes.app.domain.shadow

import java.math.BigDecimal
import java.security.MessageDigest
import net.plainnotes.app.domain.measurement.*

/** TEST_ONLY. Stateless DTO inspector. No repository, database, callback, clock or IO capability. */
internal data class ShadowScope(val dataset: String, val medication: String)
internal enum class ShadowEvent { APPLY, REPLACE, REMOVE, DETACH }
internal class ActualSelection(val ref: PresentationRef, containers: List<String>, val confirmed: Boolean) {
    val containers: List<String> = containers.toList()
}
internal data class ShadowRequest(val scope: ShadowScope, val record: String, val revision: Int, val operation: String,
    val occurredUtc: Long, val event: ShadowEvent, val ingredient: Meaning?, val amount: Quantity?, val selection: ActualSelection?)
internal data class FrozenContainer(val scope: ShadowScope, val uuid: String, val legacyId: Long,
    val snapshot: PresentationSnapshot?, val ingredient: Meaning?, val stockUnit: MeasureUnit?, val stockMeaning: Meaning?,
    val used: Quantity?, val state: String, val openedOn: String?)
/** Unit/meaning must be independently confirmed; a current medication unit is not container evidence. */
internal data class LegacyAllocation(val scope: ShadowScope, val container: String, val record: String, val revision: Int,
    val entryId: String, val kind: String, val delta: Double, val unit: MeasureUnit?, val meaning: Meaning?, val reversalOf: String? = null)
internal data class ExactAllocation(val container: String, val quantity: Quantity, val originalEntryId: String? = null)
internal class ShadowCapture(val scope: ShadowScope, val record: String, val revision: Int, val operation: String,
    val fingerprint: String, val quantity: Quantity?, allocations: List<ExactAllocation>, val reversed: Boolean = false) {
    val allocations: List<ExactAllocation> = allocations.toList()
}
internal enum class ShadowStatus { CONVERTIBLE, NO_STOCK_EFFECT, UNKNOWN, REJECTED, REPLAY }
internal enum class ShadowComparison { APPROXIMATE_MATCH, DIFFERENCE, UNKNOWN }
internal enum class ShadowReason {
    MISSING_FACT, SCOPE_MISMATCH, INVALID_IDENTITY, OPERATION_CONFLICT, MIXED_PRESENTATIONS, MISSING_CONTAINER,
    MISSING_UNIT, SOURCE_CONFLICT, MISSING_INGREDIENT, UNKNOWN_CAPACITY, UNAVAILABLE_CONTAINER, MISSING_ORDER, CONVERSION_REFUSED,
    ALLOCATION_COUNT, ALLOCATION_ORDER, RECORD_REVISION, LEGACY_UNIT_UNKNOWN, LEGACY_UNIT_MISMATCH,
    LEGACY_INVALID, LEGACY_QUANTITY_DIFFERENCE, REVERSAL_TARGET, UNALLOCATED_DIFFERENCE
}
internal data class ShadowResult(val status: ShadowStatus, val comparison: ShadowComparison,
    val quantity: Quantity? = null, val allocations: List<ExactAllocation> = emptyList(), val unallocated: Quantity? = null,
    val reasons: Set<ShadowReason> = emptySet(), val conversionError: MeasureError? = null,
    val additionalConfirmationRequired: Boolean = false, val legacyOriginalPrecision: String = "UNKNOWN")
internal data class ShadowForecastContract(val confirmedCompatible: Quantity?, val unknownContainers: Set<String>,
    val unallocatedActual: Quantity?, val reasons: Set<ShadowReason>, val completeInputs: Boolean,
    val reliableRemainingDate: String? = null) // Exact date computation is not implemented by B0.

internal object ShadowMeasurementAllocator {
    private val epsilon = BigDecimal("0.000000001")
    fun fingerprint(r: ShadowRequest): String {
        val q = r.amount
        val fields = listOf(r.scope.dataset, r.scope.medication, r.record, r.revision.toString(), r.operation,
            r.occurredUtc.toString(), r.event.name, r.ingredient?.toString().orEmpty(),
            q?.value?.stripTrailingZeros()?.toPlainString().orEmpty(), q?.unit?.name.orEmpty(), q?.meaning?.toString().orEmpty(),
            r.selection?.ref?.toString().orEmpty(), r.selection?.confirmed.toString()) + r.selection?.containers.orEmpty()
        val bytes = fields.joinToString("") { "${it.length}:$it" }.toByteArray(Charsets.UTF_8)
        return MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
    private fun unknown(reason: ShadowReason, error: MeasureError? = null) = ShadowResult(ShadowStatus.UNKNOWN,
        ShadowComparison.UNKNOWN, reasons = setOf(reason), conversionError = error, additionalConfirmationRequired = true)
    private fun rejected(reason: ShadowReason, error: MeasureError? = null) = ShadowResult(ShadowStatus.REJECTED,
        ShadowComparison.UNKNOWN, reasons = setOf(reason), conversionError = error)
    private fun conversionFailure(error: MeasureError): ShadowResult = when (error) {
        MeasureError.INSUFFICIENT_SOURCE, MeasureError.MISSING_HISTORICAL_SNAPSHOT, MeasureError.UNKNOWN_CAPACITY,
        MeasureError.AMBIGUOUS_STRENGTH, MeasureError.FRACTION_NOT_VERIFIED -> unknown(ShadowReason.CONVERSION_REFUSED, error)
        else -> rejected(ShadowReason.CONVERSION_REFUSED, error)
    }
    fun evaluate(request: ShadowRequest, containers: List<FrozenContainer>, observed: List<LegacyAllocation>,
                 prior: ShadowCapture? = null, observedUnallocated: Double? = null): ShadowResult {
        if (request.scope.dataset.isBlank() || request.scope.medication.isBlank() || request.record.isBlank() ||
            request.operation.isBlank() || request.revision < 1) return rejected(ShadowReason.INVALID_IDENTITY)
        if (prior != null) {
            if (prior.scope != request.scope || prior.operation != request.operation || prior.record != request.record ||
                prior.revision != request.revision || prior.fingerprint != fingerprint(request)) return rejected(ShadowReason.OPERATION_CONFLICT)
            // Existing capture is immutable evidence. A replay never proposes another consumption, even after reversal.
            return compare(request.scope, request.record, request.revision,
                ShadowResult(ShadowStatus.REPLAY, ShadowComparison.UNKNOWN, quantity = prior.quantity), emptyList(), observed, "CONSUME")
        }
        if (request.event in setOf(ShadowEvent.REMOVE, ShadowEvent.DETACH)) {
            return compare(request.scope, request.record, request.revision,
                ShadowResult(ShadowStatus.NO_STOCK_EFFECT, ShadowComparison.UNKNOWN), emptyList(), observed, "CONSUME")
        }
        val input = request.amount ?: return unknown(ShadowReason.MISSING_FACT)
        Measurements.error(input)?.let { return conversionFailure(it) }
        if (input.value.signum() <= 0) return rejected(ShadowReason.CONVERSION_REFUSED, MeasureError.INVALID_NUMBER)
        if (input.meaning.kind in setOf(QuantityKind.RELEASE_RATE, QuantityKind.ABSORBED_MASS))
            return rejected(ShadowReason.CONVERSION_REFUSED, MeasureError.INCOMPATIBLE_UNIT)
        val selection = request.selection ?: return unknown(ShadowReason.MISSING_FACT)
        if (!selection.confirmed) return unknown(ShadowReason.MISSING_FACT)
        if (!Measurements.refValid(selection.ref)) return rejected(ShadowReason.INVALID_IDENTITY)
        if (selection.containers.isEmpty() || selection.containers.distinct().size != selection.containers.size ||
            containers.map { it.uuid }.distinct().size != containers.size) return rejected(ShadowReason.INVALID_IDENTITY)
        val selected = selection.containers.map { id -> containers.singleOrNull { it.uuid == id } ?: return unknown(ShadowReason.MISSING_CONTAINER) }
        if (selected.any { it.uuid.isBlank() || it.legacyId <= 0 } || selected.map { it.legacyId }.distinct().size != selected.size)
            return rejected(ShadowReason.INVALID_IDENTITY)
        if (selected.any { it.scope != request.scope }) return rejected(ShadowReason.SCOPE_MISMATCH)
        if (request.ingredient == null || selected.any { it.ingredient == null }) return unknown(ShadowReason.MISSING_INGREDIENT)
        if (request.ingredient.kind != QuantityKind.ACTIVE_MASS || selected.any { it.ingredient != request.ingredient })
            return rejected(ShadowReason.CONVERSION_REFUSED, MeasureError.INGREDIENT_MISMATCH)
        val snapshots = selected.map { it.snapshot ?: return unknown(ShadowReason.MISSING_FACT) }
        if (snapshots.map { it.ref }.distinct().size != 1) return unknown(ShadowReason.MIXED_PRESENTATIONS)
        // Same claimed reference must not conceal conflicting strength evidence. Usable capacity is container-specific.
        val firstSource = snapshots.first()
        if (snapshots.any { it.relations != firstSource.relations || it.nominalTotal != firstSource.nominalTotal ||
                it.nominalUses != firstSource.nominalUses || it.releaseRate != firstSource.releaseRate })
            return rejected(ShadowReason.SOURCE_CONFLICT)
        Measurements.compareRefs(snapshots.first().ref, selection.ref)?.let { return conversionFailure(it) }
        val unit = selected.first().stockUnit ?: return unknown(ShadowReason.MISSING_UNIT)
        val meaning = selected.first().stockMeaning ?: return unknown(ShadowReason.MISSING_UNIT)
        if (selected.any { it.stockUnit != unit || it.stockMeaning != meaning }) return unknown(ShadowReason.MISSING_UNIT)
        if (selected.any { it.state !in setOf("IN_USE", "SEALED", "EMPTY") }) return rejected(ShadowReason.UNAVAILABLE_CONTAINER)
        if (selected.any { it.state == "IN_USE" && it.openedOn == null }) return unknown(ShadowReason.MISSING_ORDER)
        val result = if (input.meaning == meaning) Measurements.inUnit(input, unit) else when (
            val converted = snapshots.first().convert(input, meaning, unit, ActualPackage(selection.ref, true))) {
            is MeasureResult.Failure -> converted
            is MeasureResult.Success -> MeasureResult.Success(converted.value.quantity)
        }
        if (result is MeasureResult.Failure) return conversionFailure(result.error)
        val quantity = (result as MeasureResult.Success).value
        val sorted = selected.sortedWith(compareBy<FrozenContainer>({ if (it.state == "IN_USE") 0 else if (it.state == "SEALED") 1 else 2 },
            { if (it.state == "IN_USE") it.openedOn.orEmpty() else "" }, { it.legacyId }))
        var remaining = quantity.value
        val expected = mutableListOf<ExactAllocation>()
        for (c in sorted) {
            val used = c.used ?: return unknown(ShadowReason.MISSING_FACT)
            val available = c.snapshot!!.remaining(used, ActualPackage(selection.ref, true))
            if (available is MeasureResult.Failure) return if (available.error == MeasureError.UNKNOWN_CAPACITY)
                unknown(ShadowReason.UNKNOWN_CAPACITY, available.error) else conversionFailure(available.error)
            val raw = (available as MeasureResult.Success).value
            if (raw.unit != unit || raw.meaning != meaning) return unknown(ShadowReason.MISSING_UNIT)
            if (c.state == "EMPTY") {
                if (raw.value.signum() != 0) return rejected(ShadowReason.UNAVAILABLE_CONTAINER)
                continue
            }
            val take = raw.value.min(remaining)
            if (take.signum() > 0) expected += ExactAllocation(c.uuid, quantity.copy(value = take.stripTrailingZeros()))
            remaining -= take
        }
        val base = ShadowResult(ShadowStatus.CONVERTIBLE, ShadowComparison.UNKNOWN, quantity,
            expected.toList(), quantity.copy(value = remaining.stripTrailingZeros()))
        var compared = compare(request.scope, request.record, request.revision, base, expected, observed, "CONSUME")
        if (observedUnallocated != null && (!observedUnallocated.isFinite() ||
                BigDecimal(observedUnallocated).subtract(remaining).abs() > epsilon))
            compared = compared.copy(comparison = ShadowComparison.DIFFERENCE, reasons = compared.reasons + ShadowReason.UNALLOCATED_DIFFERENCE)
        return compared
    }
    /** Uses original captured values, never current catalog/medication quantities. */
    fun reverse(scope: ShadowScope, record: String, revision: Int, capture: ShadowCapture?, observed: List<LegacyAllocation>): ShadowResult {
        val original = capture ?: return unknown(ShadowReason.MISSING_FACT)
        if (scope != original.scope || record != original.record || revision < original.revision) return rejected(ShadowReason.SCOPE_MISMATCH)
        val expected = if (original.reversed) emptyList() else original.allocations
        val base = ShadowResult(if (original.reversed) ShadowStatus.REPLAY else ShadowStatus.CONVERTIBLE,
            ShadowComparison.UNKNOWN, original.quantity, expected)
        return compare(scope, record, revision, base, expected, observed, "REVERSE")
    }
    private fun compare(scope: ShadowScope, record: String, revision: Int, base: ShadowResult,
                        expected: List<ExactAllocation>, observed: List<LegacyAllocation>, kind: String): ShadowResult {
        val differences = mutableSetOf<ShadowReason>()
        var unknown = false
        if (observed.any { it.scope != scope }) differences += ShadowReason.SCOPE_MISMATCH
        if (observed.any { it.record != record || it.revision != revision }) differences += ShadowReason.RECORD_REVISION
        if (observed.size != expected.size || observed.map { it.entryId }.distinct().size != observed.size)
            differences += ShadowReason.ALLOCATION_COUNT
        expected.zip(observed).forEach { (e, o) ->
            if (e.container != o.container || o.kind != kind) differences += ShadowReason.ALLOCATION_ORDER
            if (o.unit == null || o.meaning == null) { unknown = true; differences += ShadowReason.LEGACY_UNIT_UNKNOWN }
            else if (o.unit != e.quantity.unit || o.meaning != e.quantity.meaning) differences += ShadowReason.LEGACY_UNIT_MISMATCH
            if (!o.delta.isFinite()) differences += ShadowReason.LEGACY_INVALID
            else {
                val signed = if (kind == "REVERSE") e.quantity.value.negate() else e.quantity.value
                // BigDecimal(double) observes the binary value; never claims to restore the user's original decimal precision.
                if (BigDecimal(o.delta).subtract(signed).abs() > epsilon) differences += ShadowReason.LEGACY_QUANTITY_DIFFERENCE
            }
            if (kind == "REVERSE" && (e.originalEntryId == null || o.reversalOf != e.originalEntryId)) differences += ShadowReason.REVERSAL_TARGET
        }
        val comparison = if (differences.any { it != ShadowReason.LEGACY_UNIT_UNKNOWN }) ShadowComparison.DIFFERENCE
            else if (unknown) ShadowComparison.UNKNOWN else ShadowComparison.APPROXIMATE_MATCH
        return base.copy(comparison = comparison, reasons = base.reasons + differences,
            additionalConfirmationRequired = base.additionalConfirmationRequired || unknown)
    }
    /** Admission contract only. Never imports or replaces the production forecast/date calculator. */
    fun forecastContract(confirmed: List<Quantity>, unknownContainers: Set<String>, unallocated: Quantity?): ShadowForecastContract {
        var total: Quantity? = null
        val reasons = mutableSetOf<ShadowReason>()
        for (q in confirmed) {
            val check = if (total == null) Measurements.inUnit(q, q.unit) else Measurements.add(total, q)
            if (check is MeasureResult.Failure) { reasons += ShadowReason.MISSING_UNIT; total = null; break }
            total = (check as MeasureResult.Success).value
        }
        if (unknownContainers.isNotEmpty()) reasons += ShadowReason.MISSING_FACT
        if (unallocated == null || Measurements.error(unallocated) != null || unallocated.value.signum() != 0) reasons += ShadowReason.UNALLOCATED_DIFFERENCE
        return ShadowForecastContract(total, unknownContainers.toSet(), unallocated, reasons.toSet(), reasons.isEmpty() && total != null)
    }
}
