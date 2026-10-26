package net.plainnotes.app

import android.content.Intent
import android.hardware.SensorManager
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import net.plainnotes.app.disguise.Disguise
import net.plainnotes.app.security.AppLock
import net.plainnotes.app.security.UnlockTarget
import net.plainnotes.app.security.taskIdentity
import net.plainnotes.app.security.Session
import net.plainnotes.app.security.ShakeDetector
import net.plainnotes.app.security.ScreenCapture
import net.plainnotes.app.ui.LockScreen
import net.plainnotes.app.ui.NotesApp
import net.plainnotes.app.ui.NotesTheme
import net.plainnotes.app.ui.UiPrefs

/**
 * The PIN lock state of one unlocked session. A ViewModel survives only a configuration-change recreation (rotation,
 * dark mode, font size, split screen) of this activity; process death and finishing the activity drop it, so those
 * still start locked. Nothing here is restored from a Bundle or disk.
 */
class LockState : androidx.lifecycle.ViewModel() {
    var initialized = false
    var locked by mutableStateOf(true)
    /** When the activity last left the screen; kept across a recreation so the background auto-lock timer is not reset. */
    var leftAt = 0L
}

@AndroidEntryPoint class MainActivity : AppCompatActivity() {
    private val model: NotesViewModel by viewModels()
    private val lockState: LockState by viewModels()
    private lateinit var lock: AppLock
    private var locked: Boolean get() = lockState.locked; set(value) { lockState.locked = value }
    private var leftAt: Long get() = lockState.leftAt; set(value) { lockState.leftAt = value }
    private var initialized = false
    private var contentVisible by mutableStateOf(true)
    private var sessionGeneration = Session.generation
    private var pendingReminder:Intent?=null
    private var libraryUiOpened=false
    private val shake = ShakeDetector { if (Disguise.enabled(this)) Disguise.exit(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        libraryUiOpened=savedInstanceState?.getBoolean("library.ui.opened",false) ?: false
        intent.putExtra(net.plainnotes.app.runtime.RuntimeLibrarySession.RESTORED,savedInstanceState!=null && (!savedInstanceState.containsKey("library.ui.opened") || libraryUiOpened))
        // §135: secure unless the user allowed screenshots (and never while disguised); set before any content is drawn.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        ScreenCapture.apply(this, UiPrefs(this).allowScreenshots, Disguise.enabled(this))
        // In disguise mode the app is only reachable through the shell code; recents and stale tasks go back to the shell.
        if (!authorized()) return
        sessionGeneration = Session.generation
        val shell=Disguise.shell(this)
        setTaskDescription(taskIdentity(getString(shell?.label ?: R.string.app_name),shell?.icon ?: R.mipmap.ic_launcher))
        val prefs = UiPrefs(this)
        lock = AppLock(this)
        // A configuration-change recreation keeps the session's state; any other creation starts from the lock rule.
        if (!lockState.initialized) { locked = Session.requiresAppPin(Disguise.enabled(this),lock.enabled); lockState.initialized = true }
        initialized=true
        if(!locked)libraryUiOpened=true
        setContent {
            var appearance by remember { mutableStateOf(prefs.appearance) }
            NotesTheme(appearance.mode, appearance.dynamic, appearance.contrast) {
                if (!contentVisible) androidx.compose.material3.Surface(modifier=androidx.compose.ui.Modifier.fillMaxSize()) {}
                else if (locked) LockScreen({ pin -> lock.verify(pin).also { if (it) unlockRuntime() } }, { lock.waitMillis() }, if (lock.biometric && biometricAvailable()) ::showBiometric else null)
                else NotesApp(model, appearance, { appearance = it; prefs.appearance = it }, backToCalendar = !Disguise.enabled(this@MainActivity),onLibraryReopen=::reopenLibrary)
            }
        }
        onBackPressedDispatcher.addCallback(this,object:androidx.activity.OnBackPressedCallback(true){
            override fun handleOnBackPressed(){if(Disguise.enabled(this@MainActivity))Disguise.exit(this@MainActivity) else finish()}
        })
        pendingReminder=intent.takeIf {it.hasExtra("reminder_id") || it.getBooleanExtra(net.plainnotes.app.data.LibraryRequestScope.REJECTED_EXTRA,false)};if(!locked)deliverReminder()
    }
    override fun onSaveInstanceState(outState:Bundle) {outState.putBoolean("library.ui.opened",libraryUiOpened);super.onSaveInstanceState(outState)}
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); if(!authorized() || !initialized)return;pendingReminder=intent.takeIf {it.hasExtra("reminder_id") || it.getBooleanExtra(net.plainnotes.app.data.LibraryRequestScope.REJECTED_EXTRA,false)};if(!locked)deliverReminder() }
    private fun unlockRuntime() {locked=false;libraryUiOpened=true;model.runtimeResumed();model.sync();deliverReminder()}
    private fun deliverReminder() {val pending=pendingReminder ?: return;pendingReminder=null
        if(pending.getBooleanExtra(net.plainnotes.app.data.LibraryRequestScope.REJECTED_EXTRA,false))model.libraryRequestRejected()
        pending.getStringExtra("reminder_id")?.let {model.notification(it,net.plainnotes.app.data.LibraryRequestScope.decode(runCatching {pending.getStringArrayListExtra(net.plainnotes.app.runtime.RuntimeLibrarySession.INTENT_SCOPE)}.getOrNull()))}
    }
    private fun reopenLibrary() {
        if(!authorized() || locked)return
        // A NEW ViewModel/saved-state owner, never a mutable retag of old callbacks or integers.
        if(Disguise.enabled(this))Session.begin(UnlockTarget.PRIMARY)
        startActivity(Intent(this,MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            model.pendingLibraryOperation()?.let {putExtra(net.plainnotes.app.runtime.RuntimeLibrarySession.RESTORE_OPERATION,it)}
        });finish()
    }
    override fun onStart() {
        super.onStart()
        if(!initialized)return
        val picking = Session.consumePicker(sessionGeneration)
        if (!authorized()) return
        contentVisible=true
        if (picking) { leftAt = 0; return }
        if (!Disguise.enabled(this) && lock.enabled && leftAt > 0 && SystemClock.elapsedRealtime() - leftAt >= UiPrefs(this).lockAfterMillis) locked = true
    }
    override fun onStop() {
        // A configuration change is not leaving the app; a real leave starts the auto-lock timer.
        super.onStop(); if (!isChangingConfigurations) leftAt = SystemClock.elapsedRealtime()
        if(initialized && !isChangingConfigurations && !locked)model.runtimeStopped(if(Session.pickerActive(sessionGeneration))null else if(Disguise.enabled(this))0L else if(lock.enabled)UiPrefs(this).lockAfterMillis else null)
        // Disguise mode locks as soon as the app leaves the screen; only a system file picker is exempt.
        if (Disguise.enabled(this) && !Session.pickerActive(sessionGeneration) && !isChangingConfigurations) { Session.lock(sessionGeneration);contentVisible=false }
    }
    override fun onResume() { super.onResume(); ScreenCapture.apply(this, UiPrefs(this).allowScreenshots, Disguise.enabled(this)); if(!initialized || !authorized())return; if(!locked) {model.runtimeResumed();model.sync();deliverReminder()}; if (Disguise.enabled(this)) shake.register(getSystemService(SensorManager::class.java)) }
    override fun onPause() { if(initialized)model.sensitivePresentationHidden(); super.onPause(); shake.unregister(getSystemService(SensorManager::class.java)) }

    /** An old task cannot redirect a newer PRIVATE/PRIMARY session during task replacement. */
    private fun authorized():Boolean {
        if(!Disguise.enabled(this) || Session.allows(UnlockTarget.PRIMARY,sessionGeneration))return true
        if(Session.target!=null && Session.generation!=sessionGeneration)finish() else Disguise.exit(this)
        return false
    }

    private fun biometricAvailable() = BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
    private fun showBiometric() {
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { unlockRuntime() }
        })
        prompt.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle(getString(R.string.lock_title)).setNegativeButtonText(getString(R.string.lock_use_pin))
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK).build())
    }
}
