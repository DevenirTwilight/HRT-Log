package net.plainnotes.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import net.plainnotes.app.importer.TransMemo
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28])
class DataTransferTest {
    private lateinit var db: NotesDatabase
    private val dao get() = db.dao()
    private val raw get() = db.openHelper.writableDatabase
    private val zone = ZoneId.of("Europe/Paris")
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    @Before fun open() { db = Room.inMemoryDatabaseBuilder(context, NotesDatabase::class.java).allowMainThreadQueries().addCallback(SchemaGuards).build(); raw }
    @After fun close() = db.close()

    private fun syntheticExport(): File {
        val f = File(context.cacheDir, "tm.db").apply { delete() }
        val sql = File("../../importer/src/test/resources/transmemo_v8_synthetic.sql").readText()
        SQLiteDatabase.openOrCreateDatabase(f, null).use { d ->
            sql.split(";\n").map { it.lines().filterNot { l -> l.trim().startsWith("--") }.joinToString("\n").trim() }.filter { it.isNotEmpty() && !it.contains("android_metadata") }.forEach { d.execSQL(it) }
        }
        return f
    }
    private fun plan() = AndroidSqlSource.open(syntheticExport()).use { src ->
        TransMemo.plan(TransMemo.read(src), zone, TransMemo.Choices(mapOf(1L to 120, 2L to 60, 3L to 60, 4L to 60), TransMemo.LateHandling.AS_MISSED, TransMemo.WellbeingScale.ONE_TO_FIVE))
    }
    private fun import(overwrite: Boolean) = runBlocking { db.withTransaction { TransMemoWriter.write(dao, raw, plan(), overwrite, zone) } }

    @Test fun importsPausedMedicationsAndMergesWithoutDuplicates() {
        val first = import(false)
        assertEquals(4, first.medications); assertEquals(9, first.intakes); assertEquals(3, first.containers)
        val meds = runBlocking { dao.medications() }
        assertTrue(meds.all { it.needs_review != null && !it.notifications_on && it.soon_alert_minutes == null && it.late_after_minutes == null && it.route == null })
        val review = JSONObject(meds.first { it.name == "Synthetic gel" }.needs_review!!)
        assertEquals("2", review.getJSONObject("raw").getString("lateAlertDelay"))
        assertTrue(review.getJSONObject("prefill").getBoolean("daily"))
        assertTrue(runBlocking { dao.records() }.all { it.origin == "IMPORT_TM" && it.source_record_key != null })
        val second = import(false)
        assertEquals(0, second.medications); assertEquals(4, second.reusedMedications); assertEquals(0, second.intakes); assertEquals(9, second.duplicates)
        assertEquals(0, second.containers); assertEquals(0, second.scores); assertEquals(0, second.notes); assertEquals(0, second.appointments)
        val over = import(true)
        assertEquals(4, over.medications); assertEquals(9, over.intakes)
        assertEquals(4, runBlocking { dao.medications() }.size)
    }

    @Test fun backupRoundTripRestoresEverythingIncludingLedger() {
        import(false)
        val m = runBlocking { dao.medications() }.first()
        val c = runBlocking { dao.insertContainer(ContainerEntity(medication_id = m.id, capacity = 10.0, initial_used_amount = 1.0, used_amount = 1.0, opened_on = "2026-01-01", state = "IN_USE")) }
        runBlocking { db.withTransaction { SupplyLedger.setRemaining(dao, c, 4.0) } }
        val before = runBlocking { db.withTransaction { RawData.dump(raw) } }
        val enc = BackupCodec.encrypt(JSONObject().put("tables", before).toString().toByteArray(), "correct horse".toCharArray())
        runBlocking { db.withTransaction { RawData.clear(raw) } }
        assertTrue(runBlocking { dao.medications() }.isEmpty())
        try { BackupCodec.decrypt(enc, "wrong password".toCharArray()); fail() } catch (_: BackupCodec.WrongPassword) {}
        val tables = JSONObject(String(BackupCodec.decrypt(enc, "correct horse".toCharArray()))).getJSONObject("tables")
        runBlocking { db.withTransaction { RawData.restore(raw, tables) } }
        assertEquals(before.toString(), runBlocking { db.withTransaction { RawData.dump(raw) } }.toString())
        assertEquals(6.0, runBlocking { dao.container(c).used_amount }, 1e-9)
        // Guards are back after restore: the ledger stays append-only.
        try { raw.execSQL("DELETE FROM supply_transaction"); fail() } catch (_: Exception) {}
    }

    @Test fun rejectsForeignFiles() {
        try { BackupCodec.decrypt("not a backup at all, definitely not".toByteArray(), "whatever1".toCharArray()); fail() } catch (_: BackupCodec.BadFile) {}
    }
}
