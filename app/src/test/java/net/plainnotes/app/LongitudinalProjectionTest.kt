package net.plainnotes.app

import net.plainnotes.app.data.*
import net.plainnotes.app.domain.*
import net.plainnotes.app.timeline.*
import org.junit.*
import org.junit.Assert.*
import java.time.*

/** Synthetic facts only: saved event time and saved plan relationship are separate. */
class LongitudinalProjectionTest {
    private val zone=ZoneId.of("UTC")
    private val start=Instant.parse("2026-03-10T08:00:00Z")
    private val cut=start.plusSeconds(3600)
    private fun version(id:Long,from:Instant,until:Instant?)=RegimenVersionEntity(id,1,from.toEpochMilli(),until?.toEpochMilli(),"UTC","{}","signature","APP",from.toEpochMilli())
    private val versions=listOf(version(1,start,cut),version(2,cut,null))
    private fun dose(id:Long,status:String="ON_TIME",origin:String="APP",med:Long=1)=RecordEntity(id,med,rule_version_id=10,scheduled_utc=start.toEpochMilli(),taken_utc=if(status=="ON_TIME")cut.toEpochMilli() else null,actual_dose=2.0,status=status,origin=origin,revision=1,config_snapshot="{}")
    @Test fun crossBoundaryActualKeepsOldPlanLinkAndDeletedRecordsDisappear() {
        val e=NotesViewModel.ExtraState(regimens=versions,regimenLinks=listOf(RegimenRuleLinkEntity(10,1)),records=listOf(dose(1),dose(2).copy(deleted_at_utc=cut.toEpochMilli())))
        val view=LongitudinalProjection.build(e,emptyList(),zone)
        val actual=view.events.single{it.kind==EventKind.DOSE}
        assertEquals(setOf(view.epochs[1].key),actual.epochs.keys)
        assertEquals(1L,(actual.source as EventSource.Intake).plannedRegimenId)
        val edited=LongitudinalProjection.build(e.copy(records=listOf(dose(1).copy(taken_utc=start.toEpochMilli(),revision=2))),emptyList(),zone)
        assertEquals(setOf(edited.epochs[0].key),edited.events.single{it.kind==EventKind.DOSE}.epochs.keys)
    }
    @Test fun dateOnlyObservationsAreAmbiguousAndNeverBecomeMidnightEvents() {
        val e=NotesViewModel.ExtraState(regimens=versions,milestones=listOf(MilestoneEntity(1,"2026-03-10",title="Synthetic date")),
            labs=listOf(LabValueEntity(1,"E2",100.0,"pg/mL",cut.toEpochMilli(),"UTC")))
        val view=LongitudinalProjection.build(e,emptyList(),zone)
        val milestone=view.events.single{it.kind==EventKind.MILESTONE}
        assertNull(milestone.at);assertEquals(2,milestone.epochs.keys.size);assertTrue(milestone.epochs.unknownPortion)
        assertFalse(view.events.single{it.kind==EventKind.LAB}.epochs.uncertain)
    }
    @Test fun automaticUnconfirmedIsNotMissedAndMedicationFilterDoesNotInflateCount() {
        val e=NotesViewModel.ExtraState(regimens=versions,records=listOf(dose(1,"MISSED","AUTO_MISSED"),dose(2,"MISSED","AUTO_MISSED",2),dose(3,"MISSED")),milestones=listOf(MilestoneEntity(1,"2026-03-10",title="Synthetic global")))
        val view=LongitudinalProjection.build(e,emptyList(),zone,medicationId=1)
        assertEquals(1,(view.events.single{it.kind==EventKind.UNCONFIRMED}.source as EventSource.Unconfirmed).records.size)
        assertEquals(1,view.events.count{it.kind==EventKind.MISSED})
        assertEquals(1,view.events.count{it.kind==EventKind.MILESTONE})
    }
    @Test fun futureScheduleIsOptionalAndNeverClassifiedAsAnActualDose() {
        val slot=Slot("synthetic",10,1,cut,cut,zone,2.0,15,60,start)
        val e=NotesViewModel.ExtraState(regimens=versions,upcoming=listOf(TimelineEntry(slot,SlotState.PENDING)))
        assertEquals(0,LongitudinalProjection.build(e,emptyList(),zone).events.count{it.kind==EventKind.PLANNED})
        val view=LongitudinalProjection.build(e,emptyList(),zone,true)
        assertEquals(1,view.events.count{it.kind==EventKind.PLANNED});assertEquals(0,view.events.count{it.kind==EventKind.DOSE})
        assertEquals(0,view.events.count{it.kind==EventKind.MILESTONE}) // no inferred treatment start
    }
}
