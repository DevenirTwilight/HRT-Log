package net.plainnotes.app.data

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters

/** One bounded, wiped workspace for the existing 64MiB archive KDFs.
 * BC's default per-generator pool otherwise becomes 64MiB of garbage on EACH
 * password attempt, allowing consecutive attempts to overlap before ART collects.
 * Only allocation is shared: no password, salt, key or derived result is cached.
 * Cryptography and all wire parameters remain the existing BC implementation.
 * Zeroed blocks stay process-resident; this does not claim to erase ART/native copies.
 */
internal object ArchiveKdfWorkspace {
    private val blocks=Blocks()
    @Synchronized fun derive(password:ByteArray,salt:ByteArray,key:ByteArray,checkActive:()->Unit) {
        checkActive() // A request revoked while waiting may not start a KDF.
        require(key.size==32)
        val params=Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13).withSalt(salt)
            .withMemoryAsKB(65536).withIterations(3).withParallelism(1)
            .withBlockPool(blocks).build()
        try {
            Argon2BytesGenerator().apply {init(params);generateBytes(password,key)}
            checkActive()
        } catch(t:Throwable) {key.fill(0);throw t}
        finally {blocks.clear();params.clear()}
    }

    /** Public BC allocator contract, not a second implementation of Argon2. */
    internal class Blocks:Argon2BytesGenerator.BlockPool {
        // BC1.86 FillBlock borrows four auxiliary blocks in addition to the
        // protocol's65536 work blocks. This is allocator overhead, not KDF memory.
        private val owned=ArrayList<Argon2BytesGenerator.Block>(65536+4)
        private var next=0
        override fun allocate():Argon2BytesGenerator.Block {
            check(next<65536+4) {"Archive KDF workspace exceeded"}
            if(next==owned.size)owned.add(Argon2BytesGenerator.Block())
            return owned[next++].clear()
        }
        override fun deallocate(block:Argon2BytesGenerator.Block) {block.clear()}
        fun clear() {owned.forEach {it.clear()};next=0}
    }
}
