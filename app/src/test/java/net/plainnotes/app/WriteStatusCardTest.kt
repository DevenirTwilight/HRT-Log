package net.plainnotes.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import net.plainnotes.app.domain.write.*
import net.plainnotes.app.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Real Compose component tests; not an activated P4 save flow or a device/TalkBack test. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[35],qualifiers="en-rUS-w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WriteStatusCardTest {
    @get:Rule val ui=createAndroidComposeRule<ComponentActivity>()
    @Test fun pendingDoesNotBlockBrowsingAndHideCannotRetryOrClaimSuccess() {
        var browsed=0;var hidden=0;var retries=0
        val state=WriteProgressSummary("SYNTHETIC",WriteStatus.PENDING,WritePhase.WRITING)
        ui.setContent {NotesTheme(ThemeMode.LIGHT){Column {
            WriteStatusCard(state,{hidden++},onRetry={retries++})
            Button(onClick={browsed++}){Text("SYNTHETIC browse")}
        }}}
        ui.onNodeWithText("SYNTHETIC browse").performClick()
        ui.onNodeWithTag("write-status-hide").performClick()
        ui.onNodeWithTag("write-status-retry").assertDoesNotExist()
        ui.onNodeWithText("Saved").assertDoesNotExist()
        assertEquals(1,browsed);assertEquals(1,hidden);assertEquals(0,retries)
    }
    @Test fun unknownOutcomeShowsManualCheckAndNeverOffersRetry() {
        val view=mutableStateOf(WriteProgressSummary("SYNTHETIC",WriteStatus.PENDING,WritePhase.RECONCILING))
        var checks=0;var retries=0
        ui.setContent {NotesTheme(ThemeMode.LIGHT){WriteStatusCard(view.value,{},onCheck={checks++},onRetry={retries++})}}
        ui.onNodeWithText("Checking save result").assertExists()
        ui.onNodeWithTag("write-status-check").assertDoesNotExist()
        ui.runOnIdle {view.value=view.value.copy(phase=WritePhase.MANUAL_CHECK)}
        ui.onNodeWithText("Save result needs checking").assertExists()
        ui.onNodeWithTag("write-status-check").performClick()
        ui.onNodeWithTag("write-status-retry").assertDoesNotExist()
        ui.onNodeWithText("Not saved").assertDoesNotExist()
        assertEquals(1,checks);assertEquals(0,retries)
    }
    @Test fun onlyExplicitlyConfirmedFailureCanOfferRetryAndSuccessNeverDoes() {
        val view=mutableStateOf(WriteProgressSummary("SYNTHETIC",WriteStatus.FAILED,WritePhase.TERMINAL))
        var retries=0
        ui.setContent {NotesTheme(ThemeMode.LIGHT){WriteStatusCard(view.value,{},onRetry={retries++})}}
        ui.onNodeWithTag("write-status-retry").assertDoesNotExist()
        ui.runOnIdle {view.value=view.value.copy(retryAllowed=true)}
        ui.onNodeWithTag("write-status-retry").performClick();assertEquals(1,retries)
        ui.runOnIdle {view.value=WriteProgressSummary("SYNTHETIC",WriteStatus.SUCCESS,WritePhase.TERMINAL)}
        ui.onNodeWithText("Saved").assertExists()
        ui.onNodeWithTag("write-status-retry").assertDoesNotExist()
        ui.onNodeWithTag("write-status-check").assertDoesNotExist()
    }
}
