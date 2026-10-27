package net.plainnotes.app.data

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants.*
import android.system.StructPollfd
import net.plainnotes.app.domain.AttachmentSession
import java.io.FileDescriptor
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean

/** One SAF open, no plaintext file. Android8 AutoClose streams can close their FD without
 * interrupting an already blocked pipe syscall. Use O_NONBLOCK and short poll intervals so
 * revoked pipe IO exits itself; do not rely on a remote provider to release its opposite end.
 * A dup held by the IO worker prevents descriptor-number reuse during concurrent close.
 * Regular/FUSE IO and a provider blocking inside open may ignore nonblocking semantics;
 * this is not a universal OS/provider deadline or another authorization authority.
 */
object AttachmentProviderStreams {
    private class Handle(val physical:ParcelFileDescriptor,private val cap:AttachmentSession.Capability) {
        private val closed=AtomicBoolean(false)
        private val monitor=Any()
        init {
            cap.check()
            val flags=Os.fcntlInt(physical.fileDescriptor,F_GETFL,0)
            Os.fcntlInt(physical.fileDescriptor,F_SETFL,flags or O_NONBLOCK)
        }
        private fun check() {cap.check();check(!closed.get()) {"Provider stream closed"}}
        fun <T> io(block:(FileDescriptor)->T):T {
            val fd=synchronized(monitor) {check();Os.dup(physical.fileDescriptor)}
            try {check();return block(fd)}finally {Os.close(fd)}
        }
        fun ready(fd:FileDescriptor,event:Int,started:Long) {
            check()
            if(SystemClock.elapsedRealtime()-started>30000)throw IOException("Provider pipe stalled")
            val poll=StructPollfd().apply {this.fd=fd;events=event.toShort()}
            try {Os.poll(arrayOf(poll),50)}catch(e:ErrnoException) {if(e.errno!=EINTR)throw IOException(e)}
            check()
        }
        fun read(fd:FileDescriptor,b:ByteArray,o:Int,n:Int):Int {
            val start=SystemClock.elapsedRealtime()
            while(true) {
                check()
                try {
                    val count=Os.read(fd,b,o,n)
                    if(count==0) {physical.checkError();return -1}
                    return count
                }catch(e:ErrnoException) {
                    if(e.errno!=EAGAIN && e.errno!=EINTR)throw IOException(e)
                    ready(fd,POLLIN,start)
                }
            }
        }
        fun write(fd:FileDescriptor,b:ByteArray,o:Int,n:Int) {
            var done=0;var progress=SystemClock.elapsedRealtime()
            while(done<n) {
                check()
                try {
                    val count=Os.write(fd,b,o+done,n-done)
                    if(count<=0)throw IOException("Provider write made no progress")
                    done+=count;progress=SystemClock.elapsedRealtime()
                }catch(e:ErrnoException) {
                    if(e.errno!=EAGAIN && e.errno!=EINTR) {physical.checkError();throw IOException(e)}
                    ready(fd,POLLOUT,progress)
                }
            }
        }
        fun close() {
            closed.set(true)
            synchronized(monitor) {physical.close()}
        }
    }
    private fun open(context:Context,uri:Uri,mode:String,cap:AttachmentSession.Capability):Handle {
        require(uri.scheme=="content");cap.check()
        val fd=requireNotNull(context.contentResolver.openFileDescriptor(uri,mode))
        try {return Handle(fd,cap)}catch(t:Throwable) {fd.close();throw t}
    }
    fun input(context:Context,uri:Uri,cap:AttachmentSession.Capability):InputStream {
        val handle=open(context,uri,"r",cap)
        return object:InputStream() {
            override fun read():Int {val b=ByteArray(1);return if(read(b)<0)-1 else b[0].toInt() and 255}
            override fun read(b:ByteArray,o:Int,n:Int):Int {
                require(o>=0 && n>=0 && n<=b.size-o)
                if(n==0)return 0
                return handle.io {handle.read(it,b,o,n)}
            }
            override fun close()=handle.close()
        }
    }
    fun output(context:Context,uri:Uri,cap:AttachmentSession.Capability):OutputStream {
        val handle=open(context,uri,"w",cap)
        val target=object:OutputStream() {
            override fun write(value:Int)=write(byteArrayOf(value.toByte()))
            override fun write(b:ByteArray,o:Int,n:Int) {
                require(o>=0 && n>=0 && n<=b.size-o)
                handle.io {handle.write(it,b,o,n)}
            }
            override fun close()=handle.close()
        }
        val owned=try {RevocableArchiveOutput(target)}catch(t:Throwable) {handle.close();throw t}
        try {cap.own(AutoCloseable {owned.revoke()});return owned}
        catch(t:Throwable) {owned.close();throw t}
    }
}
