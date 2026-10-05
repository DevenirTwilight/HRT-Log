package net.plainnotes.app

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import net.plainnotes.app.security.AppLock
import net.plainnotes.app.ui.LockScreen
import net.plainnotes.app.ui.NotesApp
import net.plainnotes.app.ui.NotesTheme
import net.plainnotes.app.ui.UiPrefs

@AndroidEntryPoint class MainActivity : AppCompatActivity() {
    private val model: NotesViewModel by viewModels()
    private lateinit var lock: AppLock
    private var locked by mutableStateOf(false)
    private var leftAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val prefs = UiPrefs(this)
        lock = AppLock(this)
        locked = lock.enabled
        setContent {
            var appearance by remember { mutableStateOf(prefs.appearance) }
            NotesTheme(appearance.mode, appearance.dynamic, appearance.contrast) {
                if (locked) LockScreen({ pin -> lock.verify(pin).also { if (it) locked = false } }, { lock.waitMillis() }, if (lock.biometric && biometricAvailable()) ::showBiometric else null)
                else NotesApp(model, appearance) { appearance = it; prefs.appearance = it }
            }
        }
        intent.getStringExtra("reminder_id")?.let { model.notification(it) }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); intent.getStringExtra("reminder_id")?.let { model.notification(it) } }
    override fun onStart() {
        super.onStart()
        if (lock.enabled && leftAt > 0 && SystemClock.elapsedRealtime() - leftAt >= UiPrefs(this).lockAfterMillis) locked = true
    }
    override fun onStop() { super.onStop(); leftAt = SystemClock.elapsedRealtime() }
    override fun onResume() { super.onResume(); model.sync() }

    private fun biometricAvailable() = BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
    private fun showBiometric() {
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { locked = false }
        })
        prompt.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle(getString(R.string.lock_title)).setNegativeButtonText(getString(R.string.lock_use_pin))
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK).build())
    }
}
