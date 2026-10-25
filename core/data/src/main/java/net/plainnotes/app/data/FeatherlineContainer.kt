package net.plainnotes.app.data

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.zip.GZIPInputStream
import javax.crypto.Cipher
import javax.crypto.AEADBadTagException
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Independently authored interoperability reader. No native HRT Log backup codec is used.
 * The encrypted snapshot is owned by this object and destroyed on close; caller owns returned
 * authenticated JSON bytes and must clear them after its bounded source parser finishes.
 */
class FeatherlineContainer private constructor(private val bytes:ByteArray,val jsonBytes:Int):AutoCloseable {
    class Invalid:Exception()
    class Unsupported:Exception()
    class TooLarge:Exception()
    class Authentication:Exception()
    private var closed=false
    @Synchronized override fun close(){bytes.fill(0);closed=true}
    /** Never publishes unauthenticated plaintext, including providers that stream before doFinal. */
    @Synchronized fun authenticate(password:CharArray,checkActive:()->Unit={}):ByteArray {
        val key=ByteArray(32);var utf8:ByteArray?=null;var compressed:ByteArray?=null
        var salt:ByteArray?=null
        try {
            check(!closed);checkActive();if(password.size>4096)throw TooLarge()
            val encoded=Charsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).encode(java.nio.CharBuffer.wrap(password))
            utf8=ByteArray(encoded.remaining());encoded.get(utf8)
            if(encoded.hasArray())encoded.array().fill(0)
            salt=bytes.copyOfRange(37,53)
            val params=Argon2Parameters.Builder(Argon2Parameters.ARGON2_id).withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withSalt(salt).withMemoryAsKB(65536).withIterations(3).withParallelism(1).build()
            try {Argon2BytesGenerator().apply{init(params)}.generateBytes(utf8,key)}finally{params.clear()}
            checkActive()
            val cipher=Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE,SecretKeySpec(key,"AES"),GCMParameterSpec(128,bytes.copyOfRange(53,65)))
            cipher.updateAAD(bytes,0,65)
            compressed=try {cipher.doFinal(bytes,65,bytes.size-65)}catch(_:AEADBadTagException){throw Authentication()}
            checkActive()
            val output=object:ByteArrayOutputStream(minOf(jsonBytes,65536)) {fun clear(){buf.fill(0);reset()}}
            val block=ByteArray(8192)
            try {
                try {GZIPInputStream(ByteArrayInputStream(compressed)).use {input->
                    while(true) {checkActive();val n=input.read(block);if(n<0)break
                        if(n>jsonBytes-output.size())throw TooLarge()
                        output.write(block,0,n)
                    }
                }}catch(e:java.io.IOException){throw Invalid()}
                if(output.size()!=jsonBytes)throw Invalid()
                checkActive();return output.toByteArray()
            } finally {block.fill(0);output.clear()}
        } finally {password.fill('\u0000');key.fill(0);utf8?.fill(0);compressed?.fill(0);salt?.fill(0)}
    }
    companion object {
        const val MAXIMUM_BYTES=32*1024*1024
        // Independent plaintext budget; upstream's128MiB is deliberately not inherited.
        const val MAXIMUM_JSON_BYTES=8*1024*1024
        /** Exact supported v3 profile. Header costs are checked BEFORE KDF/allocation.
         * Different profiles/legacy versions remain unsupported, rather than silently normalized. */
        fun read(input:ByteArray):FeatherlineContainer {
            if(input.size>MAXIMUM_BYTES)throw TooLarge()
            if(input.size<81)throw Invalid()
            val h=ByteBuffer.wrap(input)
            val magic=ByteArray(7);h.get(magic)
            if(!magic.contentEquals("HRTBKP1".toByteArray(Charsets.US_ASCII)))throw Invalid()
            if(h.get().toInt()!=3 || h.get().toInt()!=2 || h.get().toInt()!=1 || h.get().toInt()!=1)throw Unsupported()
            val length=h.long
            if(length<=0)throw Invalid()
            if(length>MAXIMUM_JSON_BYTES)throw TooLarge()
            if(h.int!=3 || h.int!=65536 || h.int!=1 || h.int!=32 || h.get().toInt()!=16 || h.get().toInt()!=12)throw Unsupported()
            return FeatherlineContainer(input.copyOf(),length.toInt())
        }
    }
}
