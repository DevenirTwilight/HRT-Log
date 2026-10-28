package net.plainnotes.app

import net.plainnotes.app.conc.experimental.SlAdapterAudit
import net.plainnotes.app.experimental.ConcentrationPreview
import net.plainnotes.app.pk.DoseEvent
import net.plainnotes.app.pk.DoseExtras
import net.plainnotes.app.pk.Ester
import net.plainnotes.app.pk.Route
import net.plainnotes.app.pk.experimental.ResearchModel
import net.plainnotes.app.ui.E2Total
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

/**
 * D-1 (REQUIREMENTS §67 item 4) display rules of the M2 snapshot, migrated by UI-12 (REQUIREMENTS §162):
 * - kept: the snapshot carries its own time base and the chart uses it (now through [E2Total.Curve.nowH]); values never depend on labs or the refresh time;
 * - superseded: "measured labs are never drawn on the scenario curve" — every E2 lab is now a dot on the total (see [labsAreDotsOnTheTotal]).
 */
class M2ScenarioDisplayTest {
    private val now = 500_000.0
    /** Fixed synthetic doses (absolute hours); only the snapshot time varies. */
    private fun preview(asOf: Double) = ConcentrationPreview.compute(SlAdapterAudit(listOf(-30.0, -3.0).map {
        DoseEvent("synthetic$it", Route.SUBLINGUAL, now + it, 1.0, Ester.E2, 70.0, DoseExtras(sublingualTier = 2.0)) }, emptyMap()), ResearchModel.M2, asOf)

    @Test fun scenarioCarriesItsOwnTimeBaseAndTheTotalUsesIt() {
        val p = preview(now)
        assertEquals(now, p.asOfH!!, 0.0)
        assertEquals(p.valueAt(now)!!, p.currentPgMl!!, 0.0)
        assertEquals(2, p.doseCount)
        // A compatibility result refreshed on another clock must not move the "now" line: the total takes the M2 snapshot's time base.
        val total = E2Total.curve(p, null, null)!!
        assertEquals(now, total.nowH, 0.0)
        assertEquals(p.currentPgMl, total.nowValue)
        assertNull(ConcentrationPreview.pending(ResearchModel.M2).asOfH)
        assertNull("a pending snapshot is no curve", E2Total.curve(ConcentrationPreview.pending(ResearchModel.M2), null, null))
    }

    @Test fun labsAreDotsOnTheTotal() {
        // UI-12 supersedes D-1 here: all E2 labs are drawn on the total, in the displayed unit, unfiltered.
        val labs = listOf(now - 2 to 150.0, now - 50 to 9000.0)
        assertEquals(labs, E2Total.labPoints(labs, 1.0))
        assertEquals(labs.map { it.first to it.second * 3.671 }, E2Total.labPoints(labs, 3.671))
        val data = E2Total.chartData(E2Total.curve(preview(now), null, null)!!, labs, 1.0, emptyList(), 3, "pg/mL", "12:00", null)
        assertEquals(labs, data.points)
    }

    @Test fun onlyDosesUpToNowAreMarkedAndTheRangeSetsTheLineWidth() {
        val total = E2Total.curve(preview(now), null, null)!!
        val taken = listOf(Instant.ofEpochMilli(((now - 30) * 3_600_000).toLong()), Instant.ofEpochMilli(((now - 3) * 3_600_000).toLong()),
            Instant.ofEpochMilli(((now + 5) * 3_600_000).toLong()))
        val three = E2Total.chartData(total, emptyList(), 1.0, taken, 3, "pg/mL", "12:00", null)
        assertEquals(2, three.marks.size)
        assertTrue(three.marks.all { it <= now })
        assertEquals(2.6f, three.lineWidthDp)
        assertEquals(2f, E2Total.chartData(total, emptyList(), 1.0, taken, 7, "pg/mL", "12:00", null).lineWidthDp)
        assertTrue(three.axisDays)
        assertEquals("no bands on the page", null to null, three.band68 to three.band95)
    }

    @Test fun scenarioValuesDoNotDependOnLabsOrTheRefreshTime() {
        // Same history, later snapshot: the curve at a fixed past hour is identical; only the time base moves.
        val a = preview(now); val b = preview(now + 0.05)
        for (h in listOf(now - 29.0, now - 10.0, now - 2.5)) assertEquals(a.valueAt(h)!!, b.valueAt(h)!!, 0.0)
        assertNotEquals(a.asOfH, b.asOfH)
    }
}
