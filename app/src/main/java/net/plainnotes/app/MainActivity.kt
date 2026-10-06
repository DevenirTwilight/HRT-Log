package net.plainnotes.app

import android.app.ActivityManager
import android.content.Intent
import android.hardware.SensorManager
import android.os.Build
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
import net.plainnotes.app.disguise.Disguise
import net.plainnotes.app.security.AppLock
import net.plainnotes.app.security.Session
import net.plainnotes.app.security.ShakeDetector
import net.plainnotes.app.ui.LockScreen
import net.plainnotes.app.ui.NotesApp
import net.plainnotes.app.ui.NotesTheme
import net.plainnotes.app.ui.UiPrefs

@AndroidEntryPoint class MainActivity : AppCompatActivity() {
    private val model: NotesViewModel by viewModels()
    private lateinit var lock: AppLock
    private var locked by mutableStateOf(false)
    private var leftAt = 0L
    private var sessionGeneration = Session.generation
    private val shake = ShakeDetector { if (Disguise.enabled(this)) Disguise.exit(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        // In disguise mode the app is only reachable through the shell code; recents and stale tasks go back to the shell.
        if (Disguise.enabled(this) && !Session.open) { Disguise.exit(this); return }
        sessionGeneration = Session.generation
        Disguise.shell(this)?.let { setTaskDescription(taskDescription(getString(it.label), it.icon)) }
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
        val picking = Session.externalPicker; Session.externalPicker = false
        if (Disguise.enabled(this) && !Session.open) { Disguise.exit(this); return }
        if (picking) { leftAt = 0; return }
        if (lock.enabled && leftAt > 0 && SystemClock.elapsedRealtime() - leftAt >= UiPrefs(this).lockAfterMillis) locked = true
    }
    override fun onStop() {
        super.onStop(); leftAt = SystemClock.elapsedRealtime()
        // Disguise mode locks as soon as the app leaves the screen; only a system file picker is exempt.
        if (Disguise.enabled(this) && !Session.externalPicker && !isChangingConfigurations) Session.lock(sessionGeneration)
    }
    override fun onResume() { super.onResume(); if (Disguise.enabled(this) && !Session.open) return; model.sync(); if (Disguise.enabled(this)) shake.register(getSystemService(SensorManager::class.java)) }
    override fun onPause() { super.onPause(); shake.unregister(getSystemService(SensorManager::class.java)) }

    @Suppress("DEPRECATION")
    private fun taskDescription(label: String, icon: Int) =
        if (Build.VERSION.SDK_INT >= 28) ActivityManager.TaskDescription(label, icon, 0) else ActivityManager.TaskDescription(label)

    private fun biometricAvailable() = BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
    private fun showBiometric() {
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { locked = false }
        })
        prompt.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle(getString(R.string.lock_title)).setNegativeButtonText(getString(R.string.lock_use_pin))
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK).build())
    }
}
