package net.plainnotes.app

import net.plainnotes.app.data.*
import net.plainnotes.app.domain.*
import net.plainnotes.app.timeline.*
import net.plainnotes.app.visit.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class PeriodTimelineTest {
    private val zone=ZoneId.of("UTC")
    private val start=Instant.parse("2026-01-01T08:00:00Z")
    private val cut=start.plusSeconds(86400)
    private val now=cut.plusSeconds(10*86400)
    private val med=MedicationEntity(1,"Synthetic E2","E2","ORAL","MG",2.0,40.0,site_rotation=false,notifications_on=false,active=true,sort_order=0)
    private fun definition(time:String="08:00:00",dose:Double=2.0,zoneName:String="UTC",anchor:String="2026-01-01",name:String=med.name,
        doses:List<Pair<String,Double?>>?=null,profile:ProfileEntity=ProfileEntity(1,"EV","oral"))=RegimenDefinition(MedicationSnapshot.encode(med.copy(name=name),profile),"EVERY_N_DAYS",1,0,dose,zoneName,anchor,null,doses ?: listOf(time to null))
    private fun version(id:Long,from:Instant,until:Instant?,d:RegimenDefinition)=RegimenVersionEntity(id,1,from.toEpochMilli(),until?.toEpochMilli(),d.zone,d.json(),d.signature(),"APP",from.toEpochMilli())
    private fun build(versions:List<RegimenVersionEntity>,extra:NotesViewModel.ExtraState=NotesViewModel.ExtraState())=
        PeriodTimelineProjection.build(extra.copy(regimens=versions),emptyList(),now)
    @Test fun reminderClockAnchorZoneNamesAndPureModelSettingDoNotCutTheDisplay() {
        val first=definition()
        val changes=listOf(definition("09:00:00"),definition(zoneName="Asia/Tokyo"),definition(anchor="2025-12-31"),definition(name="Synthetic renamed"),
            definition(profile=ProfileEntity(1,"EV","oral",sl_tier=2,gel_site="THIGH",gel_area_cm2=100.0)))
        for(next in changes) {
            val versions=listOf(version(1,start,cut,first),version(2,cut,null,next))
            assertEquals(1,build(versions).projection.periods.size)
            assertEquals(2,build(versions).projection.raw.size)
        }
        val sublingual=definition().copy(medicationJson=MedicationSnapshot.encode(med.copy(route="SUBLINGUAL"),ProfileEntity(1,"EV","sublingual",sl_tier=1)))
        val tier=sublingual.copy(medicationJson=MedicationSnapshot.encode(med.copy(route="SUBLINGUAL"),ProfileEntity(1,"EV","sublingual",sl_tier=2)))
        assertEquals(sublingual.therapyStandard().therapySignatureV2(),tier.therapyStandard().therapySignatureV2())
    }
    @Test fun clockCrossingWithNonuniformDosesIsNotTreatedAsAProvenDistributionChange() {
        val first=definition(doses=listOf("08:00:00" to 1.0,"20:00:00" to 2.0))
        val next=definition(doses=listOf("20:00:00" to 2.0,"21:00:00" to 1.0))
        val view=build(listOf(version(1,start,cut,first),version(2,cut,null,next)))
        assertEquals(1,view.projection.periods.size);assertTrue(view.projection.standards.single().standard.slotIdentityUnknown)
    }
    @Test fun executionAndDailyObservationsNeverEnterStoryOrCutPeriodsButImportantFactsRemain() {
        val versions=listOf(version(1,start,null,definition()))
        val records=listOf("ON_TIME","LATE","MISSED","SKIPPED").mapIndexed{i,s->RecordEntity(i+1L,1,scheduled_utc=cut.toEpochMilli(),taken_utc=if(s in listOf("ON_TIME","LATE"))cut.toEpochMilli() else null,
            actual_dose=9.0,status=s,origin="APP",revision=1,config_snapshot="{}")}+
            RecordEntity(5,1,scheduled_utc=cut.toEpochMilli(),status="MISSED",origin="AUTO_MISSED",revision=1,config_snapshot="{}")
        val extra=NotesViewModel.ExtraState(records=records,symptoms=listOf(SymptomCheckEntity("2026-01-02","SYNTHETIC")),notes=listOf(DayNoteEntity("2026-01-02","Synthetic")),
            reviews=listOf(StageReviewEntity(1,"2026-01-02")),milestones=listOf(MilestoneEntity(1,"2026-01-02",kind="STARTED")),labs=listOf(LabValueEntity(1,"E2",100.0,"pg/mL",cut.toEpochMilli(),"UTC")))
        val view=build(versions,extra)
        assertEquals(setOf(EventKind.LAB,EventKind.REVIEW,EventKind.MILESTONE),view.events.map{it.kind}.toSet());assertEquals(1,view.projection.periods.size)
        assertEquals(5,extra.records.size) // no ledger rewrite
        assertTrue(build(versions).events.isEmpty()) // no inferred start
    }
    @Test fun exactLabContextIsSeparateFromSameDayDisplayAndFutureEventsStayOutside() {
        val afternoon=cut.plusSeconds(7*3600)
        val versions=listOf(version(1,start,afternoon,definition()),version(2,afternoon,null,definition(dose=3.0)))
        val before=LabValueEntity(1,"E2",100.0,"pg/mL",cut.plusSeconds(3600).toEpochMilli(),"UTC")
        val after=before.copy(id=2,sampled_utc=afternoon.plusSeconds(3600).toEpochMilli())
        val extra=NotesViewModel.ExtraState(regimens=versions,labs=listOf(before,after),milestones=listOf(MilestoneEntity(1,"2025-01-01",kind="STARTED"),MilestoneEntity(2,"2027-01-01",kind="SURGERY")))
        val saved=LabContext.build(before,emptyList(),versions,emptyMap())
        val view=PeriodTimelineProjection.build(extra,listOf(AppointmentEntity(1,"ENDO",now.plusSeconds(3600).toEpochMilli(),"UTC",remind_minutes_before=0)),now)
        assertEquals(setOf(1L),view.events.single{it.key=="lab:1"}.exactRegimenIds)
        assertEquals(setOf(2L),view.events.single{it.key=="lab:2"}.exactRegimenIds)
        assertEquals(1,view.unknownEvents.size);assertEquals(2,view.upcoming.size);assertTrue(view.upcoming.all{it.displayPeriodKey==null})
        assertEquals(saved,LabContext.build(before,emptyList(),versions,emptyMap()))
        LabContext.validate(saved)
        assertEquals("epoch:${start.toEpochMilli()}:1",org.json.JSONObject(saved).getJSONObject("epoch").getString("key"))
    }
    @Test fun reminderMergedDisplayDoesNotChangeLegacyEpochContextOrTemplateOneFactsAndDigest() {
        val versions=listOf(version(1,start,cut,definition()),version(2,cut,null,definition("09:00:00")))
        val lab=LabValueEntity(1,"E2",100.0,"pg/mL",cut.plusSeconds(3600).toEpochMilli(),"UTC")
        val context=LabContext.build(lab,emptyList(),versions,emptyMap())
        val appointment=AppointmentEntity(1,"ENDO",now.toEpochMilli(),"UTC",remind_minutes_before=0)
        val d=net.plainnotes.app.export.ExportData(listOf(med),emptyMap(),emptyList(),listOf(lab),emptyList(),emptyList(),emptyList(),emptyMap(),{"Synthetic"},
            regimens=versions,labContexts=listOf(LabContextEntity(1,1,1,now.toEpochMilli(),"AT_ENTRY",context)))
        val from=start.atZone(zone).toLocalDate();val to=now.atZone(zone).toLocalDate()
        val facts=VisitFacts.build(d,appointment,from,to,zone)
        val spec=VisitPackSpec(appointment,from,to,setOf(VisitSection.FACTS,VisitSection.REGIMEN,VisitSection.LABS),emptyList(),emptyMap(),"Synthetic unknown")
        val digest=VisitDigest.compute(d,spec,facts,"en",zone)
        val original=versions.map{it.definition_json to it.clinical_signature}
        val view=build(versions,NotesViewModel.ExtraState(labs=listOf(lab)))
        assertEquals(1,view.projection.periods.size);assertEquals(2,TreatmentEpochs.build(versions.map{it.span()}).size)
        assertEquals(2,facts.regimenStarted);assertEquals(1,facts.regimenEnded)
        assertEquals("epoch:${cut.toEpochMilli()}:2",LabContext.validate(context).getJSONObject("epoch").getString("key"))
        assertEquals(context,LabContext.build(lab,emptyList(),versions,emptyMap()))
        assertEquals(digest,VisitDigest.compute(d,spec,VisitFacts.build(d,appointment,from,to,zone),"en",zone))
        assertEquals(original,versions.map{it.definition_json to it.clinical_signature});assertEquals(2,VISIT_TEMPLATE_VERSION)
    }

}
