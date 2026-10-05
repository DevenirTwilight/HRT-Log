package net.plainnotes.app

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dagger.hilt.android.AndroidEntryPoint
import net.plainnotes.app.ui.NotesApp
import net.plainnotes.app.ui.NotesTheme
import net.plainnotes.app.ui.UiPrefs

@AndroidEntryPoint class MainActivity : ComponentActivity() {
    private val model: NotesViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val prefs = UiPrefs(this)
        setContent {
            var appearance by remember { mutableStateOf(prefs.appearance) }
            NotesTheme(appearance.mode, appearance.dynamic) { NotesApp(model, appearance) { appearance = it; prefs.appearance = it } }
        }
        intent.getStringExtra("reminder_id")?.let { model.notification(it) }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); intent.getStringExtra("reminder_id")?.let { model.notification(it) } }
    override fun onResume() { super.onResume(); model.sync() }
}
