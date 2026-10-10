package net.plainnotes.app.conc.experimental

import net.plainnotes.app.conc.ConcentrationCalculator
import net.plainnotes.app.data.HistoricalContext
import net.plainnotes.app.data.MedicationSnapshot
import net.plainnotes.app.data.RecordEntity
import net.plainnotes.app.pk.DoseEvent
import net.plainnotes.app.pk.DoseExtras
import net.plainnotes.app.pk.Ester
import net.plainnotes.app.pk.Route

/** First failing gate for a record, in the order the adapter checks them. */
enum class SlExclusion { NOT_TAKEN, DELETED, NO_TIME, FUTURE, OUTSIDE_WINDOW, INVALID_DOSE, NO_SNAPSHOT, NOT_SUBLINGUAL_E2, INCOMPLETE_CONTEXT }

/** Qualified events plus a count of every excluded record by reason. Counts only; no record content. */
class SlAdapterAudit(val events: List<DoseEvent>, val excluded: Map<SlExclusion, Int>)

/**
 * A READ-ONLY research adapter. The frozen production ConcentrationCalculator is not touched.
 * Reuses its public historical-context and missing-input checks, then applies stricter SL/E2 gates.
 * Nothing is logged, saved, calibrated, uploaded or passed back to production.
 */
object ResearchHistoricalSlAdapter {
    fun verifiedEvents(
        records: List<RecordEntity>,
        immutableSnapshots: Map<Long, String>,
        asOfHour: Double,
    ): List<DoseEvent> = audit(records, immutableSnapshots, asOfHour).events

    fun audit(
        records: List<RecordEntity>,
        immutableSnapshots: Map<Long, String>,
        asOfHour: Double,
    ): SlAdapterAudit {
        require(asOfHour.isFinite())
        val cutoff = asOfHour - ConcentrationCalculator.HISTORY_DAYS * 24.0
        val excluded = sortedMapOf<SlExclusion, Int>()
        val events = records.mapNotNull { record ->
            when (val gate = qualify(record, immutableSnapshots, cutoff, asOfHour)) {
                is SlExclusion -> { excluded.merge(gate, 1, Int::plus); null }
                else -> gate as DoseEvent
            }
        }.sortedBy { it.timeH }
        return SlAdapterAudit(events, excluded)
    }

    /** Returns the [DoseEvent] or the first [SlExclusion] that applies. */
    private fun qualify(record: RecordEntity, immutableSnapshots: Map<Long, String>, cutoff: Double, asOfHour: Double): Any {
        if (record.status != "ON_TIME" && record.status != "LATE") return SlExclusion.NOT_TAKEN
        if (record.deleted_at_utc != null) return SlExclusion.DELETED
        val millis = record.taken_utc ?: return SlExclusion.NO_TIME
        val hour = ConcentrationCalculator.hours(millis)
        if (!hour.isFinite()) return SlExclusion.NO_TIME
        if (hour > asOfHour) return SlExclusion.FUTURE
        if (hour < cutoff) return SlExclusion.OUTSIDE_WINDOW
        val amount = record.actual_dose ?: return SlExclusion.INVALID_DOSE
        if (!amount.isFinite() || amount <= 0.0) return SlExclusion.INVALID_DOSE
        // The configuration frozen with the record (gaps filled only from the immutable rule version, never from the current medication).
        val frozen = HistoricalContext.resolved(record, immutableSnapshots)
        val snapshot = MedicationSnapshot.decode(frozen, record.medication_id) ?: return SlExclusion.NO_SNAPSHOT
        val med = snapshot.medication(record.medication_id) ?: return SlExclusion.NO_SNAPSHOT
        val profile = snapshot.profile ?: return if (med.molecule == "E2" && med.route == "SUBLINGUAL") SlExclusion.NO_SNAPSHOT else SlExclusion.NOT_SUBLINGUAL_E2
        if (med.molecule != "E2" || med.route != "SUBLINGUAL" || profile.ester != "E2" || profile.pk_route != "sublingual") return SlExclusion.NOT_SUBLINGUAL_E2
        if (ConcentrationCalculator.missingFor(med, profile).isNotEmpty()) return SlExclusion.INCOMPLETE_CONTEXT
        // Engine's current E2 SL reference has no weight scaling, but carry explicit neutral 70kg.
        return DoseEvent(
            id = "r${record.id}", route = Route.SUBLINGUAL, timeH = hour,
            doseMG = amount, ester = Ester.E2, weightKG = 70.0,
            extras = DoseExtras(sublingualTier = profile.sl_tier?.toDouble()),
        )
    }
}
