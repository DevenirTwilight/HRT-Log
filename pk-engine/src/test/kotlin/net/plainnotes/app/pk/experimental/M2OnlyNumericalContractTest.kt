package net.plainnotes.app.pk.experimental
import java.io.File
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import net.plainnotes.app.pk.Pk

/** Independent SciPy incomplete gamma, global peak refinement and adaptive quadrature oracle. */
class M2OnlyNumericalContractTest {
 private val reference=JSONObject(File("../docs/design/m2-only-evidence/independent-reference.json").readText())
 @Test fun fixedCandidateHashVersionAndPriceProjectionCannotRegressToExample() {
  val c=ResearchDisplayProtocol.candidate(ResearchModel.M2);val a=ResearchDisplayProtocol.anchor(ResearchModel.M2)
  assertEquals("p2x-39",c.id)
  assertEquals(reference.getString("model_version"),ResearchSublingualV01.version)
  assertEquals(reference.getString("display_protocol"),ResearchDisplayProtocol.version)
  assertEquals(reference.getString("parameter_sha256"),ResearchDisplayProtocol.parameterHash(c))
  assertEquals(PriceFigure1997.STUDY_ID,a.studyId);assertEquals(24.0,a.background,0.0)
  assertEquals(434.8357220077087,a.oneMgIncrementAtOneHour,1e-8)
  assertNotEquals(100.0,a.oneMgIncrementAtOneHour)
  assertFalse(ResearchSublingualV01.individualPredictionValidated)
 }
 @Test fun oneAndTwoMgTruePeaksAllRequestedAreasAndLateValuesMatchIndependentMath() {
  val c=ResearchDisplayProtocol.candidate(ResearchModel.M2);val a=ResearchDisplayProtocol.anchor(ResearchModel.M2)
  for(index in 0..1) {
   val row=reference.getJSONArray("independent_single_dose").getJSONObject(index)
   val dose=row.getDouble("dose_mg")
   fun exact(t:Double)=ResearchSublingualV01.studyScenario(t,listOf(ExperimentalDose(0.0,dose)),c,a)
   val fine=ResearchExtrema.maximum(0.0,24.0,1.0/120,::exact)
   val normal=ResearchExtrema.maximum(0.0,24.0,value=::exact)
   assertEquals(row.getDouble("cmax_pgml"),normal.pgMl,1e-7)
   assertEquals(row.getDouble("tmax_h"),normal.hour,2e-6)
   assertEquals(fine.pgMl,normal.pgMl,1e-7);assertEquals(fine.hour,normal.hour,2e-6)
   assertTrue(normal.pgMl>exact(1.0)+10);assertTrue(kotlin.math.abs(normal.hour-1.0)>.1)
   val values=row.getJSONObject("values_pgml")
   for(key in values.keySet()) {
    assertEquals(values.getDouble(key),exact(key.toDouble()),1e-7)
    assertEquals(values.getDouble(key)*3.671,exact(key.toDouble())*Pk.PMOL_PER_PG,1e-6)
   }
   for(span in listOf(4,8,24)) {
    var area=0.0;var previous=exact(0.0)-a.background
    for(i in 1..span*4000) {val y=exact(i/4000.0)-a.background;area+=(previous+y)/8000;previous=y}
    assertEquals(row.getJSONObject("auc_increment_pg_h_ml").getDouble(span.toString()),area,0.0001)
    assertEquals(row.getJSONObject("auc_total_pg_h_ml").getDouble(span.toString()),area+span*a.background,0.0001)
   }
  }
 }
 @Test fun recordedTimestampSuperpositionAddsBackgroundOnceAndNeverInventsDoses() {
  val c=ResearchDisplayProtocol.candidate(ResearchModel.M2);val a=ResearchDisplayProtocol.anchor(ResearchModel.M2)
  val doses=listOf(ExperimentalDose(-30.013,.75),ExperimentalDose(-20.717,1.2),ExperimentalDose(-3.27,2.0))
  val prepared=PreparedResearchHistory(doses,c)
  for(t in listOf(-40.0,-20.716,0.0,1.0,8.0,12.0,24.0,720.0)) {
   val expected=a.background+doses.filter{it.atHour<=t}.sumOf{a.oneMgIncrementAtOneHour*it.mg*ResearchSublingualV01.relativeIncrement(t-it.atHour,c)}
   assertEquals(expected,prepared.studyAt(t,a),1e-9)
  }
  assertEquals(a.background,prepared.studyAt(-40.0,a),0.0)
  assertEquals(3,doses.size)
  assertTrue(prepared.studyAt(24.0,a)>a.background)
 }
}
