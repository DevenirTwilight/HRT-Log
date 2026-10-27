package net.plainnotes.app.data

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class RevocableArchiveOutputTest {
    @Test fun revocationDoesNotRunBlockingCloseOnCallerAndWorkerObservesCloseFailure() {
        val entered=CountDownLatch(1);val release=CountDownLatch(1)
        val thread=AtomicReference<Thread>()
        val owned=RevocableArchiveOutput(object:OutputStream() {
            override fun write(value:Int) {}
            override fun close() {thread.set(Thread.currentThread());entered.countDown();check(release.await(5,TimeUnit.SECONDS));throw IOException("synthetic close error")}
        })
        try {
            owned.revoke()
            assertTrue(entered.await(2,TimeUnit.SECONDS))
            assertNotSame(Thread.currentThread(),thread.get())
            try {owned.write(1);fail("write accepted after revocation")}catch(_:IllegalStateException){}
        }finally {release.countDown()}
        try {owned.close();fail("close failure was hidden")}catch(e:IOException) {assertTrue(e.cause is IOException)}
    }
}
