package net.plainnotes.app.data

import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import net.plainnotes.app.domain.AttachmentSession
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CountDownLatch

/** External harness kills this test process at exact production filesystem/SQL seams. */
class AttachmentPersistentRestartAndroidTest {
    private val app get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val directory get()=File(app.noBackupFilesDir,"synthetic-persistent-restart")
    private val context get()=object:ContextWrapper(app) {override fun getNoBackupFilesDir()=directory}
    private val marker get()=File(app.filesDir,"synthetic-persistent-restart-ready")
    private val name="synthetic-persistent-restart.db"
    private val data get()=ByteArray(65573) {(it*13).toByte()}
    private fun database():NotesDatabase {
        System.loadLibrary("sqlcipher")
        return Room.databaseBuilder(app,NotesDatabase::class.java,name).openHelperFactory(SupportOpenHelperFactory(ByteArray(32) {71}))
            .addMigrations(*allMigrations()).addCallback(SchemaGuards).build()
    }
    @Test fun persistentCiphertextRestart() {
        val args=InstrumentationRegistry.getArguments()
        val stop=args.getString("persistent_boundary") ?: "STAGED"
        require(stop in setOf("WRITE","STAGED","IN_COMMIT","COMMITTED"))
        when(args.getString("persistent_phase")) {
            "prepare"->prepare(stop,true)
            "verify"->verify(stop,true)
            null->{prepare(stop,false);verify(stop,false)}
            else->error("Unknown restart phase")
        }
    }
    private fun prepare(stop:String,wait:Boolean) {
        marker.delete();app.deleteDatabase(name);directory.deleteRecursively();check(directory.mkdirs())
        val db=database()
        try {
            val raw=db.openHelper.writableDatabase
            raw.beginTransaction()
            val scope:AttachmentIdentity.Scope;val record:String
            try {
                raw.execSQL("INSERT INTO appointment(id,type,at_utc,at_zone,note,remind_minutes_before) VALUES (1,'ENDO',1000,'UTC','synthetic restart',0)")
                scope=requireNotNull(AttachmentIdentity.initialize(raw,true));record=AttachmentIdentity.bind(raw,scope,"APPOINTMENT",1)
                raw.setTransactionSuccessful()
            } finally {raw.endTransaction()}
            val session=AttachmentSession(UUID.fromString(scope.dataset),UUID.fromString(scope.epoch))
            val bytes=data;val sha=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {"%02x".format(it.toInt() and 255)}
            fun request()=AttachmentPersistentStore.Request(UUID.randomUUID(),UUID.randomUUID(),UUID.fromString(record),0,"Synthetic restart","OTHER","application/octet-stream",bytes.size.toLong(),sha)
            val safe=request();AttachmentPersistentStore(context,db).save(scope,session.issue(),safe) {ByteArrayInputStream(bytes)}
            val interrupted=request()
            val state=JSONObject().put("pid",android.os.Process.myPid()).put("dataset",scope.dataset).put("epoch",scope.epoch)
                .put("safe",safe.document.toString()).put("interrupted",interrupted.document.toString()).put("operation",interrupted.operation.toString()).put("boundary",stop)
            try {
                AttachmentPersistentStore(context,db,checkpoint={boundary->if(boundary.name==stop) {
                    marker.writeText(state.toString()) // Synthetic IDs only; no plaintext attachment or raw key.
                    if(wait)CountDownLatch(1).await() else throw InterruptedException("synthetic restart seam")
                }}).save(scope,session.issue(),interrupted) {
                    if(stop!="WRITE") ByteArrayInputStream(bytes) else object:java.io.InputStream() {
                        private val input=ByteArrayInputStream(bytes);private var reads=0
                        override fun read():Int=error("Bulk reads required")
                        override fun read(out:ByteArray,offset:Int,length:Int):Int {
                            if(reads++==1) {
                                marker.writeText(state.toString())
                                if(wait)CountDownLatch(1).await() else throw InterruptedException("synthetic partial ciphertext")
                            }
                            return input.read(out,offset,length)
                        }
                        override fun close()=input.close()
                    }
                }
                fail("Seam was not reached")
            } catch(expected:InterruptedException) {check(!wait)} finally {session.revoke()}
        } finally {db.close()}
    }
    private fun verify(stop:String,fresh:Boolean) {
        val state=JSONObject(marker.readText());assertEquals(stop,state.getString("boundary"))
        if(fresh)assertNotEquals(state.getInt("pid"),android.os.Process.myPid())
        val db=database()
        try {
            val scope=requireNotNull(AttachmentIdentity.current(db.openHelper.writableDatabase))
            assertEquals(state.getString("dataset"),scope.dataset);assertEquals(state.getString("epoch"),scope.epoch)
            val session=AttachmentSession(UUID.fromString(scope.dataset),UUID.fromString(scope.epoch))
            try {
                val store=AttachmentPersistentStore(context,db)
                store.open(scope,session.issue(),UUID.fromString(state.getString("safe"))).use {reader ->val out=ByteArray(data.size);var offset=0;while(offset<out.size) {val n=reader.readAt(offset.toLong(),out,offset,out.size-offset);assertTrue(n>0);offset+=n};assertArrayEquals(data,out)}
                val committed=stop=="COMMITTED"
                assertEquals(if(committed)0 else 1,store.recover(scope,session.issue()));assertEquals(0,store.recover(scope,session.issue()))
                val raw=db.openHelper.writableDatabase
                raw.query("SELECT COUNT(*) FROM attachment_version").use {assertTrue(it.moveToFirst());assertEquals(if(committed)2 else 1,it.getInt(0))}
                raw.query("SELECT stage_uses,active_uses,proof FROM attachment_journal WHERE operation_uuid=?",arrayOf(state.getString("operation"))).use {
                    assertTrue(it.moveToFirst());assertEquals(1,it.getInt(0));assertEquals(if(stop in setOf("WRITE","STAGED"))0 else 1,it.getInt(1));assertEquals(if(committed)"COMMITTED" else "QUARANTINED",it.getString(2))
                }
                raw.query("SELECT feature_enabled FROM attachment_store WHERE store_state='CURRENT'").use {assertTrue(it.moveToFirst());assertEquals(0,it.getInt(0))}
                raw.query("PRAGMA foreign_key_check").use {assertEquals(0,it.count)}
                if(committed)store.open(scope,session.issue(),UUID.fromString(state.getString("interrupted"))).close()
            } finally {session.revoke()}
        } finally {db.close();marker.delete()}
    }
}
