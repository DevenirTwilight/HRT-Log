package net.plainnotes.app.conc

import net.plainnotes.app.conc.TotalE2Calibration.Reason
import net.plainnotes.app.conc.TotalE2Calibration.Use
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * SL-CAL-02: the Kotlin engine against the SL-CAL-01 research reference, number by number. The fixture
 * (`app/src/test/resources/sl-cal-02/parity.json`, made by `tools/pk-research/sl_cal/make_parity_fixture.py`) holds synthetic users only:
 * the curves at each lab time (and ±0.05 h), the labs, and what the reference's C_common/delta method and eligibility rules returned.
 */
@RunWith(RobolectricTestRunner::class) @Config(sdk = [35], application = android.app.Application::class)
class TotalE2CalibrationParityTest {
    private val doc: JSONObject = javaClass.getResourceAsStream("/sl-cal-02/parity.json")!!.bufferedReader().use { JSONObject(it.readText()) }
    private fun doubles(a: JSONArray) = DoubleArray(a.length()) { a.getDouble(it) }
    private fun relative(expected: Double, actual: Double, what: String, tolerance: Double = 1e-8) =
        assertEquals("$what: expected $expected, was $actual", 0.0, Math.abs(expected - actual) / Math.max(1.0, Math.abs(expected)), tolerance)

    private fun curves(c: JSONObject): TotalE2Calibration.Curves {
        val times = doubles(c.getJSONArray("curve_times"))
        val s = doubles(c.getJSONArray("sublingual")); val n = doubles(c.getJSONArray("other"))
        fun lookup(values: DoubleArray) = { t: Double -> values[times.indexOfFirst { it == t }.also { require(it >= 0) { "curve not tabulated at $t" } }] }
        return TotalE2Calibration.Curves(c.getDouble("background"), lookup(s), lookup(n))
    }

    @Test fun constantsEqualTheReferenceAndProduction() {
        val k = doc.getJSONObject("constants")
        assertEquals(k.getDouble("prior_sd"), TotalE2Calibration.PRIOR_SD, 0.0)
        assertEquals(k.getDouble("timing_sd_h"), TotalE2Calibration.TIMING_SD_H, 0.0)
        assertEquals(k.getDouble("peak_window_h"), TotalE2Calibration.PEAK_WINDOW_H, 0.0)
        assertEquals(k.getDouble("min_information_gain"), TotalE2Calibration.MIN_INFORMATION_GAIN, 0.0)
        assertEquals(k.getDouble("sigma_lab"), TotalE2Calibration.SIGMA_LAB, 1e-15)
        assertEquals("the production observation noise", net.plainnotes.app.pk.LabFit.SIGMA_LAB, TotalE2Calibration.SIGMA_LAB, 0.0)
        assertEquals("sqrt(ln(1 + 0.299^2)); the SL-CAL-01 report rounds it as 0.2937 by mistake", 0.29262, k.getDouble("sigma_lab"), 5e-6)
    }

    @Test fun everySyntheticCaseGivesTheReferencesNumbersAndDecision() {
        val cases = doc.getJSONArray("cases"); var calibrated = 0; var refused = 0
        for (i in 0 until cases.length()) {
            val c = cases.getJSONObject(i); val name = c.getString("name"); val e = c.getJSONObject("expected")
            val labs = c.getJSONArray("labs").let { a -> (0 until a.length()).map { a.getJSONObject(it) } }.map {
                // Every lab here has passed the history gate and has a clock time; the rules are tested on their own below.
                TotalE2Calibration.Lab(it.getLong("id"), it.getDouble("time"), it.getDouble("pg_ml"), true, true, it.getDouble("hours_since_sl"))
            }
            val outcome = TotalE2Calibration.calibrate(labs, curves(c))
            val reasons = e.getJSONArray("reasons").let { a -> (0 until a.length()).map { a.getString(it) } }
            when (e.getString("decision")) {
                "CALIBRATE" -> {
                    calibrated++
                    val got = outcome as? TotalE2Calibration.Calibrated ?: fail("$name: expected a calibration, got $outcome").let { return }
                    relative(e.getDouble("factor"), got.factor, "$name factor")
                    relative(e.getDouble("ln_sd"), got.lnSd, "$name ln sd")
                    relative(e.getDouble("information_gain"), got.informationGain, "$name information gain")
                    if (e.isNull("loo")) assertNull("$name leave-one-out", got.leaveOneOut) else {
                        val loo = e.getJSONObject("loo"); val g = requireNotNull(got.leaveOneOut) { "$name leave-one-out missing" }
                        relative(loo.getDouble("median_abs_ln"), g.medianAbsLn, "$name loo median"); relative(loo.getDouble("rms_ln"), g.rmsLn, "$name loo rms")
                    }
                }
                else -> {
                    refused++
                    val got = outcome as? TotalE2Calibration.Refused ?: fail("$name: expected a refusal, got $outcome").let { return }
                    assertEquals(name, reasons.map { Reason.valueOf(it) }, got.reasons)
                    relative(e.getDouble("information_gain"), requireNotNull(got.informationGain), "$name refused information gain")
                }
            }
        }
        assertTrue("the fixture exercises both outcomes (calibrated $calibrated, refused $refused)", calibrated > 20 && refused > 3)
    }

    @Test fun theEligibilityRulesAreTheReferencesRules() {
        val rules = doc.getJSONArray("lab_rules")
        for (i in 0 until rules.length()) {
            val r = rules.getJSONObject(i)
            val hours = if (r.isNull("hours")) null else r.getDouble("hours")
            val got = TotalE2Calibration.assess(TotalE2Calibration.Lab(1, 100.0, 80.0, r.getBoolean("history"), r.getBoolean("precise"),
                if (r.getBoolean("known")) hours else null))
            val expectedUse = Use.valueOf(r.getString("decision")); val expectedReasons = r.getJSONArray("reasons").let { a -> (0 until a.length()).map { a.getString(it) } }
            assertEquals("rule case $i $r", expectedUse, got.use)
            assertEquals("rule case $i $r", expectedReasons, got.reasons.map { it.name })
        }
        val sets = doc.getJSONArray("set_rules")
        for (i in 0 until sets.length()) {
            val s = sets.getJSONObject(i)
            val got = TotalE2Calibration.refusal(s.getInt("usable"), s.getDouble("gain"))
            val expected = s.getJSONArray("reasons").let { a -> (0 until a.length()).map { a.getString(it) } }
            assertEquals("set case $i $s", expected, listOfNotNull(got?.name))
        }
    }
}
