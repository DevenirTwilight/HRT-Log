package net.plainnotes.app.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/**
 * Stock bookkeeping on top of the append-only `supply_transaction` ledger.
 * A taken dose consumes from open containers (oldest first), opening a sealed one when needed;
 * whatever cannot be allocated stays in `dose_record.unallocated_supply_amount`.
 * Editing or deleting a dose appends REVERSE entries instead of rewriting history.
 */
internal object SupplyLedger {
    private const val EPS = 1e-9

    private fun entry(container: Long, record: RecordEntity?, kind: String, delta: Double, reversalOf: Long? = null, reason: String? = null) =
        SupplyEntryEntity(container_id = container, dose_record_id = record?.id, dose_revision = record?.revision, operation_id = UUID.randomUUID().toString(),
            kind = kind, used_delta = delta, reversal_of_id = reversalOf, created_utc = Instant.now().toEpochMilli(), created_zone = ZoneId.systemDefault().id, reason = reason)

    /** Allocates [record]'s actual dose; returns the amount that could not be allocated. */
    suspend fun allocate(dao: NotesDao, record: RecordEntity): Double {
        val dose = record.actual_dose ?: return 0.0
        if (record.status !in listOf("ON_TIME", "LATE") || record.deleted_at_utc != null) return 0.0
        val snapshotUnit=MedicationSnapshot.decode(record.config_snapshot,record.medication_id)?.unit
        if(snapshotUnit!=null && snapshotUnit!=dao.medication(record.medication_id).unit) {
            dao.updateRecord(record.copy(unallocated_supply_amount=dose))
            return dose // No implicit conversion between historical and current inventory units.
        }
        var remaining = dose
        while (remaining > EPS) {
            val all = dao.containers().filter { it.medication_id == record.medication_id }
            val open = all.filter { it.state == "IN_USE" && it.capacity - it.used_amount > EPS }.sortedWith(compareBy<ContainerEntity>({ it.opened_on ?: "" }, { it.id })).firstOrNull()
                ?: all.filter { it.state == "SEALED" }.minByOrNull { it.id }?.also { dao.setContainerState(it.id, "IN_USE", LocalDate.now().toString()) }
                ?: break
            val take = minOf(open.capacity - open.used_amount, remaining)
            dao.supply(entry(open.id, record, "CONSUME", take))
            remaining -= take
            if (open.capacity - open.used_amount - take <= EPS) dao.setContainerState(open.id, "EMPTY", open.opened_on)
        }
        val left = if (remaining > EPS) remaining else 0.0
        dao.updateRecord(record.copy(unallocated_supply_amount = left.takeIf { it > 0 }))
        return left
    }

    /** Undoes every not-yet-reversed consumption of [recordId]. */
    suspend fun reverse(dao: NotesDao, recordId: Long) {
        val entries = dao.supplyFor(recordId)
        val reversed = entries.mapNotNull { it.reversal_of_id }.toSet()
        val record = dao.recordById(recordId)
        entries.filter { it.kind == "CONSUME" && it.id !in reversed }.forEach { c ->
            dao.supply(SupplyEntryEntity(container_id = c.container_id, dose_record_id = recordId, dose_revision = record?.revision, operation_id = UUID.randomUUID().toString(),
                kind = "REVERSE", used_delta = -c.used_delta, reversal_of_id = c.id, created_utc = Instant.now().toEpochMilli(), created_zone = ZoneId.systemDefault().id))
            val box = dao.container(c.container_id)
            if (box.state == "EMPTY" && box.capacity - box.used_amount > EPS) dao.setContainerState(box.id, "IN_USE", box.opened_on)
        }
    }

    /** Sets the remaining amount of a container through an ADJUST entry (manual correction). */
    suspend fun setRemaining(dao: NotesDao, containerId: Long, remaining: Double) {
        val c = dao.container(containerId)
        require(remaining.isFinite() && remaining >= 0 && remaining <= c.capacity)
        val delta = (c.capacity - remaining) - c.used_amount
        if (kotlin.math.abs(delta) > EPS) dao.supply(entry(c.id, null, "ADJUST", delta, reason = "manual"))
        val after = dao.container(containerId)
        if (after.capacity - after.used_amount <= EPS && after.state == "IN_USE") dao.setContainerState(c.id, "EMPTY", c.opened_on)
        if (after.capacity - after.used_amount > EPS && after.state == "EMPTY") dao.setContainerState(c.id, "IN_USE", c.opened_on)
    }
}

/**
 * Open and sealed packages of [medicationId] whose capacity differs from [capacity] and can take it: the amount
 * already used stays as recorded, so a package that has used more than the new capacity is left unchanged.
 */
fun resizableContainers(containers: List<ContainerEntity>, medicationId: Long, capacity: Double): List<ContainerEntity> =
    containers.filter { it.medication_id == medicationId && it.state in setOf("IN_USE", "SEALED") && it.capacity != capacity &&
        it.used_amount <= capacity && it.initial_used_amount <= capacity }
