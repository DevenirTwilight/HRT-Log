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

/** REQUIREMENTS §39 recycle bin: delete, restore exactly, purge for good or hide for good. Synthetic data only. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[28])
class TrashDataTest {
    private lateinit var db:NotesDatabase;private lateinit var repo:NotesRepository
    private val zone=ZoneId.of("Europe/Paris");private val day=LocalDate.of(2026,6,10)
    private val med=MedicationEntity(name="Synthetic E2",molecule="E2",route="SUBLINGUAL",unit="MG",dose_per_intake=1.0,container_capacity=30.0,site_rotation=false,notifications_on=false,active=true,sort_order=0)
    @Before fun open(){val c=ApplicationProvider.getApplicationContext<Context>();db=Room.inMemoryDatabaseBuilder(c,NotesDatabase::class.java).allowMainThreadQueries().addCallback(SchemaGuards).build();repo=NotesRepository(object:DatabaseAccess(c){override fun get(space:Space)=db})}
    @After fun close()=db.close()
    private fun dump()=RawData.dump(db.openHelper.writableDatabase).let{t->JSONObject(t.toString()).apply{remove("trash_item")}}.toString()
    private suspend fun only()=repo.trash().single()

    @Test fun userContentGoesToTheBinComesBackIdenticalAndIsPurgedForGood()=runBlocking {
        val id=db.dao().insertMedication(med)
        repo.saveMilestone(MilestoneEntity(date=day.toString(),title="Synthetic milestone"))
        val lab=repo.saveLab(LabValueEntity(analyte_code="E2",value=120.0,unit="pg/mL",sampled_utc=day.atTime(9,0).atZone(zone).toInstant().toEpochMilli(),sampled_zone=zone.id))
        repo.saveAppointment(AppointmentEntity(type="ENDO",at_utc=day.atTime(10,0).atZone(zone).toInstant().toEpochMilli(),at_zone=zone.id,remind_minutes_before=60))
        val appointment=db.dao().appointments().single().id
        repo.saveVisitQuestion(VisitQuestionEntity(appointment_id=appointment,sort_order=0,text="Synthetic question",status="OPEN"))
        repo.recordVisitPack(VisitPackEntity(appointment_id=appointment,generated_utc=1000,zone=zone.id,range_from="2026-05-01",range_to="2026-06-01",sections="FACTS",language="en",template_version=2,input_digest="0".repeat(64),facts_json="{}"))
        repo.saveStageReview(StageReviewEntity(date=day.toString()))
        repo.setSymptomCheck(day,"SYNTHETIC_GROUP",true,"note")
        val item=repo.checkinItems().first().id;repo.setScore(day,item,3);repo.setNote(day,"synthetic day note")
        val before=dump()
        val deletes:List<suspend ()->Unit> = listOf(
            {repo.deleteMilestone(db.dao().milestones().single().id)},{repo.deleteLab(db.dao().labs().single().id)},{repo.deleteAppointment(appointment)},
            {repo.deleteStageReview(db.dao().stageReviews().single().id)},{repo.setSymptomCheck(day,"SYNTHETIC_GROUP",false)},{repo.setScore(day,item,null)},{repo.setNote(day,"")})
        deletes.forEach{delete->
            delete();assertNotEquals(before,dump());assertEquals(1,repo.trash().size)
            repo.restoreTrash(only().id);assertEquals(before,dump());assertTrue(repo.trash().isEmpty())
        }
        // Lab contexts and visit packs came back unchanged with their parents.
        assertEquals(1,repo.labContexts().size);assertEquals(1,db.dao().visitPacks().size)
        deletes.forEach{it()}
        assertEquals(7,repo.trash().size)
        repo.trash().forEach{assertTrue(repo.purgeTrash(it.id))}
        assertTrue(repo.trash().isEmpty());assertTrue(db.dao().milestones().isEmpty());assertTrue(db.dao().labs().isEmpty());assertTrue(repo.labContexts().isEmpty())
        assertTrue(db.dao().appointments().isEmpty());assertTrue(db.dao().visitPacks().isEmpty());assertTrue(db.dao().trash().isEmpty())
        assertEquals(1,db.dao().medications().size);assertNotNull(lab)
    }

    @Test fun aDailyValueEnteredAgainIsSwappedIntoTheBinOnRestore()=runBlocking {
        val item=repo.checkinItems().first().id
        repo.setScore(day,item,2);repo.setScore(day,item,null);repo.setScore(day,item,5)
        repo.restoreTrash(only().id)
        assertEquals(2,db.dao().scores(day.toString(),day.toString()).single{it.item_id==item}.value)
        assertEquals(1,repo.trash().size) // the 5 is now in the bin
    }

    @Test fun recordsAreRestoredOrPurgedAndLedgerRecordsAreHiddenInstead()=runBlocking {
        val id=db.dao().insertMedication(med)
        val imported=db.dao().record(RecordEntity(medication_id=id,taken_utc=day.atTime(8,0).atZone(zone).toInstant().toEpochMilli(),taken_zone=zone.id,actual_dose=1.0,status="ON_TIME",origin="IMPORT_HT",source_record_key="ht:synthetic:trash",revision=1,config_snapshot="{}"))
        repo.addContainers(id,30.0,1,true)
        repo.unscheduled(id,day.atTime(20,0).atZone(zone).toInstant(),1.0);val stocked=db.dao().records().single{it.origin=="APP"}.id
        repo.deleteRecord(imported);repo.deleteRecord(stocked)
        assertEquals(2,repo.trash().size)
        val bin=repo.trash().associateBy{it.ref.toLong()}
        repo.restoreTrash(bin.getValue(stocked).id);assertNull(db.dao().recordById(stocked)!!.deleted_at_utc)
        assertEquals(1.0,repo.containers().single().used_amount,1e-9)
        repo.deleteRecord(stocked)
        assertTrue(repo.purgeTrash(repo.trash().single{it.ref=="$imported"}.id));assertNull(db.dao().recordById(imported))
        assertFalse(repo.purgeTrash(repo.trash().single{it.ref=="$stocked"}.id));assertNotNull(db.dao().recordById(stocked))
        assertTrue(repo.trash().isEmpty());assertEquals(Trash.HIDDEN,db.dao().trash().single().state)
        assertEquals(0.0,repo.containers().single().used_amount,1e-9)
    }

    @Test fun periodsAndCorrectionsUseTheBinAndPurgeRemovesTheUsersOwnRows()=runBlocking {
        val id=db.dao().insertMedication(med)
        val twice=TherapyStandard("E2","E2","SUBLINGUAL","MG",null,"EVERY_N_DAYS",1,0,listOf(1.0,1.0))
        fun row(kind:String,a:Long,b:Long,s:TherapyStandard?=twice)=TimelineEditRow(kind,id,s.takeIf{kind==HistoryPeriods.PERIOD},day.plusDays(a),day.plusDays(b),zone,"{}")
        val created=repo.editTimeline(emptyList(),listOf(row(HistoryPeriods.PERIOD,0,30)))
        val key=HistoryPeriods.userEdits(repo.historyPeriods()).single().period_key
        repo.deletePeriod(listOf(key),listOf(row(HistoryPeriods.DELETED,0,30,null)))
        assertEquals(Trash.PERIOD,only().kind)
        repo.restoreTrash(only().id);assertEquals(listOf(HistoryPeriods.PERIOD),HistoryPeriods.userEdits(repo.historyPeriods()).map{it.kind})
        // Delete again, purge: the user's period is really gone; nothing system-made was under it.
        repo.deletePeriod(listOf(key),listOf(row(HistoryPeriods.DELETED,0,30,null)))
        assertTrue(repo.purgeTrash(only().id,systemUnderneath=false))
        assertTrue(repo.historyPeriods().none{it.period_key==key});assertTrue(HistoryPeriods.userEdits(repo.historyPeriods()).isEmpty())
        // A deletion over system-made plans is hidden for good, its marker kept.
        repo.deletePeriod(emptyList(),listOf(row(HistoryPeriods.DELETED,40,50,null)))
        assertFalse(repo.purgeTrash(only().id,systemUnderneath=true))
        assertEquals(listOf(HistoryPeriods.DELETED),HistoryPeriods.userEdits(repo.historyPeriods()).map{it.kind});assertTrue(repo.trash().isEmpty())
        // A correction: deleted into the bin, restored, then purged with its revisions.
        val fix=repo.editTimeline(emptyList(),listOf(row(HistoryPeriods.PERIOD,60,70)))
        deleteLegacyCorrection(fix);assertTrue(HistoryPeriods.userEdits(repo.historyPeriods()).none{it.group_key==fix})
        repo.restoreTrash(only().id);assertTrue(HistoryPeriods.userEdits(repo.historyPeriods()).any{it.group_key==fix})
        deleteLegacyCorrection(fix);assertTrue(repo.purgeTrash(only().id));assertTrue(repo.historyPeriods().none{it.group_key==fix})
        try{db.openHelper.writableDatabase.execSQL("DELETE FROM history_period_revision");fail()}catch(_:Exception){}
        assertNotNull(created)
    }

    private suspend fun deleteLegacyCorrection(group:String) {
        val day=HistoryPeriods.userEdits(repo.historyPeriods()).filter{it.group_key==group}.minOf{it.from_date}
        repo.undoTimelineEdit(group)
        Trash.insert(db.openHelper.writableDatabase,Trash.CORRECTION,group,day,org.json.JSONObject(),Instant.now().toEpochMilli())
    }

    @Test fun binSurvivesBackupAndOlderBackupsFillIt()=runBlocking {
        val id=db.dao().insertMedication(med)
        val r=db.dao().record(RecordEntity(medication_id=id,taken_utc=day.atTime(8,0).atZone(zone).toInstant().toEpochMilli(),taken_zone=zone.id,actual_dose=1.0,status="ON_TIME",origin="IMPORT_HT",source_record_key="ht:synthetic:backup",revision=1,config_snapshot="{}"))
        repo.saveMilestone(MilestoneEntity(date=day.toString(),title="Synthetic"));repo.deleteMilestone(db.dao().milestones().single().id)
        repo.deleteRecord(r)
        repo.deletePeriod(emptyList(),listOf(TimelineEditRow(HistoryPeriods.DELETED,id,null,day,day.plusDays(3),zone,"{}")))
        val bin=repo.trash();val pwd="synthetic-pass".toCharArray();val backup=repo.exportBackup(pwd)
        repo.restoreBackup(backup,pwd);assertEquals(bin,repo.trash())
        // A schema 8 backup has no bin: its soft-deleted record and deleted period move in; the milestone was really deleted then.
        val v8=BackupCodec.encrypt(JSONObject(String(BackupCodec.decrypt(backup,pwd))).apply{put("schema",8);getJSONObject("tables").remove("trash_item")}.toString().toByteArray(),pwd)
        repo.restoreBackup(v8,pwd)
        assertEquals(setOf(Trash.RECORD,Trash.PERIOD),repo.trash().map{it.kind}.toSet())
        // A purged item stays purged in a later backup.
        repo.purgeTrash(repo.trash().single{it.kind==Trash.RECORD}.id)
        val after=repo.exportBackup(pwd);repo.restoreBackup(after,pwd)
        assertNull(db.dao().recordById(r));assertEquals(listOf(Trash.PERIOD),repo.trash().map{it.kind})
    }
}
