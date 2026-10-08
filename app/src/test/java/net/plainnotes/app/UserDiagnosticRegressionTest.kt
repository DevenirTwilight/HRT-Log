package net.plainnotes.app

import net.plainnotes.app.data.*
import net.plainnotes.app.timeline.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

/**
 * REQUIREMENTS §36c: rebuilt from the structure in the user's build 21 merge diagnostics (timestamps, sources and
 * standards only; every record is synthetic). A plan saved as "every 11 days" and corrected to "every 1 day" 64 minutes
 * later, with no record inside it, is a correction and must not split the period.
 */
class UserDiagnosticRegressionTest {
    private val zone=ZoneId.of("Europe/Paris")
    private val now=Instant.parse("2026-10-20T10:00:00Z")
    private val med=MedicationEntity(1,"Synthetic E2","E2","SUBLINGUAL","MG",2.0,40.0,site_rotation=false,notifications_on=false,active=true,sort_order=0)
    private val json=MedicationSnapshot.encode(med,ProfileEntity(1,"E2","sublingual",sl_tier=2))
    private val v1From=Instant.ofEpochMilli(1791297124220);private val v2From=Instant.ofEpochMilli(1791300980865);private val v3From=Instant.ofEpochMilli(1791382559405)
    private fun version(id:Long,from:Instant,until:Instant?,interval:Int):RegimenVersionEntity {
        val d=RegimenDefinition(json,"EVERY_N_DAYS",interval,0,2.0,zone.id,"2026-10-06",null,listOf("08:00:00" to null,"20:00:00" to null))
        return RegimenVersionEntity(id,1,from.toEpochMilli(),until?.toEpochMilli(),zone.id,d.json(),d.signature(),"LEGACY_RULE",null)
    }
    private val versions=listOf(version(1,v1From,v2From,11),version(2,v2From,v3From,1),version(3,v3From,null,1))
    private val confirmed=HistoryPeriodEntity(1,"00000000-0000-4000-8000-000000000036",3,HistoryPeriods.CONFIRMED,1,json,
        HistoryPeriods.standardJson(net.plainnotes.app.domain.TherapyStandard("E2","E2","SUBLINGUAL","MG",null,"EVERY_N_DAYS",1,0,listOf(2.0,2.0))),
        "2026-02-26",null,zone.id,"{}",created_utc=1L)
    private fun records(extraInsideV1:Boolean):List<RecordEntity> {
        var id=1L
        val days=generateSequence(LocalDate.of(2026,2,26)){it.plusDays(1)}.takeWhile{it<LocalDate.of(2026,10,19)}
        return days.flatMap{d->listOf(8,20).map{h->d.atTime(h,0).atZone(zone).toInstant()}}
            .filter{at->extraInsideV1 || at !in v1From..v2From}
            .plus(if(extraInsideV1)listOf(v1From.plusSeconds(600)) else emptyList())
            .map{at->val app=at>=v1From
                RecordEntity(id++,1,taken_utc=at.toEpochMilli(),taken_zone=zone.id,actual_dose=2.0,status="ON_TIME",origin=if(app)"APP" else "IMPORT_HT",
                    source_record_key=if(app)null else "ht:synthetic:diag:$id",revision=1,config_snapshot=json)}.toList()
    }
    private fun view(rows:List<RecordEntity>)=PeriodTimelineProjection.build(NotesViewModel.ExtraState(records=rows,regimens=versions,historyPeriods=listOf(confirmed)),emptyList(),now)

    @Test fun planCorrectedAfterSixtyFourMinutesWithoutRecordsIsOnePeriod() {
        val rows=records(false);val v=view(rows)
        assertEquals(v.projection.raw.map{"${it.span.id} ${it.span.from}..${it.span.until} ${it.standard.interval}"}.toString(),1,v.projection.periods.size)
        rows.forEach{assertEquals(v.projection.periods.single().key,v.projection.periodAt(Instant.ofEpochMilli(it.taken_utc!!))?.key)}
        val text=MergeDiagnostics.text(v,NotesViewModel.ExtraState(records=rows,regimens=versions,historyPeriods=listOf(confirmed)),v.projection.periods.single(),22)
        assertTrue(text,text.contains("compared_as (correction)") && text.contains("records_inside=0"))
    }
    @Test fun evenWithADoseLoggedUnderTheShortPlanTheFourteenDayRuleKeepsOnePeriod() {
        // REQUIREMENTS §37a: a saved version shorter than 14 days between parts with the same standard joins them.
        val rows=records(true);val v=view(rows)
        assertEquals(v.projection.raw.map{"${it.span.id} ${it.span.from}..${it.span.until} ${it.standard.interval}"}.toString(),1,v.projection.periods.size)
        rows.forEach{assertEquals(v.projection.periods.single().key,v.projection.periodAt(Instant.ofEpochMilli(it.taken_utc!!))?.key)}
        // Every record, including the one under the every-11-days version, is judged by the merged twice-daily standard.
        val labels=HistoryLabels.build(rows,v,emptyList(),zone)
        assertTrue(labels.toString(),labels.isEmpty() || labels.keys.all{id->rows.single{it.id==id}.taken_utc!!.let{t->Instant.ofEpochMilli(t).atZone(zone).toLocalDate()==LocalDate.of(2026,10,6)}})
        assertEquals(net.plainnotes.app.domain.TherapyStandard("E2","E2","SUBLINGUAL","MG",null,"EVERY_N_DAYS",11,0,listOf(2.0,2.0)),v.projection.absorbed[1L])
        assertEquals(1,v.coverage[rows.single{it.taken_utc==v1From.plusSeconds(600).toEpochMilli()}.id]?.interval?.standard?.interval)
    }
    @Test fun aShortVersionBetweenDifferentStandardsOrAtTheEndStaysVisible() {
        val endOnly=listOf(version(1,v1From,v2From,1),version(2,v2From,null,11))
        val v=PeriodTimelineProjection.build(NotesViewModel.ExtraState(records=records(false),regimens=endOnly,historyPeriods=listOf(confirmed)),emptyList(),now)
        assertTrue(v.projection.absorbed.isEmpty());assertEquals(2,v.projection.periods.size)
    }
}
