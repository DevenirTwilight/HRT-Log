package net.plainnotes.app.debug

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.PaddingValues
import net.plainnotes.app.ui.AboutScreen
import net.plainnotes.app.ui.NotesTheme
import net.plainnotes.app.ui.ThemeMode

/** Debug instrumentation only (PRIVACY-P1): the production About screen, so device tests can recreate the activity and
 * check that the offline privacy notice survives rotation and closes with system back. No data, no release entry. */
class AboutTestActivity : ComponentActivity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContent { NotesTheme(ThemeMode.LIGHT) { AboutScreen(PaddingValues()) } }
    }
}
