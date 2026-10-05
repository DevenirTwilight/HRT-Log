package net.plainnotes.app.pk

import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.io.File
import kotlin.math.abs
import kotlin.math.max

/**
 * Compares the Kotlin port with fixtures produced by the unmodified upstream TypeScript
 * (tools/pk-reference). Every sampled point must agree within 1 % relative error, or
 * 0.01 absolute where the reference is below 1 (pg/mL or ng/mL).
 */
class UpstreamParityTest {
    private val dir = File(javaClass.getResource("/reference")!!.toURI())

    private fun doubles(a: JSONArray) = DoubleArray(a.length()) { a.getDouble(it) }
    private fun opt(o: JSONObject, k: String) = if (o.has(k) && !o.isNull(k)) o.getDouble(k) else null

    private fun event(o: JSONObject): DoseEvent {
        val x = o.getJSONObject("extras")
        return DoseEvent(o.getString("id"), Route.of(o.getString("route")), o.getDouble("timeH"), o.getDouble("doseMG"),
            Ester.valueOf(o.getString("ester")), o.getDouble("weightKG"),
            DoseExtras(opt(x, "concentrationMGmL"), opt(x, "areaCM2"), opt(x, "releaseRateUGPerDay"), opt(x, "sublingualTheta"),
                opt(x, "sublingualTier"), opt(x, "gelSite"), opt(x, "gelProductId"), opt(x, "gelWashAfterH"), opt(x, "gelCoverage"),
                opt(x, "gelCoApplied"), x.optString("patchInstanceId").ifEmpty { null }, x.optString("patchRemovalFor").ifEmpty { null }))
    }

    private fun lab(o: JSONObject) = LabResult(o.getString("id"), o.getDouble("timeH"), o.getDouble("concValue"),
        if (o.getString("unit") == "pmol/l") LabUnit.PMOL_L else LabUnit.PG_ML)

    private fun close(label: String, expected: Double, actual: Double) {
        val ok = if (abs(expected) < 1.0) abs(expected - actual) <= 0.01 else abs(expected - actual) / abs(expected) <= 0.01
        assertTrue(ok) { "$label expected $expected but was $actual" }
    }

    private fun series(name: String, idx: IntArray, expected: DoubleArray, actual: DoubleArray) =
        idx.forEachIndexed { k, i -> close("$name[$i]", expected[k], actual[i]) }

    @TestFactory fun fixtures(): List<DynamicTest> = dir.listFiles { f -> f.extension == "json" }!!.sortedBy { it.name }.map { file ->
        DynamicTest.dynamicTest(file.nameWithoutExtension) {
            val root = JSONObject(file.readText())
            val events = (0 until root.getJSONArray("events").length()).map { event(root.getJSONArray("events").getJSONObject(it)) }
            val s = root.getJSONObject("sim")
            val sim = Pk.simulate(events)!!
            assertEquals(s.getInt("steps"), sim.timeH.size, "grid size")
            close("start", s.getDouble("start"), sim.timeH.first()); close("end", s.getDouble("end"), sim.timeH.last())
            close("auc", s.getDouble("auc"), sim.auc)
            val idx = s.getJSONArray("idx").let { a -> IntArray(a.length()) { a.getInt(it) } }
            series("conc", idx, doubles(s.getJSONArray("conc")), sim.concPGmL)
            series("e2", idx, doubles(s.getJSONArray("e2")), sim.concPGmLE2)
            series("cpa", idx, doubles(s.getJSONArray("cpa")), sim.concPGmLCPA)
            val by = s.getJSONObject("by")
            assertEquals(by.keySet(), sim.byCompound.keys.map { it.name }.toSet(), "compounds")
            by.keySet().forEach { k -> series(k, idx, doubles(by.getJSONArray(k)), sim.byCompound.getValue(Ester.valueOf(k))) }

            val labsJson = root.getJSONArray("labs")
            if (labsJson.length() == 0) return@dynamicTest
            val labs = (0 until labsJson.length()).map { lab(labsJson.getJSONObject(it)) }
            val m = root.getJSONObject("model")
            val model = Calibration.replay(events, labs)
            close("thetaS", m.getDouble("thetaS"), model.thetaS); close("thetaK", m.getDouble("thetaK"), model.thetaK)
            val cov = doubles(m.getJSONArray("cov"))
            close("cov.a", cov[0], model.cov.a); close("cov.b", cov[1], model.cov.b); close("cov.d", cov[3], model.cov.d)
            assertEquals(m.getInt("obs"), model.observationCount); assertEquals(m.getInt("post"), model.postDoseObservationCount)
            if (!m.isNull("baseline")) close("baseline", m.getDouble("baseline"), model.baselinePGmL!!) else assertEquals(null, model.baselinePGmL)
            val d = root.getJSONObject("diag"); val diag = Calibration.lastDiagnostics(events, labs)!!
            close("nis", d.getDouble("NIS"), diag.nis); assertEquals(d.getBoolean("isOutlier"), diag.isOutlier)
            close("pred", d.getDouble("predictedPGmL"), diag.predictedPGmL); close("ci95Low", d.getDouble("ci95Low"), diag.ci95Low)
            close("ci95High", d.getDouble("ci95High"), diag.ci95High); close("convergence", d.getDouble("convergenceScore"), diag.convergenceScore)
            val mode = if (root.getString("mode") == "causal") CalibrationMode.CAUSAL else CalibrationMode.RETROSPECTIVE
            val curve = Calibration.calibrate(sim, events, labs, mode, root.getBoolean("aaLearn"), root.getBoolean("inhibit"))
            val ci = root.getJSONObject("ci")
            series("ci.e2", idx, doubles(ci.getJSONArray("e2")), curve.e2)
            series("ci.lo95", idx, doubles(ci.getJSONArray("lo95")), curve.ci95Low); series("ci.hi95", idx, doubles(ci.getJSONArray("hi95")), curve.ci95High)
            series("ci.lo68", idx, doubles(ci.getJSONArray("lo68")), curve.ci68Low); series("ci.hi68", idx, doubles(ci.getJSONArray("hi68")), curve.ci68High)
            val aa = ci.getJSONObject("aa")
            assertEquals(aa.keySet(), curve.antiandrogen.keys.map { it.name }.toSet(), "anti-androgen curves")
            aa.keySet().forEach { k ->
                val o = aa.getJSONObject(k); val (adj, lo, hi) = curve.antiandrogen.getValue(Ester.valueOf(k))
                series("$k.adj", idx, doubles(o.getJSONArray("adj")), adj); series("$k.lo", idx, doubles(o.getJSONArray("lo")), lo); series("$k.hi", idx, doubles(o.getJSONArray("hi")), hi)
            }
        }
    }

    @org.junit.jupiter.api.Test fun fixturesExist() = assertTrue(max(0, dir.listFiles()!!.size) >= 30)
}
