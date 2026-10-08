package net.plainnotes.app

import net.plainnotes.app.data.*
import net.plainnotes.app.timeline.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class ObservedTreatmentHistoryTest {
    private val start=Instant.parse("2025-01-01T08:00:00Z")
    private val now=Instant.parse("2026-10-08T12:00:00Z")
    private val med=MedicationEntity(1,"Synthetic frozen medicine","E2","SUBLINGUAL","MG",2.0,40.0,site_rotation=false,notifications_on=false,active=true,sort_order=0)
    private val snapshot=MedicationSnapshot.encode(med,ProfileEntity(1,"E2","sublingual",sl_tier=2))
    private fun row(day:Int,dose:Double=2.0,id:Long=day+1L,json:String=snapshot)=RecordEntity(id,1,taken_utc=start.plusSeconds(day*86400L).toEpochMilli(),taken_zone="UTC",actual_dose=dose,status="ON_TIME",origin="IMPORT_HT",source_record_key="ht:synthetic:$id",revision=1,config_snapshot=json)
    private fun view(rows:List<RecordEntity>,versions:List<RegimenVersionEntity> = emptyList(),labs:List<LabValueEntity> = emptyList())=
        PeriodTimelineProjection.build(NotesViewModel.ExtraState(records=rows,regimens=versions,labs=labs),emptyList(),now)
    private fun saved(day:Int,dose:Double=2.0,id:Long=1,medId:Long=1):RegimenVersionEntity {
        val from=start.plusSeconds(day*86400L)
        val d=RegimenDefinition(snapshot,"EVERY_N_DAYS",1,0,dose,"UTC","2025-01-01",null,listOf("08:00:00" to null))
        return RegimenVersionEntity(id,medId,from.toEpochMilli(),null,"UTC",d.json(),d.signature(),"APP",from.toEpochMilli())
    }
    @Test fun recognizesOldDoseChangesWithoutSeparateImportedCardsOrPersistedVersions() {
        val rows=(0..4).map{row(it)}+(5..9).map{row(it,3.0)}
        val v=view(rows)
        assertEquals(listOf(2.0,3.0),v.observed.map{it.interval.standard.doses.single()})
        assertEquals(2,v.projection.standards.size);assertEquals(rows.map{it.id}.toSet(),v.resolvedRecords.map{it.id}.toSet())
        assertTrue(v.importedHistory.isEmpty());assertTrue(v.events.isEmpty());assertTrue(v.projection.raw.all{it.span.id<0})
        assertEquals(snapshot,rows.first().config_snapshot);assertEquals(1,rows.first().revision)
    }
    @Test fun matchesDuplicateMedicationIdentityAndMergesWithCurrentSavedPlan() {
        val rows=(0..5).map{row(it)}
        val v=view(rows,listOf(saved(6,medId=9)))
        assertEquals(1,v.projection.standards.size)
        assertEquals(9L,v.projection.standards.single().medicationId)
        assertEquals(listOf(-1L,1L),v.projection.standards.single().rawVersionIds)
        assertTrue(v.importedHistory.isEmpty());assertEquals(1L,rows.first().medication_id)
    }
    @Test fun isolatedExtraDoseMissingDayAndLateTimeDoNotCreateAChange() {
        val rows=(0..10).filter{it!=5}.map{row(it,if(it==4)7.0 else 2.0).let{r->if(it==7)r.copy(taken_utc=r.taken_utc!!+2*3600000,status="LATE") else r}}
        val v=view(rows)
        assertEquals(1,v.observed.size);assertEquals(listOf(2.0),v.observed.single().interval.standard.doses)
        assertEquals(rows.size,v.resolvedRecords.size)
    }
    @Test fun multiDoseDailyDistributionAndSustainedIntervalChangesAreRecognized() {
        val twice=(0..4).flatMap{d->listOf(row(d,1.0,d*2+1L),row(d,2.0,d*2+2L).let{it.copy(taken_utc=it.taken_utc!!+12*3600000)})}
        assertEquals(listOf(1.0,2.0),view(twice).observed.single().interval.standard.doses)
        val rows=(0..4).map{row(it)}+listOf(6,8,10,12,14).map{row(it)}
        assertEquals(listOf(1,2),view(rows).observed.map{it.interval.standard.interval})
    }
    @Test fun longGapsHaveUnknownCoverageNotInferredStoppingOrOngoingTherapy() {
        val v=view((0..3).map{row(it)}+(100..103).map{row(it)})
        assertEquals(2,v.observed.size)
        assertNull(v.projection.periodAt(start.plusSeconds(50*86400L))?.finalStandardSpanKeys?.singleOrNull())
        assertTrue(v.events.isEmpty());assertNotNull(v.observed.last().interval.span.until)
    }
    @Test fun savedPlanWinsAndLabContextNeverReferencesSyntheticIds() {
        val rows=(0..7).map{row(it,3.0)};val version=saved(4)
        val lab=LabValueEntity(1,"E2",100.0,"pg/mL",start.plusSeconds(2*86400L).toEpochMilli(),"UTC")
        val v=view(rows,listOf(version),listOf(lab))
        assertTrue(v.observed.all{it.interval.span.until!!<=Instant.ofEpochMilli(version.effective_from_utc)})
        assertTrue(v.events.single().exactRegimenIds.isEmpty());assertNotNull(v.events.single().displayPeriodKey)
        assertEquals(version.definition_json,saved(4).definition_json)
    }
    @Test fun routeChangeIsRecognizedUsingFrozenHistoryNotCurrentMedication() {
        val oral=MedicationSnapshot.encode(med.copy(route="ORAL"),ProfileEntity(1,"E2","oral"))
        val v=view((0..3).map{row(it)}+(4..7).map{row(it,json=oral)})
        assertEquals(listOf("SUBLINGUAL","ORAL"),v.observed.map{it.interval.standard.route})
    }
    @Test fun uncertainSnapshotsFutureDeletedMissedAndTooFewEventsDoNotInventPlans() {
        val rows=listOf(row(0),row(1),row(2).copy(config_snapshot="{}"),row(3).copy(deleted_at_utc=now.toEpochMilli()),row(4).copy(status="MISSED"),row(800))
        assertTrue(view(rows).observed.isEmpty())
        val unknown=snapshot.replace("\"route\":\"SUBLINGUAL\"","\"route\":null")
        assertNull(view((0..3).map{row(it,json=unknown)}).observed.single().interval.standard.route)
    }
    @Test fun ambiguousExistingIdentitiesAreNotArbitrarilyMerged() {
        val v=view((0..3).map{row(it)},listOf(saved(100,id=1,medId=8),saved(100,id=2,medId=9)))
        assertEquals(1L,v.observed.single().interval.span.medicationId)
    }
    @Test fun appManualHistoryIsRecognizedAndUnchangedLikeImportedHistory() {
        val rows=(0..3).map{row(it).copy(origin="APP",source_record_key=null)}
        assertEquals(rows,view(rows).observed.single().records)
    }
    @Test fun preservedExactDuplicatesAreNotEvidenceForDoublingTheRecognizedDose() {
        val rows=(0..4).flatMap{d->listOf(row(d,id=d*2+1L),row(d,id=d*2+2L))}
        assertTrue(view(rows).observed.isEmpty());assertEquals(rows.size,view(rows).importedHistory.single().records.size)
    }

    @Test fun purePkTierChangesDoNotSplitTheObservedClinicalPattern() {
        val tier=MedicationSnapshot.encode(med,ProfileEntity(1,"E2","sublingual",sl_tier=1))
        val rows=(0..7).map{row(it,json=if(it<4)snapshot else tier)}
        assertEquals(1,view(rows).observed.size)
    }

}
