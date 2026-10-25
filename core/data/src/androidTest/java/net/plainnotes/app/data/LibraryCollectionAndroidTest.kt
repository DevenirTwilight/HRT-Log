package net.plainnotes.app.data

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import net.plainnotes.app.domain.AttachmentSession
import org.junit.Assert.*
import org.junit.Before
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.io.FileInputStream
import java.security.KeyStore
import java.util.UUID

/** Real OFF facade/SQLCipher/Tink/journal/codecs; synthetic provider only, no process-local restore. */
class LibraryCollectionAndroidTest {
    private val app get()=InstrumentationRegistry.getInstrumentation().targetContext
    @Before fun isolatedWholeUid() {
        val ks=KeyStore.getInstance("AndroidKeyStore").apply {load(null)}
        assertTrue(ks.aliases().toList().none {it.startsWith("notes.obj.v1.") || it.startsWith("notes.gen.db.")})
    }
    private fun reject(block:()->Unit) {try {block();fail("Unsafe collection operation accepted")}catch(_:Exception){}}
    private fun password()="SYNTHETIC complete collection".toCharArray()
    private fun uri(name:String)=MedicalAttachmentOperationAndroidTest().uri(name)
    private fun compareRecords(left:ByteArray,right:ByteArray) {
        BackupJsonReader.indexed(left.copyOf()).use {a->BackupJsonReader.indexed(right.copyOf()).use {b->
            fun values(rows:BackupRows):List<String> =(0 until rows.length()).map {i->
                val row=rows.getJSONObject(i)
                row.keys().asSequence().sorted().joinToString("|") {key->
                    val value=row.get(key)
                    org.json.JSONObject.quote(key)+":"+when(value) {
                        org.json.JSONObject.NULL->"null"
                        is String->"s"+org.json.JSONObject.quote(value)
                        is Number->"n"+java.math.BigDecimal(value.toString()).stripTrailingZeros().toString()
                        else->error("Unexpected database scalar")
                    }
                }
            }.sorted()
            val version=RecordBackupCompatibility.readVersion(a.metadata)
            assertEquals(version,RecordBackupCompatibility.readVersion(b.metadata))
            val tables=RecordBackupCompatibility.tables(version)
            assertEquals(tables.toSet(),a.tables.keys());assertEquals(tables.toSet(),b.tables.keys())
            tables.forEach {t->assertEquals(t,values(a.tables.getJSONArray(t)),values(b.tables.getJSONArray(t)))}
        }}
    }
    @Test fun restoredLibraryAddsOriginalAndExportsAllLibrariesToIndependentNewDevice()=runBlocking {
        val first=File.createTempFile("synthetic-collection-a-",".cipher",app.cacheDir)
        val second=File.createTempFile("synthetic-collection-ab-",".cipher",app.cacheDir)
        val third=File.createTempFile("synthetic-collection-c-",".cipher",app.cacheDir)
        try {
            LibraryRestoreAndroidTest.RootFixture(app).use {f->
                val repo=NotesRepository(f.access).forGeneration(LibraryRequestScope("LEGACY",f.scope.dataset,f.scope.epoch)).pinnedTo(Space.PRIMARY)
                val ops=repo.medicalAttachments();val cap=f.session.issue()
                val appointment=MedicalAttachmentOperations.Target(MedicalAttachmentOperations.Kind.APPOINTMENT,2)
                val document=UUID.randomUUID()
                ops.import(cap,MedicalAttachmentOperations.Import(UUID.randomUUID(),document,appointment,"SYNTHETIC history"),uri("unknown"))
                ops.import(cap,MedicalAttachmentOperations.Import(UUID.randomUUID(),document,appointment,"SYNTHETIC history",1),uri("mime"))
                ops.trash(cap,document,2)
                repo.saveLab(LabValueEntity(analyte_code="E2",value=123.0,unit="pg/mL",sampled_utc=1000,sampled_zone="UTC"))
                val lab=MedicalAttachmentOperations.Target(MedicalAttachmentOperations.Kind.LAB,repo.labs().single().id)
                ops.import(cap,MedicalAttachmentOperations.Import(UUID.randomUUID(),UUID.randomUUID(),lab,"SYNTHETIC lab"),uri("pdfclaim"))
                ops.writeCollection(cap,password(),first.outputStream())
                val original=f.facts()
                MedicalCollectionReader.read(cap,password(),FileInputStream(first).channel).use {archive->
                    assertEquals(1,archive.index().libraries.size)
                    assertEquals(3,archive.manifest(archive.index().active).versions.size)
                    LibraryCollectionRestore(f.access).restore(f.scope,cap,UUID.randomUUID(),archive)
                }
                assertArrayEquals(original,f.facts()) // Legacy A was not overwritten.
                val current=NotesRepository(f.access);val newScope=current.currentGenerationScope()
                val session=AttachmentSession(UUID.fromString(newScope.dataset),UUID.fromString(newScope.epoch))
                try {
                    val newOps=current.forGeneration(newScope).pinnedTo(Space.PRIMARY).medicalAttachments()
                    val newCap=session.issue()
                    reject {runBlocking {ops.import(cap,MedicalAttachmentOperations.Import(UUID.randomUUID(),UUID.randomUUID(),appointment,"SYNTHETIC stale"),uri("unknown"))}}
                    val request=MedicalAttachmentOperations.Import(UUID.randomUUID(),UUID.randomUUID(),appointment,"SYNTHETIC after restore")
                    val attempts=coroutineScope {
                        listOf(request,request.copy(title="SYNTHETIC conflicting concurrent payload")).map {r->
                            async(Dispatchers.IO) {runCatching {newOps.import(newCap,r,uri("unknown"))}}
                        }.map {it.await()}
                    }
                    assertEquals("One UUID cannot produce two committed versions",1,attempts.count {it.isSuccess})
                    val receipt=attempts.single {it.isSuccess}.getOrThrow()
                    f.access.get(Space.PRIMARY).openHelper.writableDatabase.query("SELECT COUNT(*) FROM attachment_journal WHERE operation_uuid=?",arrayOf(request.operation.toString())).use {assertTrue(it.moveToFirst());assertEquals(1,it.getInt(0))}
                    assertEquals(receipt,newOps.reconcile(newCap,request.operation,appointment,request.document).receipt)
                    reject {runBlocking {newOps.import(newCap,request.copy(title="changed"),uri("failure"))}}
                    newOps.open(newCap,appointment,request.document).use {read->val bytes=ByteArray(8192);assertEquals(bytes.size,read.readAt(0,bytes,0,bytes.size));assertEquals(17,bytes[0].toInt())}
                    f.raw.query("SELECT state FROM library_write_reservation").use {assertTrue(it.moveToFirst());assertEquals("COMMITTED",it.getString(0));assertFalse(it.moveToNext())}
                    f.access.close(Space.PRIMARY) // Actual persisted key/DB handles, not an in-memory candidate.
                    newOps.writeCollection(newCap,password(),second.outputStream())
                    assertArrayEquals(original,f.facts())
                }finally {session.revoke()}
            }
            // Source device's exact fixture-owned keys/files are now gone. Target has a new
            // namespace/Keystore database key and an initially empty retained root.
            isolatedWholeUid()
            verifyNewTarget(second,third)
            if(InstrumentationRegistry.getArguments().getString("collection_transfer")=="export") {
                val transfer=File(app.filesDir,"synthetic-collection-transfer.cipher")
                check(!transfer.exists());second.inputStream().use {input->transfer.outputStream().use {out->input.copyTo(out);out.fd.sync()}}
                android.system.Os.chmod(transfer.path,384) // Ciphertext only, no URI/key/database transfer.
            }

        }finally {listOf(first,second,third).forEach {check(it.delete())}}
    }
    @Test fun freshDeviceRestoresOnlyTransferredEncryptedCollection()=runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("collection_transfer")=="restore")
        assertTrue("Fresh target UID must have none of the source keys",KeyStore.getInstance("AndroidKeyStore").apply {load(null)}.aliases().toList().isEmpty())
        val input=File(app.filesDir,"synthetic-collection-transfer.cipher")
        val output=File.createTempFile("synthetic-fresh-c-",".cipher",app.cacheDir)
        try {verifyNewTarget(input,output)}finally {check(output.delete());check(input.delete())}
    }
    private suspend fun verifyNewTarget(second:File,third:File) {
            LibraryRestoreAndroidTest.RootFixture(app,seed=false).use {target->
                val original=target.facts();val cap=target.session.issue()
                MedicalCollectionReader.read(cap,password(),FileInputStream(second).channel).use {archive->
                    assertEquals(2,archive.index().libraries.size)
                    assertEquals(listOf(3,4),archive.index().libraries.map {it.members.size}.sorted())
                    val operation=UUID.randomUUID()
                    val restored=LibraryCollectionRestore(target.access).restore(target.scope,cap,operation,archive)
                    assertEquals(2,restored.libraries)
                    assertArrayEquals(original,target.facts())
                    val fresh=NotesRepository(target.access);val scope=fresh.currentGenerationScope()
                    val session=AttachmentSession(UUID.fromString(scope.dataset),UUID.fromString(scope.epoch))
                    try {
                        target.access.close(Space.PRIMARY)
                        fresh.forGeneration(scope).pinnedTo(Space.PRIMARY).medicalAttachments().writeCollection(session.issue(),password(),third.outputStream())
                        MedicalCollectionReader.read(session.issue(),password(),FileInputStream(third).channel).use {copy->
                            assertEquals(3,copy.index().libraries.size) // Do not omit the truly empty target root.
                            val empty=copy.index().libraries.single {it.dataset.toString()==target.scope.dataset}
                            assertEquals(0L,empty.businessRows);assertTrue(empty.members.isEmpty())
                            archive.index().libraries.forEach {source->
                                val destination=target.raw.query("""SELECT c.dataset_uuid,c.epoch_uuid FROM library_collection_member m JOIN library_candidate c USING(operation_uuid)
                                    WHERE m.collection_uuid=? AND m.library_uuid=?""",arrayOf(operation.toString(),source.id.toString())).use {c->assertTrue(c.moveToFirst());UUID.fromString(c.getString(0)) to UUID.fromString(c.getString(1))}
                                val copied=copy.index().libraries.single {it.dataset==destination.first}
                                assertEquals(source.role,copied.role)
                                assertEquals(AttachmentCandidateMetadata.normalized(archive.manifest(source.id).copy(dataset=destination.first,epoch=destination.second)),AttachmentCandidateMetadata.normalized(copy.manifest(copied.id)))
                                archive.records(source.id) {left->copy.records(copied.id) {right->compareRecords(left,right)}}
                                // Reader has independently authenticated every original after source-key loss.
                                assertEquals(source.members.map {it.sha256},copied.members.map {it.sha256})
                            }
                        }
                    }finally {session.revoke()}
                }
            }
    }

}
