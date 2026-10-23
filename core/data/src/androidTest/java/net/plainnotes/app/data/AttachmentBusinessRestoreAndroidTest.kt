package net.plainnotes.app.data

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.time.Instant
import java.time.LocalTime
import java.util.UUID
import java.util.concurrent.Callable

/** Real synthetic clinical writes -> production capture -> admitted plan -> RAM SQLCipher rebuild
 * -> exact independent comparison + authenticated quarantine. The decoded transport below is only
 * a test source: no production portable writer, password/KDF, new-device wrapping or activation.
 */
class AttachmentBusinessRestoreAndroidTest {
    private val app get()=InstrumentationRegistry.getInstrumentation().targetContext
    internal fun build(f:AttachmentBackupPreparationAndroidTest.Fixture) {
        val repo=NotesRepository(object:DatabaseAccess(f.app) {override fun get(space:Space)=f.db})
        runBlocking {
            val med=repo.saveMedication(MedicationEntity(name="SYNTHETIC complete restore",molecule="E2",route="SUBLINGUAL",unit="MG",dose_per_intake=0.5,container_capacity=37.0,soon_alert_minutes=0,late_after_minutes=60,site_rotation=false,notifications_on=false,active=true,sort_order=0),
                "E2",net.plainnotes.app.domain.RuleKind.EVERY_N_DAYS,1,listOf(LocalTime.NOON),emptySet(),Instant.now().minusSeconds(86400))
            repo.unscheduled(med,Instant.now().minusSeconds(3600),0.5)
            repo.saveLab(LabValueEntity(analyte_code="E2",value=12.0,unit="pg/mL",sampled_utc=1000,sampled_zone="UTC"))
        }
        val dose=f.number("SELECT MAX(id) FROM dose_record");val lab=f.number("SELECT MAX(id) FROM lab_value")
        fun bind(kind:String,id:Long)=f.db.runInTransaction(Callable {UUID.fromString(AttachmentIdentity.bind(f.raw,f.scope,kind,id))})
        val records=listOf(f.record,bind("LAB",lab),bind("DOSE_RECORD",dose))
        val extra=runBlocking {listOf(
            repo.saveAppointment(AppointmentEntity(type="ENDO",at_utc=2000,at_zone="UTC",note="SYNTHETIC active",remind_minutes_before=0)),
            repo.saveAppointment(AppointmentEntity(type="ENDO",at_utc=3000,at_zone="UTC",note="SYNTHETIC future tombstone",remind_minutes_before=0))) }
        extra.forEach {bind("APPOINTMENT",it)}
        val pdf=ByteArrayOutputStream().also {out->val d=PdfDocument();try {
            val p=d.startPage(PdfDocument.PageInfo.Builder(200,200,1).create());p.canvas.drawText("Synthetic restore",10f,40f,Paint());d.finishPage(p);d.writeTo(out)
        }finally {d.close()}}.toByteArray()
        val image=java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jc1sAAAAASUVORK5CYII=")
        val bytes=listOf(pdf,image,ByteArray(70123) {(it*7).toByte()})
        val requests=bytes.mapIndexed {i,b->f.request(b,mime=listOf("application/pdf","image/png","application/octet-stream")[i]).copy(record=records[i])}
        requests.zip(bytes).forEach {(r,b)->f.store().save(f.scope,f.session.issue(),r) {ByteArrayInputStream(b)}}
        // Retain both the original PDF and a new immutable version; trash the binary document.
        f.store().save(f.scope,f.session.issue(),f.request(pdf,requests[0].document,1,"application/pdf")) {ByteArrayInputStream(pdf)}
        f.store().trash(f.scope,f.session.issue(),requests[2].document,1)
        runBlocking {
            repo.editRecord(dose,Instant.now().minusSeconds(3500),0.75);repo.deleteRecord(dose);repo.deleteLab(lab);repo.deleteAppointment(1)
            repo.deleteAppointment(extra[1])
            val trash=f.raw.query("SELECT id FROM trash_item WHERE kind='APPOINTMENT' AND ref=?",arrayOf(extra[1].toString())).use {check(it.moveToFirst());it.getLong(0)}
            assertTrue(repo.purgeTrash(trash)) // Existing clinical snapshot purge only; no ciphertext/key deletion.
        }
    }
    private fun plan(m:AttachmentBackupManifest,b:ByteArray,renumber:Boolean=false)=AttachmentRestorePreflight.inspect(m,b.copyOf(),m.versions.map {it.manifest},
        AttachmentRestorePreflight.Namespace(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID()),
        m.records.map {AttachmentBackupManifest.Target(it.uuid,it.kind,if(it.state=="PURGED")null else if(renumber)it.sourceId+10000 else it.sourceId)},
        m.records.filter {it.state!="PURGED"}.map {AttachmentRestorePreflight.TargetRow(it.uuid,it.kind,if(renumber)it.sourceId+10000 else it.sourceId,it.state)},emptySet(),
        AttachmentRestorePreflight.Resources(1024L*1024*1024,4096,100,100))
    private fun source(f:AttachmentBackupPreparationAndroidTest.Fixture,m:AttachmentBackupManifest)=object:AttachmentRestoreStaging.CiphertextSource {
        override fun members()=m.versions.map {it.manifest}
        override fun open(version:UUID)=FileInputStream(File(f.directory,"attachment-objects-v1/${m.versions.single {it.context.version==version}.context.objectId}"))
        override fun takeDek(version:UUID):ByteArray {
            val v=m.versions.single {it.context.version==version}
            return f.raw.query("SELECT j.operation_uuid,j.physical_generation,j.active_alias_uuid,e.wrapped_dek FROM attachment_journal j JOIN attachment_key_envelope e ON e.store_uuid=j.store_uuid AND e.version_uuid=j.version_uuid WHERE j.version_uuid=? AND e.state='AVAILABLE' AND e.wrap_epoch=1",arrayOf(version.toString())).use {c->
                check(c.moveToFirst());val role=AttachmentDeviceKeys.Role.ACTIVE
                AttachmentDeviceKeys().unwrapExisting(AttachmentDeviceKeys.Alias(UUID.fromString(c.getString(2)),role),
                    AttachmentDeviceKeys.Binding(v.context,UUID.fromString(f.scope.dataset),UUID.fromString(c.getString(1)),UUID.fromString(c.getString(0)),1,role),c.getBlob(3))
            }
        }
    }
    private fun business(f:AttachmentBackupPreparationAndroidTest.Fixture)=f.db.runInTransaction(Callable {MeasuredBackupJson.encode(f.raw,Instant.EPOCH)})
    private fun allRows(f:AttachmentBackupPreparationAndroidTest.Fixture):String {
        val tables=f.raw.query("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name").use {c->buildList {while(c.moveToNext())add(c.getString(0))}}
        return buildString {tables.forEach {t->append(org.json.JSONObject.quote(t))
            f.raw.query("SELECT * FROM \"${t.replace("\"","\"\"")}\" ORDER BY rowid").use {c->while(c.moveToNext()) {
                append('[');for(i in 0 until c.columnCount) {append(c.getType(i));append(':');append(if(c.isNull(i))"null" else if(c.getType(i)==android.database.Cursor.FIELD_TYPE_BLOB)android.util.Base64.encodeToString(c.getBlob(i),android.util.Base64.NO_WRAP) else org.json.JSONObject.quote(c.getString(i)));append(';')};append(']')
            }}
        }}
    }
    private fun reject(block:()->Unit) {try {block();fail("Unsafe candidate accepted")}catch(_:Exception){}}
    @Test fun genuineHistoryTrashAndTypedLinksRebuildInDifferentDatasetAndBorrowedBytesAreErased() {
        AttachmentBackupPreparationAndroidTest.Fixture(app).use {f->build(f)
            AttachmentBackupPreparation(f.context,f.db).capture(f.scope,f.session.issue(),created=Instant.EPOCH).use {snapshot->
                val m=snapshot.manifest();assertEquals(4,m.versions.size);assertEquals(5,m.records.size)
                assertEquals(setOf("DOSE_RECORD","LAB","APPOINTMENT"),m.records.map {it.kind.name}.toSet())
                assertEquals(3,m.records.count {it.state=="TRASHED"});assertEquals(1,m.records.count {it.state=="ACTIVE"});assertEquals(1,m.records.count {it.state=="PURGED"})
                assertTrue(m.links.all {it.state=="QUARANTINED"})
                snapshot.records {bytes->val original=bytes.copyOf();val unchanged=allRows(f);try {val p=plan(m,bytes)
                    AttachmentBusinessRestore(f.context,f.db).reconstruct(f.scope,f.session.issue(),p,bytes.copyOf(),source(f,m)).use {candidate->
                        assertFalse(candidate.activatable);assertFalse(candidate.objects.wholeArchiveAuthenticated)
                        assertEquals(AttachmentCandidateMetadata.normalized(m.copy(dataset=p.namespace.dataset,epoch=p.namespace.epoch)),candidate.manifest())
                        lateinit var borrowed:ByteArray
                        candidate.records {b->borrowed=b;assertArrayEquals(original,b)}
                        assertTrue(borrowed.all {it==0.toByte()})
                        assertEquals(4,candidate.objects.objects.size)
                        candidate.objects.objects.forEach {o->val v=m.versions.single {it.context.version==o.version}
                            assertArrayEquals(File(f.directory,"attachment-objects-v1/${v.context.objectId}").readBytes(),File(f.directory,"attachment-restore-quarantine-v1/${o.physicalFile}").readBytes())}
                        // Current library still has precisely the same IDs, historical content and ciphertext.
                        assertArrayEquals(original,business(f));assertEquals(f.scope,AttachmentIdentity.current(f.raw))
                        assertEquals(0L,f.number("SELECT feature_enabled FROM attachment_store WHERE store_state='CURRENT'"))
                    }
                    assertArrayEquals(original,business(f));assertEquals(unchanged,allRows(f))
                }finally {original.fill(0)}}
            }
        }
    }
    @Test fun missingHistoryCorruptionCancellationAndRenumberingNeverPublishAndPreserveOriginal() {
        AttachmentBackupPreparationAndroidTest.Fixture(app).use {f->build(f)
            AttachmentBackupPreparation(f.context,f.db).capture(f.scope,f.session.issue(),created=Instant.EPOCH).use {snapshot->val m=snapshot.manifest()
                snapshot.records {b->val unchanged=allRows(f);for(fault in listOf("MISSING","CORRUPT","CANCEL","RENUMBER","BYTES")) {
                    val cap=f.session.issue();val original=source(f,m);var opened=false
                    val source=object:AttachmentRestoreStaging.CiphertextSource by original {
                        override fun members()=if(fault=="MISSING")original.members().drop(1) else original.members()
                        override fun open(version:UUID):java.io.InputStream {opened=true
                            if(fault=="CANCEL")cap.close()
                            if(fault=="CORRUPT") {val bytes=original.open(version).use {it.readBytes()};bytes[bytes.lastIndex]=(bytes.last().toInt() xor 1).toByte();return ByteArrayInputStream(bytes)}
                            return original.open(version)
                        }
                    }
                    val owned=b.copyOf();if(fault=="BYTES")owned[0]=0
                    reject {AttachmentBusinessRestore(f.context,f.db).reconstruct(f.scope,cap,plan(m,b,fault=="RENUMBER"),owned,source)}
                    assertTrue(owned.all {it==0.toByte()});if(fault in setOf("MISSING","RENUMBER","BYTES"))assertFalse(opened)
                    assertArrayEquals(b,business(f));assertEquals(unchanged,allRows(f));assertEquals(f.scope,AttachmentIdentity.current(f.raw));cap.close()
                }}
            }
        }
    }
    @Test fun revocationAfterReconstructionWithholdsCandidateAndNeverActivates() {
        AttachmentBackupPreparationAndroidTest.Fixture(app).use {f->build(f)
            AttachmentBackupPreparation(f.context,f.db).capture(f.scope,f.session.issue(),created=Instant.EPOCH).use {snapshot->val m=snapshot.manifest()
                snapshot.records {b->val cap=f.session.issue();val candidate=AttachmentBusinessRestore(f.context,f.db).reconstruct(f.scope,cap,plan(m,b),b.copyOf(),source(f,m))
                    cap.close();reject {candidate.manifest()};reject {candidate.records {fail("Revoked bytes escaped")}}
                    assertArrayEquals(b,business(f));assertEquals(0L,f.number("SELECT feature_enabled FROM attachment_store WHERE store_state='CURRENT'"));candidate.close()
                }
            }
        }
    }
}
