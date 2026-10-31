package net.plainnotes.app.conc.formulations

import net.plainnotes.app.data.HistoricalContext
import net.plainnotes.app.data.MedicationSnapshot
import net.plainnotes.app.data.RecordEntity
import net.plainnotes.app.pk.formulations.TestosteroneFormulations as T

/** Read-only frozen actual administrations. No current medication/profile or planned-dose fallback. */
object TestosteroneHistory {
    data class Audit(val events: Map<String, List<T.Event>>, val unsupported: Int, val missing: Int)
    fun read(records: List<RecordEntity>, ruleSnapshots: Map<Long, String>, nowH: Double): Audit {
        require(nowH.isFinite())
        val events = linkedMapOf<String, MutableList<T.Event>>()
        var unsupported = 0; var missing = 0
        for (r in records) {
            if (r.deleted_at_utc != null || r.status !in setOf("ON_TIME", "LATE")) continue
            val snap = MedicationSnapshot.decode(HistoricalContext.resolved(r, ruleSnapshots), r.medication_id) ?: continue
            if (snap.molecule != "T") continue
            val time = r.taken_utc?.let { it / 3_600_000.0 }
            if (time == null || r.actual_dose == null) { missing++; continue }
            if (time > nowH) { missing++; continue }
            val product = T.product(snap.route)
            if (product == null || product.status == T.Status.RECORD_ONLY) { unsupported++; continue }
            val amount = T.parentMg(r.actual_dose!!, snap.unit.orEmpty(), product)
            if (amount == null) { missing++; continue }
            events.getOrPut(product.code) { mutableListOf() }.add(T.Event(time, amount))
        }
        return Audit(events.mapValues { it.value.sortedBy(T.Event::timeH) }, unsupported, missing)
    }
}
