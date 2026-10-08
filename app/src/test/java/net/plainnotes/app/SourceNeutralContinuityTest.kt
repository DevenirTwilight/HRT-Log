package net.plainnotes.app

import net.plainnotes.app.data.*
import net.plainnotes.app.timeline.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

/** Synthetic October 6 boundary; no user's dataset or assumed import start event. */
class SourceNeutralContinuityTest {
    private val base=Instant.parse("2026-09-25T01:00:00Z")
    private val cut=Instant.parse("2026-10-06T08:00:00Z")
    private val now=Instant.parse("2026-10-08T22:00:00Z")
    private val med=MedicationEntity(1,"Synthetic same regimen","E2","SUBLINGUAL","MG",2.0,40.0,site_rotation=false,notifications_on=false,active=true,sort_order=0)
    private val json=MedicationSnapshot.encode(med,ProfileEntity(1,"E2","sublingual"))
    private fun rows()=(0..10).flatMap{d->listOf(0,12).mapIndexed{slot,h->RecordEntity(d*2+slot+1L,1,
        taken_utc=base.plusSeconds((d*24+h)*3600L).toEpochMilli(),taken_zone="Asia/Shanghai",actual_dose=2.0,status="ON_TIME",
        origin="IMPORT_HT",source_record_key="ht:synthetic:continuity:$d:$h",revision=1,config_snapshot=json)}}
    private fun daily(times:List<Pair<String,Double?>> = listOf("08:00:00" to null,"20:00:00" to null))=
        RegimenDefinition(json,"EVERY_N_DAYS",1,0,2.0,"UTC","2026-10-06",null,times)
    private fun hours(interval:Int=12)=RegimenDefinition(json,"EVERY_N_HOURS",interval,0,2.0,"UTC",null,cut.toEpochMilli(),emptyList())
    private fun saved(d:RegimenDefinition,from:Instant=cut,medId:Long=1,id:Long=1,until:Instant?=null)=
        RegimenVersionEntity(id,medId,from.toEpochMilli(),until?.toEpochMilli(),d.zone,d.json(),d.signature(),"APP",from.toEpochMilli())
    private fun view(records:List<RecordEntity>,versions:List<RegimenVersionEntity>,labs:List<LabValueEntity> = emptyList())=
        PeriodTimelineProjection.build(NotesViewModel.ExtraState(records=records,regimens=versions,labs=labs),emptyList(),now)
    @Test fun importedDailyTwiceAndSavedTwelveHourlyAreOnePeriodAcrossRecordedZoneBoundary() {
        val before=rows()
        val after=(0..2).flatMap{d->listOf(0,12).mapIndexed{slot,h->before.first().copy(id=100+d*2+slot.toLong(),medication_id=9,
            taken_utc=cut.plusSeconds((d*24+h)*3600L).toEpochMilli(),taken_zone="UTC",origin="APP",source_record_key=null)}}
        val original=before+after;val version=saved(hours(),medId=9)
        val lab=LabValueEntity(1,"E2",100.0,"pg/mL",cut.plusSeconds(60).toEpochMilli(),"UTC")
        val v=view(original,listOf(version),listOf(lab))
        assertFalse(v.recognitionUnavailable);assertEquals(1,v.projection.standards.size);assertEquals(1,v.projection.periods.size)
        assertEquals(cut,v.observed.single().interval.span.until)
        assertEquals(listOf(2.0,2.0),v.projection.standards.single().standard.doses)
        assertEquals(9L,v.projection.standards.single().medicationId)
        assertEquals(setOf(1L),v.events.single().exactRegimenIds)
        assertEquals(original,v.resolvedRecords);assertTrue(v.importedHistory.isEmpty())
        assertEquals("IMPORT_HT",before.first().origin);assertEquals(json,before.first().config_snapshot)
        assertEquals(version.definition_json,saved(hours(),medId=9).definition_json)
    }
    @Test fun dailyClockScheduleAlsoJoinsTheShortCrossZoneBoundary() {
        val v=view(rows(),listOf(saved(daily())))
        assertEquals(1,v.projection.periods.size);assertEquals(1,v.projection.standards.size)
    }
    @Test fun equivalentCadenceExpressionsOnlyMergeWhenExactDoseDistributionMatches() {
        val twice=daily();val hourly=hours()
        assertEquals(twice.therapyStandard(),hourly.therapyStandard())
        assertEquals("EVERY_N_HOURS",hourly.therapyStandard(normalizeCadence=false).kind)
        assertNotEquals(twice.signature(),hourly.signature())
        assertEquals(twice.therapyStandard(),twice.copy(kind="WEEKLY",weekdays=127).therapyStandard())
        assertNotEquals(twice.therapyStandard(),twice.copy(kind="WEEKLY",weekdays=31).therapyStandard())
        assertEquals("EVERY_N_HOURS",hours(36).therapyStandard().kind)
        assertEquals(2,hours(48).therapyStandard().interval)
        assertEquals(2,view(rows(),listOf(saved(daily(listOf("08:00:00" to null))))).projection.standards.size)
        assertEquals(2,view(rows(),listOf(saved(daily().copy(dose=3.0)))).projection.standards.size)
        assertEquals(2,view(rows(),listOf(saved(hours(36)))).projection.standards.size)
    }
    @Test fun thirtyDayGapAndAnInterveningSavedChangePreventJoining() {
        // REQUIREMENTS §36: a gap under 30 days with the same standard no longer splits; 30 days still does.
        assertEquals(1,view(rows(),listOf(saved(hours(),cut.plusSeconds(3*86400L)))).projection.standards.size)
        assertEquals(2,view(rows(),listOf(saved(hours(),cut.plusSeconds(31*86400L)))).projection.standards.size)
        val first=saved(daily().copy(dose=3.0),until=cut.plusSeconds(3600))
        val second=saved(hours(),from=cut.plusSeconds(3600),id=2)
        val v=view(rows(),listOf(first,second))
        assertFalse(v.recognitionUnavailable)
        assertTrue(v.observed.single().interval.span.until!!<=cut)
        assertEquals(listOf(1L,2L),v.projection.raw.filter{it.span.id>0}.map{it.span.id})
    }
    @Test fun mixedUnmatchedSourcesUseOneBucketWithoutChangingSourceFacts() {
        val row=rows().first().copy(config_snapshot="{}")
        val source=listOf(row,row.copy(id=101,origin="APP",source_record_key=null),row.copy(id=102,origin="IMPORT_TM",source_record_key="tm:synthetic"))
        val v=view(source,emptyList())
        assertEquals(source,v.importedHistory.single().records)
        assertEquals(setOf("IMPORT_HT","APP","IMPORT_TM"),v.importedHistory.single().records.map{it.origin}.toSet())
        assertTrue(v.projection.periods.isEmpty())
    }
    @Test fun scheduledMissedAndSkippedRecordsStayInTheirKnownPeriodForEverySource() {
        val source=listOf("APP","IMPORT_HT","IMPORT_TM").flatMapIndexed{i,origin->listOf("MISSED","SKIPPED").mapIndexed{j,status->
            rows().first().copy(id=100+i*2+j.toLong(),taken_utc=null,actual_dose=null,scheduled_utc=cut.plusSeconds(60).toEpochMilli(),origin=origin,status=status)}}
        val v=view(source,listOf(saved(daily())))
        assertEquals(source,v.resolvedRecords);assertTrue(v.importedHistory.isEmpty());assertTrue(v.observed.isEmpty())
        assertTrue(v.events.isEmpty());assertTrue(v.resolvedRecords.all{it.actual_dose==null && it.taken_utc==null})
    }

}
