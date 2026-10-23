package net.plainnotes.app.data

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import net.plainnotes.app.domain.AttachmentSession
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.KeyStore
import java.security.MessageDigest
import java.util.UUID

/** Production coordinator + real private inodes + Android Keystore + SQLCipher. Synthetic bytes only. */
class AttachmentPersistentStoreAndroidTest {
    private val app get()=InstrumentationRegistry.getInstrumentation().targetContext
    private class Fixture(val app:Context):AutoCloseable {
        val name="synthetic-persistent-${UUID.randomUUID()}.db"
        val directory=File(app.noBackupFilesDir,"synthetic-gate2-persistent-${UUID.randomUUID()}").apply {check(mkdir())}
        val context=object:ContextWrapper(app) {override fun getNoBackupFilesDir()=directory}
        private val password=ByteArray(32) {67}
        lateinit var db:NotesDatabase
        lateinit var scope:AttachmentIdentity.Scope
        lateinit var record:UUID
        lateinit var session:AttachmentSession
        val raw get()=db.openHelper.writableDatabase
        init {
            System.loadLibrary("sqlcipher");reopen()
            raw.beginTransaction()
            try {
                raw.execSQL("INSERT INTO appointment(id,type,at_utc,at_zone,note,remind_minutes_before) VALUES (1,'ENDO',1000,'UTC','synthetic persistent sentinel',0)")
                scope=requireNotNull(AttachmentIdentity.initialize(raw,true))
                record=UUID.fromString(AttachmentIdentity.bind(raw,scope,"APPOINTMENT",1));raw.setTransactionSuccessful()
            } finally {raw.endTransaction()}
            session=AttachmentSession(UUID.fromString(scope.dataset),UUID.fromString(scope.epoch))
        }
        fun reopen() {
            if(::db.isInitialized) db.close()
            db=Room.databaseBuilder(app,NotesDatabase::class.java,name).openHelperFactory(SupportOpenHelperFactory(password))
                .addMigrations(*allMigrations()).addCallback(SchemaGuards).build()
        }
        fun store(budget:AttachmentStorageBudget=AttachmentStorageBudget(),boundary:(AttachmentPersistentStore.Boundary)->Unit={})=
            AttachmentPersistentStore(context,db,checkpoint=boundary,storageBudget=budget)
        fun request(bytes:ByteArray,doc:UUID=UUID.randomUUID(),revision:Long=0)=AttachmentPersistentStore.Request(
            UUID.randomUUID(),doc,record,revision,"Synthetic attachment","REPORT","application/octet-stream",bytes.size.toLong(),sha(bytes))
        fun number(sql:String)=raw.query(sql).use {check(it.moveToFirst());it.getLong(0)}
        fun safety() {
            assertEquals(0L,number("SELECT feature_enabled FROM attachment_store WHERE store_state='CURRENT'"))
            raw.query("PRAGMA foreign_key_check").use {assertEquals(0,it.count)}
            raw.query("SELECT note FROM appointment WHERE id=1").use {assertTrue(it.moveToFirst());assertEquals("synthetic persistent sentinel",it.getString(0))}
            assertFalse(app.getDatabasePath(name).inputStream().use {String(ByteArray(16).also {header->java.io.DataInputStream(it).readFully(header)},Charsets.US_ASCII)}.startsWith("SQLite format 3"))
        }
        override fun close() {
            session.revoke()
            try {
                val keyStore=KeyStore.getInstance("AndroidKeyStore").apply {load(null)}
                raw.query("SELECT stage_alias_uuid,active_alias_uuid FROM attachment_journal").use {c->while(c.moveToNext()) {
                    keyStore.deleteEntry(AttachmentDeviceKeys.Alias(UUID.fromString(c.getString(0)),AttachmentDeviceKeys.Role.STAGING).value)
                    keyStore.deleteEntry(AttachmentDeviceKeys.Alias(UUID.fromString(c.getString(1)),AttachmentDeviceKeys.Role.ACTIVE).value)
                }}
            } finally {db.close();app.deleteDatabase(name);directory.deleteRecursively();password.fill(0)}
        }
    }
    @Test fun budgetRefusesBeforePrepareKeysOrInputButCommittedReplayStillWorks() {
        Fixture(app).use {f->
            val bytes=byteArrayOf(1,2,3);val r=f.request(bytes)
            val before=AttachmentDeviceKeys().retainedAliases(10000)
            for(b in listOf(AttachmentStorageBudget(maximumBytes=0),AttachmentStorageBudget(maximumAliases=0),
                AttachmentStorageBudget(minimumFreeBytes=Long.MAX_VALUE-100000000))) {
                try {f.store(budget=b).save(f.scope,f.session.issue(),r) {error("Rejected write opened input")};fail("Budget ignored")}
                catch(_:AttachmentStorageBudget.Rejected){}
                assertEquals(0L,f.number("SELECT COUNT(*) FROM attachment_journal"))
                assertEquals(before,AttachmentDeviceKeys().retainedAliases(10000))
            }
            val saved=f.store().save(f.scope,f.session.issue(),r) {ByteArrayInputStream(bytes)}
            assertEquals(saved,f.store(budget=AttachmentStorageBudget(maximumBytes=0)).save(f.scope,f.session.issue(),r) {error("Replay opened input")})
            f.safety()
        }
    }
    @Test fun accountingRetainsSupersededTrashedInterruptedAndUnknownObjects() {
        Fixture(app).use {f->
            val bytes=ByteArray(129) {11};val first=f.request(bytes)
            f.store().save(f.scope,f.session.issue(),first) {ByteArrayInputStream(bytes)}
            f.store().save(f.scope,f.session.issue(),f.request(bytes,first.document,1)) {ByteArrayInputStream(bytes)}
            var u=f.store().storageUsage(f.scope,f.session.issue())
            assertEquals(1L,u.categories.getValue(AttachmentStorageBudget.Category.CURRENT).objects)
            assertEquals(1L,u.categories.getValue(AttachmentStorageBudget.Category.HISTORY).objects)
            f.store().trash(f.scope,f.session.issue(),first.document,2)
            val broken=f.request(bytes)
            reject {f.store(boundary={if(it==AttachmentPersistentStore.Boundary.STAGED)throw IOException("synthetic interruption")}).save(f.scope,f.session.issue(),broken) {ByteArrayInputStream(bytes)}}
            f.reopen();assertEquals(1,f.store().recover(f.scope,f.session.issue()));assertEquals(0,f.store().recover(f.scope,f.session.issue()))
            val unknown=File(f.directory,"attachment-objects-v1/not-an-owned-object").apply {writeBytes(byteArrayOf(9,8,7));android.system.Os.chmod(path,384)}
            u=f.store().storageUsage(f.scope,f.session.issue())
            assertEquals(2L,u.categories.getValue(AttachmentStorageBudget.Category.HISTORY).objects)
            assertEquals(1L,u.categories.getValue(AttachmentStorageBudget.Category.QUARANTINED).objects)
            assertEquals(1L,u.categories.getValue(AttachmentStorageBudget.Category.UNKNOWN).objects)
            assertTrue(u.complete);assertEquals(0L,u.missingKnownObjects)
            val aliases=AttachmentDeviceKeys().retainedAliases(10000)
            val tight=AttachmentStorageBudget(maximumAliases=u.aliasIdentities)
            try {f.store(budget=tight).save(f.scope,f.session.issue(),f.request(bytes)) {error("Alias budget opened input")};fail("Alias budget ignored")}
            catch(e:AttachmentStorageBudget.Rejected) {assertEquals(AttachmentStorageBudget.Refusal.ALIAS_BUDGET,e.reason)}
            assertEquals(aliases,AttachmentDeviceKeys().retainedAliases(10000));assertArrayEquals(byteArrayOf(9,8,7),unknown.readBytes())
            f.store().restore(f.scope,f.session.issue(),first.document,3)
            f.store().open(f.scope,f.session.issue(),first.document).use {reader->val out=ByteArray(bytes.size);readFully(reader,out);assertArrayEquals(bytes,out)}
            f.safety()
        }
    }
    @Test fun unknownSymlinkIsNotFollowedOrDeletedAndAdmissionFailsClosed() {
        Fixture(app).use {f->
            val outside=File(f.directory,"synthetic-outside").apply {writeBytes(byteArrayOf(1,4,7))}
            AttachmentOwnedFiles(f.context).locked().close()
            val link=File(f.directory,"attachment-objects-v1/unknown-link")
            android.system.Os.symlink(outside.path,link.path)
            val usage=f.store().storageUsage(f.scope,f.session.issue());assertFalse(usage.complete)
            try {f.store().save(f.scope,f.session.issue(),f.request(byteArrayOf(5))) {error("Unknown leaf opened input")};fail("Unknown directory entry accepted")}
            catch(e:AttachmentStorageBudget.Rejected) {assertEquals(AttachmentStorageBudget.Refusal.INCOMPLETE_INVENTORY,e.reason)}
            assertEquals(0L,f.number("SELECT COUNT(*) FROM attachment_journal"));assertArrayEquals(byteArrayOf(1,4,7),outside.readBytes())
            assertEquals(outside.path,android.system.Os.readlink(link.path));f.safety()
        }
    }
    @Test fun changedInventoryBeforePublicationRefusesAndKeepsOldCommittedVersion() {
        Fixture(app).use {f->
            val bytes=byteArrayOf(2,3,5);val first=f.request(bytes)
            f.store().save(f.scope,f.session.issue(),first) {ByteArrayInputStream(bytes)}
            val outside=File(f.directory,"synthetic-outside").apply {writeBytes(byteArrayOf(8))}
            val next=f.request(byteArrayOf(7),first.document,1)
            try {
                f.store {if(it==AttachmentPersistentStore.Boundary.AUTHENTICATED)
                    android.system.Os.symlink(outside.path,File(f.directory,"attachment-objects-v1/unknown-late").path)
                }.save(f.scope,f.session.issue(),next) {ByteArrayInputStream(byteArrayOf(7))}
                fail("Changed inventory was published")
            } catch(e:AttachmentStorageBudget.Rejected) {assertEquals(AttachmentStorageBudget.Refusal.INCOMPLETE_INVENTORY,e.reason)}
            assertEquals(1L,f.number("SELECT COUNT(*) FROM attachment_version"))
            assertEquals(1,f.store().recover(f.scope,f.session.issue()));assertEquals(0,f.store().recover(f.scope,f.session.issue()))
            f.store().open(f.scope,f.session.issue(),first.document).use {r->val out=ByteArray(bytes.size);readFully(r,out);assertArrayEquals(bytes,out)}
            assertArrayEquals(byteArrayOf(8),outside.readBytes());f.safety()
        }
    }
    @Test fun syntheticPdfImageAndBinaryPersistReopenReadReplaceTrashRestore() {
        Fixture(app).use { f ->
            val samples=listOf(syntheticPdf(),java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jc1sAAAAASUVORK5CYII="),ByteArray(131091) {(it*17).toByte()})
            val requests=samples.mapIndexed {i,bytes->f.request(bytes).copy(mime=listOf("application/pdf","image/png","application/octet-stream")[i])}
            requests.zip(samples).forEach { (r,bytes) ->
                val result=f.store().save(f.scope,f.session.issue(),r) {ByteArrayInputStream(bytes)}
                assertEquals(r.operation,result.operation);assertEquals(1L,result.revision)
                assertEquals(result,f.store().operationState(f.scope,f.session.issue(),r.operation)!!.committed)
                assertEquals(result,f.store().save(f.scope,f.session.issue(),r) {error("Replay opened source")})
            }
            f.session.revoke();f.reopen();f.session=AttachmentSession(UUID.fromString(f.scope.dataset),UUID.fromString(f.scope.epoch))
            requests.zip(samples).forEach { (r,bytes) -> f.store().open(f.scope,f.session.issue(),r.document).use {reader ->
                val actual=ByteArray(bytes.size);readFully(reader,actual);assertArrayEquals(bytes,actual)
            } }
            val next="synthetic replacement".toByteArray();val replace=f.request(next,requests.last().document,1)
            f.store().save(f.scope,f.session.issue(),replace) {ByteArrayInputStream(next)}
            assertEquals(4L,f.number("SELECT COUNT(*) FROM attachment_version"));assertEquals(8L,f.number("SELECT SUM(stage_uses+active_uses) FROM attachment_journal"))
            f.store().trash(f.scope,f.session.issue(),replace.document,2)
            reject {f.store().open(f.scope,f.session.issue(),replace.document)}
            f.store().restore(f.scope,f.session.issue(),replace.document,3)
            f.store().open(f.scope,f.session.issue(),replace.document).use {reader ->val out=ByteArray(next.size);readFully(reader,out);assertArrayEquals(next,out)}
            assertEquals(1L,f.number("SELECT COUNT(*) FROM attachment_link WHERE state='QUARANTINED'"));assertEquals(0,f.store().recover(f.scope,f.session.issue()))
            f.safety()
        }
    }
    @Test fun everyFaultBoundaryRollsBackOrReturnsAnAlreadyCommittedReplay() {
        Fixture(app).use {f->
            AttachmentPersistentStore.Boundary.entries.forEach {stop->
                val bytes=ByteArray(64) {5};val r=f.request(bytes)
                reject { f.store {if(it==stop)throw IOException("synthetic $stop")}.save(f.scope,f.session.issue(),r) {ByteArrayInputStream(bytes)} }
                f.reopen()
                val committed=stop==AttachmentPersistentStore.Boundary.COMMITTED
                assertEquals(if(committed)1L else 0L,f.number("SELECT COUNT(*) FROM attachment_document WHERE document_uuid='${r.document}' AND active_version_uuid IS NOT NULL"))
                assertEquals(if(committed)0 else 1,f.store().recover(f.scope,f.session.issue()));assertEquals(0,f.store().recover(f.scope,f.session.issue()))
                if(committed)f.store().save(f.scope,f.session.issue(),r) {error("Replay reopened input")}
                else reject { f.store().save(f.scope,f.session.issue(),r) {error("Aborted attempt reused") } }
            }
            f.safety()
        }
    }
    @Test fun conflictingUuidAndChangedInputNeverPublish() {
        Fixture(app).use {f->
            val bytes=byteArrayOf(1,2,3);val r=f.request(bytes)
            f.store().save(f.scope,f.session.issue(),r) {ByteArrayInputStream(bytes)}
            reject {f.store().save(f.scope,f.session.issue(),r.copy(title="different request")) {error("Conflicting replay opened input")}}
            val changed=f.request(bytes)
            reject {f.store().save(f.scope,f.session.issue(),changed) {ByteArrayInputStream(byteArrayOf(9,8,7))}}
            assertEquals(1L,f.number("SELECT COUNT(*) FROM attachment_version"));f.store().recover(f.scope,f.session.issue())
            f.safety()
        }
    }
    @Test fun interruptedSourceAndRevocationRetainBudgetAndOldVersion() {
        Fixture(app).use {f->
            val bytes=ByteArray(4096) {3};val r=f.request(bytes)
            reject {f.store().save(f.scope,f.session.issue(),r) {object:InputStream() {override fun read():Int=throw IOException("synthetic ENOSPC/interruption")}}}
            assertEquals(1L,f.number("SELECT SUM(stage_uses+active_uses) FROM attachment_journal"))
            f.store().recover(f.scope,f.session.issue())
            val cancelled=f.request(bytes)
            reject {f.store {if(it==AttachmentPersistentStore.Boundary.STAGED)f.session.revoke()}.save(f.scope,f.session.issue(),cancelled) {ByteArrayInputStream(bytes)}}
            f.session=AttachmentSession(UUID.fromString(f.scope.dataset),UUID.fromString(f.scope.epoch))
            assertEquals(1,f.store().recover(f.scope,f.session.issue()));assertEquals(0L,f.number("SELECT COUNT(*) FROM attachment_version"))
            assertEquals(2L,f.number("SELECT SUM(stage_uses+active_uses) FROM attachment_journal"));f.safety()
        }
    }
    @Test fun casConflictAndRetiredDatasetCannotPublishOrReattach() {
        Fixture(app).use {f->
            val bytes=byteArrayOf(7,6,5);val r=f.request(bytes);val first=f.store().save(f.scope,f.session.issue(),r) {ByteArrayInputStream(bytes)}
            val replacement=f.request(bytes,r.document,1)
            reject {f.store {if(it==AttachmentPersistentStore.Boundary.AUTHENTICATED) f.raw.execSQL("UPDATE attachment_document SET revision=revision+1 WHERE document_uuid=?",arrayOf(r.document.toString()))}.save(f.scope,f.session.issue(),replacement) {ByteArrayInputStream(bytes)}}
            f.raw.query("SELECT active_version_uuid FROM attachment_document").use {assertTrue(it.moveToFirst());assertEquals(first.version.toString(),it.getString(0))}
            f.store().recover(f.scope,f.session.issue())
            val generationConflict=f.request(bytes,r.document,2)
            reject {f.store {if(it==AttachmentPersistentStore.Boundary.AUTHENTICATED)f.raw.execSQL("UPDATE attachment_store SET generation=generation+1 WHERE store_state='CURRENT'")}.save(f.scope,f.session.issue(),generationConflict) {ByteArrayInputStream(bytes)}}
            f.store().recover(f.scope,f.session.issue())
            val old=f.scope
            val live=f.store().open(old,f.session.issue(),r.document)
            f.raw.beginTransaction();try {AttachmentIdentity.replaceStructuredDataset(f.raw);f.raw.setTransactionSuccessful()} finally {f.raw.endTransaction()}
            val denied=ByteArray(bytes.size) {9}
            reject {live.readAt(0,denied,0,denied.size)};assertArrayEquals(ByteArray(bytes.size),denied);live.close()
            reject {f.store().open(old,f.session.issue(),r.document)}
            val next=requireNotNull(AttachmentIdentity.current(f.raw));assertNotEquals(old.epoch,next.epoch)
            f.raw.beginTransaction();val nextRecord:UUID
            try {nextRecord=UUID.fromString(AttachmentIdentity.bind(f.raw,next,"APPOINTMENT",1));f.raw.setTransactionSuccessful()} finally {f.raw.endTransaction()}
            assertNotEquals(f.record,nextRecord)
            val fresh=AttachmentSession(UUID.fromString(next.dataset),UUID.fromString(next.epoch))
            try {reject {f.store().save(next,fresh.issue(),r.copy(record=nextRecord)) {error("Old operation UUID reused after restore")}}} finally {fresh.revoke()}
            assertEquals(1L,f.number("SELECT COUNT(*) FROM attachment_link WHERE state='QUARANTINED'"));f.safety()
        }
    }
    @Test fun missingKeyAndReplacedFileRefuseWithoutCreatingKeysOrDeletingUnknownBytes() {
        Fixture(app).use {f->
            val bytes=byteArrayOf(1,3,5,7)
            // Arrange every independent corruption target while the inventory is still valid.
            // Once a selected inode is replaced, new allocations must now fail closed.
            val r=f.request(bytes);val second=f.request(bytes);val wrong=f.request(bytes);val tampered=f.request(bytes)
            listOf(r,second,wrong,tampered).forEach {request->f.store().save(f.scope,f.session.issue(),request) {ByteArrayInputStream(bytes)}}
            val alias=f.raw.query("SELECT active_alias_uuid FROM attachment_journal WHERE operation_uuid=?",arrayOf(r.operation.toString())).use {assertTrue(it.moveToFirst());AttachmentDeviceKeys.Alias(UUID.fromString(it.getString(0)),AttachmentDeviceKeys.Role.ACTIVE).value}
            val keyStore=KeyStore.getInstance("AndroidKeyStore").apply {load(null)};keyStore.deleteEntry(alias)
            reject {f.store().open(f.scope,f.session.issue(),r.document)};assertFalse(keyStore.containsAlias(alias))
            val objectId=f.raw.query("SELECT object_uuid FROM attachment_journal WHERE operation_uuid=?",arrayOf(second.operation.toString())).use {assertTrue(it.moveToFirst());it.getString(0)}
            val target=File(f.directory,"attachment-objects-v1/$objectId");val retained=File(f.directory,"retained-original")
            assertTrue(target.renameTo(retained));target.writeBytes(byteArrayOf(9,9,9));assertTrue(target.setReadOnly())
            reject {f.store().open(f.scope,f.session.issue(),second.document)}
            assertEquals(0,f.store().recover(f.scope,f.session.issue()));assertTrue(target.exists());assertTrue(retained.exists())
            val wrongAlias=f.raw.query("SELECT active_alias_uuid FROM attachment_journal WHERE operation_uuid=?",arrayOf(wrong.operation.toString())).use {assertTrue(it.moveToFirst());AttachmentDeviceKeys.Alias(UUID.fromString(it.getString(0)),AttachmentDeviceKeys.Role.ACTIVE)}
            keyStore.deleteEntry(wrongAlias.value);AttachmentDeviceKeys().provisionNew(wrongAlias)
            reject {f.store().open(f.scope,f.session.issue(),wrong.document)}
            val tamperedId=f.raw.query("SELECT object_uuid FROM attachment_journal WHERE operation_uuid=?",arrayOf(tampered.operation.toString())).use {assertTrue(it.moveToFirst());it.getString(0)}
            val ciphertext=File(f.directory,"attachment-objects-v1/$tamperedId");assertTrue(ciphertext.setWritable(true,true))
            java.io.RandomAccessFile(ciphertext,"rw").use {it.seek(it.length()-1);val old=it.readByte();it.seek(it.length()-1);it.writeByte(old.toInt() xor 1)}
            assertTrue(ciphertext.setReadOnly());reject {f.store().open(f.scope,f.session.issue(),tampered.document)}
            assertEquals(4L,f.number("SELECT COUNT(*) FROM attachment_version WHERE status='CORRUPT'"))
            val aliases=AttachmentDeviceKeys().retainedAliases(10000)
            try {f.store().save(f.scope,f.session.issue(),f.request(bytes)) {error("Damaged inventory opened input")};fail("Damaged inventory accepted allocation")}
            catch(e:AttachmentStorageBudget.Rejected) {assertEquals(AttachmentStorageBudget.Refusal.INCOMPLETE_INVENTORY,e.reason)}
            assertEquals(4L,f.number("SELECT COUNT(*) FROM attachment_journal"))
            assertEquals(aliases,AttachmentDeviceKeys().retainedAliases(10000));f.safety()
        }
    }
    @Test fun noReplaceSymlinkCreationAndLiveWriterExclusionPreserveUnknownFiles() {
        Fixture(app).use {f->
            val bytes=byteArrayOf(2,4,6);val r=f.request(bytes)
            val victim=File(f.directory,"synthetic-unknown-owner").apply {writeBytes(byteArrayOf(8,8))}
            var link:File?=null
            reject {f.store {if(it==AttachmentPersistentStore.Boundary.PREPARED) {
                val objectId=f.raw.query("SELECT object_uuid FROM attachment_journal WHERE operation_uuid=?",arrayOf(r.operation.toString())).use {assertTrue(it.moveToFirst());it.getString(0)}
                link=File(f.directory,"attachment-objects-v1/$objectId")
                android.system.Os.symlink(victim.path,link!!.path)
            }}.save(f.scope,f.session.issue(),r) {ByteArrayInputStream(bytes)}}
            AttachmentOwnedFiles(f.context).locked().use {
                reject {f.store().recover(f.scope,f.session.issue())}
                // Reuse frozen Gate3 peer/oracle: an unsuccessful same-process probe must NOT drop the kernel lock.
                f.context.contentResolver.acquireUnstableContentProviderClient(Gate2PeerProvider.AUTHORITY)!!.use {peer->
                    val result=peer.call("leaseState",null,android.os.Bundle().apply {putString("lock",File(f.directory,"attachment-objects-v1/lock").path)})!!
                    assertNotEquals(android.os.Process.myPid(),result.getInt("pid"))
                    assertEquals("HELD_OTHER_PROCESS",result.getString("state"))
                }
            }
            assertEquals(1,f.store().recover(f.scope,f.session.issue()));assertArrayEquals(byteArrayOf(8,8),victim.readBytes())
            assertTrue(java.nio.file.Files.isSymbolicLink(link!!.toPath()));assertEquals(0L,f.number("SELECT COUNT(*) FROM attachment_version"));f.safety()
        }
    }
    companion object {
        private fun syntheticPdf():ByteArray {
            val output=java.io.ByteArrayOutputStream()
            fun write(value:String) {output.write(value.toByteArray(Charsets.US_ASCII))}
            write("%PDF-1.4\n")
            val offsets=mutableListOf<Int>()
            listOf("<</Type /Catalog /Pages 2 0 R>>","<</Type /Pages /Kids [3 0 R] /Count 1>>","<</Type /Page /Parent 2 0 R /MediaBox [0 0 72 72] /Resources <<>>>>").forEachIndexed {i,value->offsets+=output.size();write("${i+1} 0 obj\n$value\nendobj\n")}
            val start=output.size();write("xref\n0 4\n0000000000 65535 f \n")
            offsets.forEach {write(it.toString().padStart(10,'0')+" 00000 n \n")}
            write("trailer\n<</Size 4 /Root 1 0 R>>\nstartxref\n$start\n%%EOF\n")
            return output.toByteArray()
        }
        private fun readFully(reader:AttachmentScopedReader,out:ByteArray) {
            var offset=0
            while(offset<out.size) {val n=reader.readAt(offset.toLong(),out,offset,out.size-offset);assertTrue(n>0);offset+=n}
            assertEquals(-1,reader.readAt(out.size.toLong(),ByteArray(1),0,1))
        }
        private fun sha(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {"%02x".format(it.toInt() and 255)}
        private fun reject(block:()->Unit) {try {block();fail("Unsafe operation accepted")} catch(_:Exception) {}}
    }
}
