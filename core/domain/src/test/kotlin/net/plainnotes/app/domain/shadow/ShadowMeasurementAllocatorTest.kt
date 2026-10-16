package net.plainnotes.app.domain.shadow

import java.math.BigDecimal
import net.plainnotes.app.domain.measurement.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

internal class ShadowMeasurementAllocatorTest {
    private val scope = ShadowScope("SYNTH-DATASET-A", "SYNTH-MED-A")
    private val ref = PresentationRef("SYNTH", "PRODUCT-A", "PACK-A", "v1", "a".repeat(64))
    private val active = Meaning(QuantityKind.ACTIVE_MASS, "SYNTH-compound-free:SA:1")
    private val count = Meaning(QuantityKind.USE_COUNT, "SYNTH-tablet@PACK-A:2mg:v1")
    private fun q(n: String, unit: MeasureUnit = MeasureUnit.TABLET, meaning: Meaning = count) = Quantity(BigDecimal(n), unit, meaning)
    private fun snapshot(r: PresentationRef = ref, capacity: String? = "2", unit: MeasureUnit = MeasureUnit.TABLET,
                         meaning: Meaning = count, strength: String = "2"): PresentationSnapshot {
        val e = ConversionEvidence(r, "synthetic://label", "content", "2026-10-16", "explicit source", true, true)
        val per = ContentRelation(q("1", unit, meaning), q(strength, MeasureUnit.MG, active), e)
        return PresentationSnapshot(r, null, q("64", unit, meaning), listOf(per, ContentRelation(per.to, per.from, e)),
            capacity?.let { q(it, unit, meaning) })
    }
    private fun box(id: String = "A", legacy: Long = 1, used: String = "0", state: String = "IN_USE",
                    snap: PresentationSnapshot? = snapshot(), unit: MeasureUnit = MeasureUnit.TABLET, meaning: Meaning = count) =
        FrozenContainer(scope, id, legacy, snap, active, unit, meaning, q(used, unit, meaning), state, if (state == "IN_USE") "2026-01-01" else null)
    private fun request(n: String = "1", unit: MeasureUnit = MeasureUnit.TABLET, meaning: Meaning = count,
                        ids: List<String> = listOf("A"), r: PresentationRef = ref, event: ShadowEvent = ShadowEvent.APPLY) =
        ShadowRequest(scope, "SYNTH-RECORD", 1, "SYNTH-OP", 1000, event, active, q(n, unit, meaning), ActualSelection(r, ids, true))
    private fun observed(id: String = "A", n: Double = 1.0, unit: MeasureUnit? = MeasureUnit.TABLET, meaning: Meaning? = count,
                         entry: String = "entry-A", kind: String = "CONSUME", reversal: String? = null) =
        LegacyAllocation(scope, id, "SYNTH-RECORD", 1, entry, kind, n, unit, meaning, reversal)
    private fun eval(r: ShadowRequest = request(), boxes: List<FrozenContainer> = listOf(box()), rows: List<LegacyAllocation> = listOf(observed()),
                     capture: ShadowCapture? = null) = ShadowMeasurementAllocator.evaluate(r, boxes, rows, capture)
    private fun amount(n: String, q: Quantity?) { assertNotNull(q); assertEquals(0, BigDecimal(n).compareTo(q!!.value)) }
    private fun capture(r: ShadowRequest = request(), result: ShadowResult = eval(), reversed: Boolean = false) =
        ShadowCapture(r.scope, r.record, r.revision, r.operation, ShadowMeasurementAllocator.fingerprint(r), result.quantity,
            result.allocations.map { it.copy(originalEntryId = "entry-${it.container}") }, reversed)

    @Test fun `confirmed same specification uses existing oldest open then sealed ordering`() {
        val r = request("2", ids = listOf("B", "A"))
        val result = eval(r, listOf(box("B", 2, state = "SEALED"), box(used = "1")), listOf(observed(), observed("B", entry = "entry-B")))
        assertEquals(ShadowStatus.CONVERTIBLE, result.status); assertEquals(ShadowComparison.APPROXIMATE_MATCH, result.comparison)
        assertEquals(listOf("A", "B"), result.allocations.map { it.container }); amount("2", result.quantity); amount("0", result.unallocated)
    }
    @Test fun `known shortage remains unallocated actual quantity rather than disappearing`() {
        val r = request("4", ids = listOf("A", "B"))
        val result = ShadowMeasurementAllocator.evaluate(r, listOf(box(used = "1"), box("B", 2, state = "SEALED")),
            listOf(observed(), observed("B", 2.0, entry = "entry-B")), observedUnallocated = 1.0)
        assertEquals(ShadowComparison.APPROXIMATE_MATCH, result.comparison); amount("1", result.unallocated)
        val wrong = ShadowMeasurementAllocator.evaluate(r, listOf(box(used = "1"), box("B", 2, state = "SEALED")),
            listOf(observed(), observed("B", 2.0, entry = "entry-B")), observedUnallocated = 0.0)
        assertTrue(ShadowReason.UNALLOCATED_DIFFERENCE in wrong.reasons)
    }
    @Test fun `matching totals cannot hide wrong order duplicate row or unexpected container`() {
        val r = request("2", ids = listOf("A", "B")); val boxes = listOf(box(used = "1"), box("B", 2, state = "SEALED"))
        val reversed = eval(r, boxes, listOf(observed("B", entry = "entry-B"), observed()))
        assertEquals(ShadowComparison.DIFFERENCE, reversed.comparison); assertTrue(ShadowReason.ALLOCATION_ORDER in reversed.reasons)
        val dup = eval(r, boxes, listOf(observed(), observed("B")))
        assertTrue(ShadowReason.ALLOCATION_COUNT in dup.reasons)
        assertTrue(ShadowReason.ALLOCATION_ORDER in eval(rows = listOf(observed("OTHER"))).reasons)
    }
    @Test fun `old binary observations never become recovered exact history`() {
        val result = eval(request("0.1"), rows = listOf(observed(n = 0.1)))
        assertEquals(ShadowComparison.APPROXIMATE_MATCH, result.comparison); assertEquals("UNKNOWN", result.legacyOriginalPrecision)
        assertEquals(ShadowComparison.DIFFERENCE, eval(request("0.1"), rows = listOf(observed(n = 0.2))).comparison)
    }
    @Test fun `nonfinite observations wrong record revision and wrong dataset are visible differences`() {
        assertTrue(ShadowReason.LEGACY_INVALID in eval(rows = listOf(observed(n = Double.NaN))).reasons)
        assertTrue(ShadowReason.RECORD_REVISION in eval(rows = listOf(observed().copy(revision = 2))).reasons)
        assertTrue(ShadowReason.SCOPE_MISMATCH in eval(rows = listOf(observed().copy(scope = scope.copy(dataset = "SYNTH-OTHER")))).reasons)
    }
    @Test fun `unknown legacy units prevent affirmative comparison`() {
        val result = eval(rows = listOf(observed(unit = null, meaning = null)))
        assertEquals(ShadowComparison.UNKNOWN, result.comparison); assertTrue(result.additionalConfirmationRequired)
        assertTrue(ShadowReason.LEGACY_UNIT_MISMATCH in eval(rows = listOf(observed(unit = MeasureUnit.MG))).reasons)
    }
    @Test fun `mixed strength pool never assembles a dose even with identical ingredients`() {
        val other = box("B", 2, snap = snapshot(ref.copy(product = "PRODUCT-B", presentation = "PACK-B"), strength = "1"))
        val result = eval(request("2", ids = listOf("A", "B")), listOf(box(), other), emptyList())
        assertEquals(ShadowStatus.UNKNOWN, result.status); assertEquals(setOf(ShadowReason.MIXED_PRESENTATIONS), result.reasons)
        assertTrue(result.allocations.isEmpty()); assertNull(result.quantity)
    }
    @Test fun `explicit source permits one strength without guessing from other available packages`() {
        val input = request("2", MeasureUnit.MG, active)
        val result = eval(input, listOf(box(), box("B", 2, snap = snapshot(ref.copy(product = "OTHER"), strength = "1"))))
        amount("1", result.quantity); assertEquals(ShadowComparison.APPROXIMATE_MATCH, result.comparison)
        assertEquals(listOf("A"), result.allocations.map { it.container })
    }
    @Test fun `missing confirmation identity unit capacity or ingredient does not default to zero`() {
        val inputs = listOf(request().copy(amount = null), request().copy(selection = null), request().copy(ingredient = null),
            request().copy(selection = ActualSelection(ref, listOf("A"), false)))
        inputs.forEach { assertEquals(ShadowStatus.UNKNOWN, eval(it).status) }
        listOf(box(snap = null), box(snap = snapshot(capacity = null)), box().copy(stockUnit = null), box().copy(ingredient = null)).forEach {
            val result = eval(boxes = listOf(it)); assertEquals(ShadowStatus.UNKNOWN, result.status); assertNull(result.quantity)
        }
    }
    @Test fun `same integer container handle in another scope cannot satisfy actual selection`() {
        val other = box().copy(scope = scope.copy(dataset = "SYNTH-OTHER"))
        assertEquals(ShadowStatus.REJECTED, eval(boxes = listOf(other)).status)
        assertEquals(ShadowStatus.UNKNOWN, eval(request(ids = listOf("DELETED"))).status)
    }
    @Test fun `nominal gel count is not confirmed usable capacity and no grams to pumps guess`() {
        val pump = count.copy(identity = "SYNTH-pump@PACK-A")
        val snap = snapshot(capacity = null, unit = MeasureUnit.PUMP, meaning = pump, strength = "0.75")
        val c = box(snap = snap, unit = MeasureUnit.PUMP, meaning = pump)
        val result = eval(request("2", MeasureUnit.PUMP, pump), listOf(c), emptyList())
        assertEquals(ShadowStatus.UNKNOWN, result.status); assertTrue(ShadowReason.UNKNOWN_CAPACITY in result.reasons)
        val gel = Meaning(QuantityKind.FORMULATION, "SYNTH-gel")
        val grams = eval(request("10", MeasureUnit.G, gel), listOf(c), emptyList())
        assertEquals(ShadowStatus.UNKNOWN, grams.status); assertEquals(MeasureError.INSUFFICIENT_SOURCE, grams.conversionError)
    }
    @Test fun `gel active mass needs explicit inverse and detects unsafe scalar pass through`() {
        val pump = count.copy(identity = "SYNTH-pump@PACK-A")
        val snap = snapshot(capacity = "60", unit = MeasureUnit.PUMP, meaning = pump, strength = "0.75")
        val r = request("1.5", MeasureUnit.MG, active)
        val c = box(snap = snap, unit = MeasureUnit.PUMP, meaning = pump)
        val correct = eval(r, listOf(c), listOf(observed(n = 2.0, unit = MeasureUnit.PUMP, meaning = pump)))
        amount("2", correct.quantity); assertEquals(ShadowComparison.APPROXIMATE_MATCH, correct.comparison)
        assertEquals(ShadowComparison.DIFFERENCE, eval(r, listOf(c), listOf(observed(n = 1.5, unit = MeasureUnit.PUMP, meaning = pump))).comparison)
    }
    @Test fun `release rate and absorbed mass cannot become stock quantities`() {
        listOf(q("25", MeasureUnit.UG_PER_24H, active.copy(kind = QuantityKind.RELEASE_RATE)),
            q("1", MeasureUnit.MG, active.copy(kind = QuantityKind.ABSORBED_MASS))).forEach {
            assertEquals(ShadowStatus.REJECTED, eval(request().copy(amount = it)).status)
        }
    }
    @Test fun `version drift and mismatched ingredient cannot reinterpret source`() {
        assertEquals(MeasureError.VERSION_MISMATCH, eval(request(r = ref.copy(version = "v2"))).conversionError)
        assertEquals(MeasureError.INGREDIENT_MISMATCH, eval(boxes = listOf(box().copy(ingredient = active.copy(identity = "SYNTH-ester")))).conversionError)
    }
    @Test fun `operation replay never proposes another consumption even if originally reversed`() {
        for (reversed in listOf(false, true)) {
            val result = eval(capture = capture(reversed = reversed), rows = emptyList())
            assertEquals(ShadowStatus.REPLAY, result.status); assertTrue(result.allocations.isEmpty())
            assertEquals(ShadowComparison.APPROXIMATE_MATCH, result.comparison)
        }
        assertEquals(ShadowComparison.DIFFERENCE, eval(capture = capture()).comparison)
    }
    @Test fun `same business operation with different amount time revision or source refuses`() {
        val prior = capture()
        listOf(request("2"), request().copy(occurredUtc = 1001), request().copy(revision = 2),
            request(r = ref.copy(version = "v2")), request().copy(scope = scope.copy(dataset = "SYNTH-OTHER"))).forEach {
            assertEquals(ShadowStatus.REJECTED, eval(it, capture = prior).status)
        }
    }
    @Test fun `reversal reads frozen original quantity and exact target without current catalog`() {
        val old = capture()
        val result = ShadowMeasurementAllocator.reverse(scope, "SYNTH-RECORD", 2, old,
            listOf(observed(n = -1.0, kind = "REVERSE", reversal = "entry-A").copy(revision = 2)))
        assertEquals(ShadowComparison.APPROXIMATE_MATCH, result.comparison); amount("1", result.quantity)
        val wrong = ShadowMeasurementAllocator.reverse(scope, "SYNTH-RECORD", 2, old,
            listOf(observed(n = -2.0, kind = "REVERSE", reversal = "WRONG").copy(revision = 2)))
        assertTrue(ShadowReason.REVERSAL_TARGET in wrong.reasons); assertTrue(ShadowReason.LEGACY_QUANTITY_DIFFERENCE in wrong.reasons)
    }
    @Test fun `patch detach and remove do not manufacture usage or consume again`() {
        listOf(ShadowEvent.DETACH, ShadowEvent.REMOVE).forEach { event ->
            val r = request(event = event).copy(amount = null, selection = null)
            val result = eval(r, rows = emptyList())
            assertEquals(ShadowStatus.NO_STOCK_EFFECT, result.status); assertEquals(ShadowComparison.APPROXIMATE_MATCH, result.comparison)
            assertEquals(ShadowComparison.DIFFERENCE, eval(r).comparison)
        }
        assertEquals(ShadowStatus.REJECTED, eval(request(event = ShadowEvent.REPLACE), capture = capture()).status)
    }
    @Test fun `frozen selections and captures do not alias caller mutable lists`() {
        val ids = mutableListOf("A"); val selected = ActualSelection(ref, ids, true); ids.clear()
        assertEquals(listOf("A"), selected.containers)
        val entries = mutableListOf(ExactAllocation("A", q("1"), "entry-A"))
        val old = ShadowCapture(scope, "SYNTH-RECORD", 1, "SYNTH-OP", "fingerprint", q("1"), entries)
        entries.clear(); assertEquals(1, old.allocations.size)
    }
    @Test fun `unknown or incompatible stock never obtains reliable date from partial subtotal`() {
        val zero = q("0")
        val unknown = ShadowMeasurementAllocator.forecastContract(listOf(q("2")), setOf("B"), zero)
        assertFalse(unknown.completeInputs); assertNull(unknown.reliableRemainingDate); amount("2", unknown.confirmedCompatible)
        val mixed = ShadowMeasurementAllocator.forecastContract(listOf(q("2"), q("2", MeasureUnit.MG, active)), emptySet(), zero)
        assertFalse(mixed.completeInputs); assertNull(mixed.confirmedCompatible)
        val compatible = ShadowMeasurementAllocator.forecastContract(listOf(q("2"), q("3")), emptySet(), zero)
        assertTrue(compatible.completeInputs); amount("5", compatible.confirmedCompatible); assertNull(compatible.reliableRemainingDate)
    }
    @Test fun `invalid quantity unallocated observation and missing opening order remain visible`() {
        assertEquals(ShadowStatus.REJECTED, eval(request("-1")).status)
        assertEquals(ShadowStatus.REJECTED, eval(request("0")).status)
        assertEquals(ShadowStatus.UNKNOWN, eval(boxes = listOf(box().copy(openedOn = null))).status)
        assertEquals(ShadowStatus.REJECTED, eval(boxes = listOf(box(state = "VOIDED"))).status)
        assertFalse(ShadowMeasurementAllocator.forecastContract(listOf(q("2")), emptySet(), null).completeInputs)
    }
    @Test fun `claimed same source cannot conceal contradictory strength facts`() {
        val result = eval(request("2", ids = listOf("A", "B")),
            listOf(box(), box("B", 2, snap = snapshot(strength = "1"))))
        assertEquals(ShadowStatus.REJECTED, result.status)
        assertTrue(ShadowReason.SOURCE_CONFLICT in result.reasons)
        assertTrue(result.allocations.isEmpty())
    }
    @Test fun `duplicate or invalid legacy handles cannot decide allocation order`() {
        for (boxes in listOf(listOf(box(legacy = 0)), listOf(box(), box("B", 1)))) {
            val result = eval(request(ids = boxes.map { it.uuid }), boxes)
            assertEquals(ShadowStatus.REJECTED, result.status)
            assertTrue(ShadowReason.INVALID_IDENTITY in result.reasons)
        }
    }

}
