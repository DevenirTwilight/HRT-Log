package net.plainnotes.app

import net.plainnotes.app.data.*
import net.plainnotes.app.domain.RecordLabel
import net.plainnotes.app.domain.RecordLabel.*
import net.plainnotes.app.timeline.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

/** REQUIREMENTS §35a: per-record attribution of imported and app records by confirmed periods. Synthetic data only. */
class HistoryAttributionTest {
    private val zone=ZoneId.of("Asia/Shanghai")
    private val now=Instant.parse("2026-10-20T12:00:00Z")
    private val med=MedicationEntity(1,"Synthetic twice daily","E2","SUBLINGUAL","MG",2.0,40.0,site_rotation=false,notifications_on=false,active=true,sort_order=0)
    private val snapshot=MedicationSnapshot.encode(med,ProfileEntity(1,"E2","sublingual",sl_tier=2))
    private val day0=LocalDate.of(2026,9,10)
    private fun at(day:Int,hour:Int,minute:Int=0)=day0.plusDays(day.toLong()).atTime(hour,minute).atZone(zone).toInstant().toEpochMilli()
    private fun row(id:Long,day:Int,hour:Int,dose:Double=2.0,origin:String="IMPORT_HT",minute:Int=0)=RecordEntity(id,1,taken_utc=at(day,hour,minute),taken_zone=zone.id,
        actual_dose=dose,status="ON_TIME",origin=origin,source_record_key=if(origin=="APP")null else "ht:synthetic:$id",revision=1,config_snapshot=snapshot)

    /** Twice daily from 09-10; HT export until 10-05, app records from 10-06. Day 5 has a third dose, day 8 one 3 mg dose, days 12 and 20 one log only. */
    private val records=buildList {
        var id=1L
        for(day in 0..35) {
            val origin=if(day0.plusDays(day.toLong())>=LocalDate.of(2026,10,6))"APP" else "IMPORT_HT"
            add(row(id++,day,8,origin=origin))
            if(day!=12 && day!=20)add(row(id++,day,20,if(day==8)3.0 else 2.0,origin))
            if(day==5)add(row(1000,day,22,minute=30,origin=origin))
        }
    }
    private val standard=net.plainnotes.app.domain.TherapyStandard("E2",null,"SUBLINGUAL","MG",null,"EVERY_N_DAYS",1,0,listOf(2.0,2.0))
    private fun period(revision:Int,state:String=HistoryPeriods.CONFIRMED,id:Long=revision.toLong())=HistoryPeriodEntity(id,"00000000-0000-4000-8000-000000000001",revision,state,1,snapshot,
        HistoryPeriods.standardJson(standard),day0.toString(),null,zone.id,"{}",created_utc=1000L+revision)
    private fun labels(periods:List<HistoryPeriodEntity> = emptyList(),annotations:List<RecordAnnotationEntity> = emptyList()):Map<Long,Set<RecordLabel>> {
        val extra=NotesViewModel.ExtraState(records=records,historyPeriods=periods,annotations=annotations)
        return HistoryLabels.build(records,PeriodTimelineProjection.build(extra,emptyList(),now),annotations,zone)
    }

    @Test fun mixedSourcesAcrossOctoberSixAreOnePendingPeriodUntilConfirmed() {
        val view=PeriodTimelineProjection.build(NotesViewModel.ExtraState(records=records),emptyList(),now)
        assertEquals(1,view.observed.size);assertEquals(listOf(2.0,2.0),view.observed.single().interval.standard.doses)
        assertEquals(records.map{it.id}.toSet(),labels().filterValues{it==setOf(PENDING_PERIOD)}.keys)
    }

    @Test fun confirmedPeriodLabelsEachRecord() {
        val l=labels(listOf(period(1)))
        // The third dose of day 5 (latest by time) is extra; the 3 mg dose differs; days with one log are not missed and carry nothing.
        assertEquals(mapOf(1000L to setOf(EXTRA_INFERRED),records.single{it.actual_dose==3.0}.id to setOf(DOSE_DIFFERS)),l)
    }

    @Test fun userMarkedExtraIsKeptApartFromInferredExtra() {
        val marked=records.first{it.taken_utc==at(5,8)}.id
        val l=labels(listOf(period(1)),listOf(RecordAnnotationEntity(marked,HistoryPeriods.EXTRA,1L)))
        assertEquals(setOf(EXTRA_USER),l[marked]);assertNull(l[1000L])
    }

    @Test fun revokingReturnsToPendingAndConfirmingAgainGivesTheSameLabels() {
        val first=labels(listOf(period(1)))
        assertTrue(labels(listOf(period(1),period(2,HistoryPeriods.REVOKED))).values.all{it==setOf(PENDING_PERIOD)})
        assertEquals(first,labels(listOf(period(1),period(2,HistoryPeriods.REVOKED),period(3))))
        // Records themselves are never changed.
        assertTrue(records.all{it.scheduled_utc==null && it.revision==1})
    }
}
