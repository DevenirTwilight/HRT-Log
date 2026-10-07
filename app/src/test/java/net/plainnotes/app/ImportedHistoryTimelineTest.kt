package net.plainnotes.app

import net.plainnotes.app.data.*
import net.plainnotes.app.timeline.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class ImportedHistoryTimelineTest {
    private val now=Instant.parse("2026-10-07T12:00:00Z")
    private fun record(id:Long,origin:String="IMPORT_HT",at:Instant=now.minusSeconds(400*86400L),status:String="ON_TIME")=
        RecordEntity(id,1,taken_utc=if(status in listOf("ON_TIME","LATE"))at.toEpochMilli() else null,taken_zone="Asia/Shanghai",
            scheduled_utc=at.minusSeconds(3600).toEpochMilli(),scheduled_zone="Asia/Shanghai",actual_dose=if(status in listOf("ON_TIME","LATE"))2.0 else null,
            status=status,origin=origin,source_record_key="synthetic:$id",revision=1,config_snapshot="{}")
    private fun view(records:List<RecordEntity>,versions:List<RegimenVersionEntity> = emptyList(),milestones:List<MilestoneEntity> = emptyList())=
        PeriodTimelineProjection.build(NotesViewModel.ExtraState(records=records,regimens=versions,milestones=milestones),emptyList(),now)
    private fun version(id:Long,from:Instant,until:Instant?,time:String="08:00:00"):RegimenVersionEntity {
        val medication=MedicationEntity(1,"Synthetic frozen name","E2","SUBLINGUAL","MG",2.0,40.0,site_rotation=false,notifications_on=false,active=true,sort_order=0)
        val d=RegimenDefinition(MedicationSnapshot.encode(medication,ProfileEntity(1,"E2","sublingual")),"EVERY_N_DAYS",1,0,2.0,"UTC","2025-01-01",null,listOf(time to null))
        return RegimenVersionEntity(id,1,from.toEpochMilli(),until?.toEpochMilli(),d.zone,d.json(),d.signature(),"APP",from.toEpochMilli())
    }
    @Test fun bothImportersAppearWithoutFabricatingARegimenOrStartMilestone() {
        val rows=listOf(record(1),record(2,"IMPORT_TM"))
        val v=view(rows)
        assertTrue(v.projection.periods.isEmpty());assertTrue(v.events.isEmpty())
        assertEquals(setOf("IMPORT_HT","IMPORT_TM"),v.importedHistory.map{it.origin}.toSet())
        assertTrue(v.importedHistory.all{it.displayPeriodKey==null && !it.future})
        assertEquals(rows.sortedBy{it.id},v.importedHistory.flatMap{it.records}.sortedBy{it.id})
        assertEquals(ZoneId.of("UTC"),v.projection.zone)
    }
    @Test fun importedIntakesUseActualTimeAndStayVisibleBeforeFirstRegimen() {
        val cut=now.minusSeconds(86400)
        val after=record(2,at=cut.plusSeconds(10)).copy(scheduled_utc=cut.minusSeconds(3600).toEpochMilli())
        val v=view(listOf(record(1),after),listOf(version(1,cut,null)))
        assertEquals(2,v.importedHistory.size)
        assertNull(v.importedHistory.single{it.records.single().id==1L}.displayPeriodKey)
        assertEquals(v.projection.periods.single().key,v.importedHistory.single{it.records.single().id==2L}.displayPeriodKey)
        assertTrue(v.events.isEmpty()) // no individual dose cards
    }
    @Test fun clockOnlyVersionsRemainOneSummaryAndOriginalSourcesSurvive() {
        val cut=now.minusSeconds(86400)
        val versions=listOf(version(1,cut.minusSeconds(86400),cut),version(2,cut,null,"20:00:00"))
        val rows=listOf(record(1,at=cut.minusSeconds(10)),record(2,at=cut.plusSeconds(10)))
        val v=view(rows,versions)
        assertEquals(1,v.projection.periods.size);assertEquals(1,v.importedHistory.size)
        assertEquals(rows,v.importedHistory.single().records);assertEquals(versions.map{it.definition_json},v.projection.raw.map{r->versions.single{it.id==r.span.id}.definition_json})
    }
    @Test fun deletedUndatedAndAppEntriesAreExcludedWhileFutureImportsAreSeparate() {
        val future=record(4,at=now.plusSeconds(1))
        val rows=listOf(record(1).copy(deleted_at_utc=now.toEpochMilli()),record(2,"APP"),record(3).copy(taken_utc=null,scheduled_utc=null),future)
        val v=view(rows,listOf(version(1,now.minusSeconds(86400),null)))
        assertEquals(listOf(future),v.importedHistory.single().records)
        assertTrue(v.importedHistory.single().future);assertNull(v.importedHistory.single().displayPeriodKey)
    }
    @Test fun transMemoUnknownDoseAndRouteRemainUnknownNotInferredFromPlan() {
        val saved="""{"snapshot_version":2,"name":"Synthetic incomplete","molecule":"E2","route":null,"unit":"MG","pk_profile":null}"""
        val row=record(1,"IMPORT_TM",now.minusSeconds(60)).copy(actual_dose=null,config_snapshot=saved)
        val v=view(listOf(row),listOf(version(1,now.minusSeconds(86400),null)))
        val preserved=v.importedHistory.single().records.single()
        assertNull(preserved.actual_dose);assertNull(MedicationSnapshot.decode(preserved.config_snapshot,1)!!.route)
        assertEquals(saved,preserved.config_snapshot)
    }
    @Test fun missedAndSkippedImportsAreSummariesNotEvidenceOfStoppingTreatment() {
        val v=view(listOf(record(1,"IMPORT_TM",status="MISSED"),record(2,"IMPORT_TM",status="SKIPPED")))
        assertEquals(2,v.importedHistory.single().records.size);assertTrue(v.projection.periods.isEmpty());assertTrue(v.events.isEmpty())
    }
    @Test fun userLifecycleMilestonesAreFactsNotCommandsToRewriteSavedRegimens() {
        val versions=listOf(version(1,now.minusSeconds(10*86400),null))
        val facts=listOf("PAUSED","STOPPED","RESUMED").mapIndexed{i,k->MilestoneEntity(i+1L,"2026-10-0${i+1}",k,note="Synthetic reason")}
        val v=view(emptyList(),versions,facts)
        assertEquals(3,v.events.size);assertEquals(1,v.projection.periods.size)
        assertEquals(versions.single().id,v.projection.raw.single().span.id);assertNull(v.projection.raw.single().span.until)
    }
}
