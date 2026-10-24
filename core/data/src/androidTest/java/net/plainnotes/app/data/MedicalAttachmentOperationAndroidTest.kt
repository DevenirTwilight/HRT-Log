package net.plainnotes.app.data

import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import net.plainnotes.app.domain.AttachmentSession
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.util.UUID

/** Facade integration only; all cryptography, journal, backup and validation are production code. */
class MedicalAttachmentOperationAndroidTest {
    private val app get()=InstrumentationRegistry.getInstrumentation().targetContext
    internal fun uri(name:String)=Uri.parse("content://net.plainnotes.app.data.test.synthetic.gate2/$name")
    internal fun scope(f:LibraryRestoreAndroidTest.RootFixture)=LibraryRequestScope("LEGACY",f.scope.dataset,f.scope.epoch)
    private fun reject(block:()->Unit) {try {block();fail("Unsafe operation accepted")}catch(_:Exception){}}
    @Test fun explicitAppointmentLabHistoryTrashAndFormalArchiveAreComplete()=runBlocking {
        LibraryRestoreAndroidTest.RootFixture(app).use {f->
            val repo=NotesRepository(f.access).forGeneration(scope(f)).pinnedTo(Space.PRIMARY)
            val service=repo.medicalAttachments();val target=MedicalAttachmentOperations.Target(MedicalAttachmentOperations.Kind.APPOINTMENT,2)
            val r=MedicalAttachmentOperations.Import(UUID.randomUUID(),UUID.randomUUID(),target,"SYNTHETIC")
            val cap=f.session.issue()
            try {
                val saved=service.import(cap,r,uri("unknown"));assertEquals(saved,service.reconcile(cap,r.operation,target,r.document).receipt)
                reject {runBlocking {service.import(cap,r,uri("failure"))}} // Cannot reopen provider or duplicate links.
                reject {runBlocking {service.reconcile(cap,r.operation,target,UUID.randomUUID())}}
                val opened=service.open(cap,target,r.document);val bytes=ByteArray(8192);assertEquals(8192,opened.readAt(0,bytes,0,bytes.size));assertEquals(17,bytes[0].toInt())
                service.import(cap,r.copy(operation=UUID.randomUUID(),revision=1),uri("mime"))
                reject {opened.readAt(0,bytes,0,bytes.size)};assertTrue(bytes.all {it==0.toByte()});opened.close()
                repo.saveLab(LabValueEntity(analyte_code="E2",value=120.0,unit="pg/mL",sampled_utc=1000,sampled_zone="UTC"))
                val lab=repo.labs().single().id
                val labTarget=MedicalAttachmentOperations.Target(MedicalAttachmentOperations.Kind.LAB,lab)
                val labRequest=MedicalAttachmentOperations.Import(UUID.randomUUID(),UUID.randomUUID(),labTarget,"SYNTHETIC lab")
                service.import(cap,labRequest,uri("pdfclaim"));assertEquals(1,service.documents(cap,labTarget).size)
                val current=service.open(cap,target,r.document);service.trash(cap,r.document,2)
                reject {current.readAt(0,bytes,0,bytes.size)};current.close()
                service.restore(cap,r.document,3)
                assertEquals("QUARANTINED",service.documents(cap,target).single().linkState)
                reject {runBlocking {service.open(cap,target,r.document)}} // Restore is not automatic reassociation.
                assertEquals(MedicalAttachmentOperations.Export.COMPLETE,service.exportEligibility(cap))
                val out=ByteArrayOutputStream();val password="SYNTHETIC facade archive".toCharArray()
                service.writeArchive(cap,password.copyOf(),out)
                val file=File.createTempFile("synthetic-facade-",".cipher",app.cacheDir)
                try {file.writeBytes(out.toByteArray());MedicalArchiveReader.read(cap,password,FileInputStream(file).channel).use {a->
                    assertTrue(a.wholeArchiveAuthenticated);assertEquals(3,a.manifest().versions.size)
                }}finally {file.delete();password.fill('\u0000')}
                assertEquals(0L,f.raw.query("SELECT feature_enabled FROM attachment_store WHERE store_state='CURRENT'").use {it.moveToFirst();it.getLong(0)})
            }finally {cap.close()}
        }
    }
    @Test fun hostileInputCapacityUnknownAndLostAuthorityRefuseWithoutFalseCommit()=runBlocking {
        LibraryRestoreAndroidTest.RootFixture(app).use {f->
            val target=MedicalAttachmentOperations.Target(MedicalAttachmentOperations.Kind.APPOINTMENT,2)
            val service=NotesRepository(f.access).forGeneration(scope(f)).pinnedTo(Space.PRIMARY).medicalAttachments()
            val before=f.facts();val cap=f.session.issue()
            try {
                for(path in listOf("failure","oversize")) {
                    val r=MedicalAttachmentOperations.Import(UUID.randomUUID(),UUID.randomUUID(),target,"SYNTHETIC")
                    reject {runBlocking {service.import(cap,r,uri(path))}}
                    assertEquals(MedicalAttachmentOperations.Phase.NOT_FOUND,service.reconcile(cap,r.operation).phase)
                }
                val denied=MedicalAttachmentOperations(f.access,scope(f),{},storageBudget=AttachmentStorageBudget(minimumFreeBytes=Long.MAX_VALUE-100000000))
                val r=MedicalAttachmentOperations.Import(UUID.randomUUID(),UUID.randomUUID(),target,"SYNTHETIC")
                reject {runBlocking {denied.import(cap,r,uri("unknown"))}};assertTrue(service.documents(cap,target).isEmpty())
                assertArrayEquals(before,f.facts())
                val committed=service.import(cap,r.copy(operation=UUID.randomUUID()),uri("unknown"))
                val previous=service.open(cap,target,committed.document)
                val objectId=f.raw.query("SELECT object_uuid FROM attachment_journal WHERE operation_uuid=?",arrayOf(committed.operation.toString())).use {it.moveToFirst();it.getString(0)}
                val objectFile=File(f.directory,"attachment-objects-v1/$objectId")
                val sealed=android.system.Os.lstat(objectFile.path)
                assertTrue(android.system.OsConstants.S_ISREG(sealed.st_mode));assertEquals(android.os.Process.myUid(),sealed.st_uid)
                assertEquals("Production ciphertext remains sealed read-only",256,sealed.st_mode and 511)
                // Deliberate attacker mutation of this test-owned object only. Restore the exact
                // production mode before testing authentication, without replacing its inode.
                android.system.Os.chmod(objectFile.path,384)
                try {java.io.RandomAccessFile(objectFile,"rw").use {file->val at=file.length()-1;file.seek(at);val b=file.read();file.seek(at);file.write(b xor 1);file.fd.sync()}}
                finally {android.system.Os.chmod(objectFile.path,sealed.st_mode and 511)}
                val changed=android.system.Os.lstat(objectFile.path)
                assertEquals(sealed.st_dev,changed.st_dev);assertEquals(sealed.st_ino,changed.st_ino)
                assertEquals(sealed.st_mode,changed.st_mode)
                reject {runBlocking {service.open(cap,target,committed.document)}}
                val blocked=ByteArray(16) {7};reject {previous.readAt(0,blocked,0,blocked.size)};assertTrue(blocked.all {it==0.toByte()});previous.close()
                assertEquals(MedicalAttachmentOperations.Export.REFUSED,service.exportEligibility(cap))
                val unknown=File(f.directory,"attachment-objects-v1/${UUID.randomUUID()}");unknown.writeBytes(byteArrayOf(4,5,6))
                assertEquals(MedicalAttachmentOperations.Export.UNKNOWN,service.exportEligibility(cap))
                val output=ByteArrayOutputStream();reject {runBlocking {service.writeArchive(cap,"synthetic".toCharArray(),output)}};assertEquals(0,output.size())
                f.session.revoke();reject {runBlocking {service.documents(cap,target)}}
            }finally {cap.close()}
        }
    }
    @Test fun realGenerationSwitchRejectsOldFacadeAndRetainedSetExport()=runBlocking {
        LibraryRestoreAndroidTest.SourceBundle(app).use {source->LibraryRestoreAndroidTest.RootFixture(app).use {f->
            val target=MedicalAttachmentOperations.Target(MedicalAttachmentOperations.Kind.APPOINTMENT,2)
            val service=NotesRepository(f.access).forGeneration(scope(f)).pinnedTo(Space.PRIMARY).medicalAttachments()
            val cap=f.session.issue()
            try {
                val r=MedicalAttachmentOperations.Import(UUID.randomUUID(),UUID.randomUUID(),target,"SYNTHETIC old owner")
                val saved=service.import(cap,r,uri("unknown"));val read=service.open(cap,target,saved.document)
                MedicalArchiveReader.read(cap,source.password(),FileInputStream(source.file).channel).use {archive->LibraryRestoreCoordinator(f.access).restore(f.scope,cap,UUID.randomUUID(),archive)}
                reject {runBlocking {service.documents(cap,target)}}
                val destination=ByteArray(16) {7};reject {read.readAt(0,destination,0,destination.size)};assertTrue(destination.all {it==0.toByte()});read.close()
                val fresh=NotesRepository(f.access);val current=fresh.currentGenerationScope()
                val newSession=AttachmentSession(UUID.fromString(current.dataset),UUID.fromString(current.epoch));val newCap=newSession.issue()
                try {
                    val ops=fresh.forGeneration(current).pinnedTo(Space.PRIMARY).medicalAttachments()
                    val active=MedicalAttachmentOperations.Target(MedicalAttachmentOperations.Kind.APPOINTMENT,fresh.forGeneration(current).appointments().first().id)
                    // A fresh explicit target has no attachment identity yet. Refusal must not
                    // bind it, read even a failing provider, reserve aliases or append a journal.
                    val isolated=fresh.forGeneration(current).pinnedTo(Space.PRIMARY)
                    isolated.saveAppointment(AppointmentEntity(9876,"SYNTHETIC admission target",2000,"UTC",remind_minutes_before=0))
                    val unbound=MedicalAttachmentOperations.Target(MedicalAttachmentOperations.Kind.APPOINTMENT,9876)
                    val selected=f.access.get(Space.PRIMARY);val sql=selected.openHelper.writableDatabase
                    val business=selected.runInTransaction(java.util.concurrent.Callable {MeasuredBackupJson.encode(sql,java.time.Instant.EPOCH)})
                    val legacy=f.facts()
                    fun count(table:String)=sql.query("SELECT COUNT(*) FROM $table").use {assertTrue(it.moveToFirst());it.getLong(0)}
                    val tables=listOf("attachment_record_identity","attachment_operation","attachment_journal","attachment_document","attachment_version","attachment_link","attachment_key_envelope")
                    val metadata=tables.associateWith(::count)
                    val objects=File(DatabaseAccess.storageContext(selected,f.context).noBackupFilesDir,"attachment-objects-v1").list()?.sorted()
                    val aliases=AttachmentDeviceKeys().retainedAliases(4096)
                    fun providerOpens()=requireNotNull(app.contentResolver.call(uri("unknown"),"openCount",null,null)).getInt("count")
                    val opened=providerOpens()
                    val refusedOperation=UUID.randomUUID()
                    var refused=false
                    try {ops.import(newCap,MedicalAttachmentOperations.Import(refusedOperation,UUID.randomUUID(),unbound,"SYNTHETIC managed refusal"),uri("failure"))}
                    catch(e:IllegalStateException) {assertTrue(e.message.orEmpty().contains("joint generation admission"));refused=true}
                    assertTrue("Restored generations must retain their existing write admission",refused)
                    assertEquals(opened,providerOpens())
                    assertEquals(metadata,tables.associateWith(::count))
                    assertArrayEquals(business,selected.runInTransaction(java.util.concurrent.Callable {MeasuredBackupJson.encode(sql,java.time.Instant.EPOCH)}))
                    assertArrayEquals(legacy,f.facts())
                    assertEquals(objects,File(DatabaseAccess.storageContext(selected,f.context).noBackupFilesDir,"attachment-objects-v1").list()?.sorted())
                    assertEquals(MedicalAttachmentOperations.Phase.NOT_FOUND,ops.reconcile(newCap,refusedOperation).phase)
                    assertEquals(aliases,AttachmentDeviceKeys().retainedAliases(4096))
                    assertEquals(MedicalAttachmentOperations.Export.REFUSED,ops.exportEligibility(newCap))
                    val output=ByteArrayOutputStream();reject {runBlocking {ops.writeArchive(newCap,source.password(),output)}};assertEquals(0,output.size())
                }finally {newCap.close();newSession.revoke()}
            }finally {cap.close()}
        }}
    }
}
