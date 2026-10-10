package net.plainnotes.app

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import net.plainnotes.app.ui.Destination
import net.plainnotes.app.ui.DrawerContent
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The approved navigation entry (PD-2026-10-10-M2-ENTRY): one clearly labelled drawer item, next to the concentration page. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[35],application=android.app.Application::class,qualifiers="w320dp-h480dp")
class ExperimentalPkNavigationTest {
    @get:Rule val ui=createAndroidComposeRule<ComponentActivity>()
    private fun opens(expectedLabel:String) {
        val chosen=mutableListOf<Destination>()
        ui.setContent{MaterialTheme{DrawerContent(Destination.CONCENTRATION){chosen+=it}}}
        assertEquals(expectedLabel,ui.activity.getString(R.string.xpk_title))
        ui.onNodeWithTag("drawer-content").performScrollToNode(hasText(expectedLabel))
        ui.onNodeWithText(expectedLabel).assertIsDisplayed().performClick()
        ui.runOnIdle{assertEquals(listOf(Destination.EXPERIMENTAL_PK),chosen)}
    }
    @Test fun drawerShowsTheExperimentalPkModelEntry()=opens("Experimental PK model")
    @Test @Config(qualifiers="zh-w320dp-h480dp") fun chineseDrawerLabelIsExperimentalPkModel()=opens("实验药代模型")
    @Test fun entryFollowsTheConcentrationPageAndTheOfficialPageStaysFirst() {
        val order=Destination.entries
        assertEquals(order.indexOf(Destination.CONCENTRATION)+1,order.indexOf(Destination.EXPERIMENTAL_PK))
        assertEquals(R.string.xpk_title,Destination.EXPERIMENTAL_PK.title)
    }
}
