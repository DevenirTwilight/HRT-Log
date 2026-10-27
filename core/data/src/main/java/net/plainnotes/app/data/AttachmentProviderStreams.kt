package net.plainnotes.app.data

import android.content.Context
import android.net.Uri
import android.os.Looper
import android.os.ParcelFileDescriptor
import net.plainnotes.app.domain.AttachmentSession
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.Buffer

/** One SAF open, no plaintext file or hidden API. FileChannel is publicly interruptible on
 * close, unlike Android8 AutoClose streams' raw pipe IO. Close is scheduled off main;
 * normal worker completion still waits for close errors before committing or acknowledging.
 * This does not promise that arbitrary OS/FUSE/provider primitives honor interruption.
 */
object AttachmentProviderStreams {
    private fun open(context:Context,uri:Uri,mode:String,cap:AttachmentSession.Capability):ParcelFileDescriptor {
        require(uri.scheme=="content");cap.check()
        val fd=requireNotNull(context.contentResolver.openFileDescriptor(uri,mode))
        try {cap.check();return fd}catch(t:Throwable) {fd.close();throw t}
    }
    fun input(context:Context,uri:Uri,cap:AttachmentSession.Capability):InputStream {
        val physical=open(context,uri,"r",cap)
        val stream=ParcelFileDescriptor.AutoCloseInputStream(physical)
        val channel=try {stream.channel}catch(t:Throwable) {stream.close();throw t}
        val reads=Any()
        // Own and wipe a bounded native buffer instead of leaving plaintext in FileChannel's
        // implicit thread-local temporary direct-buffer cache (or allocating a 16MiB read buffer).
        val buffer=try {ByteBuffer.allocateDirect(65536)}catch(t:Throwable) {stream.close();throw t}
        val zeros=try {ByteArray(buffer.capacity())}catch(t:Throwable) {stream.close();throw t}
        fun wipe() {(buffer as Buffer).clear();buffer.put(zeros);(buffer as Buffer).clear()}
        val cleanup=try {RevocableProviderClose(AutoCloseable {
            try {channel.close()}finally {try {stream.close()}finally {synchronized(reads) {wipe()}}}
        })}catch(t:Throwable) {stream.close();throw t}
        try {cap.own(AutoCloseable {cleanup.revoke()})}
        catch(t:Throwable) {cleanup.await();throw t}
        return object:InputStream() {
            override fun read():Int {
                val b=ByteArray(1)
                try {return if(read(b)<0)-1 else b[0].toInt() and 255}finally {b.fill(0)}
            }
            override fun read(b:ByteArray,o:Int,n:Int):Int=synchronized(reads) {
                require(o>=0 && n>=0 && n<=b.size-o)
                cap.check();cleanup.checkOpen()
                if(n==0)return@synchronized 0
                (buffer as Buffer).clear();(buffer as Buffer).limit(minOf(n,buffer.capacity()))
                try {
                    val count=channel.read(buffer)
                    cap.check();cleanup.checkOpen()
                    if(count<0)physical.checkError() // Preserve reliable-pipe failure, never accept partial input.
                    if(count>0) {(buffer as Buffer).flip();buffer.get(b,o,count)}
                    count
                }finally {wipe()}
            }
            override fun close() {
                cleanup.revoke()
                // ResourceLease may invoke close on the lifecycle thread. Never join external
                // channel teardown there; the IO worker observes its failure and awaits cleanup.
                if(Looper.myLooper()!=Looper.getMainLooper())cleanup.await()
            }
        }
    }
    fun output(context:Context,uri:Uri,cap:AttachmentSession.Capability):OutputStream {
        val physical=open(context,uri,"w",cap)
        val stream=ParcelFileDescriptor.AutoCloseOutputStream(physical)
        val channel=try {stream.channel}catch(t:Throwable) {stream.close();throw t}
        val target=object:OutputStream() {
            override fun write(value:Int)=write(byteArrayOf(value.toByte()))
            override fun write(b:ByteArray,o:Int,n:Int) {
                require(o>=0 && n>=0 && n<=b.size-o)
                val buffer=ByteBuffer.wrap(b,o,n)
                while(buffer.hasRemaining()) {
                    cap.check()
                    if(channel.write(buffer)<=0)throw IOException("Provider write made no progress")
                    cap.check()
                }
            }
            override fun close() {try {channel.close()}finally {stream.close()}}
        }
        val owned=try {RevocableArchiveOutput(target)}catch(t:Throwable) {target.close();throw t}
        try {cap.own(AutoCloseable {owned.revoke()});return owned}
        catch(t:Throwable) {owned.close();throw t}
    }
}
