package net.plainnotes.app.conc
import net.plainnotes.app.data.*
import net.plainnotes.app.pk.*
import org.junit.Test
import org.junit.Assert.*
import java.time.Instant
/** Real Android synthetic calculator/snapshot runtime, no user database opened. */
class OutlierDispositionAndroidTest {
 private val now=Instant.parse("2026-10-25T01:10:00Z")
 private val med=MedicationEntity(1,"Synthetic E2","E2","SUBLINGUAL","MG",2.0,40.0,site_rotation=false,notifications_on=true,active=true,sort_order=0)
 private val p=ProfileEntity(1,"E2","sublingual",sl_tier=2)
 private val record=RecordEntity(1,1,taken_utc=now.minusSeconds(101*3600).toEpochMilli(),taken_zone="UTC",actual_dose=2.0,status="ON_TIME",origin="APP",revision=1,config_snapshot=MedicationSnapshot.encode(med,p))
 private fun lab(id:Long,t:Double,v:Double)=LabValueEntity(id,"E2",v,"pg/mL",record.taken_utc!!+(t*3600000).toLong(),"UTC")
 private fun pop(t:Double)=Engine.simulate(listOf(DoseEvent("r1",Route.SUBLINGUAL,0.0,2.0,Ester.E2,80.0,DoseExtras(sublingualTier=2.0))),grid=doubleArrayOf(t))!!.curves.getValue(Curve.E2)[0]
 private fun run(ls:List<LabValueEntity>,at:Instant=now)=ConcentrationCalculator.compute(listOf(med),mapOf(1L to p),listOf(record),emptyList(),ls,80.0,now,true,CalibrationMode.CAUSAL,historyRead=HistoryRead(true,ConcentrationCalculator.hours(now)),evaluationTime=at)
 @Test fun actualAllWarningObservationIsUsedNotExcluded(){val r=run(listOf(lab(1,100.0,200.0)));assertTrue(r.labEligibility.single().eligible);val m=r.calibration!!.model;assertEquals(setOf("l1"),m.usedLabIds);assertEquals(setOf("l1"),m.warningLabIds);assertTrue(m.excludedLabIds.isEmpty());assertEquals(1,m.postDoseObservationCount);assertEquals(1,r.labs.size)}
 @Test fun partialRemovalMatchesIndependentRetainedSubset(){val normal=listOf(.5,1.0,2.0).mapIndexed{i,t->lab(i+1L,t,pop(t))};val a=run(normal);val b=run(normal+lab(4,3.0,pop(3.0)*30));assertEquals(setOf("l4"),b.calibration!!.model.excludedLabIds);assertEquals(3,b.calibration!!.model.postDoseObservationCount);assertEquals(a.currentPgMl!!,b.currentPgMl!!,1e-7);assertArrayEquals(a.calibration!!.model.cov,b.calibration!!.model.cov,1e-12)}
 @Test fun historicalQueriesCannotBorrowFutureWarningOrUsedState(){val t=now.minusSeconds(7200);val a=run(emptyList(),t);val b=run(listOf(lab(1,100.0,200.0)),t);assertEquals(a.currentPgMl!!,b.currentPgMl!!,1e-7);assertNull(b.calibration);assertTrue(b.currentEvaluation!!.fitDisposition!!.warningLabIds.isEmpty());assertTrue(b.evaluateAt(ConcentrationCalculator.hours(now))!!.fitDisposition!!.usedLabIds.isNotEmpty())}
}
