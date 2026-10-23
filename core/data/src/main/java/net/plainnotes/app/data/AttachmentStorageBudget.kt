package net.plainnotes.app.data

/** Integer byte/identity admission only. No cleanup authority or transferable disk reservation. */
internal data class AttachmentStorageBudget(
    val maximumBytes: Long = 256L * 1024 * 1024,
    val maximumAliases: Int = 512,
    val maximumEntries: Int = 4096,
    val minimumFreeBytes: Long = 32L * 1024 * 1024
) {
    init {
        require(maximumBytes >= 0 && maximumAliases >= 0 && maximumEntries in 1..100000 && minimumFreeBytes >= 0)
    }
    enum class Category { CURRENT, HISTORY, QUARANTINED, IN_FLIGHT, UNKNOWN }
    data class Usage(val objects: Long, val logicalBytes: Long, val chargedBytes: Long) {
        init { require(objects >= 0 && logicalBytes >= 0 && chargedBytes >= logicalBytes) }
    }
    data class Report(val categories: Map<Category, Usage>, val overheadBytes: Long,
        val aliasIdentities: Int, val missingKnownObjects: Long, val complete: Boolean,
        val freeBytes: Long, val allocationUnit: Long, val directoryEntries: Int,
        val reservedAliasIdentities: Int = aliasIdentities, val untrackedAliasIdentities: Int = 0) {
        init {
            require(overheadBytes >= 0 && aliasIdentities >= 0 && missingKnownObjects >= 0 && freeBytes >= 0)
            require(allocationUnit > 0 && directoryEntries >= 0)
            require(reservedAliasIdentities>=0 && untrackedAliasIdentities>=0 &&
                reservedAliasIdentities.toLong()+untrackedAliasIdentities==aliasIdentities.toLong())
            require(categories.keys == Category.entries.toSet())
        }
        val chargedBytes: Long get() = categories.values.fold(overheadBytes) { sum, u -> Math.addExact(sum,u.chargedBytes) }
    }
    enum class Refusal { INCOMPLETE_INVENTORY, MISSING_OBJECTS, BYTE_BUDGET, ALIAS_BUDGET, ENTRY_BUDGET, LOW_SPACE, RANGE }
    sealed interface Decision {
        data class Allowed(val reservedBytes: Long): Decision
        data class Denied(val reason: Refusal): Decision
    }
    fun evaluate(report: Report): Decision = try {
        // Existing writer's independent ciphertext bound, rounded conservatively for allocation.
        val maximumCiphertext = 16L * 1024 * 1024 + 65536
        val reserve = Math.multiplyExact(Math.addExact(maximumCiphertext,report.allocationUnit-1)/report.allocationUnit,report.allocationUnit)
        decide(report,reserve,2,1)
    } catch(_:ArithmeticException) { Decision.Denied(Refusal.RANGE) }
    /** Actual occupancy/headroom is rechecked before publication; filesystem allocation is not promised by preflight. */
    fun retained(report: Report): Decision = try {decide(report,0,0,0)}
        catch(_:ArithmeticException) {Decision.Denied(Refusal.RANGE)}
    private fun decide(report:Report,reserve:Long,newAliases:Int,newEntries:Int):Decision =
        when {
            !report.complete -> Decision.Denied(Refusal.INCOMPLETE_INVENTORY)
            report.missingKnownObjects != 0L -> Decision.Denied(Refusal.MISSING_OBJECTS)
            report.directoryEntries.toLong()+newEntries > maximumEntries -> Decision.Denied(Refusal.ENTRY_BUDGET)
            report.aliasIdentities.toLong()+newAliases > maximumAliases -> Decision.Denied(Refusal.ALIAS_BUDGET)
            Math.addExact(report.chargedBytes,reserve) > maximumBytes -> Decision.Denied(Refusal.BYTE_BUDGET)
            Math.addExact(minimumFreeBytes,reserve) > report.freeBytes -> Decision.Denied(Refusal.LOW_SPACE)
            else -> Decision.Allowed(reserve)
        }
    class Rejected(val reason: Refusal): java.io.IOException("Attachment capacity refused: $reason")
}
