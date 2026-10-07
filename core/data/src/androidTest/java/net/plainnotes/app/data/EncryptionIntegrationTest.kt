package net.plainnotes.app.data
import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import androidx.room.withTransaction
import org.junit.*
import org.junit.Assert.*
import java.security.KeyStore

/** Real Android Keystore + native SQLCipher; only synthetic data. */
class EncryptionIntegrationTest {
    private val context:Context get()=InstrumentationRegistry.getInstrumentation().targetContext
    @Before fun reset(){context.deleteDatabase("notes_b.db");java.io.File(context.noBackupFilesDir,"key_b.wrap").delete();java.io.File(context.noBackupFilesDir,"key_b.wrap.bak").delete();KeyStore.getInstance("AndroidKeyStore").apply{load(null)}.deleteEntry("notes_b.wrap");context.deleteDatabase("notes.db");java.io.File(context.noBackupFilesDir,"key.wrap").delete();java.io.File(context.noBackupFilesDir,"key.wrap.bak").delete();KeyStore.getInstance("AndroidKeyStore").apply{load(null)}.deleteEntry("notes.wrap")}
    @After fun cleanup(){reset()}
    @Test fun encryptedDatabaseReopensWithWrappedRandomKey(){
        val first=DatabaseAccess(context).get()
        runBlocking { first.dao().insertMedication(MedicationEntity(name="Synthetic native test",molecule="OTHER",unit="MG",dose_per_intake=1.0,container_capacity=10.0,soon_alert_minutes=0,late_after_minutes=10,site_rotation=false,notifications_on=false,active=true,sort_order=0)) }
        first.close()
        val header=context.getDatabasePath("notes.db").inputStream().use{ByteArray(16).also { bytes-> java.io.DataInputStream(it).readFully(bytes) }}
        assertFalse(String(header).startsWith("SQLite format 3"))
        val reopened=DatabaseAccess(context).get()
        assertEquals("Synthetic native test",runBlocking{reopened.dao().medications().single().name});reopened.close()
        assertTrue(java.io.File(context.noBackupFilesDir,"key.wrap").length()>32)
    }
    @Test fun concurrentWalReaderAfterEditKeepsTheEncryptionKey()=runBlocking {
        val access=DatabaseAccess(context);val db=access.get()
        try {
            val med=MedicationEntity(name="Synthetic WAL",molecule="OTHER",unit="MG",dose_per_intake=2.0,container_capacity=84.0,soon_alert_minutes=0,late_after_minutes=10,site_rotation=false,notifications_on=false,active=true,sort_order=0)
            val id=db.dao().insertMedication(med)
            val writing=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>()
            val writer=launch(Dispatchers.IO) { db.withTransaction {
                db.dao().updateMedication(med.copy(id=id,dose_per_intake=4.0));writing.complete(Unit);release.await()
            } }
            writing.await()
            try {
                withContext(Dispatchers.IO) {
                    // While the primary connection is held by the writer, this opens a second WAL connection.
                    db.openHelper.readableDatabase.query("SELECT dose_per_intake FROM medication").use {
                        assertTrue(it.moveToFirst());assertEquals(2.0,it.getDouble(0),0.0)
                    }
                }
            } finally { release.complete(Unit);writer.join() }
            assertEquals(4.0,db.dao().medications().single().dose_per_intake,0.0)
        } finally { access.close() }
    }

    @Test fun realSpaceSurvivesRepeatedDecoySwitchesAndReopen()=runBlocking {
        val access=DatabaseAccess(context);val ui=NotesRepository(access)
        val real=ui.pinnedTo(Space.PRIMARY);val decoy=ui.pinnedTo(Space.DECOY)
        try {
            real.transaction { it.insertMedication(MedicationEntity(name="Synthetic real",molecule="OTHER",unit="MG",dose_per_intake=4.0,container_capacity=84.0,soon_alert_minutes=0,late_after_minutes=10,site_rotation=false,notifications_on=false,active=true,sort_order=0)) }
            repeat(3) {
                ui.select(Space.DECOY);assertTrue(decoy.medications().isEmpty())
                assertEquals("Synthetic real",real.medications().single().name)
                ui.select(Space.PRIMARY);assertEquals("Synthetic real",ui.medications().single().name)
            }
            access.close(Space.PRIMARY);access.close(Space.DECOY)
            assertEquals("Synthetic real",real.medications().single().name)
        } finally { access.close(Space.PRIMARY);access.close(Space.DECOY) }
    }

    @Test fun missingWrappedKeyFailsWithoutReplacingDatabase(){
        val db=DatabaseAccess(context).get();db.close()
        val original=context.getDatabasePath("notes.db").readBytes();java.io.File(context.noBackupFilesDir,"key.wrap").delete()
        try{DatabaseAccess(context).get();fail("Recovery must be required")}catch(_:KeyRecoveryRequired){}
        assertArrayEquals(original,context.getDatabasePath("notes.db").readBytes())
    }
    @Test fun notificationResolutionRejectsRescheduleAndCompletionIsIdempotent()=runBlocking {
        val access=DatabaseAccess(context);val repo=NotesRepository(access);val now=java.time.Instant.now()
        repo.saveMedication(MedicationEntity(name="Synthetic reminder",molecule="OTHER",unit="MG",dose_per_intake=2.0,container_capacity=10.0,soon_alert_minutes=0,late_after_minutes=10,site_rotation=false,notifications_on=true,active=true,sort_order=0),null,
            net.plainnotes.app.domain.RuleKind.EVERY_N_HOURS,12,emptyList(),emptySet(),now)
        val futureDate=now.atZone(java.time.ZoneId.systemDefault()).toLocalDate().plusDays(90)
        assertTrue(repo.calendar(now,displayFrom=futureDate).any{it.slot.at.atZone(java.time.ZoneId.systemDefault()).toLocalDate()>=futureDate})
        val slot=repo.calendar(now).first().slot
        repo.transaction{it.mapping(ReminderMappingEntity("source","generation",slot.key+":DUE",slot.at.toEpochMilli(),false))}
        assertEquals(slot.key,repo.reminderSlot("source","generation")?.key)
        val delayed=now.plusSeconds(600).toEpochMilli()
        val cache=net.plainnotes.app.domain.AlarmCache("generation",delayed+1,listOf(net.plainnotes.app.domain.CachedAlarm("source",delayed,"NORMAL")))
        repo.reconcileCache(cache);repo.reconcileCache(cache)
        val mappings=repo.transaction{it.mappings()}
        assertTrue(mappings.single{it.opaque_id=="source"}.sent)
        assertEquals(1,mappings.count{it.identity=="snooze:source"})
        assertEquals(delayed,mappings.single{it.identity=="snooze:source"}.trigger_utc)
        assertEquals(slot.key,repo.reminderSlot(mappings.single{it.identity=="snooze:source"}.opaque_id)?.key)
        repo.override(slot,net.plainnotes.app.domain.SlotOverride(slot.key,now.plusSeconds(60),java.time.ZoneId.systemDefault()))
        assertNull(repo.reminderSlot("source","generation"))
        repo.override(slot,net.plainnotes.app.domain.SlotOverride(slot.key))
        val resolved=requireNotNull(repo.reminderSlot("source","generation"))
        repo.complete(resolved,now,resolved.dose);repo.complete(resolved,now,resolved.dose)
        assertNull(repo.reminderSlot("source","generation"))
        val record=repo.transaction{it.records().single()};assertEquals(2.0,record.actual_dose!!,0.0)
        assertEquals(1,record.revision);access.get().close()
    }

    @Test fun versionCutoverUsesDatabaseMillisecondPrecisionWithoutDuplicateSlot()=runBlocking {
        val access=DatabaseAccess(context);val repo=NotesRepository(access)
        val cut=java.time.Instant.ofEpochMilli(System.currentTimeMillis())
        val med=MedicationEntity(name="Synthetic cutover",molecule="OTHER",unit="MG",dose_per_intake=1.0,container_capacity=10.0,soon_alert_minutes=0,late_after_minutes=10,site_rotation=false,notifications_on=false,active=true,sort_order=0)
        val kind=net.plainnotes.app.domain.RuleKind.EVERY_N_HOURS
        val id=repo.saveMedication(med,null,kind,12,emptyList(),emptySet(),cut.minusSeconds(12*3600))
        // A plan change (late window) forces a new version; metadata-only edits keep the current one.
        repo.saveMedication(med.copy(id=id,late_after_minutes=11),null,kind,12,emptyList(),emptySet(),cut.plusNanos(500_000))
        val slots=repo.calendar(cut).filter{it.slot.at==cut}
        assertEquals(1,slots.size)
        assertEquals(repo.rules().last().id,slots.single().slot.ruleId)
        access.get().close()
    }

    @Test fun encryptedWellbeingRecordsAndPackageMetadataSurviveBackupAndReopen()=runBlocking {
        val access=DatabaseAccess(context);val repo=NotesRepository(access);val day=java.time.LocalDate.of(2026,2,10)
        try {
            val medId=repo.transaction{it.insertMedication(MedicationEntity(name="Synthetic package",molecule="OTHER",unit="MG",dose_per_intake=1.0,container_capacity=10.0,soon_alert_minutes=0,late_after_minutes=60,site_rotation=false,notifications_on=false,active=false,sort_order=0))}
            repo.addContainers(medId,10.0,1,false,"Synthetic source","LOT-SYNTHETIC")
            repo.setSymptomCheck(day,"JAUNDICE",true,"Synthetic note")
            repo.saveStageReview(StageReviewEntity(date=day.toString(),effects_json="{\"FAT\":\"NOTICED\"}",weight_kg=60.0))
            repo.setReviewEffect("FAT",false)
            val archived=repo.exportBackup("synthetic-password".toCharArray())
            repo.deleteStageReview(repo.stageReviews().single().id)
            repo.restoreBackup(archived,"synthetic-password".toCharArray())
            access.close()
            assertEquals(60.0,repo.stageReviews().single().weight_kg!!,0.0)
            assertEquals("Synthetic note",repo.symptomChecks(day,day).single().note)
            assertFalse(repo.reviewEffects().single().enabled)
            assertEquals("LOT-SYNTHETIC",repo.containers().single().batch)
            assertEquals("Synthetic source",repo.containers().single().source_note)
            assertNull(repo.transaction{it.pkSettings()?.current_weight_kg})
        } finally {access.close()}
    }

}
