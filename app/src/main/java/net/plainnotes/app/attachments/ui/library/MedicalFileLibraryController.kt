package net.plainnotes.app.attachments.ui.library

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import net.plainnotes.app.attachments.ui.MedicalAttachmentsController
import net.plainnotes.app.attachments.ui.MedicalFileGateway
import net.plainnotes.app.data.MedicalAttachmentOperations as Ops
import java.util.UUID

/** A thin product projection over the one runtime gateway. No database, crypto or persisted grant here. */
internal class MedicalFileLibraryController(private val gateway:MedicalFileGateway,private val scope:CoroutineScope,
    private val io:CoroutineDispatcher=Dispatchers.IO):AutoCloseable {
    enum class Outcome { IDLE, WORKING, COMMITTED, LIST_STALE, NOT_SAVED, UNKNOWN, CHECKED }
    data class State(val documents:List<Ops.Document> = emptyList(),val loading:Boolean=true,val failed:Boolean=false,
        val selected:UUID?=null,val links:List<Ops.Relationship> = emptyList(),val linksFailed:Boolean=false,val linksLoading:Boolean=false,
        val outcome:Outcome=Outcome.IDLE,val revoked:Boolean=false)
    val files=MedicalAttachmentsController(gateway,scope,io)
    private var pendingRelation:Pair<UUID,UUID>?=null
    private var pendingLifecycle:Pair<UUID,UUID>?=null
    private var invalidCheckpoint=false
    private val mutable=MutableStateFlow(State())
    val state=mutable.asStateFlow()
    private var closed=false
    private var writing:Job?=null
    private var selection=0L
    private val revoked=gateway.onRevoked {
        mutable.value=State(loading=false,revoked=true,outcome=mutable.value.outcome)
    }
    init {
        try {pendingRelation=gateway.relationPending();pendingLifecycle=gateway.lifecyclePending()}
        catch(_:Exception) {invalidCheckpoint=true}
        if(pendingRelation!=null || pendingLifecycle!=null || invalidCheckpoint)mutable.update {it.copy(outcome=Outcome.UNKNOWN)}
    }
    fun canWrite()=!closed && !state.value.revoked && !gateway.scopeEnded && state.value.outcome!=Outcome.WORKING &&
        !state.value.loading && !state.value.failed && !invalidCheckpoint && pendingRelation==null && pendingLifecycle==null &&
        files.state.value.import !in setOf(MedicalAttachmentsController.Import.Saving,MedicalAttachmentsController.Import.Unknown,MedicalAttachmentsController.Import.Checking)
    fun refresh() {
        if(closed || state.value.revoked)return
        files.refresh()
        scope.launch {
            try {
                val docs=withContext(io) {gateway.documents()}
                if(!closed && !state.value.revoked)mutable.update {it.copy(documents=docs,loading=false,failed=false)}
            }catch(e:CancellationException) {throw e}
            catch(_:Exception) {if(!closed && !state.value.revoked)mutable.update {it.copy(loading=false,failed=true,outcome=if(it.outcome==Outcome.COMMITTED)Outcome.LIST_STALE else it.outcome)}}
        }
    }
    fun select(document:UUID?) {
        if(closed || state.value.revoked)return
        files.closeViewer();val token=++selection
        mutable.update {it.copy(selected=document,links=emptyList(),linksFailed=false,linksLoading=document!=null)}
        if(document==null || state.value.documents.firstOrNull {it.document==document}?.state!="ACTIVE"){mutable.update {it.copy(linksLoading=false)};return}
        scope.launch {
            val links=try {withContext(io) {gateway.relationships(document)}}catch(e:CancellationException){throw e}catch(_:Exception){null}
            if(!closed && !state.value.revoked && token==selection)mutable.update {it.copy(links=links.orEmpty(),linksFailed=links==null,linksLoading=false)}
        }
    }
    fun preview(document:Ops.Document) {
        if(closed || state.value.revoked || document.state!="ACTIVE")return
        files.open(MedicalAttachmentsController.Item(document.document,document.title,document.fileKind(),true))
    }
    /** Resolve the real target before showing its confirmation. This creates no file/link. */
    suspend fun resolve(target:Ops.Target):Ops.Target {
        check(canWrite());return withContext(io) {gateway.resolveTarget(target)}
    }
    fun relate(document:Ops.Document,target:Ops.Target,unlink:Ops.Relationship?=null) {
        if(!canWrite() || document.state!="ACTIVE")return
        val operation=UUID.randomUUID();pendingRelation=operation to document.document
        mutable.update {it.copy(outcome=Outcome.WORKING)}
        writing=scope.launch {
            val result=try {withContext(io) {
                gateway.relationRetain(operation,document.document)
                gateway.associate(operation,target,document.document,document.revision,unlink)
            }}catch(e:CancellationException) {mutable.update {it.copy(outcome=Outcome.UNKNOWN)};throw e}catch(_:Exception){null}
            if(closed || state.value.revoked)return@launch
            if(result?.phase==Ops.Phase.COMMITTED || result?.phase==Ops.Phase.ABORTED) {
                val cleared=runCatching {withContext(io) {gateway.relationResolved(operation,document.document)}}.isSuccess
                if(cleared)pendingRelation=null
                mutable.update {it.copy(outcome=if(!cleared)Outcome.UNKNOWN else if(result.phase==Ops.Phase.COMMITTED)Outcome.COMMITTED else Outcome.NOT_SAVED)}
                refresh();select(document.document)
            }else mutable.update {it.copy(outcome=Outcome.UNKNOWN)}
        }
    }
    /** Existing CAS-based soft lifecycle, retaining every original, key, quota and quarantined link. */
    fun recycle(document:Ops.Document,restore:Boolean) {
        if(!canWrite() || restore!=(document.state=="TRASHED"))return
        files.closeViewer()
        val operation=UUID.randomUUID();pendingLifecycle=operation to document.document
        mutable.update {it.copy(outcome=Outcome.WORKING)}
        writing=scope.launch {
            val acknowledged=try {withContext(io) {
                gateway.lifecycleRetain(operation,document.document)
                if(restore)gateway.restore(document.document,document.revision) else gateway.trash(document.document,document.revision)
            };true}catch(e:CancellationException){mutable.update {it.copy(outcome=Outcome.UNKNOWN)};throw e}catch(_:Exception){false}
            if(closed || state.value.revoked)return@launch
            if(acknowledged) {
                val cleared=runCatching {withContext(io) {gateway.lifecycleResolved(operation,document.document)}}.isSuccess
                if(cleared)pendingLifecycle=null
                mutable.update {it.copy(outcome=if(cleared)Outcome.COMMITTED else Outcome.UNKNOWN)}
                refresh();select(null)
            }else mutable.update {it.copy(outcome=Outcome.UNKNOWN)}
        }
    }
    /** Never invokes associate/trash/restore. Absence is decisive only after this owner's worker has ended. */
    fun checkStatus() {
        if(closed || state.value.revoked || writing?.isActive==true || invalidCheckpoint)return
        mutable.update {it.copy(outcome=Outcome.WORKING)}
        writing=scope.launch {
            try {
                var committed=false
                pendingRelation?.let {(operation,document)->
                    val result=withContext(io) {gateway.relationStatus(operation,document)}
                    if(result?.phase==Ops.Phase.PENDING) {mutable.update {it.copy(outcome=Outcome.UNKNOWN)};return@launch}
                    withContext(io) {gateway.relationResolved(operation,document)}
                    pendingRelation=null;committed=result?.phase==Ops.Phase.COMMITTED
                }
                pendingLifecycle?.let {(operation,document)->
                    // This checks present file state, not attribution of an earlier interrupted CAS.
                    val docs=withContext(io) {gateway.documents()}
                    check(docs.any {it.document==document})
                    withContext(io) {gateway.lifecycleResolved(operation,document)}
                    pendingLifecycle=null
                }
                if(!closed && !state.value.revoked) {
                    mutable.update {it.copy(outcome=if(committed)Outcome.COMMITTED else Outcome.CHECKED)}
                    refresh();select(state.value.selected)
                }
            }catch(e:CancellationException){throw e}catch(_:Exception){if(!closed && !state.value.revoked)mutable.update {it.copy(outcome=Outcome.UNKNOWN)}}
        }
    }
    fun dismissOutcome() {if(canWrite())mutable.update {it.copy(outcome=Outcome.IDLE)}}
    override fun close() {
        if(closed)return
        files.close() // remove pixels before releasing runtime resources
        closed=true;selection++;mutable.value=State(loading=false,revoked=true)
        revoked.close()
    }
}

internal fun Ops.Document.fileKind()=when(mime) {"application/pdf"->MedicalAttachmentsController.FileKind.PDF;"image/jpeg","image/png"->MedicalAttachmentsController.FileKind.IMAGE;else->MedicalAttachmentsController.FileKind.OTHER}
