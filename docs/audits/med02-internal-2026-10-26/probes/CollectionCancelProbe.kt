package net.plainnotes.app.data
import net.plainnotes.app.domain.AttachmentSession
import java.util.UUID
import java.io.OutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

fun main() {
    val session=AttachmentSession(UUID.randomUUID(),UUID.randomUUID())
    val entered=CountDownLatch(1);val unblock=CountDownLatch(1)
    val closed=AtomicBoolean(false);val done=AtomicBoolean(false)
    val out=object:OutputStream() {
        override fun write(value:Int) {entered.countDown();check(unblock.await(10,TimeUnit.SECONDS))}
        override fun write(b:ByteArray,o:Int,n:Int) {entered.countDown();check(unblock.await(10,TimeUnit.SECONDS))}
        override fun close(){closed.set(true);unblock.countDown()}
    }
    val dataset=UUID.randomUUID()
    val manifest=AttachmentBackupManifest(dataset,UUID.randomUUID(),UUID.randomUUID(),emptyList(),emptyList(),emptyList(),emptyList())
    val source=MedicalCollectionWriter.Source(dataset,MedicalCollectionIndex.Role.ACTIVE,AttachmentBackupPreparation.Snapshot(manifest))
    val thread=Thread {
        try {MedicalCollectionWriter.write(listOf(source),dataset,session.issue(),"synthetic-password".toCharArray(),out)}
        catch(_:IllegalStateException) {} finally {done.set(true)}
    }.apply {start()}
    check(entered.await(20,TimeUnit.SECONDS))
    session.revoke()
    check(!closed.get() && !done.get())
    println("actual PNMEDBK2 writer blocked inside target.write; after capability revoke: target.closed=${closed.get()}, worker.finished=${done.get()}")
    unblock.countDown();thread.join(5000)
    check(closed.get() && done.get())
    println("after external unblock: target.closed=${closed.get()}, worker.finished=${done.get()}; no successful receipt")
}
