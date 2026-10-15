package net.plainnotes.app.privacy

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import net.plainnotes.app.R
import net.plainnotes.app.debug.DataPrivacyTestActivity
import net.plainnotes.app.reminder.NotificationPrefs
import net.plainnotes.app.reminder.StockAlerts
import org.junit.*
import org.junit.Assert.*

/** PRIVACY-P1, native emulator only: the production "Delete all data" dialog (real DataSection over the synthetic
 * in-memory fixture) clears stock-alert markers, including legacy keys; cancelling keeps them. Destructive for the test
 * install's own preferences, so it refuses to run anywhere but an emulator. */
class WipeStockAlertsAndroidTest {
    @get:Rule val ui = createAndroidComposeRule<DataPrivacyTestActivity>()
    private val instrument get() = InstrumentationRegistry.getInstrumentation()
    private val context: Context get() = instrument.targetContext
    private val markers get() = context.getSharedPreferences(StockAlerts.PREFS, Context.MODE_PRIVATE)
    private fun text(id: Int) = context.getString(id)

    private var saved: List<DeviceTestHygiene.Saved> = emptyList()
    @Before fun emulatorOnly() {
        val qemu = Class.forName("android.os.SystemProperties").getMethod("get", String::class.java).invoke(null, "ro.kernel.qemu")
        check(qemu == "1") { "Synthetic wipe test runs on an emulator only" }
        saved = DeviceTestHygiene.save(context)
        markers.edit().clear().putString("1:LOW", "2026-03-10").putString("4:EXPIRY", "2026-03-09").putString("legacy-key", "x").commit()
    }
    @After fun cleanup() {
        DeviceTestHygiene.finishAll(); markers.edit().clear().commit(); saved.forEach { it.restore() }
    }
    private fun openDialog() { ui.onNodeWithText(text(R.string.wipe)).performScrollTo().performClick(); ui.onNodeWithText(text(R.string.wipe_action)).assertIsNotEnabled() }

    @Test fun cancellingTheDialogKeepsMarkers() {
        openDialog()
        ui.onNodeWithText(text(R.string.cancel)).performClick(); ui.waitForIdle()
        assertEquals("2026-03-10", markers.getString("1:LOW", null)); assertFalse(StockAlerts.isClear(context))
    }

    @Test fun confirmedWipeClearsCurrentAndLegacyMarkers() {
        NotificationPrefs(context).title = "synthetic"
        openDialog()
        ui.onNode(hasSetTextAction()).performTextInput(text(R.string.wipe_word))
        ui.onNodeWithText(text(R.string.wipe_action)).assertIsEnabled().performClick()
        val original = ui.activity
        ui.waitUntil(20_000) { StockAlerts.isClear(context) && NotificationPrefs(context).title == null }
        // The production flow finishes and relaunches the screen after a wipe; wait for that before cleaning up.
        ui.waitUntil(20_000) { DeviceTestHygiene.activities().any { it is DataPrivacyTestActivity && it !== original } }
        assertNull(markers.getString("1:LOW", null)); assertNull(markers.getString("legacy-key", null))
    }
}
