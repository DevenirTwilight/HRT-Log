package net.plainnotes.app.domain.write

/** Presentation protocol only. These values are observations from the write owner, never proof
 * of database validity, a durable queue, or permission to replay a stock operation. */
enum class WriteStatus { PENDING, SUCCESS, FAILED }
enum class WritePhase { QUEUED, WRITING, RECONCILING, MANUAL_CHECK, TERMINAL }

data class WriteProgressSummary(
    val operationUuid:String,
    val status:WriteStatus,
    val phase:WritePhase,
    val retryAllowed:Boolean=false,
) {
    init {
        require(operationUuid.isNotBlank())
        require((status==WriteStatus.PENDING)==(phase!=WritePhase.TERMINAL))
        require(!retryAllowed || status==WriteStatus.FAILED)
    }
}
