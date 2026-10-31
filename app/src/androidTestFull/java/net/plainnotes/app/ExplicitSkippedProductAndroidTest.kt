package net.plainnotes.app

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import net.plainnotes.app.data.*
import net.plainnotes.app.disguise.Disguise
import net.plainnotes.app.domain.*
import net.plainnotes.app.security.AppLock
import net.plainnotes.app.security.Session
import net.plainnotes.app.timeline.*
import net.plainnotes.app.ui.*
import org.junit.*
import org.junit.Assert.*
import java.time.*

/** Real production navigation and Room; only the medication and package are synthetic. */
class ExplicitSkippedProductAndroidTest {
    @get:Rule val ui=createEmptyComposeRule()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private var activity:ActivityScenario<MainActivity>?=null
    private lateinit var model:NotesViewModel
    @Before fun setup() {
        net.plainnotes.app.attachmentpoc.SyntheticEmulatorGuard.requireEmulator()
        Disguise.disable(context);AppLock(context).disable();Session.reset()
        UiPrefs(context).disclaimerAccepted=true
    }
    @After fun cleanup(){activity?.close();Session.reset()}
    private fun launch() {
        activity=ActivityScenario.launch(Intent(context,MainActivity::class.java))
        activity!!.onActivity{model=ViewModelProvider(it)[NotesViewModel::class.java]}
        ui.waitUntil(30_000){model.librarySession.value is net.plainnotes.app.runtime.RuntimeLibrarySession.State.Ready}
    }
    private fun navigate(to:Destination) {
        ui.waitUntil(30_000){ui.onAllNodesWithContentDescription(context.getString(R.string.menu)).fetchSemanticsNodes().isNotEmpty()}
        ui.onNodeWithContentDescription(context.getString(R.string.menu)).performClick()
        ui.onNodeWithTag("drawer-content").performScrollToNode(hasText(context.getString(to.title)))
        ui.onNodeWithTag("drawer-content").onChildren().filterToOne(hasText(context.getString(to.title))).performClick()
    }
    @Test fun fiveDaysTwiceDailyHaveOneSummaryAndReopenWithoutTenRows() {
        launch()
        val repo=model.libraryRuntime.currentRepository();val now=Instant.now();val zone=ZoneId.systemDefault()
        assertTrue("Requires an isolated synthetic installation, not another test’s treatment stages",runBlocking{repo.records().isEmpty() && repo.medications().isEmpty()})
        val today=now.atZone(zone).toLocalDate();val from=today.minusDays(4)
        val decidedAt=from.atTime(7,0).atZone(zone).toInstant()
        // Prepare the unchanged five historical days as decisions made before their
        // doses, atomically: a foreground read must not first materialize MISSED.
        val (id,before)=runBlocking{repo.transaction {
        val medication=repo.saveMedication(MedicationEntity(name="SYNTHETIC grouped skips",molecule="E2",route="ORAL",unit="MG",
            dose_per_intake=2.0,container_capacity=20.0,site_rotation=false,notifications_on=false,
            soon_alert_minutes=15,late_after_minutes=120,active=true,sort_order=0),null,RuleKind.EVERY_N_DAYS,1,
            listOf(LocalTime.of(8,0),LocalTime.of(20,0)),emptySet(),from.atStartOfDay(zone).toInstant())
        repo.addContainers(medication,20.0,1,true)
        val stock=repo.containers().filter{it.medication_id==medication}
        val slots=repo.calendar(decidedAt,zone,from).filter{it.slot.medicationId==medication &&
            it.slot.at.atZone(zone).toLocalDate() in from..today}.map{it.slot}
        assertEquals(10,slots.size)
        slots.forEach{repo.override(it,SlotOverride(it.key,skipped=true),decidedAt)}
        medication to stock
        }}
        runBlocking{model.refresh().join()}
        ui.waitUntil(30_000){model.extra.value.records.count{it.medication_id==id && it.status=="SKIPPED"}==10}
        val rows=model.extra.value.records.filter{it.medication_id==id}.sortedBy{it.id}
        assertEquals(10,rows.size);assertTrue(rows.all{it.status=="SKIPPED" && it.taken_utc==null && it.actual_dose==null})
        val first=rows.minBy{requireNotNull(it.scheduled_utc)}
        val summary="timeline:skip-group:skip:${first.id}"
        navigate(Destination.TIMELINE)
        ui.onNodeWithTag("period-timeline").performScrollToNode(hasTestTag(summary))
        ui.onNodeWithTag(summary).assertTextContains(context.resources.getQuantityString(R.plurals.timeline_skip_count,10,10),substring=true)
        rows.forEach{ui.onNodeWithTag("timeline:skip:${it.id}").assertDoesNotExist()}
        ui.onNodeWithTag(summary).performClick()
        val last=rows.maxBy{requireNotNull(it.scheduled_utc)}
        ui.onNodeWithTag("period-timeline").performScrollToNode(hasTestTag("timeline:skip:${last.id}"))
        ui.onNodeWithTag("timeline:skip:${last.id}").assertIsDisplayed().performClick()
        ui.onNodeWithTag("timeline-event-time").assertExists()
        activity!!.close();activity=null;launch();navigate(Destination.TIMELINE)
        ui.waitUntil(30_000){model.extra.value.records.count{it.medication_id==id && it.status=="SKIPPED"}==10}
        ui.onNodeWithTag("period-timeline").performScrollToNode(hasTestTag(summary))
        ui.onNodeWithTag(summary).assertExists()
        rows.forEach{ui.onNodeWithTag("timeline:skip:${it.id}").assertDoesNotExist()}
        assertEquals(rows,model.extra.value.records.filter{it.medication_id==id}.sortedBy{it.id})
        assertEquals(before,runBlocking{model.libraryRuntime.currentRepository().containers()}.filter{it.medication_id==id})
    }
    @Test fun skipInCalendarBelongsToTreatmentAndCanBeUndoneAfterReopen() {
        launch()
        val repo=model.libraryRuntime.currentRepository()
        val now=Instant.now();val zone=ZoneId.systemDefault()
        val today=now.atZone(zone).toLocalDate()
        val preferred=now.atZone(zone).plusHours(2).withSecond(0).withNano(0)
        val scheduled=if(preferred.toLocalDate()==today)preferred else today.atTime(23,59).atZone(zone)
        val id=runBlocking{repo.saveMedication(MedicationEntity(name="SYNTHETIC explicit skip",molecule="E2",route="ORAL",unit="MG",
            dose_per_intake=2.0,container_capacity=20.0,site_rotation=false,notifications_on=false,
            soon_alert_minutes=15,late_after_minutes=120,active=true,sort_order=0),null,RuleKind.EVERY_N_DAYS,1,
            listOf(scheduled.toLocalTime()),emptySet(),now.minusSeconds(86400))}
        runBlocking{repo.addContainers(id,20.0,1,true);model.refresh().join()}
        val before=runBlocking{repo.containers()}.filter{it.medication_id==id}
        val originalSlot=runBlocking{repo.calendar(now,zone,scheduled.toLocalDate())}.single{it.slot.medicationId==id && it.slot.at==scheduled.toInstant()}.slot
        // Seed an already confirmed move/dose; the actual skip must preserve it, not reset it.
        runBlocking {
            repo.override(originalSlot,SlotOverride(originalSlot.key,originalSlot.at.minusSeconds(60),zone,3.0),now)
            model.refresh().join()
        }
        val slot=runBlocking{repo.calendar(now,zone,scheduled.toLocalDate())}.single{it.slot.key==originalSlot.key}.slot
        ui.waitUntil(30_000){!model.state.value.loading && model.state.value.medications.any{it.id==id}}
        // Today's doses are in the real TodayCard, not the other-dates day list.
        val open=(model.state.value.slots+model.extra.value.upcoming).distinctBy{it.slot.key}
            .filter{it.state in OPEN_STATES && it.slot.at.atZone(zone).toLocalDate()==today}.sortedBy{it.slot.at}
        val first=open.first()
        val menu=when {
            first.slot.key==slot.key -> "today-more"
            first.slot.at==slot.at -> "today-more:${slot.key}"
            else -> {
                if(ui.onAllNodesWithTag("today-show-all:later").fetchSemanticsNodes().isNotEmpty())
                    ui.onNodeWithTag("today-show-all:later").performScrollTo().performClick()
                "today-later-more:${slot.key}"
            }
        }
        ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag(menu))
        ui.onNodeWithTag(menu).performClick()
        ui.onNodeWithText(context.getString(R.string.today_change_or_skip)).performClick()
        ui.waitUntil(30_000){ui.onAllNodesWithContentDescription(context.getString(R.string.skipped)).fetchSemanticsNodes().isNotEmpty()}
        ui.onNodeWithContentDescription(context.getString(R.string.skipped)).assertIsOff().performClick()
        ui.onNode(hasText(context.getString(R.string.save)) and hasAnyAncestor(isDialog())).performClick()
        ui.waitUntil(30_000){model.extra.value.records.any{it.medication_id==id && it.status=="SKIPPED"}}
        val skipped=model.extra.value.records.single{it.medication_id==id && it.status=="SKIPPED"}
        assertNull(skipped.taken_utc);assertNull(skipped.actual_dose);assertEquals(slot.at.toEpochMilli(),skipped.scheduled_utc)
        assertEquals(3.0,skipped.planned_dose!!,0.0)
        navigate(Destination.TIMELINE)
        ui.onNodeWithTag("period-timeline").performScrollToNode(hasTestTag("timeline:skip:${skipped.id}"))
        ui.onNodeWithTag("timeline:skip:${skipped.id}").assertIsDisplayed()
        val view=PeriodTimelineProjection.build(model.extra.value,emptyList(),Instant.now())
        assertFalse(view.upcoming.any{it.key=="skip:${skipped.id}"})
        assertNotNull(view.events.single{it.key=="skip:${skipped.id}"}.displayPeriodKey)
        assertFalse(view.importedHistory.any{bucket->bucket.records.any{it.id==skipped.id}})
        assertEquals(before,runBlocking{repo.containers()}.filter{it.medication_id==id})
        // A new real Activity reads the saved skip; no test controller or injected projection.
        activity!!.close();activity=null;launch();navigate(Destination.HISTORY)
        ui.waitUntil(30_000){model.extra.value.records.any{it.id==skipped.id}}
        ui.onNodeWithTag("history-records").performScrollToNode(hasTestTag("history-record:${skipped.id}"))
        ui.onNodeWithTag("history-record:${skipped.id}").performClick()
        ui.onNodeWithTag("history-undo-skip:${skipped.id}").performClick()
        ui.onNodeWithTag("undo-skip-confirm").performClick()
        ui.waitUntil(30_000){model.extra.value.records.none{it.id==skipped.id}}
        navigate(Destination.TIMELINE)
        ui.onNodeWithTag("timeline:skip:${skipped.id}").assertDoesNotExist()
        val current=model.libraryRuntime.currentRepository()
        assertEquals(before,runBlocking{current.containers()}.filter{it.medication_id==id})
        val restored=runBlocking{current.calendar(Instant.now(),zone,scheduled.toLocalDate())}.single{it.slot.key==slot.key}
        assertTrue(restored.state in OPEN_STATES)
        assertEquals(slot.at,restored.slot.at)
        assertEquals(slot.dose,restored.slot.dose,0.0)
    }
}
