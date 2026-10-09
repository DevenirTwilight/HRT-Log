package net.plainnotes.app.conc
import net.plainnotes.app.data.*
import net.plainnotes.app.pk.CalibrationMode
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

/** Actual Android PK path, synthetic data only; does not open or alter a user's database. */
class CalibrationEligibilityAndroidTest {
    private val now=Instant.parse("2026-10-25T01:10:00Z")
    private val med=MedicationEntity(1,"Synthetic E2","E2","SUBLINGUAL","MG",2.0,40.0,site_rotation=false,notifications_on=true,active=true,sort_order=0)
    private val profile=ProfileEntity(1,"E2","sublingual",sl_tier=2)
    private fun record(id:Long,ago:Long,snapshot:String=MedicationSnapshot.encode(med,profile))=RecordEntity(id,1,taken_utc=now.minusSeconds(ago).toEpochMilli(),taken_zone="Europe/Paris",actual_dose=2.0,status="ON_TIME",origin="APP",revision=1,config_snapshot=snapshot)
    private fun lab(ago:Long,value:Double)=LabValueEntity(1,"E2",value,"pg/mL",now.minusSeconds(ago).toEpochMilli(),"Europe/Paris")
    private fun run(records:List<RecordEntity>,labs:List<LabValueEntity>,on:Boolean=true)=ConcentrationCalculator.compute(listOf(med),mapOf(1L to profile),records,emptyList(),labs,80.0,now,on,CalibrationMode.RETROSPECTIVE,historyRead=HistoryRead(true,ConcentrationCalculator.hours(now)))
    @Test fun olderOnTreatmentSampleCannotAdd500AsBaseline() {
        val rows=listOf(record(1,201*86400L),record(2,46*60))
        val a=run(rows,listOf(lab(200*86400L,500.0)))
        assertNull(a.calibration);assertEquals(277.767987,a.currentPgMl!!,.002)
        assertEquals(BaselineEligibility.NOT_PRE_TREATMENT,a.labEligibility.single().baseline);assertEquals(1,a.labs.size)
    }
    @Test fun missingFrozenContextCannotAdd220AsBaseline() {
        val a=run(listOf(record(1,12*3600,"{}"),record(2,46*60)),listOf(lab(6*3600,220.0)))
        assertNull(a.calibration);assertFalse(a.labEligibility.single().eligible);assertEquals(1,a.labs.size)
        assertEquals(277.767987,a.currentPgMl!!,.002)
    }
    @Test fun eligibleActualIntakeStillCalibratesWithFixedSlRate() {
        val a=run(listOf(record(1,46*60)),listOf(lab(0,400.0)))
        assertTrue(a.labEligibility.single().eligible);assertTrue(a.currentPgMl!!>300)
        assertEquals(0.0,a.calibration!!.model.logRate,0.0);assertNull(a.calibration!!.model.baselinePGmL)
    }
    @Test fun switchOffUsesPopulationAndKeepsObservationAndVersion() {
        val a=run(listOf(record(1,46*60)),listOf(lab(0,400.0)),false)
        assertNull(a.calibration);assertEquals(1,a.labs.size);assertEquals(277.767987,a.currentPgMl!!,.002)
        assertEquals(2,ConcentrationCalculator.VERSION)
    }
}
