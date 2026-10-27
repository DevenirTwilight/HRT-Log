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

/** PNMEDBK2 target ownership only. Revocation never calls external close on its caller.
 * Normal worker completion waits for close and propagates failures before any receipt.
 * Four outstanding closes bound both threads and queued retained descriptors. No caller-runs.
 * This cannot promise to terminate an arbitrary OS/provider primitive that ignores close.
 */
internal class RevocableArchiveOutput(private val target:OutputStream):OutputStream() {
    private val closing=AtomicBoolean(false)
    private val finished=CompletableFuture<Unit>()
    init {check(slots.tryAcquire()) {"Archive output cleanup busy"}}
    override fun write(value:Int) {check(!closing.get());target.write(value)}
    override fun write(bytes:ByteArray,offset:Int,length:Int) {check(!closing.get());target.write(bytes,offset,length)}
    override fun flush() {check(!closing.get());target.flush()}
    fun revoke() {
        if(closing.compareAndSet(false,true))closer.execute {
            try {target.close();finished.complete(Unit)}
            catch(t:Throwable) {finished.completeExceptionally(t)}
            finally {slots.release()}
        }
    }
    override fun close() {
        revoke()
        try {finished.get()}
        catch(e:ExecutionException) {throw IOException("Archive output close failed",e.cause)}
        catch(e:InterruptedException) {Thread.currentThread().interrupt();throw IOException("Archive output close interrupted",e)}
    }
    private companion object {
        val slots=Semaphore(4)
        val closer=ThreadPoolExecutor(2,2,0,TimeUnit.MILLISECONDS,ArrayBlockingQueue<Runnable>(4),
            {r->Thread(r,"medical-archive-close").apply {isDaemon=true}})
    }
}
