package net.plainnotes.app.pk

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.math.ln

/** The literature engine: agreement with the fitted models, route handling, and literature checks per route. */
class EngineTest {
    private fun within(label: String, value: Double, mean: Double, sd: Double) =
        assertTrue(abs(value - mean) <= sd, label + ": model %.2f, literature %.2f ± %.2f".format(value, mean, sd))
    private fun ev(id: String, route: Route, t: Double, dose: Double, ester: Ester, extras: DoseExtras = DoseExtras(), w: Double = 70.0) =
        DoseEvent(id, route, t, dose, ester, w, extras)
    private fun at(r: EngineResult, c: Curve, hour: Double) = Pk.interpolate(r.timeH, r.curves.getValue(c), hour)!!
    private fun mean(r: EngineResult, c: Curve, from: Double, to: Double): Double {
        val idx = r.timeH.indices.filter { r.timeH[it] in from..to }
        return idx.sumOf { r.curves.getValue(c)[it] } / idx.size
    }

    @Test fun matchesFittedModelForSingleDoses() {
        for ((route, ester, key) in listOf(Triple(Route.ORAL, Ester.EV, "EV_ORAL"), Triple(Route.ORAL, Ester.CPA, "CPA_ORAL"), Triple(Route.INJECTION, Ester.EV, "EV_IM"))) {
            val taus = doubleArrayOf(1.0, 6.0, 30.0, 100.0)
            val r = Engine.simulate(listOf(ev("a", route, 1000.0, 5.0, ester)), grid = DoubleArray(taus.size) { 1000.0 + taus[it] })!!
            val curve = if (ester == Ester.CPA) Curve.CPA else Curve.E2
            taus.forEachIndexed { i, tau ->
                val expected = PkParams.model(key).response(tau, 5.0, 70.0)
                assertEquals(expected, r.curves.getValue(curve)[i], expected * 1e-6 + 1e-9, "$key at $tau h")
            }
        }
    }

    @Test fun patchMatchesLabelsAndFallsAfterRemoval() {
        // Twice-weekly Vivelle-Dot (3.5 days), each new patch replacing the previous one.
        for ((rate, cavg, sd) in listOf(Triple(37.5, 34.0, 10.0), Triple(50.0, 57.0, 23.0), Triple(75.0, 72.0, 24.0), Triple(100.0, 89.0, 38.0))) {
            val events = (0 until 12).flatMap { k ->
                val t = k * 84.0
                listOfNotNull(ev("p$k", Route.PATCH_APPLY, t, 1.0, Ester.E2, DoseExtras(releaseRateUGPerDay = rate, patchInstanceId = "p$k")),
                    if (k < 11) ev("r$k", Route.PATCH_REMOVE, t + 84.0, 0.0, Ester.E2, DoseExtras(patchRemovalFor = "p$k")) else null)
            }
            val r = Engine.simulate(events)!!
            within("Vivelle-Dot Cavg at $rate µg/day", mean(r, Curve.E2, 10 * 84.0, 11 * 84.0), cavg, sd)
        }
        // One patch removed after 84 h: apparent half-life after removal 5.9–7.7 h (label).
        val one = Engine.simulate(listOf(ev("p", Route.PATCH_APPLY, 0.0, 1.0, Ester.E2, DoseExtras(releaseRateUGPerDay = 50.0, patchInstanceId = "p")),
            ev("r", Route.PATCH_REMOVE, 84.0, 0.0, Ester.E2, DoseExtras(patchRemovalFor = "p"))), 200.0)!!
        val th = ln(2.0) * 12 / ln(at(one, Curve.E2, 96.0) / at(one, Curve.E2, 108.0))
        assertTrue(th in 5.9..7.7, "half-life after removal %.2f h".format(th))
    }

    @Test fun gelsPerProduct() {
        fun daily(product: Int, dose: Double) = Engine.simulate((0 until 21).map { ev("g$it", Route.GEL, it * 24.0, dose, Ester.E2, DoseExtras(gelProductId = product.toDouble())) })!!
        // Divigel: an independent study not used in fitting (Sirviö 2026, day 14 Cavg).
        for ((dose, cavg, sd) in listOf(Triple(0.5, 12.4, 7.0), Triple(1.0, 29.7, 7.3), Triple(1.5, 55.0, 33.1)))
            within("Sirviö 2026 Divigel $dose mg/day Cavg", mean(daily(4, dose), Curve.E2, 13 * 24.0, 14 * 24.0), cavg, sd)
        assertEquals(setOf(CurveFlag.NO_PRODUCT_DATA), daily(2, 1.5).flags[Curve.E2], "Estreva has no product data")
        assertEquals(emptySet<CurveFlag>(), daily(1, 1.5).flags[Curve.E2])
        // EstroGel / Oestrogel decline more slowly than Divigel after stopping (36 h vs 10 h on the labels).
        val eg = daily(1, 1.5); val dv = daily(4, 1.5)
        val ratio = { r: EngineResult -> at(r, Curve.E2, 20 * 24.0 + 72) / at(r, Curve.E2, 20 * 24.0 + 24) }
        assertTrue(ratio(eg) > ratio(dv))
    }

    @Test fun intramuscularValeratePeaksAfterAboutTwoDays() {
        val r = Engine.simulate(listOf(ev("i", Route.INJECTION, 0.0, 10.0, Ester.EV)), 400.0)!!
        val idx = r.curves.getValue(Curve.E2).indices.maxByOrNull { r.curves.getValue(Curve.E2)[it] }!!
        assertEquals(48.0, r.timeH[idx], 4.0, "Tmax (Oriowo 1980: about 2 days)")
        assertEquals(505.7, r.curves.getValue(Curve.E2)[idx], 15.0, "Cmax 10 mg (Schug 2012 geometric mean)")
        assertNotNull(Engine.choose(ev("x", Route.INJECTION, 0.0, 5.0, Ester.EC)).let { it as? ModelChoice.None })
    }

    @Test fun sublingualCalibrationAndFlags() {
        val r = Engine.simulate(listOf(ev("s", Route.SUBLINGUAL, 0.0, 1.0, Ester.E2, DoseExtras(sublingualTier = 2.0))), 48.0)!!
        assertEquals(144.0, at(r, Curve.E2, 1.0), 3.0, "Doll 2022 Cmax at 1 h")
        assertEquals(setOf(CurveFlag.EXTRAPOLATED_AFTER_CALIBRATED_HOURS), r.flags[Curve.E2])
        val other = Engine.simulate(listOf(ev("s", Route.SUBLINGUAL, 0.0, 1.0, Ester.E2, DoseExtras(sublingualTier = 0.0))), 48.0)!!
        assertTrue(CurveFlag.EXTRAPOLATED_TIER in other.flags.getValue(Curve.E2))
        val evSl = Engine.simulate(listOf(ev("v", Route.SUBLINGUAL, 0.0, 2.0, Ester.EV)))!!
        assertEquals(Unsupported.SUBLINGUAL_EV, evSl.unsupported["v"]); assertTrue(evSl.curves.isEmpty())
    }

    @Test fun spironolactoneAndCanrenone() {
        val r = Engine.simulate((0 until 15).map { ev("s$it", Route.ORAL, it * 24.0, 100.0, Ester.SPI) })!!
        val day1 = { c: Curve -> r.timeH.indices.filter { r.timeH[it] in 0.0..24.0 }.maxOf { r.curves.getValue(c)[it] } }
        within("Gardiner 1989 parent Cmax day 1", day1(Curve.SPIRONOLACTONE), 72.0, 45.0)
        within("Gardiner 1989 canrenone Cmax day 1", day1(Curve.CANRENONE), 155.0, 43.0)
        val ss = r.timeH.indices.filter { r.timeH[it] in 14 * 24.0..15 * 24.0 }
        within("Gardiner 1989 canrenone Cmax day 15", ss.maxOf { r.curves.getValue(Curve.CANRENONE)[it] }, 181.0, 39.0)
        within("Gardiner 1989 parent Cmax day 15", ss.maxOf { r.curves.getValue(Curve.SPIRONOLACTONE)[it] }, 80.0, 20.0)
    }

    @Test fun progesteroneIsIllustrative() {
        val r = Engine.simulate((0 until 5).map { ev("p$it", Route.ORAL, it * 24.0, 200.0, Ester.P4) })!!
        assertEquals(setOf(CurveFlag.ILLUSTRATIVE), r.flags[Curve.PROGESTERONE])
        val idx = r.timeH.indices.filter { r.timeH[it] in 96.0..106.0 }
        val auc10 = (1 until idx.size).sumOf { (r.curves.getValue(Curve.PROGESTERONE)[idx[it]] + r.curves.getValue(Curve.PROGESTERONE)[idx[it - 1]]) / 2 * (r.timeH[idx[it]] - r.timeH[idx[it - 1]]) }
        within("Prometrium AUC0-10 200 mg", auc10, 101.2, 66.0)
    }

    @Test fun cpaScalesWithWeightAndBicalutamideHasNoCurve() {
        val light = Engine.simulate(listOf(ev("c", Route.ORAL, 0.0, 10.0, Ester.CPA, w = 50.0)))!!
        val heavy = Engine.simulate(listOf(ev("c", Route.ORAL, 0.0, 10.0, Ester.CPA, w = 100.0)))!!
        assertEquals(2.0, at(light, Curve.CPA, 5.0) / at(heavy, Curve.CPA, 5.0), 1e-6)
        assertEquals(Unsupported.BICALUTAMIDE, Engine.simulate(listOf(ev("b", Route.ORAL, 0.0, 50.0, Ester.BICA)))!!.unsupported["b"])
    }
}
