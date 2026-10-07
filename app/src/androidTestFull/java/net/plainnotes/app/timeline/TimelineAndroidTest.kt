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
}
