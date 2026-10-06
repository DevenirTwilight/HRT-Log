package net.plainnotes.app.reminder

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[28])
class NotificationPrivacyTest {
    private lateinit var prefs:NotificationPrefs
    @Before fun setup(){prefs=NotificationPrefs(ApplicationProvider.getApplicationContext<Context>());prefs.clear()}
    @After fun cleanup(){prefs.clear()}
    @Test fun disguiseSuppressesDetailsAndCustomTextWithoutErasingUserPreferences() {
        prefs.title="Synthetic custom title";prefs.body="Synthetic custom content";prefs.details=true
        prefs.disguised=true
        assertNull(prefs.title);assertNull(prefs.body);assertFalse(prefs.details)
        prefs.disguised=false
        assertEquals("Synthetic custom title",prefs.title);assertEquals("Synthetic custom content",prefs.body);assertTrue(prefs.details)
    }
}
