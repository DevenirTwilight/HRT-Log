package net.plainnotes.app.data

import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.Semaphore
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Bounded external provider cleanup. Revocation schedules; normal IO completion awaits errors.
 * Four outstanding resources bound both retained descriptors and queued closes. No caller-runs.
 */
internal class RevocableProviderClose(private val resource:AutoCloseable) {
    private val closing=AtomicBoolean(false)
    private val finished=CompletableFuture<Unit>()
    init {check(slots.tryAcquire()) {"Provider cleanup busy"}}
    fun checkOpen() {check(!closing.get()) {"Provider stream closed"}}
    fun revoke() {
        if(closing.compareAndSet(false,true))closer.execute {
            try {resource.close();finished.complete(Unit)}
            catch(t:Throwable) {finished.completeExceptionally(t)}
            finally {slots.release()}
        }
    }
    fun await() {
        revoke()
        try {finished.get()}
        catch(e:ExecutionException) {throw IOException("Provider close failed",e.cause)}
        catch(e:InterruptedException) {Thread.currentThread().interrupt();throw IOException("Provider close interrupted",e)}
    }
    private companion object {
        val slots=Semaphore(4)
        val closer=ThreadPoolExecutor(2,2,0,TimeUnit.MILLISECONDS,ArrayBlockingQueue<Runnable>(4),
            {r->Thread(r,"attachment-provider-close").apply {isDaemon=true}})
    }
}

/** PNMEDBK2 target ownership. No external close runs on the revocation caller. */
internal class RevocableArchiveOutput(private val target:OutputStream):OutputStream() {
    private val cleanup=RevocableProviderClose(target)
    override fun write(value:Int) {cleanup.checkOpen();target.write(value)}
    override fun write(bytes:ByteArray,offset:Int,length:Int) {cleanup.checkOpen();target.write(bytes,offset,length)}
    override fun flush() {cleanup.checkOpen();target.flush()}
    fun revoke()=cleanup.revoke()
    override fun close()=cleanup.await()
}
