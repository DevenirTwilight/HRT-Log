package net.plainnotes.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import net.plainnotes.app.data.*
import net.plainnotes.app.ui.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[35],application=android.app.Application::class)
class LongitudinalUiTest {
    @get:Rule val ui=createAndroidComposeRule<ComponentActivity>()
    @Test fun milestoneRequiresCustomTitleAndPreservesExplicitUserDate() {
        var saved:MilestoneEntity?=null
        ui.setContent{MaterialTheme{LongitudinalScreen(NotesState(loading=false),NotesViewModel.ExtraState(),{saved=it},{},{},PaddingValues())}}
        ui.onNodeWithText(ui.activity.getString(R.string.milestone_add)).performClick()
        ui.onNodeWithText(ui.activity.getString(R.string.save)).assertIsNotEnabled()
        ui.onNodeWithText(ui.activity.getString(R.string.milestone_title)).performTextInput("Synthetic milestone")
        ui.onNodeWithText(ui.activity.getString(R.string.save)).performClick()
        ui.runOnIdle{assertEquals("CUSTOM",saved!!.kind);assertEquals("Synthetic milestone",saved!!.title);assertEquals(java.time.LocalDate.now().toString(),saved!!.date)}
    }
    @Test fun simpleModeHidesMilestoneTitleAndNote() {
        val extra=NotesViewModel.ExtraState(milestones=listOf(MilestoneEntity(1,java.time.LocalDate.now().toString(),title="Synthetic sensitive title",note="Synthetic sensitive note")))
        ui.setContent{androidx.compose.runtime.CompositionLocalProvider(LocalSimpleMode provides true){MaterialTheme{LongitudinalScreen(NotesState(loading=false),extra,{},{},{},PaddingValues())}}}
        ui.onAllNodesWithText("Synthetic sensitive title").assertCountEquals(0)
        ui.onAllNodesWithText("Synthetic sensitive note").assertCountEquals(0)
    }
    @Test fun historicalStartedMilestoneIsVisibleAndOpensItsExactSourceWithoutInferringStart() {
        val oldDate=java.time.LocalDate.now().minusDays(400).toString()
        val extra=NotesViewModel.ExtraState(milestones=listOf(MilestoneEntity(42,oldDate,kind="STARTED",note="Synthetic exact-source note")))
        ui.setContent{MaterialTheme{LongitudinalScreen(NotesState(loading=false),extra,{},{},{},PaddingValues())}}
        ui.onNodeWithTag("period-timeline").performScrollToNode(hasTestTag("timeline:milestone:42"))
        ui.onNodeWithTag("timeline:milestone:42").performClick()
        ui.onNodeWithText("Synthetic exact-source note").assertIsDisplayed()
    }
    @Test fun failedSaveKeepsDraftAndSavingDisablesRepeatWhileStateRestorationPreservesInput() {
        val restoration=androidx.compose.ui.test.junit4.StateRestorationTester(ui)
        val result=androidx.compose.runtime.mutableStateOf(net.plainnotes.app.timeline.MilestoneSaveState())
        var calls=0
        restoration.setContent{MaterialTheme{LongitudinalScreen(NotesState(loading=false),NotesViewModel.ExtraState(),{calls++;result.value=net.plainnotes.app.timeline.MilestoneSaveState(saving=true)},{},{},PaddingValues(),saveState=result.value)}}
        ui.onNodeWithText(ui.activity.getString(R.string.milestone_add)).performClick()
        ui.onNodeWithText(ui.activity.getString(R.string.milestone_title)).performTextInput("Synthetic restored draft")
        restoration.emulateSavedInstanceStateRestore()
        ui.onNodeWithText("Synthetic restored draft").assertExists()
        ui.onNodeWithText(ui.activity.getString(R.string.save)).performClick()
        ui.onNodeWithText(ui.activity.getString(R.string.milestone_saving)).assertIsNotEnabled()
        ui.runOnIdle{assertEquals(1,calls);result.value=net.plainnotes.app.timeline.MilestoneSaveState(failed=true)}
        ui.onNodeWithText("Synthetic restored draft").assertExists()
        ui.onNodeWithText(ui.activity.getString(R.string.operation_error)).assertExists()
        ui.onNodeWithText(ui.activity.getString(R.string.save)).assertIsEnabled()
    }

}
