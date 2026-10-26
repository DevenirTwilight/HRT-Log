package net.plainnotes.app.data

import net.plainnotes.app.domain.AttachmentSession
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
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
    enum class Protocol(val version:Int) { SINGLE(1), COLLECTION(2) }
    data class Header internal constructor(val archive:UUID,val plaintextBytes:Long,val ciphertextBytes:Long,private val encoded:ByteArray,
        val protocol:Protocol) {
        fun bytes()=encoded.copyOf()
        fun salt()=encoded.copyOfRange(16,32)
        fun aad()="notes.medical.backup.outer.v${protocol.version}\u0000".toByteArray(Charsets.US_ASCII)+encoded
    }
    /** Encryption always starts a new attempt, never accepts a previous header/key/randomness. */
    fun fresh(n:Long):Header=fresh(n,Protocol.SINGLE)
    fun freshCollection(n:Long):Header=fresh(n,Protocol.COLLECTION)
    private fun fresh(n:Long,protocol:Protocol):Header {
        val salt=ByteArray(16).also(SecureRandom()::nextBytes)
        val id=UUID.randomUUID();val cipher=cipherLength(n)
        val b=ByteBuffer.allocate(HEADER_BYTES).put("PNMEDBK${protocol.version}".toByteArray(Charsets.US_ASCII))
        b.putShort(protocol.version.toShort()).putShort(1).putShort(1).putShort(0).put(salt).put(uuidBytes(id))
            .putInt(65536).putInt(3).putShort(1).putShort(0).putLong(n).putLong(cipher).putInt(0)
        return parse(b.array(),Math.addExact(HEADER_BYTES.toLong(),cipher),protocol)
    }
    /** Called before any password derivation or variable-length allocation. */
    fun parse(bytes:ByteArray,physicalBytes:Long):Header=parse(bytes,physicalBytes,Protocol.SINGLE)
    fun parseCollection(bytes:ByteArray,physicalBytes:Long):Header=parse(bytes,physicalBytes,Protocol.COLLECTION)
    private fun parse(bytes:ByteArray,physicalBytes:Long,protocol:Protocol):Header {
        require(bytes.size==HEADER_BYTES && physicalBytes in HEADER_BYTES.toLong()..MAX_WIRE)
        val b=ByteBuffer.wrap(bytes)
        val magic=ByteArray(8);b.get(magic);require(magic.contentEquals("PNMEDBK${protocol.version}".toByteArray(Charsets.US_ASCII)))
        require(b.short.toInt()==protocol.version && b.short.toInt()==1 && b.short.toInt()==1 && b.short.toInt()==0)
        b.position(32);val id=uuid(b)
        require(b.int==65536 && b.int==3 && b.short.toInt()==1 && b.short.toInt()==0)
        val n=b.long;val c=b.long;require(b.int==0)
        require(n in 91..MAX_WIRE && c==cipherLength(n) && Math.addExact(c,HEADER_BYTES.toLong())==physicalBytes)
        return Header(id,n,c,bytes.copyOf(),protocol)
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
            ArchiveKdfWorkspace.derive(p,header.salt(),root){cap.check()}
            cap.check()
            fun key(label:String)=ByteArray(32).also {out->HKDFBytesGenerator(SHA256Digest()).apply {
                init(HKDFParameters(root,uuidBytes(header.archive),label.toByteArray(Charsets.US_ASCII)))
                generateBytes(out,0,out.size)
            }}
            outer=key("notes.medical.backup.v${header.protocol.version}/outer");wrapping=key("notes.medical.backup.v${header.protocol.version}/portable-dek")
            return Keys(outer,wrapping).also {cap.check()}
        } catch(e:Throwable) {outer?.fill(0);wrapping?.fill(0);throw e}
        finally {password.fill('\u0000');encoded?.fill(0);utf8?.let {if(it.hasArray())it.array().fill(0)};root.fill(0)}
    }
    private fun aad(header:Header,c:AttachmentObjectContext,library:UUID?):ByteArray {
        require((header.protocol==Protocol.COLLECTION)==(library!=null)) {"Wrong portable-key namespace"}
        return "notes.medical.backup.dek.v${header.protocol.version}\u0000".toByteArray(Charsets.US_ASCII)+
            uuidBytes(header.archive)+(library?.let(::uuidBytes) ?: byteArrayOf())+c.encode()
    }
    private fun wrap(header:Header,key:ByteArray,context:AttachmentObjectContext,library:UUID?,dek:ByteArray):ByteArray {
        require(dek.size==32 && key.size==32)
        val iv=ByteArray(12).also(SecureRandom()::nextBytes)
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE,SecretKeySpec(key,"AES"),GCMParameterSpec(128,iv));cipher.updateAAD(aad(header,context,library))
        return iv+cipher.doFinal(dek)
    }
    class CollectionWrapAttempt(private val header:Header,private val key:ByteArray,contexts:List<Pair<UUID,AttachmentObjectContext>>) {
        private val remaining=contexts.associate {(library,context)->(library to context.version) to context}.toMutableMap()
        init {require(header.protocol==Protocol.COLLECTION && contexts.size<=1000 && remaining.size==contexts.size)}
        @Synchronized fun wrap(library:UUID,context:AttachmentObjectContext,dek:ByteArray):ByteArray {
            require(remaining.remove(library to context.version)==context)
            return MedicalArchiveCrypto.wrap(header,key,context,library,dek)
        }
    }
    class WrapAttempt(private val header:Header,private val key:ByteArray,contexts:List<AttachmentObjectContext>) {
        private val remaining=contexts.associateBy {it.version}.toMutableMap()
        init {require(header.protocol==Protocol.SINGLE && contexts.size<=1000 && remaining.size==contexts.size)}
        /** Reserve before crypto. Never retry this version within an attempt, even after failure. */
        @Synchronized fun wrap(context:AttachmentObjectContext,dek:ByteArray):ByteArray {
            require(remaining.remove(context.version)==context && dek.size==32)
            return MedicalArchiveCrypto.wrap(header,key,context,null,dek)
        }
    }
    fun unwrap(header:Header,key:ByteArray,context:AttachmentObjectContext,envelope:ByteArray,library:UUID?=null):ByteArray {
        require(key.size==32 && envelope.size==60)
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE,SecretKeySpec(key,"AES"),GCMParameterSpec(128,envelope,0,12));cipher.updateAAD(aad(header,context,library))
        return cipher.doFinal(envelope,12,48).also {check(it.size==32)}
    }
    fun hex(b:ByteArray)=b.joinToString("") {"%02x".format(it.toInt() and 255)}
    fun unhex(s:String):ByteArray {require(s.length%2==0 && s.all {it in '0'..'9' || it in 'a'..'f'});return ByteArray(s.length/2) {s.substring(2*it,2*it+2).toInt(16).toByte()}}
}
