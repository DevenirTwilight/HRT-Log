package net.plainnotes.app.domain.measurement

import java.math.BigDecimal
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import net.plainnotes.app.domain.prototype.measurement.Measurements as Prototype
import net.plainnotes.app.domain.prototype.measurement.MeasureResult as PrototypeResult

class ProductionMeasurementTest {
    private fun <T> ok(result:MeasureResult<T>):T = (result as MeasureResult.Success).value
    private val meaning=Meaning(QuantityKind.ACTIVE_MASS,"SYNTHETIC free compound / SA / group1")
    @Test fun `original parser agrees for valid and invalid boundary corpus`() {
        val corpus=listOf("0","1","0.000000000000000001","1000000000000000000","-1","1e3","0,5","1.0000000000000000001","9".repeat(81)) +
            (0..1000).map { BigDecimal(it).divide(BigDecimal("8")).toPlainString() }
        corpus.forEach { text ->
            val old=Prototype.parse(text);val current=Measurements.parse(text)
            when(old) {
                is PrototypeResult.Success -> assertEquals(old.value,ok(current),text)
                is PrototypeResult.Failure -> assertEquals(old.error.name,(current as MeasureResult.Failure).error.name,text)
            }
        }
    }
    @Test fun `arithmetic and unit changes agree with frozen prototype without rounding`() {
        val oldMeaning=net.plainnotes.app.domain.prototype.measurement.Meaning(
            net.plainnotes.app.domain.prototype.measurement.QuantityKind.ACTIVE_MASS,meaning.identity)
        repeat(1000) { i ->
            val n=BigDecimal.valueOf(i.toLong(),3)
            val old=net.plainnotes.app.domain.prototype.measurement.Quantity(n,net.plainnotes.app.domain.prototype.measurement.MeasureUnit.MG,oldMeaning)
            val current=Quantity(n,MeasureUnit.MG,meaning)
            val expected=Prototype.inUnit(old,net.plainnotes.app.domain.prototype.measurement.MeasureUnit.UG) as PrototypeResult.Success
            assertEquals(expected.value.value,ok(Measurements.inUnit(current,MeasureUnit.UG)).value)
            val oldSum=Prototype.add(old,old) as PrototypeResult.Success
            assertEquals(oldSum.value.value,ok(Measurements.add(current,current)).value)
        }
    }
    @Test fun `directed conversions agree with prototype for fractional and whole confirmed uses`() {
        val ref=PresentationRef("SYNTH","SYNTH-product","SYNTH-pack","v1","a".repeat(64))
        val count=Meaning(QuantityKind.USE_COUNT,"SYNTH-tablet-2mg")
        val evidence=ConversionEvidence(ref,"synthetic://fixture","label","2026-10-16","confirmed package",true,true)
        val relation=ContentRelation(Quantity(BigDecimal.ONE,MeasureUnit.TABLET,count),Quantity(BigDecimal("2"),MeasureUnit.MG,meaning),evidence)
        val oldRef=net.plainnotes.app.domain.prototype.measurement.PresentationRef(ref.namespace,ref.product,ref.presentation,ref.version,ref.sha256)
        val oldCount=net.plainnotes.app.domain.prototype.measurement.Meaning(net.plainnotes.app.domain.prototype.measurement.QuantityKind.USE_COUNT,count.identity)
        val oldActive=net.plainnotes.app.domain.prototype.measurement.Meaning(net.plainnotes.app.domain.prototype.measurement.QuantityKind.ACTIVE_MASS,meaning.identity)
        val oldEvidence=net.plainnotes.app.domain.prototype.measurement.ConversionEvidence(oldRef,evidence.source,evidence.locator,evidence.verifiedOn,evidence.conditions,true,true)
        val oldRelation=net.plainnotes.app.domain.prototype.measurement.ContentRelation(
            net.plainnotes.app.domain.prototype.measurement.Quantity(BigDecimal.ONE,net.plainnotes.app.domain.prototype.measurement.MeasureUnit.TABLET,oldCount),
            net.plainnotes.app.domain.prototype.measurement.Quantity(BigDecimal("2"),net.plainnotes.app.domain.prototype.measurement.MeasureUnit.MG,oldActive),oldEvidence)
        repeat(1000) { i ->
            val n=BigDecimal(i).divide(BigDecimal("8"))
            val input=Quantity(n,MeasureUnit.TABLET,count)
            val result=Measurements.convert(input,meaning,MeasureUnit.MG,relation,ActualPackage(ref,true))
            val expected=Prototype.convert(net.plainnotes.app.domain.prototype.measurement.Quantity(n,net.plainnotes.app.domain.prototype.measurement.MeasureUnit.TABLET,oldCount),
                oldActive,net.plainnotes.app.domain.prototype.measurement.MeasureUnit.MG,oldRelation,net.plainnotes.app.domain.prototype.measurement.ActualPackage(oldRef,true)) as PrototypeResult.Success
            assertEquals(expected.value.quantity.value,ok(result).quantity.value)
            assertEquals(expected.value.quantity.unit.name,ok(result).quantity.unit.name)
            assertEquals(expected.value.quantity.meaning.identity,ok(result).quantity.meaning.identity)
            val invalid=Measurements.convert(input,meaning,MeasureUnit.MG,relation,ActualPackage(ref,false))
            assertEquals(MeasureResult.Failure(MeasureError.INSUFFICIENT_SOURCE),invalid)
        }
    }
    @Test fun `canonical storage is plain exact decimal and extreme exponents safely fail`() {
        assertEquals("0",ok(Measurements.decimalText(BigDecimal("0.000"))))
        assertEquals("1000",ok(Measurements.decimalText(BigDecimal("1E+3"))))
        assertEquals("0.123456789012345678",ok(Measurements.decimalText(BigDecimal("0.123456789012345678"))))
        listOf(BigDecimal("1E+1000000"),BigDecimal("1E-1000000"),BigDecimal("1000000000000000001")).forEach {
            assertEquals(MeasureResult.Failure(MeasureError.PRECISION_OR_RANGE),Measurements.decimalText(it))
        }
    }
    @Test fun `public callers cannot bypass identity budget or mutate frozen relations`() {
        listOf("","x".repeat(2049),"SYNTHETIC\u0000identity").forEach {
            assertEquals(MeasureResult.Failure(MeasureError.INVALID_QUANTITY),Measurements.inUnit(Quantity(BigDecimal.ONE,MeasureUnit.MG,meaning.copy(identity=it)),MeasureUnit.MG))
        }
        val ref=PresentationRef("SYNTH","SYNTH-product","SYNTH-package","v1","a".repeat(64))
        val e=ConversionEvidence(ref,"synthetic://fixture","label","2026-10-16","explicit confirmation",true)
        val relation=ContentRelation(Quantity(BigDecimal.ONE,MeasureUnit.ML,Meaning(QuantityKind.FORMULATION,"SYNTH-solution")),Quantity(BigDecimal("20"),MeasureUnit.MG,meaning),e)
        val snapshot=PresentationSnapshot(ref,null,null,listOf(relation,relation))
        assertThrows(UnsupportedOperationException::class.java) {(snapshot.relations as MutableList).clear()}
        assertEquals(2,snapshot.relations.size)
        assertFalse(Measurements.refValid(ref.copy(product="x".repeat(2049))))
        assertEquals("ansm03-exact-v2",Measurements.ALGORITHM)
    }
}
