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
}
