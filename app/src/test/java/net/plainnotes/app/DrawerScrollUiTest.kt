package net.plainnotes.app

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import net.plainnotes.app.ui.Destination
import net.plainnotes.app.ui.DrawerContent
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * P1 finding F1 (2026-10-10): on a 320x640 screen the drawer did not scroll, so Settings and About were unreachable.
 * The drawer must scroll to every destination on a short screen, including with a large font scale.
 */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[35],application=android.app.Application::class,qualifiers="w320dp-h480dp")
class DrawerScrollUiTest {
    @get:Rule val ui=createAndroidComposeRule<ComponentActivity>()
    private fun reachesEveryDestination() {
        val chosen=mutableListOf<Destination>()
        ui.setContent{MaterialTheme{DrawerContent(Destination.CALENDAR){chosen+=it}}}
        for (d in listOf(Destination.SETTINGS,Destination.ABOUT)) {
            val label=ui.activity.getString(d.title)
            ui.onNodeWithTag("drawer-content").performScrollToNode(hasText(label))
            ui.onNodeWithText(label).assertIsDisplayed().performClick()
        }
        ui.runOnIdle{assertEquals(listOf(Destination.SETTINGS,Destination.ABOUT),chosen)}
    }
    @Test fun settingsAndAboutAreReachableOnAShortScreen()=reachesEveryDestination()
    @Test @Config(qualifiers="w320dp-h480dp",fontScale=2.0f) fun settingsAndAboutAreReachableWithLargeText()=reachesEveryDestination()
}
