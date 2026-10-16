package net.plainnotes.app.domain.measurement

import java.math.BigDecimal
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** Tiny single-container protocol oracle, NOT a production allocator/backup codec. No automatic pack selection. */
internal class SyntheticHistoryContractTest {
    private val ref = PresentationRef("SYNTH", "SYNTH-P", "SYNTH-C", "v1", "c".repeat(64))
    private val tablets = Meaning(QuantityKind.USE_COUNT, "tablet@SYNTH-C:2mg:v1")
    private val active = Meaning(QuantityKind.ACTIVE_MASS, "SYNTH-active:SA:1")
    private fun q(n: String, unit: MeasureUnit = MeasureUnit.TABLET, meaning: Meaning = tablets) = Quantity(BigDecimal(n), unit, meaning)
    private val evidence = ConversionEvidence(ref, "synthetic://history", "line1", "2026-10-16", "confirmed actual package", true, true)
    private val relation = ContentRelation(q("1"), q("2", MeasureUnit.MG, active), evidence)
    private val actual = ActualPackage(ref, true)
    private val snapshot = PresentationSnapshot(ref, q("28"), null, listOf(relation), confirmedUsable = q("28"))
    private data class Capture(val operation: String, val input: Quantity, val output: Conversion)
    private data class Book(val used: Quantity, val entries: Map<String, Capture> = emptyMap(), val reversed: Set<String> = emptySet(), val voided: Boolean = false)
    private val empty = Book(q("0"))
    private fun <T> ok(r: MeasureResult<T>): T { assertTrue(r is MeasureResult.Success, "$r"); return (r as MeasureResult.Success).value }
    private fun bad(e: MeasureError, r: MeasureResult<*>) = assertEquals(MeasureResult.Failure(e), r)
    private fun consume(book: Book, id: String, amount: Quantity, a: ActualPackage? = actual): MeasureResult<Book> {
        book.entries[id]?.let {
            return if (it.input == amount && it.output.ref == a?.ref) MeasureResult.Success(book) else MeasureResult.Failure(MeasureError.OPERATION_CONFLICT)
        }
        if (book.voided) return MeasureResult.Failure(MeasureError.VOIDED)
        val conversion = Measurements.actualUse(amount, active, MeasureUnit.MG, relation, a)
        if (conversion is MeasureResult.Failure) return conversion
        val used = Measurements.add(book.used, amount)
        if (used is MeasureResult.Failure) return used
        val next = (used as MeasureResult.Success).value
        val remaining = snapshot.remaining(next, a)
        if (remaining is MeasureResult.Failure) return if (remaining.error == MeasureError.INVALID_NUMBER)
            MeasureResult.Failure(MeasureError.INSUFFICIENT_STOCK) else remaining
        val capture = Capture(id, amount, (conversion as MeasureResult.Success).value)
        return MeasureResult.Success(book.copy(used = next, entries = book.entries + (id to capture)))
    }
    private fun reverse(book: Book, id: String): Book {
        if (id in book.reversed) return book
        val capture = book.entries.getValue(id)
        // Uses the frozen original input, never a current relation/strength.
        return book.copy(used = ok(Measurements.subtract(book.used, capture.input)), reversed = book.reversed + id)
    }
    @Test fun `record replay is idempotent different payload fails and failures leave input unchanged`() {
        val b = ok(consume(empty, "op1", q("0.5")))
        assertEquals(b, ok(consume(b, "op1", q("0.5"))))
        bad(MeasureError.OPERATION_CONFLICT, consume(b, "op1", q("1")))
        bad(MeasureError.MISSING_HISTORICAL_SNAPSHOT, consume(b, "op2", q("1"), null))
        assertEquals(q("0.5"), b.used); assertEquals(1, b.entries.size)
        assertEquals(emptyMap<String, Capture>(), empty.entries)
    }
    @Test fun `zero actual use is illegal exhausted remaining is legal and stock cannot go below zero`() {
        bad(MeasureError.INVALID_NUMBER, consume(empty, "zero", q("0")))
        bad(MeasureError.INVALID_NUMBER, consume(empty, "negative", q("-1")))
        val full = ok(consume(empty, "all", q("28")))
        assertEquals(BigDecimal.ZERO, ok(snapshot.remaining(full.used, actual)).value)
        bad(MeasureError.INSUFFICIENT_STOCK, consume(full, "extra", q("0.5")))
        assertEquals(q("28"), full.used)
    }
    @Test fun `reversal is exact repeatable and retains original frozen capture`() {
        val b = ok(consume(empty, "op1", q("0.5")))
        val back = reverse(b, "op1")
        assertEquals(BigDecimal.ZERO, back.used.value)
        assertEquals(back, reverse(back, "op1")); assertEquals(b.entries, back.entries)
        assertEquals(back, ok(consume(back, "op1", q("0.5")))) // Same operation never consumes again after reversal.
    }
    @Test fun `voided protocol refuses consumption before producing any capture`() {
        val void = empty.copy(voided = true)
        bad(MeasureError.VOIDED, consume(void, "op1", q("1")))
        assertEquals(empty.entries, void.entries); assertEquals(empty.used, void.used)
        // Real void/restore, consumption/context hash policy: existing Room StockCorrectionTest, not this oracle.
    }
    @Test fun `catalog and plan changes leave original capture and original container interpretation frozen`() {
        val b = ok(consume(empty, "op1", q("0.5")))
        val original = b.entries.getValue("op1")
        val newRef = ref.copy(version = "v2", sha256 = "d".repeat(64))
        val currentRelation = relation.copy(to = q("4", MeasureUnit.MG, active), evidence = evidence.copy(ref = newRef))
        bad(MeasureError.VERSION_MISMATCH, Measurements.convert(q("1"), active, MeasureUnit.MG, currentRelation, actual))
        assertEquals(q("1", MeasureUnit.MG, active), original.output.quantity)
        assertEquals(ref, original.output.ref)
        assertEquals(BigDecimal.ZERO, reverse(b, "op1").used.value)
        assertEquals(original, b.entries.getValue("op1"))
        val otherCount = tablets.copy(identity = "tablet@SYNTH-C2:4mg:v2")
        bad(MeasureError.INGREDIENT_MISMATCH, Measurements.add(q("28"), q("28", meaning = otherCount)))
    }
}
