package net.plainnotes.app.conc

import net.plainnotes.app.conc.formulations.TestosteroneHistory
import net.plainnotes.app.data.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk = [29], application = android.app.Application::class)
class TestosteroneHistoryTest {
    private val m = MedicationEntity(1, "Not used for matching", "T", "T_ANDROGEL_50", "MG", 50.0, 1000.0,
        site_rotation = false, notifications_on = false, active = true, sort_order = 0)
    private val record = RecordEntity(1, 1, taken_utc = 3_600_000, taken_zone = "UTC", actual_dose = 50.0,
        status = "ON_TIME", origin = "APP", revision = 1, config_snapshot = MedicationSnapshot.encode(m, null))
    @Test fun frozenActualFactsOnlyNoPlanOrMutableNameMatching() {
        val unknown = record.copy(id = 2, config_snapshot = MedicationSnapshot.encode(m.copy(name = "ANDROGEL 50", route = null), null))
        val noDose = record.copy(id = 3, actual_dose = null, planned_dose = 50.0)
        val skipped = record.copy(id = 4, status = "SKIPPED")
        val future = record.copy(id = 5, taken_utc = 4_000_000_000)
        val r = TestosteroneHistory.read(listOf(unknown, noDose, skipped, future, record), emptyMap(), 100.0)
        assertEquals(1, r.events.getValue("T_ANDROGEL_50").size)
        assertEquals(1, r.unsupported)
        assertEquals(2, r.missing)
    }
    @Test fun routeAndUnitsDoNotBorrowE2OrAnotherTestosteroneProduct() {
        val wrongProduct = record.copy(config_snapshot = MedicationSnapshot.encode(m.copy(route = "T_ANDROGEL_162"), null))
        val volume = record.copy(id = 2, config_snapshot = MedicationSnapshot.encode(m.copy(unit = "ML"), null))
        val e2 = record.copy(id = 3, config_snapshot = MedicationSnapshot.encode(m.copy(molecule = "E2"), null))
        val r = TestosteroneHistory.read(listOf(wrongProduct, volume, e2), emptyMap(), 100.0)
        assertTrue(r.events.isEmpty()); assertEquals(1, r.unsupported); assertEquals(1, r.missing)
    }
    @Test fun deletedAndOmittedEventsContributeNothingAndSnapshotRoundTrips() {
        val decoded = MedicationSnapshot.decode(record.config_snapshot, 1)!!
        assertEquals(m.route, decoded.route)
        val r = TestosteroneHistory.read(listOf(record.copy(deleted_at_utc = 100), record.copy(status = "MISSED")), emptyMap(), 100.0)
        assertTrue(r.events.isEmpty())
    }
}
