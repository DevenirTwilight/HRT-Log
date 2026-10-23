package net.plainnotes.app.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import net.plainnotes.app.domain.AttachmentSession
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch

/** Own synthetic SQLCipher DB in the library test APK; external phase blocks inside a SQL transaction. */
class AttachmentFoundationRestartAndroidTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "synthetic-med02b1-foundation-restart.db"
    private val marker get() = File(context.filesDir, "synthetic-med02b1-foundation-ready")
    private fun database(): NotesDatabase {
        System.loadLibrary("sqlcipher")
        return Room.databaseBuilder(context, NotesDatabase::class.java, name)
            .openHelperFactory(SupportOpenHelperFactory(ByteArray(32) { 23 }))
            .addCallback(SchemaGuards).build()
    }

    @Test fun interruptedMetadataTransaction() {
        when (InstrumentationRegistry.getArguments().getString("med02b1_phase")) {
            "prepare" -> prepare(true)
            "verify" -> verify(true)
            null -> { prepare(false); verify(false) }
            else -> error("Unknown synthetic restart phase")
        }
    }

    private fun prepare(blockUntilKilled: Boolean) {
        marker.delete()
        context.deleteDatabase(name)
        val db = database()
        try {
            val raw = db.openHelper.writableDatabase
            raw.beginTransaction()
            val state: JSONObject
            try {
                raw.execSQL("INSERT INTO appointment(id,type,at_utc,at_zone,note,remind_minutes_before) VALUES (1,'ENDO',1000,'UTC','synthetic restart sentinel',0)")
                val scope = requireNotNull(AttachmentIdentity.initialize(raw, true))
                val identity = AttachmentIdentity.bind(raw, scope, "APPOINTMENT", 1)
                val committed = AttachmentOperations.prepare(raw, scope, UUID.randomUUID(), "IMPORT", 10, 1)
                val session = AttachmentSession(UUID.fromString(scope.dataset), UUID.fromString(scope.epoch))
                state = JSONObject().put("dataset", scope.dataset).put("vault", scope.logicalVault)
                    .put("epoch", scope.epoch).put("identity", identity).put("committed", committed)
                    .put("pid", android.os.Process.myPid()).put("process", session.processIdentity().toString())
                raw.setTransactionSuccessful()
            } finally { raw.endTransaction() }

            // No success flag: ordinary run rolls back; external phase dies while this transaction is live.
            raw.beginTransaction()
            try {
                val scope = requireNotNull(AttachmentIdentity.current(raw))
                val uncommitted = AttachmentOperations.prepare(raw, scope, UUID.randomUUID(), "REPLACE", 20, 1)
                raw.execSQL("UPDATE appointment SET note='synthetic uncommitted change' WHERE id=1")
                marker.writeText(state.put("uncommitted", uncommitted).toString())
                if (blockUntilKilled) CountDownLatch(1).await()
            } finally { raw.endTransaction() }
        } finally { db.close() }
    }

    private fun verify(fresh: Boolean) {
        val state = JSONObject(marker.readText())
        val db = database()
        try {
            if (fresh) assertNotEquals(state.getInt("pid"), android.os.Process.myPid())
            val raw = db.openHelper.writableDatabase
            assertEquals(14, raw.version)
            val scope = requireNotNull(AttachmentIdentity.current(raw))
            assertEquals(state.getString("dataset"), scope.dataset)
            assertEquals(state.getString("vault"), scope.logicalVault)
            assertEquals(state.getString("epoch"), scope.epoch)
            val session = AttachmentSession(UUID.fromString(scope.dataset), UUID.fromString(scope.epoch))
            assertNotEquals(state.getString("process"), session.processIdentity().toString())
            raw.query("SELECT note FROM appointment WHERE id=1").use {
                assertTrue(it.moveToFirst()); assertEquals("synthetic restart sentinel", it.getString(0))
            }
            raw.query("SELECT record_uuid FROM attachment_record_identity WHERE store_uuid=?", arrayOf(scope.dataset)).use {
                assertTrue(it.moveToFirst()); assertEquals(state.getString("identity"), it.getString(0)); assertEquals(1, it.count)
            }
            raw.query("SELECT operation_uuid,phase FROM attachment_operation WHERE store_uuid=?", arrayOf(scope.dataset)).use {
                assertEquals(1, it.count); assertTrue(it.moveToFirst())
                assertEquals(state.getString("committed"), it.getString(0)); assertEquals("PREPARED", it.getString(1))
                assertNotEquals(state.getString("uncommitted"), it.getString(0))
            }
            raw.query("SELECT feature_enabled FROM attachment_store WHERE store_state='CURRENT'").use {
                assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0))
            }
            raw.query("SELECT COUNT(*) FROM attachment_version").use { assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)) }
            raw.query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }
            android.util.Log.i("MED02B1", "api=${android.os.Build.VERSION.SDK_INT} fresh_pid=$fresh committed_pending=1 uncommitted_rolled_back=1 enabled=0")
        } finally { db.close(); context.deleteDatabase(name); marker.delete() }
    }
}
