package net.plainnotes.app.pk
import org.junit.jupiter.api.Test
import org.json.*
import java.io.File
import kotlin.math.exp
class NonSlBaseline {
 @Test fun generate() {
  val out=JSONObject().put("source_commit","2761ed95c49e1ae74f0accd46aaf66bb87254040")
  val rows=JSONArray();val grid=doubleArrayOf(.25,.5,1.0,2.0,6.0,12.0,24.0)
  for ((name,route,ester,extra,dose) in listOf(
   Case("oral_e2",Route.ORAL,Ester.E2,DoseExtras(),2.0),
   Case("oral_ev",Route.ORAL,Ester.EV,DoseExtras(),2.0),
   Case("injection",Route.INJECTION,Ester.EV,DoseExtras(),5.0),
   Case("gel",Route.GEL,Ester.E2,DoseExtras(gelProductId=1.0),1.5),
   Case("patch",Route.PATCH_APPLY,Ester.E2,DoseExtras(releaseRateUGPerDay=50.0,patchInstanceId="p"),1.0))) {
    val events=listOf(DoseEvent(name,route,0.0,dose,ester,80.0,extra))
    val pop=LabFit.bands(events,grid,emptyList(),CalibrationMode.RETROSPECTIVE).getValue(Curve.E2)
    val vals=Engine.simulate(events,grid=grid)!!.curves.getValue(Curve.E2)
    val labs=listOf(1,3,5).map{LabResult("l$it",grid[it],vals[it]*1.35,LabUnit.PG_ML)}
    val fit=LabFit.fit(events,labs);val band=LabFit.bands(events,grid,labs,CalibrationMode.RETROSPECTIVE).getValue(Curve.E2)
    rows.put(JSONObject().put("name",name).put("amplitude",exp(fit.logAmplitude)).put("rate",exp(fit.logRate)).put("cov",JSONArray(fit.cov.toList()))
     .put("population",JSONArray(pop.center.toList())).put("prior_p5",JSONArray(pop.p5.toList())).put("prior_p95",JSONArray(pop.p95.toList()))
     .put("calibrated",JSONArray(band.center.toList())).put("posterior_p5",JSONArray(band.p5.toList())).put("posterior_p95",JSONArray(band.p95.toList())))
  }
  out.put("cases",rows)
  val events=(0 until 120).map{DoseEvent("s$it",Route.SUBLINGUAL,it*6.0,2.0,Ester.E2,80.0)}
  val t=DoubleArray(2881){it*.25};val values=Engine.simulate(events,grid=t)!!.curves.getValue(Curve.E2)
  val labs=listOf(200,1000,2000).map{LabResult("l$it",t[it],values[it]*1.5,LabUnit.PG_ML)}
  repeat(2){LabFit.bands(events,t,labs,CalibrationMode.RETROSPECTIVE)}
  val times=(0 until 5).map{val a=System.nanoTime();LabFit.bands(events,t,labs,CalibrationMode.RETROSPECTIVE);(System.nanoTime()-a)/1e9}
  out.put("performance",JSONObject().put("events",120).put("grid_points",2881).put("samples",200).put("warmups",2).put("seconds",JSONArray(times)))
  val file=File(System.getenv("PK_BASELINE_OUTPUT") ?: "build/reports/non-sl-v1.json");file.parentFile.mkdirs();file.writeText(out.toString(2)+"\n")
 }
 data class Case(val name:String,val route:Route,val ester:Ester,val extra:DoseExtras,val dose:Double)
}
