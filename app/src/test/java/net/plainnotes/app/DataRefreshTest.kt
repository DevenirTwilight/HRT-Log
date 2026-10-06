package net.plainnotes.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import net.plainnotes.app.data.*
import net.plainnotes.app.domain.*
import net.plainnotes.app.reminder.ReminderCoordinator
import net.plainnotes.app.ui.stockSummary
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.time.*
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class) @Config(sdk=[28],application=android.app.Application::class) @LooperMode(LooperMode.Mode.PAUSED)
class DataRefreshTest {
    private lateinit var db:NotesDatabase
    private lateinit var repo:NotesRepository
    private lateinit var model:NotesViewModel
    private var id=0L
    private val today=LocalDate.now()
    private val zone=ZoneId.systemDefault()
    private fun await(check:()->Boolean) {
        val deadline=System.nanoTime()+20_000_000_000L
        while(!check() && System.nanoTime()<deadline) { shadowOf(android.os.Looper.getMainLooper()).idle();Thread.sleep(10) }
        assertTrue("State did not refresh: ${model.state.value.error}",check())
    }
    @Before fun setup() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        db=Room.inMemoryDatabaseBuilder(context,NotesDatabase::class.java).allowMainThreadQueries().addCallback(SchemaGuards).build()
        db.openHelper.writableDatabase
        repo=NotesRepository(object:DatabaseAccess(context){override fun get(space:Space)=db})
        id=runBlocking { repo.saveMedication(MedicationEntity(name="Synthetic daily",molecule="OTHER",unit="MG",dose_per_intake=2.0,container_capacity=84.0,soon_alert_minutes=0,late_after_minutes=60,site_rotation=false,notifications_on=false,active=true,sort_order=0),null,
            RuleKind.EVERY_N_DAYS,1,listOf(LocalTime.of(8,0),LocalTime.of(20,0)),emptySet(),today.atStartOfDay(zone).toInstant()) }
        runBlocking { repo.addContainers(id,84.0,1,true) }
        model=NotesViewModel(repo,ReminderCoordinator(context,repo),context)
        await{model.extra.value.containers.size==1 && model.state.value.schedules[id]!=null}
    }
    @After fun cleanup(){ androidx.lifecycle.ViewModelStore().apply{put("model",model);clear()};db.close() }

    @Test fun stockAndCalendarRefreshTogetherAfterAdjustmentAndScheduleEdit() {
        assertEquals(21,stockSummary(model.state.value.medications.single(),model.extra.value.containers,model.state.value.schedules[id]).daysLeft)
        model.setRemaining(model.extra.value.containers.single().id,40.0)
        await{model.extra.value.containers.single().used_amount==44.0}
        assertEquals(10,stockSummary(model.state.value.medications.single(),model.extra.value.containers,model.state.value.schedules[id]).daysLeft)
        val m=model.state.value.medications.single().copy(dose_per_intake=4.0)
        model.save(net.plainnotes.app.ui.MedicationDraft(m,null,RuleKind.EVERY_N_DAYS,1,listOf(LocalTime.NOON),emptySet(),null))
        await{model.state.value.schedules[id]?.times==listOf(LocalTime.NOON)}
        assertEquals(4.0,model.state.value.schedules[id]!!.dose!!,0.0)
        assertTrue(model.extra.value.upcoming.filter{it.slot.medicationId==id && it.slot.at>Instant.now()}.all{it.slot.dose==4.0})
        assertNull(model.state.value.error)
    }

    @Test fun completedTwiceDailyIntakesStayCompletedAcrossReloadsAndHistoryEdits() {
        val slots=model.state.value.slots.filter{it.slot.at.atZone(zone).toLocalDate()==today}.map{it.slot}
        assertEquals(2,slots.size)
        slots.forEach { model.complete(it,Instant.now(),it.dose) }
        await{model.extra.value.records.count{it.status in listOf("ON_TIME","LATE")}==2}
        repeat(3){ model.sync();model.loadExtra();model.refresh() }
        await{model.state.value.slots.count{it.slot.key in slots.map{it.key} && it.state in listOf(SlotState.ON_TIME,SlotState.LATE)}==2}
        assertTrue(model.extra.value.upcoming.none{it.slot.key in slots.map{it.key}})
        assertEquals(4.0,model.extra.value.containers.single().used_amount,0.0)
        val record=model.extra.value.records.first{it.status in listOf("ON_TIME","LATE")}
        model.editRecord(record.id,Instant.now(),3.0)
        await{model.extra.value.containers.single().used_amount==5.0}
        assertEquals(3.0,model.extra.value.records.single{it.id==record.id}.actual_dose!!,0.0)
        assertNull(model.state.value.error)
    }
    @Test fun importedAndClickedIntakesCompleteBothSlotsWithoutDoubleStockDeduction() {
        val slots=model.state.value.slots.filter{it.slot.at.atZone(zone).toLocalDate()==today}.map{it.slot}.sortedBy{it.at}
        val imported=RecordEntity(medication_id=id,taken_utc=slots.first().at.toEpochMilli(),taken_zone=zone.id,actual_dose=2.0,
            status="ON_TIME",origin="IMPORT_HT",source_record_key="synthetic-source",revision=1,config_snapshot="{}")
        val recordId=runBlocking{repo.transaction{it.record(imported)}}
        model.refresh()
        await{model.extra.value.records.any{it.id==recordId}}
        // Imports do not silently claim a slot; the user selects the matching planned intake.
        assertTrue(model.state.value.slots.any{it.slot.key==slots.first().key && it.state !in listOf(SlotState.ON_TIME,SlotState.LATE)})
        model.prepareImportedLink(model.extra.value.records.single{it.id==recordId})
        await{model.importedLink.value!=null}
        assertTrue(model.importedLink.value!!.candidates.any{it.slot.key==slots.first().key})
        model.linkImported(recordId,slots.first().key)
        await{model.extra.value.records.any{it.id==recordId && it.slot_key==slots.first().key}}
        model.complete(slots.last(),Instant.now(),2.0)
        await{model.extra.value.records.count{it.status in listOf("ON_TIME","LATE")}==2}
        repeat(3){model.sync();model.refresh()}
        await{model.state.value.slots.count{it.slot.key in slots.map{it.key} && it.state in listOf(SlotState.ON_TIME,SlotState.LATE)}==2}
        assertTrue(model.extra.value.upcoming.none{it.slot.key in slots.map{it.key}})
        val linked=model.extra.value.records.single{it.id==recordId}
        assertEquals(imported.taken_utc,linked.taken_utc);assertEquals(imported.taken_zone,linked.taken_zone)
        assertEquals(imported.actual_dose,linked.actual_dose);assertEquals(imported.source_record_key,linked.source_record_key)
        assertEquals(imported.config_snapshot,linked.config_snapshot);assertEquals(imported.origin,linked.origin)
        assertEquals(2.0,model.extra.value.containers.single().used_amount,0.0)
        runBlocking{repo.linkImported(recordId,slots.first().key)}
        assertEquals(2.0,runBlocking{repo.transaction{it.containers().single().used_amount}},0.0)
        assertNull(model.state.value.error)
    }

    @Test fun linkingUnknownDoseReplacesMissedPlaceholderAndRejectsOccupiedSlot() {
        val slots=model.state.value.slots.filter{it.slot.at.atZone(zone).toLocalDate()==today}.map{it.slot}.sortedBy{it.at}
        val now=today.plusDays(1).atTime(21,0).atZone(zone).toInstant()
        runBlocking {
            repo.calendar(now,zone,today)
            assertEquals("MISSED",repo.transaction{it.records().single{r->r.slot_key==slots.first().key}.status})
            val imported=RecordEntity(medication_id=id,taken_utc=slots.first().at.toEpochMilli(),taken_zone=zone.id,status="ON_TIME",origin="IMPORT_TM",source_record_key="synthetic-unknown-dose",revision=1,config_snapshot="{}")
            val first=repo.transaction{it.record(imported)}
            val second=repo.transaction{it.record(imported.copy(source_record_key="synthetic-other"))}
            repo.linkImported(first,slots.first().key,now,zone)
            assertNull(repo.transaction{it.recordById(first)!!.actual_dose})
            assertEquals(1,repo.transaction{it.records().count{r->r.slot_key==slots.first().key}})
            assertFalse(repo.importedCandidates(second,now,zone).any{it.slot.key==slots.first().key})
            assertTrue(runCatching{repo.linkImported(second,slots.first().key,now,zone)}.isFailure)
            assertNull(repo.transaction{it.recordById(second)!!.slot_key})
            assertEquals(0.0,repo.transaction{it.containers().single().used_amount},0.0)
        }
    }

}
