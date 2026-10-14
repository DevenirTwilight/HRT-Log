package net.plainnotes.poc

import com.google.crypto.tink.subtle.AesGcmHkdfStreaming
import java.io.File
import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.nio.file.LinkOption
import java.nio.channels.NonWritableChannelException
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import net.plainnotes.app.domain.AttachmentSession

/** NON_PRODUCTION candidate, no production format/key service. Full scan uses the pinned FD. */
object B2StreamingCandidate {
    const val MAX_BYTES=16L*1024*1024
    const val SCRATCH=65536
    data class Evidence(val length:Long,val plainHash:ByteArray,val cipherHash:ByteArray)
    fun primitive(key: ByteArray) = AesGcmHkdfStreaming(key, "HmacSHA256", 32, 65536, 0)
    private fun session()=AttachmentSession(UUID.randomUUID(),UUID.randomUUID()).issue()
    fun openAuthenticated(file:File,key:ByteArray,aad:ByteArray,capability:AttachmentSession.Capability=session(),
                          expected:Evidence?=null,beforeLease:()->Unit={}):Lease {
        val child=capability.fork()
        val cipher=try { Files.newByteChannel(file.toPath(),StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS) }
            catch(e:Throwable){child.close();throw e}
        var reader:SeekableByteChannel?=null
        val bytes=ByteArray(SCRATCH)
        try {
            if(cipher.size()>primitive(key).expectedCiphertextSize(MAX_BYTES))throw IOException("Candidate quota")
            val hash=MessageDigest.getInstance("SHA-256");var total=0L;var zeros=0
            val pinned=object:java.io.FilterInputStream(java.nio.channels.Channels.newInputStream(cipher)) {
                override fun close() { /* keep the same ciphertext FD for the future lease */ }
            }
            primitive(key).newDecryptingStream(pinned,aad).use { stream -> while(true) {
                child.check();val n=stream.read(bytes)
                if(n<0)break
                if(n==0){if(++zeros>8)throw IOException("Nonprogressing candidate");continue}
                zeros=0;total+=n;if(total>MAX_BYTES)throw IOException("Candidate quota")
                hash.update(bytes,0,n);bytes.fill(0)
            } }
            val physical=cipher.size();cipher.position(0)
            val cipherHash=MessageDigest.getInstance("SHA-256")
            while(true){val n=cipher.read(ByteBuffer.wrap(bytes));if(n<0)break;if(n==0)throw IOException("Cipher stalled");cipherHash.update(bytes,0,n)}
            val evidence=Evidence(total,hash.digest(),cipherHash.digest())
            if(expected!=null && (total!=expected.length || !MessageDigest.isEqual(evidence.plainHash,expected.plainHash) ||
                        !MessageDigest.isEqual(evidence.cipherHash,expected.cipherHash)))throw IOException("Commitment mismatch")
            cipher.position(0)
            reader=primitive(key).newSeekableDecryptingChannel(cipher,aad)
            reader.position(0);beforeLease()
            return child.publish { Lease(reader,child,physical,cipher,evidence) }
        } catch(e:Throwable){child.close();reader?.close();if(cipher.isOpen)cipher.close();throw e}
        finally {bytes.fill(0)}
    }
    class Lease internal constructor(private val reader:SeekableByteChannel,private val capability:AttachmentSession.Capability,
                                     private val physical:Long,private val cipher:SeekableByteChannel,val evidence:Evidence):SeekableByteChannel {
        private var closed=false
        @Synchronized override fun read(dst:ByteBuffer):Int = readWithHook(dst) {}
        private fun readWithHook(dst:ByteBuffer,beforeDelivery:()->Unit):Int {
            check(!closed);require(dst.remaining()<=SCRATCH)
            val scratch=ByteArray(dst.remaining())
            try {
                capability.check();if(cipher.size()!=physical)throw IOException("Object changed")
                val n=reader.read(ByteBuffer.wrap(scratch))
                beforeDelivery()
                return capability.publish { if(n>0)dst.put(scratch,0,n);n }
            } finally {scratch.fill(0)}
        }
        @Synchronized fun readAt(offset:Long,dst:ByteBuffer,beforeDelivery:()->Unit={}):Int {
            val previous=reader.position()
            try { position(offset);return readWithHook(dst,beforeDelivery) }finally {if(reader.isOpen)reader.position(previous)}
        }
        override fun size():Long=capability.publish{check(!closed);evidence.length}
        @Synchronized override fun position():Long=capability.publish{check(!closed);reader.position()}
        @Synchronized override fun position(value:Long):SeekableByteChannel=capability.publish{check(!closed);reader.position(value);this}
        override fun write(src:ByteBuffer):Int=throw NonWritableChannelException()
        override fun truncate(size:Long):SeekableByteChannel=throw NonWritableChannelException()
        override fun isOpen():Boolean=!closed&&reader.isOpen
        override fun close(){capability.close();synchronized(this){closed=true;reader.close()}}
    }
}
