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
    inner class Capability internal constructor(private val issuedEpoch: Long, private val parent: Capability? = null) {
        private var closed = false
        private fun valid(): Boolean = !closed && (parent?.valid() ?: true)
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
        fun close() = synchronized(monitor) { closed = true }
    }
    fun issue(expectedDataset: UUID = dataset, expectedRecordEpoch: UUID = recordEpoch,
              primary: Boolean = true): Capability = synchronized(monitor) {
        check(primary && active && expectedDataset == dataset && expectedRecordEpoch == recordEpoch)
        Capability(epoch)
    }
    /** Lock, space change and cancel revoke this session permanently; unlock creates a fresh one. */
    fun revoke() = synchronized(monitor) { active = false; epoch++ }
    fun processIdentity(): UUID = processEpoch
}
