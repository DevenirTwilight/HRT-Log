package net.plainnotes.app.data

import net.plainnotes.app.domain.write.WriteStatus as Status
import net.plainnotes.app.domain.write.WritePhase as Phase

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** TEST_ONLY orchestration prototype. The action must return after the real Repository commit.
 * The resolver must independently check the original operation in its fixed database scope.
 * Neither this class nor a request fingerprint proves database validity. No runtime caller. */
internal class NonBlockingWritePrototype<T>(
    private val owner:CoroutineScope,
    private val io:CoroutineDispatcher,
    val key:Key,
    private val action:suspend()->T,
    private val resolver:suspend()->Resolution<T>,
    private val clock:()->Long=System::nanoTime,
) {
    data class Key(val dataset:String,val epoch:String,val operationUuid:String,val requestSha:String)
    enum class Reason { CANCELLED_BEFORE_START, OUTCOME_UNCERTAIN, NOT_COMMITTED, NEEDS_MANUAL_CHECK }
    data class Progress<T>(val status:Status,val phase:Phase,val result:T?=null,val reason:Reason?=null,val elapsedNanos:Long=0)
    sealed interface Resolution<out T> {
        data class Committed<T>(val result:T):Resolution<T>
        data object NotCommitted:Resolution<Nothing>
        data object Unknown:Resolution<Nothing>
    }
    private val lock=Any()
    private val created=clock()
    private val mutable=MutableStateFlow(Progress<T>(Status.PENDING,Phase.QUEUED))
    val state=mutable.asStateFlow()
    private var submitted=false
    private var entered=false
    private var writer:Job?=null
    private var resolving=false
    private fun publish(status:Status,phase:Phase,result:T?=null,reason:Reason?=null) {
        mutable.value=Progress(status,phase,result,reason,clock()-created)
    }
    private fun uncertain()=synchronized(lock) {
        if(mutable.value.status==Status.PENDING)publish(Status.PENDING,Phase.RECONCILING,reason=Reason.OUTCOME_UNCERTAIN)
    }
    fun start() {
        val job=synchronized(lock) {
            if(submitted || mutable.value.status!=Status.PENDING)return
            submitted=true
            owner.launch(io,start=CoroutineStart.LAZY) {
                synchronized(lock) {
                    if(mutable.value.status!=Status.PENDING)return@launch
                    entered=true;publish(Status.PENDING,Phase.WRITING)
                }
                try {
                    val committed=action()
                    synchronized(lock){publish(Status.SUCCESS,Phase.TERMINAL,committed)}
                }catch(e:CancellationException){uncertain();throw e}
                catch(_:Exception){uncertain()}
            }.also{writer=it}
        }
        job.invokeOnCompletion { cause->
            synchronized(lock) {
                if(cause!=null && mutable.value.status==Status.PENDING) {
                    if(entered)uncertain()
                    else publish(Status.FAILED,Phase.TERMINAL,reason=Reason.CANCELLED_BEFORE_START)
                }
            }
        }
        job.start()
    }
    /** Navigation/observer cancellation never calls this. After entry there is no fake undo. */
    fun cancelBeforeStart():Boolean {
        val job=synchronized(lock) {
            if(entered || mutable.value.status!=Status.PENDING || mutable.value.phase!=Phase.QUEUED)return false
            publish(Status.FAILED,Phase.TERMINAL,reason=Reason.CANCELLED_BEFORE_START)
            writer
        }
        job?.cancel();return true
    }
    /** No automatic retry or second writer; an unresolved outcome keeps the operation pending. */
    fun reconcile(expected:Key):Boolean {
        require(expected==key){"Operation scope or request changed"}
        val job=synchronized(lock) {
            val current=mutable.value
            if(current.status!=Status.PENDING || current.phase !in setOf(Phase.RECONCILING,Phase.MANUAL_CHECK) ||
                writer?.isCompleted!=true || resolving)return false
            resolving=true
            owner.launch(io,start=CoroutineStart.LAZY) {
                try {
                    val observation=resolver()
                    synchronized(lock) {
                        when(observation) {
                            is Resolution.Committed->publish(Status.SUCCESS,Phase.TERMINAL,observation.result)
                            Resolution.NotCommitted->publish(Status.FAILED,Phase.TERMINAL,reason=Reason.NOT_COMMITTED)
                            Resolution.Unknown->publish(Status.PENDING,Phase.MANUAL_CHECK,reason=Reason.NEEDS_MANUAL_CHECK)
                        }
                    }
                }catch(e:CancellationException){throw e}
                catch(_:Exception){synchronized(lock){publish(Status.PENDING,Phase.MANUAL_CHECK,reason=Reason.NEEDS_MANUAL_CHECK)}}
            }
        }
        job.invokeOnCompletion { synchronized(lock){resolving=false} }
        job.start();return true
    }
}
