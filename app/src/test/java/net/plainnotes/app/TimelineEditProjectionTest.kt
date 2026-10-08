package net.plainnotes.app

import net.plainnotes.app.data.*
import net.plainnotes.app.domain.RecordLabel
import net.plainnotes.app.domain.SpanKind
import net.plainnotes.app.domain.TherapyStandard
import net.plainnotes.app.timeline.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

/** REQUIREMENTS §37b: user edits come first on the timeline and never change stored plans or records. Synthetic data. */
class TimelineEditProjectionTest {
    private val zone=ZoneId.of("UTC")
    private val now=Instant.parse("2026-10-20T00:00:00Z")
    private val d0=LocalDate.of(2026,6,1)
    private val med=MedicationEntity(1,"Synthetic E2","E2","SUBLINGUAL","MG",2.0,40.0,site_rotation=false,notifications_on=false,active=true,sort_order=0)
    private val json=MedicationSnapshot.encode(med,ProfileEntity(1,"E2","sublingual",sl_tier=2))
    private val twice=TherapyStandard("E2","E2","SUBLINGUAL","MG",null,"EVERY_N_DAYS",1,0,listOf(2.0,2.0))
    private fun day(n:Long)=d0.plusDays(n)
    private fun at(n:Long,h:Int=0)=day(n).atTime(h,0).atZone(zone).toInstant()
    private val definition=RegimenDefinition(json,"EVERY_N_DAYS",1,0,2.0,zone.id,d0.toString(),null,listOf("08:00:00" to null,"20:00:00" to null))
    private val version=RegimenVersionEntity(1,1,at(0).toEpochMilli(),null,zone.id,definition.json(),definition.signature(),"APP",1L)
    private val records=(0L..80L).flatMap{n->listOf(8,20).mapIndexed{i,h->RecordEntity(n*2+i+1,1,scheduled_utc=at(n,h).toEpochMilli(),scheduled_zone=zone.id,
        taken_utc=at(n,h).toEpochMilli(),taken_zone=zone.id,actual_dose=2.0,status="ON_TIME",origin="APP",revision=1,config_snapshot=json)}}
    private var nextId=1L
    private fun edit(kind:String,from:Long,until:Long?,standard:TherapyStandard?=twice,group:String="00000000-0000-4000-8000-0000000000${10+nextId}")=
        HistoryPeriodEntity(nextId++,java.util.UUID.randomUUID().toString(),1,HistoryPeriods.CONFIRMED,1,json,standard?.let(HistoryPeriods::standardJson) ?: "{}",
            day(from).toString(),until?.let{day(it).toString()},zone.id,"{}",HistoryPeriods.USER_ORIGIN,1L,kind,group)
    private fun view(edits:List<HistoryPeriodEntity>,regimens:List<RegimenVersionEntity> = listOf(version))=
        PeriodTimelineProjection.build(NotesViewModel.ExtraState(records=records,regimens=regimens,historyPeriods=edits),emptyList(),now)
    private fun plans(v:PeriodTimeline)=v.projection.periods.filter{it.finalStandardSpanKeys.isNotEmpty()}

    @Test fun editingASavedPlanOverridesItForDisplayOnly() {
        val v=view(listOf(edit(HistoryPeriods.PERIOD,10,20,twice.copy(doses=listOf(3.0,3.0)))))
        assertEquals(3,plans(v).size)
        val raw=v.projection.raw.sortedBy{it.span.from}
        assertEquals(listOf(SpanKind.SAVED,SpanKind.USER,SpanKind.SAVED),raw.map{it.kind});assertEquals(listOf(1L,raw[1].sourceId,1L),raw.map{it.sourceId})
        // Records under the edit are judged by it (2 mg taken against 3 mg); the stored version still reads as it was.
        val labels=HistoryLabels.build(records,v,emptyList(),zone)
        val inside=records.filter{Instant.ofEpochMilli(it.taken_utc!!) in at(10)..at(20).minusSeconds(1)}
        assertTrue(inside.all{labels[it.id]==null}) // scheduled app records keep on-time/late; no extra label
        assertEquals(definition,RegimenDefinition.read(version.definition_json))
        // Undoing the edit (no active rows) gives back the plain saved plan.
        assertEquals(1,plans(view(emptyList())).size)
    }

    @Test fun deletedRangeHasNoPlanAndItsRecordsShowRegimenUnknown() {
        val unscheduled=records.map{it.copy(scheduled_utc=null,scheduled_zone=null)}
        val v=PeriodTimelineProjection.build(NotesViewModel.ExtraState(records=unscheduled,regimens=listOf(version),historyPeriods=listOf(edit(HistoryPeriods.DELETED,10,20,null))),emptyList(),now)
        val gap=v.projection.periodAt(at(15))!!;assertTrue(gap.finalStandardSpanKeys.isEmpty())
        val labels=HistoryLabels.build(unscheduled,v,emptyList(),zone)
        unscheduled.filter{Instant.ofEpochMilli(it.taken_utc!!) in at(10)..at(20).minusSeconds(1)}.forEach{assertEquals(setOf(RecordLabel.REGIMEN_UNKNOWN),labels[it.id])}
        assertNull(labels[unscheduled.first().id])
    }

    @Test fun splitKeepsTheBoundaryAndFillJoins() {
        val split=view(listOf(edit(HistoryPeriods.PERIOD,0,30),edit(HistoryPeriods.PERIOD,30,60)),emptyList())
        // Same standard on both sides, still two periods: the user's boundary stays. (Records after day 60 form their own recognised part.)
        assertNotEquals(split.projection.periodAt(at(29))!!.key,split.projection.periodAt(at(30))!!.key)
        val fill=view(listOf(edit(HistoryPeriods.FILL,30,60)),listOf(version.copy(effective_until_utc=at(30).toEpochMilli()),version.copy(id=2,effective_from_utc=at(60).toEpochMilli())))
        assertEquals(1,plans(fill).size)
    }

    @Test fun mergingDifferentStandardsUsesTheChosenOneAndAStopSetByTheUserIsShown() {
        val v1=version.copy(effective_until_utc=at(30).toEpochMilli())
        val d3=RegimenDefinition(json,"EVERY_N_DAYS",1,0,3.0,zone.id,d0.toString(),null,listOf("08:00:00" to null,"20:00:00" to null))
        val v2=RegimenVersionEntity(2,1,at(30).toEpochMilli(),null,zone.id,d3.json(),d3.signature(),"APP",2L)
        assertEquals(2,plans(view(emptyList(),listOf(v1,v2))).size)
        val merged=view(listOf(edit(HistoryPeriods.PERIOD,0,null,twice.copy(doses=listOf(3.0,3.0)))),listOf(v1,v2))
        assertEquals(1,plans(merged).size);assertEquals(listOf(3.0,3.0),merged.projection.standards.single{it.until==null || it.until!!>at(1)}.standard.doses)
        val stopped=view(listOf(edit(HistoryPeriods.STOP,40,45,null)))
        assertEquals(listOf(net.plainnotes.app.domain.TreatmentStop(1,at(40),at(45))),stopped.projection.stops)
        assertEquals(2,plans(stopped).size)
    }

    @Test fun anAbsorbedShortVersionCanBeTakenOutByAnEdit() {
        val d11=RegimenDefinition(json,"EVERY_N_DAYS",11,0,2.0,zone.id,d0.toString(),null,listOf("08:00:00" to null,"20:00:00" to null))
        val versions=listOf(version.copy(effective_until_utc=at(30).toEpochMilli()),
            RegimenVersionEntity(2,1,at(30).toEpochMilli(),at(31).toEpochMilli(),zone.id,d11.json(),d11.signature(),"LEGACY_RULE",null),
            version.copy(id=3,effective_from_utc=at(31).toEpochMilli()))
        val joined=view(emptyList(),versions);assertEquals(1,plans(joined).size);assertTrue(2L in joined.projection.absorbed)
        val out=view(listOf(edit(HistoryPeriods.PERIOD,30,31,twice.copy(interval=11))),versions)
        assertEquals(3,plans(out).size)
    }
}
