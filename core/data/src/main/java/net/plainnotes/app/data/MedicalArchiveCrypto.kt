package net.plainnotes.app.data

import net.plainnotes.app.domain.AttachmentSession
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import org.bouncycastle.crypto.params.HKDFParameters
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** §147.D implementation parameters. OFF, not permanently frozen or an activation protocol. */
internal object MedicalArchiveCrypto {
    const val HEADER_BYTES=80
    const val MAX_WIRE=1024L*1024*1024
    const val MAX_CIPHER=512L*1024*1024
    const val MAX_MANIFEST=8*1024*1024
    val uuidOrder:Comparator<UUID> = compareBy {it.toString()}
    fun cipherLength(n:Long):Long {
        require(n>=0 && n<=MAX_WIRE)
        val plus=Math.addExact(n,40)
        return Math.addExact(plus,Math.multiplyExact(16,maxOf(1,(plus+65519)/65520)))
    }
    fun uuidBytes(id:UUID)=ByteBuffer.allocate(16).putLong(id.mostSignificantBits).putLong(id.leastSignificantBits).array()
    fun uuid(b:ByteBuffer)=UUID(b.long,b.long)
    data class Header internal constructor(val archive:UUID,val plaintextBytes:Long,val ciphertextBytes:Long,private val encoded:ByteArray) {
        fun bytes()=encoded.copyOf()
        fun salt()=encoded.copyOfRange(16,32)
        fun aad()="notes.medical.backup.outer.v1\u0000".toByteArray(Charsets.US_ASCII)+encoded
    }
    /** Encryption always starts a new attempt, never accepts a previous header/key/randomness. */
    fun fresh(n:Long):Header {
        val salt=ByteArray(16).also(SecureRandom()::nextBytes)
        val id=UUID.randomUUID();val cipher=cipherLength(n)
        val b=ByteBuffer.allocate(HEADER_BYTES).put("PNMEDBK1".toByteArray(Charsets.US_ASCII))
        b.putShort(1).putShort(1).putShort(1).putShort(0).put(salt).put(uuidBytes(id))
            .putInt(65536).putInt(3).putShort(1).putShort(0).putLong(n).putLong(cipher).putInt(0)
        return parse(b.array(),Math.addExact(HEADER_BYTES.toLong(),cipher))
    }
    /** Called before any password derivation or variable-length allocation. */
    fun parse(bytes:ByteArray,physicalBytes:Long):Header {
        require(bytes.size==HEADER_BYTES && physicalBytes in HEADER_BYTES.toLong()..MAX_WIRE)
        val b=ByteBuffer.wrap(bytes)
        val magic=ByteArray(8);b.get(magic);require(magic.contentEquals("PNMEDBK1".toByteArray(Charsets.US_ASCII)))
        require(b.short.toInt()==1 && b.short.toInt()==1 && b.short.toInt()==1 && b.short.toInt()==0)
        b.position(32);val id=uuid(b)
        require(b.int==65536 && b.int==3 && b.short.toInt()==1 && b.short.toInt()==0)
        val n=b.long;val c=b.long;require(b.int==0)
        require(n in 91..MAX_WIRE && c==cipherLength(n) && Math.addExact(c,HEADER_BYTES.toLong())==physicalBytes)
        return Header(id,n,c,bytes.copyOf())
    }
    class Keys internal constructor(val outer:ByteArray,val wrapping:ByteArray):AutoCloseable {
        override fun close() {outer.fill(0);wrapping.fill(0)}
    }
    /** Sole password ownership, including early refusal. No normalization or weak fallback. */
    fun derive(header:Header,password:CharArray,cap:AttachmentSession.Capability):Keys {
        var utf8:ByteBuffer?=null;var encoded:ByteArray?=null
        val root=ByteArray(32);var outer:ByteArray?=null;var wrapping:ByteArray?=null
        try {
            cap.check();require(password.size in 1..4096)
            utf8=Charsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(password))
            require(utf8.remaining() in 1..4096)
            val p=ByteArray(utf8.remaining()).also {utf8.get(it);encoded=it}
            val params=Argon2Parameters.Builder(Argon2Parameters.ARGON2_id).withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withSalt(header.salt()).withMemoryAsKB(65536).withIterations(3).withParallelism(1).build()
            try {Argon2BytesGenerator().apply {init(params);generateBytes(p,root)}} finally {params.clear()}
            cap.check()
            fun key(label:String)=ByteArray(32).also {out->HKDFBytesGenerator(SHA256Digest()).apply {
                init(HKDFParameters(root,uuidBytes(header.archive),label.toByteArray(Charsets.US_ASCII)))
                generateBytes(out,0,out.size)
            }}
            outer=key("notes.medical.backup.v1/outer");wrapping=key("notes.medical.backup.v1/portable-dek")
            return Keys(outer,wrapping).also {cap.check()}
        } catch(e:Throwable) {outer?.fill(0);wrapping?.fill(0);throw e}
        finally {password.fill('\u0000');encoded?.fill(0);utf8?.let {if(it.hasArray())it.array().fill(0)};root.fill(0)}
    }
    private fun aad(id:UUID,c:AttachmentObjectContext)="notes.medical.backup.dek.v1\u0000".toByteArray(Charsets.US_ASCII)+uuidBytes(id)+c.encode()
    class WrapAttempt(private val header:Header,private val key:ByteArray,contexts:List<AttachmentObjectContext>) {
        private val remaining=contexts.associateBy {it.version}.toMutableMap()
        init {require(contexts.size<=1000 && remaining.size==contexts.size)}
        /** Reserve before crypto. Never retry this version within an attempt, even after failure. */
        @Synchronized fun wrap(context:AttachmentObjectContext,dek:ByteArray):ByteArray {
            require(remaining.remove(context.version)==context && dek.size==32)
            val iv=ByteArray(12).also(SecureRandom()::nextBytes)
            val cipher=Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE,SecretKeySpec(key,"AES"),GCMParameterSpec(128,iv));cipher.updateAAD(aad(header.archive,context))
            return iv+cipher.doFinal(dek)
        }
    }
    fun unwrap(header:Header,key:ByteArray,context:AttachmentObjectContext,envelope:ByteArray):ByteArray {
        require(key.size==32 && envelope.size==60)
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE,SecretKeySpec(key,"AES"),GCMParameterSpec(128,envelope,0,12));cipher.updateAAD(aad(header.archive,context))
        return cipher.doFinal(envelope,12,48).also {check(it.size==32)}
    }
    fun hex(b:ByteArray)=b.joinToString("") {"%02x".format(it.toInt() and 255)}
    fun unhex(s:String):ByteArray {require(s.length%2==0 && s.all {it in '0'..'9' || it in 'a'..'f'});return ByteArray(s.length/2) {s.substring(2*it,2*it+2).toInt(16).toByte()}}
}
