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
    /** MED-04: the result of saving what the user typed about a file; independent of the link/bin outcome. */
    enum class DetailOutcome { NONE, SAVED, FAILED, CONFLICT, CHECKED }
    data class State(val documents:List<Ops.Document> = emptyList(),val details:Map<UUID,Ops.Detail> = emptyMap(),val detailsFailed:Boolean=false,
        val detailOutcome:DetailOutcome=DetailOutcome.NONE,val loading:Boolean=true,val failed:Boolean=false,
        val selected:UUID?=null,val links:List<Ops.Relationship> = emptyList(),val linksFailed:Boolean=false,val linksLoading:Boolean=false,
        val outcome:Outcome=Outcome.IDLE,val revoked:Boolean=false)
    val files=MedicalAttachmentsController(gateway,scope,io)
    /** MED-05: in-memory first-page pictures; dropped on lock, disguise, a data switch, leaving the page and close. */
    val thumbnails=FileThumbnails(scope,io,{document->gateway.preview(document)})
    private val hidden=gateway.onHidden {thumbnails.clear()}
    private var pendingRelation:Pair<UUID,UUID>?=null
    private var pendingLifecycle:Pair<UUID,UUID>?=null
    private var pendingInfo:Pair<UUID,UUID>?=null
    private var invalidCheckpoint=false
    private val mutable=MutableStateFlow(State())
    val state=mutable.asStateFlow()
    private var closed=false
    private var writing:Job?=null
    private var selection=0L
    private val revoked=gateway.onRevoked {
        thumbnails.clear()
        mutable.value=State(loading=false,revoked=true,outcome=mutable.value.outcome)
    }
    init {
        try {pendingRelation=gateway.relationPending();pendingLifecycle=gateway.lifecyclePending();pendingInfo=gateway.infoPending()}
        catch(_:Exception) {invalidCheckpoint=true}
        if(pendingRelation!=null || pendingLifecycle!=null || pendingInfo!=null || invalidCheckpoint)mutable.update {it.copy(outcome=Outcome.UNKNOWN)}
    }
    fun canWrite()=!closed && !state.value.revoked && !gateway.scopeEnded && state.value.outcome!=Outcome.WORKING &&
        !state.value.loading && !state.value.failed && !invalidCheckpoint && pendingRelation==null && pendingLifecycle==null && pendingInfo==null &&
        files.state.value.import !in setOf(MedicalAttachmentsController.Import.Saving,MedicalAttachmentsController.Import.Unknown,MedicalAttachmentsController.Import.Checking)
    fun refresh() {
        if(closed || state.value.revoked)return
        files.refresh()
        scope.launch {
            try {
                val docs=withContext(io) {gateway.documents()}
                // The file list never waits for, or fails with, the user's own notes about the files.
                val details=try {withContext(io) {gateway.details()}}catch(e:CancellationException) {throw e}catch(_:Exception) {null}
                if(!closed && !state.value.revoked)mutable.update {it.copy(documents=docs,details=details.orEmpty(),detailsFailed=details==null,loading=false,failed=false)}
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
    /** Saves what the user typed about an active file. Never touches the original, its key or its links; a failure only reports and never retries by itself. */
    fun saveDetail(document:Ops.Document,detail:Ops.Detail,then:(()->Unit)?=null) {
        if(!canWrite() || document.state!="ACTIVE" || state.value.detailsFailed) {then?.invoke();return}
        val revision=state.value.details[document.document]?.revision ?: 0L
        mutable.update {it.copy(outcome=Outcome.WORKING,detailOutcome=DetailOutcome.NONE)}
        writing=scope.launch {
            val saved=try {withContext(io) {gateway.saveDetail(document.document,revision,detail.copy(revision=revision))};true}
                catch(e:CancellationException) {mutable.update {it.copy(outcome=Outcome.IDLE)};throw e}catch(_:Exception) {false}
            if(closed || state.value.revoked)return@launch
            // After a failure the stored value may still equal what was asked for (a commit whose acknowledgement was lost): read it, do not guess.
            val actual=runCatching {withContext(io) {gateway.details()}}.getOrNull()?.get(document.document)
            val ok=saved || (actual!=null && actual.category==detail.category && actual.date==detail.date && actual.institution==detail.institution && actual.revision>revision)
            mutable.update {it.copy(outcome=Outcome.IDLE,detailOutcome=if(ok)DetailOutcome.SAVED else DetailOutcome.FAILED)}
            refresh()
            then?.invoke()
        }
    }
    /** Editing a name is a metadata CAS, not an import. A lost response retains only neutral IDs;
     * no title/URI/bytes survive and no failure handler automatically repeats this write. */
    fun saveFileInfo(document:Ops.Document,title:String,detail:Ops.Detail) {
        if(!canWrite() || document.state!="ACTIVE" || state.value.detailsFailed || !Ops.validTitle(title))return
        val revision=state.value.details[document.document]?.revision ?: 0L
        val operation=UUID.randomUUID();pendingInfo=operation to document.document
        files.closeViewer()
        mutable.update {it.copy(outcome=Outcome.WORKING,detailOutcome=DetailOutcome.NONE)}
        val worker=operation to document.document
        check(infoWorkers.putIfAbsent(worker,Unit)==null)
        writing=scope.launch {
            var conflict=false
            val receipt=try {withContext(io) {
                gateway.infoRetain(operation,document.document)
                gateway.saveFileInfo(document.document,document.revision,revision,title,detail.copy(revision=revision))
            }}catch(e:CancellationException) {mutable.update {it.copy(outcome=Outcome.UNKNOWN)};throw e}
            catch(_:Ops.FileInfoConflict) {conflict=true;null}catch(_:Exception){null}
            if(receipt!=null || conflict) {
                val cleared=runCatching {withContext(io) {gateway.infoResolved(operation,document.document)}}.isSuccess
                if(cleared)pendingInfo=null
                if(!closed)mutable.update {before->
                    before.copy(outcome=if(cleared)Outcome.IDLE else Outcome.UNKNOWN,
                        detailOutcome=if(receipt!=null)DetailOutcome.SAVED else if(cleared)DetailOutcome.CONFLICT else DetailOutcome.NONE,
                        documents=if(receipt!=null && !before.revoked)before.documents.map {if(it.document==receipt.document)it.copy(title=receipt.title,revision=receipt.documentRevision)else it} else before.documents,
                        details=if(receipt!=null && !before.revoked)before.details+(receipt.document to receipt.detail)else before.details)
                }
                if(!closed && !state.value.revoked)refresh()
            }else if(!closed)mutable.update {it.copy(outcome=Outcome.UNKNOWN)}
        }.also {job->job.invokeOnCompletion {infoWorkers.remove(worker)}}
    }
    fun dismissDetailOutcome() {mutable.update {it.copy(detailOutcome=DetailOutcome.NONE)}}
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
        if(closed || state.value.revoked || writing?.isActive==true || invalidCheckpoint || pendingInfo?.let(infoWorkers::containsKey)==true)return
        mutable.update {it.copy(outcome=Outcome.WORKING)}
        writing=scope.launch {
            try {
                var committed=false
                var infoChecked=false
                pendingInfo?.let {(operation,document)->
                    // No operation receipt exists for file information. Show only authenticated
                    // CURRENT values, never claim which historical writer committed them.
                    val docs=withContext(io) {gateway.documents()}
                    val details=withContext(io) {gateway.details()}
                    check(docs.any {it.document==document})
                    if(closed || state.value.revoked || gateway.scopeEnded)return@launch
                    withContext(io) {gateway.infoResolved(operation,document)}
                    pendingInfo=null;infoChecked=true
                    mutable.update {it.copy(documents=docs,details=details,detailsFailed=false)}
                }
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
                    mutable.update {it.copy(outcome=if(committed)Outcome.COMMITTED else Outcome.CHECKED,detailOutcome=if(infoChecked)DetailOutcome.CHECKED else it.detailOutcome)}
                    refresh();select(state.value.selected)
                }
            }catch(e:CancellationException){throw e}catch(_:Exception){if(!closed && !state.value.revoked)mutable.update {it.copy(outcome=Outcome.UNKNOWN)}}
        }
    }
    fun dismissOutcome() {if(canWrite())mutable.update {it.copy(outcome=Outcome.IDLE)}}
    private companion object {
        // A replacement page must not clear a checkpoint while this process still has its writer.
        // New processes start empty: a dead process cannot leave a live SQL worker behind.
        val infoWorkers=java.util.concurrent.ConcurrentHashMap<Pair<UUID,UUID>,Unit>()
    }
    override fun close() {
        if(closed)return
        files.close() // remove pixels before releasing runtime resources
        thumbnails.close();hidden.close()
        closed=true;selection++;mutable.value=State(loading=false,revoked=true)
        revoked.close()
    }
}

internal fun Ops.Document.fileKind()=when(mime) {"application/pdf"->MedicalAttachmentsController.FileKind.PDF;"image/jpeg","image/png"->MedicalAttachmentsController.FileKind.IMAGE;else->MedicalAttachmentsController.FileKind.OTHER}
