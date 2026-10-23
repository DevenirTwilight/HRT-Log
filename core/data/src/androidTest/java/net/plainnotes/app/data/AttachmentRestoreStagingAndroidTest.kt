package net.plainnotes.app.data

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import net.plainnotes.app.domain.AttachmentSession
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException
import java.util.UUID

/** Real SQLCipher + held private FDs + unchanged Tink. No production archive format involved. */
class AttachmentRestoreStagingAndroidTest {
    private val app get()=InstrumentationRegistry.getInstrumentation().targetContext
    internal class Fixture(val app:Context,val name:String="synthetic-restore-${UUID.randomUUID()}.db",
        val directory:File=File(app.noBackupFilesDir,"synthetic-restore-${UUID.randomUUID()}").apply {check(mkdir())},
        reopen:Boolean=false):AutoCloseable {
        val context=object:ContextWrapper(app) {override fun getNoBackupFilesDir()=directory}
        val db:NotesDatabase
        val raw get()=db.openHelper.writableDatabase
        val scope:AttachmentIdentity.Scope
        val session:AttachmentSession
        init {
            System.loadLibrary("sqlcipher")
            db=Room.databaseBuilder(app,NotesDatabase::class.java,name).openHelperFactory(SupportOpenHelperFactory(ByteArray(32) {56}))
                .addMigrations(*allMigrations()).addCallback(SchemaGuards).build()
            if(!reopen) {
                raw.beginTransaction();try {
                    raw.execSQL("INSERT INTO appointment(id,type,at_utc,at_zone,note,remind_minutes_before) VALUES(1,'ENDO',1000,'UTC','SYNTHETIC old destination remains',0)")
                    AttachmentIdentity.initialize(raw,true);raw.setTransactionSuccessful()
                } finally {raw.endTransaction()}
            }
            scope=requireNotNull(AttachmentIdentity.current(raw));session=AttachmentSession(UUID.fromString(scope.dataset),UUID.fromString(scope.epoch))
        }
        fun safety() {
            raw.query("SELECT note FROM appointment WHERE id=1").use {assertTrue(it.moveToFirst());assertEquals("SYNTHETIC old destination remains",it.getString(0))}
            for(t in listOf("attachment_document","attachment_version","attachment_journal","attachment_operation","attachment_link"))raw.query("SELECT COUNT(*) FROM $t").use {assertTrue(it.moveToFirst());assertEquals(0,it.getInt(0))}
            raw.query("SELECT feature_enabled FROM attachment_store WHERE store_state='CURRENT'").use {assertTrue(it.moveToFirst());assertEquals(0,it.getInt(0))}
            raw.query("PRAGMA foreign_key_check").use {assertEquals(0,it.count)}
        }
        fun store()=AttachmentRestoreStaging(context,db)
        fun closeRetainingFiles() {session.revoke();db.close()}
        override fun close() {closeRetainingFiles();app.deleteDatabase(name);directory.deleteRecursively()}
    }
    private fun reject(block:()->Unit) {try {block();fail("Unsafe staging accepted")}catch(_:Exception){}}
    @Test fun encryptedHistoryAndTrashStageWithoutPublishingAndColdReopenNeverAdopts() {
        Fixture(app).use {f->RestoreSyntheticArchive().use {a->
            val receipt=f.store().stage(f.scope,f.session.issue(),a.plan(),a.source())
            assertEquals(3,receipt.objects.size);assertFalse(receipt.activatable);assertFalse(receipt.wholeArchiveAuthenticated)
            receipt.objects.forEachIndexed {i,o->val file=File(f.directory,"attachment-restore-quarantine-v1/${o.physicalFile}")
                assertArrayEquals(a.ciphertext[i],file.readBytes());assertFalse(file.readBytes().contentEquals(a.plaintext[i]))}
            f.safety();val retained=f.store().retained(f.scope,f.session.issue());assertEquals(3,retained.entries.size)
            // Cold coordinator has only charged UNKNOWN leaves, no reconstructible commit/receipt.
            assertEquals(retained.entries.toSet(),f.store().retained(f.scope,f.session.issue()).entries.toSet())
            assertEquals(3,f.store().retained(f.scope,f.session.issue()).entries.size);f.safety()
        }}
    }
    @Test fun corruptionWrongKeysMissingExtraAndInterruptedSourcesRetainOldData() {
        for(fault in listOf("CORRUPT","KEY","SHORT","EXTRA","IO"))Fixture(app).use {f->RestoreSyntheticArchive().use {a->
            val original=a.source();var consumed:ByteArray?=null
            val source=object:AttachmentRestoreStaging.CiphertextSource by original {
                override fun takeDek(version:UUID):ByteArray = original.takeDek(version).also {if(fault=="KEY")it[0]=(it[0].toInt() xor 1).toByte();consumed=it}
                override fun open(version:UUID):java.io.InputStream {
                    if(fault=="IO")throw IOException("Synthetic source interruption")
                    val i=a.versions.indexOfFirst {it.context.version==version};val b=a.ciphertext[i].copyOf()
                    return java.io.ByteArrayInputStream(when(fault) {
                        "CORRUPT"->b.also {it[it.lastIndex]=(it.last().toInt() xor 1).toByte()}
                        "SHORT"->b.copyOf(b.size-1);"EXTRA"->b+byteArrayOf(1);else->b
                    })
                }
            }
            reject {f.store().stage(f.scope,f.session.issue(),a.plan(),source)}
            consumed?.let {assertTrue(it.all {b->b==0.toByte()})};f.safety()
            assertTrue(f.store().retained(f.scope,f.session.issue()).entries.isNotEmpty())
        }}
    }
    @Test fun cancellationAndDatasetSwitchRefuseLateOutputWithoutClearingOldRecords() {
        for(switch in listOf(false,true))Fixture(app).use {f->RestoreSyntheticArchive().use {a->var ran=false
            val source=a.source {if(!ran) {ran=true
                if(switch) {f.raw.beginTransaction();try {AttachmentIdentity.replaceStructuredDataset(f.raw);f.raw.setTransactionSuccessful()}finally {f.raw.endTransaction()}}
                else f.session.revoke()
            }}
            reject {f.store().stage(f.scope,f.session.issue(),a.plan(),source)};assertTrue(ran);f.safety()
            reject {f.store().retained(f.scope,f.session.issue())}
            val scope=requireNotNull(AttachmentIdentity.current(f.raw));val s=AttachmentSession(UUID.fromString(scope.dataset),UUID.fromString(scope.epoch))
            try {assertTrue(f.store().retained(scope,s.issue()).entries.isNotEmpty())}finally {s.revoke()}
        }}
    }
    @Test fun unknownStagingIsChargedBeforeOpeningSourceAndBorrowedTransactionsRefuse() {
        Fixture(app).use {f->RestoreSyntheticArchive().use {a->
            val plan=a.plan(limits=AttachmentRestorePreflight.Limits(maximumStagingBytes=1024*1024))
            f.store().retained(f.scope,f.session.issue())
            File(f.directory,"attachment-restore-quarantine-v1/${UUID.randomUUID()}").apply {writeBytes(ByteArray(2*1024*1024) {42});setReadable(false,false);setReadable(true,true);setWritable(false,false)}
            var opened=false;val original=a.source();val source=object:AttachmentRestoreStaging.CiphertextSource by original {
                override fun open(version:UUID):java.io.InputStream {opened=true;return original.open(version)}
            }
            reject {f.store().stage(f.scope,f.session.issue(),plan,source)};assertFalse(opened)
            f.raw.beginTransaction();try {reject {f.store().stage(f.scope,f.session.issue(),plan,source)}}finally {f.raw.endTransaction()}
            f.safety()
        }}
    }
}
