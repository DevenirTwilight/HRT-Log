package net.plainnotes.app.domain.stock

import java.math.BigDecimal
import net.plainnotes.app.domain.measurement.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class StockQuantumTest {
    private val ref=PresentationRef("USER_ENTERED","SYNTHETIC product 2mg","SYNTHETIC tablets","v1","a".repeat(64))
    private val actual=ActualPackage(ref,true)
    private val tablet=Meaning(QuantityKind.USE_COUNT,"SYNTHETIC product 2mg tablet")
    private fun q(text:String,unit:MeasureUnit=MeasureUnit.TABLET,meaning:Meaning=tablet)=Quantity(BigDecimal(text),unit,meaning)
    private fun <T> ok(result:MeasureResult<T>):T {
        assertTrue(result is MeasureResult.Success,"$result")
        return (result as MeasureResult.Success).value
    }
    private fun failure(error:MeasureError,result:MeasureResult<*>)=assertEquals(MeasureResult.Failure(error),result)
    @Test fun rawPhysicalDoubleDoesNotSatisfyTheExistingStrictCapacityBoundary() {
        assertTrue(0.4+0.2>0.6) // Existing strict guards must remain intact; use exact integer-mode projection.
        assertEquals(6.0,4.0+2.0)
    }
    @Test fun fractionalTabletCountDoesNotClaimFractionalIngredientContent() {
        val stock=ok(StockQuantum.create(q("28"),q("0.5"),actual))
        assertEquals(56L,stock.capacityQuanta)
        assertEquals(1L,ok(stock.toQuanta(q("0.5"),actual)))
        assertEquals(BigDecimal("27.5"),ok(stock.remaining(1)).value)
        failure(MeasureError.INGREDIENT_MISMATCH,stock.toQuanta(q("0.5",meaning=tablet.copy(identity="SYNTHETIC 1mg tablet")),actual))
        failure(MeasureError.INCOMPATIBLE_UNIT,stock.toQuanta(q("1",MeasureUnit.MG,Meaning(QuantityKind.ACTIVE_MASS,"estradiol")),actual))
    }
    @Test fun noUnknownSourceOrNominalCapacityCanCreateUsableStock() {
        failure(MeasureError.MISSING_HISTORICAL_SNAPSHOT,StockQuantum.create(q("64"),q("1"),null))
        failure(MeasureError.INSUFFICIENT_SOURCE,StockQuantum.create(q("64"),q("1"),actual.copy(userConfirmed=false)))
        failure(MeasureError.INSUFFICIENT_SOURCE,StockQuantum.create(q("64"),q("1"),actual.copy(ref=ref.copy(sha256="bad"))))
    }
    @Test fun quantitiesMustBeExactMultiplesWithoutRounding() {
        failure(MeasureError.PRECISION_OR_RANGE,StockQuantum.create(q("1"),q("0.3"),actual))
        val stock=ok(StockQuantum.create(q("28"),q("0.5"),actual))
        failure(MeasureError.PRECISION_OR_RANGE,stock.toQuanta(q("0.25"),actual))
        failure(MeasureError.INVALID_NUMBER,stock.toQuanta(q("-1"),actual))
        failure(MeasureError.INVALID_NUMBER,stock.toQuanta(q("0"),actual))
        failure(MeasureError.INVALID_NUMBER,StockQuantum.create(q("28"),q("0"),actual))
    }
    @Test fun gelVolumeAndMassRemainIndependentOfPumpsAndAbsorption() {
        val pump=Meaning(QuantityKind.USE_COUNT,"SYNTHETIC pump 0.75mg")
        val stock=ok(StockQuantum.create(q("64",MeasureUnit.PUMP,pump),q("1",MeasureUnit.PUMP,pump),actual))
        assertEquals(2L,ok(stock.toQuanta(q("2",MeasureUnit.PUMP,pump),actual)))
        failure(MeasureError.INCOMPATIBLE_UNIT,stock.toQuanta(q("2.5",MeasureUnit.G,Meaning(QuantityKind.FORMULATION,"gel")),actual))
        failure(MeasureError.INCOMPATIBLE_UNIT,stock.toQuanta(q("1.5",MeasureUnit.MG,Meaning(QuantityKind.ABSORBED_MASS,"estradiol")),actual))
        // 64 is an explicitly confirmed synthetic capacity, never inferred from a product's nominal label.
        assertEquals(BigDecimal("62"),ok(stock.remaining(2)).value)
    }
    @Test fun formulationVolumeDoesNotInferAnEsterOrActiveMass() {
        val solution=Meaning(QuantityKind.FORMULATION,"SYNTHETIC ester solution 20mg/mL")
        val stock=ok(StockQuantum.create(q("10",MeasureUnit.ML,solution),q("0.001",MeasureUnit.ML,solution),actual))
        assertEquals(250L,ok(stock.toQuanta(q("0.25",MeasureUnit.ML,solution),actual)))
        failure(MeasureError.INCOMPATIBLE_UNIT,stock.toQuanta(q("5",MeasureUnit.MG,Meaning(QuantityKind.ACTIVE_MASS,"estradiol")),actual))
    }
    @Test fun explicitlyCompatibleMassUnitsUseTheSameQuantum() {
        val gel=Meaning(QuantityKind.FORMULATION,"SYNTHETIC formulation")
        val stock=ok(StockQuantum.create(q("1",MeasureUnit.G,gel),q("1",MeasureUnit.MG,gel),actual))
        assertEquals(1000L,stock.capacityQuanta)
        assertEquals(500L,ok(stock.toQuanta(q("500000",MeasureUnit.UG,gel),actual)))
        assertEquals(BigDecimal("0.5"),ok(stock.remaining(500)).value)
    }
    @Test fun patchReleaseRatesAndAbsorbedQuantitiesCannotBecomeStock() {
        val rate=q("50",MeasureUnit.UG_PER_24H,Meaning(QuantityKind.RELEASE_RATE,"SYNTHETIC patch"))
        failure(MeasureError.INCOMPATIBLE_UNIT,StockQuantum.create(rate,rate,actual))
        val absorbed=q("1",MeasureUnit.MG,Meaning(QuantityKind.ABSORBED_MASS,"estradiol"))
        failure(MeasureError.INCOMPATIBLE_UNIT,StockQuantum.create(absorbed,absorbed,actual))
        val patch=Meaning(QuantityKind.USE_COUNT,"SYNTHETIC patch")
        val stock=ok(StockQuantum.create(q("8",MeasureUnit.PATCH,patch),q("1",MeasureUnit.PATCH,patch),actual))
        assertEquals(1L,ok(stock.toQuanta(q("1",MeasureUnit.PATCH,patch),actual)))
    }
    @Test fun originalSnapshotAndPackageConfirmationAreRequiredForEveryActualTake() {
        val stock=ok(StockQuantum.create(q("28"),q("0.5"),actual))
        failure(MeasureError.VERSION_MISMATCH,stock.toQuanta(q("1"),actual.copy(ref=ref.copy(version="v2"))))
        failure(MeasureError.PRESENTATION_MISMATCH,stock.toQuanta(q("1"),actual.copy(ref=ref.copy(product="SYNTHETIC other product"))))
        failure(MeasureError.INSUFFICIENT_SOURCE,stock.toQuanta(q("1"),actual.copy(userConfirmed=false)))
        failure(MeasureError.MISSING_HISTORICAL_SNAPSHOT,stock.toQuanta(q("1"),null))
        assertEquals(2L,ok(stock.toQuanta(q("1"),actual)))
    }
    @Test fun integerBridgeIsExactAndBoundedBeforeAnyDoubleProjection() {
        val max=StockQuantum.MAX_QUANTA
        val stock=ok(StockQuantum.create(q(max.toString()),q("1"),actual))
        assertEquals(max,stock.capacityQuanta)
        assertEquals(max,stock.capacityQuanta.toDouble().toLong())
        failure(MeasureError.PRECISION_OR_RANGE,StockQuantum.create(q((max+1).toString()),q("1"),actual))
        failure(MeasureError.PRECISION_OR_RANGE,StockQuantum.create(q("1"),q("0.000000000000000001"),actual))
        failure(MeasureError.INSUFFICIENT_STOCK,stock.remaining(max+1))
        failure(MeasureError.INVALID_NUMBER,stock.remaining(-1))
    }
    @Test fun repeatedConsumeReverseAndRestoreOrderDoNotAccumulateBinaryDrift() {
        val stock=ok(StockQuantum.create(q("0.6"),q("0.1"),actual))
        val amounts=listOf("0.4","0.2").map{ok(stock.toQuanta(q(it),actual))}
        repeat(1000) {
            val used=amounts.sum();assertEquals(6L,used)
            assertEquals(BigDecimal.ZERO,ok(stock.remaining(used)).value)
            assertEquals(6.0,amounts.reversed().sumOf{it.toDouble()})
            assertEquals(0.0,amounts.sumOf{it.toDouble()}-amounts.reversed().sumOf{it.toDouble()})
        }
        assertEquals(BigDecimal("0.6"),ok(stock.remaining(0)).value)
    }
    @Test fun restoredIntegerLedgerUsesExactSummationEvenWhenCancellationOrderIsAdversarial() {
        val max=StockQuantum.MAX_QUANTA
        val stock=ok(StockQuantum.create(q(max.toString()),q("1"),actual))
        val deltas=List(1000){max}+listOf(1L)+List(1000){-max}
        assertNotEquals(1.0,deltas.sumOf{it.toDouble()})
        assertEquals(1L,ok(stock.restoredUsed(0,deltas)))
        assertEquals(1L,ok(stock.restoredUsed(0,deltas.reversed())))
        failure(MeasureError.INSUFFICIENT_STOCK,stock.restoredUsed(0,listOf(-1)))
        failure(MeasureError.INSUFFICIENT_STOCK,stock.restoredUsed(max,listOf(1)))
        failure(MeasureError.INVALID_NUMBER,stock.restoredUsed(0,listOf(Long.MIN_VALUE)))
        failure(MeasureError.PRECISION_OR_RANGE,stock.restoredUsed(0,List(100001){1L}))
    }
    @Test fun deterministicFractionAndBalancePropertiesCoverBothBounds() {
        val stock=ok(StockQuantum.create(q("1000"),q("0.0001"),actual))
        var seed=17L
        repeat(2000) {
            seed=(seed*48271)%2147483647
            val used=seed%10000001L
            val physical=ok(stock.remaining(stock.capacityQuanta-used))
            if(used>0)assertEquals(used,ok(stock.toQuanta(physical,actual)))
            assertEquals(physical,ok(stock.remaining(stock.capacityQuanta-used)))
        }
        failure(MeasureError.PRECISION_OR_RANGE,StockQuantum.create(q("1.0000000000000000001"),q("1"),actual))
    }
}
