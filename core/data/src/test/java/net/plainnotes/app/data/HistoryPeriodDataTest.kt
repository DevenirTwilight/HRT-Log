package net.plainnotes.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import net.plainnotes.app.domain.TherapyStandard
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.*

/** REQUIREMENTS §35/35a: confirmed past periods are append-only and never touch records, rules or regimen versions. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[28])
class HistoryPeriodDataTest {
    private lateinit var db:NotesDatabase;private lateinit var repo:NotesRepository
    private val zone=ZoneId.of("Europe/Paris");private val d0=LocalDate.of(2026,6,10)
    private val twice=TherapyStandard("E2","E2","SUBLINGUAL","MG",null,"EVERY_N_DAYS",1,0,listOf(1.0,1.0))
    private val med=MedicationEntity(name="Synthetic E2",molecule="E2",route="SUBLINGUAL",unit="MG",dose_per_intake=1.0,container_capacity=30.0,site_rotation=false,notifications_on=false,active=true,sort_order=0)
    @Before fun open(){val c=ApplicationProvider.getApplicationContext<Context>();db=Room.inMemoryDatabaseBuilder(c,NotesDatabase::class.java).allowMainThreadQueries().addCallback(SchemaGuards).build();repo=NotesRepository(object:DatabaseAccess(c){override fun get(space:Space)=db})}
    @After fun close()=db.close()
    private suspend fun medication()=db.dao().insertMedication(med)
    private suspend fun record(id:Long):Long=db.dao().record(RecordEntity(medication_id=id,taken_utc=d0.atTime(8,0).atZone(zone).toInstant().toEpochMilli(),taken_zone=zone.id,actual_dose=1.0,status="ON_TIME",origin="IMPORT_HT",source_record_key="ht:synthetic:1",revision=1,config_snapshot="{}"))
    private suspend fun confirm(id:Long,from:LocalDate=d0,until:LocalDate?=d0.plusDays(120),key:String?=null,standard:TherapyStandard=twice)=
        repo.confirmHistoryPeriod(key,id,standard,from,until,zone,"{}","""{"source_records":[1]}""",Instant.parse("2026-10-08T10:00:00Z"))

    @Test fun confirmEditRevokeReconfirmIsAppendOnlyAndLeavesRecordsAlone()=runBlocking {
        val id=medication();val r=record(id);val before=db.dao().recordById(r)
        val key=confirm(id)
        confirm(id,from=d0.plusDays(1),key=key)
        repo.revokeHistoryPeriod(key)
        assertTrue(HistoryPeriods.confirmed(repo.historyPeriods()).isEmpty())
        confirm(id,key=key)
        assertEquals(listOf(1,2,3,4),repo.historyPeriods().map{it.revision})
        assertEquals(listOf("CONFIRMED","CONFIRMED","REVOKED","CONFIRMED"),repo.historyPeriods().map{it.state})
        assertEquals(before,db.dao().recordById(r));assertTrue(db.dao().rules().isEmpty());assertTrue(db.dao().regimens().isEmpty())
        try{db.openHelper.writableDatabase.execSQL("UPDATE history_period_revision SET from_date='2026-01-01'");fail()}catch(_:Exception){}
        try{db.openHelper.writableDatabase.execSQL("DELETE FROM history_period_revision");fail()}catch(_:Exception){}
    }
    @Test fun splitAndMergeKeepOneConfirmedCoverage()=runBlocking {
        val id=medication();val key=confirm(id)
        val second=repo.splitHistoryPeriod(key,d0.plusDays(40))
        val now=HistoryPeriods.confirmed(repo.historyPeriods()).sortedBy{it.from_date}
        assertEquals(listOf(d0.toString(),d0.plusDays(40).toString()),now.map{it.from_date});assertEquals(d0.plusDays(40).toString(),now.first().until_date)
        repo.mergeHistoryPeriods(key,second)
        val merged=HistoryPeriods.confirmed(repo.historyPeriods()).single()
        assertEquals(key,merged.period_key);assertEquals(d0.plusDays(120).toString(),merged.until_date)
    }
    @Test fun invalidPeriodsAreRejected()=runBlocking {
        val id=medication();confirm(id)
        // Overlap with another confirmed period of the same medication.
        assertThrows(IllegalArgumentException::class.java){runBlocking{confirm(id,from=d0.plusDays(30),until=d0.plusDays(200))}}
        assertThrows(IllegalArgumentException::class.java){runBlocking{confirm(id,from=d0.plusDays(300),until=d0.plusDays(300))}}
        // Unknown frequency cannot be confirmed.
        assertThrows(IllegalArgumentException::class.java){runBlocking{confirm(id,from=d0.plusDays(300),until=null,standard=twice.copy(kind="OBSERVED",interval=0))}}
        val other=confirm(id,from=d0.plusDays(300),until=null,standard=twice.copy(doses=listOf(2.0,2.0)))
        assertThrows(IllegalArgumentException::class.java){runBlocking{repo.mergeHistoryPeriods(HistoryPeriods.confirmed(repo.historyPeriods()).first{it.period_key!=other}.period_key,other)}}
        Unit
    }
    @Test fun labContextCarriesTheConfirmedPeriodOnlyWhenItContainsTheSample()=runBlocking {
        val id=medication();val key=confirm(id)
        fun lab(day:LocalDate)=LabValueEntity(analyte_code="E2",value=120.0,unit="pg/mL",sampled_utc=day.atTime(9,0).atZone(zone).toInstant().toEpochMilli(),sampled_zone=zone.id)
        repo.saveLab(lab(d0.plusDays(30)));repo.saveLab(lab(d0.plusDays(200)))
        val contexts=repo.labContexts().sortedBy{it.lab_id}.map{LabContext.validate(it.context_json)}
        val periods=contexts[0].getJSONArray("confirmed_periods")
        assertEquals(1,periods.length());assertEquals(key,periods.getJSONObject(0).getString("period_key"))
        assertEquals(HistoryPeriods.ORIGIN,periods.getJSONObject(0).getString("origin"))
        assertFalse(contexts[1].has("confirmed_periods"))
        // A context naming a period that does not contain the sample is rejected.
        val bad=JSONObject(repo.labContexts().minBy{it.lab_id}.context_json);bad.getJSONArray("confirmed_periods").getJSONObject(0).put("from_date",d0.plusDays(40).toString())
        try{LabContext.validate(bad.toString());fail()}catch(_:IllegalArgumentException){}
    }

    @Test fun extraAnnotationIsSeparateFromTheRecord()=runBlocking {
        val id=medication();val r=record(id);val before=db.dao().recordById(r)
        repo.setExtra(r,true);repo.setExtra(r,true)
        assertEquals(listOf(r),repo.annotations().map{it.record_id})
        repo.setExtra(r,false);assertTrue(repo.annotations().isEmpty());assertEquals(before,db.dao().recordById(r))
        repo.unscheduled(id,Instant.parse("2026-06-11T08:00:00Z"),1.0,extra=true)
        assertEquals(1,repo.annotations().size)
    }
    @Test fun backupRoundTripOldSchemaAndMalformedRowsRollBack()=runBlocking {
        val id=medication();val r=record(id);confirm(id);repo.setExtra(r,true)
        val periods=repo.historyPeriods();val notes=repo.annotations()
        val pwd="synthetic-pass".toCharArray();val backup=repo.exportBackup(pwd)
        repo.restoreBackup(backup,pwd);assertEquals(periods,repo.historyPeriods());assertEquals(notes,repo.annotations())
        fun tamper(edit:(JSONObject)->Unit)=BackupCodec.encrypt(JSONObject(String(BackupCodec.decrypt(backup,pwd))).also(edit).toString().toByteArray(),pwd)
        listOf<(JSONObject)->Unit>(
            {it.getJSONObject("tables").getJSONArray("history_period_revision").getJSONObject(0).put("state","MAYBE")},
            {it.getJSONObject("tables").getJSONArray("history_period_revision").getJSONObject(0).put("standard_json","""{"version":1,"kind":"OBSERVED"}""")},
            {it.getJSONObject("tables").getJSONArray("history_period_revision").getJSONObject(0).put("revision",2)},
            {it.getJSONObject("tables").getJSONArray("record_annotation").getJSONObject(0).put("kind","MISSED")},
        ).forEach{edit->try{repo.restoreBackup(tamper(edit),pwd);fail()}catch(_:Exception){};assertEquals(periods,repo.historyPeriods())}
        val old=tamper{o->o.put("schema",6);o.getJSONObject("tables").apply{remove("history_period_revision");remove("record_annotation")}}
        repo.restoreBackup(old,pwd);assertTrue(repo.historyPeriods().isEmpty());assertTrue(repo.annotations().isEmpty());assertEquals(1,repo.records().size)
        try{repo.restoreBackup(tamper{it.getJSONObject("tables").remove("record_annotation")},pwd);fail()}catch(_:Exception){}
    }

    // --- REQUIREMENTS §37b user edits ---
    private fun row(kind:String,id:Long,from:LocalDate,until:LocalDate?,standard:TherapyStandard?=twice)=TimelineEditRow(kind,id,standard.takeIf{kind in listOf(HistoryPeriods.PERIOD,HistoryPeriods.FILL)},from,until,zone,"{}")

    @Test fun userEditsAreAppendOnlyUndoableAndNeverTouchRecordsOrPlans()=runBlocking {
        val id=medication();val r=record(id);val before=db.dao().recordById(r)
        val created=repo.editTimeline(emptyList(),listOf(row(HistoryPeriods.PERIOD,id,d0,d0.plusDays(30))))
        assertEquals(1,HistoryPeriods.userEdits(repo.historyPeriods()).size)
        // Editing replaces the period; undoing the edit brings the replaced one back.
        val key=HistoryPeriods.userEdits(repo.historyPeriods()).single().period_key
        val edited=repo.editTimeline(listOf(key),listOf(row(HistoryPeriods.PERIOD,id,d0,d0.plusDays(40),twice.copy(interval=2,doses=listOf(1.0)))))
        assertEquals(listOf(d0.plusDays(40).toString()),HistoryPeriods.userEdits(repo.historyPeriods()).map{it.until_date})
        repo.undoTimelineEdit(edited)
        assertEquals(listOf(key),HistoryPeriods.userEdits(repo.historyPeriods()).map{it.period_key})
        // Delete and restore.
        val deleted=repo.editTimeline(listOf(key),listOf(row(HistoryPeriods.DELETED,id,d0,d0.plusDays(30))))
        assertEquals(listOf(HistoryPeriods.DELETED),HistoryPeriods.userEdits(repo.historyPeriods()).map{it.kind})
        repo.undoTimelineEdit(deleted)
        assertEquals(listOf(HistoryPeriods.PERIOD),HistoryPeriods.userEdits(repo.historyPeriods()).map{it.kind})
        repo.undoTimelineEdit(created);assertTrue(HistoryPeriods.userEdits(repo.historyPeriods()).isEmpty())
        // Every step is a new row; records, rules and plan versions are untouched.
        assertTrue(repo.historyPeriods().size>=8);assertEquals(before,db.dao().recordById(r));assertTrue(db.dao().regimens().isEmpty());assertTrue(db.dao().rules().isEmpty())
        try{db.openHelper.writableDatabase.execSQL("DELETE FROM history_period_revision");fail()}catch(_:Exception){}
        try{repo.undoTimelineEdit(created);fail()}catch(_:Exception){}
    }

    @Test fun overlapIsRefusedForTheSameMedicineOnly()=runBlocking {
        val a=medication();val b=db.dao().insertMedication(med.copy(name="Synthetic CPA",molecule="CPA",route=null))
        repo.editTimeline(emptyList(),listOf(row(HistoryPeriods.PERIOD,a,d0,d0.plusDays(30))))
        try{repo.editTimeline(emptyList(),listOf(row(HistoryPeriods.STOP,a,d0.plusDays(10),d0.plusDays(20))));fail()}catch(_:IllegalArgumentException){}
        repo.editTimeline(emptyList(),listOf(row(HistoryPeriods.PERIOD,b,d0.plusDays(10),d0.plusDays(20),twice.copy(compound="CPA",ester=null,route=null))))
        // Confirmed periods and user edits are different layers: a user edit may lie over a confirmed period.
        confirm(a,from=d0,until=d0.plusDays(60))
        assertEquals(2,HistoryPeriods.userEdits(repo.historyPeriods()).size);assertEquals(1,HistoryPeriods.confirmed(repo.historyPeriods()).size)
        try{repo.editTimeline(emptyList(),listOf(row(HistoryPeriods.DELETED,a,d0,d0.plusDays(5),twice)));fail()}catch(_:IllegalArgumentException){}
    }

    @Test fun userEditsSurviveBackupRestoreAndSchemaSevenBackupsStillRestore()=runBlocking {
        val id=medication();record(id);confirm(id)
        repo.editTimeline(emptyList(),listOf(row(HistoryPeriods.STOP,id,d0.plusDays(200),d0.plusDays(210))))
        val periods=repo.historyPeriods();val pwd="synthetic-pass".toCharArray();val backup=repo.exportBackup(pwd)
        repo.restoreBackup(backup,pwd);assertEquals(periods,repo.historyPeriods())
        fun tamper(edit:(JSONObject)->Unit)=BackupCodec.encrypt(JSONObject(String(BackupCodec.decrypt(backup,pwd))).also(edit).toString().toByteArray(),pwd)
        // A schema 7 backup has no kind or group_key: its rows are confirmed periods.
        val v7=tamper{o->o.put("schema",7);val rows=o.getJSONObject("tables").getJSONArray("history_period_revision")
            val keep=org.json.JSONArray();for(i in 0 until rows.length())rows.getJSONObject(i).let{if(it.getString("kind")=="CONFIRMED"){it.remove("kind");it.remove("group_key");keep.put(it)}}
            o.getJSONObject("tables").put("history_period_revision",keep)}
        repo.restoreBackup(v7,pwd)
        assertEquals(1,HistoryPeriods.confirmed(repo.historyPeriods()).size);assertTrue(HistoryPeriods.userEdits(repo.historyPeriods()).isEmpty())
        // A user edit with a standard on a DELETED row, or an unknown kind, is rejected.
        listOf<(JSONObject)->Unit>({it.getJSONObject("tables").getJSONArray("history_period_revision").let{a->(0 until a.length()).map(a::getJSONObject).first{r->r.getString("kind")=="STOP"}.put("kind","MAYBE")}},
            {it.getJSONObject("tables").getJSONArray("history_period_revision").let{a->(0 until a.length()).map(a::getJSONObject).first{r->r.getString("kind")=="STOP"}.put("origin","OBSERVED_USER_CONFIRMED")}})
            .forEach{edit->try{repo.restoreBackup(tamper(edit),pwd);fail()}catch(_:Exception){}}
    }
}
