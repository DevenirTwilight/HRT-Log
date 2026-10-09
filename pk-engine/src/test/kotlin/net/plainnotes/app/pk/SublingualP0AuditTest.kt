package net.plainnotes.app.pk

import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.File
import java.security.MessageDigest
import kotlin.math.*

/** P0: characterize the released implementation, including confirmed defects; do not approve them as desired behavior. */
class SublingualP0AuditTest {
    private val times = doubleArrayOf(.25, .5, .75, 46.0 / 60, 1.0, 1.5, 2.0, 3.0, 4.0, 6.0, 8.0, 12.0, 24.0)
    private fun event(id: String = "s", t: Double = 0.0, dose: Double = 2.0, tier: Int = 2) =
        DoseEvent(id, Route.SUBLINGUAL, t, dose, Ester.E2, 80.0, DoseExtras(sublingualTier = tier.toDouble()))
    private fun curve(events: List<DoseEvent>, grid: DoubleArray, rate: Double = 1.0, amplitude: Double = 1.0) =
        Engine.simulate(events, grid = grid) { _, _ -> Scale(amplitude, rate) }!!.curves.getValue(Curve.E2)
    /** Independent stable evaluation: factoring the exponential avoids subtraction of near-equal values. */
    private fun stable(model: FittedModel, t: Double): Double = if (t < 0) 0.0 else model.terms.sumOf { (a, k) ->
        a * exp(-k * t) * -expm1(-(model.ka - k) * t)
    }
    private fun stableSl(t: Double, dose: Double = 2.0): Double = dose *
        (stable(PkParams.model("E2_SL"), t) + PkParams.model("E2_SL").swallowedShare * stable(PkParams.model("E2_ORAL"), t))

    @Test fun releasedParametersAndLiteratureAreIndependentInputs() {
        val bytes = javaClass.getResourceAsStream("/pk-params.json")!!.readBytes()
        val evidence = JSONObject(javaClass.getResourceAsStream("/sublingual-literature-validation.json")!!.bufferedReader().readText())
        val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        assertEquals(evidence.getString("production_params_sha256"), sha, "P0 must not change production parameters")
        val records = evidence.getJSONArray("records")
        val external = (0 until records.length()).map { records.getJSONObject(it) }.filter { it.getBoolean("independent_validation") }
        assertTrue(external.map { it.getString("study_id") }.toSet().size >= 2)
        external.forEach { assertFalse(it.getBoolean("used_in_fitting")); assertTrue(it.getString("source_location").isNotBlank()) }
        assertEquals(1759.0, external.single { it.getString("id") == "pines_60min" }.getDouble("value"))
        assertEquals(1994.0, external.single { it.getString("id") == "yaish_90min" }.getDouble("value"))
        assertEquals(1.0005, PkParams.model("E2_SL").ka)
    }

    @Test fun exactSingleDoseAndProductionInterpolationAreDifferent() {
        val direct = curve(listOf(event()), times)
        times.indices.forEach { assertEquals(stableSl(times[it]), direct[it], 1e-7) }
        assertEquals(278.86477960569, direct[3], 1e-7)
        val grid = Engine.gridFor(listOf(event()), 46.0 / 60 + 720)
        val interpolated = Pk.interpolate(grid, curve(listOf(event()), grid), 46.0 / 60)!!
        assertEquals(277.7679870778, interpolated, 1e-6)
        assertNotEquals(direct[3], interpolated, .5)
        assertEquals(direct[3], curve(listOf(event().copy(weightKG = 60.0)), times)[3], 1e-9)
    }

    @Test fun repeatedDosesSuperposeAndRemainStableForLongHistories() {
        for (tau in listOf(6.0, 12.0, 24.0)) {
            val events = (0 until 1460).filter { it % 5 != 0 }.map { event("d$it", (it - 1459) * tau) }
            val actual = curve(events.reversed(), times)
            times.indices.forEach { i ->
                val expected = events.sumOf { stableSl(times[i] - it.timeH) }
                assertEquals(expected, actual[i], max(1e-7, expected * 1e-8))
                assertTrue(actual[i].isFinite() && actual[i] >= 0)
            }
        }
        val a = curve(listOf(event()), times)
        for (dose in listOf(1.0, 4.0)) {
            val b = curve(listOf(event(dose = dose)), times)
            times.indices.forEach { assertEquals(a[it] * dose / 2, b[it], 1e-8) }
        }
        // This proves software linearity, not human dose proportionality.
    }

    @Test fun holdTimeTiersAreFlaggedAndUseUnvalidatedLinearScaling() {
        for (tier in 0..3) {
            val choice = Engine.choose(event(tier = tier)) as ModelChoice.Use
            assertEquals(tier != 2, CurveFlag.EXTRAPOLATED_TIER in choice.flags)
            assertTrue(CurveFlag.EXTRAPOLATED_AFTER_CALIBRATED_HOURS in choice.flags)
            val sl = PkParams.model("E2_SL")
            val mucosal = min(1.0, (1 - sl.swallowedShare) * sl.tierMinutes[tier] / 10)
            assertEquals(mucosal / (1 - sl.swallowedShare), choice.parts.first().third, 1e-12)
        }
    }

    @Test fun characterizeNearEqualRateCalibrationClippingAndExplodingPriorBand() {
        val events = listOf(event())
        val pop = curve(events, doubleArrayOf(46.0 / 60))[0]
        assertTrue(curve(events, doubleArrayOf(46.0 / 60), rate = .9)[0] > 90 * pop,
            "Confirmed defect: changing rate by -10% changes empirical peak amplitude by ~100x")
        assertEquals(0.0, curve(events, doubleArrayOf(46.0 / 60), rate = 1.1)[0],
            "Confirmed defect: faster lambda crosses ka; huge negative term is clamped to zero")
        val band = LabFit.bands(events, doubleArrayOf(46.0 / 60), emptyList(), CalibrationMode.RETROSPECTIVE).getValue(Curve.E2)
        assertEquals(0.0, band.p5[0]); assertTrue(band.p95[0] > 100 * pop)
    }

    @Test fun causalFitsDoNotUseFutureLabsAtExactGridPoints() {
        val events = listOf(event())
        val grid = doubleArrayOf(.5, 46.0 / 60, 1.0, 2.0, 3.0)
        val lab = LabResult("synthetic", 2.0, stableSl(2.0) * 1.5, LabUnit.PG_ML)
        val causal = LabFit.bands(events, grid, listOf(lab), CalibrationMode.CAUSAL, 40).getValue(Curve.E2)
        val pop = LabFit.bands(events, grid, emptyList(), CalibrationMode.CAUSAL, 40).getValue(Curve.E2)
        for (i in 0..2) {
            assertEquals(pop.center[i], causal.center[i], 1e-9)
            assertEquals(pop.p5[i], causal.p5[i], 1e-9); assertEquals(pop.p95[i], causal.p95[i], 1e-9)
        }
        val retro = LabFit.bands(events, grid, listOf(lab), CalibrationMode.RETROSPECTIVE, 40).getValue(Curve.E2)
        assertTrue(abs(retro.center[1] - pop.center[1]) > 1)
    }

    @Test fun characterizeExcludedIdCanStillBeIncludedWhenEveryLabIsFlagged() {
        val events = listOf(event())
        val labs = listOf(LabResult("far-tail", 100.0, 200.0, LabUnit.PG_ML))
        val fit = LabFit.fit(events, labs)
        assertTrue("far-tail" in fit.excludedLabIds)
        assertEquals(1, fit.postDoseObservationCount,
            "Confirmed bookkeeping defect: all-outlier guard keeps observation but reports its ID excluded")
    }

    @Test fun generateActualKotlinPopulationAndCalibrationAuditOutputs() {
        val cases = linkedMapOf(
            "single_2mg" to listOf(event()),
            "single_1mg" to listOf(event(dose = 1.0)),
            "single_4mg" to listOf(event(dose = 4.0)),
            "daily_2mg" to (0 until 30).map { event("q$it", -it * 24.0) },
            "twice_daily_2mg" to (0 until 60).map { event("b$it", -it * 12.0) },
            "daily_4mg" to (0 until 30).map { event("q4$it", -it * 24.0, 4.0) },
            "prior_2mg_6h" to listOf(event(), event("prior", -6.0)),
            "prior_oral_2mg_24h" to listOf(event(), event("oral", -24.0).copy(route = Route.ORAL)),
            "yaish_regular_q6h_0.5mg" to (0 until 240).map { event("y$it", -it * 6.0, .5) },
        )
        val fine = DoubleArray(4801) { it * .005 }
        val out = JSONObject().put("schema_version", 1).put("synthetic_only", true).put("engine_version_label", PkParams.version)
        val results = JSONArray()
        for ((name, events) in cases) {
            val y = curve(events, fine); val peak = y.indices.maxBy { y[it] }
            fun auc(end: Int) = (1..end).sumOf { (y[it - 1] + y[it]) * .0025 }
            val item = JSONObject().put("id", name).put("events", JSONArray(events.map { JSONObject().put("time_h", it.timeH).put("dose_mg", it.doseMG).put("route", it.route.code) }))
                .put("time_h", JSONArray(times.toList())).put("concentration_pg_ml", JSONArray(curve(events, times).toList()))
                .put("cmax_0_24_pg_ml", y[peak]).put("tmax_0_24_h", fine[peak]).put("auc0_8_pg_h_ml", auc(1600)).put("auc0_24_pg_h_ml", auc(4800))
                .put("pre_current_dose_pg_ml", y[0]).put("post_stop_48h_pg_ml", curve(events, doubleArrayOf(48.0))[0])
            results.put(item)
        }
        out.put("population_cases", results)
        out.put("rate_sensitivity", JSONArray(listOf(.5, .9, .999, 1.0, 1.001, 1.01, 1.1).map { rate ->
            JSONObject().put("rate_factor", rate).put("c_46min_pg_ml", curve(listOf(event()), doubleArrayOf(46.0 / 60), rate)[0])
        }))
        val bands = LabFit.bands(listOf(event()), doubleArrayOf(46.0 / 60), emptyList(), CalibrationMode.RETROSPECTIVE).getValue(Curve.E2)
        out.put("prior_band_46min", JSONObject().put("p5", bands.p5[0]).put("p95", bands.p95[0]).put("center", bands.center[0]))
        val fits = JSONArray()
        for ((name, ts) in listOf("one_lab" to listOf(1.0), "same_phase" to listOf(1.0, 1.0, 1.0), "different_times" to listOf(.5, 1.0, 3.0))) {
            val labs = ts.mapIndexed { i, t -> LabResult("synthetic$i", t, stableSl(t) * 1.5, LabUnit.PG_ML) }
            val fit = LabFit.fit(listOf(event()), labs)
            fits.put(JSONObject().put("id", name).put("amplitude", exp(fit.logAmplitude)).put("rate", exp(fit.logRate))
                .put("covariance", JSONArray(fit.cov.toList())).put("correlation", fit.cov[1] / sqrt(fit.cov[0] * fit.cov[3]))
                .put("excluded_ids", JSONArray(fit.excludedLabIds.toList())).put("observations", fit.postDoseObservationCount))
        }
        out.put("synthetic_fits", fits)
        val file = File("build/reports/pk-p0/engine-outputs.json"); file.parentFile.mkdirs(); file.writeText(out.toString(2) + "\n")
        assertTrue(file.length() > 1000)
    }
}
