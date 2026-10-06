package net.plainnotes.app.disguise.privatenotes

import android.hardware.SensorManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.plainnotes.app.R
import net.plainnotes.app.disguise.Disguise
import net.plainnotes.app.disguise.ShellTheme
import net.plainnotes.app.security.Session
import net.plainnotes.app.security.ShakeDetector
import net.plainnotes.app.security.UnlockTarget
import net.plainnotes.app.security.taskIdentity

/** No injected HRT services, no main-application layout or route. Intents and restored state cannot authenticate it. */
class PrivateNotesActivity:ComponentActivity() {
    private val owner=Session.generation
    private var contentVisible by mutableStateOf(false)
    private var initialized=false
    private val model:PrivateNotesModel by viewModels { object:ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T:ViewModel> create(modelClass:Class<T>):T=PrivateNotesModel(PrivateStore(applicationContext)) as T
    } }
    private val shake=ShakeDetector{close()}
    override fun onCreate(savedInstanceState:Bundle?) {
        enableEdgeToEdge();super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setTaskDescription(taskIdentity(getString(R.string.private_notes),R.mipmap.ic_shell_notes))
        if(!authorized())return
        contentVisible=true;initialized=true
        onBackPressedDispatcher.addCallback(this,object:OnBackPressedCallback(true){override fun handleOnBackPressed(){close()}})
        setContent { ShellTheme {
            if(contentVisible){val state by model.state.collectAsStateWithLifecycle()
                PrivateNotesScreen(state,::close,model::edit,model::draft,model::cancel,model::save,model::delete,model::reload)
            } else Surface(Modifier.fillMaxSize()){}
        } }
    }
    private fun authorized():Boolean {
        if(!Disguise.enabled(this)){Session.lock(owner);finishAndRemoveTask();return false}
        if(Disguise.hasPrivateCode(this) && Session.allows(UnlockTarget.PRIVATE,owner))return true
        if(Session.target!=null && Session.generation!=owner)finish() else Disguise.exit(this)
        return false
    }
    private fun close(){if(initialized)model.clearMemory();if(Disguise.enabled(this))Disguise.exit(this) else {Session.lock(owner);finishAndRemoveTask()}}
    override fun onStart(){super.onStart();if(initialized && authorized())contentVisible=true}
    override fun onResume(){super.onResume();if(initialized && authorized())shake.register(getSystemService(SensorManager::class.java))}
    override fun onPause(){super.onPause();shake.unregister(getSystemService(SensorManager::class.java))}
    override fun onStop(){super.onStop();if(initialized && !isChangingConfigurations){Session.lock(owner);contentVisible=false;model.clearMemory()}}
}
