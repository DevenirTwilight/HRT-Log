package net.plainnotes.app
import net.plainnotes.app.conc.ConcentrationCalculator
import net.plainnotes.app.data.*
import net.plainnotes.app.pk.CalibrationMode
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import java.io.File
import java.time.Instant
/** Independent synthetic generator for b9a5393 only. Never placed in production source. */
class P1bBeforeTest {
 @Test fun export() {
  val now=Instant.parse("2026-10-25T01:10:00Z")
  val m=MedicationEntity(1,"Synthetic E2","E2","SUBLINGUAL","MG",2.0,40.0,site_rotation=false,notifications_on=true,active=true,sort_order=0)
  val p=ProfileEntity(1,"E2","sublingual",sl_tier=2)
  fun rec(id:Long,ago:Long,json:String=MedicationSnapshot.encode(m,p))=RecordEntity(id,1,taken_utc=now.minusSeconds(ago).toEpochMilli(),taken_zone="Europe/Paris",actual_dose=2.0,status="ON_TIME",origin="APP",revision=1,config_snapshot=json)
  fun lab(ago:Long,v:Double)=LabValueEntity(1,"E2",v,"pg/mL",now.minusSeconds(ago).toEpochMilli(),"Europe/Paris")
  val rows=listOf(Triple("old_treatment_lab",listOf(rec(1,201*86400L),rec(2,46*60)),listOf(lab(200*86400L,500.0))),Triple("unknown_context_lab",listOf(rec(1,12*3600,"{}"),rec(2,46*60)),listOf(lab(6*3600,220.0))))
  val array=JSONArray()
  rows.forEach{(id,records,labs)->
   val on=ConcentrationCalculator.compute(listOf(m),mapOf(1L to p),records,emptyList(),labs,80.0,now,true,CalibrationMode.RETROSPECTIVE)
   val off=ConcentrationCalculator.compute(listOf(m),mapOf(1L to p),records,emptyList(),labs,80.0,now,false,CalibrationMode.RETROSPECTIVE)
   array.put(JSONObject().put("id",id).put("population_pg_ml",off.currentPgMl!!).put("calibrated_pg_ml",on.currentPgMl!!).put("baseline_pg_ml",on.calibration!!.model.baselinePGmL!!).put("original_observations",on.labs.size))
  }
  val output=JSONObject().put("source_commit","b9a5393a7435accd3afe02407b38709a0e40f179").put("synthetic_only",true).put("cases",array)
  File(System.getenv("P1B_BEFORE_OUTPUT")!!).writeText(output.toString(2)+"\n")
 }
}
