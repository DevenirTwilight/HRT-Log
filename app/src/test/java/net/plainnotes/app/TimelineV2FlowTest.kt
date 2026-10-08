package net.plainnotes.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import net.plainnotes.app.data.*
import net.plainnotes.app.domain.*
import net.plainnotes.app.timeline.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.*

@RunWith(RobolectricTestRunner::class) @Config(sdk=[28],application=android.app.Application::class)
class TimelineV2FlowTest {
    private lateinit var db:NotesDatabase;private lateinit var repo:NotesRepository
    private val zone=ZoneId.of("Europe/Paris");private val d0=LocalDate.of(2026,3,1)
    private fun at(n:Long)=d0.plusDays(n).atStartOfDay(zone).toInstant()
    private val now=at(150)
    private var med=0L
    private lateinit var identity:String
    private val standard=TherapyStandard("E2","E2","SUBLINGUAL","MG",null,"EVERY_N_DAYS",1,0,listOf(2.0,2.0))
    @Before fun open()=runBlocking {
        val c=ApplicationProvider.getApplicationContext<Context>()
        db=Room.inMemoryDatabaseBuilder(c,NotesDatabase::class.java).allowMainThreadQueries().addCallback(SchemaGuards).build()
        repo=NotesRepository(object:DatabaseAccess(c){override fun get(space:Space)=db})
        val m=MedicationEntity(name="Synthetic E2",molecule="E2",route="SUBLINGUAL",unit="MG",dose_per_intake=2.0,container_capacity=40.0,site_rotation=false,notifications_on=false,active=true,sort_order=0)
        med=db.dao().insertMedication(m);identity=MedicationSnapshot.encode(m.copy(id=med),ProfileEntity(med,"E2","sublingual"))
        val d=RegimenDefinition(identity,"EVERY_N_DAYS",1,0,2.0,zone.id,d0.toString(),null,listOf("08:00:00" to null,"20:00:00" to null))
        db.dao().regimen(RegimenVersionEntity(medication_id=med,effective_from_utc=at(0).toEpochMilli(),zone=zone.id,definition_json=d.json(),clinical_signature=d.signature(),origin="APP",recorded_at_utc=at(0).toEpochMilli()))
        (0L..89).forEach{day->listOf(8L,20L).forEach{h->db.dao().record(RecordEntity(medication_id=med,taken_utc=at(day).plusSeconds(h*3600).toEpochMilli(),taken_zone=zone.id,
            actual_dose=2.0,status="ON_TIME",origin="IMPORT_HT",source_record_key="ht:synthetic:v2:$day:$h",revision=1,config_snapshot=identity))}}
    }
    @After fun close(){db.close()}
    private suspend fun extra()=NotesViewModel.ExtraState(records=repo.records(),regimens=db.dao().regimens(),historyPeriods=repo.historyPeriods())
    private suspend fun view()=PeriodTimelineProjection.build(extra(),emptyList(),now)
    private fun range(a:Long,b:Long?)=TimelineEdits.Range(d0.plusDays(a),b?.let{d0.plusDays(it)})
    private suspend fun save(a:Long,b:Long?,target:TimelineEdits.Range?=null,dose:Double=3.0) {
        val e=extra();val edit=TimelineEdits.saveV2(e.historyPeriods,view().projection,med,standard.copy(doses=listOf(dose,dose)),range(a,b),identity,target)
        repo.editTimeline(edit.replace,edit.rows,now)
    }
    @Test fun systemNeighbourIsSplitIntoFixedUserPeriodsWithoutChangingFacts()=runBlocking {
        val records=repo.records();val versions=db.dao().regimens()
        val changes=TimelineEdits.overlapChanges(view().projection,med,range(20,40),null)
        assertEquals(1,changes.size);assertEquals(listOf(at(0) to at(20),at(40) to null),changes.single().remaining)
        save(20,40)
        assertEquals(listOf(at(0),at(20),at(40)),view().projection.standards.map{it.from})
        assertTrue(view().projection.raw.all{it.kind==SpanKind.USER})
        assertEquals(records,repo.records());assertEquals(versions,db.dao().regimens())
        val before=view().projection;val pwd="synthetic-only".toCharArray()
        repo.restoreBackup(repo.exportBackup(pwd),pwd);assertEquals(before,view().projection)
    }
    @Test fun shorteningOwnPeriodExposesSystemAndDeletingRestoresTheSameIdentity()=runBlocking {
        save(0,60,range(0,null))
        save(20,40,range(0,60))
        val p=view().projection
        assertEquals(SpanKind.SAVED,p.exactAt(at(10)).single().kind)
        assertEquals(SpanKind.SAVED,p.exactAt(at(50)).single().kind)
        val before=view();val own=before.projection.periodAt(at(30))!!
        val del=TimelineEdits.delete(repo.historyPeriods(),before.projection,own,{identity})!!
        repo.deletePeriod(del.replace,del.rows,now)
        assertTrue(view().projection.periodAt(at(30))!!.finalStandardSpanKeys.isEmpty())
        repo.restoreTrash(repo.trash().single().id,now)
        assertEquals(before.projection,view().projection)
    }
    @Test fun fullyCoveredPeriodGoesToBinAndCannotRestoreOverANewUserPeriod()=runBlocking {
        save(0,40,range(0,null))
        save(0,50)
        val item=repo.trash().single();assertEquals(Trash.PERIOD,item.kind)
        val before=repo.historyPeriods()
        try{repo.restoreTrash(item.id,now);fail("Overlapping restoration must fail")}catch(_:PeriodRestoreConflict){}
        assertEquals(before,repo.historyPeriods());assertEquals(listOf(item),repo.trash())
        val current=view();val p=current.projection.periodAt(at(10))!!
        val del=TimelineEdits.delete(repo.historyPeriods(),current.projection,p,{identity})!!
        repo.deletePeriod(del.replace,del.rows,now)
        repo.restoreTrash(item.id,now)
        assertEquals(listOf(3.0,3.0),view().projection.exactAt(at(10)).single().standard.doses)
    }
    @Test fun oldConfirmedHistoryConvertsOnceAndOldBinRemainsRestorable()=runBlocking {
        repo.confirmHistoryPeriod(null,med,standard,d0.minusDays(30),null,zone,identity,"{}",now)
        val e=extra();val old=PeriodTimelineProjection.build(e,emptyList(),now,legacy=true).projection
        val conversion=TimelineV2Migration.plan(e,now)!!;repo.migrateTimeline(conversion.replace,conversion.rows,now)
        assertFalse(TimelineV2Migration.needed(repo.historyPeriods()))
        assertEquals(old.periods.map{it.from to it.until},view().projection.periods.map{it.from to it.until})
        assertEquals(old.standards.map{it.standard},view().projection.standards.map{it.standard})
        val pwd="synthetic-only".toCharArray();val backup=repo.exportBackup(pwd)
        repo.restoreBackup(backup,pwd);assertNull(TimelineV2Migration.plan(extra(),now))
    }    @Test fun legacyDeletionRestoresItsConvertedConfirmedBackground()=runBlocking {
        repo.confirmHistoryPeriod(null,med,standard,d0.minusDays(30),null,zone,identity,"{}",now)
        val original=PeriodTimelineProjection.build(extra(),emptyList(),now,legacy=true).projection
        val group=repo.editTimeline(emptyList(),listOf(TimelineEditRow(HistoryPeriods.DELETED,med,null,d0.plusDays(10),d0.plusDays(11),zone,identity)),now)
        Trash.insert(db.openHelper.writableDatabase,Trash.PERIOD,group,d0.plusDays(10).toString(),org.json.JSONObject(),now.toEpochMilli())
        TimelineV2Migration.plan(extra(),now)!!.let{repo.migrateTimeline(it.replace,it.rows,now)}
        assertTrue(view().projection.periodAt(at(10).plusSeconds(3600))!!.finalStandardSpanKeys.isEmpty())
        repo.restoreTrash(repo.trash().single().id,now)
        TimelineV2Migration.plan(extra(),now)?.let{repo.migrateTimeline(it.replace,it.rows,now)}
        assertEquals(original.periods.map{it.from to it.until},view().projection.periods.map{it.from to it.until})
        assertEquals(original.standards.map{it.standard},view().projection.standards.map{it.standard})
        assertTrue(repo.trash().isEmpty());assertFalse(TimelineV2Migration.needed(repo.historyPeriods()))
    }

    @Test fun timelineEditsDoNotRewriteFrozenFactsStockOrReminderMappings()=runBlocking {
        db.dao().profile(ProfileEntity(med,"E2","sublingual",sl_tier=2))
        repo.addContainers(med,60.0,1,true)
        repo.unscheduled(med,at(49).plusSeconds(3600),2.0)
        repo.setWeight(62.0)
        repo.saveLab(LabValueEntity(analyte_code="E2",value=120.0,unit="pg/mL",sampled_utc=at(50).toEpochMilli(),sampled_zone=zone.id),now,
            estimate={dao,lab->net.plainnotes.app.conc.LabEstimate.capture(dao,lab)})
        val context=repo.labContexts().single()
        assertTrue(org.json.JSONObject(context.context_json).getJSONObject("estimate").has("parameter_document"))
        val appointment=repo.saveAppointment(AppointmentEntity(type="ENDO",at_utc=at(100).toEpochMilli(),at_zone=zone.id,remind_minutes_before=60))
        repo.recordVisitPack(VisitPackEntity(appointment_id=appointment,generated_utc=now.toEpochMilli(),zone=zone.id,range_from=d0.toString(),range_to=d0.plusDays(90).toString(),
            sections="FACTS",language="en",template_version=3,input_digest="a".repeat(64),facts_json="{\"intakes\":181}"))
        db.dao().mapping(ReminderMappingEntity("synthetic-reminder","synthetic-generation","synthetic-identity",at(151).toEpochMilli(),false))
        val protected=listOf("medication","pk_profile","schedule_rule","rule_time","dose_record","supply_container","supply_transaction","regimen_version","regimen_rule_link",
            "lab_value","lab_context_revision","appointment","visit_pack","reminder_mapping","pk_settings")
        fun frozen()=protected.associateWith{table->Trash.rows(db.openHelper.writableDatabase,table,"1=1 ORDER BY rowid",emptyArray()).toString()}
        val original=frozen()
        save(20,40);assertEquals(original,frozen())
        save(25,35,range(20,40));assertEquals(original,frozen())
        val current=view();val own=current.projection.periodAt(at(30))!!
        val del=TimelineEdits.delete(repo.historyPeriods(),current.projection,own,{identity})!!
        repo.deletePeriod(del.replace,del.rows,now);assertEquals(original,frozen())
        repo.restoreTrash(repo.trash().single().id,now);assertEquals(original,frozen())
    }

}
