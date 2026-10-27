package net.plainnotes.app.data
import net.plainnotes.app.domain.AttachmentSession
import java.util.UUID
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel
import java.security.MessageDigest

private class MemoryChannel(private val bytes:ByteArray):SeekableByteChannel {
    private var p=0;var opened=true
    override fun read(dst:ByteBuffer):Int {check(opened);if(!dst.hasRemaining())return 0;if(p==bytes.size)return -1;val n=minOf(dst.remaining(),bytes.size-p);dst.put(bytes,p,n);p+=n;return n}
    override fun position()=p.toLong()
    override fun position(n:Long):SeekableByteChannel {require(n in 0..bytes.size.toLong());p=n.toInt();return this}
    override fun size()=bytes.size.toLong()
    override fun isOpen()=opened
    override fun close(){opened=false}
    override fun write(src:ByteBuffer):Int=error("read only")
    override fun truncate(n:Long):SeekableByteChannel=error("read only")
}
fun main() {
    fun session()=AttachmentSession(UUID.randomUUID(),UUID.randomUUID())
    fun hash(b:ByteArray)=MessageDigest.getInstance("SHA-256").digest(b).joinToString(""){"%02x".format(it.toInt() and 255)}
    val context=AttachmentObjectContext(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID())
    val key=ByteArray(32){it.toByte()};val plain=ByteArray(140000){(it%251).toByte()}
    val output=ByteArrayOutputStream();val raw=key.copyOf()
    val manifest=AttachmentObjectStreams.write(session().issue(),context,raw,ByteArrayInputStream(plain),output,16L*1024*1024)
    check(raw.all {it==0.toByte()})
    val cipher=output.toByteArray()
    AttachmentObjectStreams.open(session().issue(),manifest,key.copyOf(),MemoryChannel(cipher)).use {reader->
        for(p in listOf(0,65500,65536,131000)) {
            val dst=ByteArray(64);var done=0
            while(done<64) {val n=reader.readAt((p+done).toLong(),dst,done,64-done);check(n>0);done+=n}
            check(dst.contentEquals(plain.copyOfRange(p,p+64)))
        }
    }
    println("actual Tink adapter: synthetic 140000-byte roundtrip + cross-segment random access PASS; raw write DEK cleared")
    var refused=0
    fun reject(label:String,data:ByteArray,expected:AttachmentObjectStreams.Manifest=manifest,k:ByteArray=key.copyOf()) {
        val channel=MemoryChannel(data);var returned=false
        try {AttachmentObjectStreams.open(session().issue(),expected,k,channel).use {returned=true}}catch(_:Exception){}
        check(!returned && !channel.opened && k.all {it==0.toByte()})
        refused++;println("$label: refused before Reader publication; channel closed; raw DEK cleared")
    }
    reject("wrong DEK",cipher,k=ByteArray(32){0x77})
    reject("wrong object AAD",cipher,manifest.copy(context=context.copy(objectId=UUID.randomUUID())))
    val mutated=cipher.copyOf().also {it[it.lastIndex]=(it.last().toInt() xor 1).toByte()}
    reject("last segment modified + recommitted cipher SHA",mutated,manifest.copy(ciphertextSha256=hash(mutated)))
    val truncated=cipher.copyOf(cipher.size-1)
    reject("truncated + recommitted cipher length/SHA",truncated,manifest.copy(ciphertextBytes=truncated.size.toLong(),ciphertextSha256=hash(truncated)))
    val trailing=cipher+byteArrayOf(1)
    reject("trailing byte + recommitted cipher length/SHA",trailing,manifest.copy(ciphertextBytes=trailing.size.toLong(),ciphertextSha256=hash(trailing)))
    reject("wrong plaintext commitment",cipher,manifest.copy(plaintextSha256="0".repeat(64)))
    val authority=session();val reader=AttachmentObjectStreams.open(authority.issue(),manifest,key.copyOf(),MemoryChannel(cipher))
    authority.revoke();val dest=ByteArray(64){0x42}
    check(runCatching {reader.readAt(0,dest,0,dest.size)}.isFailure && dest.all {it==0.toByte()})
    println("revoked read: denied and requested destination zeroed")
    println("negative authentication cases=$refused; result=NOT_REPRODUCED for unauthenticated Reader release within this corpus")
    plain.fill(0);key.fill(0)
}
