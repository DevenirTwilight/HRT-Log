package net.plainnotes.app.privacy

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Ephemeral user intent only. Never save this state, a password, or invoke restore from a backup result. */
class RestoreProtection {
    enum class Notice { CANCELLED, FAILED, SAVED, SAME_TARGET }
    sealed interface Stage {
        data object Idle:Stage
        data class Choice(val source:Uri,val notice:Notice?=null):Stage
        data class PickingBackup(val source:Uri):Stage
        data class BackupPassword(val source:Uri,val target:Uri):Stage
        data class BackingUp(val source:Uri):Stage
        data class RestorePassword(val source:Uri):Stage
    }
    var stage:Stage by mutableStateOf(Stage.Idle)
        private set
    fun select(uri:Uri?){stage=uri?.let{Stage.Choice(it)} ?: Stage.Idle}
    fun chooseBackup(){(stage as? Stage.Choice)?.let{stage=Stage.PickingBackup(it.source)}}
    fun destination(uri:Uri?){(stage as? Stage.PickingBackup)?.let{
        stage=when(uri){null->Stage.Choice(it.source,Notice.CANCELLED);it.source->Stage.Choice(it.source,Notice.SAME_TARGET);else->Stage.BackupPassword(it.source,uri)}
    }}
    fun beginBackup(source:Uri,target:Uri):Boolean {
        val current=stage as? Stage.BackupPassword ?: return false
        if(current.source!=source || current.target!=target || source==target)return false
        stage=Stage.BackingUp(source);return true
    }
    fun backupFinished(success:Boolean){(stage as? Stage.BackingUp)?.let{stage=Stage.Choice(it.source,if(success)Notice.SAVED else Notice.FAILED)}}
    fun continueRestore(){(stage as? Stage.Choice)?.let{stage=Stage.RestorePassword(it.source)}}
    fun takeRestore(source:Uri):Uri? {
        val current=stage as? Stage.RestorePassword ?: return null
        if(current.source!=source)return null
        stage=Stage.Idle;return current.source
    }
    fun cancelPassword(){stage=when(val s=stage){is Stage.BackupPassword->Stage.Choice(s.source);is Stage.RestorePassword->Stage.Choice(s.source);else->stage}}
    fun pickerFailed(){(stage as? Stage.PickingBackup)?.let{stage=Stage.Choice(it.source,Notice.FAILED)}}
    fun cancel(){stage=Stage.Idle}
}
