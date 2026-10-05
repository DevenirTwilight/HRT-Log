package net.plainnotes.app.data
import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.security.KeyStore

/** Real Android Keystore + native SQLCipher; only synthetic data. */
class EncryptionIntegrationTest {
    private val context:Context get()=InstrumentationRegistry.getInstrumentation().targetContext
    @Before fun reset(){context.deleteDatabase("notes.db");java.io.File(context.noBackupFilesDir,"key.wrap").delete();java.io.File(context.noBackupFilesDir,"key.wrap.bak").delete();KeyStore.getInstance("AndroidKeyStore").apply{load(null)}.deleteEntry("notes.wrap")}
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

}
