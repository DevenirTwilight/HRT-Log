package net.plainnotes.app.conc.experimental

import net.plainnotes.app.conc.ConcentrationCalculator
import net.plainnotes.app.data.HistoricalContext
import net.plainnotes.app.data.MedicationSnapshot
import net.plainnotes.app.data.RecordEntity
import net.plainnotes.app.pk.DoseEvent
import net.plainnotes.app.pk.DoseExtras
import net.plainnotes.app.pk.Ester
import net.plainnotes.app.pk.Route

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
    ): List<DoseEvent> {
        require(asOfHour.isFinite())
        val cutoff = asOfHour - ConcentrationCalculator.HISTORY_DAYS * 24.0
        return records.asSequence().mapNotNull { record ->
            if (record.status != "ON_TIME" && record.status != "LATE") return@mapNotNull null
            if (record.deleted_at_utc != null) return@mapNotNull null
            val millis = record.taken_utc ?: return@mapNotNull null
            val hour = ConcentrationCalculator.hours(millis)
            if (!hour.isFinite() || hour < cutoff || hour > asOfHour) return@mapNotNull null
            val amount = record.actual_dose ?: return@mapNotNull null
            if (!amount.isFinite() || amount <= 0.0) return@mapNotNull null
            val frozen = HistoricalContext.resolved(record, immutableSnapshots)
            val snapshot = MedicationSnapshot.decode(frozen, record.medication_id) ?: return@mapNotNull null
            val med = snapshot.medication(record.medication_id) ?: return@mapNotNull null
            val profile = snapshot.profile ?: return@mapNotNull null
            if (med.molecule != "E2" || med.route != "SUBLINGUAL" || profile.ester != "E2" ||
                profile.pk_route != "sublingual" || ConcentrationCalculator.missingFor(med, profile).isNotEmpty()
            ) return@mapNotNull null
            // Engine's current E2 SL reference has no weight scaling, but carry explicit neutral 70kg.
            DoseEvent(
                id = "r${record.id}", route = Route.SUBLINGUAL, timeH = hour,
                doseMG = amount, ester = Ester.E2, weightKG = 70.0,
                extras = DoseExtras(sublingualTier = profile.sl_tier?.toDouble()),
            )
        }.sortedBy { it.timeH }.toList()
    }
}
