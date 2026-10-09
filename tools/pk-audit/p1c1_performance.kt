package net.plainnotes.app
import net.plainnotes.app.conc.*
import net.plainnotes.app.data.*
import net.plainnotes.app.pk.*
import org.json.*
import org.junit.Test
import java.io.File
import java.time.Instant
/** Identical full-calculator calls in old/new trees, local runner only. Synthetic; no GC tuning. */
class P1c1PerformanceTest {
 @Test fun measureMatchedCalls(){
  val now=Instant.parse("2026-10-25T01:10:00Z");val t0=now.minusSeconds(1800)
  val m=MedicationEntity(1,"Synthetic E2","E2","SUBLINGUAL","MG",2.0,40.0,site_rotation=false,notifications_on=true,active=true,sort_order=0)
  val p=ProfileEntity(1,"E2","sublingual",sl_tier=2)
  val r=RecordEntity(1,1,taken_utc=t0.minusSeconds(2760).toEpochMilli(),taken_zone="Europe/Paris",actual_dose=2.0,status="ON_TIME",origin="APP",revision=1,config_snapshot=MedicationSnapshot.encode(m,p))
  fun lab(id:Long,v:Double)=LabValueEntity(id,"E2",v,"pg/mL",t0.plusSeconds(300).toEpochMilli(),"Europe/Paris")
  val cases=listOf(emptyList(),listOf(lab(1,400.0)),listOf(lab(1,800.0)),listOf(lab(1,400.0),lab(2,350.0)))
  fun batch(){cases.forEach{ls->val a=ConcentrationCalculator.compute(listOf(m),mapOf(1L to p),listOf(r),emptyList(),ls,80.0,now,true,CalibrationMode.CAUSAL,historyRead=HistoryRead(true,ConcentrationCalculator.hours(now)));check(a.currentPgMl!!.isFinite())}}
  repeat(2){batch()};val times=JSONArray();val heaps=JSONArray();val rt=Runtime.getRuntime()
  repeat(5){val heap=rt.totalMemory()-rt.freeMemory();val t=System.nanoTime();batch();times.put((System.nanoTime()-t)/1e9);heaps.put(rt.totalMemory()-rt.freeMemory()-heap)}
  File(System.getenv("P1C1_PERF_OUTPUT")!!).writeText(JSONObject().put("source_commit",System.getenv("P1C1_PERF_COMMIT")).put("synthetic_only",true).put("java_version",System.getProperty("java.version")).put("warmups",2).put("repetitions",5).put("cases_per_batch",4).put("seconds",times).put("heap_delta_bytes_not_peak",heaps).toString(2)+"\n")
 }
}
