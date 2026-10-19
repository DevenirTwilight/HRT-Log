package net.plainnotes.app.data
import android.content.Context
import org.junit.runner.RunWith
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.AndroidJUnit4
@Suppress("DEPRECATION") @RunWith(AndroidJUnit4::class)
class LabPanelDataAndroidTest : LabPanelDataContract() {
    override val assetsContext: Context get() = InstrumentationRegistry.getInstrumentation().context
}
