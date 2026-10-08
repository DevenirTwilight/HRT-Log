package net.plainnotes.app

import net.plainnotes.app.data.*
import net.plainnotes.app.domain.*
import net.plainnotes.app.timeline.*
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*
import java.time.*
import java.util.UUID

/** Synthetic build-24 history and the §40 invariants; no private data. */
class TimelineV2Test {
    private val zone=ZoneId.of("Europe/Paris")
    private val start=LocalDate.parse("2026-02-26")
    private fun at(day:Int)=start.plusDays(day.toLong()).atStartOfDay(zone).toInstant()
    private val now=at(100)
    private val med=MedicationEntity(id=1,name="Synthetic",molecule="E2",route="SUBLINGUAL",unit="MG",dose_per_intake=2.0,container_capacity=60.0,
        expiry_days_after_open=90,soon_alert_minutes=15,late_after_minutes=60,site_rotation=false,notifications_on=true,active=true,sort_order=0)
    private val identity=MedicationSnapshot.encode(med,ProfileEntity(1,"E2","sublingual",sl_tier=2))
    private val standard=TherapyStandard("E2","E2","SUBLINGUAL","MG",null,"EVERY_N_DAYS",1,0,listOf(2.0,2.0))
    private val definition=RegimenDefinition(identity,"EVERY_N_DAYS",1,0,2.0,zone.id,start.toString(),null,listOf("08:00:00" to null,"20:00:00" to null))
    private fun version(id:Long,from:Int,until:Int?=null)=RegimenVersionEntity(id,1,at(from).toEpochMilli(),until?.let{at(it).toEpochMilli()},zone.id,definition.json(),definition.signature(),"APP",id)
    private fun row(id:Long,from:Int,until:Int?,kind:String=HistoryPeriods.PERIOD,stated:Int?=90)=HistoryPeriodEntity(id=id,period_key=UUID.randomUUID().toString(),revision=1,
        state=HistoryPeriods.CONFIRMED,medication_id=1,identity_json=identity,standard_json=HistoryPeriods.standardJson(standard),from_date=start.plusDays(from.toLong()).toString(),
        until_date=until?.let{start.plusDays(it.toLong()).toString()},zone=zone.id,evidence_json=JSONObject().apply{stated?.let{put("stated_utc",at(it).toEpochMilli())}}.toString(),
        origin=if(kind==HistoryPeriods.CONFIRMED)HistoryPeriods.ORIGIN else HistoryPeriods.USER_ORIGIN,created_utc=at(90).toEpochMilli(),kind=kind)
    private fun records()=(0..79).flatMap{d->listOf(8L,20L).map{h->RecordEntity(id=d*2L+h/12+1,medication_id=1,taken_utc=at(d).plusSeconds(h*3600).toEpochMilli(),taken_zone=zone.id,
        actual_dose=2.0,status="ON_TIME",origin="IMPORT_HT",revision=1,config_snapshot=identity)}}
    private fun view(rows:List<HistoryPeriodEntity>,versions:List<RegimenVersionEntity> = emptyList())=
        PeriodTimelineProjection.build(NotesViewModel.ExtraState(records=records(),regimens=versions,historyPeriods=rows),emptyList(),now)

    @Test fun systemRecognitionIsEffectiveWithoutConfirmation() {
        val v=view(emptyList());val labels=HistoryLabels.build(records(),v,emptyList(),zone)
        assertTrue(v.coverage.values.all{!it.pending && it.interval!=null})
        assertTrue(labels.isEmpty())
        val different=records().first().copy(actual_dose=3.0)
        assertEquals(setOf(RecordLabel.DOSE_DIFFERS),HistoryLabels.build(listOf(different),v,emptyList(),zone)[different.id])
    }
    @Test fun equalUserAndSystemStandardsKeepAllUserBoundaries() {
        val v=view(listOf(row(1,20,40),row(2,40,60)))
        assertNotEquals(v.projection.periodAt(at(39))!!.key,v.projection.periodAt(at(40))!!.key)
        assertNotEquals(v.projection.periodAt(at(59))!!.key,v.projection.periodAt(at(60))!!.key)
        assertEquals(2,v.projection.raw.count{it.kind==SpanKind.USER})
    }
    @Test fun ongoingEndsAtAPlanChangeSavedAfterTheUserStatement() {
        val v=view(listOf(row(1,0,null,stated=30)),listOf(version(1,10,50),version(2,50).let{v->val d=definition.copy(interval=2);v.copy(definition_json=d.json(),clinical_signature=d.signature())}))
        assertEquals(at(50),v.projection.raw.single{it.kind==SpanKind.USER}.span.until)
        assertEquals(SpanKind.SAVED,v.projection.exactAt(at(51)).single().kind)
        // Versions already known when the user wrote it are covered, including their future dates.
        assertNull(view(listOf(row(1,0,null,stated=60)),listOf(version(1,10,50),version(2,50).let{v->val d=definition.copy(interval=2);v.copy(definition_json=d.json(),clinical_signature=d.signature())})).projection.raw.single{it.kind==SpanKind.USER}.span.until)
    }
    @Test fun migrationPreservesMergedDisplayedBoundsAndIsIdempotent() {
        val extra=NotesViewModel.ExtraState(records=records(),regimens=listOf(version(1,50)),historyPeriods=listOf(row(1,0,null,HistoryPeriods.CONFIRMED,null)))
        val old=PeriodTimelineProjection.build(extra,emptyList(),now,legacy=true)
        val conversion=TimelineV2Migration.plan(extra,now)!!
        val newRows=conversion.rows.mapIndexed{i,e->row(i+2L,0,null).copy(from_date=e.from.toString(),until_date=e.until?.toString(),standard_json=HistoryPeriods.standardJson(e.standard!!),
            evidence_json=JSONObject(e.evidenceJson).put("exact_from_utc",e.exactFromUtc).put("exact_until_utc",e.exactUntilUtc ?: JSONObject.NULL).toString())}
        val v=PeriodTimelineProjection.build(extra.copy(historyPeriods=newRows),emptyList(),now)
        assertEquals(old.projection.periods.map{it.from to it.until},v.projection.periods.map{it.from to it.until})
        assertEquals(old.projection.standards.map{it.standard},v.projection.standards.map{it.standard})
        assertFalse(TimelineV2Migration.needed(newRows))
        assertNull(TimelineV2Migration.plan(extra.copy(historyPeriods=newRows),now))
    }
}
