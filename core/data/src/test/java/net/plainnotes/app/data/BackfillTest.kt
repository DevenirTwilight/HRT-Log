package net.plainnotes.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import net.plainnotes.app.domain.RuleKind
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.*
import java.util.TimeZone

/** Batch backfill against the real schema guards, with synthetic data only. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[28])
class BackfillTest {
    private lateinit var db: NotesDatabase
    private lateinit var repo: NotesRepository
    private val zone = ZoneId.of("UTC")
    private val now = Instant.parse("2026-03-10T12:00:00Z")
    private val am = LocalTime.of(8, 0); private val pm = LocalTime.of(20, 0)

    @Before fun open() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, NotesDatabase::class.java).allowMainThreadQueries().addCallback(SchemaGuards).build(); db.openHelper.writableDatabase
        repo = NotesRepository(object : DatabaseAccess(context) { override fun get(space: Space) = db })
    }
    @After fun close() = db.close()

    private fun med(created: Instant) = runBlocking {
        repo.saveMedication(MedicationEntity(name = "Synthetic", molecule = "E2", route = "SUBLINGUAL", unit = "MG", dose_per_intake = 2.0, container_capacity = 100.0,
            soon_alert_minutes = 0, late_after_minutes = 60, site_rotation = false, notifications_on = false, active = true, sort_order = 0),
            "EV", RuleKind.EVERY_N_DAYS, 1, listOf(am, pm), emptySet(), created)
    }

    @Test fun fillsOpenSlotsAndAddsEarlierIntakesOnce() {
        val id = med(Instant.parse("2026-03-08T00:00:00Z"))
        val added = runBlocking { repo.backfill(id, LocalDate.parse("2026-03-01"), LocalDate.parse("2026-03-10"), listOf(am, pm), 2.0, now, zone) }
        assertEquals(19, added) // 1-9 March twice a day, plus 10 March 08:00; 20:00 is still in the future
        val records = runBlocking { repo.records() }.filter { it.taken_utc != null }
        assertEquals(19, records.size)
        assertTrue(records.all { it.actual_dose == 2.0 && it.status == "ON_TIME" && it.unallocated_supply_amount == null })
        // Since the medication exists its slots are completed, not duplicated by unscheduled intakes.
        assertEquals(5, records.count { it.slot_key != null }); assertEquals(14, records.count { it.slot_key == null })
        assertTrue(runBlocking { repo.records() }.none { it.status == "MISSED" })
        assertEquals(0, runBlocking { repo.backfill(id, LocalDate.parse("2026-03-01"), LocalDate.parse("2026-03-10"), listOf(am, pm), 2.0, now, zone) })
    }

    @Test fun skipsTimesNearAnExistingIntake() {
        val id = med(now)
        runBlocking { repo.unscheduled(id, Instant.parse("2026-03-05T08:30:00Z"), 2.0) }
        assertEquals(1, runBlocking { repo.backfill(id, LocalDate.parse("2026-03-05"), LocalDate.parse("2026-03-05"), listOf(am, pm), 2.0, now, zone) })
    }
}
