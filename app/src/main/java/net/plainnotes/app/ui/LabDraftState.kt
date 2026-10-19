package net.plainnotes.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import java.time.Instant

/** Unsaved clinical input stays in memory only: no Bundle, SavedStateHandle, preferences, file or database. */
@Stable class LabBatchDraft {
    var nextKey by mutableIntStateOf(1)
    val rows=mutableStateListOf(LabBatchRow(0,"E2"))
    var time by mutableStateOf(Instant.now().toLocalHere())
    var laboratory by mutableStateOf("")
    var includeEstimate by mutableStateOf(false)
    fun discard() {
        rows.forEach{it.value="";it.lo="";it.hi="";it.note=""}
        rows.clear();laboratory="";includeEstimate=false
    }
}

/** Activity-owned, primary-space editor state; survives rotation, never process recreation. */
class LabUiState:ViewModel() {
    val dialogs=LabDialogs()
    var token:Any=Any()
        private set
    var draft by mutableStateOf(LabBatchDraft())
        private set
    fun discardDraft() {draft.discard();draft=LabBatchDraft();token=Any()}
    fun clear() {
        dialogs.labNew=false;dialogs.labEdit=null;dialogs.panel=null;dialogs.saveAs=null
        dialogs.batchFailed=false;dialogs.batchSaving=false
        discardDraft()
    }
    override fun onCleared() {clear()}
}

private fun Context.hostActivity():Activity? {
    var current:Context=this
    repeat(16) {
        if(current is Activity)return current as Activity
        val wrapper=current as? ContextWrapper ?: return null
        val next=wrapper.baseContext
        if(next===current)return null
        current=next
    }
    return null
}

@Composable internal fun rememberLabUiState():LabUiState {
    val activity=LocalContext.current.hostActivity()
    val owner=requireNotNull(activity as? ViewModelStoreOwner)
    val state=remember(owner){ViewModelProvider(owner)[LabUiState::class.java]}
    // A lock/disguise removal or real teardown clears drafts. A configuration change retains only memory.
    DisposableEffect(state,activity) {onDispose{if(activity?.isChangingConfigurations!=true)state.clear()}}
    return state
}
