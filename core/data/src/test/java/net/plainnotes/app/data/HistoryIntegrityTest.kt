package net.plainnotes.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import net.plainnotes.app.domain.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.*
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class) @Config(sdk=[28])
class HistoryIntegrityTest {
    private lateinit var db:NotesDatabase
    private lateinit var repo:NotesRepository
    private val start=Instant.parse("2026-03-09T12:00:00Z")
    private val zone=ZoneId.of("UTC")
    private val base=MedicationEntity(name="Synthetic",molecule="E2",route="GEL",unit="MG",dose_per_intake=2.0,container_capacity=40.0,
        soon_alert_minutes=15,late_after_minutes=60,site_rotation=false,notifications_on=true,active=true,sort_order=0)
    @Before fun open() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        val c=ApplicationProvider.getApplicationContext<Context>()
        db=Room.inMemoryDatabaseBuilder(c,NotesDatabase::class.java).allowMainThreadQueries().addCallback(SchemaGuards).build()
        repo=NotesRepository(object:DatabaseAccess(c){override fun get(space:Space)=db})
    }
    @After fun close()=db.close()
    private suspend fun seed():Long=repo.saveMedication(base,"E2",RuleKind.EVERY_N_DAYS,1,listOf(LocalTime.of(20,0)),emptySet(),start,
        pk=ProfileEntity(0,"E2","gel",gel_product_id=1))

    @Test fun productEditPreservesActualAndCreatesNewPlanContext()=runBlocking {
        val id=seed()
        repo.unscheduled(id,start,2.0)
        val original=repo.records().single().config_snapshot
        repo.saveMedication(base.copy(id=id),"E2",RuleKind.EVERY_N_DAYS,1,listOf(LocalTime.of(20,0)),emptySet(),start.plusSeconds(60),
            pk=ProfileEntity(id,"E2","gel",gel_product_id=2))
        assertEquals(2,repo.rules().size)
        assertEquals(original,repo.records().single().config_snapshot)
        assertEquals(1,MedicationSnapshot.decode(original,id)!!.profile!!.gel_product_id)
        assertEquals(2,MedicationSnapshot.decode(repo.rules().last().config_snapshot,id)!!.profile!!.gel_product_id)
    }

    @Test fun noteEditDoesNotRecaptureSourceContext()=runBlocking {
        seed();val day=LocalDate.of(2026,3,9);var calls=0
        repo.setSymptomCheck(day,"SYNTHETIC",true,"first"){_,_->calls++;"{\"version\":\"original\"}"}
        repo.setSymptomCheck(day,"SYNTHETIC",true,"edited"){_,_->calls++;"{\"version\":\"changed\"}"}
        val saved=repo.symptomChecks(day,day).single()
        assertEquals(1,calls);assertEquals("original",JSONObject(saved.context_snapshot!!).getString("version"));assertEquals("edited",saved.note)
        // Legacy unknown remains unknown on a note edit.
        repo.setSymptomCheck(day,"LEGACY",true)
        repo.setSymptomCheck(day,"LEGACY",true,"edited"){_,_->"{}"}
        assertNull(repo.symptomChecks(day,day).single{it.group_id=="LEGACY"}.context_snapshot)
    }

    @Test fun overdueWithoutRecordRemainsUnconfirmedUntilUserConfirms()=runBlocking {
        seed();val now=start.plusSeconds(2*86400)
        repo.calendar(now,zone,LocalDate.of(2026,3,9))
        val r=repo.records().first{it.unconfirmed}
        assertEquals(SlotState.UNCONFIRMED,repo.calendar(now,zone,LocalDate.of(2026,3,9)).single{it.slot.key==r.slot_key}.state)
        repo.confirmMissed(r.id)
        assertFalse(repo.records().single{it.id==r.id}.unconfirmed)
        assertEquals(SlotState.MISSED,repo.calendar(now,zone,LocalDate.of(2026,3,9)).single{it.slot.key==r.slot_key}.state)
        assertTrue(repo.transaction{it.supplyFor(r.id)}.isEmpty())
    }

    @Test fun invalidAuthenticatedBackupRollsBackExistingData()=runBlocking {
        val id=seed();repo.addContainers(id,40.0,1,true);repo.unscheduled(id,start,2.0)
        val password="synthetic-password".toCharArray()
        val before=JSONObject(String(BackupCodec.decrypt(repo.exportBackup(password),password)))
        val original=before.getJSONObject("tables").toString()
        val alterations=listOf<(JSONObject)->Unit>(
            {it.getJSONArray("medication").getJSONObject(0).put("dose_per_intake",-2)},
            {it.getJSONArray("medication").getJSONObject(0).put("unexpected_column","x")},
            {it.getJSONArray("dose_record").getJSONObject(0).put("taken_zone","not/a/zone")},
            {it.getJSONArray("schedule_rule").getJSONObject(0).put("interval",0)},
            {it.getJSONArray("supply_transaction").getJSONObject(0).put("kind","REVERSE")}
        )
        alterations.forEach{change->
            val broken=JSONObject(before.toString());change(broken.getJSONObject("tables"))
            val file=BackupCodec.encrypt(broken.toString().toByteArray(),password)
            try{repo.restoreBackup(file,password);fail("must reject malformed domain state")}catch(_:Exception){}
            val after=JSONObject(String(BackupCodec.decrypt(repo.exportBackup(password),password))).getJSONObject("tables").toString()
            assertEquals(original,after)
        }
        repo.restoreBackup(repo.exportBackup(password),password)
        assertEquals(38.0,repo.containers().single().capacity-repo.containers().single().used_amount,0.0)
    }

    @Test fun lexicalAndStreamLimitsRejectBeforeParsing() {
        try{BackupLimits.checkJson("[".repeat(33)+"]".repeat(33));fail()}catch(_:BackupCodec.TooLarge){}
        try{BackupLimits.checkJson("{\"x\":\""+"a".repeat(1024*1024)+"\"}");fail()}catch(_:BackupCodec.TooLarge){}
        val stream=object:java.io.InputStream(){var remaining=BackupLimits.MAX_FILE_BYTES+1
            override fun read():Int=if(remaining-- > 0)0 else -1
            override fun read(b:ByteArray,off:Int,len:Int):Int{if(remaining==0)return -1;val n=minOf(len,remaining);remaining-=n;return n}}
        try{BackupLimits.read(stream);fail()}catch(_:BackupCodec.TooLarge){}
    }
}
