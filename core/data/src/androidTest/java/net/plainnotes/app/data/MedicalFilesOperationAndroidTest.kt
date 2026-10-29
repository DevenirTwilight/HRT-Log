package net.plainnotes.app.data

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import net.plainnotes.app.domain.AttachmentSession
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.FileInputStream
import java.util.UUID

/** Real SQLCipher/Tink/Keystore/journal/collection restore. Synthetic provider bytes only. */
class MedicalFilesOperationAndroidTest {
    private val app get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun reject(block:()->Unit) {try {block();fail("Unproven identity accepted")}catch(_:Exception){}}
    @Test fun oneOriginalFivePanelMembersStandaloneMultiLinkRecycleAndAuthenticatedRestore()=runBlocking {
        LibraryRestoreAndroidTest.RootFixture(app).use {f->
            val repo=NotesRepository(f.access).forGeneration(MedicalAttachmentOperationAndroidTest().scope(f)).pinnedTo(Space.PRIMARY)
            val values=listOf("E2" to "pg/mL","T" to "ng/dL","FSH" to "IU/L","AST" to "U/L","ALT" to "U/L")
            val panel=repo.saveLabs(values.map {(code,unit)->LabValueEntity(analyte_code=code,value=12.0,unit=unit,sampled_utc=1000,sampled_zone="UTC")})
            val members=repo.labs().filter {it.panel_id==panel.id};assertEquals(5,members.size)
            val ops=repo.medicalAttachments();val cap=f.session.issue()
            try {
                val standalone=MedicalAttachmentOperations.Import(UUID.randomUUID(),UUID.randomUUID(),null,"SYNTHETIC one report")
                ops.import(cap,standalone,MedicalAttachmentOperationAndroidTest().uri("pdfok"))
                assertEquals(standalone.document,ops.libraryDocuments(cap).single().document)
                assertTrue(ops.relationships(cap,standalone.document).isEmpty())
                // MED-04: what the user typed is its own small write, compare-and-set on its own revision, and never touches the original's revision.
                val typed=MedicalAttachmentOperations.Detail("LAB_REPORT","2026-10-07","SYNTHETIC clinic")
                assertTrue(ops.details(cap).isEmpty())
                assertEquals(1L,ops.saveDetail(cap,standalone.document,0,typed))
                assertEquals(typed.copy(revision=1),ops.details(cap).getValue(standalone.document))
                reject {runBlocking {ops.saveDetail(cap,standalone.document,0,typed)}}
                reject {runBlocking {ops.saveDetail(cap,UUID.randomUUID(),0,typed)}}
                assertEquals(2L,ops.saveDetail(cap,standalone.document,1,typed.copy(institution=null)))
                assertEquals(1L,ops.libraryDocuments(cap).single().revision)
                val target=MedicalAttachmentOperations.Target(MedicalAttachmentOperations.Kind.LAB_PANEL,panel.id,UUID.fromString(panel.uuid))
                val frozen=ops.resolveTarget(cap,target)
                val link=ops.associate(cap,UUID.randomUUID(),frozen,standalone.document,1)
                assertEquals(MedicalAttachmentOperations.Phase.COMMITTED,link.phase)
                for(member in members)assertEquals(standalone.document,ops.documents(cap,target.copy(memberLabId=member.id)).single().document)
                assertEquals(panel.id,members.first().id)
                val one=MedicalAttachmentOperations.Target(MedicalAttachmentOperations.Kind.LAB,members.first().id)
                assertNotEquals(frozen.expectedUuid,ops.resolveTarget(cap,one).expectedUuid)
                assertTrue(ops.documents(cap,one).isEmpty()) // inherited report is not rewritten as a single-result attachment
                reject {runBlocking {ops.documents(cap,target.copy(expectedUuid=UUID.randomUUID()))}}
                val visit=ops.resolveTarget(cap,MedicalAttachmentOperations.Target(MedicalAttachmentOperations.Kind.APPOINTMENT,2))
                val operation=UUID.randomUUID();val second=ops.associate(cap,operation,visit,standalone.document,1)
                assertEquals(second,ops.associate(cap,operation,visit,standalone.document,1))
                reject {runBlocking {ops.associate(cap,operation,frozen,standalone.document,1)}}
                fun count(table:String)=f.raw.query("SELECT COUNT(*) FROM $table").use {it.moveToFirst();it.getInt(0)}
                assertEquals(1,count("attachment_document"));assertEquals(1,count("attachment_version"));assertEquals(1,count("attachment_journal"));assertEquals(2,ops.libraryDocuments(cap).single().linkCount)
                val relationship=ops.relationships(cap,standalone.document).single {it.target.kind==MedicalAttachmentOperations.Kind.APPOINTMENT}
                ops.associate(cap,UUID.randomUUID(),visit,standalone.document,1,relationship)
                assertTrue(ops.documents(cap,visit).isEmpty());assertEquals(1,ops.documents(cap,target).size)
                ops.trash(cap,standalone.document,1)
                reject {runBlocking {ops.saveDetail(cap,standalone.document,2,typed)}} // a file in the bin is not edited
                assertTrue(ops.libraryDocuments(cap).isEmpty());assertEquals("TRASHED",ops.libraryDocuments(cap,true).single().state)
                ops.restore(cap,standalone.document,2)
                assertTrue(ops.relationships(cap,standalone.document).isEmpty())
                assertTrue(ops.documents(cap,target).isEmpty()) // never revive quarantined associations
                ops.associate(cap,UUID.randomUUID(),frozen,standalone.document,3)
                // Rename the single retained original after its lifecycle/history/link operations.
                // Metadata CAS must not rewrite the import request digest, ciphertext/key rows or any link.
                fun immutableRows(table:String)=f.raw.query("SELECT * FROM $table ORDER BY rowid").use {c->
                    buildList {while(c.moveToNext())add((0 until c.columnCount).map {i->when(c.getType(i)) {
                        android.database.Cursor.FIELD_TYPE_NULL->null
                        android.database.Cursor.FIELD_TYPE_BLOB->c.getBlob(i).contentToString()
                        else->c.getString(i)
                    }})}
                }
                val retained=listOf("attachment_version","attachment_key_envelope","attachment_link","attachment_journal","attachment_operation")
                val beforeRename=retained.associateWith(::immutableRows)
                val priorReceipt=ops.reconcile(cap,standalone.operation,document=standalone.document)
                val renamed="2026年10月雌二醇化验报告.pdf"
                val renameReceipt=ops.saveFileInfo(cap,standalone.document,3,2,renamed,typed.copy(institution=null))
                assertEquals(4L,renameReceipt.documentRevision);assertEquals(3L,renameReceipt.detailRevision)
                assertEquals(beforeRename,retained.associateWith(::immutableRows))
                assertEquals(priorReceipt,ops.reconcile(cap,standalone.operation,document=standalone.document))
                assertEquals(renamed,ops.libraryDocuments(cap).single().title)
                for(member in members)assertEquals(renamed,ops.documents(cap,target.copy(memberLabId=member.id)).single().title)
                fun fileRows()=immutableRows("attachment_document") to immutableRows(MedicalFileDetailSchema.TABLE)
                val savedNames=fileRows()
                for(invalid in listOf("", "   ", "x".repeat(257), "bad\nname", "bad\u007fname"))
                    reject {runBlocking {ops.saveFileInfo(cap,standalone.document,4,3,invalid,typed)}}
                reject {runBlocking {ops.saveFileInfo(cap,standalone.document,3,3,"stale document",typed)}}
                reject {runBlocking {ops.saveFileInfo(cap,standalone.document,4,2,"stale detail",typed)}}
                assertEquals(savedNames,fileRows())
                f.raw.execSQL("CREATE TRIGGER synthetic_file_info_abort BEFORE UPDATE ON ${MedicalFileDetailSchema.TABLE} BEGIN SELECT RAISE(ABORT,'synthetic metadata interruption'); END")
                try {reject {runBlocking {ops.saveFileInfo(cap,standalone.document,4,3,"must roll back",typed)}}}
                finally {f.raw.execSQL("DROP TRIGGER synthetic_file_info_abort")}
                assertEquals(savedNames,fileRows());assertEquals(beforeRename,retained.associateWith(::immutableRows))
                val unlinked=standalone.copy(operation=UUID.randomUUID(),document=UUID.randomUUID(),title="SYNTHETIC kept unlinked")
                ops.import(cap,unlinked,MedicalAttachmentOperationAndroidTest().uri("unknown"))
                val file=File.createTempFile("synthetic-med03-",".cipher",app.cacheDir)
                try {
                    ops.writeCollection(cap,"SYNTHETIC med03 archive".toCharArray(),file.outputStream())
                    MedicalCollectionReader.read(cap,"SYNTHETIC med03 archive".toCharArray(),FileInputStream(file).channel).use {archive->
                        assertEquals(2,archive.manifest(archive.index().active).documents.size)
                        assertEquals(renamed,archive.manifest(archive.index().active).documents.single {it.uuid==standalone.document}.title)
                        assertEquals(1,archive.manifest(archive.index().active).records.count {it.kind==AttachmentBackupManifest.Kind.LAB_PANEL})
                        LibraryCollectionRestore(f.access).restore(f.scope,cap,UUID.randomUUID(),archive)
                    }
                    val fresh=NotesRepository(f.access);val scope=fresh.currentGenerationScope()
                    val session=AttachmentSession(UUID.fromString(scope.dataset),UUID.fromString(scope.epoch))
                    try {
                        val restored=fresh.forGeneration(scope).pinnedTo(Space.PRIMARY).medicalAttachments();val newCap=session.issue()
                        assertEquals(2,restored.libraryDocuments(newCap).size)
                        assertEquals(typed.copy(institution=null,revision=3),restored.details(newCap).getValue(standalone.document))
                        assertEquals(renamed,restored.libraryDocuments(newCap).single {it.document==standalone.document}.title)
                        assertNull(restored.details(newCap)[unlinked.document])
                        for(member in members)assertEquals(standalone.document,restored.documents(newCap,target.copy(memberLabId=member.id)).single().document)
                        restored.open(newCap,target,standalone.document).use {reader->val b=ByteArray(5);assertEquals(5,reader.readAt(0,b,0,5));assertEquals("%PDF-",String(b,Charsets.US_ASCII))}
                        assertTrue(restored.relationships(newCap,unlinked.document).isEmpty())
                        val activeRepo=fresh.forGeneration(scope).pinnedTo(Space.PRIMARY)
                        val oldMember=target.copy(memberLabId=members.first().id)
                        val read=restored.open(newCap,oldMember,standalone.document)
                        activeRepo.removeFromLabPanel(members.first().id)
                        val blocked=ByteArray(5){7}
                        reject {read.readAt(0,blocked,0,blocked.size)};assertTrue(blocked.all {it==0.toByte()});read.close()
                        assertEquals(1,restored.documents(newCap,target.copy(memberLabId=members.last().id)).size)
                        activeRepo.deleteLabPanel(panel.id)
                        reject {runBlocking {restored.documents(newCap,target)}}
                        val trashed=activeRepo.trash().single {it.kind==Trash.LAB_PANEL}
                        activeRepo.restoreTrash(trashed.id)
                        assertTrue(restored.documents(newCap,target).isEmpty())
                        assertEquals(2,restored.libraryDocuments(newCap).size)
                        restored.associate(newCap,UUID.randomUUID(),restored.resolveTarget(newCap,target),standalone.document,4)
                        assertEquals(1,restored.documents(newCap,target).size)
                        reject {runBlocking {ops.libraryDocuments(cap)}}
                        reject {runBlocking {ops.saveFileInfo(cap,standalone.document,4,3,"old scope must not rename",typed)}}
                        assertEquals(renamed,restored.libraryDocuments(newCap).single {it.document==standalone.document}.title)
                    }finally {session.revoke()}
                }finally {file.delete()}
            }finally {cap.close()}
        }
    }
}
