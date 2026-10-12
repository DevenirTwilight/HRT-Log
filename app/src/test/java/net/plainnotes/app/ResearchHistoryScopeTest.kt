package net.plainnotes.app
import net.plainnotes.app.conc.ConcentrationCalculator
import net.plainnotes.app.conc.experimental.ResearchHistoryScope
import net.plainnotes.app.data.*
import org.junit.Assert.*
import org.junit.Test
class ResearchHistoryScopeTest {
 private val hour=600000.0
 private val sl=MedicationEntity(1,"Synthetic E2","E2","SUBLINGUAL","MG",1.0,30.0,null,15,120,false,null,true,true,0)
 private val profile=ProfileEntity(1,"E2","sublingual",sl_tier=2)
 private fun record(id:Long,med:MedicationEntity=sl,p:ProfileEntity=profile)=RecordEntity(id,med.id,
  taken_utc=((hour-1)*3600000).toLong(),taken_zone="UTC",actual_dose=1.0,status="ON_TIME",origin="APP",revision=1,config_snapshot=MedicationSnapshot.encode(med,p))
 @Test fun mixedScopesRetainOtherRouteAndUnknownFactsWithoutChangingRecords() {
  val routes=listOf("ORAL" to "oral","GEL" to "gel","PATCH" to "patchApply","INJECTION" to "injection")
  for((route,pk) in routes) {
   val other=record(2,sl.copy(id=2,route=route,unit=if(route=="PATCH")"PATCH" else "MG"),ProfileEntity(2,"E2",pk,gel_product_id=1,patch_release_ug_day=50.0))
   val rows=listOf(record(1),other,record(3).copy(config_snapshot="{}"))
   val before=rows.map{it.copy()};val s=ResearchHistoryScope.inspect(rows,emptyMap(),hour)
   assertEquals(1,s.otherE2Records);assertEquals(1,s.unknownRecords);assertTrue(s.hasSublingualE2)
   assertTrue(s.partial);assertFalse(s.compatibilityOnly);assertEquals(before,rows)
  }
 }
 @Test fun onlyOtherRoutesAreCompatibilityAndSublingualEvIsNeverM2() {
  val oral=record(1,sl.copy(route="ORAL"),profile.copy(pk_route="oral"))
  assertTrue(ResearchHistoryScope.inspect(listOf(oral),emptyMap(),hour).compatibilityOnly)
  val ev=record(2,p=profile.copy(ester="EV"))
  val s=ResearchHistoryScope.inspect(listOf(ev),emptyMap(),hour)
  assertFalse(s.hasSublingualE2);assertEquals(1,s.otherE2Records)
 }
 @Test fun unknownSnapshotsTimesAndMismatchedRoutesNeverProveSafeCompatibility() {
  for(r in listOf(record(1).copy(config_snapshot="{}"),record(1).copy(taken_utc=null),record(1,sl.copy(route="ORAL"),profile))) {
   val s=ResearchHistoryScope.inspect(listOf(r),emptyMap(),hour)
   assertEquals(1,s.unknownRecords);assertFalse(s.compatibilityOnly)
  }
 }
 @Test fun cancelledDeletedFutureOlderAndOtherCompoundsCannotInventMixedE2Exposure() {
  val spi=sl.copy(molecule="SPI",route="ORAL")
  val rows=listOf(record(1).copy(status="SKIPPED"),record(2).copy(deleted_at_utc=1),record(3).copy(taken_utc=((hour+1)*3600000).toLong()),
   record(4).copy(taken_utc=((hour-ConcentrationCalculator.HISTORY_DAYS*24-1)*3600000).toLong()),record(5,spi,profile.copy(pk_route="oral")))
  assertEquals(ResearchHistoryScope(0,0,false),ResearchHistoryScope.inspect(rows,emptyMap(),hour))
 }
 @Test fun mixedCompatibilityStillContainsOtherRouteContributionAndM2StaysSlOnly() {
  val oralMed=sl.copy(id=2,route="ORAL");val oralProfile=ProfileEntity(2,"E2","oral")
  val slRow=record(1);val oralRow=record(2,oralMed,oralProfile)
  val instant=java.time.Instant.ofEpochMilli((hour*3600000).toLong())
  fun compatibility(rows:List<RecordEntity>)=ConcentrationCalculator.compute(listOf(sl,oralMed),mapOf(1L to profile,2L to oralProfile),rows,emptyList(),emptyList(),70.0,instant,calibrate=false)
  val oldSl=compatibility(listOf(slRow));val oldMixed=compatibility(listOf(slRow,oralRow))
  assertTrue(oldMixed.currentPgMl!!>oldSl.currentPgMl!!)
  assertTrue(oldMixed.models.getValue(net.plainnotes.app.pk.Curve.E2).any { it.key=="E2_SL" })
  assertTrue(oldMixed.models.getValue(net.plainnotes.app.pk.Curve.E2).any { it.key=="E2_ORAL" })
  val audit=net.plainnotes.app.conc.experimental.ResearchHistoricalSlAdapter.audit(listOf(slRow,oralRow),emptyMap(),hour)
  assertEquals(listOf("r1"),audit.events.map{it.id})
  val p=net.plainnotes.app.experimental.ConcentrationPreview.compute(audit,net.plainnotes.app.pk.experimental.ResearchModel.M2,hour)
  val one=net.plainnotes.app.experimental.ConcentrationPreview.compute(net.plainnotes.app.conc.experimental.ResearchHistoricalSlAdapter.audit(listOf(slRow),emptyMap(),hour),net.plainnotes.app.pk.experimental.ResearchModel.M2,hour)
  assertEquals(one.currentPgMl!!,p.currentPgMl!!,0.0)
  assertTrue(ResearchHistoryScope.inspect(listOf(slRow,oralRow),emptyMap(),hour).partial)
 }

}
