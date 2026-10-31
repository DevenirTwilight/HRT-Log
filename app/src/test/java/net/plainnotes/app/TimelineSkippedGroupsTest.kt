package net.plainnotes.app

import net.plainnotes.app.data.RecordEntity
import net.plainnotes.app.timeline.*
import net.plainnotes.app.ui.TimelineSkippedGroups
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class TimelineSkippedGroupsTest {
    private val zone=ZoneId.of("Europe/Paris")
    private val first=LocalDate.of(2026,10,24)
    private fun event(id:Long,day:Long=0,hour:Int=8,med:Long=1,period:String="period",rule:Long=7):PeriodEvent {
        val at=first.plusDays(day).atTime(hour,0).atZone(zone).toInstant()
        val r=RecordEntity(id,med,rule_version_id=rule,slot_key="slot:$id",scheduled_utc=at.toEpochMilli(),
            scheduled_zone=zone.id,planned_dose=2.0,status="SKIPPED",origin="APP",revision=1,config_snapshot="frozen:$med")
        return PeriodEvent("skip:$id",EventKind.SKIPPED,at,at.atZone(zone).toLocalDate(),EventSource.Intake(r,null),period,setOf(rule),false)
    }
    @Test fun fiveDaysTwiceDailyBecomeOneSummaryRetainingAllTenOriginalEvents() {
        val rows=(0L..4).flatMap{day->listOf(event(day*2+1,day),event(day*2+2,day,20))}
        val g=TimelineSkippedGroups.build(rows.reversed()).single()
        assertEquals(10,g.events.size);assertEquals(rows.reversed(),g.events)
        assertEquals(first,g.from);assertEquals(first.plusDays(4),g.through)
        assertEquals("skip-group:skip:1",g.key)
        assertTrue(g.events.all{(it.source as EventSource.Intake).record.taken_utc==null})
        assertTrue(g.events.all{(it.source as EventSource.Intake).record.actual_dose==null})
    }
    @Test fun singleSkipStaysAnIndividualRow() { assertTrue(TimelineSkippedGroups.build(listOf(event(1))).isEmpty()) }
    @Test fun twoDifferentMedicinesNeverShareOneSummary() {
        val rows=listOf(event(1),event(2,hour=20),event(3,med=2),event(4,hour=20,med=2))
        val gs=TimelineSkippedGroups.build(rows);assertEquals(2,gs.size)
        assertTrue(gs.all{it.events.map{e->(e.source as EventSource.Intake).record.medication_id}.distinct().size==1})
    }
    @Test fun treatmentBoundaryAndRuleVersionRemainSeparate() {
        val rows=listOf(event(1),event(2,hour=20),event(3,1,period="other"),event(4,1,20,period="other"),event(5,2,rule=8),event(6,2,20,rule=8))
        assertEquals(listOf(2,2,2),TimelineSkippedGroups.build(rows).map{it.events.size})
    }
    @Test fun aDateGapStartsAnotherRun() {
        val rows=listOf(event(1),event(2,hour=20),event(3,2),event(4,2,20))
        assertEquals(2,TimelineSkippedGroups.build(rows).size)
    }
    @Test fun dstIsComparedAsLocalDatesNotTwentyFourHourDurations() {
        val rows=listOf(event(1,hour=20),event(2,1,20))
        assertEquals(25,Duration.between(rows[0].at,rows[1].at).toHours())
        assertEquals(2,TimelineSkippedGroups.build(rows).single().events.size)
    }
    @Test fun undoChangesOnlyTheCountAndNeverSynthesizesEvents() {
        val rows=(0L..4).flatMap{listOf(event(it*2+1,it),event(it*2+2,it,20))}
        val remaining=rows.filterNot{it.key=="skip:5"}
        assertEquals(remaining.toSet(),TimelineSkippedGroups.build(remaining).single().events.toSet())
        assertTrue(TimelineSkippedGroups.build(emptyList()).isEmpty())
    }
    @Test fun importedDeletedUnlinkedOrUnverifiableSkipsAreNotGrouped() {
        val base=event(1)
        val r=(base.source as EventSource.Intake).record
        val invalid=listOf(r.copy(origin="IMPORT_HT"),r.copy(origin="IMPORT_TM"),r.copy(origin="IMPORT_FEATHERLINE"),
            r.copy(deleted_at_utc=1),r.copy(slot_key=""),r.copy(rule_version_id=null),r.copy(scheduled_zone="broken"),r.copy(status="ON_TIME"))
        invalid.forEach{bad->assertTrue(TimelineSkippedGroups.build(listOf(base,base.copy(key="bad",source=EventSource.Intake(bad,null)))).isEmpty())}
        assertTrue(TimelineSkippedGroups.build(listOf(base.copy(displayPeriodKey=null),event(2).copy(displayPeriodKey=null))).isEmpty())
    }
    @Test fun overridesRemainExactAndOrderDoesNotChangeGroupIdentity() {
        val first=event(1);val second=event(2,hour=20);val r=(second.source as EventSource.Intake).record
        val changed=second.copy(source=EventSource.Intake(r.copy(planned_dose=3.0),null))
        val a=TimelineSkippedGroups.build(listOf(first,changed)).single();val b=TimelineSkippedGroups.build(listOf(changed,first)).single()
        assertEquals(a,b);assertEquals(listOf(3.0,2.0),a.events.map{(it.source as EventSource.Intake).record.planned_dose})
    }
    @Test fun frozenIdentityAndZoneChangesDoNotSilentlyMerge() {
        val a=event(1);val b=event(2,hour=20)
        fun change(e:PeriodEvent,zone:String,snapshot:String):PeriodEvent {
            val r=(e.source as EventSource.Intake).record
            return e.copy(key=e.key+zone+snapshot,source=EventSource.Intake(r.copy(scheduled_zone=zone,config_snapshot=snapshot),null))
        }
        assertEquals(2,TimelineSkippedGroups.build(listOf(a,b,change(a,"UTC","frozen:1"),change(b,"UTC","frozen:1"))).size)
        assertEquals(2,TimelineSkippedGroups.build(listOf(a,b,change(a,zone.id,"renamed"),change(b,zone.id,"renamed"))).size)
    }
}
