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
    @Test fun skipInCalendarBelongsToTreatmentAndCanBeUndoneAfterReopen() {
        launch()
        val repo=model.libraryRuntime.currentRepository()
        val now=Instant.now();val zone=ZoneId.systemDefault()
        val scheduled=now.atZone(zone).plusHours(2).withSecond(0).withNano(0)
        val id=runBlocking{repo.saveMedication(MedicationEntity(name="SYNTHETIC explicit skip",molecule="E2",route="ORAL",unit="MG",
            dose_per_intake=2.0,container_capacity=20.0,site_rotation=false,notifications_on=false,
            soon_alert_minutes=15,late_after_minutes=120,active=true,sort_order=0),null,RuleKind.EVERY_N_DAYS,1,
            listOf(scheduled.toLocalTime()),emptySet(),now.minusSeconds(86400))}
        runBlocking{repo.addContainers(id,20.0,1,true);model.refresh().join()}
        val before=runBlocking{repo.containers()}.filter{it.medication_id==id}
        val slot=runBlocking{repo.calendar(now,zone,scheduled.toLocalDate())}.single{it.slot.medicationId==id && it.slot.at==scheduled.toInstant()}.slot
        ui.waitUntil(30_000){!model.state.value.loading && model.state.value.medications.any{it.id==id}}
        ui.onNodeWithTag("recent-day:${scheduled.toLocalDate()}").performClick()
        ui.onNodeWithTag("day-dose:${slot.key}").performScrollTo()
        ui.onNode(hasText(context.getString(R.string.reschedule)) and hasAnyAncestor(hasTestTag("day-dose:${slot.key}"))).performClick()
        ui.onNodeWithContentDescription(context.getString(R.string.skipped)).assertIsOff().performClick()
        ui.onNode(hasText(context.getString(R.string.save)) and hasAnyAncestor(isDialog())).performClick()
        ui.waitUntil(30_000){model.extra.value.records.any{it.medication_id==id && it.status=="SKIPPED"}}
        val skipped=model.extra.value.records.single{it.medication_id==id && it.status=="SKIPPED"}
        assertNull(skipped.taken_utc);assertNull(skipped.actual_dose);assertEquals(slot.at.toEpochMilli(),skipped.scheduled_utc)
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
        assertEquals(SlotState.PENDING,runBlocking{current.calendar(Instant.now(),zone,scheduled.toLocalDate())}.single{it.slot.key==slot.key}.state)
    }
}
