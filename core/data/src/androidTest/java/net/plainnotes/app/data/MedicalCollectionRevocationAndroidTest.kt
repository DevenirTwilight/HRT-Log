package net.plainnotes.app.data

import android.os.Looper
import android.os.SystemClock
import android.provider.DocumentsContract
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import net.plainnotes.app.domain.AttachmentInputStream
import net.plainnotes.app.domain.AttachmentSession
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import org.junit.Assume.assumeTrue
import java.io.File
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Genuine separate-process DocumentsProvider pipes, production exporter, real SQLCipher root. */
class MedicalCollectionRevocationAndroidTest {
    @Before fun dedicatedSyntheticUidOnly() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("securityPipes")=="1")
    }
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val app get()=instrumentation.targetContext
    private val uri get()=DocumentsContract.buildDocumentUri("net.plainnotes.app.data.test.security-provider","stall")
    private fun releaseProvider() {app.contentResolver.call(uri,"release",null,null)}
    private fun waitForNativeIo(worker:Thread) {
        val until=SystemClock.elapsedRealtime()+15000
        while(SystemClock.elapsedRealtime()<until) {
            if(worker.stackTrace.any {it.className=="libcore.io.IoBridge" || it.className=="libcore.io.Linux"})return
            check(worker.isAlive) {"Worker ended before entering native pipe IO"}
            Thread.sleep(10)
        }
        error("Native blocked pipe boundary not observed")
    }
    @Test fun blockedExportRevocationExitsWorkerReleasesRootAndNeverAcknowledgesSuccess() {
        LibraryRestoreAndroidTest.RootFixture(app).use {f->
            f.root.runInTransaction {repeat(80) {n->f.raw.execSQL(
                "INSERT INTO appointment(id,type,at_utc,at_zone,note,remind_minutes_before) VALUES(?,?,?,?,?,?)",
                arrayOf(n+10,"SYNTHETIC",1000,"UTC","SYNTHETIC "+"x".repeat(4096),0))}}
            val original=f.facts()
            val physical=AttachmentProviderStreams.output(app,uri,f.session.issue())
            val closeOnMain=AtomicBoolean(false);val entered=CountDownLatch(1)
            val target=object:OutputStream() {
                override fun write(value:Int)=physical.write(value)
                override fun write(b:ByteArray,o:Int,n:Int) {if(n>4096)entered.countDown();physical.write(b,o,n)}
                override fun close() {closeOnMain.set(Looper.myLooper()==Looper.getMainLooper());physical.close()}
            }
            val success=AtomicBoolean(false);val failure=AtomicReference<Throwable?>()
            val worker=Thread({try {
                LibraryCollectionCapture(f.access).write(f.scope,f.session.issue(),UUID.randomUUID(),"SYNTHETIC pipe export".toCharArray(),target)
                success.set(true)
            }catch(t:Throwable) {failure.set(t)}},"security-export-worker")
            try {
                worker.start();assertTrue("Real export did not reach target write",entered.await(45,TimeUnit.SECONDS))
                waitForNativeIo(worker)
                val start=SystemClock.elapsedRealtime()
                instrumentation.runOnMainSync {f.session.revoke()}
                val elapsed=SystemClock.elapsedRealtime()-start
                worker.join(10000)
                File(app.filesDir,"security-f03-observation.json").writeText(
                    "{\"api\":${android.os.Build.VERSION.SDK_INT},\"revokeMs\":$elapsed,\"workerExited\":${!worker.isAlive},\"closeOnMain\":${closeOnMain.get()},\"success\":${success.get()}}")
                assertFalse("Output close ran on main",closeOnMain.get())
                assertTrue("Revocation blocked main",elapsed<2000)
                assertFalse("Worker must exit BEFORE provider release",worker.isAlive)
                assertFalse(success.get());assertNotNull(failure.get())
                val executor=Executors.newSingleThreadExecutor()
                try {assertArrayEquals("Root writer remains usable and business facts unchanged",original,
                    executor.submit<ByteArray> {f.facts()}.get(5,TimeUnit.SECONDS))}
                finally {executor.shutdownNow()}
                assertEquals("LEGACY",LibraryAuthority.identity(f.raw))
            }finally {releaseProvider();worker.join(5000);physical.close()}
        }
    }
    @Test fun f04RealStalledReadCloseObservation() {
        val session=AttachmentSession(UUID.randomUUID(),UUID.randomUUID())
        val input=AttachmentInputStream.open(session.issue(),AttachmentProviderStreams.input(app,uri,session.issue()),16)
        val result=AtomicReference<Throwable?>()
        val worker=Thread({try {input.read();error("Stalled input unexpectedly read data")}catch(t:Throwable){result.set(t)}},"security-input-worker")
        try {
            worker.start();waitForNativeIo(worker)
            val returned=CountDownLatch(1);val elapsed=AtomicReference<Long>()
            android.os.Handler(Looper.getMainLooper()).post {
                val start=SystemClock.elapsedRealtime()
                try {session.revoke()}finally {elapsed.set(SystemClock.elapsedRealtime()-start);returned.countDown()}
            }
            val prompt=returned.await(3,TimeUnit.SECONDS)
            if(!prompt)releaseProvider() // Preserve the observation; prevent a stuck test process.
            assertTrue(returned.await(5,TimeUnit.SECONDS))
            worker.join(5000)
            File(app.filesDir,"security-f04-observation.json").writeText(
                "{\"api\":${android.os.Build.VERSION.SDK_INT},\"returnedBeforeProviderRelease\":$prompt,\"revokeMs\":${elapsed.get()},\"workerExited\":${!worker.isAlive}}")
            assertTrue("F04 reproduced: local Android FD close stalled main before provider release",prompt)
            assertFalse(worker.isAlive);assertNotNull(result.get())
        }finally {releaseProvider();worker.join(5000);input.close()}
    }
}
