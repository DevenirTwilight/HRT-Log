package net.plainnotes.app

import net.plainnotes.app.conc.experimental.SlAdapterAudit
import net.plainnotes.app.experimental.ConcentrationPreview
import net.plainnotes.app.pk.DoseEvent
import net.plainnotes.app.pk.DoseExtras
import net.plainnotes.app.pk.Ester
import net.plainnotes.app.pk.Route
import net.plainnotes.app.pk.experimental.ResearchModel
import net.plainnotes.app.ui.m2ChartLabs
import net.plainnotes.app.ui.m2ChartNowH
import org.junit.Assert.*
import org.junit.Test

/** D-1 (REQUIREMENTS §67 item 4): pure display rules of the M2 conditional literature scenario. */
class M2ScenarioDisplayTest {
    private val now = 500_000.0
    /** Fixed synthetic doses (absolute hours); only the snapshot time varies. */
    private fun preview(asOf: Double) = ConcentrationPreview.compute(SlAdapterAudit(listOf(-30.0, -3.0).map {
        DoseEvent("synthetic$it", Route.SUBLINGUAL, now + it, 1.0, Ester.E2, 70.0, DoseExtras(sublingualTier = 2.0)) }, emptyMap()), ResearchModel.M2, asOf)

    @Test fun scenarioCarriesItsOwnTimeBaseAndTheChartUsesIt() {
        val p = preview(now)
        assertEquals(now, p.asOfH!!, 0.0)
        assertEquals(p.valueAt(now)!!, p.currentPgMl!!, 0.0)
        // A Legacy result refreshed on another clock must not move the M2 "now" line.
        assertEquals(now, m2ChartNowH(p, now + 0.5), 0.0)
        assertEquals(now + 0.5, m2ChartNowH(null, now + 0.5), 0.0)
        assertNull(ConcentrationPreview.pending(ResearchModel.M2).asOfH)
    }

    @Test fun measuredLabsAreNeverDrawnOnTheScenarioCurve() {
        val labs = listOf(now - 2 to 150.0, now - 50 to 9000.0)
        assertEquals(emptyList<Pair<Double, Double>>(), m2ChartLabs(preview(now), labs, 1.0))
        assertEquals(labs.map { it.first to it.second * 3.671 }, m2ChartLabs(null, labs, 3.671))
    }

    @Test fun scenarioValuesDoNotDependOnLabsOrTheRefreshTime() {
        // Same history, later snapshot: the curve at a fixed past hour is identical; only the time base moves.
        val a = preview(now); val b = preview(now + 0.05)
        for (h in listOf(now - 29.0, now - 10.0, now - 2.5)) assertEquals(a.valueAt(h)!!, b.valueAt(h)!!, 0.0)
        assertNotEquals(a.asOfH, b.asOfH)
    }
}
