package net.plainnotes.app.domain

import java.util.UUID

/** Memory-only, PRIMARY-only authority. A reconstructed process starts with no capabilities.
 * Slow IO runs before publish. The short synchronous publish callback must INCLUDE its commit,
 * not merely enqueue work or return from a Room withTransaction callback before Room commits.
 * Revocation and publication linearize on the same monitor. This is not a crypto format.
 */
class AttachmentSession(private val dataset: UUID, private val recordEpoch: UUID) {
    private val monitor = Any()
    private val processEpoch = UUID.randomUUID()
    private var epoch = 0L
    private var active = true
    private val resources = LinkedHashMap<ResourceLease, Capability>()

    /** Sole ownership of a resource, not permission to read it. All reads still require a capability.
     * Cleanup must only release resources; it must not publish work or acquire other leases.
     */
    inner class ResourceLease internal constructor(private val resource: AutoCloseable) : AutoCloseable {
        private var closed = false
        private var failure: Throwable? = null
        override fun close() {
            val error = synchronized(this) {
                if (!closed) {
                    closed = true
                    try { resource.close() }
                    catch (error: Throwable) { failure = error }
                }
                failure
            }
            // Never acquire the publication monitor while holding the lease monitor.
            // Another publisher is allowed to close this same lease.
            error?.let { throw it }
            synchronized(monitor) { resources.remove(this) }
        }
    }

    private fun release(selected: List<ResourceLease>) {
        var failure: IllegalStateException? = null
        selected.forEach { lease ->
            try { lease.close() }
            catch (error: Throwable) {
                val report = failure ?: IllegalStateException("Attachment resource cleanup failed").also { failure = it }
                report.addSuppressed(error)
            }
        }
        failure?.let { throw it }
    }
    inner class Capability internal constructor(private val issuedEpoch: Long, private val parent: Capability? = null) {
        private var closed = false
        private fun valid(): Boolean = !closed && (parent?.valid() ?: true)
        private fun belongsTo(owner: Capability): Boolean = this === owner || parent?.belongsTo(owner) == true
        fun fork(): Capability = publish { Capability(issuedEpoch, this) }
        fun <T> publish(block: () -> T): T = synchronized(monitor) {
            check(active && valid() && epoch == issuedEpoch) { "Attachment capability revoked" }
            block()
        }
        fun <T> publishFor(expectedDataset: UUID, expectedRecordEpoch: UUID, block: () -> T): T = publish {
            check(expectedDataset==dataset && expectedRecordEpoch==recordEpoch) { "Capability scope mismatch" }
            block()
        }
        fun check() = publish { Unit }
        /** Transfer a newly created handle. Rejected ownership closes it, including after revocation.
         * Never register the same handle twice. Closing the lease does not revoke its capability.
         */
        fun own(resource: AutoCloseable): ResourceLease {
            try {
                return publish { ResourceLease(resource).also { resources[it] = this } }
            } catch (failure: Throwable) {
                try { resource.close() } catch (cleanup: Throwable) { if (cleanup !== failure) failure.addSuppressed(cleanup) }
                throw failure
            }
        }
        /** Clears this array only; it cannot erase copies or revoke a native consumer's own buffer. */
        fun ownBytes(bytes: ByteArray): ResourceLease = own(AutoCloseable { bytes.fill(0) })
        fun close() {
            val selected = synchronized(monitor) {
                closed = true
                resources.filterValues { it.belongsTo(this) }.keys.toList()
            }
            release(selected)
        }
    }
    fun issue(expectedDataset: UUID = dataset, expectedRecordEpoch: UUID = recordEpoch,
              primary: Boolean = true): Capability = synchronized(monitor) {
        check(primary && active && expectedDataset == dataset && expectedRecordEpoch == recordEpoch)
        Capability(epoch)
    }
    /** Lock, space change and cancel revoke this session permanently; unlock creates a fresh one. */
    fun revoke() {
        val selected = synchronized(monitor) {
            if (active) { active = false; epoch++ }
            resources.keys.toList()
        }
        release(selected)
    }
    fun processIdentity(): UUID = processEpoch
}
