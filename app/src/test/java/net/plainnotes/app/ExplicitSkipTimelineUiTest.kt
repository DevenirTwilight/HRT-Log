package net.plainnotes.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import net.plainnotes.app.data.*
import net.plainnotes.app.ui.*
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.*

@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk=[35],application=android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ExplicitSkipTimelineUiTest(private val language:String) {
    companion object { @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name="{0}/320dp/2x") fun languages()=listOf("en","zh-rCN","b+zh+Hant","fr").map{arrayOf<Any>(it)} }
    @get:Rule val ui=createAndroidComposeRule<ComponentActivity>()
    @Before fun locale(){RuntimeEnvironment.setQualifiers("$language-w320dp-h640dp-xxhdpi")}
    @Test fun actualOverrideDialogSelectsSkipWithoutChangingTimeOrDose() {
        val now=Instant.now();val at=now.plusSeconds(7200)
        val slot=net.plainnotes.app.domain.Slot("synthetic-slot",7,1,at,at,ZoneId.of("UTC"),2.0,15,120,now)
        var saved:net.plainnotes.app.domain.SlotOverride?=null
        ui.setContent { MaterialTheme { OverrideDialog(net.plainnotes.app.domain.TimelineEntry(slot,net.plainnotes.app.domain.SlotState.PENDING),null,
            net.plainnotes.app.domain.SlotOverride(slot.key),{}) { saved=it } } }
        val label=ui.activity.getString(R.string.skipped)
        ui.onNodeWithContentDescription(label).assertIsOff().performClick()
        ui.onNode(hasText(ui.activity.getString(R.string.save)) and hasAnyAncestor(isDialog())).performClick()
        org.junit.Assert.assertEquals(net.plainnotes.app.domain.SlotOverride(slot.key,skipped=true),saved)
    }
    @Test fun skippingPreservesPreviouslySavedMoveAndDoseOverride() {
        val now=Instant.now();val original=now.plusSeconds(7200)
        val slot=net.plainnotes.app.domain.Slot("synthetic-moved-slot",7,1,original,original,ZoneId.of("UTC"),2.0,15,120,now)
        val initial=net.plainnotes.app.domain.SlotOverride(slot.key,original.plusSeconds(3600),ZoneId.of("UTC"),3.0)
        var saved:net.plainnotes.app.domain.SlotOverride?=null
        ui.setContent { MaterialTheme { OverrideDialog(net.plainnotes.app.domain.TimelineEntry(initial.apply(slot),net.plainnotes.app.domain.SlotState.PENDING),null,
            initial,{}) { saved=it } } }
        ui.onNodeWithContentDescription(ui.activity.getString(R.string.skipped)).performClick()
        ui.onNode(hasText(ui.activity.getString(R.string.save)) and hasAnyAncestor(isDialog())).performClick()
        org.junit.Assert.assertEquals(initial.copy(skipped=true),saved)
    }
    @Test fun confirmedSkipIsUnderTreatmentWithFrozenNameTimeAndPlannedDose() {
        val now=Instant.now();val scheduled=now.plusSeconds(3600)
        val med=MedicationEntity(1,"Synthetic frozen medicine","E2","ORAL","MG",2.0,20.0,site_rotation=false,notifications_on=false,active=true,sort_order=0)
        val frozen=MedicationSnapshot.encode(med,ProfileEntity(1,"E2","oral"))
        val definition=RegimenDefinition(frozen,"EVERY_N_DAYS",1,0,2.0,"UTC","2026-01-01",null,listOf("08:00:00" to null))
        val version=RegimenVersionEntity(1,1,now.minusSeconds(86400).toEpochMilli(),null,"UTC",definition.json(),definition.signature(),"APP",now.minusSeconds(86400).toEpochMilli())
        val row=RecordEntity(9,1,rule_version_id=7,slot_key="synthetic-slot",scheduled_utc=scheduled.toEpochMilli(),scheduled_zone="UTC",planned_dose=2.0,status="SKIPPED",origin="APP",revision=1,config_snapshot=frozen)
        ui.setContent { CompositionLocalProvider(LocalDensity provides Density(3f,2f)) { MaterialTheme {
            LongitudinalScreen(NotesState(loading=false),NotesViewModel.ExtraState(records=listOf(row),regimens=listOf(version)),{},{},{},PaddingValues())
        } } }
        ui.onNodeWithTag("period-timeline").performScrollToNode(hasTestTag("timeline:skip:9"))
        ui.onNodeWithTag("timeline:skip:9").assertIsDisplayed().assertHasClickAction()
        ui.onNodeWithText(ui.activity.getString(R.string.timeline_explicit_skipped),substring=true).assertExists()
        ui.onNodeWithTag("timeline:skip:9").assertTextContains("Synthetic frozen medicine",substring=true)
        ui.onNodeWithTag("timeline-upcoming-card").assertDoesNotExist()
        ui.onNodeWithText(ui.activity.getString(R.string.timeline_imported_history)).assertDoesNotExist()
        ui.onNodeWithTag("timeline:skip:9").performClick()
        ui.onNodeWithTag("timeline-event-time").assertTextContains("UTC",substring=true)
        ui.onNodeWithText(ui.activity.getString(R.string.history_planned_dose_label,ui.activity.getString(R.string.dose_display,"2",ui.activity.getString(R.string.choice_mg)))).assertExists()
    }
}
