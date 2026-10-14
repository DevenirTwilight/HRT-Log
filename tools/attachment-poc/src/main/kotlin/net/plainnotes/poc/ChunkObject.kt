package net.plainnotes.poc

import java.io.*
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** SYNTHETIC POC ONLY. Not a production format, clinical store, or security certification. */
object ChunkObject {
    const val HEADER = 68
    const val FOOTER = 68
    const val MAX_BYTES = 16L * 1024 * 1024
    private val magic = "SYNPOC01".toByteArray(Charsets.US_ASCII)
    private val endMagic = "POC-END1".toByteArray(Charsets.US_ASCII)
    private val random = SecureRandom()

    data class Identity(val store: UUID, val objectId: UUID, val version: UUID) {
        companion object { fun fresh(store: UUID = UUID.randomUUID()) = Identity(store, UUID.randomUUID(), UUID.randomUUID()) }
    }
    class Owned(val file: File, val identity: Identity, key: ByteArray) : Closeable {
        private val key = key.copyOf()
        private var closed = false
        @Synchronized fun open(expected: Identity = identity): Reader {
            check(!closed); return Reader(file, key, expected)
        }
        /** Only synthetic portable-envelope tests use this; no production key export API. */
        @Synchronized fun syntheticKey(): ByteArray { check(!closed); return key.copyOf() }
        @Synchronized override fun close() { closed = true; key.fill(0) }
    }
    enum class Stage { BLOCK, SYNC, RENAME }
    private fun nonce(prefix: ByteArray, sequence: Int) = ByteBuffer.allocate(12).put(prefix).putInt(sequence).array()
    private fun aad(header: ByteArray, index: Int, length: Int) = header + ByteBuffer.allocate(9).put(if(index == -1) 2.toByte() else 1.toByte()).putInt(index).putInt(length).array()
    fun gcm(encrypt: Boolean, key: ByteArray, nonce: ByteArray, aad: ByteArray, value: ByteArray): ByteArray {
        require(key.size == 32 && nonce.size == 12)
        return Cipher.getInstance("AES/GCM/NoPadding").run {
            init(if(encrypt) Cipher.ENCRYPT_MODE else Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
            updateAAD(aad); doFinal(value)
        }
    }
    private fun putId(b: ByteBuffer, v: UUID) { b.putLong(v.mostSignificantBits).putLong(v.leastSignificantBits) }
    private fun getId(b: ByteBuffer) = UUID(b.long, b.long)

    /** Caller owns source. Length is deliberately unknown; limits use bytes actually read. */
    fun write(root: File, id: Identity, source: InputStream, blockSize: Int = 64 * 1024,
               active: () -> Boolean = { true }, fault: (Stage, Int) -> Unit = { _, _ -> }): Owned {
        require(blockSize in 1024..1024*1024)
        require(root.isDirectory && !Files.isSymbolicLink(root.toPath()))
        val key = ByteArray(32).also(random::nextBytes)
        val prefix = ByteArray(8).also(random::nextBytes)
        val header = ByteBuffer.allocate(HEADER).put(magic).putInt(blockSize).also {
            putId(it,id.store); putId(it,id.objectId); putId(it,id.version)
        }.put(prefix).array()
        val temp = File(root, "${UUID.randomUUID()}.part")
        val target = File(root, "${UUID.randomUUID()}.object")
        val buffer = ByteArray(blockSize)
        var total = 0L; var index = 0; var zeroReads = 0
        val hash = MessageDigest.getInstance("SHA-256")
        fun authorized() { if(!active()) throw IOException("Synthetic session revoked") }
        try {
            // CREATE_NEW: no overwrite of any earlier object or caller-supplied path.
            Files.newOutputStream(temp.toPath(), java.nio.file.StandardOpenOption.CREATE_NEW).use { raw ->
                val output = DataOutputStream(raw); output.write(header)
                var eof = false
                while(!eof) {
                    authorized(); var used = 0
                    while(used < buffer.size) {
                        authorized()
                        val n = source.read(buffer,used,buffer.size-used)
                        if(n == -1) { eof = true; break }
                        if(n == 0) { if(++zeroReads > 8) throw IOException("Nonprogressing input"); continue }
                        zeroReads=0; used+=n
                        if(total+used > MAX_BYTES) throw IOException("Synthetic quota")
                    }
                    if(used == 0) break
                    check(index >= 0 && index < 16384) // Far below reserved 0xffffffff.
                    hash.update(buffer,0,used)
                    val plain=buffer.copyOf(used)
                    val cipher=try { gcm(true,key,nonce(prefix,index),aad(header,index,used),plain) } finally { plain.fill(0) }
                    try { output.writeInt(index);output.writeInt(used);output.write(cipher) } finally { cipher.fill(0) }
                    total+=used;index++;fault(Stage.BLOCK,index)
                }
                authorized()
                val manifest=ByteBuffer.allocate(44).putLong(total).putInt(index).put(hash.digest()).array()
                val sealed=try { gcm(true,key,nonce(prefix,-1),aad(header,-1,44),manifest) } finally { manifest.fill(0) }
                try { output.write(endMagic);output.write(sealed);output.flush() } finally { sealed.fill(0) }
            }
            RandomAccessFile(temp,"rw").use { it.fd.sync() };fault(Stage.SYNC,index)
            authorized(); fault(Stage.RENAME,index)
            Files.move(temp.toPath(),target.toPath(),StandardCopyOption.ATOMIC_MOVE)
            return Owned(target,id,key)
        } catch(e: Throwable) { temp.delete(); target.delete(); throw e }
        finally { buffer.fill(0);key.fill(0) }
    }

    /** Authenticate the complete object before exposing any byte. Each random block is reauthenticated. */
    class Reader constructor(file: File, key: ByteArray, expected: Identity) : Closeable {
        private val input = RandomAccessFile(file,"r")
        private val key = key.copyOf()
        private val header = ByteArray(HEADER)
        private val prefix: ByteArray
        val blockSize: Int
        val size: Long
        val blocks: Int
        private val physicalSize: Long
        private var closed = false
        var blockReads = 0; private set
        val maxScratchBytes: Int get() = 3*blockSize+16 // Algorithm buffers only, not VM/native peak.
        init {
            try {
                physicalSize=input.length()
                require(physicalSize in (HEADER+FOOTER).toLong()..(MAX_BYTES+16384*24+HEADER+FOOTER))
                input.readFully(header)
                val b=ByteBuffer.wrap(header);val tag=ByteArray(8);b.get(tag);require(tag.contentEquals(magic))
                blockSize=b.int;require(blockSize in 1024..1024*1024)
                require(Identity(getId(b),getId(b),getId(b))==expected)
                prefix=ByteArray(8).also(b::get)
                input.seek(physicalSize-FOOTER);input.readFully(tag);require(tag.contentEquals(endMagic))
                val sealed=ByteArray(60);input.readFully(sealed)
                val manifest=gcm(false,this.key,nonce(prefix,-1),aad(header,-1,44),sealed)
                val mb=ByteBuffer.wrap(manifest);size=mb.long;blocks=mb.int;val digest=ByteArray(32);mb.get(digest);manifest.fill(0)
                require(size in 0..MAX_BYTES && blocks.toLong()==(size+blockSize-1)/blockSize)
                require(physicalSize==HEADER+size+blocks.toLong()*24+FOOTER)
                val hash=MessageDigest.getInstance("SHA-256")
                for(i in 0 until blocks) { val plain=block(i);try {hash.update(plain)}finally {plain.fill(0)} }
                require(MessageDigest.isEqual(digest,hash.digest()))
            } catch(e: Throwable) { input.close();this.key.fill(0);throw e }
        }
        private fun block(i: Int): ByteArray {
            check(!closed); require(i in 0 until blocks)
            require(input.length()==physicalSize)
            input.seek(HEADER+i.toLong()*(blockSize+24))
            require(input.readInt()==i)
            val length=input.readInt();val expected=minOf(blockSize.toLong(),size-i.toLong()*blockSize).toInt()
            require(length==expected)
            val sealed=ByteArray(length+16);input.readFully(sealed);blockReads++
            return try { gcm(false,key,nonce(prefix,i),aad(header,i,length),sealed) } finally {sealed.fill(0)}
        }
        @Synchronized fun readAt(offset: Long, destination: ByteArray, requested: Int = destination.size): Int {
            check(!closed);require(offset>=0 && requested in 0..destination.size)
            if(offset>=size || requested==0)return 0
            val wanted=minOf(requested.toLong(),size-offset).toInt();var done=0
            // On failure wipe any earlier authenticated blocks in this response; no partial error result.
            try {
                while(done<wanted) {
                    val position=offset+done;val i=(position/blockSize).toInt();val within=(position%blockSize).toInt()
                    val plain=block(i)
                    try {val n=minOf(wanted-done,plain.size-within);plain.copyInto(destination,done,within,within+n);done+=n}
                    finally {plain.fill(0)}
                }
                return done
            } catch(e:Throwable) {destination.fill(0,0,wanted);throw e}
        }
        @Synchronized override fun close() { if(!closed){closed=true;input.close();key.fill(0)} }
    }
}
