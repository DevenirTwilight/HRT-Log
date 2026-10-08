package net.plainnotes.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import net.plainnotes.app.data.*
import net.plainnotes.app.domain.TherapyStandard
import net.plainnotes.app.timeline.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.*

/** REQUIREMENTS §37b end to end: edits through the repository, shown by the projection, surviving reopen and restore. Synthetic data. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[28],application=android.app.Application::class)
class TimelineEditFlowTest {
    private val context=ApplicationProvider.getApplicationContext<Context>()
    private val name="timeline-edit-flow.db"
    private lateinit var db:NotesDatabase;private lateinit var repo:NotesRepository
    private val zone=ZoneId.of("UTC");private val now=Instant.parse("2026-10-20T00:00:00Z");private val d0=LocalDate.of(2026,6,1)
    private val med=MedicationEntity(name="Synthetic E2",molecule="E2",route="SUBLINGUAL",unit="MG",dose_per_intake=2.0,container_capacity=40.0,site_rotation=false,notifications_on=false,active=true,sort_order=0)
    private fun open(){db=Room.databaseBuilder(context,NotesDatabase::class.java,name).allowMainThreadQueries().addCallback(SchemaGuards).build();repo=NotesRepository(object:DatabaseAccess(context){override fun get(space:Space)=db})}
    @Before fun start(){context.deleteDatabase(name);open()}
    @After fun stop(){db.close();context.deleteDatabase(name)}
    private fun at(n:Long,h:Int=0)=d0.plusDays(n).atTime(h,0).atZone(zone).toInstant()
    private fun range(a:Long,b:Long?)=TimelineEdits.Range(d0.plusDays(a),b?.let{d0.plusDays(it)})
    private lateinit var json:String;private var id=0L
    private suspend fun setup() {
        id=db.dao().insertMedication(med);json=MedicationSnapshot.encode(med.copy(id=id),null)
        (0L..89L).forEach{n->listOf(8,20).forEach{h->db.dao().record(RecordEntity(medication_id=id,taken_utc=at(n,h).toEpochMilli(),taken_zone=zone.id,actual_dose=if(n<45)2.0 else 3.0,
            status="ON_TIME",origin="IMPORT_HT",source_record_key="ht:synthetic:flow:$n:$h",revision=1,config_snapshot=json))}}
    }
    private suspend fun extra()=NotesViewModel.ExtraState(records=repo.records(),regimens=db.dao().regimens(),historyPeriods=repo.historyPeriods())
    private suspend fun view()=PeriodTimelineProjection.build(extra(),emptyList(),now)
    private fun plans(v:PeriodTimeline)=v.projection.periods.filter{it.finalStandardSpanKeys.isNotEmpty()}
    private fun std(dose:Double,interval:Int=1)=TherapyStandard("E2",null,"SUBLINGUAL","MG",null,"EVERY_N_DAYS",interval,0,listOf(dose,dose))
    private suspend fun apply(e:TimelineEdits.Edit?)=repo.editTimeline(e!!.replace,e.rows)

    @Test fun createEditSplitMergeDeleteRestoreAndUndo()=runBlocking {
        setup()
        val start=view();assertEquals(2,plans(start).size)
        // Merge the two recognised periods with different standards: the user's choice (the later one) is used.
        val first=plans(start)[0];val second=plans(start)[1]
        assertNotEquals(TimelineEdits.standardOf(start.projection,first,id),TimelineEdits.standardOf(start.projection,second,id))
        val target=TimelineEdits.Range(TimelineEdits.rangeOf(first,zone).from,TimelineEdits.rangeOf(second,zone).until)
        val merge=apply(TimelineEdits.period(repo.historyPeriods(),id,std(3.0),target,zone,json,listOf(target)))
        val merged=view();assertEquals(1,plans(merged).size);assertEquals(listOf(3.0,3.0),merged.projection.standards.single().standard.doses)
        // Split on day 20: two periods with the same standard, the user's boundary kept.
        val p0=plans(merged).single()
        assertNotNull("range=${TimelineEdits.rangeOf(p0,zone)} meds=${TimelineEdits.medicationsIn(merged.projection,p0)} raw=${merged.projection.raw.map{"${it.span.id}/${it.span.medicationId} ${it.span.from}..${it.span.until} ${it.kind}"}}",
            TimelineEdits.split(repo.historyPeriods(),merged.projection,p0,d0.plusDays(20)){json})
        val split=apply(TimelineEdits.split(repo.historyPeriods(),merged.projection,p0,d0.plusDays(20)){json})
        assertEquals(2,plans(view()).size)
        // Delete the first part: its records show regimen unknown; restore brings it back.
        val v=view();val del=apply(TimelineEdits.delete(repo.historyPeriods(),v.projection,plans(v).first(),{json}))
        val deleted=view();assertTrue(deleted.projection.periodAt(at(5))?.finalStandardSpanKeys.isNullOrEmpty()) // §39: no card for deleted days
        val labels=HistoryLabels.build(repo.records(),deleted,emptyList(),zone)
        assertEquals(setOf(net.plainnotes.app.domain.RecordLabel.REGIMEN_UNKNOWN),labels[repo.records().first{it.taken_utc==at(5,8).toEpochMilli()}.id])
        repo.undoTimelineEdit(del);assertEquals(2,plans(view()).size)
        // Undo the split and the merge: back to what the system made.
        repo.undoTimelineEdit(split);assertEquals(1,plans(view()).size)
        repo.undoTimelineEdit(merge);assertEquals(start.projection.periods.map{it.from},view().projection.periods.map{it.from})
        assertEquals(90*2,repo.records().size);assertTrue(db.dao().regimens().isEmpty())
    }

    @Test fun createdPeriodAndOverlapChecks()=runBlocking {
        setup()
        val v=view()
        // A new period over days the history already covers for this medicine is refused; another medicine is fine.
        assertTrue(TimelineEdits.conflicts(v.projection,id,range(10,20),null))
        assertFalse(TimelineEdits.conflicts(v.projection,id+1,range(10,20),null))
        // Before the history there is nothing: a created period fills it.
        assertFalse(TimelineEdits.conflicts(v.projection,id,range(-60,-1),null))
        apply(TimelineEdits.period(repo.historyPeriods(),id,std(1.0),range(-60,-1),zone,json))
        assertEquals(3,plans(view()).size)
        // Editing a period may cover its own days but not its neighbour's.
        val first=plans(v).first();val own=TimelineEdits.rangeOf(first,zone)
        assertFalse(TimelineEdits.conflicts(v.projection,id,own,own))
        assertTrue(TimelineEdits.conflicts(v.projection,id,TimelineEdits.Range(own.from,own.until!!.plusDays(5)),own))
    }

    @Test fun savedPlanOverrideAndStopMoveKeepTheStoredVersion()=runBlocking {
        setup()
        repo.saveMedication(med.copy(id=id,soon_alert_minutes=15,late_after_minutes=60),null,net.plainnotes.app.domain.RuleKind.EVERY_N_DAYS,1,
            listOf(LocalTime.of(8,0),LocalTime.of(20,0)),emptySet(),at(90))
        repo.saveMedication(med.copy(id=id,soon_alert_minutes=15,late_after_minutes=60,active=false),null,net.plainnotes.app.domain.RuleKind.EVERY_N_DAYS,1,
            listOf(LocalTime.of(8,0),LocalTime.of(20,0)),emptySet(),at(100))
        val versions=db.dao().regimens();val v=view()
        val stop=v.projection.stops.single();assertEquals(at(100),stop.from)
        apply(TimelineEdits.moveStop(repo.historyPeriods(),v.projection,stop,id,range(103,null),json))
        val moved=view();assertEquals(at(103),moved.projection.stops.single().from)
        // Days 100–102 are no longer a stop: a fill continues the plan before it.
        assertTrue(moved.projection.periodAt(at(101))!!.finalStandardSpanKeys.isNotEmpty())
        assertEquals(versions,db.dao().regimens());versions.forEach{RegimenDefinition.read(it.definition_json)}
    }

    @Test fun editsSurviveReopeningAndBackupRestore()=runBlocking {
        setup()
        apply(TimelineEdits.period(repo.historyPeriods(),id,std(2.0,2),range(0,45),zone,json,listOf(range(0,45))))
        val before=view().projection
        db.close();open()
        assertEquals(before,view().projection)
        val pwd="synthetic-pass".toCharArray();val backup=repo.exportBackup(pwd);repo.restoreBackup(backup,pwd)
        assertEquals(before,view().projection)
    }
}
