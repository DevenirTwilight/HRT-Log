package net.plainnotes.app

import net.plainnotes.app.conc.ConcentrationCalculator
import net.plainnotes.app.conc.experimental.ResearchHistoricalSlAdapter
import net.plainnotes.app.conc.experimental.SlExclusion
import net.plainnotes.app.data.MedicationEntity
import net.plainnotes.app.data.MedicationSnapshot
import net.plainnotes.app.data.ProfileEntity
import net.plainnotes.app.data.RecordEntity
import net.plainnotes.app.experimental.ComparisonUiState
import net.plainnotes.app.experimental.ComparisonWindow
import net.plainnotes.app.experimental.comparisonUiState
import net.plainnotes.app.pk.experimental.ConcentrationModelComparison
import net.plainnotes.app.pk.experimental.ExperimentalSlModelView
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

/** Synthetic records only. The experimental page reads history; it must never reinterpret or change it. */
class ExperimentalPkHistoryTest {
    private val now = Instant.parse("2026-03-10T12:00:00Z")
    private val nowH = ConcentrationCalculator.hours(now)
    private val sl = MedicationEntity(1, "SL E2", "E2", "SUBLINGUAL", "MG", 1.0, 30.0, null, 15, 120, false, null, true, true, 0)
    private val slProfile = ProfileEntity(1, "E2", "sublingual", sl_tier = 2)
    private val oral = sl.copy(id = 2, name = "oral E2", route = "ORAL")
    private val oralProfile = ProfileEntity(2, "E2", "oral")
    private val slEv = sl.copy(id = 3, name = "SL EV")
    private val slEvProfile = ProfileEntity(3, "EV", "sublingual", sl_tier = 2)
    private val gel = sl.copy(id = 4, name = "gel", route = "GEL")
    private val gelProfile = ProfileEntity(4, "E2", "gel", gel_product_id = 1)
    private val first = ExperimentalSlModelView.candidates.first().id
    private fun experimentalPkState(rows: List<RecordEntity>, rules: Map<Long, String>, at: java.time.Instant, id: String) =
        comparisonUiState(rows, rules, at, ComparisonWindow.H24, null, id)

    private fun record(
        id: Long, hoursAgo: Double, dose: Double? = 1.0, med: MedicationEntity = sl, profile: ProfileEntity? = slProfile,
        status: String = "ON_TIME", origin: String = "APP", deleted: Long? = null, rule: Long? = null,
        snapshot: String = MedicationSnapshot.encode(med, profile),
    ) = RecordEntity(id, med.id, rule_version_id = rule, taken_utc = now.toEpochMilli() - (hoursAgo * 3_600_000).toLong(), taken_zone = "Europe/Paris",
        actual_dose = dose, status = status, origin = origin, revision = 1, deleted_at_utc = deleted, config_snapshot = snapshot)

    @Test fun everyExclusionIsCountedUnderItsFirstFailingGate() {
        val rows = listOf(
            record(1, 2.0), record(2, 6.0, 0.5, status = "LATE"),
            record(3, 3.0, med = oral, profile = oralProfile), record(4, 4.0, med = slEv, profile = slEvProfile),
            record(5, 5.0, med = gel, profile = gelProfile),
            record(6, 1.0, status = "SKIPPED"), record(7, 1.0, status = "MISSED", origin = "AUTO_MISSED"),
            record(8, 1.0, deleted = now.toEpochMilli()), record(9, -2.0),
            record(10, ConcentrationCalculator.HISTORY_DAYS * 24.0 + 1), record(11, 1.0, dose = null),
            record(12, 1.0, dose = Double.NaN), record(13, 1.0, dose = -1.0), record(14, 1.0, dose = Double.POSITIVE_INFINITY),
            record(15, 1.0, snapshot = "{}"), record(16, 1.0, profile = ProfileEntity(1, "E2", "sublingual")),
        )
        val audit = ResearchHistoricalSlAdapter.audit(rows, emptyMap(), nowH)
        assertEquals(listOf("r2", "r1"), audit.events.map { it.id })
        assertEquals(mapOf(
            SlExclusion.NOT_TAKEN to 2, SlExclusion.DELETED to 1, SlExclusion.FUTURE to 1, SlExclusion.OUTSIDE_WINDOW to 1,
            SlExclusion.INVALID_DOSE to 4, SlExclusion.NO_SNAPSHOT to 1, SlExclusion.NOT_SUBLINGUAL_E2 to 3, SlExclusion.INCOMPLETE_CONTEXT to 1,
        ), audit.excluded)
        assertEquals(audit.events, ResearchHistoricalSlAdapter.verifiedEvents(rows, emptyMap(), nowH))
    }

    @Test fun frozenSnapshotWinsOverLaterSettings() {
        // The record was taken with tier 2; a later rule version says tier 4 and the medication is now oral. Neither may rewrite it.
        val laterRule = mapOf(9L to MedicationSnapshot.encode(sl.copy(route = "ORAL"), ProfileEntity(1, "E2", "oral")))
        val r = record(1, 3.0, rule = 9L)
        val e = ResearchHistoricalSlAdapter.verifiedEvents(listOf(r), laterRule, nowH).single()
        assertEquals(2.0, e.extras.sublingualTier!!, 0.0)
        // Only a missing fact is filled, and only from the immutable rule version of that dose.
        val incomplete = record(2, 3.0, profile = ProfileEntity(1, "E2", "sublingual"), rule = 5L)
        val ruleWithTier = mapOf(5L to MedicationSnapshot.encode(sl, ProfileEntity(1, "E2", "sublingual", sl_tier = 3)))
        assertEquals(3.0, ResearchHistoricalSlAdapter.verifiedEvents(listOf(incomplete), ruleWithTier, nowH).single().extras.sublingualTier!!, 0.0)
        // Imported history is never completed from app rules.
        assertTrue(ResearchHistoricalSlAdapter.verifiedEvents(listOf(incomplete.copy(origin = "IMPORT_HT")), ruleWithTier, nowH).isEmpty())
    }

    @Test fun changingCurrentSettingsDoesNotChangeOldResults() {
        val rows = listOf(record(1, 30.0), record(2, 20.0, 0.5), record(3, 3.0))
        val before = experimentalPkState(rows, emptyMap(), now, first) as ComparisonUiState.Ready
        // The page has no access to current medication settings at all; a later rule version for the same medication is ignored.
        val edited = mapOf(1L to MedicationSnapshot.encode(sl.copy(route = "ORAL", dose_per_intake = 4.0), ProfileEntity(1, "E2", "oral")))
        val after = experimentalPkState(rows.map { it.copy(rule_version_id = 1L) }, edited, now, first) as ComparisonUiState.Ready
        assertArrayEquals(before.snapshot.result.m2, after.snapshot.result.m2, 0.0)
        assertArrayEquals(before.snapshot.result.legacy, after.snapshot.result.legacy, 0.0)
    }

    @Test fun readingNeverMutatesRecordsOrSnapshots() {
        val rows = listOf(record(1, 30.0), record(2, 3.0, med = oral, profile = oralProfile))
        val copy = rows.map { it.copy() }
        val rules = mapOf(1L to MedicationSnapshot.encode(sl, slProfile))
        val rulesCopy = rules.toMap()
        experimentalPkState(rows, rules, now, first)
        assertEquals(copy, rows)
        assertEquals(rulesCopy, rules)
    }

    @Test fun pageStateFromRecordsMatchesTheEngineViewAndHandlesEmptyHistory() {
        val rows = listOf(record(1, 10.0), record(2, 4.0, 0.5))
        val ready = experimentalPkState(rows, emptyMap(), now, first) as ComparisonUiState.Ready
        val events = ResearchHistoricalSlAdapter.verifiedEvents(rows, emptyMap(), nowH)
        val direct = ConcentrationModelComparison.compute(events, events.last().timeH, events.last().timeH + 24, events.last().timeH, first)!!
        assertArrayEquals(direct.m2, ready.snapshot.result.m2, 0.0)
        assertArrayEquals(direct.legacy, ready.snapshot.result.legacy, 0.0)
        assertEquals(2, ready.snapshot.result.doses.size)
        val onlyOther = listOf(record(3, 2.0, med = oral, profile = oralProfile), record(4, 2.0, med = slEv, profile = slEvProfile))
        val empty = experimentalPkState(onlyOther, emptyMap(), now, first) as ComparisonUiState.Empty
        assertEquals(mapOf(SlExclusion.NOT_SUBLINGUAL_E2 to 2), empty.audit.excluded)
        assertTrue(experimentalPkState(emptyList(), emptyMap(), now, first) is ComparisonUiState.Empty)
        // A single old dose is still a valid origin: the window starts at that dose, nothing is invented after it.
        val old = experimentalPkState(listOf(record(5, 40.0 * 24)), emptyMap(), now, first) as ComparisonUiState.Ready
        assertEquals(1, old.snapshot.result.doses.size)
        assertEquals(old.snapshot.result.doses.single().first, old.snapshot.result.originHour, 0.0)
        // An unknown candidate id cannot crash the page.
        assertTrue(experimentalPkState(rows, emptyMap(), now, "unknown") is ComparisonUiState.Failed)
    }

    @Test fun officialConcentrationResultIsUntouchedByTheExperimentalPage() {
        val rows = listOf(record(1, 30.0), record(2, 20.0, 0.5), record(3, 3.0), record(4, 5.0, 2.0, med = oral, profile = oralProfile))
        fun official() = ConcentrationCalculator.compute(listOf(sl, oral), mapOf(1L to slProfile, 2L to oralProfile), rows, emptyList(), emptyList(), 70.0, now,
            historyRead = net.plainnotes.app.conc.HistoryRead(true, nowH))
        val before = official()
        ExperimentalSlModelView.candidates.forEach { experimentalPkState(rows, emptyMap(), now, it.id) }
        val after = official()
        assertArrayEquals(before.timeH, after.timeH, 0.0)
        assertArrayEquals(before.e2, after.e2, 0.0)
        assertEquals(before.currentPgMl, after.currentPgMl)
        assertEquals(before.usedDoses, after.usedDoses)
        assertEquals(4, before.usedDoses)
    }
}
