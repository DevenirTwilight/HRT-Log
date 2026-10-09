package net.plainnotes.app
import net.plainnotes.app.conc.*
import net.plainnotes.app.data.*
import net.plainnotes.app.pk.*
import org.json.JSONObject
import org.json.JSONArray
import org.junit.Test
import java.io.File
import java.time.Instant
/** Run only in the starting d45a67a tree. Synthetic, independently executable pre-fix record. */
class P1c1BeforeTest {
 @Test fun exportHistoricalLeak() {
  val now=Instant.parse("2026-10-25T01:10:00Z");val t0=now.minusSeconds(30*60)
  val m=MedicationEntity(1,"Synthetic E2","E2","SUBLINGUAL","MG",2.0,40.0,site_rotation=false,notifications_on=true,active=true,sort_order=0)
  val p=ProfileEntity(1,"E2","sublingual",sl_tier=2)
  val records=listOf(RecordEntity(1,1,taken_utc=t0.minusSeconds(46*60).toEpochMilli(),taken_zone="Europe/Paris",actual_dose=2.0,status="ON_TIME",origin="APP",revision=1,config_snapshot=MedicationSnapshot.encode(m,p)))
  fun lab(id:Long,value:Double)=LabValueEntity(id,"E2",value,"pg/mL",t0.plusSeconds(5*60).toEpochMilli(),"Europe/Paris")
  val cases=listOf("none" to emptyList(),"future400" to listOf(lab(1,400.0)),"future800" to listOf(lab(1,800.0)),"same_time_two" to listOf(lab(1,400.0),lab(2,350.0)))
  val rows=JSONArray();val begin=System.nanoTime()
  for((id,labs) in cases){
   val a=ConcentrationCalculator.compute(listOf(m),mapOf(1L to p),records,emptyList(),labs,80.0,now,true,CalibrationMode.CAUSAL,historyRead=HistoryRead(true,ConcentrationCalculator.hours(now)))
   val t=ConcentrationCalculator.hours(t0)
   val v=listOf(a.e2,a.bandOuter!!.first,a.bandInner!!.first,a.bandInner!!.second,a.bandOuter!!.second).map{Pk.interpolate(a.timeH,it,t)!!}
   rows.put(JSONObject().put("id",id).put("values_center_p5_p25_p75_p95",JSONArray(v)).put("qualified_count",a.labEligibility.count{it.eligible}).put("raw_count",a.labs.size).put("summary_count",a.calibration?.labCount ?: 0).put("post_count",a.calibration?.model?.postDoseObservationCount ?: 0).put("log_amplitude",a.calibration?.model?.logAmplitude ?: JSONObject.NULL).put("covariance",a.calibration?.model?.cov?.let{JSONArray(it.toList())} ?: JSONObject.NULL).put("diagnostic_observed",a.calibration?.diagnostics?.observedPGmL ?: JSONObject.NULL))
  }
  val j=JSONObject().put("source_commit","d45a67a4f5309f1e951b2dfe69e9295fa42234cb").put("synthetic_only",true).put("now",now.toString()).put("query",t0.toString()).put("dose",t0.minusSeconds(46*60).toString()).put("sample",t0.plusSeconds(5*60).toString()).put("cases",rows).put("seconds",(System.nanoTime()-begin)/1e9)
  File(System.getenv("P1C1_BEFORE_OUTPUT")!!).writeText(j.toString(2)+"\n")
 }
}
