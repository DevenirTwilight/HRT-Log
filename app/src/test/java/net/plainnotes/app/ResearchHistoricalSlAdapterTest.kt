package net.plainnotes.app

import net.plainnotes.app.conc.ConcentrationCalculator
import net.plainnotes.app.conc.experimental.ResearchHistoricalSlAdapter
import net.plainnotes.app.data.MedicationEntity
import net.plainnotes.app.data.MedicationSnapshot
import net.plainnotes.app.data.ProfileEntity
import net.plainnotes.app.data.RecordEntity
import net.plainnotes.app.pk.Ester
import net.plainnotes.app.pk.Route
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

/** Unlike production calculator tests, these exercise only the new read-only research adapter. */
class ResearchHistoricalSlAdapterTest {
    private val now = Instant.parse("2026-03-10T12:00:00Z")
    private val nowH = ConcentrationCalculator.hours(now)
    private val sl = MedicationEntity(1, "SL E2", "E2", "SUBLINGUAL", "MG", 0.5, 30.0, null, 15, 120, false, null, true, true, 0)
    private val slProfile = ProfileEntity(1, "E2", "sublingual", sl_tier = 2)
    private fun record(
        id: Long, hoursAgo: Long, dose: Double? = 0.5, medication: MedicationEntity = sl,
        profile: ProfileEntity? = slProfile, status: String = "ON_TIME", origin: String = "APP",
        snapshot: String = MedicationSnapshot.encode(medication,profile),
    ) = RecordEntity(id, medication.id,
        taken_utc = now.minusSeconds(hoursAgo * 3600).toEpochMilli(),
        taken_zone = "Europe/Paris", actual_dose = dose, status = status, origin = origin,
        revision = 1, config_snapshot = snapshot,
    )

    @Test fun keepsOnlyGenuineSnapshotQualifiedHistoricSublingualE2() {
        val oral = sl.copy(id=2, name="oral", route="ORAL")
        val oralProfile = ProfileEntity(2, "E2", "oral")
        val wrongEster = sl.copy(id=3, name="EV", route="SUBLINGUAL")
        val wrongProfile = ProfileEntity(3, "EV", "sublingual", sl_tier=2)
        val rows = listOf(
            record(1,24,1.0), record(2,6,0.5),
            record(3,4,1.0,oral,oralProfile),
            record(4,5,1.0,wrongEster,wrongProfile),
            record(5,3,0.5,status="SKIPPED"),
            record(6,2,null),
            record(7,1,0.5,snapshot="{}"),
        )
        val events = ResearchHistoricalSlAdapter.verifiedEvents(rows,emptyMap(),nowH)
        assertEquals(2, events.size)
        assertEquals(listOf("r1","r2"), events.map { it.id })
        assertEquals(listOf(1.0,0.5),events.map { it.doseMG })
        assertTrue(events.all { it.ester==Ester.E2 && it.route==Route.SUBLINGUAL && it.timeH<=nowH })
        assertTrue(events.all { it.extras.sublingualTier==2.0 })
    }

    @Test fun excludesFutureAndOutsideHistoricalWindowAndWrongTier() {
        val noTier = ProfileEntity(1,"E2","sublingual")
        val rows = listOf(
            record(1,-1), record(2,ConcentrationCalculator.HISTORY_DAYS*24+1),
            record(3,2,profile=noTier), record(4,4),
        )
        val events = ResearchHistoricalSlAdapter.verifiedEvents(rows,emptyMap(),nowH)
        assertEquals(listOf("r4"),events.map { it.id })
    }

    @Test fun hasNoEffectsOnImmutableInputAndRejectsBadAsOfTime() {
        val rows = listOf(record(1,12))
        val before = rows.toList()
        assertEquals(1,ResearchHistoricalSlAdapter.verifiedEvents(rows,emptyMap(),nowH).size)
        assertEquals(before,rows)
        assertThrows(IllegalArgumentException::class.java) {
            ResearchHistoricalSlAdapter.verifiedEvents(rows,emptyMap(),Double.NaN)
        }
    }
}
