package net.plainnotes.app.pk
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class BaselineEligibilityTest {
    private val event=DoseEvent("s",Route.SUBLINGUAL,0.0,2.0,Ester.E2,80.0)
    private val labs=listOf(LabResult("old",-1.0,500.0,LabUnit.PG_ML),LabResult("on",1.0,300.0,LabUnit.PG_ML))
    @Test fun earlyObservationIsNotAutomaticallyBaselineOrFitInput() {
        val oldOnly=LabFit.fit(listOf(event),labs.take(1))
        assertNull(oldOnly.baselinePGmL);assertEquals(0,oldOnly.postDoseObservationCount);assertEquals(0.0,oldOnly.logAmplitude)
        val withOld=LabFit.fit(listOf(event),labs);val withoutOld=LabFit.fit(listOf(event),labs.drop(1))
        assertNull(withOld.baselinePGmL);assertEquals(withoutOld.logAmplitude,withOld.logAmplitude,0.0)
        val a=LabFit.bands(listOf(event),doubleArrayOf(.5,1.0),labs,CalibrationMode.RETROSPECTIVE).getValue(Curve.E2)
        val b=LabFit.bands(listOf(event),doubleArrayOf(.5,1.0),labs.drop(1),CalibrationMode.RETROSPECTIVE).getValue(Curve.E2)
        assertArrayEquals(b.center,a.center,0.0);assertArrayEquals(b.p95,a.p95,0.0)
    }
    @Test fun explicitConfirmationIsTheOnlyBaselineSourceAndUnknownIdsDoNothing() {
        assertEquals(500.0,LabFit.fit(listOf(event),labs,confirmedBaselineLabIds=setOf("old")).baselinePGmL)
        assertNull(LabFit.fit(listOf(event),labs,confirmedBaselineLabIds=setOf("missing","on")).baselinePGmL)
    }
    @Test fun nonE2EventDoesNotDefineTreatmentStart() {
        val cpa=DoseEvent("c",Route.ORAL,-10.0,10.0,Ester.CPA,80.0)
        val a=LabFit.fit(listOf(cpa,event),labs)
        assertNull(a.baselinePGmL);assertEquals(1,a.postDoseObservationCount)
    }
    @Test fun explicitBaselineCannotLeakBeforeItIsSampledInCausalBands() {
        val grid=doubleArrayOf(-2.0,-.5,.5)
        val b=LabFit.bands(listOf(event),grid,labs.take(1),CalibrationMode.CAUSAL,confirmedBaselineLabIds=setOf("old")).getValue(Curve.E2)
        assertEquals(0.0,b.center[0]);assertEquals(500.0,b.center[1])
    }
}
