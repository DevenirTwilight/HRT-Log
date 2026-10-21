package net.plainnotes.app.data

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import net.plainnotes.app.data.NonBlockingWritePrototype.*

/** Synthetic coroutine contracts, not SQLCipher commit or process-death acceptance. */
class NonBlockingWritePrototypeTest {
    private val key=Key("SYNTHETIC dataset","SYNTHETIC epoch","SYNTHETIC operation","SYNTHETIC request")
    private fun runCase(block:suspend CoroutineScope.(CoroutineScope,CoroutineDispatcher)->Unit)=runBlocking {
        val owner=CoroutineScope(SupervisorJob())
        val io=Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        try {withTimeout(10000){block(owner,io)}}finally{owner.cancel();io.close()}
    }
    @Test fun observerCanLeaveWhileOneWriteContinuesOnIoAndSuccessWaitsForReturn()=runCase {owner,io->
        val entered=CompletableDeferred<Unit>();val commit=CompletableDeferred<Unit>();val calls=AtomicInteger()
        val callerThread=Thread.currentThread().id
        val task=NonBlockingWritePrototype(owner,io,key,action={
            calls.incrementAndGet();assertNotEquals(callerThread,Thread.currentThread().id)
            entered.complete(Unit);commit.await();"SYNTHETIC committed receipt"
        },resolver={Resolution.Unknown})
        val observer=launch {task.state.collect{}}
        task.start();task.start();entered.await()
        assertEquals(Status.PENDING,task.state.value.status)
        assertFalse(task.cancelBeforeStart())
        assertFalse(task.reconcile(key))
        observer.cancelAndJoin()
        assertTrue(owner.isActive);assertEquals(1,calls.get())
        commit.complete(Unit)
        val done=task.state.first{it.status==Status.SUCCESS}
        assertEquals("SYNTHETIC committed receipt",done.result)
        assertTrue(done.elapsedNanos>=0)
    }
    @Test fun confirmedCancelBeforeSubmissionNeverCallsAction()=runCase {owner,io->
        val calls=AtomicInteger()
        val task=NonBlockingWritePrototype(owner,io,key,{calls.incrementAndGet()}, {Resolution.Unknown})
        assertTrue(task.cancelBeforeStart());task.start()
        assertEquals(Status.FAILED,task.state.value.status)
        assertEquals(Reason.CANCELLED_BEFORE_START,task.state.value.reason)
        assertEquals(0,calls.get())
    }
    @Test fun exceptionCannotMeanRollbackAndUnknownDoesNotRetry()=runCase {owner,io->
        val calls=AtomicInteger();val resolved=CompletableDeferred<Unit>()
        val task=NonBlockingWritePrototype<String>(owner,io,key,{
            calls.incrementAndGet();throw IllegalStateException("SYNTHETIC delivery fault after possible commit")
        },{resolved.complete(Unit);Resolution.Unknown})
        task.start();task.state.first{it.phase==Phase.RECONCILING}
        while(!task.reconcile(key))yield()
        resolved.await();task.state.first{it.phase==Phase.MANUAL_CHECK}
        assertEquals(Status.PENDING,task.state.value.status)
        task.start();assertEquals(1,calls.get())
    }
    @Test fun matchingCommitObservationCanResolveAmbiguousDelivery()=runCase {owner,io->
        val task=NonBlockingWritePrototype<String>(owner,io,key,{throw IllegalStateException("SYNTHETIC lost delivery")},
            {Resolution.Committed("SYNTHETIC existing committed result")})
        task.start();task.state.first{it.phase==Phase.RECONCILING}
        while(!task.reconcile(key))yield()
        assertEquals("SYNTHETIC existing committed result",task.state.first{it.status==Status.SUCCESS}.result)
    }
    @Test fun failedRequiresExplicitNoCommitObservation()=runCase {owner,io->
        val task=NonBlockingWritePrototype<String>(owner,io,key,{throw IllegalStateException("SYNTHETIC failure")},
            {Resolution.NotCommitted})
        task.start();task.state.first{it.phase==Phase.RECONCILING}
        while(!task.reconcile(key))yield()
        assertEquals(Reason.NOT_COMMITTED,task.state.first{it.status==Status.FAILED}.reason)
    }
    @Test fun otherEpochOperationAndPayloadCannotResolveThisTask()=runCase {owner,io->
        val task=NonBlockingWritePrototype<String>(owner,io,key,{throw IllegalStateException("SYNTHETIC failure")},
            {Resolution.Committed("SYNTHETIC receipt")})
        task.start();task.state.first{it.phase==Phase.RECONCILING}
        for(other in listOf(key.copy(dataset="OTHER"),key.copy(epoch="OTHER"),key.copy(operationUuid="OTHER"),key.copy(requestSha="OTHER"))) {
            try {task.reconcile(other);fail("Other request resolved this operation")}
            catch(_:IllegalArgumentException){}
        }
        assertEquals(Status.PENDING,task.state.value.status)
    }
    @Test fun ownerCancellationAfterEntryCannotClaimNoCommit()=runCase {owner,io->
        val entered=CompletableDeferred<Unit>()
        val task=NonBlockingWritePrototype<String>(owner,io,key,{entered.complete(Unit);awaitCancellation()}, {Resolution.Unknown})
        task.start();entered.await();owner.cancel()
        task.state.first{it.phase==Phase.RECONCILING}
        assertEquals(Status.PENDING,task.state.value.status)
        assertNull(task.state.value.result)
    }
}
