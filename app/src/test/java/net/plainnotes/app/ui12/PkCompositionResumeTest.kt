package net.plainnotes.app.ui12

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.plainnotes.app.ui.computeOffMain
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Collections

/** PR167's already authorized dispatcher bridge, scoped to the PK regression seen in run41123635349. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[35], application=android.app.Application::class)
class PkCompositionResumeTest {
    @get:Rule val ui=createAndroidComposeRule<ComponentActivity>()
    @Test fun asynchronousComputationRestoresTheViewOwningThread() {
        val after=Collections.synchronizedMap(HashMap<String,Thread>())
        val main=Looper.getMainLooper().thread
        ui.setContent { LaunchedEffect(Unit) {
            // Force a genuine asynchronous completion; this delay does not wait for a UI condition.
            withContext(Dispatchers.Default) { Thread.sleep(20) }
            after["bare"]=Thread.currentThread()
            computeOffMain { Thread.sleep(20) }
            after["bridge"]=Thread.currentThread()
        } }
        // A plain Map read does not drain Robolectric's main looper; synchronize it as in PR167.
        ui.waitUntil(10_000) { ui.waitForIdle(); after.size==2 }
        assertNotSame("The original test-harness failure mechanism must actually be exercised",main,after["bare"])
        assertSame("Background work must resume on the thread owning the view",main,after["bridge"])
    }
}
