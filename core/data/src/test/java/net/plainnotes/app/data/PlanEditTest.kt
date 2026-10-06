package net.plainnotes.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import net.plainnotes.app.domain.RuleKind
import net.plainnotes.app.domain.SlotState
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.*
import java.util.TimeZone

/** Editing a medication: metadata keeps the plan, package sizes follow on request, a dose done early stays done. Synthetic data only. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[28])
class PlanEditTest {
    private lateinit var db: NotesDatabase
    private lateinit var repo: NotesRepository
    private val zone = ZoneId.of("UTC")
    private val day = LocalDate.parse("2026-03-10")
    private fun at(h: Int, m: Int) = day.atTime(h, m).atZone(zone).toInstant()
    private val times = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0))
    private val base = MedicationEntity(name = "Synthetic", molecule = "E2", route = "SUBLINGUAL", unit = "MG", dose_per_intake = 2.0, container_capacity = 58.0,
        soon_alert_minutes = 15, late_after_minutes = 60, site_rotation = false, notifications_on = false, active = true, sort_order = 0)

    @Before fun open() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, NotesDatabase::class.java).allowMainThreadQueries().addCallback(SchemaGuards).build(); db.openHelper.writableDatabase
        repo = NotesRepository(object : DatabaseAccess(context) { override fun get(space: Space) = db })
    }
    @After fun close() = db.close()

    private fun evening(now: Instant) = runBlocking { repo.calendar(now, zone, day) }.filter { it.slot.at == at(20, 0) }

    @Test fun earlyDoseStaysDoneAcrossMetadataAndPlanEdits() = runBlocking {
        val id = repo.saveMedication(base, "E2", RuleKind.EVERY_N_DAYS, 1, times, emptySet(), at(16, 32))
        repo.addContainers(id, 58.0, 2, true)
        repo.complete(evening(at(17, 5)).single().slot, at(17, 5), 2.0)

        // Only the package size changes: same plan version, packages follow, the evening dose stays done.
        repo.saveMedication(base.copy(id = id, container_capacity = 56.0), "E2", RuleKind.EVERY_N_DAYS, 1, times, emptySet(), at(17, 33), resizeContainers = true)
        assertEquals(1, repo.rules().size)
        val boxes = repo.containers()
        assertTrue(boxes.all { it.capacity == 56.0 }); assertEquals(2.0, boxes.sumOf { it.used_amount }, 1e-9)
        assertEquals(listOf(SlotState.ON_TIME), evening(at(17, 34)).map { it.state })

        // A real plan change starts a new version; the dose taken early is still the only evening entry.
        repo.saveMedication(base.copy(id = id, container_capacity = 56.0, late_after_minutes = 90), "E2", RuleKind.EVERY_N_DAYS, 1, times, emptySet(), at(17, 36))
        assertEquals(2, repo.rules().size)
        assertEquals(listOf(SlotState.ON_TIME), evening(at(17, 37)).map { it.state })
        assertEquals(listOf(SlotState.ON_TIME), evening(at(21, 0)).map { it.state })
    }

    @Test fun packageSizesStayWhenNotRequestedOrWhenMoreIsUsed() = runBlocking {
        val id = repo.saveMedication(base, "E2", RuleKind.EVERY_N_DAYS, 1, times, emptySet(), at(9, 0))
        repo.addContainers(id, 58.0, 1, true)
        repo.setRemaining(repo.containers().single().id, 8.0)   // 50 used
        repo.saveMedication(base.copy(id = id, container_capacity = 56.0), "E2", RuleKind.EVERY_N_DAYS, 1, times, emptySet(), at(9, 1))
        assertEquals(58.0, repo.containers().single().capacity, 0.0)
        repo.saveMedication(base.copy(id = id, container_capacity = 40.0), "E2", RuleKind.EVERY_N_DAYS, 1, times, emptySet(), at(9, 2), resizeContainers = true)
        assertEquals("a package that used more than the new size is left as it is", 58.0, repo.containers().single().capacity, 0.0)
        assertEquals(1, repo.rules().size)
    }
}
