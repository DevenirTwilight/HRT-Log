package net.plainnotes.app.timeline

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import net.plainnotes.app.*
import net.plainnotes.app.data.*
import net.plainnotes.app.ui.*
import org.junit.Test
import org.junit.Rule
import java.time.LocalDate

/** Native long-scroll/accessibility smoke test with synthetic milestones; no health data. */
class TimelineAndroidTest {
    @get:Rule val ui=createAndroidComposeRule<ComponentActivity>()
    @Test fun largeFontHistoricalTimelineScrollsToTheExactOldSource() {
        val today=LocalDate.now()
        val extra=NotesViewModel.ExtraState(milestones=(1..160).map{MilestoneEntity(it.toLong(),today.minusDays(it.toLong()*4).toString(),"CUSTOM","Synthetic $it","Synthetic exact-source $it")})
        ui.setContent{val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,2f)){MaterialTheme{LongitudinalScreen(NotesState(loading=false),extra,{},{},{},PaddingValues())}}}
        ui.onNodeWithTag("period-timeline").performScrollToNode(hasTestTag("timeline:milestone:160"))
        ui.onNodeWithTag("timeline:milestone:160").assertHasClickAction().performClick()
        ui.onNodeWithText("Synthetic exact-source 160").assertIsDisplayed()
    }
    @Test fun oldImportedHistoryWithoutARegimenOpensExactSources() {
        val time=java.time.Instant.now().minusSeconds(400*86400L).toEpochMilli()
        val medication=MedicationEntity(1,"Synthetic frozen import","E2","SUBLINGUAL","MG",2.0,40.0,site_rotation=false,notifications_on=false,active=true,sort_order=0)
        val row=RecordEntity(71,1,taken_utc=time,taken_zone="UTC",actual_dose=2.0,status="ON_TIME",origin="IMPORT_HT",source_record_key="ht:synthetic",revision=1,
            config_snapshot=MedicationSnapshot.encode(medication,ProfileEntity(1,"E2","sublingual")))
        var opened:List<Long>?=null
        ui.setContent{MaterialTheme{LongitudinalScreen(NotesState(loading=false),NotesViewModel.ExtraState(records=listOf(row)),{},{},{},PaddingValues(),onImportedHistory={opened=it})}}
        ui.onNodeWithTag("timeline:import-history:IMPORT_HT:unknown:false").assertIsDisplayed().performClick()
        ui.onNodeWithText("Synthetic frozen import").assertIsDisplayed()
        ui.onNodeWithText(ui.activity.getString(R.string.timeline_imported_open_history)).performClick()
        ui.runOnIdle{org.junit.Assert.assertEquals(listOf(71L),opened)}
    }
    @Test fun simpleModeImportedDetailHidesFrozenMedicationNames() {
        val time=java.time.Instant.now().minusSeconds(400*86400L).toEpochMilli()
        val row=RecordEntity(71,1,taken_utc=time,taken_zone="UTC",actual_dose=null,status="ON_TIME",origin="IMPORT_TM",source_record_key="tm:synthetic",revision=1,
            config_snapshot="""{"name":"Synthetic private import","molecule":"E2","unit":"MG"}""")
        ui.setContent{CompositionLocalProvider(LocalSimpleMode provides true){MaterialTheme{
            LongitudinalScreen(NotesState(loading=false),NotesViewModel.ExtraState(records=listOf(row)),{},{},{},PaddingValues())}}}
        ui.onNodeWithTag("timeline:import-history:IMPORT_TM:unknown:false").performClick()
        ui.onNodeWithText("Synthetic private import").assertDoesNotExist()
        ui.onNodeWithText(ui.activity.getString(R.string.timeline_imported_open_history)).assertIsDisplayed()
    }
}
