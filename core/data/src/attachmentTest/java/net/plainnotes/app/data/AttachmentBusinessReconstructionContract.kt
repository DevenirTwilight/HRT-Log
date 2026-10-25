package net.plainnotes.app.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalTime
import java.util.UUID
import java.util.concurrent.Callable

/** Production insertion/original full oracle/exact row comparison, on real Room SQL in both suites.
 * Crypto transport is exercised separately by AttachmentBusinessRestoreAndroidTest.
 */
abstract class AttachmentBusinessReconstructionContract {
    private inline fun <T> NotesDatabase.use(block:(NotesDatabase)->T):T = try {block(this)} finally {close()}
    protected abstract fun factory():SupportSQLiteOpenHelper.Factory
    private val context:Context get()=ApplicationProvider.getApplicationContext()
    private fun database()=Room.inMemoryDatabaseBuilder(context,NotesDatabase::class.java)
        .openHelperFactory(factory()).addCallback(SchemaGuards).allowMainThreadQueries().build().also {
            it.openHelper.writableDatabase.execSQL("PRAGMA temp_store=MEMORY")
        }
    private fun fixture(db:NotesDatabase):ByteArray {
        val repo=NotesRepository(object:DatabaseAccess(context) {override fun get(space:Space)=db})
        runBlocking {
            val med=repo.saveMedication(MedicationEntity(name="SYNTHETIC reconstruction",molecule="E2",route="SUBLINGUAL",unit="MG",dose_per_intake=0.5,container_capacity=37.0,soon_alert_minutes=0,late_after_minutes=60,site_rotation=false,notifications_on=false,active=true,sort_order=0),
                "E2",net.plainnotes.app.domain.RuleKind.EVERY_N_DAYS,1,listOf(LocalTime.NOON),emptySet(),Instant.now().minusSeconds(86400))
            repo.unscheduled(med,Instant.now().minusSeconds(3600),0.5)
            val id=db.openHelper.writableDatabase.query("SELECT MAX(id) FROM dose_record").use {check(it.moveToFirst());it.getLong(0)}
            repo.editRecord(id,Instant.now().minusSeconds(3500),0.75)
            repo.deleteRecord(id)
            val ap=repo.saveAppointment(AppointmentEntity(type="ENDO",at_utc=1000,at_zone="UTC",note="SYNTHETIC retained visit",remind_minutes_before=0))
            repo.deleteAppointment(ap)
            repo.saveLab(LabValueEntity(analyte_code="E2",value=12.0,unit="pg/mL",sampled_utc=1000,sampled_zone="UTC"))
            val lab=db.openHelper.writableDatabase.query("SELECT MAX(id) FROM lab_value").use {check(it.moveToFirst());it.getLong(0)}
            repo.deleteLab(lab)
        }
        return db.runInTransaction(Callable {MeasuredBackupJson.encode(db.openHelper.writableDatabase,Instant.EPOCH)})
    }
    private fun manifest(bytes:ByteArray):AttachmentBackupManifest {
        val tables=JSONObject(String(bytes,Charsets.UTF_8)).getJSONObject("tables")
        val trash=tables.getJSONArray("trash_item")
        val records=(0 until trash.length()).map {i->val r=trash.getJSONObject(i)
            val kind=when(r.getString("kind")) {"RECORD"->AttachmentBackupManifest.Kind.DOSE_RECORD;"APPOINTMENT"->AttachmentBackupManifest.Kind.APPOINTMENT;"LAB"->AttachmentBackupManifest.Kind.LAB;else->error("Unexpected synthetic trash")}
            AttachmentBackupManifest.Record(UUID.randomUUID(),kind,r.getString("ref").toLong(),"TRASHED",r.getLong("id"),sha(r.getString("payload_json").toByteArray()))
        }
        return AttachmentBackupManifest(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),records,emptyList(),emptyList(),emptyList())
    }
    private fun plan(m:AttachmentBackupManifest,b:ByteArray)=AttachmentRestorePreflight.inspect(m,b.copyOf(),emptyList(),
        AttachmentRestorePreflight.Namespace(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID()),
        m.records.map {AttachmentBackupManifest.Target(it.uuid,it.kind,it.sourceId)},
        m.records.map {AttachmentRestorePreflight.TargetRow(it.uuid,it.kind,it.sourceId,it.state)},emptySet(),
        AttachmentRestorePreflight.Resources(1024L*1024*1024,4096,100,100))
    private fun reject(block:()->Unit) {try {block();fail("Invalid reconstruction accepted")}catch(_:Exception){}}
    private fun tables(b:ByteArray)=BackupTables.of(JSONObject(String(b,Charsets.UTF_8)).getJSONObject("tables"))
    @Test fun fullBusinessHistoryAndThreeTrashRepresentationsAreReconstructedAndIndependentlyCompared() {
        database().use {source->val b=fixture(source);try {val m=manifest(b);val p=plan(m,b)
            database().use {candidate->candidate.runInTransaction {
                AttachmentCandidateMetadata.reconstruct(candidate.openHelper.writableDatabase,tables(b),p) {}
            }
                val actual=candidate.runInTransaction(Callable {MeasuredBackupJson.encode(candidate.openHelper.writableDatabase,Instant.EPOCH)})
                try {assertEquals(JSONObject(String(b)).toString(),JSONObject(String(actual)).toString())}finally {actual.fill(0)}
                assertEquals(AttachmentCandidateMetadata.normalized(m.copy(dataset=p.namespace.dataset,epoch=p.namespace.epoch)),AttachmentCandidateMetadata.read(candidate.openHelper.writableDatabase))
                assertEquals(setOf("DOSE_RECORD","LAB","APPOINTMENT"),m.records.map {it.kind.name}.toSet())
                candidate.openHelper.writableDatabase.query("SELECT revision,deleted_at_utc FROM dose_record").use {assertTrue(it.moveToFirst());assertTrue(it.getLong(0)>1);assertFalse(it.isNull(1))}
            }
            assertArrayEquals(b,source.runInTransaction(Callable {MeasuredBackupJson.encode(source.openHelper.writableDatabase,Instant.EPOCH)}))
        }finally {b.fill(0)}}
    }
    @Test fun sameCountsChangedFactsAreRejectedByIndependentComparison() {
        database().use {source->val b=fixture(source);try {val p=plan(manifest(b),b)
            database().use {candidate->candidate.runInTransaction {AttachmentCandidateMetadata.reconstruct(candidate.openHelper.writableDatabase,tables(b),p) {}}
                candidate.openHelper.writableDatabase.execSQL("UPDATE medication SET name='SYNTHETIC altered same row count'")
                reject {AttachmentCandidateMetadata.compareBusiness(candidate.openHelper.writableDatabase,tables(b)) {}}
            }
        }finally {b.fill(0)}}
    }
    @Test fun cancellationRollsBackWholeCandidateAndSourceRemainsBitForBitUnchanged() {
        database().use {source->val b=fixture(source);try {val p=plan(manifest(b),b)
            database().use {candidate->var reached=false
                reject {candidate.runInTransaction {AttachmentCandidateMetadata.reconstruct(candidate.openHelper.writableDatabase,tables(b),p) {
                    if(candidate.openHelper.writableDatabase.query("SELECT 1 FROM medication").use {it.moveToFirst()}) {reached=true;throw IllegalStateException("Synthetic cancellation")}
                }}}
                assertTrue(reached)
                DOMAIN_TABLES.forEach {t->candidate.openHelper.writableDatabase.query("SELECT COUNT(*) FROM $t").use {assertTrue(it.moveToFirst());assertEquals(0L,it.getLong(0))}}
            }
            assertArrayEquals(b,source.runInTransaction(Callable {MeasuredBackupJson.encode(source.openHelper.writableDatabase,Instant.EPOCH)}))
        }finally {b.fill(0)}}
    }
    @Test fun missingTypedTrashDuplicateIdentityAndBrokenRelationshipRefuseWholePlan() {
        database().use {source->val b=fixture(source);try {val m=manifest(b)
            reject {plan(m.copy(records=m.records+m.records.first()),b)}
            val broken=JSONObject(String(b));broken.getJSONObject("tables").put("trash_item",org.json.JSONArray())
            reject {plan(m,broken.toString().toByteArray())}
            reject {plan(m.copy(records=m.records.map {it.copy(sourceId=999999)}),b)}
            assertArrayEquals(b,source.runInTransaction(Callable {MeasuredBackupJson.encode(source.openHelper.writableDatabase,Instant.EPOCH)}))
        }finally {b.fill(0)}}
    }
    @Test fun reconstructionRejectsExistingBusinessRowsInsteadOfReplacingThem() {
        database().use {db->val b=fixture(db);try {val p=plan(manifest(b),b)
            reject {db.runInTransaction {AttachmentCandidateMetadata.reconstruct(db.openHelper.writableDatabase,tables(b),p) {}}}
            assertArrayEquals(b,db.runInTransaction(Callable {MeasuredBackupJson.encode(db.openHelper.writableDatabase,Instant.EPOCH)}))
        }finally {b.fill(0)}}
    }
    @Test fun admittedTypedClosureCannotReplaceOriginalFullBusinessIntegrityAudit() {
        database().use {source->val b=fixture(source);try {
            val broken=JSONObject(String(b));broken.getJSONObject("tables").getJSONArray("dose_record").getJSONObject(0).put("medication_id",999999)
            val corrupt=broken.toString().toByteArray();try {
                val p=plan(manifest(corrupt),corrupt) // Typed trash/member admission alone is insufficient.
                database().use {candidate->
                    reject {candidate.runInTransaction {AttachmentCandidateMetadata.reconstruct(candidate.openHelper.writableDatabase,tables(corrupt),p) {}}}
                    DOMAIN_TABLES.forEach {t->candidate.openHelper.writableDatabase.query("SELECT COUNT(*) FROM $t").use {assertTrue(it.moveToFirst());assertEquals(0L,it.getLong(0))}}
                }
            }finally {corrupt.fill(0)}
            assertArrayEquals(b,source.runInTransaction(Callable {MeasuredBackupJson.encode(source.openHelper.writableDatabase,Instant.EPOCH)}))
        }finally {b.fill(0)}}
    }
    private fun sha(b:ByteArray)=MessageDigest.getInstance("SHA-256").digest(b).joinToString("") {"%02x".format(it.toInt() and 255)}
    @Test fun monaReceiptsSurviveFullBusinessReconstructionAndPreventReplay() {
        val json="""{"metadata":{"app_version":"synthetic","database_version":23,"export_date":"2026-10-25T12:00:00"},"data":{"medication_intakes":[{"id":801,"takenDateTime":"2026-10-25T00:30:00Z","takenTimeZone":"Europe/Paris","takenDose":"1.25","molecule":"{\"name\":\"estradiol\",\"massUnit\":\"mg\"}","administrationRoute":"oral","dosingBasis":"mass"}],"blood_tests":[],"medication_schedules":[],"supply_items":[]}}"""
        val export=net.plainnotes.app.importer.Mona.read(json)
        database().use {source->
            val repo=NotesRepository(object:DatabaseAccess(context){override fun get(space:Space)=source})
            runBlocking {assertEquals(1,repo.importMona(export,emptyMap(),emptyMap()).addedDoses)}
            val bytes=source.runInTransaction(Callable {MeasuredBackupJson.encode(source.openHelper.writableDatabase,Instant.EPOCH)})
            try {
                assertEquals(14,JSONObject(String(bytes)).getInt("schema"))
                val m=AttachmentBackupManifest(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),emptyList(),emptyList(),emptyList(),emptyList())
                val prepared=plan(m,bytes)
                database().use {candidate->
                    candidate.runInTransaction {AttachmentCandidateMetadata.reconstruct(candidate.openHelper.writableDatabase,tables(bytes),prepared) {}}
                    candidate.runInTransaction {AttachmentCandidateMetadata.compareBusiness(candidate.openHelper.writableDatabase,tables(bytes)) {}}
                    val restored=NotesRepository(object:DatabaseAccess(context){override fun get(space:Space)=candidate})
                    runBlocking {val replay=restored.importMona(export,emptyMap(),emptyMap());assertEquals(0,replay.addedDoses);assertEquals(1,replay.existing)}
                    assertArrayEquals(bytes,candidate.runInTransaction(Callable {MeasuredBackupJson.encode(candidate.openHelper.writableDatabase,Instant.EPOCH)}))
                }
                assertArrayEquals(bytes,source.runInTransaction(Callable {MeasuredBackupJson.encode(source.openHelper.writableDatabase,Instant.EPOCH)}))
            } finally {bytes.fill(0)}
        }
    }    @Test fun featherlineReceiptsSurviveFullBusinessReconstructionAndPreventReplay() {
        val export=FeatherlineSynthetic.export()
        database().use {source->
            val repo=NotesRepository(object:DatabaseAccess(context){override fun get(space:Space)=source})
            runBlocking {assertEquals(1,repo.importFeatherline(export).doses)}
            val bytes=source.runInTransaction(Callable {MeasuredBackupJson.encode(source.openHelper.writableDatabase,Instant.EPOCH)})
            try {
                assertEquals(15,JSONObject(String(bytes)).getInt("schema"))
                val m=AttachmentBackupManifest(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),emptyList(),emptyList(),emptyList(),emptyList())
                val prepared=plan(m,bytes)
                database().use {candidate->
                    candidate.runInTransaction {AttachmentCandidateMetadata.reconstruct(candidate.openHelper.writableDatabase,tables(bytes),prepared) {}}
                    candidate.runInTransaction {AttachmentCandidateMetadata.compareBusiness(candidate.openHelper.writableDatabase,tables(bytes)) {}}
                    val restored=NotesRepository(object:DatabaseAccess(context){override fun get(space:Space)=candidate})
                    runBlocking {val replay=restored.importFeatherline(export);assertEquals(0,replay.doses);assertEquals(3,replay.existing)}
                    assertArrayEquals(bytes,candidate.runInTransaction(Callable {MeasuredBackupJson.encode(candidate.openHelper.writableDatabase,Instant.EPOCH)}))
                }
                assertArrayEquals(bytes,source.runInTransaction(Callable {MeasuredBackupJson.encode(source.openHelper.writableDatabase,Instant.EPOCH)}))
            } finally {bytes.fill(0)}
        }
    }
}
