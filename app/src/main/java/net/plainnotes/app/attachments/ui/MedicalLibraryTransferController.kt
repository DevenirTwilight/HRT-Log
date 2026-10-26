package net.plainnotes.app.attachments.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.result.ActivityResult
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import net.plainnotes.app.attachments.MedicalLibraryGateway
import net.plainnotes.app.data.MedicalLibraryTransfer
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/** UI request state only. The sole persisted value is a neutral operation UUID for read-only reconciliation. */
internal class MedicalLibraryTransferController(private val gateway:MedicalLibraryGateway,private val scope:CoroutineScope,private val saved:SavedStateHandle):AutoCloseable {
    enum class Mode { EXPORT, RESTORE }
    enum class Phase { IDLE, PICKING, PASSWORD, WORKING, REVIEW, RESTORING, EXPORTED, RESTORED, UNKNOWN, CHECKED, FAILED, CANCELLED, REVOKED }
    data class State(val phase:Phase=Phase.IDLE,val mode:Mode?=null,val prepared:MedicalLibraryGateway.Prepared?=null,
        val receipt:MedicalLibraryTransfer.Committed?=null,val status:MedicalLibraryTransfer.Status?=null,val revoked:Boolean=false)
    private fun operation():UUID?=saved.get<String>(OPERATION)?.let {runCatching {UUID.fromString(it)}.getOrNull()}
    private val mutable=MutableStateFlow(State(if(saved.contains(OPERATION))Phase.UNKNOWN else Phase.IDLE))
    val state=mutable.asStateFlow()
    private val epoch=AtomicLong()
    @Volatile private var closed=false
    private var uri:Uri?=null
    private var restoreSource:Uri?=null
    private var protectionSource:Uri?=null
    private val hidden=gateway.onHidden {
        if(!closed && state.value.phase in setOf(Phase.PASSWORD,Phase.WORKING,Phase.REVIEW,Phase.RESTORING))cancel()
    }
    private val revocation=try {gateway.onRevoked {if(!closed)revoke()}}catch(t:Throwable) {hidden.close();throw t}
    fun choose(mode:Mode):Intent? {
        if(closed || state.value.revoked || state.value.phase in setOf(Phase.PICKING,Phase.PASSWORD,Phase.WORKING,Phase.REVIEW,Phase.RESTORING,Phase.UNKNOWN))return null
        if(operation()!=null) {
            val fact=state.value.status
            if(state.value.phase!=Phase.RESTORED && !(state.value.phase==Phase.CHECKED && (fact==null || fact.committed || fact.phase=="ABORTED")))return null
            saved.remove<String>(OPERATION)
        }
        epoch.incrementAndGet();uri=null
        if(mode==Mode.RESTORE){restoreSource=null;protectionSource=null}
        gateway.cancel()
        mutable.value=State(Phase.PICKING,mode)
        return Intent(if(mode==Mode.EXPORT)Intent.ACTION_CREATE_DOCUMENT else Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE);type=if(mode==Mode.EXPORT)"application/octet-stream" else "*/*"
            if(mode==Mode.EXPORT)putExtra(Intent.EXTRA_TITLE,"medical-backup.pnmedbk2")
        }
    }
    fun picked(result:ActivityResult) {
        if(closed || state.value.revoked || state.value.phase!=Phase.PICKING)return
        val selected=result.data?.data
        if(result.resultCode!=Activity.RESULT_OK || selected?.scheme!="content") {uri=null;restoreSource=null;protectionSource=null;mutable.update {it.copy(phase=Phase.CANCELLED)};return}
        if(state.value.mode==Mode.EXPORT && selected==protectionSource) {
            protectionSource=null;mutable.update {it.copy(phase=Phase.FAILED)};return
        }
        protectionSource=null
        uri=selected;mutable.update {it.copy(phase=Phase.PASSWORD)}
    }
    fun password(password:CharArray) {
        val selected=uri
        if(closed || state.value.revoked || state.value.phase!=Phase.PASSWORD || selected==null) {password.fill('\u0000');return}
        uri=null;val mode=requireNotNull(state.value.mode);val token=epoch.get()
        restoreSource=if(mode==Mode.RESTORE)selected else null
        mutable.update {it.copy(phase=Phase.WORKING)}
        val work=scope.launch {
            try {
                if(mode==Mode.EXPORT)gateway.export(selected,password) else {
                    val result=gateway.prepare(selected,password)
                    if(!closed && !state.value.revoked && token==epoch.get())mutable.update {it.copy(phase=Phase.REVIEW,prepared=result)}
                    return@launch
                }
                if(!closed && token==epoch.get())mutable.update {it.copy(phase=Phase.EXPORTED)}
            }catch(e:CancellationException) {throw e}catch(_:Exception) {
                if(!closed && token==epoch.get()) {restoreSource=null;mutable.update {it.copy(phase=Phase.FAILED)}}
            }finally {password.fill('\u0000')}
        }
        work.invokeOnCompletion {error->
            password.fill('\u0000')
            if(error is CancellationException && !closed && token==epoch.get()) {
                restoreSource=null;protectionSource=null
                mutable.update {if(it.phase==Phase.WORKING)it.copy(phase=Phase.CANCELLED)else it}
            }
        }
    }
    /** Existing pre-restore protection choice: export CURRENT data, then require a fresh file selection. */
    fun prepareSafetyBackup():Boolean {
        if(closed || state.value.revoked || state.value.phase!=Phase.REVIEW)return false
        val source=restoreSource ?: return false
        cancel();protectionSource=source
        return true
    }
    fun confirmRestore() {
        val source=state.value.prepared ?: return
        if(closed || state.value.revoked || state.value.phase!=Phase.REVIEW)return
        restoreSource=null
        saved[OPERATION]=source.operation.toString()
        mutable.update {it.copy(phase=Phase.RESTORING)}
        val work=scope.launch {
            var receipt:MedicalLibraryTransfer.Committed?=null
            try {receipt=gateway.restore()}catch(e:CancellationException) {throw e}catch(_:Exception) {receipt=gateway.committedReceipt?.takeIf {it.operation==source.operation}}
            finally {
                receipt=receipt ?: gateway.committedReceipt?.takeIf {it.operation==source.operation}
                if(!closed && operation()==source.operation)mutable.update {it.copy(phase=if(receipt!=null)Phase.RESTORED else Phase.UNKNOWN,prepared=null,receipt=receipt)}
            }
        }
        work.invokeOnCompletion {
            if(!closed && operation()==source.operation)mutable.update {old->
                if(old.phase!=Phase.RESTORING)old else gateway.committedReceipt?.takeIf {it.operation==source.operation}
                    ?.let {old.copy(phase=Phase.RESTORED,receipt=it,prepared=null)} ?: old.copy(phase=Phase.UNKNOWN,prepared=null)
            }
        }
    }
    /** No archive read, KDF, new keys or restore call. A fresh screen may check the old UUID. */
    fun checkStatus() {
        val op=operation() ?: return
        if(closed || state.value.phase !in setOf(Phase.UNKNOWN,Phase.CHECKED,Phase.REVOKED))return
        mutable.update {it.copy(phase=Phase.WORKING)}
        scope.launch {
            try {
                val fact=gateway.status(op)
                if(!closed)mutable.update {it.copy(phase=Phase.CHECKED,status=fact)}
            }catch(e:CancellationException){throw e}catch(_:Exception){if(!closed)mutable.update {it.copy(phase=Phase.UNKNOWN)}}
        }
    }
    /** Explicit logical abort is allowed only after a read-only check proved a pending journal. */
    fun abortPending() {
        val op=operation() ?: return
        if(closed || state.value.revoked || state.value.phase!=Phase.CHECKED || state.value.status?.phase !in PENDING)return
        mutable.update {it.copy(phase=Phase.WORKING)}
        scope.launch {
            val done=try {gateway.abortPending(op)}catch(e:CancellationException){throw e}catch(_:Exception){false}
            if(done)saved.remove<String>(OPERATION)
            if(!closed)mutable.update {it.copy(phase=if(done)Phase.CANCELLED else Phase.UNKNOWN,status=null)}
        }
    }
    fun cancel() {
        if(closed)return
        val uncertain=state.value.phase==Phase.RESTORING || (operation()!=null && state.value.phase in setOf(Phase.UNKNOWN,Phase.CHECKED))
        epoch.incrementAndGet();uri=null;restoreSource=null;protectionSource=null
        mutable.update {it.copy(phase=if(uncertain)Phase.UNKNOWN else Phase.CANCELLED,prepared=null)}
        gateway.cancel()
    }
    private fun revoke() {
        epoch.incrementAndGet();uri=null;restoreSource=null;protectionSource=null
        mutable.update {State(if(operation()!=null)Phase.UNKNOWN else Phase.REVOKED,revoked=true)}
        gateway.cancel()
    }
    override fun close() {
        closed=true;epoch.incrementAndGet();uri=null;restoreSource=null;protectionSource=null
        mutable.value=State(Phase.REVOKED,revoked=true)
        hidden.close();revocation.close();gateway.close()
    }
    companion object {
        const val OPERATION=net.plainnotes.app.runtime.RuntimeLibrarySession.RESTORE_OPERATION
        val PENDING=setOf("PREPARED","DATABASE_COMMITTED","OBJECTS_COMMITTED","READY")
    }
}
