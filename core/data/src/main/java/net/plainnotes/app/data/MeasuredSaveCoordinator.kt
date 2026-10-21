package net.plainnotes.app.data

import java.lang.ref.WeakReference
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.plainnotes.app.domain.write.*

/** Opaque process-local original confirmation. Never persisted, logged or refreshed on retry. */
class PreparedActualSave internal constructor(internal val owner:Any,internal val value:PreparedMeasuredCreate) {
    val operationUuid:String get()=value.operationUuid
    internal fun matches(other:PreparedActualSave):Boolean {
        val a=value;val b=other.value
        return owner===other.owner && a.ticket==b.ticket && a.operationUuid==b.operationUuid && a.draft==b.draft &&
            a.created==b.created && a.configSha256==b.configSha256 && a.referenceContextEventUuid==b.referenceContextEventUuid
    }
}
/** Historical commitment; it does not assert that the record is still current or undeleted. */
data class ConfirmedActualSave(val operationUuid:String,val recordUuid:String,val recordRevision:Int)

class ActualSaveTask internal constructor(internal val prepared:PreparedActualSave,internal val lease:MeasuredSaveGate.Lease) {
    internal val mutable=MutableStateFlow(WriteProgressSummary(prepared.operationUuid,WriteStatus.PENDING,WritePhase.QUEUED))
    val progress:StateFlow<WriteProgressSummary> = mutable.asStateFlow()
    internal val mutableResult=MutableStateFlow<ConfirmedActualSave?>(null)
    val confirmed:StateFlow<ConfirmedActualSave?> = mutableResult.asStateFlow()
    internal var writer:Job?=null
    internal var entered=false
    internal var resolving=false
}

/** Process-owned standalone actual-use saves. No UI caller/entry is enabled by this service.
 * No persistent queue, automatic replay, or assertion that process death means rollback. */
@Singleton
class MeasuredSaveCoordinator internal constructor(
    repository:NotesRepository,
    process:CoroutineScope,
    private val dispatcher:CoroutineDispatcher,
    private val writeOverride:(suspend(PreparedMeasuredCreate)->RecordedMeasuredUse)?=null,
    private val observeOverride:(suspend(PreparedMeasuredCreate)->MeasuredCreateObservation)?=null,
) {
    @Inject constructor(repository:NotesRepository):this(repository,CoroutineScope(SupervisorJob()+Dispatchers.IO),Dispatchers.IO)
    private val repo=repository.pinnedTo(Space.PRIMARY)
    private val gate=repo.measuredSaveGate
    // Deliberately inherit only the process Job, never a caller's Room transaction/context.
    private val scope=CoroutineScope(SupervisorJob(process.coroutineContext[Job])+dispatcher)
    private val identity=Any()
    private val lock=Any()
    private val tasks=HashMap<String,WeakReference<ActualSaveTask>>()
    private var active:ActualSaveTask?=null
    private val mutableLatest=MutableStateFlow<ActualSaveTask?>(null)
    /** One process observation, including its terminal result. Never saved to disk or a Bundle. */
    val latestTask:StateFlow<ActualSaveTask?> = mutableLatest.asStateFlow()

    suspend fun prepare(scope:ReferenceScope,medication:Long,operationUuid:String,draft:MeasuredActualUseDraft,created:Instant):PreparedActualSave =
        PreparedActualSave(identity,repo.prepareMeasuredActualUse(scope,medication,operationUuid,draft,created))

    /** Accept immediately or reject busy. The process scope, not its caller, owns the writer. */
    fun start(prepared:PreparedActualSave):ActualSaveTask=synchronized(lock) {
        require(prepared.owner===identity){"Confirmation belongs to another save owner"}
        tasks.entries.removeAll{it.value.get()==null}
        tasks[prepared.operationUuid]?.get()?.let {
            if(!it.prepared.matches(prepared))throw ReferenceFailure(ReferenceError.OPERATION_CONFLICT)
            return@synchronized it
        }
        val lease=gate.reserve(Space.PRIMARY)
        val task=ActualSaveTask(prepared,lease)
        active=task;tasks[prepared.operationUuid]=WeakReference(task)
        val writer=scope.launch(dispatcher+MeasuredSaveGate.Permit(gate,lease),start=CoroutineStart.LAZY) {
            synchronized(lock) {
                if(task.mutable.value.status!=WriteStatus.PENDING)return@launch
                task.entered=true;publish(task,WritePhase.WRITING)
            }
            try {
                val expected=prepared.value
                val receipt=writeOverride?.invoke(expected)?.receipt ?: repo.submitPreparedMeasuredActualUse(expected)
                require(receipt.operationUuid==expected.operationUuid)
                synchronized(lock){success(task,receipt)}
            }catch(e:CancellationException){synchronized(lock){uncertain(task)};throw e}
            catch(_:Exception){synchronized(lock){uncertain(task)}}
        }
        task.writer=writer
        writer.invokeOnCompletion { cause ->
            val needsReconcile=synchronized(lock) {
                if(task.mutable.value.status!=WriteStatus.PENDING)false
                else if(!task.entered) {finishUnstarted(task);false}
                else {if(cause!=null)uncertain(task);true}
            }
            // Completion precedes any independent resolver transaction, including lost delivery.
            if(needsReconcile)reconcile(task)
        }
        mutableLatest.value=task
        writer.start();task
    }
    private fun publish(task:ActualSaveTask,phase:WritePhase) {
        task.mutable.value=WriteProgressSummary(task.prepared.operationUuid,WriteStatus.PENDING,phase)
    }
    private fun uncertain(task:ActualSaveTask) {
        if(task.mutable.value.status==WriteStatus.PENDING)publish(task,WritePhase.RECONCILING)
    }
    private fun success(task:ActualSaveTask,receipt:MeasuredConsumptionReceipt) {
        check(active===task)
        require(receipt.operationUuid==task.prepared.operationUuid && receipt.recordRevision==1)
        gate.release(task.lease);active=null
        task.mutableResult.value=ConfirmedActualSave(receipt.operationUuid,receipt.recordUuid,receipt.recordRevision)
        // Root Repository return/independent observation, never a checkpoint, establishes this.
        task.mutable.value=WriteProgressSummary(task.prepared.operationUuid,WriteStatus.SUCCESS,WritePhase.TERMINAL)
    }
    private fun finishUnstarted(task:ActualSaveTask) {
        check(!task.entered && active===task)
        gate.release(task.lease);active=null
        task.mutable.value=WriteProgressSummary(task.prepared.operationUuid,WriteStatus.FAILED,WritePhase.TERMINAL)
    }
    fun cancelBeforeStart(task:ActualSaveTask):Boolean=synchronized(lock) {
        require(task.prepared.owner===identity)
        if(active!==task || task.entered || task.mutable.value.status!=WriteStatus.PENDING)return@synchronized false
        finishUnstarted(task);task.writer?.cancel();true
    }
    /** Read-only confirmation after writer exit. NotObserved and every error remain unresolved;
     * neither can permit a retry or release the conflicting-write reservation. */
    fun reconcile(task:ActualSaveTask):Boolean=synchronized(lock) {
        require(task.prepared.owner===identity)
        if(active!==task || task.mutable.value.status!=WriteStatus.PENDING || task.writer?.isCompleted!=true || task.resolving)return@synchronized false
        task.resolving=true;publish(task,WritePhase.RECONCILING)
        val resolver=scope.launch(dispatcher,start=CoroutineStart.LAZY) {
            try {
                val result=observeOverride?.invoke(task.prepared.value) ?: repo.observeMeasuredActualUse(task.prepared.value)
                synchronized(lock) {
                    when(result) {
                        is MeasuredCreateObservation.Committed->success(task,result.receipt)
                        MeasuredCreateObservation.NotObserved->publish(task,WritePhase.MANUAL_CHECK)
                    }
                }
            }catch(e:CancellationException){throw e}
            catch(_:Exception){synchronized(lock){publish(task,WritePhase.MANUAL_CHECK)}}
        }
        resolver.invokeOnCompletion { cause ->synchronized(lock) {
            task.resolving=false
            if(cause!=null && task.mutable.value.status==WriteStatus.PENDING)publish(task,WritePhase.MANUAL_CHECK)
        }}
        resolver.start();true
    }
}
