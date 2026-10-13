package net.plainnotes.app.data

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * B4 "check stock" and "undo a package added by mistake" against the real schema guards and append-only ledger
 * (synthetic data). Stock is kept in the medication's unit (mg here): one 2 mg tablet left is 2.0, never 1.0.
 */
@RunWith(RobolectricTestRunner::class) @Config(sdk = [28])
class StockCorrectionTest {
    private lateinit var db: NotesDatabase
    private lateinit var repo: NotesRepository
    private val dao get() = db.dao()
    @Before fun open() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, NotesDatabase::class.java).allowMainThreadQueries().addCallback(SchemaGuards).build(); db.openHelper.writableDatabase
        repo = NotesRepository(object : DatabaseAccess(context) { override fun get(space: Space) = db })
    }
    @After fun close() = db.close()
    private fun <T> tx(block: suspend () -> T): T = runBlocking { db.withTransaction { block() } }
    private fun med() = runBlocking { dao.insertMedication(MedicationEntity(name = "Synthetic", molecule = "E2", route = "ORAL", unit = "MG", dose_per_intake = 2.0,
        container_capacity = 28.0, soon_alert_minutes = 0, late_after_minutes = 60, site_rotation = false, notifications_on = true, active = true, sort_order = 0)) }
    private fun box(m: Long, state: String, used: Double = 0.0, opened: String? = "2026-01-01") = runBlocking {
        dao.insertContainer(ContainerEntity(medication_id = m, capacity = 28.0, initial_used_amount = used, used_amount = used, opened_on = if (state == "SEALED") null else opened, state = state)) }
    private fun taken(m: Long, dose: Double) = runBlocking { dao.recordById(dao.record(RecordEntity(medication_id = m, taken_utc = 0, taken_zone = "UTC",
        actual_dose = dose, status = "ON_TIME", origin = "APP", revision = 1, config_snapshot = "{}")))!! }
    private fun c(id: Long) = runBlocking { dao.container(id) }
    private fun all() = runBlocking { dao.containers() }
    private fun ledger(id: Long) = runBlocking { dao.supplyForContainer(id) }
    private fun available(m: Long) = StockForecasts.available(m, all())

    @Test fun usedUpPackageWithOneTabletLeftIsCorrectedWithoutANewPackage() {
        val m = med(); val a = box(m, "IN_USE", used = 24.0)          // 4 mg left on paper
        tx { SupplyLedger.allocate(dao, taken(m, 4.0)) }
        assertEquals("EMPTY", c(a).state)                              // app thinks it is used up
        val before = all().size
        runBlocking { repo.correctStock(a, 2.0) }                      // one 2 mg tablet is really left
        assertEquals("IN_USE", c(a).state); assertEquals(26.0, c(a).used_amount, 1e-9)
        assertEquals("no package created", before, all().size)
        assertEquals(listOf("CONSUME", "ADJUST"), ledger(a).map { it.kind }); assertEquals(-2.0, ledger(a).last().used_delta, 1e-9)
        assertEquals(2.0, available(m)!!, 1e-9)
        runBlocking { repo.correctStock(a, 2.0) }                      // same count again: nothing to apply twice
        assertEquals(2, ledger(a).size); assertEquals(2.0, available(m)!!, 1e-9)
    }

    @Test fun openPackageCorrectionAndTotalChangesOnlyByTheRealDifference() {
        val m = med(); val a = box(m, "IN_USE", used = 10.0); box(m, "SEALED")
        assertEquals(46.0, available(m)!!, 1e-9)
        runBlocking { repo.correctStock(a, 14.0) }                     // 18 on paper, 14 counted
        assertEquals(42.0, available(m)!!, 1e-9)
        assertEquals(listOf(-4.0), ledger(a).map { -it.used_delta })
    }

    @Test fun theChosenOldPackageIsCorrectedAndTheOthersStayAsTheyAre() {
        val m = med(); val older = box(m, "EMPTY", used = 28.0, opened = "2025-11-01"); val newer = box(m, "EMPTY", used = 28.0, opened = "2025-12-01")
        runBlocking { repo.correctStock(newer, 2.0) }
        assertEquals("EMPTY", c(older).state); assertEquals(28.0, c(older).used_amount, 1e-9); assertTrue(ledger(older).isEmpty())
        assertEquals("IN_USE", c(newer).state); assertEquals(2.0, available(m)!!, 1e-9)
    }

    @Test fun sealedAndDiscardedPackagesAreNotCorrectedThisWay() {
        val m = med(); val s = box(m, "SEALED"); val d = box(m, "DISCARDED", used = 20.0)
        for (id in listOf(s, d)) {
            assertThrows(IllegalArgumentException::class.java) { runBlocking { repo.correctStock(id, 2.0) } }
            assertTrue(ledger(id).isEmpty())
        }
        assertEquals("DISCARDED", c(d).state)
    }

    @Test fun restoredPackageIsUsedNextAndNotCountedTwice() {
        val m = med(); val a = box(m, "IN_USE", used = 26.0)
        tx { SupplyLedger.allocate(dao, taken(m, 2.0)) }; assertEquals("EMPTY", c(a).state)
        val b = box(m, "SEALED")
        runBlocking { repo.correctStock(a, 2.0) }
        assertEquals(30.0, available(m)!!, 1e-9)
        tx { SupplyLedger.allocate(dao, taken(m, 2.0)) }              // the restored, older package is used first
        assertEquals("EMPTY", c(a).state); assertEquals("SEALED", c(b).state); assertEquals(28.0, available(m)!!, 1e-9)
    }

    @Test fun anUnusedPackageAddedByMistakeCanBeUndone() {
        val m = med(); runBlocking { repo.addContainers(m, 28.0, 1, true) }
        val fake = all().single().id
        assertFalse(fake in runBlocking { repo.containersWithLedger() })
        runBlocking { repo.removeMistakenContainer(fake) }
        assertTrue(all().isEmpty()); assertNull(available(m))
    }

    @Test fun aPackageWithStockHistoryIsNeverDeleted() {
        val m = med()
        val consumed = box(m, "IN_USE"); tx { SupplyLedger.allocate(dao, taken(m, 2.0)) }
        val adjusted = box(m, "SEALED").also { runBlocking { dao.setContainerState(it, "IN_USE", "2026-02-01") } }
        runBlocking { repo.correctStock(adjusted, 20.0) }
        for (id in listOf(consumed, adjusted)) {
            assertTrue(id in runBlocking { repo.containersWithLedger() })
            assertThrows(IllegalArgumentException::class.java) { runBlocking { repo.removeMistakenContainer(id) } }
            assertNotNull(all().singleOrNull { it.id == id }); assertTrue(ledger(id).isNotEmpty())
        }
    }

    @Test fun fixingTheDoseRecordFirstThenCheckingStockLandsOnTheCount() {
        val m = med(); val a = box(m, "IN_USE", used = 20.0)          // 8 left
        val wrong = taken(m, 2.0); tx { SupplyLedger.allocate(dao, wrong) }   // a dose recorded by mistake: 6 on paper
        // Recommended order: undo the wrong record (its consumption is reversed once), then check stock.
        tx { SupplyLedger.reverse(dao, wrong.id) }; assertEquals(8.0, available(m)!!, 1e-9)
        tx { SupplyLedger.reverse(dao, wrong.id) }; assertEquals("reversal is not applied twice", 8.0, available(m)!!, 1e-9)
        runBlocking { repo.correctStock(a, 8.0) }
        assertEquals(8.0, available(m)!!, 1e-9); assertEquals(listOf("CONSUME", "REVERSE"), ledger(a).map { it.kind })
        // A check is an absolute count: after any later change, checking again lands on the counted amount.
        runBlocking { repo.correctStock(a, 6.0) }; assertEquals(6.0, available(m)!!, 1e-9)
    }

    @Test fun noPackagesAndNoRecords() {
        val m = med()
        assertNull(available(m)); assertTrue(runBlocking { repo.containersWithLedger() }.isEmpty())
        assertEquals(StockForecast.Status.UNTRACKED, StockForecasts.forecast(m, all(), emptyList()).status)
    }
}
