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

/** Ledger behaviour against the real schema guards (append-only, cached used amount). */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[28])
class SupplyLedgerTest {
    private lateinit var db: NotesDatabase
    private val dao get() = db.dao()
    @Before fun open() { db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), NotesDatabase::class.java).allowMainThreadQueries().addCallback(SchemaGuards).build(); db.openHelper.writableDatabase }
    @After fun close() = db.close()
    private fun <T> tx(block: suspend () -> T): T = runBlocking { db.withTransaction { block() } }
    private fun med() = runBlocking { dao.insertMedication(MedicationEntity(name = "Synthetic", molecule = "E2", route = "ORAL", unit = "MG", dose_per_intake = 2.0, container_capacity = 5.0, soon_alert_minutes = 0, late_after_minutes = 60, site_rotation = false, notifications_on = false, active = true, sort_order = 0)) }
    private fun box(m: Long, state: String, used: Double = 0.0) = runBlocking { dao.insertContainer(ContainerEntity(medication_id = m, capacity = 5.0, initial_used_amount = used, used_amount = used, opened_on = if (state == "IN_USE") "2026-01-01" else null, state = state)) }
    private fun taken(m: Long, dose: Double) = runBlocking { dao.recordById(dao.record(RecordEntity(medication_id = m, taken_utc = 0, taken_zone = "UTC", actual_dose = dose, status = "ON_TIME", origin = "APP", revision = 1, config_snapshot = "{}")))!! }

    @Test fun consumesAcrossContainersOpeningSealedOnes() {
        val m = med(); val a = box(m, "IN_USE", 4.0); val b = box(m, "SEALED")
        val r = taken(m, 2.5)
        assertEquals(0.0, tx { SupplyLedger.allocate(dao, r) }, 1e-9)
        val c = runBlocking { dao.containers() }.associateBy { it.id }
        assertEquals(5.0, c.getValue(a).used_amount, 1e-9); assertEquals("EMPTY", c.getValue(a).state)
        assertEquals(1.5, c.getValue(b).used_amount, 1e-9); assertEquals("IN_USE", c.getValue(b).state)
    }

    @Test fun shortfallIsKeptOnTheRecord() {
        val m = med(); box(m, "IN_USE", 4.0)
        val r = taken(m, 3.0)
        assertEquals(2.0, tx { SupplyLedger.allocate(dao, r) }, 1e-9)
        assertEquals(2.0, runBlocking { dao.recordById(r.id)!!.unallocated_supply_amount!! }, 1e-9)
    }

    @Test fun reverseRestoresStockAndReopensEmptyContainer() {
        val m = med(); val a = box(m, "IN_USE", 3.0)
        val r = taken(m, 2.0); tx { SupplyLedger.allocate(dao, r) }
        assertEquals("EMPTY", runBlocking { dao.container(a).state })
        tx { SupplyLedger.reverse(dao, r.id) }
        val c = runBlocking { dao.container(a) }
        assertEquals(3.0, c.used_amount, 1e-9); assertEquals("IN_USE", c.state)
        tx { SupplyLedger.reverse(dao, r.id) } // idempotent: nothing left to reverse
        assertEquals(3.0, runBlocking { dao.container(a).used_amount }, 1e-9)
    }

    @Test fun manualCorrectionUsesAdjustEntries() {
        val m = med(); val a = box(m, "IN_USE", 1.0)
        tx { SupplyLedger.setRemaining(dao, a, 0.5) }
        assertEquals(4.5, runBlocking { dao.container(a).used_amount }, 1e-9)
        assertEquals("IN_USE", runBlocking { dao.container(a).state })
        tx { SupplyLedger.setRemaining(dao, a, 0.0) }
        assertEquals("EMPTY", runBlocking { dao.container(a).state })
    }
}
