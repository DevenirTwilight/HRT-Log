import net.plainnotes.app.domain.AttachmentSession
import net.plainnotes.app.domain.AttachmentInputStream
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

fun main() {
    val session=AttachmentSession(UUID.randomUUID(),UUID.randomUUID())
    val cap=session.issue()
    val closeEntered=CountDownLatch(1);val unblock=CountDownLatch(1)
    val stream=object:InputStream() {
        override fun read()=0
        override fun close(){closeEntered.countDown();check(unblock.await(5,TimeUnit.SECONDS))}
    }
    AttachmentInputStream.open(cap,stream,16)
    val returned=AtomicBoolean(false)
    val thread=Thread {session.revoke();returned.set(true)}.apply {start()}
    check(closeEntered.await(2,TimeUnit.SECONDS))
    thread.join(250)
    check(!returned.get())
    val denied=runCatching {cap.check()}.isFailure
    check(denied)
    println("While synthetic provider close blocks: revoke returned=${returned.get()}, further publication denied=$denied")
    unblock.countDown();thread.join(2000);check(returned.get())
    println("After provider released: revoke returned=${returned.get()}")
}
