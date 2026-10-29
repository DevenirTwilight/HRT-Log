package net.plainnotes.app.ui12

import net.plainnotes.app.NotesState
import net.plainnotes.app.conc.ConcentrationCalculator
import net.plainnotes.app.conc.ConcentrationResult
import net.plainnotes.app.conc.HistoryRead
import net.plainnotes.app.conc.experimental.ResearchHistoricalSlAdapter
import net.plainnotes.app.data.*
import net.plainnotes.app.experimental.ConcentrationPreview
import net.plainnotes.app.pk.experimental.ResearchModel
import net.plainnotes.app.conc.TotalE2Calibration
import net.plainnotes.app.ui.E2Calibration
import net.plainnotes.app.ui.E2Total
import java.time.Instant

/** UI-12 synthetic scenarios: invented doses and labs only; no real records, no database. */
object Ui12Fixture {
    val now: Instant = Instant.parse("2026-03-10T12:00:00Z")
    val nowH = ConcentrationCalculator.hours(now)
    private val sl = MedicationEntity(1, "Synthetic SL E2", "E2", "SUBLINGUAL", "MG", 1.0, 30.0, null, 15, 120, false, null, true, true, 0)
    private val slProfile = ProfileEntity(1, "E2", "sublingual", sl_tier = 2)
    private val oral = sl.copy(id = 2, name = "Synthetic oral E2", route = "ORAL")
    private val oralProfile = ProfileEntity(2, "E2", "oral")
    private val cpa = sl.copy(id = 4, name = "Synthetic CPA", molecule = "CPA", route = "ORAL")
    private val profiles = mapOf(1L to slProfile, 2L to oralProfile)

    class Scenario(val state: NotesState, val records: List<RecordEntity>, val result: ConcentrationResult, val preview: ConcentrationPreview?,
                   val nonSublingual: ConcentrationResult?, val doseTimes: List<Instant>, val labs: List<LabValueEntity>) {
        /** SL-CAL-02: what the page computes with the calibration switch on (the same adapter and engine). */
        val outcome get() = E2Calibration.run(preview, nonSublingual, labs, result, doseTimes.firstOrNull()?.let { ConcentrationCalculator.hours(it) }, nowH) {}
        val total get() = E2Total.curve(preview, result, nonSublingual, outcome as? TotalE2Calibration.Calibrated)
        val totalUncalibrated get() = E2Total.curve(preview, result, nonSublingual)
    }

    private fun record(id: Long, m: MedicationEntity, p: ProfileEntity?, hoursAgo: Long, dose: Double = 1.0) = RecordEntity(id, m.id,
        taken_utc = now.minusSeconds(hoursAgo * 3600).toEpochMilli(), taken_zone = "UTC", actual_dose = dose, status = "ON_TIME", origin = "APP",
        revision = 1, config_snapshot = MedicationSnapshot.encode(m, p))
    private val slRecords = listOf(record(1, sl, slProfile, 80), record(2, sl, slProfile, 56), record(3, sl, slProfile, 32), record(4, sl, slProfile, 8), record(5, sl, slProfile, 3))
    private val oralRecords = listOf(record(11, oral, oralProfile, 70), record(12, oral, oralProfile, 46), record(13, oral, oralProfile, 22), record(14, oral, oralProfile, 4))
    private val labs = listOf(LabValueEntity(1, "E2", 180.0, "pg/mL", now.minusSeconds(2 * 3600).toEpochMilli(), "UTC"),
        LabValueEntity(2, "E2", 95.0, "pg/mL", now.minusSeconds(30 * 3600).toEpochMilli(), "UTC"))

    private fun compute(meds: List<MedicationEntity>, records: List<RecordEntity>, weight: Double? = 70.0, labs: List<LabValueEntity> = emptyList()) =
        ConcentrationCalculator.compute(meds, profiles, records, emptyList(), labs, weight, now, calibrate = true, historyRead = HistoryRead(true, nowH))
    private fun preview(records: List<RecordEntity>): ConcentrationPreview? = ConcentrationPreview.compute(ResearchHistoricalSlAdapter.audit(records, emptyMap(), nowH), ResearchModel.M2, nowH)
    private fun times(records: List<RecordEntity>) = records.mapNotNull { it.taken_utc }.map { Instant.ofEpochMilli(it) }.sorted()
    private fun state(meds: List<MedicationEntity>) = NotesState(medications = meds, profiles = profiles, loading = false)

    fun sublingualOnly(): Scenario { val meds = listOf(sl); val r = compute(meds, slRecords, labs = labs)
        return Scenario(state(meds), slRecords, r, preview(slRecords), null, times(slRecords), labs) }
    fun oralOnly(): Scenario { val meds = listOf(oral); val r = compute(meds, oralRecords, labs = labs)
        return Scenario(state(meds), oralRecords, r, null, null, times(oralRecords), labs) }
    fun mixed(): Scenario {
        val meds = listOf(sl, oral); val all = slRecords + oralRecords
        val x = E2Total.nonSublingualInputs(meds, profiles, all, emptyList(), emptyMap())
        val other = ConcentrationCalculator.compute(x.medications, x.profiles, x.records, x.planned, emptyList(), 70.0, now, calibrate = false, historyRead = HistoryRead(true, nowH))
        return Scenario(state(meds), all, compute(meds, all, labs = labs), preview(all), other, times(all), labs)
    }
    /** Labs 30 % above the uncalibrated total: 3 usable (3–4 h after a sublingual dose), one before 24 h of history, one date-only (midnight). */
    private fun scaledLabs(uncalibrated: (Double) -> Double): List<LabValueEntity> {
        fun at(hoursAgo: Double) = now.toEpochMilli() - (hoursAgo * 3_600_000).toLong()
        fun lab(id: Long, t: Long) = LabValueEntity(id, "E2", uncalibrated(ConcentrationCalculator.hours(t)) * 1.3, "pg/mL", t, "UTC")
        return listOf(lab(1, at(5.0)), lab(2, at(28.0)), lab(3, at(52.0)), lab(4, at(76.0)),
            lab(5, java.time.LocalDate.of(2026, 3, 9).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()))
    }
    fun sublingualCalibrated(): Scenario { val meds = listOf(sl); val p = preview(slRecords)!!
        val l = scaledLabs { p.valueAt(it)!! }
        return Scenario(state(meds), slRecords, compute(meds, slRecords, labs = l), p, null, times(slRecords), l) }
    fun mixedCalibrated(): Scenario {
        val meds = listOf(sl, oral); val all = slRecords + oralRecords
        val x = E2Total.nonSublingualInputs(meds, profiles, all, emptyList(), emptyMap())
        val other = ConcentrationCalculator.compute(x.medications, x.profiles, x.records, x.planned, emptyList(), 70.0, now, calibrate = false, historyRead = HistoryRead(true, nowH))
        val p = preview(all)!!
        val l = scaledLabs { p.valueAt(it)!! + (other.evaluateAt(it)?.center ?: 0.0) }
        return Scenario(state(meds), all, compute(meds, all, labs = l), p, other, times(all), l)
    }
    fun noRecords(): Scenario { val meds = listOf(oral); return Scenario(state(meds), emptyList(), compute(meds, emptyList()), null, null, emptyList(), emptyList()) }
    /** An oral estradiol plus cyproterone acetate with no weight recorded: the page asks for the weight. */
    fun needsWeight(): Scenario { val meds = listOf(oral, cpa); val all = oralRecords + record(21, cpa, null, 10)
        return Scenario(state(meds), all, compute(meds, all, weight = null), null, null, times(oralRecords), emptyList()) }
}
