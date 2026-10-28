package net.plainnotes.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import net.plainnotes.app.ui.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A real saved-state round trip, not a recomposition: no repository or fixture health data. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=android.app.Application::class)
class CenterStateRestorationTest {
    @get:Rule val ui=createAndroidComposeRule<ComponentActivity>()
    @Test fun openedCenterSurvivesHostStateRestorationAndExplicitCloseStaysClosed() {
        val restoration=StateRestorationTester(ui)
        var businessActions=0
        restoration.setContent {NotesTheme(ThemeMode.LIGHT) {
            MedicationsScreen(NotesState(loading=false),{businessActions++},{businessActions++},{businessActions++},PaddingValues(),
                onReference={businessActions++},onBrowseReference={businessActions++})
        }}
        ui.onNodeWithTag("drug-center-open").performClick()
        ui.onNodeWithTag("drug-center-window").assertExists()
        restoration.emulateSavedInstanceStateRestore()
        ui.onNodeWithTag("drug-center-window").assertExists()
        ui.onNodeWithTag("drug-center-close").assertIsDisplayed().performClick()
        ui.onNodeWithTag("drug-center-window").assertDoesNotExist()
        restoration.emulateSavedInstanceStateRestore()
        ui.onNodeWithTag("drug-center-window").assertDoesNotExist()
        ui.onNodeWithTag("drug-center-open").assertIsDisplayed()
        assertEquals("Restoration neither browses nor creates/saves medication",0,businessActions)
    }
}
