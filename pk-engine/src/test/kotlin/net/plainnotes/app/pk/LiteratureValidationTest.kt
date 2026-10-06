package net.plainnotes.app.pk

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

/**
 * Literature validation (docs/pk-model.md): model outputs must fall within the reported mean ± 1 SD,
 * or inside the reported range where only a range is given. Sources are the entries in pk-params.json.
 * Known deviations that do not pass are listed in docs/pk-model.md instead of loosening a test.
 */
class LiteratureValidationTest {
    private fun within(label: String, value: Double, mean: Double, sd: Double) =
        assertTrue(abs(value - mean) <= sd, label + ": model %.2f, literature %.2f ± %.2f".format(value, mean, sd))
    private fun inRange(label: String, value: Double, lo: Double, hi: Double) =
        assertTrue(value in lo..hi, label + ": model %.2f, literature %.2f–%.2f".format(value, lo, hi))

    /** Steady-state values over one dosing interval after many doses. */
    private fun steadyState(m: FittedModel, dose: Double, tau: Double, weight: Double = 70.0, n: Int = 60): DoubleArray {
        val t0 = (n - 1) * tau
        val f = { t: Double -> (0 until n).sumOf { k -> m.response(t0 + t - k * tau, dose, weight) } }
        val peak = Curves.peak(tau) { f(it) }.first
        val trough = minOf(f(0.0), f(tau))
        val auc = Curves.area(0.0, tau) { f(it) }
        return doubleArrayOf(peak, trough, auc / tau, auc)
    }

    @Test fun oralEstradiolValerate() {
        val m = PkParams.model("EV_ORAL")
        val (cmax, tmax) = Curves.peak(72.0) { m.response(it, 1.0, 70.0) }
        within("Zhang 2024 Cmax 1 mg", cmax, 20.47, 13.42)
        within("Zhang 2024 AUC0-inf 1 mg", Curves.area(0.0, 400.0) { m.response(it, 1.0, 70.0) }, 575.99, 215.47)
        within("Zhang 2024 t1/2", Curves.apparentHalfLife({ m.response(it, 1.0, 70.0) }, 48.0, 96.0), 14.48, 4.17)
        inRange("Tmax (Progynova RCP 4–6 h; Zhang 2024 median 10 h)", tmax, 4.0, 10.0)
        val ss = steadyState(m, 2.0, 24.0)
        within("Natazia Cmax,ss 2 mg/day", ss[0], 70.5, 25.9)
        within("Natazia AUC0-24,ss 2 mg/day", ss[3], 1323.0, 480.0)
    }

    @Test fun oralMicronizedEstradiol() {
        val m = PkParams.model("E2_ORAL")
        val (cmax, _) = Curves.peak(72.0) { m.response(it, 1.0, 70.0) }
        within("Activella Cmax 1 mg (geometric CV 36 %)", cmax, 26.8, 26.8 * 0.36)
        within("Activella AUC 1 mg (geometric CV 48 %)", Curves.area(0.0, 400.0) { m.response(it, 1.0, 70.0) }, 766.5, 766.5 * 0.48)
        inRange("Activella / Oromone t1/2 (14 h; 10–16 h)", Curves.apparentHalfLife({ m.response(it, 1.0, 70.0) }, 48.0, 96.0), 10.0, 16.0)
        val ss = steadyState(m, 2.0, 24.0)
        within("Oromone Cmax,ss 2 mg/day", ss[0], 89.0, 16.0)
        within("Oromone Cmin,ss 2 mg/day", ss[1], 35.0, 13.4)
        within("Oromone Cavg,ss 2 mg/day", ss[2], 62.9, 15.6)
        within("Oromone AUC0-24,ss 2 mg/day", ss[3], 1486.0, 374.0)
    }

    @Test fun cyproteroneAcetate() {
        val m = PkParams.model("CPA_ORAL")
        val (c2, t2) = Curves.peak(24.0) { m.response(it, 2.0, 70.0) }
        within("Kuhnz 1993 Cmax 2 mg", c2, 15.2, 6.6)
        within("Dusterberg 1979 Tmax 2 mg", t2, 1.6, 0.6)
        within("Kuhnz 1993 single-dose terminal t1/2", Curves.apparentHalfLife({ m.response(it, 2.0, 70.0) }, 24.0, 168.0), 54.0, 26.0)
        val (c100, t100) = Curves.peak(24.0) { m.response(it, 100.0, 70.0) }
        within("Huber 1988 Cmax 100 mg", c100, 255.0, 110.0)
        inRange("Huber 1988 Tmax 100 mg (2–3 h)", t100, 2.0, 3.0)
        // AUC per mg from F 0.88 and clearance 3.6 mL/min/kg (Kuhnz 1993) at 70 kg, within its SD (0.9).
        val auc = Curves.area(0.0, 2000.0, 0.1) { m.response(it, 1.0, 70.0) }
        inRange("AUC per mg from clearance 3.6 ± 0.9 mL/min/kg", auc, 0.88 / (4.5 * 0.06 * 70) * 1000, 0.88 / (2.7 * 0.06 * 70) * 1000)
        // Daily 2 mg: about 2-fold accumulation (Kuhnz 1993 "~2-fold"; Dianette SmPC 2.2–2.4).
        val single24 = Curves.area(0.0, 24.0) { m.response(it, 2.0, 70.0) }
        inRange("accumulation of daily dosing", steadyState(m, 2.0, 24.0)[3] / single24, 1.8, 2.4)
        // Longer half-life after repeated dosing (Kuhnz 1993: 78.6 h after three cycles).
        val single = Curves.apparentHalfLife({ m.response(it, 2.0, 70.0) }, 24.0, 168.0)
        val multi = Curves.apparentHalfLife({ t -> (0 until 21).sumOf { k -> m.response(t + k * 24.0, 2.0, 70.0) } }, 24.0, 168.0)
        assertTrue(multi > single + 2, "half-life after repeated doses (%.1f h) should exceed single dose (%.1f h)".format(multi, single))
        inRange("slowest half-life (Kuhnz 1993: 78.6 h)", m.terminalHalfLifeH, 70.0, 90.0)
    }

    @Test fun numericallyStable() {
        for (m in PkParams.models.values) {
            assertTrue(m.response(1.0, 1e-6, 70.0).isFinite())
            assertTrue(m.response(1e6, 1.0, 70.0).let { it.isFinite() && it >= 0 })
            // Two years of twice-daily doses with every fifth missed: finite and bounded by the no-miss steady state.
            val doses = (0 until 1460).filter { it % 5 != 0 }.map { it * 12.0 }
            val c = doses.sumOf { m.response(17520.0 - it, 1.0, 70.0) }
            val full = (0 until 1460).sumOf { m.response(17520.0 - it * 12.0, 1.0, 70.0) }
            assertTrue(c.isFinite() && c >= 0 && c <= full + 1e-9)
        }
    }
}
