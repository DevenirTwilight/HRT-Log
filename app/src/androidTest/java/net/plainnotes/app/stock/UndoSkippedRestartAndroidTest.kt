package net.plainnotes.app.stock

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import net.plainnotes.app.data.*
import net.plainnotes.app.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.*

/** Dedicated synthetic database only. Main suite runs both phases; CI additionally separates them by force-stop. */
class UndoSkippedRestartAndroidTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val name="synthetic-r15-undo-restart"
    private val day=LocalDate.parse("2026-03-10")
    private val zone=ZoneId.of("UTC")
    private fun at(hour:Int)=day.atTime(hour,0).atZone(zone).toInstant()
    private fun database()=Room.databaseBuilder(context,NotesDatabase::class.java,name).allowMainThreadQueries().addCallback(SchemaGuards).build()
    private fun repo(db:NotesDatabase)=NotesRepository(object:DatabaseAccess(context){override fun get(space:Space)=db})
    private val backup get()=File(context.filesDir,"synthetic-r15-undo-backup.pnbak")
    private val pid get()=File(context.filesDir,"synthetic-r15-undo-pid")
    @Test fun persistedCorrection()=runBlocking {
        when(InstrumentationRegistry.getArguments().getString("r15_restart_phase")) {
            "prepare"->prepare()
            "verify"->verify(true)
            null->{prepare();verify(false)}
            else->error("Unknown synthetic restart phase")
        }
    }
    private suspend fun prepare() {
        context.deleteDatabase(name)
        val db=database()
        try {
            val r=repo(db)
            val id=r.saveMedication(MedicationEntity(name="R15 Synthetic",molecule="E2",route="ORAL",unit="MG",dose_per_intake=2.0,
                container_capacity=20.0,site_rotation=false,notifications_on=true,soon_alert_minutes=30,late_after_minutes=120,active=true,sort_order=0),null,RuleKind.EVERY_N_DAYS,1,
                listOf(LocalTime.of(8,0),LocalTime.of(20,0)),emptySet(),at(7))
            r.addContainers(id,20.0,1,true);r.addContainers(id,20.0,1,true)
            val fake=r.containers().maxBy{it.id};r.correctStock(fake.id,2.0);r.removeMistakenContainer(fake.id)
            val slot=r.calendar(at(7),zone,day).single{it.slot.at==at(8)}.slot
            r.override(slot,SlotOverride(slot.key,at(10),zone,3.0,true),at(7))
            val skipped=r.records().single{it.slot_key==slot.key};r.undoSkipped(skipped,at(7))
            assertTrue(db.dao().records().none{it.status in listOf("ON_TIME","LATE")})
            backup.writeBytes(r.exportBackup("r15-synthetic-pass".toCharArray()))
            pid.writeText(android.os.Process.myPid().toString())
        } finally {db.close()}
    }
    private suspend fun verify(freshProcess:Boolean) {
        if(freshProcess)assertNotEquals(pid.readText().toInt(),android.os.Process.myPid())
        val db=database()
        try {
            val r=repo(db);val skipped=db.dao().deletedRecords().single{it.status=="SKIPPED"}
            assertEquals(2,skipped.revision);assertNull(skipped.actual_dose);assertTrue(db.dao().supplyFor(skipped.id).isEmpty())
            fun check(entry:TimelineEntry) {
                assertEquals(SlotState.PENDING,entry.state);assertEquals(at(8),entry.slot.original);assertEquals(at(10),entry.slot.at);assertEquals(3.0,entry.slot.dose,0.0)
            }
            check(r.calendar(at(7),zone,day).single{it.slot.key==skipped.slot_key})
            val hidden=r.voidedContainers().single();assertEquals("VOIDED",hidden.state);assertEquals(20.0,StockForecasts.available(skipped.medication_id,r.containers())!!,0.0)
            val plaintext=BackupCodec.decrypt(backup.readBytes(),"r15-synthetic-pass".toCharArray())
            try { assertEquals(10,org.json.JSONObject(plaintext.toString(Charsets.UTF_8)).getInt("schema")) }
            finally { plaintext.fill(0) }
            r.restoreBackup(backup.readBytes(),"r15-synthetic-pass".toCharArray())
            assertFalse(r.currentOverride(skipped.slot_key!!).skipped);assertNotNull(db.dao().recordById(skipped.id)!!.deleted_at_utc)
            val slot=r.calendar(at(7),zone,day).single{it.slot.key==skipped.slot_key};check(slot)
            r.complete(slot.slot,at(10),3.0);r.complete(slot.slot,at(10),3.0)
            assertEquals(3.0,r.containers().single().used_amount,0.0);assertEquals(hidden,r.voidedContainers().single())
            assertEquals(1,r.records().count{it.slot_key==skipped.slot_key});assertEquals(11,db.openHelper.writableDatabase.version)
        } finally { db.close();context.deleteDatabase(name);backup.delete();pid.delete() }
    }
}
