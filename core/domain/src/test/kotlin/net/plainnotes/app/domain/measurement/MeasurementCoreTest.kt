package net.plainnotes.app.domain.measurement

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.Random

internal class MeasurementCoreTest {
    private val ref = PresentationRef("SYNTH", "SYNTH-PRODUCT-A", "SYNTH-PACK-A", "synthetic-v1", "a".repeat(64))
    private val tablet = Meaning(QuantityKind.USE_COUNT, "tablet@SYNTH-PACK-A:2mg:v1")
    private val active = Meaning(QuantityKind.ACTIVE_MASS, "SYNTH-compound-free:SA:group1")
    private val gel = Meaning(QuantityKind.FORMULATION, "SYNTH-GEL-formulation-v1")
    private val pump = Meaning(QuantityKind.USE_COUNT, "pump@SYNTH-PACK-A:v1")
    private val evidence = ConversionEvidence(ref, "synthetic://fixture", "label", "2026-10-16", "explicit actual package; no absorption inference", true)
    private val actual = ActualPackage(ref, true)
    private fun q(n: String, u: MeasureUnit = MeasureUnit.MG, m: Meaning = active) = Quantity(BigDecimal(n), u, m)
    private fun rel(from: Quantity = q("1", MeasureUnit.TABLET, tablet), to: Quantity = q("2"), e: ConversionEvidence = evidence) = ContentRelation(from, to, e)
    private fun <T> ok(r: MeasureResult<T>): T { assertTrue(r is MeasureResult.Success, "$r"); return (r as MeasureResult.Success).value }
    private fun bad(e: MeasureError, r: MeasureResult<*>) = assertEquals(MeasureResult.Failure(e), r)
    private fun number(n: String, q: Quantity) = assertEquals(0, BigDecimal(n).compareTo(q.value))
    private fun convert(input: Quantity = q("1", MeasureUnit.TABLET, tablet), r: ContentRelation? = rel(), a: ActualPackage? = actual,
                        target: Meaning = active, unit: MeasureUnit = MeasureUnit.MG) = Measurements.convert(input, target, unit, r, a)

    @Test fun `explicit mass conversions preserve chemical identity`() {
        number("1000", ok(Measurements.inUnit(q("1", MeasureUnit.G), MeasureUnit.MG)))
        number("0.001", ok(Measurements.inUnit(q("1", MeasureUnit.UG), MeasureUnit.MG)))
        number("1", ok(Measurements.inUnit(q("1000000", MeasureUnit.UG), MeasureUnit.G)))
    }
    @Test fun `same numbers across unrelated dimensions are rejected`() {
        bad(MeasureError.INCOMPATIBLE_UNIT, Measurements.inUnit(q("1"), MeasureUnit.ML))
        bad(MeasureError.INCOMPATIBLE_UNIT, Measurements.add(q("1"), q("1", MeasureUnit.G, gel)))
        bad(MeasureError.INCOMPATIBLE_UNIT, Measurements.add(q("1", MeasureUnit.TABLET, tablet), q("1", MeasureUnit.PUMP, pump)))
        bad(MeasureError.INCOMPATIBLE_UNIT, Measurements.add(q("1", MeasureUnit.G, gel), q("1", MeasureUnit.ML, gel)))
    }
    @Test fun `compound ester hydrate and source roles never collapse`() {
        listOf("SYNTH-ester:SA:group1", "SYNTH-hydrate:SA:group1", "SYNTH-compound-free:FT:group1", "SYNTH-compound-free:ST:group1").forEach {
            bad(MeasureError.INGREDIENT_MISMATCH, Measurements.add(q("1"), q("1", m = active.copy(identity = it))))
            bad(MeasureError.INGREDIENT_MISMATCH, convert(target = active.copy(identity = it)))
        }
    }
    @Test fun `fractional tablet count exact does not imply fractional content`() {
        number("27.5", ok(Measurements.subtract(q("28", MeasureUnit.TABLET, tablet), q("0.5", MeasureUnit.TABLET, tablet))))
        bad(MeasureError.FRACTION_NOT_VERIFIED, convert(q("0.5", MeasureUnit.TABLET, tablet)))
        number("1", ok(convert(q("0.5", MeasureUnit.TABLET, tablet), rel(e = evidence.copy(fractionalContentVerified = true)))).quantity)
    }
    @Test fun `reverse direction requires explicit relation and actual packaging confirmation`() {
        bad(MeasureError.INCOMPATIBLE_UNIT, convert(q("2"), target = tablet, unit = MeasureUnit.TABLET))
        val reverse = rel(q("2"), q("1", MeasureUnit.TABLET, tablet))
        number("1", ok(convert(q("2"), reverse, target = tablet, unit = MeasureUnit.TABLET)).quantity)
        bad(MeasureError.FRACTION_NOT_VERIFIED, convert(q("1"), reverse, target = tablet, unit = MeasureUnit.TABLET))
        bad(MeasureError.INSUFFICIENT_SOURCE, convert(a = actual.copy(userConfirmed = false)))
    }
    @Test fun `missing history source and ambiguous strength have distinct failures`() {
        bad(MeasureError.MISSING_HISTORICAL_SNAPSHOT, convert(a = null))
        bad(MeasureError.INSUFFICIENT_SOURCE, convert(r = null))
        listOf(evidence.copy(verified = false), evidence.copy(source = ""), evidence.copy(locator = ""),
            evidence.copy(verifiedOn = "yesterday"), evidence.copy(conditions = ""), evidence.copy(ref = ref.copy(sha256 = "?"))).forEach {
            bad(MeasureError.INSUFFICIENT_SOURCE, convert(r = rel(e = it)))
        }
        bad(MeasureError.AMBIGUOUS_STRENGTH, convert(r = rel(to = q("0"))))
    }
    @Test fun `wrong product packaging version and digest rejected separately`() {
        listOf(ref.copy(namespace = "OTHER"), ref.copy(product = "OTHER"), ref.copy(presentation = "OTHER")).forEach {
            bad(MeasureError.PRESENTATION_MISMATCH, convert(a = ActualPackage(it, true)))
        }
        listOf(ref.copy(version = "synthetic-v2"), ref.copy(sha256 = "b".repeat(64))).forEach {
            bad(MeasureError.VERSION_MISMATCH, convert(a = ActualPackage(it, true)))
        }
    }
    @Test fun `same compound different strength counts cannot be combined`() {
        bad(MeasureError.INGREDIENT_MISMATCH, Measurements.add(q("1", MeasureUnit.TABLET, tablet),
            q("1", MeasureUnit.TABLET, tablet.copy(identity = "tablet@SYNTH-PACK-B:1mg:v1"))))
    }
    @Test fun `invalid numeric input and limits are explicit`() {
        listOf("-1", "-0", "NaN", "Infinity", "1e3", "0,5", "", " 1", ".5", "+1").forEach { bad(MeasureError.INVALID_NUMBER, Measurements.parse(it)) }
        listOf("9".repeat(81), "1000000000000000001", "0.0000000000000000001").forEach { bad(MeasureError.PRECISION_OR_RANGE, Measurements.parse(it)) }
        assertEquals(BigDecimal.ZERO, ok(Measurements.parse("0")))
        bad(MeasureError.INVALID_NUMBER, Measurements.inUnit(q("-1"), MeasureUnit.G))
        bad(MeasureError.PRECISION_OR_RANGE, Measurements.inUnit(q("1E+1000000"), MeasureUnit.G))
        bad(MeasureError.PRECISION_OR_RANGE, Measurements.add(q("1000000000000000000"), q("1")))
        bad(MeasureError.PRECISION_OR_RANGE, Measurements.inUnit(q("1000000000000000000", MeasureUnit.G), MeasureUnit.UG))
    }
    @Test fun `malformed quantity cannot bypass dimensional checks`() {
        bad(MeasureError.INVALID_QUANTITY, Measurements.inUnit(q("1", MeasureUnit.ML), MeasureUnit.ML))
        bad(MeasureError.INVALID_QUANTITY, Measurements.add(q("1", m = active.copy(identity = "")), q("1")))
        bad(MeasureError.INVALID_NUMBER, Measurements.subtract(q("0"), q("1")))
    }
    @Test fun `high precision exact decimal and subtraction exhaustion`() {
        val v = q("0.123456789012345678")
        number("0.246913578024691356", ok(Measurements.add(v, v)))
        var left = q("1")
        repeat(10) { left = ok(Measurements.subtract(left, q("0.1"))) }
        number("0", left)
        bad(MeasureError.PRECISION_OR_RANGE, convert(q("1", MeasureUnit.TABLET, tablet), rel(q("3", MeasureUnit.TABLET, tablet), q("1"))))
    }
    @Test fun `mass inverse and repeated conversions never drift deterministic property 1000 samples`() {
        val random = Random(20261016)
        repeat(1000) {
            val n = BigDecimal.valueOf(random.nextInt(1000000).toLong(), 6)
            val input = q(n.toPlainString())
            var result = input
            repeat(5) { result = ok(Measurements.inUnit(ok(Measurements.inUnit(result, MeasureUnit.G)), MeasureUnit.UG)); result = ok(Measurements.inUnit(result, MeasureUnit.MG)) }
            number(n.toPlainString(), result)
            val sum = ok(Measurements.add(input, q("0.25")))
            number(n.toPlainString(), ok(Measurements.subtract(sum, q("0.25"))))
        }
    }
    @Test fun `per-use relation scales linearly and is deterministic for confirmed integral tablets`() {
        repeat(1000) { i ->
            val input = q(i.toString(), MeasureUnit.TABLET, tablet)
            val a = convert(input)
            assertEquals(a, convert(input)); number((i * 2).toString(), ok(a).quantity)
            assertEquals(Measurements.ALGORITHM, (a as MeasureResult.Success).algorithm)
        }
    }
    @Test fun `pumped gel preserves nominal formulation content ingredient and uncertainty separately`() {
        // Synthetic fixture with numbers documented in prior repository evidence. No official product identity or data file distribution.
        val pumpToGel = rel(q("1", MeasureUnit.PUMP, pump), q("1.25", MeasureUnit.G, gel))
        val pumpToActive = rel(q("1", MeasureUnit.PUMP, pump), q("0.75"))
        val snap = PresentationSnapshot(ref, q("80", MeasureUnit.G, gel), q("64", MeasureUnit.PUMP, pump), listOf(pumpToGel, pumpToActive))
        number("2.5", ok(snap.convert(q("2", MeasureUnit.PUMP, pump), gel, MeasureUnit.G, actual)).quantity)
        number("1.5", ok(snap.convert(q("2", MeasureUnit.PUMP, pump), active, MeasureUnit.MG, actual)).quantity)
        number("80", ok(snap.convert(q("64", MeasureUnit.PUMP, pump), gel, MeasureUnit.G, actual)).quantity)
        bad(MeasureError.UNKNOWN_CAPACITY, snap.remaining(q("2", MeasureUnit.PUMP, pump), actual))
        val absorbed = active.copy(kind = QuantityKind.ABSORBED_MASS)
        bad(MeasureError.INSUFFICIENT_SOURCE, snap.convert(q("2", MeasureUnit.PUMP, pump), absorbed, MeasureUnit.MG, actual))
        bad(MeasureError.INCOMPATIBLE_UNIT, convert(q("2", MeasureUnit.PUMP, pump), rel(pumpToActive.from, q("0.75", m = absorbed)), target = absorbed))
    }
    @Test fun `patch total load release rate and patch count remain distinct`() {
        val patch = tablet.copy(identity = "patch@SYNTH-PACK-A:v1")
        val rate = active.copy(kind = QuantityKind.RELEASE_RATE)
        val snapshot = PresentationSnapshot(ref, q("8", MeasureUnit.PATCH, patch), null,
            listOf(rel(q("1", MeasureUnit.PATCH, patch), q("2"))), releaseRate = q("25", MeasureUnit.UG_PER_24H, rate))
        number("4", ok(snapshot.convert(q("2", MeasureUnit.PATCH, patch), active, MeasureUnit.MG, actual)).quantity)
        bad(MeasureError.INCOMPATIBLE_UNIT, Measurements.inUnit(snapshot.releaseRate!!, MeasureUnit.MG))
        bad(MeasureError.INSUFFICIENT_SOURCE, snapshot.convert(q("2"), rate, MeasureUnit.UG_PER_24H, actual))
    }
    @Test fun `capsule content conversion has no route or PK side effects`() {
        val capsule = tablet.copy(identity = "capsule@SYNTH-PACK-A:v1")
        number("200", ok(convert(q("2", MeasureUnit.CAPSULE, capsule), rel(q("1", MeasureUnit.CAPSULE, capsule), q("100")))).quantity)
        assertEquals(ref, actual.ref) // No route/Profile API is exposed.
    }
    @Test fun `injection concentration converts only explicitly described ester mass`() {
        val solution = gel.copy(identity = "SYNTH-ester-solution-v1")
        val ester = active.copy(identity = "SYNTH-ester:SA:group1")
        val concentration = rel(q("1", MeasureUnit.ML, solution), q("20", m = ester))
        number("5", ok(convert(q("0.25", MeasureUnit.ML, solution), concentration, target = ester)).quantity)
        bad(MeasureError.INGREDIENT_MISMATCH, convert(q("0.25", MeasureUnit.ML, solution), concentration))
        bad(MeasureError.INSUFFICIENT_SOURCE, convert(q("0.25", MeasureUnit.ML, solution), r = null, target = ester))
    }
    @Test fun `multicomponent package never adds SA FT ST or selects duplicate relations`() {
        val second = active.copy(identity = "SYNTH-second:SA:group2")
        val relations = mutableListOf(rel(), rel(to = q("3", m = second)))
        val snapshot = PresentationSnapshot(ref, null, null, relations)
        relations.clear() // Caller mutation cannot change the frozen relation list.
        number("2", ok(snapshot.convert(q("1", MeasureUnit.TABLET, tablet), active, MeasureUnit.MG, actual)).quantity)
        number("3", ok(snapshot.convert(q("1", MeasureUnit.TABLET, tablet), second, MeasureUnit.MG, actual)).quantity)
        bad(MeasureError.INGREDIENT_MISMATCH, Measurements.add(q("2"), q("3", m = second)))
        val ambiguous = PresentationSnapshot(ref, null, null, listOf(rel(), rel(to = q("4"))))
        bad(MeasureError.AMBIGUOUS_STRENGTH, ambiguous.convert(q("1", MeasureUnit.TABLET, tablet), active, MeasureUnit.MG, actual))
    }
    @Test fun `confirmed capacity remaining is exact and legacy unknown snapshot is not zero`() {
        val snapshot = PresentationSnapshot(ref, q("28", MeasureUnit.TABLET, tablet), null, listOf(rel()), q("28", MeasureUnit.TABLET, tablet))
        number("27.5", ok(snapshot.remaining(q("0.5", MeasureUnit.TABLET, tablet), actual)))
        number("0", ok(snapshot.remaining(q("28", MeasureUnit.TABLET, tablet), actual)))
        bad(MeasureError.INVALID_NUMBER, snapshot.remaining(q("28.5", MeasureUnit.TABLET, tablet), actual))
        bad(MeasureError.MISSING_HISTORICAL_SNAPSHOT, snapshot.remaining(q("0", MeasureUnit.TABLET, tablet), null))
        bad(MeasureError.VERSION_MISMATCH, snapshot.remaining(q("0", MeasureUnit.TABLET, tablet), ActualPackage(ref.copy(version = "v2"), true)))
    }
    @Test fun `even supplied relations cannot manufacture release or chemical equivalence`() {
        val rate = active.copy(kind = QuantityKind.RELEASE_RATE)
        bad(MeasureError.INCOMPATIBLE_UNIT, convert(q("2"), rel(q("2"), q("25", MeasureUnit.UG_PER_24H, rate)), target = rate, unit = MeasureUnit.UG_PER_24H))
        val ester = active.copy(identity = "SYNTH-ester:SA:group1")
        bad(MeasureError.INGREDIENT_MISMATCH, convert(q("2"), rel(q("2"), q("2", m = ester)), target = ester))
    }
    @Test fun `explicit inverse content relationships preserve exact fractional quantities property`() {
        val e = evidence.copy(fractionalContentVerified = true)
        val forward = rel(e = e)
        val reverse = rel(q("2"), q("1", MeasureUnit.TABLET, tablet), e)
        repeat(1000) { i ->
            val n = BigDecimal.valueOf(i.toLong()).divide(BigDecimal("8"))
            val mass = ok(convert(q(n.toPlainString(), MeasureUnit.TABLET, tablet), forward)).quantity
            val restored = ok(convert(mass, reverse, target = tablet, unit = MeasureUnit.TABLET)).quantity
            number(n.toPlainString(), restored)
        }
    }
    @Test fun `snapshot cannot borrow relation from another product or source version`() {
        val wrongPack = PresentationSnapshot(ref.copy(presentation = "SYNTH-CIP-OTHER"), null, null, listOf(rel()))
        bad(MeasureError.PRESENTATION_MISMATCH, wrongPack.convert(q("1", MeasureUnit.TABLET, tablet), active, MeasureUnit.MG, actual))
        val wrongVersion = PresentationSnapshot(ref.copy(version = "v2"), null, null, listOf(rel()))
        bad(MeasureError.VERSION_MISMATCH, wrongVersion.convert(q("1", MeasureUnit.TABLET, tablet), active, MeasureUnit.MG, actual))
    }
    @Test fun `unverified actual capacity cannot be used even when nominal count is known`() {
        val snapshot = PresentationSnapshot(ref, null, q("64", MeasureUnit.PUMP, pump), emptyList(), q("60", MeasureUnit.PUMP, pump))
        bad(MeasureError.INSUFFICIENT_SOURCE, snapshot.remaining(q("1", MeasureUnit.PUMP, pump), actual.copy(userConfirmed = false)))
        number("59", ok(snapshot.remaining(q("1", MeasureUnit.PUMP, pump), actual)))
        // Actual confirmed 60, not the nominal 64, supplies the arithmetic basis.
    }
    @Test fun `partial pumps capsules and patches do not imply proportional ingredient delivery`() {
        listOf(MeasureUnit.PUMP, MeasureUnit.CAPSULE, MeasureUnit.PATCH).forEach { unit ->
            val count = Meaning(QuantityKind.USE_COUNT, "SYNTH-use-${unit.name}")
            bad(MeasureError.FRACTION_NOT_VERIFIED, convert(q("0.5", unit, count), rel(q("1", unit, count), q("1"))))
        }
    }
    @Test fun `user recorded mass discrepancy is explicit and cannot overwrite user facts`() {
        val count = q("1", MeasureUnit.TABLET, tablet)
        val declared = q("1")
        bad(MeasureError.DECLARED_QUANTITY_CONFLICT, Measurements.verifyDeclaredContent(count, declared, rel(), actual))
        assertEquals(q("1"), declared)
        number("2000", ok(Measurements.verifyDeclaredContent(count, q("2000", MeasureUnit.UG), rel(), actual)).quantity)
    }
    @Test fun `unknown presentation identity cannot authorize remaining stock arithmetic`() {
        val unknown = ref.copy(product = "", sha256 = "")
        val snapshot = PresentationSnapshot(unknown, null, null, emptyList(), q("28", MeasureUnit.TABLET, tablet))
        bad(MeasureError.INSUFFICIENT_SOURCE, snapshot.remaining(q("1", MeasureUnit.TABLET, tablet), ActualPackage(unknown, true)))
    }
}
