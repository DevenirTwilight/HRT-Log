package net.plainnotes.app.data

import java.time.Instant

/** Process-local frozen input for a standalone actual-use submission. Not a durable task, a
 * database validity cache, or permission to refresh a stale ticket. Created in one transaction. */
internal class PreparedMeasuredCreate internal constructor(
    val ticket:MeasuredWriteTicket,
    val operationUuid:String,
    val draft:MeasuredActualUseDraft,
    val created:Instant,
    val configSha256:String,
    val referenceContextEventUuid:String?,
)

/** Historical commitment only: the record may have since been revised, reversed or deleted.
 * Absence deliberately cannot authorize retry or establish that no write ever occurred. */
internal sealed interface MeasuredCreateObservation {
    data class Committed(val receipt:MeasuredConsumptionReceipt):MeasuredCreateObservation
    data object NotObserved:MeasuredCreateObservation
}
