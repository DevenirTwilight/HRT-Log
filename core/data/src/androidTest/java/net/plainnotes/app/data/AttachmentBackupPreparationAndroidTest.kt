package net.plainnotes.app.data

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import net.plainnotes.app.domain.AttachmentSession
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

/** Production read-only snapshot + native SQLCipher/Keystore/full Tink authentication. */
class AttachmentBackupPreparationAndroidTest {
    private val app get()=InstrumentationRegistry.getInstrumentation().targetContext
    private class Fixture(val app:Context):AutoCloseable {
        val name="synthetic-backup-preparation-${UUID.randomUUID()}.db"
        val directory=File(app.noBackupFilesDir,"synthetic-backup-preparation-${UUID.randomUUID()}").apply {check(mkdir())}
        val context=object:ContextWrapper(app) {override fun getNoBackupFilesDir()=directory}
        val password=ByteArray(32) {43}
        val db:NotesDatabase
        val scope:AttachmentIdentity.Scope
        val record:UUID
        val session:AttachmentSession
        val raw get()=db.openHelper.writableDatabase
        init {
            System.loadLibrary("sqlcipher")
            db=Room.databaseBuilder(app,NotesDatabase::class.java,name).openHelperFactory(SupportOpenHelperFactory(password))
                .addMigrations(*allMigrations()).addCallback(SchemaGuards).build()
            raw.beginTransaction()
            try {
                raw.execSQL("INSERT INTO appointment(id,type,at_utc,at_zone,note,remind_minutes_before) VALUES (1,'ENDO',1000,'UTC','Synthetic preparation',0)")
                scope=requireNotNull(AttachmentIdentity.initialize(raw,true));record=UUID.fromString(AttachmentIdentity.bind(raw,scope,"APPOINTMENT",1));raw.setTransactionSuccessful()
            } finally {raw.endTransaction()}
            session=AttachmentSession(UUID.fromString(scope.dataset),UUID.fromString(scope.epoch))
        }
        fun store()=AttachmentPersistentStore(context,db)
        fun request(bytes:ByteArray,doc:UUID=UUID.randomUUID(),revision:Long=0,mime:String="application/octet-stream")=
            AttachmentPersistentStore.Request(UUID.randomUUID(),doc,record,revision,"Synthetic original","REPORT",mime,bytes.size.toLong(),sha(bytes))
        fun number(sql:String)=raw.query(sql).use {check(it.moveToFirst());it.getLong(0)}
        fun safety() {assertEquals(0L,number("SELECT feature_enabled FROM attachment_store WHERE store_state='CURRENT'"));raw.query("PRAGMA foreign_key_check").use {assertEquals(0,it.count)}
            raw.query("SELECT note FROM appointment WHERE id=1").use {assertTrue(it.moveToFirst());assertEquals("Synthetic preparation",it.getString(0))}}
        override fun close() {
            session.revoke()
            try {val ks=KeyStore.getInstance("AndroidKeyStore").apply {load(null)}
                raw.query("SELECT stage_alias_uuid,active_alias_uuid FROM attachment_journal").use {c->while(c.moveToNext()) {
                    ks.deleteEntry(AttachmentDeviceKeys.Alias(UUID.fromString(c.getString(0)),AttachmentDeviceKeys.Role.STAGING).value)
                    ks.deleteEntry(AttachmentDeviceKeys.Alias(UUID.fromString(c.getString(1)),AttachmentDeviceKeys.Role.ACTIVE).value)
                }}
            } finally {db.close();app.deleteDatabase(name);directory.deleteRecursively();password.fill(0)}
        }
    }
    @Test fun consistentSnapshotAuthenticatesPdfImageBinarySupersededVersionAndTrash() {
        Fixture(app).use {f->
            val pdf=ByteArrayOutputStream().also {output->val d=PdfDocument();try {for(i in 1..3) {
                val page=d.startPage(PdfDocument.PageInfo.Builder(200,200,i).create());page.canvas.drawText("Synthetic page $i",10f,40f,Paint());d.finishPage(page)
            };d.writeTo(output)}finally {d.close()}}.toByteArray()
            val image=java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jc1sAAAAASUVORK5CYII=")
            val binary=ByteArray(12345) {(it*9).toByte()}
            val requests=listOf(f.request(pdf,mime="application/pdf"),f.request(image,mime="image/png"),f.request(binary))
            requests.zip(listOf(pdf,image,binary)).forEach {(r,bytes)->f.store().save(f.scope,f.session.issue(),r) {ByteArrayInputStream(bytes)}}
            val replacement=byteArrayOf(2,3,5);f.store().save(f.scope,f.session.issue(),f.request(replacement,requests[0].document,1)) {ByteArrayInputStream(replacement)}
            f.store().trash(f.scope,f.session.issue(),requests[2].document,1)
            val before=f.number("SELECT COUNT(*) FROM attachment_operation")
            AttachmentBackupPreparation(f.context,f.db).capture(f.scope,f.session.issue(),created=Instant.ofEpochSecond(1234)).use {snapshot->
                val m=snapshot.manifest();assertEquals(4,m.versions.size);assertEquals(3,m.documents.size);assertEquals(1,m.records.size);assertEquals(3,m.links.size)
                assertEquals(1,m.documents.count {it.state=="TRASHED"});assertEquals(1,m.links.count {it.state=="QUARANTINED"})
                assertEquals(setOf(sha(pdf),sha(image),sha(binary),sha(replacement)),m.versions.map {it.manifest.plaintextSha256}.toSet())
                snapshot.records {bytes->val j=JSONObject(String(bytes,Charsets.UTF_8));assertEquals(RecordBackupCompatibility.exportSchema(f.raw),j.getInt("schema"))
                    assertEquals("Synthetic preparation",j.getJSONObject("tables").getJSONArray("appointment").getJSONObject(0).getString("note"))}
            }
            assertEquals(before,f.number("SELECT COUNT(*) FROM attachment_operation"));assertEquals(4L,f.number("SELECT COUNT(*) FROM attachment_version"));f.safety()
        }
    }
    @Test fun allRetainedMembersAreRequiredAndAnUnavailableSupersededOriginalRefusesSnapshot() {
        Fixture(app).use {f->
            val bytes=byteArrayOf(1,2,3);val r=f.request(bytes);f.store().save(f.scope,f.session.issue(),r) {ByteArrayInputStream(bytes)}
            f.store().save(f.scope,f.session.issue(),f.request(bytes,r.document,1)) {ByteArrayInputStream(bytes)}
            val id=f.raw.query("SELECT object_uuid FROM attachment_journal WHERE operation_uuid=?",arrayOf(r.operation.toString())).use {assertTrue(it.moveToFirst());it.getString(0)}
            val file=File(f.directory,"attachment-objects-v1/$id");val retained=File(f.directory,"retained-ciphertext")
            assertTrue(file.renameTo(retained));reject {AttachmentBackupPreparation(f.context,f.db).capture(f.scope,f.session.issue())}
            assertTrue(retained.exists());assertTrue(retained.renameTo(file));assertTrue(file.setWritable(true,true))
            java.io.RandomAccessFile(file,"rw").use {it.seek(it.length()-1);val x=it.readByte();it.seek(it.length()-1);it.writeByte(x.toInt() xor 1)};assertTrue(file.setReadOnly())
            reject {AttachmentBackupPreparation(f.context,f.db).capture(f.scope,f.session.issue())}
            // Preparation is read-only: no original pointer/status/clinical row is changed on failure.
            assertEquals(2L,f.number("SELECT COUNT(*) FROM attachment_version WHERE status='VERIFIED'"))
            f.store().open(f.scope,f.session.issue(),r.document).use {reader->val out=ByteArray(3);assertEquals(3,reader.readAt(0,out,0,3));assertArrayEquals(bytes,out)}
            f.safety()
        }
    }
    @Test fun snapshotCancellationAndDatasetSwitchRevokeAndEraseOwnedRecordBytes() {
        Fixture(app).use {f->
            val p=AttachmentBackupPreparation(f.context,f.db)
            val first=p.capture(f.scope,f.session.issue());lateinit var owned:ByteArray
            first.records {owned=it};assertTrue(owned.isNotEmpty());assertTrue(owned.any {it!=0.toByte()})
            first.close();assertTrue(owned.all {it==0.toByte()})
            var published=false;reject {first.records {published=true}};assertFalse(published)
            val next=p.capture(f.scope,f.session.issue())
            reject {next.records {owned=it
                // An omitted application session revoke must still fail the POST-callback scope check.
                f.raw.beginTransaction();try {AttachmentIdentity.replaceStructuredDataset(f.raw);f.raw.setTransactionSuccessful()}finally {f.raw.endTransaction()}
            }}
            reject {next.manifest()};assertTrue(owned.all {it==0.toByte()});next.close();f.safety()
        }
    }
    @Test fun clinicalTrashAndCorrectionKeepStableIdentityAndQuarantinedLinksInSnapshot() {
        Fixture(app).use {f->
            val bytes=byteArrayOf(1,2,3);val r=f.request(bytes);f.store().save(f.scope,f.session.issue(),r) {ByteArrayInputStream(bytes)}
            val repo=NotesRepository(object:DatabaseAccess(f.app) {override fun get(space:Space)=f.db})
            kotlinx.coroutines.runBlocking {repo.deleteAppointment(1)}
            AttachmentBackupPreparation(f.context,f.db).capture(f.scope,f.session.issue()).use {snapshot->
                val m=snapshot.manifest();assertEquals(f.record,m.records.single().uuid);assertEquals("TRASHED",m.records.single().state)
                assertNotNull(m.records.single().trashId);assertEquals("QUARANTINED",m.links.single().state)
                snapshot.records {data->val tables=JSONObject(String(data,Charsets.UTF_8)).getJSONObject("tables")
                    assertEquals(0,tables.getJSONArray("appointment").length());assertEquals(1,tables.getJSONArray("trash_item").length())}
            }
            val trashId=f.raw.query("SELECT id FROM trash_item WHERE kind='APPOINTMENT'").use {assertTrue(it.moveToFirst());it.getLong(0)}
            kotlinx.coroutines.runBlocking {repo.restoreTrash(trashId)}
            AttachmentBackupPreparation(f.context,f.db).capture(f.scope,f.session.issue()).use {snapshot->
                assertEquals(f.record,snapshot.manifest().records.single().uuid);assertEquals("ACTIVE",snapshot.manifest().records.single().state)
                assertEquals("QUARANTINED",snapshot.manifest().links.single().state)
            }
            f.safety()
        }
    }
    @Test fun callerTransactionAndWrongSessionCannotCreateOrMutateBackupFacts() {
        Fixture(app).use {f->val p=AttachmentBackupPreparation(f.context,f.db)
            f.raw.beginTransaction();try {reject {p.capture(f.scope,f.session.issue())}}finally {f.raw.endTransaction()}
            val other=AttachmentSession(UUID.randomUUID(),UUID.randomUUID())
            try {reject {p.capture(f.scope,other.issue())}}finally {other.revoke()}
            assertEquals(0L,f.number("SELECT COUNT(*) FROM attachment_operation"));f.safety()
        }
    }
    companion object {
        private fun sha(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {"%02x".format(it.toInt() and 255)}
        private fun reject(block:()->Unit) {try {block();fail("Invalid snapshot accepted")}catch(_:Exception){}}
    }
}
