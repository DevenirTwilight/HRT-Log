package net.plainnotes.app.data

import java.math.BigDecimal
import net.plainnotes.app.domain.measurement.*
import net.plainnotes.app.domain.stock.*
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[35])
class PackageMeasurementPayloadTest {
    private val product=UserReferenceProduct("SYNTHETIC pump",listOf("SYNTHETIC estradiol SA group1","SYNTHETIC hydrate FT group1"),"0.75 mg / pump","gel",listOf("cutaneous"),sourceVersion="synthetic-label-v1",recordedOn="2026-10-16")
    private val pump=Meaning(QuantityKind.USE_COUNT,"SYNTHETIC pump formulation")
    private val gel=Meaning(QuantityKind.FORMULATION,"SYNTHETIC gel formulation")
    private val active=Meaning(QuantityKind.ACTIVE_MASS,"SYNTHETIC estradiol SA group1")
    private fun q(n:String,u:MeasureUnit,m:Meaning)=Quantity(BigDecimal(n),u,m)
    private fun draft(capacity:String?="64",basis:StockCapacityBasis=StockCapacityBasis.USER_COUNTED)=PackageMeasurementDraft(product,MeasureUnit.PUMP,pump,"1",capacity,basis,
        nominalTotal=q("80",MeasureUnit.G,gel),nominalUses=q("64",MeasureUnit.PUMP,pump),relations=listOf(
            DeclaredPackageContent(q("1",MeasureUnit.PUMP,pump),q("0.75",MeasureUnit.MG,active),"SYNTHETIC label","per pump","2026-10-16","Explicit label transcription; not absorbed exposure",confirmedFromSource=true)))
    private fun <T> ok(r:MeasureResult<T>):T {assertTrue("$r",r is MeasureResult.Success);return (r as MeasureResult.Success).value}
    private fun reject(block:()->Unit) {try{block();fail("Invalid payload accepted")}catch(_:IllegalArgumentException){}}
    @Test fun canonicalRoundTripRetainsOriginalUnitsAndRoleDescriptions() {
        val frozen=PackageMeasurementPayload.freeze(draft())
        assertEquals(frozen.payload,PackageMeasurementPayload.read(frozen.payload).payload)
        assertEquals(ReferencePayload.sha(frozen.payload),frozen.sha256)
        assertEquals(product,frozen.product());assertEquals(product.ingredients,frozen.product().ingredients)
        assertEquals(MeasureUnit.G,frozen.measurement().nominalTotal!!.unit)
        assertEquals("80",frozen.measurement().nominalTotal!!.value.toPlainString())
    }
    @Test fun nominalPumpsNeverBecomeConfirmedUsableCapacity() {
        val frozen=PackageMeasurementPayload.freeze(draft(null,StockCapacityBasis.LABEL_NOMINAL))
        assertNull(frozen.measurement().confirmedUsable)
        assertEquals(MeasureResult.Failure(MeasureError.UNKNOWN_CAPACITY),frozen.stockScale())
        reject{PackageMeasurementPayload.freeze(draft("64",StockCapacityBasis.LABEL_NOMINAL))}
        reject{PackageMeasurementPayload.freeze(draft(null,StockCapacityBasis.USER_COUNTED))}
    }
    @Test fun directedLabelContentDoesNotClaimAbsorptionOrAllowAnImplicitReverse() {
        val frozen=PackageMeasurementPayload.freeze(draft());val snapshot=frozen.measurement();val actual=ActualPackage(frozen.ref(),true)
        val mass=ok(snapshot.convert(q("2",MeasureUnit.PUMP,pump),active,MeasureUnit.MG,actual))
        assertEquals(BigDecimal("1.5"),mass.quantity.value)
        assertEquals(MeasureResult.Failure(MeasureError.INSUFFICIENT_SOURCE),snapshot.convert(q("1.5",MeasureUnit.MG,active),pump,MeasureUnit.PUMP,actual))
        assertEquals(MeasureResult.Failure(MeasureError.INSUFFICIENT_SOURCE),snapshot.convert(q("2",MeasureUnit.PUMP,pump),active.copy(kind=QuantityKind.ABSORBED_MASS),MeasureUnit.MG,actual))
    }
    @Test fun historicalPayloadDoesNotChangeAfterCurrentProductOrCapacityChanges() {
        val original=PackageMeasurementPayload.freeze(draft());val before=original.payload
        val newer=PackageMeasurementPayload.freeze(draft("32").copy(product=product.copy(name="SYNTHETIC newer",sourceVersion="v2")))
        assertNotEquals(original.ref(),newer.ref());assertEquals(before,original.payload);assertEquals(product,original.product())
    }
    @Test fun sameSpecificationCanHaveDifferentConfirmedContainerCapacities() {
        val full=PackageMeasurementPayload.freeze(draft("64"));val partial=PackageMeasurementPayload.freeze(draft("32"))
        assertEquals(full.ref(),partial.ref());assertNotEquals(full.sha256,partial.sha256)
        assertEquals(64L,ok(full.stockScale()).capacityQuanta);assertEquals(32L,ok(partial.stockScale()).capacityQuanta)
    }
    @Test fun userDeclaredCapacityRemainsDistinguishableFromCountedCapacity() {
        val frozen=PackageMeasurementPayload.freeze(draft("60",StockCapacityBasis.USER_DECLARED))
        assertEquals(StockCapacityBasis.USER_DECLARED,ok(frozen.stockScale()).capacityBasis)
    }
    @Test fun unknownVolumeConcentrationIsNotInferredFromProductStrengthText() {
        val volume=Meaning(QuantityKind.FORMULATION,"SYNTHETIC injection solution")
        val frozen=PackageMeasurementPayload.freeze(PackageMeasurementDraft(product.copy(strength="20 mg/mL"),MeasureUnit.ML,volume,"0.001","10",StockCapacityBasis.USER_COUNTED))
        val result=frozen.measurement().convert(q("0.25",MeasureUnit.ML,volume),active,MeasureUnit.MG,ActualPackage(frozen.ref(),true))
        assertEquals(MeasureResult.Failure(MeasureError.INSUFFICIENT_SOURCE),result)
    }
    @Test fun fractionalPhysicalTabletsDoNotVerifyUniformIngredientContent() {
        val tablet=Meaning(QuantityKind.USE_COUNT,"SYNTHETIC tablet 2mg")
        val frozen=PackageMeasurementPayload.freeze(PackageMeasurementDraft(product,MeasureUnit.TABLET,tablet,"0.5","28",StockCapacityBasis.USER_COUNTED,
            relations=listOf(DeclaredPackageContent(q("1",MeasureUnit.TABLET,tablet),q("2",MeasureUnit.MG,active),"SYNTHETIC label","tablet","2026-10-16","Whole tablet only",true))))
        val actual=ActualPackage(frozen.ref(),true)
        assertEquals(1L,ok(ok(frozen.stockScale()).toQuanta(q("0.5",MeasureUnit.TABLET,tablet),actual)))
        assertEquals(MeasureResult.Failure(MeasureError.FRACTION_NOT_VERIFIED),frozen.measurement().convert(q("0.5",MeasureUnit.TABLET,tablet),active,MeasureUnit.MG,actual))
    }
    @Test fun unconfirmedConversionEvidenceRemainsUnusable() {
        val d=draft();val frozen=PackageMeasurementPayload.freeze(d.copy(relations=d.relations.map{it.copy(confirmedFromSource=false)}))
        assertEquals(MeasureResult.Failure(MeasureError.INSUFFICIENT_SOURCE),frozen.measurement().convert(q("1",MeasureUnit.PUMP,pump),active,MeasureUnit.MG,ActualPackage(frozen.ref(),true)))
    }
    @Test fun extraFieldsFloatingPointAndInvalidEnumsOrDecimalsAreRejected() {
        val frozen=PackageMeasurementPayload.freeze(draft())
        for(change in listOf<(JSONObject)->Unit>({it.put("unexpected",true)},{it.put("quantum",0.5)},{it.put("quantum","0.0000000000000000001")},{it.put("stock_unit","mg")},{it.put("confirmed_capacity","064")})) {
            val json=JSONObject(frozen.payload);change(json)
            val invalid=if(json.opt("quantum") is Double)json.toString() else ReferencePayload.canonical(json)
            reject{PackageMeasurementPayload.read(invalid)}
        }
    }
    @Test fun sourceVersionIsFrozenAndRequiredOnActualUse() {
        val original=PackageMeasurementPayload.freeze(draft());val newer=PackageMeasurementPayload.freeze(draft().copy(product=product.copy(sourceVersion="v2")))
        assertEquals(MeasureResult.Failure(MeasureError.VERSION_MISMATCH),Measurements.compareRefs(original.ref(),newer.ref())?.let{MeasureResult.Failure(it)})
    }
    @Test fun copiedCallerListsCannotMutateFrozenSourceFacts() {
        val names=product.ingredients.toMutableList();val input=draft().copy(product=product.copy(ingredients=names))
        val frozen=PackageMeasurementPayload.freeze(input);val before=frozen.payload;names.clear()
        assertEquals(before,frozen.payload);assertEquals(product.ingredients,frozen.product().ingredients)
    }
    @Test fun returnedRelationsStayImmutableAndCannotChangeMemoizedIdentityOrScale() {
        val frozen=PackageMeasurementPayload.freeze(draft());val before=frozen.payload;val ref=frozen.ref();val scale=ok(frozen.stockScale())
        val exposed=frozen.measurement()
        assertThrows(UnsupportedOperationException::class.java){(exposed.relations as MutableList).clear()}
        assertTrue(frozen.measurement().relations.isNotEmpty())
        assertEquals(before,frozen.payload);assertEquals(ref,frozen.ref())
        assertEquals(scale.capacityQuanta,ok(frozen.stockScale()).capacityQuanta)
        assertEquals(BigDecimal("64"),ok(frozen.stockScale()).capacity.value)
    }
}
