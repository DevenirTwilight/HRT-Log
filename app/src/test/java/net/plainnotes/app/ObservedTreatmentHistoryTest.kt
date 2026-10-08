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
        // §35a: each dose held for at least 14 days is a real change.
        val rows=(0..19).map{row(it)}+(20..39).map{row(it,3.0)}
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
        val rows=(0..19).map{row(it)}+(20..60 step 2).map{row(it)}
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
        // Same time and dose twice is one dose: the recognised standard is once daily, never doubled.
        assertEquals(listOf(2.0),view(rows).observed.single().interval.standard.doses)
    }

    @Test fun purePkTierChangesDoNotSplitTheObservedClinicalPattern() {
        val tier=MedicationSnapshot.encode(med,ProfileEntity(1,"E2","sublingual",sl_tier=1))
        val rows=(0..7).map{row(it,json=if(it<4)snapshot else tier)}
        assertEquals(1,view(rows).observed.size)
    }

    @Test fun missingHistoricalIdentityDoesNotMergeDifferentMedicationIds() {
        val unknown=MedicationSnapshot.encode(med.copy(route=null),null)
        val d=RegimenDefinition(unknown,"EVERY_N_DAYS",1,0,2.0,"UTC","2025-01-01",null,listOf("08:00:00" to null))
        val v=saved(100,medId=9).copy(definition_json=d.json(),clinical_signature=d.signature())
        val history=view((0..3).map{row(it,json=unknown)},listOf(v))
        assertEquals(1L,history.observed.single().interval.span.medicationId)
        assertNull(history.observed.single().snapshot.route)
    }

    @Test fun sameDaySavedDoseChangesMustNotBeBridgedByObservedHistory() {
        val cut=start.plusSeconds(4*86400L)
        val morning=saved(4,3.0,id=1).copy(effective_until_utc=cut.plusSeconds(4*3600).toEpochMilli())
        val afternoon=saved(4,2.0,id=2).copy(effective_from_utc=cut.plusSeconds(4*3600).toEpochMilli())
        val rows=(0..7).map{row(it)}
        val v=view(rows,listOf(morning,afternoon))
        assertEquals(cut,v.observed.single().interval.span.until)
        assertEquals(listOf(1L,2L),v.projection.raw.filter{it.span.id>0}.map{it.span.id})
    }

    @Test fun earliestIncompatibleSavedBoundaryPreventsASameDayJoin() {
        val cut=start.plusSeconds(4*86400L)
        val first=saved(4,3.0,id=1).copy(effective_until_utc=cut.plusSeconds(2*3600).toEpochMilli())
        val next=saved(4,2.0,id=2).copy(effective_from_utc=cut.plusSeconds(2*3600).toEpochMilli())
        val v=view((0..3).map{row(it)},listOf(first,next))
        assertEquals(cut.atZone(ZoneId.of("UTC")).toLocalDate().atStartOfDay(ZoneId.of("UTC")).toInstant(),v.observed.single().interval.span.until)
        assertFalse(v.recognitionUnavailable)
    }
    @Test fun variedSameDayVersionBoundariesNeverOverlapRecognizedHistory() {
        val rows=(0..7).map{row(it)}
        val originals=rows.map{it.copy()}
        for(hour in 0..7)for(doses in listOf(listOf(3.0,2.0),listOf(2.0,3.0,2.0),listOf(3.0,4.0,2.0),listOf(2.0,2.0))) {
            val base=start.plusSeconds(4*86400L+hour*3600)
            val versions=doses.mapIndexed{i,d->saved(4,d,i+1L).copy(effective_from_utc=base.plusSeconds(i*3600L).toEpochMilli(),
                effective_until_utc=if(i==doses.lastIndex)null else base.plusSeconds((i+1)*3600L).toEpochMilli())}
            val v=view(rows,versions)
            assertFalse(v.recognitionUnavailable)
            v.observed.forEach{o->versions.forEach{saved->
                assertFalse(o.interval.span.from<(saved.effective_until_utc?.let(Instant::ofEpochMilli) ?: Instant.MAX) &&
                    Instant.ofEpochMilli(saved.effective_from_utc)<o.interval.span.until!!)
            }}
            assertEquals(originals,rows)
        }
    }
    @Test fun invalidDerivedIntervalFallsBackWithoutPretendingSourcesWereRecognized() {
        val version=saved(0)
        val confirmed=net.plainnotes.app.domain.RawTreatmentInterval(version.span(),RegimenDefinition.read(version.definition_json).therapyStandard())
        val wrong=ObservedTreatment(confirmed.copy(span=net.plainnotes.app.domain.RegimenSpan(-1,1,start,start.plusSeconds(86400),true)),
            MedicationSnapshot.decode(snapshot,1)!!,listOf(row(0)))
        val result=projectHistory(listOf(confirmed),HistoricalTreatmentProjection(listOf(wrong),setOf(1)),ZoneId.of("UTC"))
        assertTrue(result.unavailable);assertTrue(result.historical.observed.isEmpty());assertTrue(result.historical.resolvedRecordIds.isEmpty())
        assertEquals(listOf(confirmed),result.projection.raw)
        assertEquals(version.definition_json,saved(0).definition_json)
    }

    @Test fun twiceDailyWithPartialDaysAndNoThreeConsecutiveCompleteDaysStaysOnePattern() {
        val rows=(0..20).flatMap{d->buildList {
            add(row(d,id=d*2+1L))
            if(d%3!=1)add(row(d,id=d*2+2L).let{it.copy(taken_utc=it.taken_utc!!+10*3600000)})
        }}
        val original=rows.map{it.copy()};val v=view(rows)
        assertFalse(v.recognitionUnavailable);assertEquals(1,v.observed.size)
        assertEquals(listOf(2.0,2.0),v.observed.single().interval.standard.doses)
        assertEquals(1,v.observed.single().interval.standard.interval)
        assertEquals(rows.map{it.id}.toSet(),v.resolvedRecords.map{it.id}.toSet())
        assertTrue(v.importedHistory.isEmpty());assertEquals(original,rows)
    }
    @Test fun sustainedTwiceToOnceDailyChangeSurvivesPartialLogging() {
        val rows=(0..45).flatMap{d->buildList {
            add(row(d,id=d*2+1L))
            if(d<20 && d%3!=1)add(row(d,id=d*2+2L).let{it.copy(taken_utc=it.taken_utc!!+10*3600000)})
        }}
        val v=view(rows)
        assertEquals(listOf(listOf(2.0,2.0),listOf(2.0)),v.observed.map{it.interval.standard.doses})
        assertTrue(v.importedHistory.isEmpty());assertFalse(v.recognitionUnavailable)
    }
    @Test fun variableHistoryGetsAnExplicitlyUnconfirmedCadenceInsteadOfInventedDailySchedule() {
        val rows=(0..5).map{row(it,dose=it+1.0)}
        val v=view(rows)
        assertEquals("OBSERVED",v.observed.single().interval.standard.kind)
        assertEquals(0,v.observed.single().interval.standard.interval)
        assertTrue(v.importedHistory.isEmpty());assertEquals(rows,v.resolvedRecords)
    }
    @Test fun sameMedicationUnknownFieldsCanBelongToSavedPeriodButKnownConflictsCannot() {
        val missing=row(1,json=snapshot.replace("\"route\":\"SUBLINGUAL\"","\"route\":null"))
        val conflict=row(2,json=snapshot.replace("SUBLINGUAL","ORAL"))
        val rows=listOf(missing,conflict);val v=view(rows,listOf(saved(0)))
        assertEquals(listOf(missing),v.resolvedRecords)
        assertEquals(listOf(conflict),v.importedHistory.single().records)
        assertNull(MedicationSnapshot.decode(v.resolvedRecords.single().config_snapshot,1)!!.route)
        assertEquals(rows,listOf(missing,conflict))
    }
    @Test fun explicitAppRuleCanSupplyFrozenContextButImportCannotBorrowIt() {
        val appRows=(0..3).map{row(it,json="{}").copy(origin="APP",rule_version_id=71)}
        val app=ObservedTreatmentHistory.build(appRows,emptyList(),ZoneId.of("UTC"),now,mapOf(71L to snapshot))
        assertEquals(appRows,app.observed.single().records)
        val imported=appRows.map{it.copy(origin="IMPORT_HT")}
        assertTrue(ObservedTreatmentHistory.build(imported,emptyList(),ZoneId.of("UTC"),now,mapOf(71L to snapshot)).observed.isEmpty())
        assertTrue(appRows.all{it.config_snapshot=="{}"})
    }
    @Test fun groupingUsesRecordedZoneForTwiceDailyDosesAcrossUtcMidnight() {
        val base=Instant.parse("2025-01-01T01:00:00Z")
        val rows=(0..5).flatMap{d->listOf(1L,15L).mapIndexed{slot,h->row(d,id=d*2+slot+1L).copy(
            taken_utc=base.plusSeconds((d*24+h-1)*3600L).toEpochMilli(),taken_zone="Asia/Shanghai")}}
        val v=view(rows)
        assertEquals(listOf(2.0,2.0),v.observed.single().interval.standard.doses)
        assertTrue(v.importedHistory.isEmpty())
    }

    @Test fun twiceDailyDoseChangesAndFrequencyReturnsAreNotCollapsedByMissingEntries() {
        val rows=(0..59).flatMap{d->buildList {
            val dose=if(d<20)2.0 else 3.0
            add(row(d,dose,d*2+1L))
            if((d<20 || d>=40) && d%3!=1)add(row(d,dose,d*2+2L).let{it.copy(taken_utc=it.taken_utc!!+10*3600000)})
        }}
        val v=view(rows)
        assertEquals(listOf(listOf(2.0,2.0),listOf(3.0),listOf(3.0,3.0)),v.observed.map{it.interval.standard.doses})
        assertFalse(v.recognitionUnavailable);assertTrue(v.importedHistory.isEmpty())
    }
    @Test fun supportedPartialDaysDoNotEraseUnknownLongGapsOrKnownUnitConflicts() {
        val rows=(0..6).map{row(it)}+(60..66).map{row(it)}
        val v=view(rows)
        assertEquals(2,v.observed.size);assertTrue(v.projection.periodAt(start.plusSeconds(30*86400L))!!.finalStandardSpanKeys.isEmpty())
        val conflict=row(1,json=snapshot.replace("\"unit\":\"MG\"","\"unit\":\"ML\""))
        assertTrue(view(listOf(conflict),listOf(saved(0))).resolvedRecords.isEmpty())
    }

    @Test fun irregularSparseDatesWithOneAdjacentPairDoNotBecomeADailyPrescription() {
        val v=view(listOf(0,2,4,5,8,10,12).map{row(it)})
        assertTrue(v.observed.all{it.interval.standard.kind=="OBSERVED"})
        assertTrue(v.importedHistory.isEmpty())
    }


    // REQUIREMENTS §35a: a new pattern must hold for 14 calendar days before it becomes a new period.
    private fun twice(day:Int,dose:Double=1.0,extra:Int=0)=(0 until 2+extra).map{k->row(day,dose,day*10L+k+1).let{r->r.copy(taken_utc=r.taken_utc!!+k*5*3600000L)}}
    @Test fun aFewDaysWithAThirdDoseStayInTheTwiceDailyPeriod() {
        val rows=(0..40).flatMap{d->twice(d,extra=if(d in 10..12)1 else 0)}
        val v=view(rows)
        assertEquals(1,v.observed.size)
        assertEquals(listOf(1.0,1.0),v.observed.single().interval.standard.doses)
        assertEquals(rows.map{it.id}.toSet(),v.observed.single().records.map{it.id}.toSet())
    }
    @Test fun aShortLoggingGapDoesNotSplitTheSamePattern() {
        val rows=(0..20).flatMap{twice(it)}+(27..50).flatMap{twice(it)}
        assertEquals(1,view(rows).observed.size)
    }
}

