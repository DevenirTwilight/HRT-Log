package net.plainnotes.app.security

import android.content.Intent
import android.view.WindowManager
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import net.plainnotes.app.MainActivity
import net.plainnotes.app.disguise.Disguise
import net.plainnotes.app.ui.UiPrefs
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * REQUIREMENTS §135 on the real MainActivity: FLAG_SECURE by default (as before), cleared when the user allowed screenshots,
 * and set again after the setting is turned off. No health data (empty database, no PIN).
 */
class ScreenshotSettingAndroidTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @Before fun setup() { Disguise.disable(context); Session.reset(); AppLock(context).disable(); UiPrefs(context).disclaimerAccepted = true; UiPrefs(context).allowScreenshots = false }
    @After fun cleanup() { UiPrefs(context).allowScreenshots = false; Session.reset() }

    private fun secureWhenLaunched(): Boolean {
        var secure = false
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { s ->
            s.onActivity { a -> secure = a.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0 }
        }
        return secure
    }

    @Test fun mainWindowFollowsTheSetting() {
        assertTrue("default: screenshots blocked", secureWhenLaunched())
        UiPrefs(context).allowScreenshots = true
        assertFalse("allowed: FLAG_SECURE cleared", secureWhenLaunched())
        UiPrefs(context).allowScreenshots = false
        assertTrue("turned off again: blocked", secureWhenLaunched())
    }
}
