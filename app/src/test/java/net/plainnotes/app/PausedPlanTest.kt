package net.plainnotes.app

import net.plainnotes.app.data.*
import net.plainnotes.app.timeline.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

/** REQUIREMENTS §36 (revised): an explicit stop in the app is kept; a gap that only lacks records is not a stop. Synthetic data. */
class PausedPlanTest {
    private val zone=ZoneId.of("Asia/Shanghai")
    private val now=Instant.parse("2026-10-20T04:00:00Z")
    private val day0=LocalDate.of(2026,6,1)
    private val med=MedicationEntity(1,"Synthetic E2","E2","SUBLINGUAL","MG",2.0,40.0,site_rotation=false,notifications_on=false,active=true,sort_order=0)
    private val json=MedicationSnapshot.encode(med,ProfileEntity(1,"E2","sublingual",sl_tier=2))
    private fun at(day:Long,hour:Int=8)=day0.plusDays(day).atTime(hour,0).atZone(zone).toInstant()
    private fun version(id:Long,from:Instant,until:Instant?):RegimenVersionEntity {
        val d=RegimenDefinition(json,"EVERY_N_DAYS",1,0,2.0,zone.id,day0.toString(),null,listOf("08:00:00" to null,"20:00:00" to null))
        return RegimenVersionEntity(id,1,from.toEpochMilli(),until?.toEpochMilli(),zone.id,d.json(),d.signature(),"APP",from.toEpochMilli())
    }
    private fun record(id:Long,day:Long,hour:Int)=RecordEntity(id,1,taken_utc=at(day,hour).toEpochMilli(),taken_zone=zone.id,actual_dose=2.0,status="ON_TIME",
        origin="IMPORT_HT",source_record_key="ht:synthetic:pause:$id",revision=1,config_snapshot=json)

    @Test fun savedPlanStoppedForThreeWeeksAndResumedIsTwoPeriodsWithTheStopShown() {
        // Stopped in the app on day 30, resumed with the same standard on day 51.
        val extra=NotesViewModel.ExtraState(regimens=listOf(version(1,at(0,0),at(30,0)),version(2,at(51,0),null)))
        val view=PeriodTimelineProjection.build(extra,emptyList(),now)
        val withPlan=view.projection.periods.filter{it.finalStandardSpanKeys.isNotEmpty()}
        assertEquals(2,withPlan.size)
        assertEquals(3,view.projection.periods.size)
        val stop=view.projection.periods[1]
        assertEquals(at(30,0),stop.from);assertEquals(at(51,0),stop.until);assertTrue(stop.finalStandardSpanKeys.isEmpty())
        assertEquals(listOf(net.plainnotes.app.domain.TreatmentStop(1,at(30,0),at(51,0))),view.projection.stops)
        assertEquals(listOf(net.plainnotes.app.domain.PeriodChange(1,setOf(net.plainnotes.app.domain.ChangeKind.STOPPED))),view.projection.changes(stop))
    }

    @Test fun threeWeeksWithoutRecordsIsOnePeriod() {
        var id=1L
        val rows=((0L..29L)+(51L..80L)).flatMap{d->listOf(8,20).map{h->record(id++,d,h)}}
        val view=PeriodTimelineProjection.build(NotesViewModel.ExtraState(records=rows),emptyList(),now)
        // After the last record the history simply ends (an empty "unknown" stretch); only one period has a standard.
        val withPlan=view.projection.periods.filter{it.finalStandardSpanKeys.isNotEmpty()}
        assertEquals(1,withPlan.size);assertEquals(1,view.projection.standards.size)
        rows.forEach{assertEquals(withPlan.single().key,view.projection.periodAt(Instant.ofEpochMilli(it.taken_utc!!))?.key)}
    }
}
